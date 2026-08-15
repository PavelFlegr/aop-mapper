@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")

package cz.pavelflegr.remap.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId

internal object CompilerReferences {
    fun functions(
        context: IrPluginContext,
        callableId: CallableId,
    ): Collection<IrSimpleFunctionSymbol> = context.referenceFunctions(callableId)

    fun classSymbol(context: IrPluginContext, classId: ClassId): IrClassSymbol =
        context.referenceClass(classId)!!
}
