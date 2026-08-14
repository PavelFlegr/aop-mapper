package io.github.enummapper

/** Maps this enum constant to a destination enum inferred from [D]. */
public inline fun <reified D : Enum<D>> Enum<*>.mapTo(): D =
    EnumMapper.map(this, D::class.java)

/** Custom source-to-destination enum mappings. */
public class EnumMapping<S : Enum<S>, D : Enum<D>> {
    private val mappings: MutableMap<S, D> = mutableMapOf()

    /** Maps this source constant to [destination] instead of using its name. */
    public infix fun S.mapsTo(destination: D) {
        mappings[this] = destination
    }

    @PublishedApi
    internal fun destinationFor(source: S): D? = mappings[source]
}

/** Maps this enum using configured overrides before falling back to its name. */
public inline fun <S : Enum<S>, reified D : Enum<D>> S.mapTo(
    configure: EnumMapping<S, D>.() -> Unit,
): D = EnumMapping<S, D>()
    .apply(configure)
    .destinationFor(this)
    ?: EnumMapper.map(this, D::class.java)
