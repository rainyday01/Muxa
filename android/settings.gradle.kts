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

rootProject.name = "m-tmux"

include(
    ":app",
    ":core:ui",
    ":core:data",
    ":core:model",
    ":feature:sessions",
    ":feature:hosts",
    ":feature:settings",
    ":feature:terminal",
    ":ssh",
    ":mosh",
    ":termux-emulator",
    ":termux-view",
    ":autocomplete",
    ":desktop-push-api",
    ":command-palette-api",
)
