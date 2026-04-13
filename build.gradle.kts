import com.github.gradle.node.npm.task.NpmTask
import org.gradle.api.tasks.wrapper.Wrapper

plugins {
    base
    id("com.github.node-gradle.node") version "7.1.0"
}

val frontendDir = layout.projectDirectory.dir("frontend")
val backendBuild = gradle.includedBuild("backend")

fun registerBackendTask(
    taskName: String,
    includedBuildTaskPath: String,
    taskGroup: String,
    taskDescription: String,
) = tasks.register(taskName) {
    group = taskGroup
    description = taskDescription
    dependsOn(backendBuild.task(includedBuildTaskPath))
}

fun registerFrontendNpmTask(
    taskName: String,
    taskDescription: String,
    vararg npmArgs: String,
) = tasks.register<NpmTask>(taskName) {
    group = "frontend"
    description = taskDescription
    dependsOn(tasks.named("npmInstall"))
    workingDir.set(frontendDir.asFile)
    args.set(npmArgs.toList())
}

node {
    // Default to the locally installed Node/npm for fast local verification.
    // CI or clean environments can opt into reproducible provisioning with:
    //   ./gradlew <task> -Pfrontend.node.download=true
    download.set(
        providers
            .gradleProperty("frontend.node.download")
            .map(String::toBoolean)
            .orElse(false),
    )
    version.set(providers.gradleProperty("frontend.node.version").orElse("24.14.0"))
    npmVersion.set(providers.gradleProperty("frontend.npm.version").orElse("11.9.0"))
    nodeProjectDir.set(frontendDir)
}

tasks.named("npmInstall") {
    group = "frontend"
    description = "Install frontend npm dependencies."
}

registerBackendTask(
    taskName = "backendClasses",
    includedBuildTaskPath = ":classes",
    taskGroup = "backend",
    taskDescription = "Compile backend classes and resolve backend dependencies.",
)
registerBackendTask(
    taskName = "backendTest",
    includedBuildTaskPath = ":test",
    taskGroup = "verification",
    taskDescription = "Run backend tests.",
)
registerBackendTask(
    taskName = "backendCheck",
    includedBuildTaskPath = ":check",
    taskGroup = "verification",
    taskDescription = "Run backend checks.",
)
registerBackendTask(
    taskName = "backendKtlintCheck",
    includedBuildTaskPath = ":ktlintCheck",
    taskGroup = "verification",
    taskDescription = "Run backend Kotlin formatting checks.",
)
registerBackendTask(
    taskName = "backendKtlintFormat",
    includedBuildTaskPath = ":ktlintFormat",
    taskGroup = "formatting",
    taskDescription = "Apply backend Kotlin formatting.",
)
registerBackendTask(
    taskName = "backendBuild",
    includedBuildTaskPath = ":build",
    taskGroup = "build",
    taskDescription = "Build the backend.",
)
registerBackendTask(
    taskName = "backendNativeCompile",
    includedBuildTaskPath = ":nativeCompile",
    taskGroup = "build",
    taskDescription = "Compile the backend native image.",
)
registerBackendTask(
    taskName = "backendClean",
    includedBuildTaskPath = ":clean",
    taskGroup = "build",
    taskDescription = "Clean backend build outputs.",
)
registerBackendTask(
    taskName = "backendRun",
    includedBuildTaskPath = ":run",
    taskGroup = "application",
    taskDescription = "Run the backend application.",
)

val frontendInstall =
    tasks.register("frontendInstall") {
        group = "frontend"
        description = "Install frontend npm dependencies."
        dependsOn(tasks.named("npmInstall"))
    }

val frontendLint =
    registerFrontendNpmTask(
        taskName = "frontendLint",
        taskDescription = "Run the frontend linter.",
        "run",
        "lint",
    )

val frontendFormatCheck =
    registerFrontendNpmTask(
        taskName = "frontendFormatCheck",
        taskDescription = "Run the frontend formatting check.",
        "run",
        "format:check",
    )

val frontendTest =
    registerFrontendNpmTask(
        taskName = "frontendTest",
        taskDescription = "Run frontend unit tests once.",
        "run",
        "test",
        "--",
        "--watch=false",
    )

val frontendBuild =
    registerFrontendNpmTask(
        taskName = "frontendBuild",
        taskDescription = "Build the frontend for production.",
        "run",
        "build",
    )

val frontendGenerateApi =
    registerFrontendNpmTask(
        taskName = "frontendGenerateApi",
        taskDescription = "Regenerate the frontend OpenAPI client.",
        "run",
        "generate:api",
    )

val frontendServe =
    registerFrontendNpmTask(
        taskName = "frontendServe",
        taskDescription = "Run the Angular development server.",
        "run",
        "start",
    )

tasks.register("bootstrap") {
    group = "build setup"
    description = "Prepare the monorepo by installing frontend dependencies and resolving backend dependencies."
    dependsOn(frontendInstall, tasks.named("backendClasses"))
}

val checkAll =
    tasks.register("checkAll") {
        group = "verification"
        description = "Run the relevant frontend and backend checks for the monorepo."
        dependsOn(tasks.named("backendCheck"), frontendFormatCheck, frontendLint, frontendTest, frontendBuild)
    }

val buildAll =
    tasks.register("buildAll") {
        group = "build"
        description = "Build the frontend and backend."
        dependsOn(tasks.named("backendBuild"), frontendBuild)
    }

tasks.register<Exec>("dev") {
    group = "application"
    description = "Start the backend and frontend development servers together."
    dependsOn(frontendInstall)
    workingDir = rootDir
    commandLine("bash", "scripts/dev-stack.sh")
}

tasks.register("cleanAll") {
    group = "build"
    description = "Clean root, frontend and backend build outputs."
    dependsOn(tasks.named("clean"), tasks.named("backendClean"))
}

tasks.named<Delete>("clean") {
    delete(frontendDir.dir("dist"))
}

tasks.named("check") {
    dependsOn(checkAll)
}

tasks.named("build") {
    dependsOn(buildAll)
}

tasks.register("test") {
    group = "verification"
    description = "Run backend tests from the root wrapper."
    dependsOn(tasks.named("backendTest"))
}

tasks.register("run") {
    group = "application"
    description = "Run the backend application from the root wrapper."
    dependsOn(tasks.named("backendRun"))
}

tasks.register("nativeCompile") {
    group = "build"
    description = "Compile the backend native image from the root wrapper."
    dependsOn(tasks.named("backendNativeCompile"))
}

tasks.register("ktlintCheck") {
    group = "verification"
    description = "Run backend Kotlin formatting checks from the root wrapper."
    dependsOn(tasks.named("backendKtlintCheck"))
}

tasks.register("ktlintFormat") {
    group = "formatting"
    description = "Apply backend Kotlin formatting from the root wrapper."
    dependsOn(tasks.named("backendKtlintFormat"))
}

tasks.named<Wrapper>("wrapper") {
    gradleVersion = "9.4.1"
    distributionType = Wrapper.DistributionType.BIN
}
