pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// Standalone build of the Contacts app from vayun-mathur/Modern-Apps.
// Only the modules Contacts needs are included; see UPSTREAM_COMMIT for the source revision.
rootProject.name = "contacts"
include(":lint-rules")
include(":library")
include(":library:ui")
include(":library:log")
include(":library:network")
include(":sdk:games")
include(":contacts")
