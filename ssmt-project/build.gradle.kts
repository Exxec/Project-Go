dependencies {
    implementation(project(":ssmt-core"))
    implementation(project(":ssmt-ai"))
    implementation(project(":ssmt-scanner"))
    implementation(project(":ssmt-extractor"))
    implementation(project(":ssmt-validation"))
    implementation(project(":ssmt-patcher"))
    implementation(project(":ssmt-tm"))
    implementation(libs.jackson.databind)
    implementation(libs.asm)
    testImplementation(libs.asm)
}

val realModAcceptance by tasks.registering(Test::class) {
    group = "verification"
    description = "Measures policy approval on a copied real mod; leaves owner review and live gates open."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter { includeTestsMatching("*RealModAcceptanceTest") }
    testLogging.showStandardStreams = true
    for (name in listOf("source", "out", "drafts")) {
        systemProperty("ssmt.realmod.$name", providers.gradleProperty("realMod." + name).orElse("").get())
    }
    doFirst {
        require(providers.gradleProperty("realMod.source").isPresent && providers.gradleProperty("realMod.out").isPresent) {
            "Give -PrealMod.source=DIR and -PrealMod.out=NEW_DIR; optionally -PrealMod.drafts=DOCUMENT"
        }
    }
    outputs.upToDateWhen { false }
}
