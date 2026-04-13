pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "coprogo-monorepo"

includeBuild("coprogo") {
    name = "backend"
}
