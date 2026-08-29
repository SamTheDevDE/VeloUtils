pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") { name = "PaperMC" }
        maven("https://repo.extendedclip.com/releases/") {
            name = "PlaceholderAPI"
            content { includeGroup("me.clip") }
        }
    }
}

rootProject.name = "VeloUtils"

include(
    "veloutils-api",
    "veloutils-common",
    "veloutils-core",
    "veloutils-protocol",
    "veloutils-proxy",
    "veloutils-bridge",
)
