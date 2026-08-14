package io.github.enummapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jetbrains.kotlin.cli.common.ExitCode;
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EnumMapperFirPluginTest {
    @Test
    void rejectsIncompleteDestinationEnumForExtension(@TempDir Path directory) throws Exception {
        String diagnostics = compile(directory, """
                package example

                import io.github.enummapper.mapTo

                enum class Source { ACTIVE, DISABLED }
                enum class Destination { ACTIVE }

                fun map(source: Source): Destination = source.mapTo()
                """);

        assertMissingMapping(diagnostics);
    }

    @Test
    void rejectsIncompleteDestinationEnumForJavaApi(@TempDir Path directory) throws Exception {
        String diagnostics = compile(directory, """
                package example

                import io.github.enummapper.EnumMapper

                enum class Source { ACTIVE, DISABLED }
                enum class Destination { ACTIVE }

                fun map(source: Source): Destination =
                    EnumMapper.map(source, Destination::class.java)
                """);

        assertMissingMapping(diagnostics);
    }

    @Test
    void rejectsIncompleteEnumInsideObjectMapping(@TempDir Path directory) throws Exception {
        String diagnostics = compile(directory, """
                package example

                import io.github.enummapper.mapper

                enum class SourceStatus { ACTIVE, LEGACY }
                enum class DestinationStatus { ACTIVE }
                data class SourceChild(val status: SourceStatus)
                data class DestinationChild(val status: DestinationStatus)
                data class Source(val child: SourceChild)
                data class Destination(val child: DestinationChild)

                fun map(source: Source): Destination = source.mapper()
                """);

        assertTrue(diagnostics.contains("cannot generate object mapping"), diagnostics);
        assertTrue(diagnostics.contains("LEGACY"), diagnostics);
        assertTrue(diagnostics.contains("child.status"), diagnostics);
    }

    @Test
    void rejectsUnsupportedArrayConversionBeforeExecution(@TempDir Path directory)
            throws Exception {
        String diagnostics = compile(directory, """
                package example
                import io.github.enummapper.mapper

                data class SourceItem(val value: String)
                data class DestinationItem(val value: String)
                data class Source(val items: Array<SourceItem>)
                data class Destination(val items: Array<DestinationItem>)

                fun map(source: Source): Destination = source.mapper()
                """);

        assertTrue(diagnostics.contains("unsupported collection conversion"), diagnostics);
        assertTrue(diagnostics.contains("items"), diagnostics);
    }

    @Test
    void rejectsIncompleteEnumInsideListBeforeExecution(@TempDir Path directory)
            throws Exception {
        String diagnostics = compile(directory, """
                package example
                import io.github.enummapper.mapper

                enum class SourceStatus { ACTIVE, LEGACY }
                enum class DestinationStatus { ACTIVE }
                data class SourceItem(val status: SourceStatus)
                data class DestinationItem(val status: DestinationStatus)
                data class Source(val items: List<SourceItem>)
                data class Destination(val items: List<DestinationItem>)

                fun map(source: Source): Destination = source.mapper()
                """);

        assertTrue(diagnostics.contains("items[].status"), diagnostics);
        assertTrue(diagnostics.contains("LEGACY"), diagnostics);
    }

    @Test
    void rejectsIncompleteEnumInsideSetBeforeExecution(@TempDir Path directory)
            throws Exception {
        String diagnostics = compile(directory, """
                package example
                import io.github.enummapper.mapper
                enum class SourceStatus { ACTIVE, LEGACY }
                enum class DestinationStatus { ACTIVE }
                data class Source(val items: Set<SourceStatus>)
                data class Destination(val items: Set<DestinationStatus>)
                fun map(source: Source): Destination = source.mapper()
                """);

        assertTrue(diagnostics.contains("items[]"), diagnostics);
        assertTrue(diagnostics.contains("LEGACY"), diagnostics);
    }

    @Test
    void rejectsIncompleteEnumInsideMapValueBeforeExecution(@TempDir Path directory)
            throws Exception {
        String diagnostics = compile(directory, """
                package example
                import io.github.enummapper.mapper
                enum class SourceStatus { ACTIVE, LEGACY }
                enum class DestinationStatus { ACTIVE }
                data class Source(val items: Map<String, SourceStatus>)
                data class Destination(val items: Map<String, DestinationStatus>)
                fun map(source: Source): Destination = source.mapper()
                """);

        assertTrue(diagnostics.contains("items{value}"), diagnostics);
        assertTrue(diagnostics.contains("LEGACY"), diagnostics);
    }

    @Test
    void rejectsMissingSourcePropertyBeforeExecution(@TempDir Path directory) throws Exception {
        String diagnostics = compile(directory, """
                package example
                import io.github.enummapper.mapper

                data class Source(val name: String)
                data class Destination(val name: String, val age: Int)

                fun map(source: Source): Destination = source.mapper()
                """);

        assertTrue(diagnostics.contains("age has no source property"), diagnostics);
    }

    @Test
    void rejectsIncompatiblePropertyTypesBeforeExecution(@TempDir Path directory)
            throws Exception {
        String diagnostics = compile(directory, """
                package example
                import io.github.enummapper.mapper

                data class Source(val value: String)
                data class Destination(val value: Int)

                fun map(source: Source): Destination = source.mapper()
                """);

        assertTrue(diagnostics.contains("value has incompatible types"), diagnostics);
        assertTrue(diagnostics.contains("kotlin.String"), diagnostics);
        assertTrue(diagnostics.contains("kotlin.Int"), diagnostics);
    }

    private static String compile(Path directory, String source) throws Exception {
        Path sourceFile = directory.resolve("Mapping.kt");
        Files.writeString(sourceFile, source);
        Path pluginJar = Path.of(System.getProperty("enumMapper.jar"));
        ByteArrayOutputStream compilerOutput = new ByteArrayOutputStream();
        ExitCode exitCode;
        try (PrintStream output = new PrintStream(
                compilerOutput, true, StandardCharsets.UTF_8)) {
            exitCode = new K2JVMCompiler().exec(output,
                    "-no-stdlib",
                    "-no-reflect",
                    "-jvm-target", "17",
                    "-classpath", System.getProperty("java.class.path")
                            + System.getProperty("path.separator") + pluginJar,
                    "-Xplugin=" + pluginJar,
                    "-d", directory.resolve("classes").toString(),
                    sourceFile.toString());
        }

        String diagnostics = compilerOutput.toString(StandardCharsets.UTF_8);
        assertEquals(ExitCode.COMPILATION_ERROR, exitCode, diagnostics);
        return diagnostics;
    }

    private static void assertMissingMapping(String diagnostics) {
        assertTrue(diagnostics.contains(
                "cannot map enum example.Source to example.Destination"), diagnostics);
        assertTrue(diagnostics.contains("[DISABLED]"), diagnostics);
    }
}
