dependencies {
    implementation(project(":ssmt-core"))
    implementation(libs.jackson.databind)
    implementation(libs.slf4j.api)

    testRuntimeOnly(libs.logback.classic)
}
