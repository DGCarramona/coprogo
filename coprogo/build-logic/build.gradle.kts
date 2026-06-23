plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("org.flywaydb:flyway-core:11.16.0")
    implementation("org.flywaydb:flyway-database-postgresql:11.16.0")
    implementation("org.jooq:jooq-codegen:3.21.4")
    implementation("org.postgresql:postgresql:42.7.8")
}

gradlePlugin {
    plugins {
        register("coprogoJooqCodegen") {
            id = "coprogo.jooq-codegen"
            implementationClass = "tech.justdev.build.CoprogoJooqCodegenPlugin"
        }
    }
}
