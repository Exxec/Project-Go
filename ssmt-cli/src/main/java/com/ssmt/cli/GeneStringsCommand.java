package com.ssmt.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssmt.project.BridgeForgeGeneStrings;
import com.ssmt.project.ProjectException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;
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
            GeneEvidenceFile.checkTarget(out, modRoot, "--out");
            ObjectMapper json = new ObjectMapper();
            ObjectNode export = BridgeForgeGeneStrings.export(GeneManifests.load(genes, json), modRoot,
                    GeneEvidenceFile.version());
            GeneEvidenceFile.write(out, json, export);
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

}
