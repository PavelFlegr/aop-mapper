package me.flegr.remap

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class RemapFirPluginTest {
    @Test
    fun rejectsIncompleteDestinationEnum(@TempDir directory: Path) {
        val diagnostics = compile(
            directory,
            """
            package example
            import me.flegr.remap.mapTo

            enum class Source { ACTIVE, DISABLED }
            enum class Destination { ACTIVE }

            fun map(source: Source): Destination = source.mapTo()
            """.trimIndent(),
        )

        assertContains(diagnostics, "cannot map enum example.Source to example.Destination")
        assertContains(diagnostics, "[DISABLED]")
    }

    @Test
    fun rejectsIncompleteEnumInsideObjectMapping(@TempDir directory: Path) {
        val diagnostics = compile(
            directory,
            """
            package example
            import me.flegr.remap.mapper

            enum class SourceStatus { ACTIVE, LEGACY }
            enum class DestinationStatus { ACTIVE }
            data class SourceChild(val status: SourceStatus)
            data class DestinationChild(val status: DestinationStatus)
            data class Source(val child: SourceChild)
            data class Destination(val child: DestinationChild)

            fun map(source: Source): Destination = source.mapper()
            """.trimIndent(),
        )

        assertContains(diagnostics, "cannot generate object mapping")
        assertContains(diagnostics, "LEGACY")
        assertContains(diagnostics, "child.status")
    }

    @Test
    fun rejectsUnsupportedArrayConversion(@TempDir directory: Path) {
        val diagnostics = compile(
            directory,
            """
            package example
            import me.flegr.remap.mapper

            data class SourceItem(val value: String)
            data class DestinationItem(val value: String)
            data class Source(val items: Array<SourceItem>)
            data class Destination(val items: Array<DestinationItem>)

            fun map(source: Source): Destination = source.mapper()
            """.trimIndent(),
        )

        assertContains(diagnostics, "unsupported collection conversion")
        assertContains(diagnostics, "items")
    }

    @Test
    fun rejectsIncompleteEnumInsideCollection(@TempDir directory: Path) {
        val diagnostics = compile(
            directory,
            """
            package example
            import me.flegr.remap.mapper

            enum class SourceStatus { ACTIVE, LEGACY }
            enum class DestinationStatus { ACTIVE }
            data class SourceItem(val status: SourceStatus)
            data class DestinationItem(val status: DestinationStatus)
            data class Source(val items: List<SourceItem>)
            data class Destination(val items: List<DestinationItem>)

            fun map(source: Source): Destination = source.mapper()
            """.trimIndent(),
        )

        assertContains(diagnostics, "items[].status")
        assertContains(diagnostics, "LEGACY")
    }

    @Test
    fun rejectsIncompleteEnumInsideMapValue(@TempDir directory: Path) {
        val diagnostics = compile(
            directory,
            """
            package example
            import me.flegr.remap.mapper

            enum class SourceStatus { ACTIVE, LEGACY }
            enum class DestinationStatus { ACTIVE }
            data class Source(val items: Map<String, SourceStatus>)
            data class Destination(val items: Map<String, DestinationStatus>)

            fun map(source: Source): Destination = source.mapper()
            """.trimIndent(),
        )

        assertContains(diagnostics, "items{value}")
        assertContains(diagnostics, "LEGACY")
    }

    @Test
    fun rejectsMissingSourceProperty(@TempDir directory: Path) {
        val diagnostics = compile(
            directory,
            """
            package example
            import me.flegr.remap.mapper

            data class Source(val name: String)
            data class Destination(val name: String, val age: Int)

            fun map(source: Source): Destination = source.mapper()
            """.trimIndent(),
        )

        assertContains(diagnostics, "age has no source property")
    }

    @Test
    fun rejectsIncompatiblePropertyTypes(@TempDir directory: Path) {
        val diagnostics = compile(
            directory,
            """
            package example
            import me.flegr.remap.mapper

            data class Source(val value: String)
            data class Destination(val value: Int)

            fun map(source: Source): Destination = source.mapper()
            """.trimIndent(),
        )

        assertContains(diagnostics, "value has incompatible types")
        assertContains(diagnostics, "kotlin.String")
        assertContains(diagnostics, "kotlin.Int")
    }

    private fun compile(directory: Path, source: String): String {
        val sourceFile = directory.resolve("Mapping.kt")
        Files.writeString(sourceFile, source)
        val pluginJar = Path.of(System.getProperty("remap.jar"))
        val compilerOutput = ByteArrayOutputStream()
        val exitCode = PrintStream(compilerOutput, true, StandardCharsets.UTF_8).use { output ->
            K2JVMCompiler().exec(
                output,
                "-no-stdlib",
                "-no-reflect",
                "-jvm-target", "17",
                "-classpath", System.getProperty("java.class.path") +
                    System.getProperty("path.separator") + pluginJar,
                "-Xplugin=$pluginJar",
                "-d", directory.resolve("classes").toString(),
                sourceFile.toString(),
            )
        }
        val diagnostics = compilerOutput.toString(StandardCharsets.UTF_8)
        assertEquals(ExitCode.COMPILATION_ERROR, exitCode, diagnostics)
        return diagnostics
    }

    private fun assertContains(actual: String, expected: String) {
        assertTrue(actual.contains(expected), actual)
    }
}
