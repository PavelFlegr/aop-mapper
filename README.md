# Enum Mapper

`enum-mapper` maps JVM enum constants by name and optionally validates Java and Kotlin calls at compile time. It has no Micronaut runtime dependency and is compatible with Micronaut 4 applications.

```java
ApiStatus result = EnumMapper.map(domainStatus, ApiStatus.class);
```

The destination enum may have additional constants, but it must contain every source constant. Without compiler validation, a missing destination constant produces the standard `IllegalArgumentException` from `Enum.valueOf`.

## Java compile-time validation

The library JAR exposes a javac plugin through `META-INF/services/com.sun.source.util.Plugin`. Enable it in a Gradle consumer:

```kotlin
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs += "-Xplugin:EnumMapper"
}
```

The plugin reports the source enum, destination enum, and all constants missing from the destination.

## Kotlin

Apply the Gradle plugin alongside Kotlin. It adds the runtime API and FIR compiler plugin to every Kotlin compilation:

```kotlin
plugins {
    kotlin("jvm") version "2.3.20"
    id("io.github.enummapper") version "1.0.0-SNAPSHOT"
}
```

Kotlin can call the runtime API directly:

```kotlin
val result = EnumMapper.map(domainStatus, ApiStatus::class.java)
```

The reified Kotlin extension avoids passing the destination class explicitly:

```kotlin
val result = domainStatus.mapTo<ApiStatus>()

// The expected return type can also infer the destination.
val inferred: ApiStatus = domainStatus.mapTo()
```

Provide typed mapping rules to override selected values:

```kotlin
val result: ApiStatus = domainStatus.mapTo {
    DomainStatus.LEGACY mapsTo ApiStatus.UNKNOWN
    DomainStatus.ACTIVE mapsTo ApiStatus.ENABLED
}
```

Configured rules run before same-name mapping, so they can handle missing destination constants or remap existing ones. Unlisted constants retain automatic same-name mapping. Using this overload opts that call out of full-set FIR validation; an unhandled missing value still throws `IllegalArgumentException` at runtime.

## Object mapping

Kotlin classes map through compiler-generated constructor calls with no reflection. Constructor parameters map from same-name source properties; nested classes, collections, maps, and enums recurse automatically:

```kotlin
val result: ApiUser = domainUser.mapper {
    displayName from it.name.uppercase()
    role from it.role.mapTo<DomainRole, ApiRole> {
        DomainRole.LEGACY mapsTo ApiRole.UNKNOWN
    }
}
```

Assignment rules override automatic properties. Automatic recursive mappings are rejected at compile time when any nested source enum constant is absent from its destination; the diagnostic includes the property path and missing constants.

`List`, `Set`, `Collection`, `Iterable`, and their mutable variants map elements recursively. `Map` and `MutableMap` map keys and values recursively. Collection element and map key/value enum pairs receive the same compile-time completeness validation as direct properties. JVM arrays are not collections and require explicit assignment.

### Automatic properties

Properties with the same name and type are passed directly to the destination constructor:

```kotlin
data class DomainProfile(val name: String, val age: Int)
data class ApiProfile(val name: String, val age: Int)

val result: ApiProfile = DomainProfile("Ada", 37).mapper()
// ApiProfile(name="Ada", age=37)
```

### Constructor defaults

A destination parameter may be absent from the source when it has a default value:

```kotlin
data class DomainSettings(val enabled: Boolean)
data class ApiSettings(
    val enabled: Boolean,
    val label: String = "default",
)

val result: ApiSettings = DomainSettings(true).mapper()
// ApiSettings(enabled=true, label="default")
```

### Property overrides

Infix `from` rules in the mapper block replace automatic values while remaining valid Kotlin in IntelliJ:

```kotlin
data class DomainUser(val firstName: String, val lastName: String, val age: Int)
data class ApiUser(val displayName: String, val age: Int)

val result: ApiUser = domainUser.mapper {
    displayName from "${it.firstName} ${it.lastName}"
    age from it.age + 1
}
```

### Nested classes

Nested constructor properties map recursively:

```kotlin
data class DomainAddress(val city: String)
data class ApiAddress(val city: String)

data class DomainUser(val name: String, val address: DomainAddress)
data class ApiUser(val name: String, val address: ApiAddress)

val result: ApiUser = domainUser.mapper()
```

### Nested enum override

Enum rules can be used inside an object assignment:

```kotlin
enum class DomainRole { USER, LEGACY }
enum class ApiRole { USER, UNKNOWN }

data class DomainUser(val role: DomainRole)
data class ApiUser(val role: ApiRole)

val result: ApiUser = domainUser.mapper {
    role from it.role.mapTo<DomainRole, ApiRole> {
        DomainRole.LEGACY mapsTo ApiRole.UNKNOWN
    }
}
```

### Lists and sets

Elements are recursively converted while the destination collection shape is preserved:

```kotlin
data class DomainItem(val name: String)
data class ApiItem(val name: String)

data class DomainCatalog(
    val featured: List<DomainItem>,
    val archived: Set<DomainItem>,
)

data class ApiCatalog(
    val featured: List<ApiItem>,
    val archived: Set<ApiItem>,
)

val result: ApiCatalog = domainCatalog.mapper()
```

Conversions between iterable collection interfaces are also supported:

```kotlin
data class DomainValues(val values: Iterable<DomainItem>)
data class ApiValues(val values: Collection<ApiItem>)

val result: ApiValues = domainValues.mapper()
```

### Mutable collections

Mutable destination types produce mutable results:

```kotlin
data class DomainBatch(
    val items: MutableList<DomainItem>,
    val uniqueItems: MutableSet<DomainItem>,
)

data class ApiBatch(
    val items: MutableList<ApiItem>,
    val uniqueItems: MutableSet<ApiItem>,
)

val result: ApiBatch = domainBatch.mapper()
result.items += ApiItem("new")
```

### Maps

Map keys and values are converted independently and recursively:

```kotlin
enum class DomainKey { PRIMARY, SECONDARY }
enum class ApiKey { PRIMARY, SECONDARY, UNKNOWN }

data class DomainIndex(val items: Map<DomainKey, DomainItem>)
data class ApiIndex(val items: Map<ApiKey, ApiItem>)

val result: ApiIndex = domainIndex.mapper()
```

`MutableMap` destinations are also supported and remain mutable.

## Compile-time failures

The compiler plugin rejects invalid automatic mappings before generated code can execute.

### Missing enum constant

```kotlin
enum class DomainStatus { ACTIVE, DISABLED }
enum class ApiStatus { ACTIVE }

val result: ApiStatus = DomainStatus.ACTIVE.mapTo()
```

Diagnostic:

```text
cannot map enum DomainStatus to ApiStatus: missing destination constants [DISABLED]
```

The compiler checks the complete source enum, not only the constant used at that call site.

### Missing nested enum constant

```kotlin
data class DomainChild(val status: DomainStatus)
data class ApiChild(val status: ApiStatus)
data class DomainParent(val child: DomainChild)
data class ApiParent(val child: ApiChild)

val result: ApiParent = domainParent.mapper()
```

Diagnostic includes the recursive path:

```text
cannot generate object mapping: child.status maps DomainStatus to ApiStatus,
missing [DISABLED]
```

### Missing enum constant in a collection

```kotlin
data class DomainItem(val status: DomainStatus)
data class ApiItem(val status: ApiStatus)
data class DomainBatch(val items: List<DomainItem>)
data class ApiBatch(val items: List<ApiItem>)

val result: ApiBatch = domainBatch.mapper()
```

The diagnostic path identifies collection traversal:

```text
items[].status maps DomainStatus to ApiStatus, missing [DISABLED]
```

The same validation applies to `Set`, `Collection`, `Iterable`, and mutable variants.

### Missing enum constant in a map

Map diagnostics distinguish keys and values:

```text
items{key} maps DomainKey to ApiKey, missing [LEGACY]
items{value}.status maps DomainStatus to ApiStatus, missing [DISABLED]
```

### Missing source property

```kotlin
data class DomainUser(val name: String)
data class ApiUser(val name: String, val age: Int)

val result: ApiUser = domainUser.mapper()
```

Compilation fails with:

```text
age has no source property
```

Adding a destination default or assigning `age` in the mapper block resolves it.

### Incompatible property types

```kotlin
data class DomainValue(val value: String)
data class ApiValue(val value: Int)

val result: ApiValue = domainValue.mapper()
```

Compilation fails with:

```text
value has incompatible types kotlin.String and kotlin.Int
```

Provide an assignment when conversion is intentional:

```kotlin
val result: ApiValue = domainValue.mapper {
    value from it.value.toInt()
}
```

### Arrays

JVM arrays and primitive arrays are not collection interfaces and are not recursively converted:

```kotlin
data class DomainArray(val items: Array<DomainItem>)
data class ApiArray(val items: Array<ApiItem>)

val result: ApiArray = domainArray.mapper() // compilation error
```

Assign arrays explicitly when needed:

```kotlin
val result: ApiArray = domainArray.mapper {
    items from it.items.map { item -> item.mapper<ApiItem>() }.toTypedArray()
}
```

### Explicit enum rules

Calls with explicit enum rules intentionally bypass full-set validation for that call because rules may handle otherwise missing constants:

```kotlin
val result: ApiStatus = domainStatus.mapTo {
    DomainStatus.DISABLED mapsTo ApiStatus.UNKNOWN
}
```

If the current value has neither an explicit rule nor a same-name destination constant, runtime fallback still throws `IllegalArgumentException`.

The FIR plugin validates both `mapTo` and direct `EnumMapper.map` calls. Kotlin compiler plugin APIs are version-specific, so this release targets Kotlin 2.3.20.

### IntelliJ highlighting

IntelliJ K2 can attempt to run the same third-party FIR plugin during editor analysis after a Gradle sync. This support is guarded by an IntelliJ registry flag:

1. Trust the project and enable Kotlin K2 mode.
2. Open **Help > Find Action > Registry**.
3. Set `kotlin.k2.only.bundled.compiler.plugins.enabled` to `false`.
4. Sync the Gradle project.

Editor loading is version-sensitive because IntelliJ bundles its own Kotlin compiler build. Gradle compilation remains the authoritative validation path when the IDE compiler is not binary-compatible with Kotlin 2.3.20.

## Build

The project uses a Java 17 toolchain. Run all Java and Kotlin interoperability tests with:

```shell
./gradlew build
```
