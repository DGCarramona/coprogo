package tech.justdev.build

import org.flywaydb.core.Flyway
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.GradleException
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.jooq.codegen.GenerationTool
import org.jooq.meta.jaxb.Configuration
import org.jooq.meta.jaxb.Database
import org.jooq.meta.jaxb.ForcedType
import org.jooq.meta.jaxb.Generate
import org.jooq.meta.jaxb.Generator
import org.jooq.meta.jaxb.Jdbc
import org.jooq.meta.jaxb.Target
import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.io.path.relativeTo
import kotlin.streams.asSequence

class CoprogoJooqCodegenPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val generatedJooqDir = project.layout.projectDirectory.dir("src/generated/jooq")
        val migrationDir = project.layout.projectDirectory.dir("src/main/resources/db/migration")
        val migrationFiles = project.fileTree(migrationDir.asFile) { include("*.sql") }

        project.plugins.withId("java") {
            project.extensions.getByType<SourceSetContainer>().named("main") {
                java.srcDir(generatedJooqDir)
            }
        }

        project.tasks.register("flywayCleanCodegen") {
            group = "jOOQ"
            description = "Clean the dedicated coprogo_codegen database schema."
            doLast { cleanCodegenDatabase(databaseConfig(), migrationDir.asFile) }
        }

        project.tasks.register("flywayMigrateCodegen") {
            group = "jOOQ"
            description = "Apply Flyway migrations to the dedicated coprogo_codegen database."
            inputs.files(migrationFiles)
            doLast { migrateCodegenDatabase(databaseConfig(), migrationDir.asFile) }
        }

        project.tasks.register("regenerateJooq") {
            group = "jOOQ"
            description = "Migrate coprogo_codegen and regenerate committed jOOQ sources."
            inputs.files(migrationFiles)
            outputs.dir(generatedJooqDir)
            doLast {
                val database = databaseConfig()
                migrateCodegenDatabase(database, migrationDir.asFile)
                generateJooq(database, generatedJooqDir.asFile)
            }
        }

        project.tasks.register("regenerateJooqFromScratch") {
            group = "jOOQ"
            description = "Clean coprogo_codegen, migrate it, and regenerate committed jOOQ sources."
            inputs.files(migrationFiles)
            outputs.dir(generatedJooqDir)
            doLast {
                val database = databaseConfig()
                cleanCodegenDatabase(database, migrationDir.asFile)
                migrateCodegenDatabase(database, migrationDir.asFile)
                generateJooq(database, generatedJooqDir.asFile)
            }
        }

        project.tasks.register("verifyJooqIsUpToDate") {
            group = "verification"
            description = "Verify committed jOOQ sources match migrations regenerated from coprogo_codegen."
            inputs.files(migrationFiles)
            inputs.dir(generatedJooqDir)
            doLast {
                val database = databaseConfig()
                val verificationDir = project.layout.buildDirectory.dir("jooq-verification/generated").get().asFile
                cleanCodegenDatabase(database, migrationDir.asFile)
                migrateCodegenDatabase(database, migrationDir.asFile)
                generateJooq(database, verificationDir)
                assertDirectoriesEqual(generatedJooqDir.asFile, verificationDir)
            }
        }
    }

    private fun databaseConfig(): DatabaseConfig =
        DatabaseConfig(
            host = System.getenv("DB_HOST") ?: "localhost",
            port = System.getenv("DB_PORT") ?: "5432",
            applicationDatabase = System.getenv("DB_NAME") ?: "coprogo",
            codegenDatabase = System.getenv("DB_CODEGEN_NAME") ?: "coprogo_codegen",
            username = System.getenv("DB_USERNAME") ?: "coprogo",
            password = System.getenv("DB_PASSWORD") ?: "coprogo",
        )

    private fun cleanCodegenDatabase(
        database: DatabaseConfig,
        migrationDir: File,
    ) {
        database.ensureCodegenDatabaseExists()
        Flyway
            .configure()
            .cleanDisabled(false)
            .dataSource(database.codegenJdbcUrl, database.username, database.password)
            .locations("filesystem:${migrationDir.absolutePath}")
            .load()
            .clean()
    }

    private fun migrateCodegenDatabase(
        database: DatabaseConfig,
        migrationDir: File,
    ) {
        database.ensureCodegenDatabaseExists()
        Flyway
            .configure()
            .dataSource(database.codegenJdbcUrl, database.username, database.password)
            .locations("filesystem:${migrationDir.absolutePath}")
            .load()
            .migrate()
    }

    private fun generateJooq(
        database: DatabaseConfig,
        outputDirectory: File,
    ) {
        outputDirectory.deleteRecursively()
        GenerationTool.generate(jooqConfiguration(database, outputDirectory))
    }

    private fun jooqConfiguration(
        database: DatabaseConfig,
        outputDirectory: File,
    ): Configuration =
        Configuration()
            .withJdbc(
                Jdbc()
                    .withDriver("org.postgresql.Driver")
                    .withUrl(database.codegenJdbcUrl)
                    .withUser(database.username)
                    .withPassword(database.password),
            ).withGenerator(
                Generator()
                    .withDatabase(
                        Database()
                            .withName("org.jooq.meta.postgres.PostgresDatabase")
                            .withInputSchema("public")
                            .withIncludes(".*")
                            .withExcludes("flyway_schema_history")
                            .withForcedTypes(
                                forcedType("java.lang.String", "member_email_address"),
                                forcedType("java.lang.Long", "money_amount_cents|positive_money_amount_cents|net_money_delta_cents"),
                                forcedType("java.lang.Integer", "ownership_basis_points"),
                            ),
                    ).withGenerate(
                        Generate()
                            .withRecords(false)
                            .withPojos(false)
                            .withDaos(false)
                            .withImplicitJoinPathsToOne(false)
                            .withImplicitJoinPathsToMany(false)
                            .withImplicitJoinPathsManyToMany(false)
                            .withFluentSetters(false)
                            .withJavaTimeTypes(true),
                    ).withTarget(
                        Target()
                            .withPackageName("tech.justdev.infrastructure.persistence.jooq")
                            .withDirectory(outputDirectory.absolutePath),
                    ),
            )

    private fun assertDirectoriesEqual(
        expected: File,
        actual: File,
    ) {
        val expectedFiles = expected.relativeRegularFiles()
        val actualFiles = actual.relativeRegularFiles()

        val missing = expectedFiles.keys - actualFiles.keys
        val extra = actualFiles.keys - expectedFiles.keys
        val changed =
            (expectedFiles.keys intersect actualFiles.keys)
                .filter { relativePath -> !expectedFiles.getValue(relativePath).readBytes().contentEquals(actualFiles.getValue(relativePath).readBytes()) }

        if (missing.isNotEmpty() || extra.isNotEmpty() || changed.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Committed jOOQ sources are not up to date. Run ./gradlew -p coprogo regenerateJooqFromScratch.")
                    if (missing.isNotEmpty()) appendLine("Missing generated files: ${missing.sorted().joinToString()}")
                    if (extra.isNotEmpty()) appendLine("Unexpected generated files: ${extra.sorted().joinToString()}")
                    if (changed.isNotEmpty()) appendLine("Changed generated files: ${changed.sorted().joinToString()}")
                },
            )
        }
    }

    private fun File.relativeRegularFiles(): Map<String, File> {
        if (!exists()) return emptyMap()
        return Files.walk(toPath()).use { paths ->
            paths
                .asSequence()
                .filter { Files.isRegularFile(it) }
                .associate { path -> path.relativeTo(toPath()).toString() to path.toFile() }
        }
    }

    private fun forcedType(
        userType: String,
        includeTypes: String,
    ): ForcedType =
        ForcedType()
            .withUserType(userType)
            .withIncludeTypes(includeTypes)

    private data class DatabaseConfig(
        val host: String,
        val port: String,
        val applicationDatabase: String,
        val codegenDatabase: String,
        val username: String,
        val password: String,
    ) {
        val maintenanceJdbcUrl: String = "jdbc:postgresql://$host:$port/postgres"
        val codegenJdbcUrl: String = "jdbc:postgresql://$host:$port/$codegenDatabase"

        fun ensureCodegenDatabaseExists() {
            validateDatabaseName(codegenDatabase)
            DriverManager.getConnection(maintenanceJdbcUrl, username, password).use { connection ->
                connection.prepareStatement("select 1 from pg_database where datname = ?").use { statement ->
                    statement.setString(1, codegenDatabase)
                    statement.executeQuery().use { result ->
                        if (result.next()) return
                    }
                }
                connection.createStatement().use { statement ->
                    statement.executeUpdate("create database $codegenDatabase")
                }
            }
        }

        private fun validateDatabaseName(name: String) {
            require(name.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) { "Invalid PostgreSQL database name: $name" }
        }
    }
}
