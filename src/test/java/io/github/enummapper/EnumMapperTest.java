package io.github.enummapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EnumMapperTest {
    private enum SourceStatus {
        ACTIVE,
        DISABLED
    }

    private enum CompleteStatus {
        ACTIVE,
        DISABLED,
        UNKNOWN
    }

    private enum IncompleteStatus {
        ACTIVE
    }

    @Test
    void mapsConstantsWithTheSameName() {
        assertEquals(CompleteStatus.ACTIVE,
                EnumMapper.map(SourceStatus.ACTIVE, CompleteStatus.class));
        assertEquals(CompleteStatus.DISABLED,
                EnumMapper.map(SourceStatus.DISABLED, CompleteStatus.class));
    }

    @Test
    void rejectsMissingDestinationConstantAtRuntime() {
        assertThrows(IllegalArgumentException.class,
                () -> EnumMapper.map(SourceStatus.DISABLED, IncompleteStatus.class));
    }

    @Test
    void rejectsNullArguments() {
        assertThrows(NullPointerException.class,
                () -> EnumMapper.map(null, CompleteStatus.class));
        assertThrows(NullPointerException.class,
                () -> EnumMapper.map(SourceStatus.ACTIVE, null));
    }
}
