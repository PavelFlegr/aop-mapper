package io.github.enummapper;

import java.util.Objects;

/** Maps enum constants by their names. */
public final class EnumMapper {
    private EnumMapper() {
    }

    /**
     * Maps a source enum constant to the destination constant with the same name.
     *
     * @param source the source enum constant
     * @param destinationType the destination enum class
     * @return the destination enum constant
     * @param <D> the destination enum type
     * @throws NullPointerException if either argument is {@code null}
     * @throws IllegalArgumentException if the destination has no constant with the source name
     */
    public static <D extends Enum<D>> D map(Enum<?> source, Class<D> destinationType) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destinationType, "destinationType");
        return Enum.valueOf(destinationType, source.name());
    }
}
