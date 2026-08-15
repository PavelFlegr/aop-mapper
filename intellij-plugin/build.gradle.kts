import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.0"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "me.flegr.remap"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        val localIntellijPath = providers.gradleProperty("intellijPlatformPath").orNull
        if (localIntellijPath == null) {
            intellijIdea("2026.2.1")
        } else {
            local(localIntellijPath)
        }
        bundledPlugin("org.jetbrains.kotlin")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
    testImplementation(kotlin("test"))
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_25
    }
}

intellijPlatform {
    pluginConfiguration {
        changeNotes = """
            <h3>0.1.0</h3>
            <ul>
                <li>Live validation for Remap enum and object mappings.</li>
                <li>Diagnostics for nested objects, collections, maps, and property overrides.</li>
            </ul>
        """.trimIndent()
        ideaVersion {
            sinceBuild = "262.9437"
            untilBuild = "262.*"
        }
    }
    publishing {
        token.set(
            providers.environmentVariable("JETBRAINS_MARKETPLACE_TOKEN")
                .orElse(providers.gradleProperty("jetbrainsMarketplaceToken")),
        )
    }
    signing {
        certificateChain.set(providers.environmentVariable("JETBRAINS_CERTIFICATE_CHAIN"))
        privateKey.set(providers.environmentVariable("JETBRAINS_PRIVATE_KEY"))
        password.set(providers.environmentVariable("JETBRAINS_PRIVATE_KEY_PASSWORD"))
    }
}
