package cz.pavelflegr.remap

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EnumMappingTest {
    private enum class SourceStatus {
        ACTIVE,
        LEGACY,
    }

    private enum class DestinationStatus {
        ACTIVE,
        UNKNOWN,
    }

    @Test
    fun `handles a missing destination constant`() {
        val result: DestinationStatus = SourceStatus.LEGACY.mapTo {
            SourceStatus.LEGACY mapsTo DestinationStatus.UNKNOWN
        }

        assertEquals(DestinationStatus.UNKNOWN, result)
    }

    @Test
    fun `remaps an existing destination constant`() {
        val result: DestinationStatus = SourceStatus.ACTIVE.mapTo {
            SourceStatus.ACTIVE mapsTo DestinationStatus.UNKNOWN
        }

        assertEquals(DestinationStatus.UNKNOWN, result)
    }

    @Test
    fun `falls back to same-name mapping`() {
        val result: DestinationStatus = SourceStatus.ACTIVE.mapTo {
            SourceStatus.LEGACY mapsTo DestinationStatus.UNKNOWN
        }

        assertEquals(DestinationStatus.ACTIVE, result)
    }
}
