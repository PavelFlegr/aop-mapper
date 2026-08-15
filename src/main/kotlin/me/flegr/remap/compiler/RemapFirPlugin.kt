package me.flegr.remap.compiler

import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticRenderers
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.declarations.collectEnumEntries
import org.jetbrains.kotlin.fir.declarations.constructors
import org.jetbrains.kotlin.fir.declarations.declaredProperties
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirAnonymousFunctionExpression
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.references.FirNamedReference
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinTypeProjection
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtElement

@OptIn(ExperimentalCompilerApi::class)
public class RemapCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = "me.flegr.remap"
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        FirExtensionRegistrarAdapter.registerExtension(RemapFirExtensionRegistrar())
        IrGenerationExtension.registerExtension(ObjectMappingIrExtension())
    }
}

private class RemapFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::RemapAdditionalCheckers
        registerDiagnosticContainers(RemapFirDiagnostics)
    }
}

private class RemapAdditionalCheckers(session: FirSession) :
    FirAdditionalCheckersExtension(session) {
    override val expressionCheckers: ExpressionCheckers = object : ExpressionCheckers() {
        override val functionCallCheckers: Set<FirFunctionCallChecker> =
            setOf(EnumMappingCallChecker)
    }
}

private object EnumMappingCallChecker : FirFunctionCallChecker(MppCheckerKind.Common) {
    private val mapToId = CallableId(FqName("me.flegr.remap"), Name.identifier("mapTo"))
    private val objectMapperId = CallableId(
        FqName("me.flegr.remap"),
        Name.identifier("mapper"),
    )

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        val callableId = expression.calleeReference.toResolvedCallableSymbol()?.callableId ?: return
        if (callableId == objectMapperId) {
            validateObjectMapping(expression)
            return
        }
        if (callableId == mapToId && expression.argumentList.arguments.isNotEmpty()) return
        if (callableId != mapToId) return
        val sourceType = expression.extensionReceiver?.resolvedType ?: return
        val destinationType = expression.resolvedType

        val source = sourceType.toRegularClassSymbol(context.session)?.takeIf { it.isEnum() } ?: return
        val destination = destinationType.toRegularClassSymbol(context.session)
            ?.takeIf { it.isEnum() }
            ?: return
        val destinationEntries = destination.collectEnumEntries(context.session)
            .mapTo(mutableSetOf()) { it.callableId.callableName.asString() }
        val missing = source.collectEnumEntries(context.session)
            .map { it.callableId.callableName.asString() }
            .filterNot(destinationEntries::contains)
        if (missing.isEmpty()) return

        val message = "Cannot map enum ${source.classId.asSingleFqName()} to " +
            "${destination.classId.asSingleFqName()}: missing destination constants $missing"
        reporter.reportOn(expression.source, RemapFirDiagnostics.MISSING_ENUM_CONSTANTS, message)
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun validateObjectMapping(expression: FirFunctionCall) {
        val sourceType = expression.extensionReceiver?.resolvedType ?: return
        val destinationType = expression.resolvedType
        val overrides = (expression.argumentList.arguments.firstOrNull()
            as? FirAnonymousFunctionExpression)
            ?.anonymousFunction?.body?.statements
            ?.filterIsInstance<FirFunctionCall>()
            ?.mapNotNull { call ->
                val calledName = call.calleeReference.name.asString()
                if (calledName != "from") return@mapNotNull null
                val receiver = call.explicitReceiver as? FirQualifiedAccessExpression
                (receiver?.calleeReference as? FirNamedReference)?.name
            }
            ?.toSet()
            .orEmpty()
        val problems = mutableListOf<String>()
        inspectTypes(sourceType, destinationType, "", mutableSetOf(), problems, overrides)
        if (problems.isNotEmpty()) {
            reporter.reportOn(
                expression.source,
                RemapFirDiagnostics.MISSING_ENUM_CONSTANTS,
                "Cannot generate object mapping: ${problems.joinToString("; ")}",
            )
        }
    }

    context(context: CheckerContext)
    private fun inspectTypes(
        sourceType: ConeKotlinType,
        destinationType: ConeKotlinType,
        path: String,
        visited: MutableSet<Pair<String, String>>,
        problems: MutableList<String>,
        rootOverrides: Set<Name> = emptySet(),
    ) {
        val source = sourceType.toRegularClassSymbol(context.session) ?: return
        val destination = destinationType.toRegularClassSymbol(context.session) ?: return
        val pair = source.classId.asString() to destination.classId.asString()
        if (!visited.add(pair)) return

        if (source.isEnum() && destination.isEnum()) {
            val destinationEntries = destination.collectEnumEntries(context.session)
                .mapTo(mutableSetOf()) { it.callableId.callableName.asString() }
            val missing = source.collectEnumEntries(context.session)
                .map { it.callableId.callableName.asString() }
                .filterNot(destinationEntries::contains)
            if (missing.isNotEmpty()) {
                problems += "${path.ifEmpty { "<root>" }} maps ${source.classId.asSingleFqName()} " +
                    "to ${destination.classId.asSingleFqName()}, missing $missing"
            }
            return
        }

        val collectionTypes = setOf(
            "kotlin.collections.List",
            "kotlin.collections.Set",
            "kotlin.collections.Map",
            "kotlin.collections.Collection",
            "kotlin.collections.MutableList",
            "kotlin.collections.MutableSet",
            "kotlin.collections.MutableCollection",
            "kotlin.collections.MutableMap",
            "kotlin.Array",
        )
        val sourceName = source.classId.asSingleFqName().asString()
        val destinationName = destination.classId.asSingleFqName().asString()
        val iterableCollections = setOf(
            "kotlin.collections.List",
            "kotlin.collections.Set",
            "kotlin.collections.Collection",
            "kotlin.collections.Iterable",
            "kotlin.collections.MutableList",
            "kotlin.collections.MutableSet",
            "kotlin.collections.MutableCollection",
        )
        val sourceArguments = (sourceType as? ConeClassLikeType)?.typeArguments
            ?.mapNotNull { (it as? ConeKotlinTypeProjection)?.type }
            .orEmpty()
        val destinationArguments = (destinationType as? ConeClassLikeType)?.typeArguments
            ?.mapNotNull { (it as? ConeKotlinTypeProjection)?.type }
            .orEmpty()
        if (sourceName in iterableCollections && destinationName in iterableCollections) {
            if (sourceArguments.isNotEmpty() && destinationArguments.isNotEmpty()) {
                inspectTypes(
                    sourceArguments[0],
                    destinationArguments[0],
                    "$path[]",
                    visited,
                    problems,
                    rootOverrides,
                )
            }
            return
        }
        if (sourceName in setOf("kotlin.collections.Map", "kotlin.collections.MutableMap") &&
            destinationName in setOf("kotlin.collections.Map", "kotlin.collections.MutableMap") &&
            sourceArguments.size == 2 && destinationArguments.size == 2
        ) {
            inspectTypes(
                sourceArguments[0], destinationArguments[0], "$path{key}", visited, problems,
                rootOverrides,
            )
            inspectTypes(
                sourceArguments[1], destinationArguments[1], "$path{value}", visited, problems,
                rootOverrides,
            )
            return
        }
        if (sourceName in collectionTypes || destinationName in collectionTypes
        ) {
            if (sourceType != destinationType) {
                problems += "${path.ifEmpty { "<root>" }} uses unsupported collection conversion " +
                    "${sourceType} to ${destinationType}"
            }
            return
        }

        if (source.classId == destination.classId) return

        val scalarTypes = setOf(
            "kotlin.String", "kotlin.Boolean", "kotlin.Byte", "kotlin.Short",
            "kotlin.Int", "kotlin.Long", "kotlin.Float", "kotlin.Double", "kotlin.Char",
        )
        if (source.classId.asSingleFqName().asString() in scalarTypes ||
            destination.classId.asSingleFqName().asString() in scalarTypes
        ) {
            problems += "${path.ifEmpty { "<root>" }} has incompatible types " +
                "${source.classId.asSingleFqName()} and ${destination.classId.asSingleFqName()}"
            return
        }

        val sourceProperties = source.declaredProperties(context.session).associateBy { it.name }
        val constructor = destination.constructors(context.session).firstOrNull() ?: return
        constructor.valueParameterSymbols.forEach { parameter ->
            if (path.isEmpty() && parameter.name in rootOverrides) return@forEach
            val childPath = if (path.isEmpty()) parameter.name.asString()
                else "$path.${parameter.name.asString()}"
            val property = sourceProperties[parameter.name]
            if (property == null) {
                if (!parameter.hasDefaultValue) {
                    problems += "$childPath has no source property"
                }
                return@forEach
            }
            inspectTypes(
                property.resolvedReturnType,
                parameter.resolvedReturnType,
                childPath,
                visited,
                problems,
                rootOverrides,
            )
        }
    }

    private fun FirRegularClassSymbol.isEnum(): Boolean = classKind == ClassKind.ENUM_CLASS
}

private object RemapFirDiagnostics : KtDiagnosticsContainer() {
    override fun getRendererFactory(): BaseDiagnosticRendererFactory = RemapFirMessages

    val MISSING_ENUM_CONSTANTS: KtDiagnosticFactory1<String> = KtDiagnosticFactory1(
        "MISSING_ENUM_CONSTANTS",
        Severity.ERROR,
        SourceElementPositioningStrategies.DEFAULT,
        KtElement::class,
        getRendererFactory(),
    )
}

private object RemapFirMessages : BaseDiagnosticRendererFactory() {
    override val MAP: KtDiagnosticFactoryToRendererMap by
        KtDiagnosticFactoryToRendererMap("REMAP") { map ->
            map.put(
                RemapFirDiagnostics.MISSING_ENUM_CONSTANTS,
                "{0}",
                KtDiagnosticRenderers.TO_STRING,
            )
        }
}
