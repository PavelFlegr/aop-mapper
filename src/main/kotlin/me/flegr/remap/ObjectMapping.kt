package me.flegr.remap

/** Marks object mapping calls that are replaced by the compiler plugin. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
public annotation class GeneratedObjectMapping

/**
 * Maps this object to [D] at compile time.
 *
 * Destination constructor properties map by name unless assigned in [configure].
 */
@GeneratedObjectMapping
public fun <D : Any, S : Any> S.mapper(
    configure: (D.(source: S) -> Unit)? = null,
): D = error("Object mapper requires the Remap compiler plugin")

/** Supplies an explicit destination property value to [mapper]. */
public infix fun <T> T.from(value: T): Unit = Unit

/** Runtime target used by generated map conversion code. */
@JvmSynthetic
public fun <SK, SV, DK, DV> mapMapEntries(
    source: Map<SK, SV>,
    keyMapper: (SK) -> DK,
    valueMapper: (SV) -> DV,
): Map<DK, DV> = source.entries.associate { (key, value) ->
    keyMapper(key) to valueMapper(value)
}

/** Runtime target used by generated mutable-map conversion code. */
@JvmSynthetic
public fun <SK, SV, DK, DV> mapMutableMapEntries(
    source: Map<SK, SV>,
    keyMapper: (SK) -> DK,
    valueMapper: (SV) -> DV,
): MutableMap<DK, DV> = mapMapEntries(source, keyMapper, valueMapper).toMutableMap()
