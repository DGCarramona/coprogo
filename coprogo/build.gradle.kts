plugins {
    kotlin("jvm")
    kotlin("plugin.allopen")
    id("org.jlleitschuh.gradle.ktlint")
    id("com.google.devtools.ksp")
    id("io.micronaut.application")
    id("com.gradleup.shadow")
    id("io.micronaut.test-resources")
    id("io.micronaut.aot")
    id("coprogo.jooq-codegen")
}

val jooqVersion: String by project
val kotlinVersion: String by project
val micronautVersion: String by project

ksp {
    arg("micronaut.openapi.filename", "openapi")
}

version = "0.1"
group = "tech.justdev"

dependencies {
    ksp("io.micronaut:micronaut-http-validation")
    ksp("io.micronaut.security:micronaut-security-annotations")
    ksp("io.micronaut.serde:micronaut-serde-processor")
    ksp("io.micronaut.openapi:micronaut-openapi")

    implementation("io.micronaut.flyway:micronaut-flyway")
    implementation("io.micronaut:micronaut-http-client")
    implementation("io.micronaut:micronaut-jackson-databind")
    implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")
    implementation("io.micronaut.security:micronaut-security-jwt")
    implementation("io.micronaut.openapi:micronaut-openapi-annotations")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    implementation("io.r2dbc:r2dbc-spi")
    implementation("org.jooq:jooq:$jooqVersion")
    implementation("org.jooq:jooq-kotlin:$jooqVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.11.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")

    compileOnly("io.swagger.core.v3:swagger-annotations")

    runtimeOnly("ch.qos.logback:logback-classic")
    runtimeOnly("com.fasterxml.jackson.module:jackson-module-kotlin")
    runtimeOnly("io.micronaut.sql:micronaut-jdbc-hikari")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.postgresql:r2dbc-postgresql")

    testImplementation("io.micronaut:micronaut-http-client")
    testImplementation("io.projectreactor:reactor-core")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testResourcesService("io.micronaut:micronaut-jackson-databind:$micronautVersion")
    testResourcesService("io.micronaut.testresources:micronaut-test-resources-jdbc-postgresql")
    testResourcesService("io.micronaut.testresources:micronaut-test-resources-r2dbc-postgresql")

    aotPlugins(platform("io.micronaut.platform:micronaut-platform:$micronautVersion"))
    aotPlugins("io.micronaut.security:micronaut-security-aot")
}

application {
    mainClass = "tech.justdev.ApplicationKt"
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
    sourceCompatibility = JavaVersion.toVersion("25")
}

graalvmNative {
    toolchainDetection = true
}

micronaut {
    runtime("netty")
    testRuntime("junit5")
    testResources {
        enabled = true
    }
    processing {
        incremental(true)
        annotations("tech.justdev.*")
    }
    aot {
        optimizeServiceLoading = false
        convertYamlToJava = false
        precomputeOperations = true
        cacheEnvironment = true
        optimizeClassLoading = true
        deduceEnvironment = true
        optimizeNetty = true
        replaceLogbackXml = true
        configurationProperties.put("micronaut.security.jwks.enabled", "false")
    }
}

tasks.named("dockerfileNative") {
    setProperty("jdkVersion", "25")
}
