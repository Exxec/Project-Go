plugins {
    application
}

dependencies {
    implementation(project(":ssmt-core"))
    implementation(project(":ssmt-scanner"))
    implementation(project(":ssmt-project"))
    implementation(project(":ssmt-tm"))
    implementation(libs.jackson.databind)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)
}

application {
    mainClass.set("com.ssmt.auto.AutoMain")
}

val packageRoot = layout.buildDirectory.dir("jpackage")
val appImage = packageRoot.map { it.dir("SSMT Auto") }
val hostOs = System.getProperty("os.name").lowercase()
val jpackageExecutable = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(25))
}.map { launcher ->
    launcher.executablePath.asFile.parentFile.resolve(
        if (hostOs.contains("win")) "jpackage.exe" else "jpackage")
}
val packageIcon = rootProject.layout.projectDirectory.file(
    "ssmt-gui/src/main/resources/com/ssmt/gui/ssmt-icon.ico")

tasks.register<Exec>("jpackageImage") {
    group = "distribution"
    description = "Builds the self-contained drag-and-drop automation application."
    dependsOn(tasks.named("installDist"))
    inputs.dir(layout.buildDirectory.dir("install/ssmt-auto"))
    inputs.file(packageIcon)
    outputs.dir(appImage)
    doFirst {
        delete(appImage)
        commandLine(
            jpackageExecutable.get(),
            "--type", "app-image",
            "--input", layout.buildDirectory.dir("install/ssmt-auto/lib").get().asFile,
            "--dest", packageRoot.get().asFile,
            "--name", "SSMT Auto",
            "--main-jar", "ssmt-auto-${project.version}.jar",
            "--main-class", "com.ssmt.auto.AutoMain",
            "--app-version", project.version.toString(),
            "--vendor", "SSMT Contributors",
            "--description", "Headless drag-and-drop Starsector localization workflow",
            "--icon", packageIcon.asFile,
            "--win-console"
        )
    }
    onlyIf { hostOs.contains("win") }
}

tasks.register<Exec>("smokeTestAppImage") {
    group = "verification"
    description = "Runs the packaged automation executable in smoke mode."
    dependsOn(tasks.named("jpackageImage"))
    inputs.dir(appImage)
    doFirst {
        commandLine(appImage.get().file("SSMT Auto.exe").asFile, "--smoke-test")
    }
    onlyIf { hostOs.contains("win") }
}
