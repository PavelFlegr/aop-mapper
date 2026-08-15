plugins {
    kotlin("jvm") version "2.3.20"
    id("com.vanniktech.maven.publish") version "0.37.0"
}

group = "me.flegr.remap"
version = "0.1.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
    withSourcesJar()
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.3.20")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.3.20")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    dependsOn(tasks.jar)
    doFirst {
        systemProperty("remap.jar", tasks.jar.get().archiveFile.get().asFile.absolutePath)
    }
    useJUnitPlatform()
}

tasks.jar {
    manifest.attributes["Implementation-Version"] = project.version
}

mavenPublishing {
    coordinates("me.flegr.remap", "remap", project.version.toString())
    publishToMavenCentral()
    signAllPublications()

    pom {
        name.set("Remap")
        description.set("Compile-time checked enum and object mapping for Kotlin/JVM")
        inceptionYear.set("2026")
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
