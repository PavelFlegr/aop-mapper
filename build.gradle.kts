plugins {
    `java-library`
    kotlin("jvm") version "2.3.20"
}

group = "io.github.enummapper"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
    withSourcesJar()
    withJavadocJar()
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

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

tasks.test {
    dependsOn(tasks.jar)
    doFirst {
        systemProperty("enumMapper.jar", tasks.jar.get().archiveFile.get().asFile.absolutePath)
    }
    useJUnitPlatform()
}

tasks.jar {
    manifest.attributes["Implementation-Version"] = project.version
}
