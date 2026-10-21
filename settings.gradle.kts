pluginManagement {
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
        maven("https://jitpack.io")
    }
}

rootProject.name = "Perform"
// The phone app needs the Android SDK; a Mac building only the desktop app skips it
// with -PdesktopOnly.
if (!providers.gradleProperty("desktopOnly").isPresent) include(":app")
include(":sync-protocol")
include(":desktop")
