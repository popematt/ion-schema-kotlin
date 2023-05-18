


allprojects {
    repositories {
        mavenCentral()
    }

    group = rootProject.properties["group"] as String
    version = rootProject.properties["version"] as String
}
