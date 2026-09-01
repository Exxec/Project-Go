import com.github.spotbugs.snom.SpotBugsTask
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.artifacts.VersionCatalogsExtension
import java.security.MessageDigest
import java.util.HexFormat
import java.util.zip.ZipFile

plugins {
    base
    checkstyle
    id("com.github.spotbugs") version "6.5.9" apply false
}

val libraries = extensions.getByType<VersionCatalogsExtension>().named("libs")

allprojects {
    group = "com.ssmt"
    version = providers.gradleProperty("ssmtVersion").get()

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "checkstyle")
    apply(plugin = "com.github.spotbugs")

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    dependencies {
        add("testImplementation", libraries.findLibrary("junit-jupiter").get())
        add("testImplementation", libraries.findLibrary("assertj-core").get())
        add("testImplementation", libraries.findLibrary("mockito-core").get())
        add("testImplementation", libraries.findLibrary("mockito-junit-jupiter").get())
        add("testRuntimeOnly", libraries.findLibrary("junit-platform-launcher").get())
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    tasks.withType<Jar>().configureEach {
        manifest {
            attributes["Implementation-Version"] = project.version.toString()
        }
    }

    checkstyle {
        toolVersion = "10.20.2"
        configFile = rootProject.file("config/checkstyle/checkstyle.xml")
        isIgnoreFailures = false
    }

    tasks.withType<SpotBugsTask>().configureEach {
        reports.create("html") {
            required = true
        }
        reports.create("xml") {
            required = false
        }
    }
}

val generateSbom by tasks.registering {
    group = "distribution"
    description = "Generates a deterministic CycloneDX-compatible dependency inventory."
    val destination = layout.buildDirectory.file("reports/ssmt-sbom.cdx.json")
    outputs.file(destination)
    doLast {
        val declaredLibraries = libraries.libraryAliases.map { alias ->
            val dependency = libraries.findLibrary(alias).get().get()
            val module = dependency.module
            val dependencyVersion = dependency.versionConstraint.requiredVersion
            mapOf(
                "type" to "library",
                "group" to module.group,
                "name" to module.name,
                "version" to dependencyVersion,
                "purl" to "pkg:maven/${module.group}/${module.name}@$dependencyVersion")
        }
        val components = (declaredLibraries + subprojects.map { child ->
            mapOf(
                "type" to "library",
                "group" to project.group.toString(),
                "name" to child.name,
                "version" to project.version.toString())
        }).distinctBy { component ->
            "${component["group"]}:${component["name"]}:${component["version"]}"
        }.sortedBy { component ->
            "${component["group"]}:${component["name"]}:${component["version"]}"
        }
        val json = groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(mapOf(
            "bomFormat" to "CycloneDX",
            "specVersion" to "1.5",
            "version" to 1,
            "metadata" to mapOf(
                "component" to mapOf(
                    "type" to "application",
                    "group" to project.group.toString(),
                    "name" to "ssmt",
                    "version" to project.version.toString())),
            "components" to components)))
        val output = destination.get().asFile
        output.parentFile.mkdirs()
        output.writeText(json + "\n", Charsets.UTF_8)
    }
}

val releaseChecksums by tasks.registering {
    group = "distribution"
    description = "Writes deterministic SHA-256 checksums for release ZIP archives."
    dependsOn(":ssmt-cli:distZip", ":ssmt-gui:distZip")
    val destination = layout.buildDirectory.file("distributions/SHA256SUMS")
    outputs.file(destination)
    doLast {
        val archives = listOf(
            project(":ssmt-cli").layout.buildDirectory
                .file("distributions/ssmt-cli-${project.version}.zip").get().asFile,
            project(":ssmt-gui").layout.buildDirectory
                .file("distributions/ssmt-gui-${project.version}.zip").get().asFile)
                .sortedBy { it.name }
        val digest = MessageDigest.getInstance("SHA-256")
        val lines = archives.map { archive ->
            "${HexFormat.of().formatHex(digest.digest(archive.readBytes()))}  ${archive.name}"
        }
        val output = destination.get().asFile
        output.parentFile.mkdirs()
        output.writeText(lines.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
    }
}

val scanReleaseArchives by tasks.registering {
    group = "verification"
    description = "Rejects unsafe or unexpectedly large release ZIP entry layouts."
    dependsOn(":ssmt-cli:distZip", ":ssmt-gui:distZip")
    doLast {
        val archives = listOf(
            project(":ssmt-cli").layout.buildDirectory
                .file("distributions/ssmt-cli-${project.version}.zip").get().asFile,
            project(":ssmt-gui").layout.buildDirectory
                .file("distributions/ssmt-gui-${project.version}.zip").get().asFile)
        archives.forEach { archive ->
            ZipFile(archive).use { zip ->
                val entries = zip.entries().asSequence().toList()
                if (entries.size > 100_000) {
                    throw GradleException("Archive entry count exceeds limit: ${archive.name}")
                }
                val names = mutableSetOf<String>()
                var expandedBytes = 0L
                entries.forEach { entry ->
                    val name = entry.name
                    if (name.startsWith("/")
                        || name.startsWith("\\")
                        || name.contains("\\")
                        || name.split('/').contains("..")
                        || !names.add(name)) {
                        throw GradleException(
                            "Unsafe or duplicate archive entry in ${archive.name}: $name")
                    }
                    if (entry.size > 0) {
                        expandedBytes = Math.addExact(expandedBytes, entry.size)
                    }
                }
                if (expandedBytes > 2L * 1024L * 1024L * 1024L) {
                    throw GradleException("Expanded archive exceeds limit: ${archive.name}")
                }
            }
        }
    }
}

val checkReleaseMetadata by tasks.registering {
    group = "verification"
    description = "Verifies semantic versioning and archive naming."
    inputs.file("gradle.properties")
    doLast {
        val releaseVersion = project.version.toString()
        if (!releaseVersion.matches(Regex("""\d+\.\d+\.\d+([-.][0-9A-Za-z.-]+)?"""))) {
            throw GradleException("ssmtVersion is not semantic: $releaseVersion")
        }
        val expected = setOf(
            "ssmt-cli-$releaseVersion.zip",
            "ssmt-gui-$releaseVersion.zip")
        val actual = listOf(
            project(":ssmt-cli").layout.buildDirectory
                .file("distributions/ssmt-cli-$releaseVersion.zip").get().asFile.name,
            project(":ssmt-gui").layout.buildDirectory
                .file("distributions/ssmt-gui-$releaseVersion.zip").get().asFile.name)
                .toSet()
        if (actual != expected) {
            throw GradleException("Release archive naming is inconsistent")
        }
    }
}
