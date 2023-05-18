/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

plugins {
    kotlin("jvm") version(libs.versions.kotlin)
    id("application")
    libs.plugins.ktlint
    libs.plugins.kover
}

val version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":ion-schema"))
    implementation(libs.ionjava)
    implementation(libs.ionelement)
    implementation(libs.clikt)
    testImplementation(libs.test.kotlintest)
    testImplementation(libs.test.kotest.assertions)
    testImplementation(libs.bundles.test.junit5)
}

application {
    mainClass.set("com.amazon.ionschema.cli.MainKt")
    applicationName = "ion-schema-cli" // startup script name
}

tasks {
    named<JavaExec>("run") {
        standardInput = System.`in`
    }
}
