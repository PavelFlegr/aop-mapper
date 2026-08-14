plugins {
    kotlin("jvm") version "2.3.20"
    id("io.github.enummapper")
}

repositories {
    mavenCentral()
}

configurations.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute(module("io.github.enummapper:enum-mapper"))
            .using(project(":"))
    }
}

dependencies {
    implementation(project(":"))
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}
