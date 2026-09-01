import java.security.MessageDigest
import java.util.HexFormat

plugins {
    application
}

val javafxPlatform = run {
    val operatingSystem = System.getProperty("os.name").lowercase()
    val architecture = System.getProperty("os.arch").lowercase()
    val arm = architecture.contains("aarch64") || architecture.contains("arm64")
    when {
        operatingSystem.contains("win") -> if (arm) "win-aarch64" else "win"
        operatingSystem.contains("mac") -> if (arm) "mac-aarch64" else "mac"
        operatingSystem.contains("linux") -> if (arm) "linux-aarch64" else "linux"
        else -> throw GradleException("Unsupported JavaFX platform: $operatingSystem/$architecture")
    }
}

dependencies {
    implementation(project(":ssmt-core"))
    implementation(project(":ssmt-ai"))
    implementation(project(":ssmt-validation"))
    implementation(project(":ssmt-extractor"))
    implementation(project(":ssmt-ocr"))
    implementation(project(":ssmt-plugin-manager"))
    implementation(project(":ssmt-project"))
    implementation(project(":ssmt-tm"))
    implementation(variantOf(libs.javafx.base) { classifier(javafxPlatform) })
    implementation(variantOf(libs.javafx.graphics) { classifier(javafxPlatform) })
    implementation(variantOf(libs.javafx.controls) { classifier(javafxPlatform) })
}

application {
    mainClass.set("com.ssmt.gui.GuiLauncher")
}

val packageRoot = layout.buildDirectory.dir("jpackage")
val appImage = packageRoot.map { it.dir("Project Go") }
val hostOs = System.getProperty("os.name").lowercase()
val jpackageExecutable = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(25))
}.map { launcher ->
    launcher.executablePath.asFile.parentFile.resolve(
        if (hostOs.contains("win")) "jpackage.exe" else "jpackage")
}
val nativePackageType = when {
    hostOs.contains("win") -> "exe"
    hostOs.contains("mac") -> "dmg"
    else -> "deb"
}
val packageIcon = if (hostOs.contains("win")) {
    layout.projectDirectory.file("src/main/resources/com/ssmt/gui/ssmt-icon.ico")
} else {
    layout.projectDirectory.file("src/main/resources/com/ssmt/gui/ssmt-icon.png")
}

tasks.register<Exec>("jpackageImage") {
    group = "distribution"
    description = "Builds a self-contained native application image with jpackage."
    dependsOn(tasks.named("installDist"))
    inputs.dir(layout.buildDirectory.dir("install/ssmt-gui"))
    inputs.file(packageIcon)
    outputs.dir(appImage)
    doFirst {
        delete(appImage)
        commandLine(
            jpackageExecutable.get(),
            "--type", "app-image",
            "--input", layout.buildDirectory.dir("install/ssmt-gui/lib").get().asFile,
            "--dest", packageRoot.get().asFile,
            "--name", "Project Go",
            "--main-jar", "ssmt-gui-${project.version}.jar",
            "--main-class", "com.ssmt.gui.GuiLauncher",
            "--app-version", project.version.toString(),
            "--vendor", "Project Go Contributors",
            "--description", "Personal-use Starsector translation tool",
            "--copyright", "Copyright 2026 Project Go Contributors",
            "--icon", packageIcon.asFile
        )
    }
}

tasks.register<Exec>("smokeTestAppImage") {
    group = "verification"
    description = "Launches the self-contained application image in headless smoke-test mode."
    dependsOn(tasks.named("jpackageImage"))
    inputs.dir(appImage)
    doFirst {
        val launcher = if (hostOs.contains("win")) {
            appImage.get().file("Project Go.exe").asFile
        } else {
            appImage.get().file("bin/Project Go").asFile
        }
        commandLine(launcher.absolutePath, "--smoke-test")
    }
}

val developmentBundle by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Creates a self-contained Windows development-testing bundle."
    dependsOn(tasks.named("smokeTestAppImage"))
    dependsOn(":ssmt-auto:smokeTestAppImage")
    archiveFileName.set("Project-Go-${project.version}-windows-x64.zip")
    destinationDirectory.set(rootProject.layout.buildDirectory.dir("development"))
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
    into("Project Go") {
        from(appImage)
    }
    into("Project Go Auto") {
        from(project(":ssmt-auto").layout.buildDirectory.dir("jpackage/Project Go Auto"))
    }
    into("documentation") {
        from(rootProject.file("USER_GUIDE.md"))
        from(rootProject.file("BEGINNERS_GUIDE.md"))
        from(rootProject.file("AUTO_GUIDE.md"))
        from(rootProject.file("SECURITY.md"))
        from(rootProject.file("COMPATIBILITY_MATRIX.md"))
    }
}

tasks.register("developmentBundleChecksum") {
    group = "distribution"
    description = "Writes the SHA-256 checksum for the development bundle."
    dependsOn(developmentBundle)
    val archive = developmentBundle.flatMap { it.archiveFile }
    val checksum = rootProject.layout.buildDirectory.file(
        "development/Project-Go-${project.version}-windows-x64.zip.sha256")
    inputs.file(archive)
    outputs.file(checksum)
    doLast {
        val bytes = archive.get().asFile.readBytes()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
        checksum.get().asFile.writeText(
            HexFormat.of().formatHex(hash)
                    + "  " + archive.get().asFile.name + "\n",
            Charsets.UTF_8)
    }
}

tasks.register<Exec>("jpackageInstaller") {
    group = "distribution"
    description = "Builds the host-native installer; platform packaging tools are required."
    dependsOn(tasks.named("jpackageImage"))
    inputs.dir(appImage)
    outputs.dir(packageRoot.map { it.dir("installer") })
    doFirst {
        val destination = packageRoot.get().dir("installer").asFile
        delete(destination)
        destination.mkdirs()
        val arguments = mutableListOf(
            "--type", nativePackageType,
            "--app-image", appImage.get().asFile.absolutePath,
            "--dest", destination.absolutePath,
            "--name", "Project Go",
            "--app-version", project.version.toString(),
            "--vendor", "Project Go Contributors"
        )
        if (hostOs.contains("win")) {
            arguments.addAll(listOf(
                "--win-dir-chooser",
                "--win-menu",
                "--win-menu-group", "Project Go",
                "--win-shortcut"
            ))
        }
        commandLine(jpackageExecutable.get(), *arguments.toTypedArray())
    }
}

tasks.register<Exec>("signNativePackage") {
    group = "distribution"
    description = "Optionally signs a native package using host tooling and Gradle properties."
    doFirst {
        val packageFile = providers.gradleProperty("packageFile").orNull
            ?: throw GradleException("Set -PpackageFile to the native package to sign")
        val identity = providers.gradleProperty("signingIdentity").orNull
            ?: throw GradleException("Set -PsigningIdentity without storing it in the repository")
        when {
            hostOs.contains("win") -> {
                val timestamp = providers.gradleProperty("timestampUrl")
                    .getOrElse("http://timestamp.digicert.com")
                commandLine(
                    "signtool", "sign",
                    "/sha1", identity,
                    "/fd", "sha256",
                    "/tr", timestamp,
                    "/td", "sha256",
                    packageFile)
            }
            hostOs.contains("mac") ->
                commandLine("codesign", "--force", "--options", "runtime",
                    "--sign", identity, packageFile)
            else -> throw GradleException(
                "Use the distribution channel's package-signing process on Linux")
        }
    }
}
