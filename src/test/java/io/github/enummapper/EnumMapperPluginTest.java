package io.github.enummapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

class EnumMapperPluginTest {
    @Test
    void rejectsIncompleteDestinationEnum() {
        String source = """
                package example;
                import io.github.enummapper.EnumMapper;
                enum Source { ACTIVE, DISABLED }
                enum Destination { ACTIVE }
                class Mapping {
                    Destination map(Source source) {
                        return EnumMapper.map(source, Destination.class);
                    }
                }
                """;

        CompilationResult result = compile(source);

        assertFalse(result.success());
        assertTrue(result.diagnostics().contains(
                "Cannot map enum example.Source to example.Destination"));
        assertTrue(result.diagnostics().contains("DISABLED"));
    }

    @Test
    void acceptsDestinationWithEverySourceConstant() {
        String source = """
                package example;
                import static io.github.enummapper.EnumMapper.map;
                enum Source { ACTIVE, DISABLED }
                enum Destination { ACTIVE, DISABLED, UNKNOWN }
                class Mapping {
                    Destination mapStatus(Source source) {
                        return map(source, Destination.class);
                    }
                }
                """;

        CompilationResult result = compile(source);

        assertTrue(result.success(), result.diagnostics());
    }

    private static CompilationResult compile(String source) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaFileObject file = new SourceFile(source);
        List<String> options = List.of(
                "-proc:none",
                "-Xplugin:EnumMapper",
                "-classpath", System.getProperty("java.class.path"),
                "-d", System.getProperty("java.io.tmpdir"));

        boolean success;
        try (StandardJavaFileManager fileManager =
                     compiler.getStandardFileManager(diagnostics, Locale.ROOT, null)) {
            success = compiler.getTask(
                    null, fileManager, diagnostics, options, null, List.of(file)).call();
        } catch (Exception exception) {
            throw new AssertionError("Compilation failed unexpectedly", exception);
        }

        String messages = diagnostics.getDiagnostics().stream()
                .map(diagnostic -> diagnostic.getMessage(Locale.ROOT))
                .reduce("", (left, right) -> left + System.lineSeparator() + right);
        return new CompilationResult(success, messages);
    }

    private record CompilationResult(boolean success, String diagnostics) {
    }

    private static final class SourceFile extends SimpleJavaFileObject {
        private final String source;

        private SourceFile(String source) {
            super(URI.create("string:///example/Mapping.java"), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }
}
