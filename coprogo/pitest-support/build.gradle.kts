plugins {
    kotlin("jvm")
    id("org.jlleitschuh.gradle.ktlint")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.4")

    compileOnly("org.pitest:pitest-entry:1.30.1")
    compileOnly("org.pitest:pitest:1.30.1")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testImplementation("org.pitest:pitest-entry:1.30.1")
    testImplementation("org.pitest:pitest:1.30.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
}

tasks.test {
    useJUnitPlatform()
}
