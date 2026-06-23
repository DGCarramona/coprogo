pluginManagement {
    includeBuild("build-logic")

    val kotlinVersion: String by settings
    val kspVersion: String by settings
    val ktlintGradlePluginVersion: String by settings
    val micronautGradlePluginVersion: String by settings
    val shadowGradlePluginVersion: String by settings

    repositories {
        gradlePluginPortal()
        mavenCentral()
    }

    plugins {
        id("org.jetbrains.kotlin.jvm") version kotlinVersion
        id("org.jetbrains.kotlin.plugin.allopen") version kotlinVersion
        id("org.jlleitschuh.gradle.ktlint") version ktlintGradlePluginVersion
        id("com.google.devtools.ksp") version kspVersion
        id("io.micronaut.application") version micronautGradlePluginVersion
        id("com.gradleup.shadow") version shadowGradlePluginVersion
        id("io.micronaut.test-resources") version micronautGradlePluginVersion
        id("io.micronaut.aot") version micronautGradlePluginVersion
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "coprogo"
