package cz.pavelflegr.remap.intellij

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RemapMappingInspectionTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(RemapMappingInspection())
        myFixture.addFileToProject(
            "api/MapperApi.kt",
            """
            package cz.pavelflegr.remap

            public inline fun <reified D : Enum<D>> Enum<*>.mapTo(): D = error("test")
            public class EnumMapping<S : Enum<S>, D : Enum<D>> {
                public infix fun S.mapsTo(destination: D): Unit = Unit
            }
            public inline fun <S : Enum<S>, reified D : Enum<D>> S.mapTo(
                configure: EnumMapping<S, D>.() -> Unit,
            ): D = error("test")
            public infix fun <T> T.from(value: T): Unit = Unit
            public fun <D : Any, S : Any> S.mapper(
                configure: (D.(source: S) -> Unit)? = null,
            ): D = error("test")
            """.trimIndent(),
        )
    }

    fun testReportsMissingEnumConstant() {
        val errors = highlight(
            """
            package example
            import cz.pavelflegr.remap.mapTo

            enum class Source { ACTIVE, DISABLED }
            enum class Destination { ACTIVE }

            fun map(source: Source): Destination = source.mapTo()
            """.trimIndent(),
        )

        assertContainsElements(
            errors.mapNotNull(HighlightInfo::getDescription),
            "Cannot map enum example.Source to example.Destination: " +
                "missing destination constants [DISABLED]",
        )
    }

    fun testAcceptsCompleteExplicitRules() {
        val errors = highlight(
            """
            package example
            import cz.pavelflegr.remap.mapTo

            enum class Source { ACTIVE, DISABLED }
            enum class Destination { ACTIVE }

            fun map(source: Source): Destination = source.mapTo {
                Source.DISABLED mapsTo Destination.ACTIVE
            }
            """.trimIndent(),
        )

        assertEmpty(errors)
    }

    fun testReportsMissingExplicitRule() {
        val errors = highlight(
            """
            package example
            import cz.pavelflegr.remap.mapTo

            enum class Source { ACTIVE, DISABLED, TEST }
            enum class Destination { ACTIVE }

            fun map(source: Source): Destination = source.mapTo {
                Source.DISABLED mapsTo Destination.ACTIVE
            }
            """.trimIndent(),
        )

        assertContainsElements(
            errors.mapNotNull(HighlightInfo::getDescription),
            "Cannot map enum example.Source to example.Destination: " +
                "missing destination constants [TEST]",
        )
    }

    fun testReportsMissingObjectProperty() {
        val errors = highlight(
            """
            package example
            import cz.pavelflegr.remap.mapper

            data class Source(val name: String)
            data class Destination(val name: String, val age: Int)

            fun map(source: Source): Destination = source.mapper()
            """.trimIndent(),
        )

        assertContainsElements(
            errors.mapNotNull(HighlightInfo::getDescription),
            "Cannot generate object mapping: age has no source property",
        )
    }

    fun testAcceptsPropertyOverride() {
        val errors = highlight(
            """
            package example
            import cz.pavelflegr.remap.from
            import cz.pavelflegr.remap.mapper

            data class Source(val name: String)
            data class Destination(val name: String, val age: Int)

            fun map(source: Source): Destination = source.mapper {
                age from 42
            }
            """.trimIndent(),
        )

        assertEmpty(errors)
    }

    fun testReportsNestedEnumInCollection() {
        val errors = highlight(
            """
            package example
            import cz.pavelflegr.remap.mapper

            enum class SourceStatus { ACTIVE, LEGACY }
            enum class DestinationStatus { ACTIVE }
            data class SourceItem(val status: SourceStatus)
            data class DestinationItem(val status: DestinationStatus)
            data class Source(val items: List<SourceItem>)
            data class Destination(val items: List<DestinationItem>)

            fun map(source: Source): Destination = source.mapper()
            """.trimIndent(),
        )

        val description = errors.single().description
        assertTrue(description, description.contains("items[].status"))
        assertTrue(description, description.contains("[LEGACY]"))
    }

    fun testReportsIncompatiblePropertyTypes() {
        val errors = highlight(
            """
            package example
            import cz.pavelflegr.remap.mapper

            data class Source(val value: String)
            data class Destination(val value: Int)

            fun map(source: Source): Destination = source.mapper()
            """.trimIndent(),
        )

        assertContainsElements(
            errors.mapNotNull(HighlightInfo::getDescription),
            "Cannot generate object mapping: value has incompatible types " +
                "kotlin.String and kotlin.Int",
        )
    }

    private fun highlight(source: String): List<HighlightInfo> {
        myFixture.configureByText("Mapping.kt", source)
        return myFixture.doHighlighting()
            .filter { info ->
                info.description?.let {
                    it.startsWith("Cannot map enum") ||
                        it.startsWith("Cannot generate object mapping")
                } == true
            }
    }
}
