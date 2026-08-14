package io.github.enummapper.compiler;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import io.github.enummapper.EnumMapper;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

/** javac plugin that validates calls to {@link EnumMapper#map(Enum, Class)}. */
public final class EnumMapperPlugin implements Plugin {
    private static final String MAPPER_TYPE = "io.github.enummapper.EnumMapper";

    @Override
    public String getName() {
        return "EnumMapper";
    }

    @Override
    public void init(JavacTask task, String... args) {
        Trees trees = Trees.instance(task);
        Set<CompilationUnitTree> scanned =
                Collections.newSetFromMap(new IdentityHashMap<>());

        task.addTaskListener(new TaskListener() {
            @Override
            public void started(TaskEvent event) {
            }

            @Override
            public void finished(TaskEvent event) {
                CompilationUnitTree unit = event.getCompilationUnit();
                if (event.getKind() == TaskEvent.Kind.ANALYZE
                        && unit != null
                        && scanned.add(unit)) {
                    new MappingScanner(trees, unit).scan(unit, null);
                }
            }
        });
    }

    private static final class MappingScanner extends TreePathScanner<Void, Void> {
        private final Trees trees;
        private final CompilationUnitTree unit;

        private MappingScanner(Trees trees, CompilationUnitTree unit) {
            this.trees = trees;
            this.unit = unit;
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree invocation, Void unused) {
            validate(invocation);
            return super.visitMethodInvocation(invocation, unused);
        }

        private void validate(MethodInvocationTree invocation) {
            TreePath invocationPath = getCurrentPath();
            Element calledElement = trees.getElement(invocationPath);
            if (!(calledElement instanceof ExecutableElement method)
                    || !method.getSimpleName().contentEquals("map")
                    || !(method.getEnclosingElement() instanceof TypeElement owner)
                    || !owner.getQualifiedName().contentEquals(MAPPER_TYPE)) {
                return;
            }

            List<? extends ExpressionTree> arguments = invocation.getArguments();
            if (arguments.size() != 2
                    || !(arguments.get(1) instanceof MemberSelectTree classLiteral)
                    || !classLiteral.getIdentifier().contentEquals("class")) {
                return;
            }

            TypeElement sourceType = enumType(
                    trees.getTypeMirror(new TreePath(invocationPath, arguments.get(0))));
            TypeElement destinationType = enumType(trees.getTypeMirror(
                    new TreePath(new TreePath(invocationPath, classLiteral),
                            classLiteral.getExpression())));
            if (sourceType == null || destinationType == null) {
                return;
            }

            Set<String> destinationConstants = enumConstants(destinationType);
            Set<String> missing = enumConstants(sourceType);
            missing.removeAll(destinationConstants);
            if (!missing.isEmpty()) {
                String message = "Cannot map enum " + sourceType.getQualifiedName()
                        + " to " + destinationType.getQualifiedName()
                        + ": missing destination constants " + missing;
                trees.printMessage(Diagnostic.Kind.ERROR, message, invocation, unit);
            }
        }

        private static TypeElement enumType(TypeMirror mirror) {
            if (!(mirror instanceof DeclaredType declaredType)
                    || !(declaredType.asElement() instanceof TypeElement type)
                    || type.getKind() != ElementKind.ENUM) {
                return null;
            }
            return type;
        }

        private static Set<String> enumConstants(TypeElement type) {
            Set<String> constants = new LinkedHashSet<>();
            for (Element element : type.getEnclosedElements()) {
                if (element.getKind() == ElementKind.ENUM_CONSTANT) {
                    constants.add(element.getSimpleName().toString());
                }
            }
            return constants;
        }
    }
}
