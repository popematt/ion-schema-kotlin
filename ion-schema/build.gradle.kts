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

import java.net.URL

plugins {
  id("org.jetbrains.kotlin.jvm") version "1.6.20"
  id("org.jetbrains.dokka") version "1.6.20"
  id("maven-publish")
  id("signing")
}

val version = "1.5.2-SNAPSHOT"

repositories {
  mavenCentral()
}

dependencies {
  api(libs.ionjava)
  testImplementation(libs.bundles.test.junit5)
  testImplementation(libs.test.kotlintest)
  testImplementation(libs.test.mockk)
}




tasks {
  compileKotlin {
    kotlinOptions.apiVersion = "1.3"
    kotlinOptions.languageVersion = "1.4" // Can be read by 1.3 compiler, so consumers on kotlin 1.3 are still supported.
  }


  dokkaJavadoc.configure {
    dokkaSourceSets {
      named("main") {
        includes.from("src/main/kotlin/com/amazon/ionschema/module.md")
        sourceLink {
          // URL showing where the source code can be accessed through the web browser
          remoteUrl.set(URL("https://github.com/amazon-ion/ion-schema-kotlin/blob/master/ion-schema/src/main/kotlin"))
          // Suffix which is used to append the line number to the URL. Use #L for GitHub
          remoteLineSuffix.set("#L")
        }
      }
    }
  }


  create<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets["main"].java.srcDirs)
    //from "src"
  }

  create<Jar>("javadocJar") {

  dependsOn(dokkaJavadoc)
  archiveClassifier.set("javadoc")
  from(dokkaJavadoc.outputDirectory)
}

  processResources {
    from("../ion-schema-schemas/isl/") {
      into("ion-schema-schemas/isl/")
    }
    from("../ion-schema-schemas/json/") {
      into("ion-schema-schemas/json/")
    }
  }

  publishing {
    publications {
      maven(MavenPublication) {
        artifactId = "ion-schema-kotlin"

        from(components.java)
        artifact(sourcesJar)
        artifact(javadocJar)

        pom {
          name = "Ion Schema Kotlin"
          packaging = "jar"
          url = "https://github.com/amazon-ion/ion-schema-kotlin"
          description = "Reference implementation of the Amazon Ion Schema Specification."
          scm {
            connection = "scm:git@github.com:amazon-ion/ion-schema-kotlin.git"
            developerConnection = "scm:git@github.com:amazon-ion/ion-schema-kotlin.git"
            url = "git@github.com:amazon-ion/ion-schema-kotlin.git"
          }
          licenses {
            license {
              name = "The Apache License, Version 2.0"
              url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
            }
          }
          developers {
            developer {
              name = "Amazon Ion Team"
              email = "ion-team@amazon.com"
              organization = "Amazon"
              organizationUrl = "https://github.com/amazon-ion"
            }
          }
        }
      }
    }
    repositories {
      maven {
        url = "https://aws.oss.sonatype.org/service/local/staging/deploy/maven2"
        credentials {
          username = ossrhUsername
          password = ossrhPassword
        }
      }
    }
  }

  signing {
    sign(publishing.publications.maven)
  }
}



