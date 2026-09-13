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
                "\"id\":\"MagicLib\"", "\"version\":\"1\"", "NOT_TESTED");
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
        Files.writeString(directory.resolve("unsupported.xyz"), "Review me");
        var before = new com.ssmt.scanner.CandidateInventory().capture(directory);
        var command = new CommandLine(new Main());
        StringWriter output = new StringWriter();
        command.setOut(new PrintWriter(output));
        assertThat(command.execute("assess", directory.toString(), "--coverage", "--json")).isZero();
        assertThat(output.toString()).contains("OBSERVED_STANDARD_EXTRACTION", "UNSUPPORTED",
                "\"strings\":1", "\"path\":\"data/strings/strings.json\"");
        assertThat(new com.ssmt.scanner.CandidateInventory().capture(directory)).isEqualTo(before);
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
                "CLASS_ENTRY_UNVERIFIED", "NOT_ESTABLISHED");
        assertThat(Files.readAllBytes(jar)).isEqualTo(before);
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
    }
}
