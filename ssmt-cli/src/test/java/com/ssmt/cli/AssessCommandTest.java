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
