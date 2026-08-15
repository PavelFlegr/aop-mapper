package cz.pavelflegr.remap.sample

import cz.pavelflegr.remap.mapTo
import kotlin.test.Test
import kotlin.test.assertEquals

class KotlinInteropTest {
    enum class DomainStatus {
        ACTIVE,
        DISABLED,
        TEST
    }

    enum class ApiStatus {
        ACTIVE,
        DISABLED,
        UNKNOWN,
    }

    @Test
    fun `maps a Kotlin enum`() {
        println(test(DomainStatus.TEST))
        println(test(DomainStatus.ACTIVE))
        println(test(DomainStatus.DISABLED))

    }

    fun test(aaa: DomainStatus): ApiStatus {
        return aaa.mapTo {
            DomainStatus.DISABLED mapsTo ApiStatus.UNKNOWN
            DomainStatus.TEST mapsTo ApiStatus.UNKNOWN
        }
    }

}
