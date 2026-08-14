package io.github.enummapper.sample

import io.github.enummapper.EnumMapper
import io.github.enummapper.mapTo
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
    fun `maps a Kotlin enum through the Java API`() {
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
