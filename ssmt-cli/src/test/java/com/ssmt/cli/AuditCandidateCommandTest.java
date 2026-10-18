package com.ssmt.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssmt.project.InstalledCopyEvidence;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class AuditCandidateCommandTest {
    @TempDir Path temporary;

    private Path candidate(String csv) throws IOException {
        Path mod = temporary.resolve("mod");
        Files.createDirectories(mod.resolve("data"));
        Files.writeString(mod.resolve("mod_info.json"), "{\"id\":\"aud\",\"name\":\"Aud\",\"version\":\"3\"}");
        Files.writeString(mod.resolve("data/a.csv"), csv);
        return mod;
    }

    private Path zip(Path directory, String name) throws IOException {
        Path archive = temporary.resolve(name);
        try (var output = new ZipOutputStream(Files.newOutputStream(archive)); var walk = Files.walk(directory)) {
            for (Path path : walk.filter(Files::isRegularFile).sorted().toList()) {
                output.putNextEntry(new ZipEntry(directory.relativize(path).toString().replace(java.io.File.separatorChar, '/')));
                output.write(Files.readAllBytes(path));
                output.closeEntry();
            }
        }
        return archive;
    }

    private int run(Path archive, Path manifest, Path ledger, String... extra) {
        var arguments = new java.util.ArrayList<>(java.util.List.of(archive.toString(), "--manifest",
                manifest.toString(), "--ledger", ledger.toString(), "--work-dir",
                temporary.resolve("work-" + System.nanoTime()).toString()));
        arguments.addAll(java.util.List.of(extra));
        return new CommandLine(new AuditCandidateCommand()).execute(arguments.toArray(String[]::new));
    }

    @Test void exitCodesSeparatePassReviewAndFailAndTheLedgerRecordsTheGates() throws Exception {
        Path clean = candidate("id,name\nx,English\n");
        Path manifest = temporary.resolve("manifest.json");
        new ObjectMapper().writeValue(manifest.toFile(), new InstalledCopyEvidence().capture(clean));
        Path archive = zip(clean, "clean.zip");
        Path ledger = temporary.resolve("out/ledger.json");
        assertThat(run(archive, manifest, ledger, "--expect-id", "aud", "--expect-version", "3")).isZero();
        var tree = new ObjectMapper().readTree(ledger.toFile());
        assertThat(tree.path("results")).anySatisfy(item -> {
            assertThat(item.path("gate").asText()).isEqualTo("PACKAGE_IDENTITY");
            assertThat(item.path("disposition").asText()).isEqualTo("PASS");
        });
        assertThat(ledger.resolveSibling("candidate-audit.json")).isRegularFile();
        assertThat(run(archive, manifest, temporary.resolve("out/second.json"), "--expect-id", "wrong")).isEqualTo(1);

        Files.writeString(clean.resolve("data/a.csv"), "id,name\nx,中文\n");
        new ObjectMapper().writeValue(manifest.toFile(), new InstalledCopyEvidence().capture(clean));
        assertThat(run(zip(clean, "left.zip"), manifest, temporary.resolve("out/third.json"))).isEqualTo(2);
    }
}
