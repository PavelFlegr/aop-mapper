plugins {
    `java-gradle-plugin`
    kotlin("jvm") version "2.3.20"
}

group = "io.github.enummapper"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin-api:2.3.20")
}

kotlin {
    jvmToolchain(17)
}

gradlePlugin {
    plugins {
        create("enumMapper") {
            id = "io.github.enummapper"
            implementationClass = "io.github.enummapper.gradle.EnumMapperGradlePlugin"
            displayName = "Enum Mapper"
            description = "Enables Kotlin FIR validation for enum mappings"
        }
    }
}

tasks.jar {
    manifest.attributes["Implementation-Version"] = project.version
}
