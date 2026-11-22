package com.ssmt.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssmt.project.BridgeForgeGeneManifest;
import com.ssmt.project.BridgeForgeGeneStrings;
import com.ssmt.project.ProjectException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** Project Go P9 item 1 (BridgeForge 94.G2): exports a BridgeForge gene's player-visible strings as one unit. */
@Command(name = "gene-strings",
        description = "Export the player-visible strings of a BridgeForge gene (gene.json), or of every gene of a"
                + " BridgeForge set proof folder, as one unit with stable ids, licence and BridgeForge's evidence header.")
final class GeneStringsCommand implements Callable<Integer> {
    private static final Logger LOG = LoggerFactory.getLogger(GeneStringsCommand.class);

    @Parameters(index = "0", description = "A gene.json, or a BridgeForge proof folder (its gene.json, then closure/*.json).")
    private Path genes;

    @Parameters(index = "1", description = "The mod folder the genes were described from (read only).")
    private Path modRoot;

    @Option(names = "--out", required = true, description = "Export JSON path; must not exist. Holds the mod's text: keep it"
            + " local when the gene's licence is LOCAL_ONLY.")
    private Path out;

    @Override
    public Integer call() {
        try {
            if (Files.exists(out, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("Refusing to overwrite " + out);
            }
            if (out.toAbsolutePath().normalize().startsWith(modRoot.toAbsolutePath().normalize())) {
                throw new IllegalArgumentException("--out is inside the mod folder, which is never written: " + out);
            }
            ObjectMapper json = new ObjectMapper();
            String version = new Main.VersionProvider().getVersion()[0].replaceFirst("^SSMT ", "");
            ObjectNode export = BridgeForgeGeneStrings.export(manifests(json), modRoot, version);
            try (OutputStream stream = Files.newOutputStream(out, StandardOpenOption.CREATE_NEW)) {
                json.writerWithDefaultPrettyPrinter().writeValue(stream, export);  // CREATE_NEW: never through a link
            }
            int units = 0;
            int uncovered = 0;
            for (JsonNode gene : export.get("genes")) {
                units += gene.get("units").size();
                uncovered += gene.get("uncovered").size();
            }
            LOG.info("Gene strings: {} gene(s), {} unit(s), {} uncovered; verdict {}", export.get("genes").size(), units,
                    uncovered, export.get("verdict").asText());
            return "PASS".equals(export.get("verdict").asText()) ? 0 : 1;
        } catch (ProjectException | IllegalArgumentException | IOException | UncheckedIOException exception) {
            LOG.error(CliDiagnostics.explain("Gene string export", exception));
            return 1;
        }
    }

    /** The manifests to export: one gene.json, or a proof folder's gene.json then its closure/*.json, each identity once. */
    private List<JsonNode> manifests(ObjectMapper json) throws IOException {
        List<Path> files = new ArrayList<>();
        Path first = Files.isDirectory(genes) ? genes.resolve("gene.json") : genes;
        files.add(first);
        if (Files.isDirectory(genes)) {
            Path closure = genes.resolve("closure");
            if (Files.isDirectory(closure)) {
                try (Stream<Path> listed = Files.list(closure)) {
                    files.addAll(listed.filter(path -> path.toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                            .sorted().toList());
                }
            }
        }
        List<JsonNode> manifests = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Path file : files) {
            JsonNode gene = json.readTree(file.toFile());
            if (!BridgeForgeGeneManifest.SCHEMA.equals(gene.path("schema").asText())) {
                if (file.equals(first)) {  // review 2026-11-22: the header is about this gene, so never skip it
                    throw new IllegalArgumentException(file + " is not a BridgeForge gene manifest");
                }
                LOG.warn("Skipped {}: not a BridgeForge gene manifest", file);  // a stray file in closure/
                continue;
            }
            if (seen.add(gene.path("identity").asText())) {
                manifests.add(gene);
            }
        }
        return manifests;
    }
}
