plugins {
    id("application")
}

application {
    mainClass = "com.reim.jnterpreter.mini.Main"
}

dependencies {
    implementation(project(":jnterpreter-core"))
    implementation(project(":jnterpreter-inference"))
}
