dependencies {
    implementation(project(":ssmt-core"))
    implementation(project(":ssmt-ai"))
    implementation(project(":ssmt-scanner"))
    implementation(project(":ssmt-extractor"))
    implementation(project(":ssmt-validation"))
    implementation(project(":ssmt-patcher"))
    implementation(project(":ssmt-tm"))
    implementation(libs.jackson.databind)
}
