plugins {
    kotlin("jvm")
    id("org.jlleitschuh.gradle.ktlint")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.2")

    compileOnly("org.pitest:pitest-entry:1.25.8")
    compileOnly("org.pitest:pitest:1.25.8")

    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testImplementation("org.pitest:pitest-entry:1.25.8")
    testImplementation("org.pitest:pitest:1.25.8")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    from(rootProject.layout.projectDirectory.file("pitest-equivalent-mutations.json"))
}
