
plugins {
    kotlin("jvm") version "1.6.20"
    java
    `maven-publish`
    jacoco
    // id("org.jetbrains.kotlinx.kover") version "0.7.0"
    signing
    id("org.cyclonedx.bom") version "1.7.2"
    id("com.github.spotbugs") version "5.0.13"
    id("org.jlleitschuh.gradle.ktlint") version "10.0.0"
    // TODO: more static analysis. E.g.:
    // id("com.diffplug.spotless") version "6.11.0"
}

repositories {
    mavenCentral()
}

allprojects {
    apply plugin: "org.jlleitschuh.gradle.ktlint"
    ktlint {
        outputToConsole = true
    }

    apply plugin: "jacoco"
    jacoco {
        toolVersion = '0.8.10'
        reportsDirectory = file("$buildDir/reports/jacoco/")
    }
}