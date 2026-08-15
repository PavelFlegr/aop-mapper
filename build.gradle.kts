plugins {
    kotlin("jvm") version "2.3.20"
}

group = "cz.pavelflegr.remap"
version = "1.0.0-SNAPSHOT"

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
