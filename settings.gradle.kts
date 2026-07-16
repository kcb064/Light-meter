pluginManagement {
    repositories {
        gradlePluginPortal()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "lightmeter"

include(":core")
// :app needs the Android SDK and Google's Maven repository. Setting
// LIGHTMETER_CORE_ONLY=1 allows building/testing the pure-Kotlin :core module
// on machines that have neither (restricted dev boxes, lightweight CI jobs).
if (System.getenv("LIGHTMETER_CORE_ONLY") == null) {
    include(":app")
}
