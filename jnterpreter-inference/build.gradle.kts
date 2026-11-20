plugins {
    kotlin("jvm") version "2.4.21"
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
        allWarningsAsErrors = true
    }
}

dependencies {
    // The public API exposes core types (Span, LangError), so consumers
    // need core on their compile classpath.
    api(project(":jnterpreter-core"))
    // Java consumers of this module need the standard library on their
    // classpath, and the public API may expose Kotlin types.
    api("org.jetbrains.kotlin:kotlin-stdlib:2.4.21")
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.4")
    // Gradle's test worker loads JUnit Platform through the launcher, which
    // junit-jupiter does NOT bring in transitively. Without this, :test
    // fails with "Failed to load JUnit Platform" (proven by CI run #10).
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.14.4")
}
