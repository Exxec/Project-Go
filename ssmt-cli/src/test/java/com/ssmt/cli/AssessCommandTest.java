package com.ssmt.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class AssessCommandTest {
    @TempDir Path directory;

    @Test void wrapperSelectionIsRepeatableWithoutClaimingMetadataValidity() throws Exception {
        Path root = Files.createDirectories(directory.resolve("wrapper/mod"));
        Files.writeString(root.resolve("mod_info.json"), "not valid JSON");
        StringWriter first = new StringWriter();
        var command = new CommandLine(new Main());
        command.setOut(new PrintWriter(first));
        assertThat(command.execute("assess", directory.toString(), "--json")).isEqualTo(1);
        StringWriter second = new StringWriter();
        command.setOut(new PrintWriter(second));
        assertThat(command.execute("assess", directory.toString(), "--json")).isEqualTo(1);
        assertThat(first.toString()).isEqualTo(second.toString())
                .contains("\"selectedRoot\":\"wrapper/mod\"", "ASSESSMENT_ONLY",
                        "\"metadataValidity\":\"INVALID\"");
        assertThat(Files.readString(root.resolve("mod_info.json"))).isEqualTo("not valid JSON");
    }

    @Test void parsesDeclaredDependenciesDirectlyFromArchiveWithoutExtraction() throws Exception {
        Path archive = directory.resolve("mod.zip");
        try (var output = new java.util.zip.ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new java.util.zip.ZipEntry("wrapper/mod_info.json"));
            output.write(("{\"id\":\"candidate\",\"name\":\"Candidate\",\"version\":1,"
                    + "\"gameVersion\":\"0.98a\",\"dependencies\":[{\"id\":\"MagicLib\"}]}")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
        }
        byte[] before = Files.readAllBytes(archive);
        var command = new CommandLine(new Main());
        StringWriter output = new StringWriter();
        command.setOut(new PrintWriter(output));
        assertThat(command.execute("assess", archive.toString(), "--json")).isZero();
        assertThat(output.toString()).contains("\"metadataValidity\":\"VALID\"",
                "\"id\":\"MagicLib\"", "\"version\":\"1\"", "NOT_TESTED",
                "\"origin\":\"USER_SUPPLIED_ARCHIVE_UNVERIFIED\"",
                "\"archiveCoverage\":\"HASHED_CONTAINER_AND_ENTRY_INVENTORY\"",
                "\"competingInputs\":\"NOT_ASSESSED_SINGLE_INPUT_ONLY\"",
                "\"severity\":\"REVIEW\"", "NESTED_WRAPPER_SELECTED",
                "SAVE_STATE_MIGRATION_NOT_ASSESSED");
        assertThat(Files.readAllBytes(archive)).isEqualTo(before);
        assertThat(directory.resolve("wrapper")).doesNotExist();
        Path copy = Files.createDirectory(directory.resolve("directory-copy"));
        try (var zip = new java.util.zip.ZipFile(archive.toFile());
                var input = zip.getInputStream(zip.getEntry("wrapper/mod_info.json"))) {
            Files.write(copy.resolve("mod_info.json"), input.readAllBytes());
        }
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var archiveReport = mapper.readTree(output.toString());
        StringWriter directoryOutput = new StringWriter();
        command.setOut(new PrintWriter(directoryOutput));
        assertThat(command.execute("assess", copy.toString(), "--json")).isZero();
        var directoryReport = mapper.readTree(directoryOutput.toString());
        assertThat(archiveReport.path("candidateSha256").asText())
                .isEqualTo(directoryReport.path("candidateSha256").asText());
        assertThat(archiveReport.path("archiveSha256").asText()).isEqualTo(
                java.util.HexFormat.of().formatHex(
                        java.security.MessageDigest.getInstance("SHA-256").digest(before)));
        assertThat(directoryReport.path("sourceAuthority").path("origin").asText())
                .isEqualTo("USER_SUPPLIED_DIRECTORY_UNVERIFIED");
        assertThat(directoryReport.path("sourceAuthority").path("archiveCoverage").asText())
                .isEqualTo("NOT_APPLICABLE_DIRECTORY_INPUT");
    }

    @Test void comparisonFailureIncludesSeparatePackageDifferences() throws Exception {
        Path candidate = Files.createDirectory(directory.resolve("candidate"));
        Files.writeString(candidate.resolve("mod_info.json"), "{\"id\":\"candidate\"}");
        Path zip = directory.resolve("package.zip");
        try (var output = new java.util.zip.ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new java.util.zip.ZipEntry("extra.txt"));
            output.write("extra".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
        }
        var command = new CommandLine(new Main());
        StringWriter output = new StringWriter();
        command.setOut(new PrintWriter(output));
        assertThat(command.execute("assess", candidate.toString(), "--compare-zip",
                zip.toString(), "--json")).isEqualTo(1);
        assertThat(output.toString()).contains("\"missing\":[\"mod_info.json\"]",
                "\"extra\":[\"extra.txt\"]", "\"changed\":[]");
    }

    @Test void optionalCoverageReportsActualHandlerCountsAndUnsupportedFiles() throws Exception {
        Files.writeString(directory.resolve("mod_info.json"), "{\"id\":\"coverage\"}");
        Path strings = Files.createDirectories(directory.resolve("data/strings"));
        Files.writeString(strings.resolve("strings.json"), "{\"hello\":\"Hello\"}");
        Path hulls = Files.createDirectories(directory.resolve("data/hulls"));
        Files.writeString(hulls.resolve("review.ship"),
                "{hullName:'Visible',spriteName:'technical.png'}");
        Files.writeString(directory.resolve("unsupported.xyz"), "Review me");
        var before = new com.ssmt.scanner.CandidateInventory().capture(directory);
        var command = new CommandLine(new Main());
        StringWriter output = new StringWriter();
        command.setOut(new PrintWriter(output));
        assertThat(command.execute("assess", directory.toString(), "--coverage", "--json")).isZero();
        assertThat(output.toString()).contains("OBSERVED_STANDARD_EXTRACTION", "UNSUPPORTED",
                "\"strings\":1", "\"path\":\"data/strings/strings.json\"",
                "OBSERVED_REVIEW_ONLY", "json:/spriteName", "UNSELECTED_TEXT_REVIEW");
        assertThat(new com.ssmt.scanner.CandidateInventory().capture(directory)).isEqualTo(before);
    }

    @Test void malformedSelectedJsonProducesPartialCoverageReportsWithoutSourceMutation() throws Exception {
        Files.writeString(directory.resolve("mod_info.json"), "{\"id\":\"bad-json\"}");
        Path strings = Files.createDirectories(directory.resolve("data/strings"));
        Files.writeString(strings.resolve("strings.json"), "{\"hello\":");
        var beforeDirectory = new com.ssmt.scanner.CandidateInventory().capture(directory);
        var command = new CommandLine(new Main());
        StringWriter directoryOutput = new StringWriter();
        command.setOut(new PrintWriter(directoryOutput));

        assertThat(command.execute("assess", directory.toString(), "--coverage", "--json"))
                .isEqualTo(1);
        var directoryReport = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(directoryOutput.toString());
        assertThat(directoryReport.path("coverageStatus").asText())
                .isEqualTo("INCOMPLETE_SOURCE_PARSE");
        assertThat(directoryReport.path("extractionCoverage")).isEmpty();
        assertThat(directoryReport.path("jsonGapStatus").asText()).isEqualTo("NOT_ASSESSED");
        assertThat(directoryReport.path("findings").toString())
                .contains("COVERAGE_SOURCE_PARSE_FAILED", "data/strings/strings.json");
        assertThat(new com.ssmt.scanner.CandidateInventory().capture(directory))
                .isEqualTo(beforeDirectory);

        Path archive = directory.resolve("candidate.zip");
        try (var output = new java.util.zip.ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new java.util.zip.ZipEntry("wrapper/mod_info.json"));
            output.write(Files.readAllBytes(directory.resolve("mod_info.json")));
            output.closeEntry();
            output.putNextEntry(new java.util.zip.ZipEntry("wrapper/data/strings/strings.json"));
            output.write(Files.readAllBytes(strings.resolve("strings.json")));
            output.closeEntry();
        }
        byte[] beforeArchive = Files.readAllBytes(archive);
        StringWriter archiveOutput = new StringWriter();
        StringWriter archiveError = new StringWriter();
        command.setOut(new PrintWriter(archiveOutput));
        command.setErr(new PrintWriter(archiveError));
        assertThat(command.execute("assess", archive.toString(), "--coverage", "--json"))
                .isEqualTo(1);
        assertThat(archiveOutput.toString()).withFailMessage("%s", archiveError).isNotBlank();
        var archiveReport = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(archiveOutput.toString());
        assertThat(archiveReport.path("coverageStatus").asText())
                .isEqualTo("INCOMPLETE_SOURCE_PARSE");
        assertThat(archiveReport.path("extractionCoverage")).isEmpty();
        assertThat(archiveReport.path("findings").toString())
                .contains("COVERAGE_SOURCE_PARSE_FAILED", "data/strings/strings.json");
        assertThat(archiveOutput.toString()).doesNotContain(archive.toString());
        assertThat(Files.readAllBytes(archive)).isEqualTo(beforeArchive);
        assertThat(directory.resolve("wrapper")).doesNotExist();
    }

    @Test void archiveCoverageUsesStandardHandlersWithoutExtractingToDisk() throws Exception {
        Path jar = directory.resolve("payload.jar");
        try (var output = new java.util.zip.ZipOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new java.util.zip.ZipEntry("notes.txt"));
            output.write("Technical resource".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new java.util.zip.ZipEntry("com/ssmt/cli/AssessCommandTest.class"));
            try (var input = AssessCommandTest.class.getResourceAsStream("AssessCommandTest.class")) {
                assertThat(input).isNotNull();
                input.transferTo(output);
            }
            output.closeEntry();
        }
        Path archive = directory.resolve("candidate.zip");
        try (var output = new java.util.zip.ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new java.util.zip.ZipEntry("wrapper/mod_info.json"));
            output.write("{\"id\":\"archive-coverage\"}"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new java.util.zip.ZipEntry("wrapper/data/strings/strings.json"));
            output.write("{\"hello\":\"Hello\"}"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new java.util.zip.ZipEntry("wrapper/data/hulls/review.ship"));
            output.write("{hullName:'Visible',spriteName:'technical.png'}"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new java.util.zip.ZipEntry("wrapper/unsupported.xyz"));
            output.write("Review me".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new java.util.zip.ZipEntry("wrapper/jars/payload.jar"));
            output.write(Files.readAllBytes(jar));
            output.closeEntry();
        }
        byte[] before = Files.readAllBytes(archive);
        var command = new CommandLine(new Main());
        StringWriter output = new StringWriter();
        command.setOut(new PrintWriter(output));

        assertThat(command.execute("assess", archive.toString(), "--coverage",
                "--jar-inventory", "--json")).isZero();

        var report = new com.fasterxml.jackson.databind.ObjectMapper().readTree(output.toString());
        assertThat(report.path("coverageStatus").asText()).isEqualTo("OBSERVED_STANDARD_EXTRACTION");
        assertThat(report.path("extractionCoverage").size()).isEqualTo(5);
        assertThat(report.path("extractionCoverage").toString()).contains(
                "\"path\":\"data/strings/strings.json\"", "\"strings\":1",
                "\"path\":\"unsupported.xyz\"", "NO_EXTRACTOR_MATCH");
        assertThat(report.path("jsonGapFindings").toString()).contains("UNSELECTED_TEXT_REVIEW");
        assertThat(report.path("jarContents").toString()).contains("notes.txt",
                "NO_STANDARD_ARCHIVE_ENTRY_EXTRACTOR", "AssessCommandTest.class",
                "ALLOWLISTED_CLASS_STRINGS_SELECTED");
        assertThat(Files.readAllBytes(archive)).isEqualTo(before);
        assertThat(directory.resolve("wrapper")).doesNotExist();
    }

    @Test void csvReviewDoesNotRequireSuccessfulTranslationExtraction() throws Exception {
        Files.writeString(directory.resolve("mod_info.json"), "{\"id\":\"csv-review\"}");
        Path weapons = Files.createDirectories(directory.resolve("data/weapons"));
        Path csv = weapons.resolve("weapon_data.csv");
        String malformed = "id,name\na,One,Extra\na,Two\n";
        Files.writeString(csv, malformed);
        var command = new CommandLine(new Main());
        StringWriter output = new StringWriter();
        command.setOut(new PrintWriter(output));
        assertThat(command.execute("assess", directory.toString(), "--csv-audit", "--json")).isZero();
        assertThat(output.toString()).contains("OBSERVED_ADVISORY_STRUCTURE", "EXTRA_COLUMNS",
                "DUPLICATE_IDENTITY", "REVIEW", "\"coverageStatus\":\"NOT_ASSESSED\"");
        assertThat(Files.readString(csv)).isEqualTo(malformed);
    }

    @Test void archiveCsvReviewReportsStructureWithoutExtraction() throws Exception {
        Path archive = directory.resolve("candidate.zip");
        try (var output = new java.util.zip.ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new java.util.zip.ZipEntry("wrapper/mod_info.json"));
            output.write("{\"id\":\"archive-csv\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new java.util.zip.ZipEntry("wrapper/data/weapons/weapon_data.csv"));
            output.write("id,name\na,One,Extra\na,Two\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
        }
        byte[] before = Files.readAllBytes(archive);
        var command = new CommandLine(new Main());
        StringWriter output = new StringWriter();
        command.setOut(new PrintWriter(output));

        assertThat(command.execute("assess", archive.toString(), "--csv-audit", "--json")).isZero();
        assertThat(output.toString()).contains("OBSERVED_ADVISORY_STRUCTURE", "EXTRA_COLUMNS",
                "DUPLICATE_IDENTITY");
        assertThat(Files.readAllBytes(archive)).isEqualTo(before);
        assertThat(directory.resolve("wrapper")).doesNotExist();
    }

    @Test void jarInventoryDoesNotValidateOrLoadClassPayloads() throws Exception {
        Files.writeString(directory.resolve("mod_info.json"), "{\"id\":\"jar-review\"}");
        Path jar = directory.resolve("mod.jar");
        try (var output = new java.util.zip.ZipOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new java.util.zip.ZipEntry("Example.class"));
            output.write("not executable class bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
        }
        byte[] before = Files.readAllBytes(jar);
        var command = new CommandLine(new Main());
        StringWriter output = new StringWriter();
        command.setOut(new PrintWriter(output));
        assertThat(command.execute("assess", directory.toString(), "--jar-inventory", "--json")).isZero();
        assertThat(output.toString()).contains("OBSERVED_PAYLOAD_INVENTORY",
                "CLASS_ENTRY_UNVERIFIED", "NOT_ESTABLISHED",
                "BYTECODE_ONLY_BEHAVIOR_UNVERIFIED", "\"severity\":\"MANUAL\"");
        assertThat(Files.readAllBytes(jar)).isEqualTo(before);
    }

    @Test void archiveJarInventoryDoesNotExtractOrLoadClassPayloads() throws Exception {
        Path jar = directory.resolve("embedded.jar");
        try (var output = new java.util.zip.ZipOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new java.util.zip.ZipEntry("Example.class"));
            output.write("not executable class bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
        }
        Path archive = directory.resolve("candidate.zip");
        try (var output = new java.util.zip.ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new java.util.zip.ZipEntry("wrapper/mod_info.json"));
            output.write("{\"id\":\"embedded-jar\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new java.util.zip.ZipEntry("wrapper/jars/embedded.jar"));
            output.write(Files.readAllBytes(jar));
            output.closeEntry();
        }
        byte[] before = Files.readAllBytes(archive);
        var command = new CommandLine(new Main());
        StringWriter output = new StringWriter();
        command.setOut(new PrintWriter(output));

        assertThat(command.execute("assess", archive.toString(), "--jar-inventory", "--json")).isZero();

        assertThat(output.toString()).contains("OBSERVED_PAYLOAD_INVENTORY",
                "wrapper/jars/embedded.jar", "CLASS_ENTRY_UNVERIFIED", "NOT_ESTABLISHED");
        assertThat(Files.readAllBytes(archive)).isEqualTo(before);
        assertThat(directory.resolve("wrapper")).doesNotExist();
    }

    @Test void corruptNestedJarStillProducesABlockingPartialAssessment() throws Exception {
        Path jar = directory.resolve("embedded.jar");
        byte[] payload = "payload".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var crc = new java.util.zip.CRC32();
        crc.update(payload);
        try (var output = new java.util.zip.ZipOutputStream(Files.newOutputStream(jar))) {
            var entry = new java.util.zip.ZipEntry("notes.txt");
            entry.setMethod(java.util.zip.ZipEntry.STORED);
            entry.setSize(payload.length);
            entry.setCompressedSize(payload.length);
            entry.setCrc(crc.getValue());
            output.putNextEntry(entry);
            output.write(payload);
            output.closeEntry();
        }
        byte[] corruptedJar = Files.readAllBytes(jar);
        java.util.Arrays.fill(corruptedJar, 14, 18, (byte) 0);
        Path archive = directory.resolve("candidate.zip");
        try (var output = new java.util.zip.ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new java.util.zip.ZipEntry("wrapper/mod_info.json"));
            output.write("{\"id\":\"bad-jar\"}"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new java.util.zip.ZipEntry("wrapper/jars/embedded.jar"));
            output.write(corruptedJar);
            output.closeEntry();
        }
        byte[] before = Files.readAllBytes(archive);
        var command = new CommandLine(new Main());
        StringWriter output = new StringWriter();
        command.setOut(new PrintWriter(output));

        assertThat(command.execute("assess", archive.toString(), "--jar-inventory",
                "--coverage", "--json")).isEqualTo(1);

        var report = new com.fasterxml.jackson.databind.ObjectMapper().readTree(output.toString());
        assertThat(report.path("status").asText()).isEqualTo("ASSESSMENT_ONLY");
        assertThat(report.path("jarInventoryStatus").asText()).isEqualTo("INCOMPLETE_JAR_INTEGRITY");
        assertThat(report.path("coverageStatus").asText()).isEqualTo("NOT_ASSESSED_INVALID_JAR");
        assertThat(report.path("sourceAuthority").path("sourceJarCorrespondence").asText())
                .isEqualTo("NOT_ESTABLISHED_INCOMPLETE_PAYLOAD_INTEGRITY");
        assertThat(report.path("findings").toString()).contains("JAR_ENTRY_INTEGRITY_FAILED",
                "notes.txt", "\"severity\":\"BLOCKING\"");

        StringWriter coverageOnlyOutput = new StringWriter();
        command.setOut(new PrintWriter(coverageOnlyOutput));
        assertThat(command.execute("assess", archive.toString(), "--coverage", "--json"))
                .isEqualTo(1);
        var coverageOnlyReport = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(coverageOnlyOutput.toString());
        assertThat(coverageOnlyReport.path("jarInventoryStatus").asText()).isEqualTo("NOT_ASSESSED");
        assertThat(coverageOnlyReport.path("jarContents")).isEmpty();
        assertThat(coverageOnlyReport.path("coverageStatus").asText())
                .isEqualTo("NOT_ASSESSED_INVALID_JAR");
        assertThat(coverageOnlyReport.path("sourceAuthority")
                .path("sourceJarCorrespondence").asText())
                .isEqualTo("NOT_ESTABLISHED_INCOMPLETE_PAYLOAD_INTEGRITY");
        assertThat(coverageOnlyReport.path("findings").toString())
                .contains("JAR_ENTRY_INTEGRITY_FAILED", "notes.txt");
        assertThat(Files.readAllBytes(archive)).isEqualTo(before);
        assertThat(directory.resolve("wrapper")).doesNotExist();
    }

    @Test void optionalSourceManifestAttestsDirectoriesAndMetadataWithoutMutation() throws Exception {
        Files.writeString(directory.resolve("mod_info.json"), "{\"id\":\"source-attestation\"}");
        Files.createDirectory(directory.resolve("empty"));
        var before = new com.ssmt.scanner.SourceTreeManifest().capture(directory);
        var command = new CommandLine(new Main());
        StringWriter output = new StringWriter();
        command.setOut(new PrintWriter(output));
        assertThat(command.execute("assess", directory.toString(), "--source-manifest", "--json")).isZero();
        assertThat(output.toString()).contains("UNCHANGED_OBSERVED_BYTES_AND_METADATA",
                "\"path\":\"empty\"", "\"kind\":\"DIRECTORY\"", "\"modified\":");
        assertThat(new com.ssmt.scanner.SourceTreeManifest().capture(directory)).isEqualTo(before);
    }

    @Test void ambiguousModRootsAreNotSelected() throws Exception {
        for (String name : java.util.List.of("a", "b")) {
            Path root = Files.createDirectories(directory.resolve(name));
            Files.writeString(root.resolve("mod_info.json"), "{}");
        }
        var command = new CommandLine(new Main());
        StringWriter output = new StringWriter();
        command.setOut(new PrintWriter(output));
        assertThat(command.execute("assess", directory.toString(), "--json")).isEqualTo(1);
        assertThat(output.toString()).contains("AMBIGUOUS", "\"selectedRoot\":\"\"");
        assertThat(output.toString()).contains("\"severity\":\"BLOCKING\"",
                "MOD_ROOT_AMBIGUOUS");
    }

    @Test void competingInputComparisonIsExplicitWithoutClaimingAuthority() throws Exception {
        Path candidate = Files.createDirectory(directory.resolve("candidate"));
        Files.writeString(candidate.resolve("mod_info.json"), "{\"id\":\"candidate\"}");
        Path competing = Files.createDirectory(directory.resolve("competing"));
        Files.writeString(competing.resolve("mod_info.json"), "{\"id\":\"candidate\"}");
        var command = new CommandLine(new Main());
        StringWriter output = new StringWriter();
        command.setOut(new PrintWriter(output));
        assertThat(command.execute("assess", candidate.toString(), "--compare-input", competing.toString(), "--json"))
                .isZero();
        assertThat(output.toString()).contains("COMPETING_INPUT_MATCHES_SELECTED_CANDIDATE",
                "USER_SUPPLIED_DIRECTORY_UNVERIFIED");
    }
}
