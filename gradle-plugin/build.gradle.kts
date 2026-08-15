plugins {
    `java-gradle-plugin`
    kotlin("jvm") version "2.3.20"
}

group = "cz.pavelflegr.remap"
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
        create("remap") {
            id = "cz.pavelflegr.remap"
            implementationClass = "cz.pavelflegr.remap.gradle.RemapGradlePlugin"
            displayName = "Remap"
            description = "Enables Kotlin FIR validation for enum mappings"
        }
    }
}

tasks.jar {
    manifest.attributes["Implementation-Version"] = project.version
}
