import org.gradle.api.publish.maven.MavenPublication

plugins {
    `java-gradle-plugin`
    kotlin("jvm") version "2.3.20"
    id("com.gradle.plugin-publish") version "2.1.1"
}

group = "me.flegr.remap"
version = "0.1.0"

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
            id = "me.flegr.remap"
            implementationClass = "me.flegr.remap.gradle.RemapGradlePlugin"
            displayName = "Remap"
            description = "Enables Kotlin FIR validation for enum mappings"
            website = "https://github.com/PavelFlegr/aop-mapper"
            vcsUrl = "https://github.com/PavelFlegr/aop-mapper.git"
            tags.set(listOf("kotlin", "mapping", "compiler-plugin", "enum"))
        }
    }
}

tasks.jar {
    manifest.attributes["Implementation-Version"] = project.version
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("Remap Gradle Plugin")
            description.set("Enables the Remap Kotlin compiler plugin")
            url.set("https://github.com/PavelFlegr/aop-mapper")
            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                    distribution.set("repo")
                }
            }
            developers {
                developer {
                    id.set("PavelFlegr")
                    name.set("Pavel Flegr")
                    email.set("PavelFlegr@users.noreply.github.com")
                    organization.set("Pavel Flegr")
                    organizationUrl.set("https://github.com/PavelFlegr")
                    url.set("https://github.com/PavelFlegr")
                }
            }
            scm {
                url.set("https://github.com/PavelFlegr/aop-mapper")
                connection.set("scm:git:git://github.com/PavelFlegr/aop-mapper.git")
                developerConnection.set("scm:git:ssh://git@github.com/PavelFlegr/aop-mapper.git")
            }
        }
    }
}
