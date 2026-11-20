group = "io.github.reim-developer"
version = "0.4.0"

subprojects {
    apply(plugin = "java-library")

    configure<org.gradle.api.plugins.JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    repositories {
        mavenCentral()
    }

    tasks.withType<org.gradle.api.tasks.compile.JavaCompile>().configureEach {
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    }

    tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
        useJUnitPlatform()
    }

    dependencies {
        // String-form add(): the testImplementation/testRuntimeOnly
        // accessors are only generated for a script whose own project
        // applies the java plugin, and the root does not apply it to
        // itself - the same reason the subprojects block above uses
        // fully-qualified type configuration.
        add("testImplementation", "org.junit.jupiter:junit-jupiter:5.14.4")
        // Gradle's test worker loads JUnit Platform through the launcher, which
        // junit-jupiter does NOT bring in transitively. Without this, :test
        // fails with "Failed to load JUnit Platform" (proven by CI run #10).
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher:1.14.4")
    }
}
