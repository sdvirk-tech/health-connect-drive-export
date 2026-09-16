pluginManagement {
    val spec = System.getProperty("java.specification.version") ?: "0"
    val major = spec.removePrefix("1.").substringBefore(".").toIntOrNull() ?: 0
    if (major >= 25) {
        throw GradleException(
            "Gradle 8.11 cannot run on Java $major (${System.getProperty("java.home")}). " +
                "Use JDK 17, not Android Studio JBR 25. On Windows run .\\build-install.cmd"
        )
    }
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "HealthConnectDriveExport"
include(":shared")
include(":app")
include(":wear")
