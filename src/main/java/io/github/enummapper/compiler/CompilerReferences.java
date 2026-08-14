package io.github.enummapper.compiler;

import java.util.Collection;
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext;
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol;
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol;
import org.jetbrains.kotlin.name.CallableId;
import org.jetbrains.kotlin.name.ClassId;

final class CompilerReferences {
    private CompilerReferences() {
    }

    static Collection<IrSimpleFunctionSymbol> functions(
            IrPluginContext context, CallableId callableId) {
        return context.referenceFunctions(callableId);
    }

    static IrClassSymbol classSymbol(IrPluginContext context, ClassId classId) {
        return context.referenceClass(classId);
    }
}
