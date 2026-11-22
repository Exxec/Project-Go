package com.ssmt.cli;

import static com.ssmt.cli.CliGeneFixtures.JSON;
import static com.ssmt.cli.CliGeneFixtures.gene;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/** Project Go P9 item 2, slice 1 (BridgeForge 94.G4): the gene-carry command. */
class GeneCarryCommandTest {
    @TempDir Path temporary;

    private Path write(String path, String text) throws Exception {
        Path file = temporary.resolve(path);
        Files.createDirectories(Objects.requireNonNull(file.getParent()));
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file;
    }

    private Path exportOf(Path manifest, Path mod) {
        Path export = temporary.resolve("gene-strings.json");
        assertThat(new CommandLine(new Main()).execute("gene-strings", manifest.toString(), mod.toString(), "--out",
                export.toString())).isZero();
        return export;
    }

    @Test
    void reportsACarryBoundToItsExportAndRefusesToOverwriteOrWriteIntoTheHost() throws Exception {
        write("donor/data/hulls/ship_data.csv", "name,id,designation\nDemo Hull,demo_hull,Cruiser\n");
        write("host/data/hulls/ship_data.csv", "name,id,designation\nDemo Hull,demo_hull,Cruiser\n");
        Path manifest = write("gene.json", JSON.writeValueAsString(gene("hull", "hull:demo_hull",
                "data/hulls/ship_data.csv [demo_hull]")));
        Path export = exportOf(manifest, temporary.resolve("donor"));
        Path report = temporary.resolve("carry.json");
        String[] arguments = {"gene-carry", manifest.toString(), export.toString(), temporary.resolve("host").toString(),
            "--report", report.toString()};

        int exit = new CommandLine(new Main()).execute(arguments);

        assertThat(exit).isZero();
        JsonNode document = JSON.readTree(report.toFile());
        assertThat(document.get("verdict").asText()).isEqualTo("PASS");
        assertThat(document.get("summary").get("data").get("UNCHANGED").asInt()).isEqualTo(2);
        assertThat(document.get("gene_strings_sha256").asText()).isEqualTo(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(export))));
        assertThat(document.get("produced_by").get("version").asText()).doesNotStartWith("SSMT ");
        String written = Files.readString(report);
        assertThat(new CommandLine(new Main()).execute(arguments)).isEqualTo(1);
        assertThat(Files.readString(report)).isEqualTo(written);
        Path inside = temporary.resolve("host/carry.json");
        assertThat(new CommandLine(new Main()).execute("gene-carry", manifest.toString(), export.toString(),
                temporary.resolve("host").toString(), "--report", inside.toString())).isEqualTo(1);
        assertThat(inside).doesNotExist();
    }

    @Test
    void aFailingCarryExitsOneAndStillWritesItsReport() throws Exception {
        write("donor/data/hulls/ship_data.csv", "name,id,designation\nDemo Hull,demo_hull,Cruiser\n");
        write("host/data/hulls/ship_data.csv", "name,id,designation\n演示舰,demo_hull,Cruiser\n");
        Path manifest = write("gene.json", JSON.writeValueAsString(gene("hull", "hull:demo_hull",
                "data/hulls/ship_data.csv [demo_hull]")));
        Path export = exportOf(manifest, temporary.resolve("donor"));
        Path report = temporary.resolve("carry.json");

        int exit = new CommandLine(new Main()).execute("gene-carry", manifest.toString(), export.toString(),
                temporary.resolve("host").toString(), "--report", report.toString());

        assertThat(exit).isEqualTo(1);
        JsonNode document = JSON.readTree(report.toFile());
        assertThat(document.get("verdict").asText()).isEqualTo("FAIL");
        assertThat(document.get("summary").get("data").get("host_cjk").asInt()).isEqualTo(1);
    }
}
