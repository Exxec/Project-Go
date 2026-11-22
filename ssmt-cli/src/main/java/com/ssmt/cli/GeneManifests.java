package com.ssmt.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssmt.project.BridgeForgeGeneManifest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The BridgeForge gene manifests a P9 command reads: one gene.json, or a proof folder's gene.json then closure/*.json. */
final class GeneManifests {
    private static final Logger LOG = LoggerFactory.getLogger(GeneManifests.class);

    private GeneManifests() { }

    /**
     * Each identity once, in order. The first file must be a gene manifest, since evidence headers are about it; a stray
     * JSON file under closure/ is skipped with a warning.
     */
    static List<JsonNode> load(Path genes, ObjectMapper json) throws IOException {
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
