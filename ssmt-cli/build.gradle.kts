plugins {
    application
}

dependencies {
    implementation(project(":ssmt-ai"))
    implementation(project(":ssmt-core"))
    implementation(project(":ssmt-scanner"))
    implementation(project(":ssmt-extractor"))
    implementation(project(":ssmt-tm"))
    implementation(project(":ssmt-validation"))
    implementation(project(":ssmt-patcher"))
    implementation(project(":ssmt-project"))
    implementation(libs.picocli)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)
}

application {
    mainClass.set("com.ssmt.cli.Main")
}

val generateVersionResource by tasks.registering {
    val destination = layout.buildDirectory.file("generated/version/ssmt-version.properties")
    outputs.file(destination)
    doLast {
        val output = destination.get().asFile
        output.parentFile.mkdirs()
        output.writeText("version=${project.version}\n", Charsets.UTF_8)
    }
}

tasks.processResources {
    dependsOn(generateVersionResource)
    from(generateVersionResource)
}
