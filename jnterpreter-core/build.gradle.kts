dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.4")
    // Gradle's test worker loads JUnit Platform through the launcher, which
    // junit-jupiter does NOT bring in transitively. Without this, :test
    // fails with "Failed to load JUnit Platform" (proven by CI run #10).
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.14.4")
}
