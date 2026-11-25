// A two-module Gradle build: the library and the sample app that drives it.
// Repositories live only here; a project-level repositories {} block is refused.
import org.gradle.api.initialization.resolve.RepositoriesMode

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
    }
}

rootProject.name = "blackview-autostart"

include(":lib")
include(":sample")
