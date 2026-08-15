@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")
@file:OptIn(
    org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI::class,
    org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class,
)

package me.flegr.remap.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrSimpleFunctionSymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrValueParameterSymbolImpl
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

internal class ObjectMappingIrExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.transformChildrenVoid(ObjectMappingTransformer(pluginContext))
        moduleFragment.patchDeclarationParents()
    }
}

private class ObjectMappingTransformer(private val context: IrPluginContext) :
    IrElementTransformerVoid() {
    private val marker = FqName("me.flegr.remap.GeneratedObjectMapping")

    override fun visitCall(expression: IrCall): IrExpression {
        val call = super.visitCall(expression) as IrCall
        if (!call.symbol.owner.hasAnnotation(marker)) return call

        val destinationType = call.typeArguments[0] ?: return call
        val sourceType = call.typeArguments[1] ?: return call
        val sourceIndex = call.symbol.owner.parameters.indexOfFirst {
            it.kind == IrParameterKind.ExtensionReceiver
        }
        val source = call.arguments[sourceIndex] ?: return call
        val lambda = call.arguments.firstNotNullOfOrNull { argument ->
            when (argument) {
                is IrFunctionExpression -> argument
                is IrTypeOperatorCall -> argument.argument as? IrFunctionExpression
                else -> null
            }
        }
        val overrides = extractOverrides(lambda)
        val builder = DeclarationIrBuilder(context, call.symbol)
        return builder.mapObject(source, sourceType, destinationType, overrides, lambda)
    }

    private fun extractOverrides(lambda: IrFunctionExpression?): Map<Name, IrExpression> {
        val body = lambda?.function?.body as? IrBlockBody ?: return emptyMap()
        return body.statements.mapNotNull { statement ->
            val call = when (statement) {
                is IrCall -> statement
                is IrTypeOperatorCall -> statement.argument as? IrCall
                else -> null
            } ?: return@mapNotNull null
            if (call.symbol.owner.fqNameWhenAvailable?.asString() !=
                "me.flegr.remap.from"
            ) {
                return@mapNotNull null
            }
            val receiverIndex = call.symbol.owner.parameters.indexOfFirst {
                it.kind == IrParameterKind.ExtensionReceiver
            }
            val valueIndex = call.symbol.owner.parameters.indexOfFirst {
                it.kind == IrParameterKind.Regular
            }
            val getter = call.arguments.getOrNull(receiverIndex) as? IrCall
                ?: return@mapNotNull null
            val property = getter.symbol.owner.correspondingPropertySymbol?.owner
                ?: return@mapNotNull null
            property.name to (call.arguments.getOrNull(valueIndex) ?: return@mapNotNull null)
        }.toMap()
    }

    private fun IrBuilderWithScope.mapObject(
        source: IrExpression,
        sourceType: IrType,
        destinationType: IrType,
        overrides: Map<Name, IrExpression> = emptyMap(),
        lambda: IrFunctionExpression? = null,
    ): IrExpression {
        if (sourceType == destinationType) return source
        val sourceClass = sourceType.classOrNull?.owner ?: error("Source is not a class")
        val destinationClass = destinationType.classOrNull?.owner
            ?: error("Destination is not a class")

        if (sourceClass.kind == ClassKind.ENUM_CLASS && destinationClass.kind == ClassKind.ENUM_CLASS) {
            return mapEnum(source, destinationClass)
        }
        if (sourceType.isIterableCollection() && destinationType.isIterableCollection()) {
            return mapIterableCollection(
                source,
                sourceType.elementTypes().single(),
                destinationType.elementTypes().single(),
                destinationType.collectionName()!!,
            )
        }
        if (sourceType.isMapCollection() && destinationType.isMapCollection()
        ) {
            return mapMap(
                source,
                sourceType.elementTypes(),
                destinationType.elementTypes(),
                destinationType.collectionName()!!,
            )
        }
        val constructor = destinationClass.constructors.firstOrNull()
            ?: error("No constructor for ${destinationClass.name}")
        val sourceProperties = sourceClass.declarations.filterIsInstance<IrProperty>()
            .filter { it.getter != null }
            .associateBy { it.name }
        return irCallConstructor(constructor.symbol, emptyList()).apply {
            constructor.parameters.forEachIndexed { index, parameter ->
                if (parameter.kind != IrParameterKind.Regular) return@forEachIndexed
                val explicit = overrides[parameter.name]
                if (explicit != null) {
                    arguments[index] = remapOverride(explicit, lambda, source)
                    return@forEachIndexed
                }
                val property = sourceProperties[parameter.name]
                if (property == null) {
                    if (parameter.defaultValue == null) {
                        error("No source property ${parameter.name}")
                    }
                    return@forEachIndexed
                }
                val value = irCall(property.getter!!.symbol).apply { dispatchReceiver = source }
                arguments[index] = mapObject(value, value.type, parameter.type)
            }
        }
    }

    private fun remapOverride(
        expression: IrExpression,
        lambda: IrFunctionExpression?,
        source: IrExpression,
    ): IrExpression {
        val sourceParameter = lambda?.function?.parameters?.firstOrNull {
            it.kind == IrParameterKind.Regular
        }?.symbol
        return expression.deepCopyWithSymbols().transform(object : IrElementTransformerVoid() {
            override fun visitGetValue(expression: IrGetValue): IrExpression =
                if (expression.symbol == sourceParameter) source.deepCopyWithSymbols()
                else super.visitGetValue(expression)
        }, null)
    }

    private fun IrBuilderWithScope.mapEnum(source: IrExpression, destination: IrClass): IrExpression {
        val valueOf = destination.declarations.filterIsInstance<org.jetbrains.kotlin.ir.declarations.IrSimpleFunction>()
            .first { it.name.asString() == "valueOf" }
        val name = source.type.classOrNull!!.owner.declarations.filterIsInstance<IrProperty>()
            .first { it.name.asString() == "name" }.getter!!
        val sourceName = irCall(name.symbol).apply { dispatchReceiver = source }
        return irCall(valueOf.symbol).apply {
            val index = valueOf.parameters.indexOfFirst { it.kind == IrParameterKind.Regular }
            arguments[index] = sourceName
        }
    }

    private fun IrBuilderWithScope.mapIterableCollection(
        source: IrExpression,
        sourceElement: IrType,
        destinationElement: IrType,
        destinationCollection: String,
    ): IrExpression {
        val map = CompilerReferences.functions(
            this@ObjectMappingTransformer.context,
            CallableId(FqName("kotlin.collections"), Name.identifier("map")),
        ).first { symbol ->
            symbol.owner.typeParameters.size == 2 && symbol.owner.parameters.any {
                it.kind == IrParameterKind.ExtensionReceiver &&
                    it.type.classOrNull?.owner?.fqNameWhenAvailable?.asString() ==
                    "kotlin.collections.Iterable"
            }
        }
        val lambda = mappingLambda(sourceElement, destinationElement)
        val mapped = irCall(map).apply {
            typeArguments[0] = sourceElement
            typeArguments[1] = destinationElement
            arguments[map.owner.parameters.indexOfFirst {
                it.kind == IrParameterKind.ExtensionReceiver
            }] = source
            arguments[map.owner.parameters.indexOfFirst {
                it.kind == IrParameterKind.Regular
            }] = lambda
        }
        val conversion = when (destinationCollection) {
            "kotlin.collections.Set" -> "toSet"
            "kotlin.collections.MutableSet" -> "toMutableSet"
            "kotlin.collections.MutableList", "kotlin.collections.MutableCollection" ->
                "toMutableList"
            else -> return mapped
        }

        val converter = CompilerReferences.functions(
            this@ObjectMappingTransformer.context,
            CallableId(FqName("kotlin.collections"), Name.identifier(conversion)),
        ).first { symbol ->
            symbol.owner.typeParameters.size == 1 && symbol.owner.parameters.any {
                it.kind == IrParameterKind.ExtensionReceiver &&
                    it.type.classOrNull?.owner?.fqNameWhenAvailable?.asString() ==
                    "kotlin.collections.Iterable"
            }
        }
        return irCall(converter).apply {
            typeArguments[0] = destinationElement
            arguments[converter.owner.parameters.indexOfFirst {
                it.kind == IrParameterKind.ExtensionReceiver
            }] = mapped
        }
    }

    private fun IrBuilderWithScope.mapMap(
        source: IrExpression,
        sourceElements: List<IrType>,
        destinationElements: List<IrType>,
        destinationCollection: String,
    ): IrExpression {
        val helperName = if (destinationCollection == "kotlin.collections.MutableMap")
            "mapMutableMapEntries" else "mapMapEntries"
        val helper = CompilerReferences.functions(
            this@ObjectMappingTransformer.context,
            CallableId(FqName("me.flegr.remap"), Name.identifier(helperName)),
        ).single()
        return irCall(helper).apply {
            sourceElements.forEachIndexed { index, type -> typeArguments[index] = type }
            destinationElements.forEachIndexed { index, type -> typeArguments[index + 2] = type }
            val regular = helper.owner.parameters.withIndex()
                .filter { it.value.kind == IrParameterKind.Regular }
                .map { it.index }
            arguments[regular[0]] = source
            arguments[regular[1]] = mappingLambda(sourceElements[0], destinationElements[0])
            arguments[regular[2]] = mappingLambda(sourceElements[1], destinationElements[1])
        }
    }

    private fun IrBuilderWithScope.mappingLambda(
        sourceElement: IrType,
        destinationElement: IrType,
    ): IrFunctionExpression {
        val function = context.irFactory.createSimpleFunction(
            UNDEFINED_OFFSET, UNDEFINED_OFFSET, IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA,
            Name.special("<anonymous>"), DescriptorVisibilities.LOCAL, false, false,
            destinationElement, Modality.FINAL, IrSimpleFunctionSymbolImpl(), false, false,
            false, false,
        )
        val parameter = context.irFactory.createValueParameter(
            UNDEFINED_OFFSET, UNDEFINED_OFFSET, IrDeclarationOrigin.DEFINED,
            IrParameterKind.Regular, Name.identifier("item"), sourceElement, false,
            IrValueParameterSymbolImpl(), null, false, false, false,
        ).also { it.parent = function }
        function.parameters = listOf(parameter)
        val lambdaBuilder = DeclarationIrBuilder(context, function.symbol)
        function.body = context.irFactory.createBlockBody(UNDEFINED_OFFSET, UNDEFINED_OFFSET).apply {
            statements += lambdaBuilder.irReturn(
                lambdaBuilder.mapObject(
                    lambdaBuilder.irGet(parameter),
                    sourceElement,
                    destinationElement,
                ),
            )
        }
        val functionType = CompilerReferences.classSymbol(
            this@ObjectMappingTransformer.context,
            ClassId(FqName("kotlin"), Name.identifier("Function1")),
        ).typeWith(sourceElement, destinationElement)
        return IrFunctionExpressionImpl(
            UNDEFINED_OFFSET,
            UNDEFINED_OFFSET,
            functionType,
            function,
            IrStatementOrigin.LAMBDA,
        )
    }

    private fun IrType.collectionName(): String? =
        classOrNull?.owner?.fqNameWhenAvailable?.asString()

    private fun IrType.isIterableCollection(): Boolean = collectionName() in setOf(
        "kotlin.collections.List",
        "kotlin.collections.Set",
        "kotlin.collections.Collection",
        "kotlin.collections.Iterable",
        "kotlin.collections.MutableList",
        "kotlin.collections.MutableSet",
        "kotlin.collections.MutableCollection",
    )

    private fun IrType.isMapCollection(): Boolean = collectionName() in setOf(
        "kotlin.collections.Map",
        "kotlin.collections.MutableMap",
    )

    private fun IrType.elementTypes(): List<IrType> = (this as IrSimpleType).arguments.map {
        (it as? IrTypeProjection)?.type ?: it as IrType
    }

}
