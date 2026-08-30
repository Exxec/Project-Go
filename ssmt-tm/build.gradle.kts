dependencies {
    implementation(project(":ssmt-core"))
    implementation(libs.sqlite.jdbc)
    implementation(libs.hikaricp)
    implementation(libs.slf4j.api)
    implementation(libs.jackson.databind)
    implementation(libs.commons.csv)

    testRuntimeOnly(libs.logback.classic)
}
