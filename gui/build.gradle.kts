import org.gradle.api.artifacts.dsl.LockMode

plugins {
    kotlin("jvm")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(21)
}

dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.STRICT
}

tasks.test {
    useJUnit()
}
