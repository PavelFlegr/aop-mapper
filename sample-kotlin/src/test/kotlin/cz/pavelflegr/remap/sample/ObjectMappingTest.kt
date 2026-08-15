package cz.pavelflegr.remap.sample

import cz.pavelflegr.remap.mapper
import cz.pavelflegr.remap.mapTo
import cz.pavelflegr.remap.from
import kotlin.test.Test
import kotlin.test.assertEquals

class ObjectMappingTest {
    private enum class DomainRole { USER, LEGACY }
    private enum class ApiRole { USER, UNKNOWN }
    private data class DomainAddress(val city: String)
    private data class ApiAddress(val city: String)
    private data class DomainUser(
        val name: String,
        val role: DomainRole,
        val addresses: List<DomainAddress>,
        val offices: Set<DomainAddress>,
        val addressByCode: Map<String, DomainAddress>,
        val history: Collection<DomainAddress>,
        val mutableAddresses: MutableList<DomainAddress>,
        val mutableAddressByCode: MutableMap<String, DomainAddress>,
    )
    private data class ApiUser(
        val displayName: String,
        val role: ApiRole,
        val addresses: List<ApiAddress>,
        val offices: Set<ApiAddress>,
        val addressByCode: Map<String, ApiAddress>,
        val history: Collection<ApiAddress>,
        val mutableAddresses: MutableList<ApiAddress>,
        val mutableAddressByCode: MutableMap<String, ApiAddress>,
    )

    private data class DomainProfile(val name: String, val age: Int)
    private data class ApiProfile(val name: String, val age: Int)
    private data class DomainSettings(val enabled: Boolean)
    private data class ApiSettings(val enabled: Boolean, val label: String = "default")

    @Test
    fun `maps nested objects and enums`() {
        val london = DomainAddress("London")
        val source = DomainUser(
            "Ada",
            DomainRole.LEGACY,
            listOf(london),
            setOf(london),
            mapOf("home" to london),
            listOf(london),
            mutableListOf(london),
            mutableMapOf("home" to london),
        )

        val result: ApiUser = source.mapper {
            displayName from it.name.uppercase()
            role from it.role.mapTo<DomainRole, ApiRole> {
                DomainRole.LEGACY mapsTo ApiRole.UNKNOWN
            }
        }

        assertEquals("ADA", result.displayName)
        assertEquals(ApiRole.UNKNOWN, result.role)
        assertEquals(listOf(ApiAddress("London")), result.addresses)
        assertEquals(setOf(ApiAddress("London")), result.offices)
        assertEquals(mapOf("home" to ApiAddress("London")), result.addressByCode)
        assertEquals(listOf(ApiAddress("London")), result.history)
        assertEquals(mutableListOf(ApiAddress("London")), result.mutableAddresses)
        assertEquals(
            mutableMapOf("home" to ApiAddress("London")),
            result.mutableAddressByCode,
        )
    }

    @Test
    fun `maps matching scalar properties automatically`() {
        val result: ApiProfile = DomainProfile("Ada", 37).mapper()

        assertEquals(ApiProfile("Ada", 37), result)
    }

    @Test
    fun `uses destination constructor defaults`() {
        val result: ApiSettings = DomainSettings(true).mapper()

        assertEquals(ApiSettings(true, "default"), result)
    }

    @Test
    fun `evaluates assignment overrides`() {
        val result: ApiProfile = DomainProfile("Ada", 37).mapper {
            name from it.name.uppercase()
            age from it.age + 1
        }

        assertEquals(ApiProfile("ADA", 38), result)
    }
}
