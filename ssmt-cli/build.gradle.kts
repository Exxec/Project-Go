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
    implementation(libs.jackson.databind)
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


// Quote assignments as well as invocation so spaces/metacharacters survive cmd.exe expansion.
tasks.startScripts {
    inputs.file("src/main/scripts/ssmt-cli.ps1")
    doLast {
        val script = windowsScript
        val original = script.readText(Charsets.UTF_8)
        val quoted = Regex("(?m)^set ([A-Z_]+)=(.*)$").replace(original) { match ->
            "set \"${match.groupValues[1]}=${match.groupValues[2].trimEnd('\r')}\""
        }.replace("%JAVA_EXE% -version", "\"%JAVA_EXE%\" -version")
            .replace("do set APP_HOME=%%~fi", "do set \"APP_HOME=%%~fi\"")
        val delegated = quoted.lineSequence().joinToString("\r\n") { line ->
            if (line.contains("com.ssmt.cli.Main %*")) {
                "powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File \"%APP_HOME%\\bin\\ssmt-cli.ps1\" %*"
            } else {
                line.trimEnd('\r')
            }
        }
        script.writeText("@echo off\r\n@chcp 65001 >nul\r\n" + delegated, Charsets.UTF_8)
        windowsScript.parentFile.resolve("ssmt-cli.ps1").writeText(
            file("src/main/scripts/ssmt-cli.ps1").readText(Charsets.UTF_8), Charsets.UTF_8)
    }
}

val verifyWindowsLauncher by tasks.registering(Exec::class) {
    dependsOn(tasks.installDist)
    onlyIf { System.getProperty("os.name").startsWith("Windows") }
    commandLine("powershell.exe", "-NoLogo", "-NoProfile", "-ExecutionPolicy", "Bypass",
        "-File", file("src/test/scripts/verify-windows-launcher.ps1").absolutePath,
        "-Launcher", layout.buildDirectory.file("install/ssmt-cli/bin/ssmt-cli.bat").get().asFile.absolutePath,
        "-Fixture", rootProject.file("fixtures/translation-conformance/input").absolutePath)
}
tasks.check { dependsOn(verifyWindowsLauncher) }
