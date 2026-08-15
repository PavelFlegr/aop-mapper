@file:OptIn(org.jetbrains.kotlin.analysis.api.KaExperimentalApi::class)

package cz.pavelflegr.remap.intellij

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.expressions.expressionType
import org.jetbrains.kotlin.analysis.api.resolution.resolveCall
import org.jetbrains.kotlin.analysis.api.scopes.declaredMemberScope
import org.jetbrains.kotlin.analysis.api.scopes.staticDeclaredMemberScope
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaEnumEntrySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.expandedSymbol
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

public class RemapMappingInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                val message = analyze(expression) { validate(expression) } ?: return
                holder.registerProblem(
                    expression.calleeExpression ?: expression,
                    message,
                    ProblemHighlightType.GENERIC_ERROR,
                )
            }
        }

    context(session: KaSession)
    private fun validate(expression: KtCallExpression): String? {
        val call = expression.resolveCall() ?: return null
        val callableName = call.signature.symbol.callableId
            ?.asSingleFqName()
            ?.asString()
            ?: return null
        return when (callableName) {
            MAP_TO -> {
                val lambda = expression.lambdaArguments.singleOrNull()?.getLambdaExpression()
                if (expression.valueArguments.isNotEmpty() && lambda == null) {
                    return null
                }
                validateEnumMapping(
                    call.extensionReceiver?.type,
                    expression.expressionType,
                    lambda?.mappedSourceEntries().orEmpty(),
                )
            }

            OBJECT_MAPPER -> {
                val source = call.extensionReceiver?.type ?: return null
                val destination = expression.expressionType ?: return null
                val overrides = expression.lambdaArguments.firstOrNull()
                    ?.getLambdaExpression()
                    ?.bodyExpression
                    ?.statements
                    ?.mapNotNull { statement ->
                        val override = statement as? KtBinaryExpression ?: return@mapNotNull null
                        if (override.operationReference.text != "from") return@mapNotNull null
                        override.left as? KtNameReferenceExpression
                    }
                    ?.mapTo(mutableSetOf()) { Name.identifier(it.getReferencedName()) }
                    .orEmpty()
                val problems = mutableListOf<String>()
                inspectTypes(source, destination, "", mutableSetOf(), problems, overrides)
                problems.takeIf { it.isNotEmpty() }
                    ?.joinToString("; ", "Cannot generate object mapping: ")
            }

            else -> null
        }
    }

    context(session: KaSession)
    private fun validateEnumMapping(
        sourceType: KaType?,
        destinationType: KaType?,
        mappedSourceEntries: Set<String> = emptySet(),
    ): String? {
        val source = sourceType?.expandedSymbol ?: return null
        val destination = destinationType?.expandedSymbol ?: return null
        if (source.classKind != KaClassKind.ENUM_CLASS ||
            destination.classKind != KaClassKind.ENUM_CLASS
        ) {
            return null
        }
        val missing = missingEnumEntries(source, destination)
            .filterNot(mappedSourceEntries::contains)
        if (missing.isEmpty()) return null
        return "Cannot map enum ${source.displayName()} to ${destination.displayName()}: " +
            "missing destination constants $missing"
    }

    context(session: KaSession)
    private fun inspectTypes(
        sourceType: KaType,
        destinationType: KaType,
        path: String,
        visited: MutableSet<Pair<String, String>>,
        problems: MutableList<String>,
        rootOverrides: Set<Name> = emptySet(),
    ) {
        val source = sourceType.expandedSymbol ?: return
        val destination = destinationType.expandedSymbol ?: return
        val pair = source.displayName() to destination.displayName()
        if (!visited.add(pair)) return

        if (source.classKind == KaClassKind.ENUM_CLASS &&
            destination.classKind == KaClassKind.ENUM_CLASS
        ) {
            val missing = missingEnumEntries(source, destination)
            if (missing.isNotEmpty()) {
                problems += "${path.ifEmpty { "<root>" }} maps ${source.displayName()} to " +
                    "${destination.displayName()}, missing $missing"
            }
            return
        }

        val sourceName = source.displayName()
        val destinationName = destination.displayName()
        val sourceArguments = (sourceType as? KaClassType)?.typeArguments
            ?.mapNotNull { it.type }
            .orEmpty()
        val destinationArguments = (destinationType as? KaClassType)?.typeArguments
            ?.mapNotNull { it.type }
            .orEmpty()

        if (sourceName in ITERABLE_TYPES && destinationName in ITERABLE_TYPES) {
            if (sourceArguments.isNotEmpty() && destinationArguments.isNotEmpty()) {
                inspectTypes(
                    sourceArguments[0], destinationArguments[0], "$path[]", visited, problems,
                )
            }
            return
        }
        if (sourceName in MAP_TYPES && destinationName in MAP_TYPES &&
            sourceArguments.size == 2 && destinationArguments.size == 2
        ) {
            inspectTypes(
                sourceArguments[0], destinationArguments[0], "$path{key}", visited, problems,
            )
            inspectTypes(
                sourceArguments[1], destinationArguments[1], "$path{value}", visited, problems,
            )
            return
        }
        if (sourceName in COLLECTION_TYPES || destinationName in COLLECTION_TYPES) {
            if (sourceType != destinationType) {
                problems += "${path.ifEmpty { "<root>" }} uses unsupported collection conversion " +
                    "$sourceType to $destinationType"
            }
            return
        }
        if (sourceName == destinationName) return
        if (sourceName in SCALAR_TYPES || destinationName in SCALAR_TYPES) {
            problems += "${path.ifEmpty { "<root>" }} has incompatible types " +
                "$sourceName and $destinationName"
            return
        }

        val sourceProperties = source.declaredMemberScope.callables
            .filterIsInstance<KaPropertySymbol>()
            .associateBy { it.name }
        val constructors = destination.declaredMemberScope.constructors
        val constructor = constructors.firstOrNull { it.isPrimary }
            ?: constructors.firstOrNull()
            ?: return
        constructor.valueParameters.forEach { parameter ->
            if (path.isEmpty() && parameter.name in rootOverrides) return@forEach
            val childPath = if (path.isEmpty()) parameter.name.asString()
                else "$path.${parameter.name.asString()}"
            val property = sourceProperties[parameter.name]
            if (property == null) {
                if (!parameter.hasDefaultValue) problems += "$childPath has no source property"
                return@forEach
            }
            inspectTypes(
                property.returnType,
                parameter.returnType,
                childPath,
                visited,
                problems,
            )
        }
    }

    context(session: KaSession)
    private fun missingEnumEntries(
        source: KaClassSymbol,
        destination: KaClassSymbol,
    ): List<String> {
        val destinationEntries = destination.staticDeclaredMemberScope.callables
            .filterIsInstance<KaEnumEntrySymbol>()
            .mapTo(mutableSetOf()) { it.name.asString() }
        return source.staticDeclaredMemberScope.callables
            .filterIsInstance<KaEnumEntrySymbol>()
            .map { it.name.asString() }
            .filterNot(destinationEntries::contains)
            .toList()
    }

    private fun KaClassSymbol.displayName(): String =
        classId?.asSingleFqName()?.asString() ?: name?.asString() ?: "<local>"

    private fun KtLambdaExpression.mappedSourceEntries(): Set<String> =
        bodyExpression?.statements
            ?.mapNotNull { statement ->
                val rule = statement as? KtBinaryExpression ?: return@mapNotNull null
                if (rule.operationReference.text != "mapsTo") return@mapNotNull null
                when (val source = rule.left) {
                    is KtNameReferenceExpression -> source.getReferencedName()
                    is KtDotQualifiedExpression ->
                        (source.selectorExpression as? KtNameReferenceExpression)
                            ?.getReferencedName()
                    else -> null
                }
            }
            ?.toSet()
            .orEmpty()

    private companion object {
        const val MAP_TO = "cz.pavelflegr.remap.mapTo"
        const val OBJECT_MAPPER = "cz.pavelflegr.remap.mapper"

        val ITERABLE_TYPES = setOf(
            "kotlin.collections.List",
            "kotlin.collections.Set",
            "kotlin.collections.Collection",
            "kotlin.collections.Iterable",
            "kotlin.collections.MutableList",
            "kotlin.collections.MutableSet",
            "kotlin.collections.MutableCollection",
        )
        val MAP_TYPES = setOf("kotlin.collections.Map", "kotlin.collections.MutableMap")
        val COLLECTION_TYPES = ITERABLE_TYPES + MAP_TYPES + "kotlin.Array"
        val SCALAR_TYPES = setOf(
            "kotlin.String", "kotlin.Boolean", "kotlin.Byte", "kotlin.Short", "kotlin.Int",
            "kotlin.Long", "kotlin.Float", "kotlin.Double", "kotlin.Char",
        )
    }
}
