plugins {
    kotlin("jvm") version "2.3.20"
    id("me.flegr.remap")
}

repositories {
    mavenCentral()
}

configurations.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute(module("me.flegr.remap:remap"))
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
