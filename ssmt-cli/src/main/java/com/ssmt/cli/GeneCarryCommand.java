package com.ssmt.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssmt.project.BridgeForgeGeneCarry;
import com.ssmt.project.LocalizationProjectService;
import com.ssmt.project.ProjectException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** Project Go P9 item 2, slice 1 (BridgeForge 94.G4): reports how a gene's strings carry into a host. Read only. */
@Command(name = "gene-carry",
        description = "Report how a BridgeForge gene's strings (a gene-strings export) carry into a host mod: each unit"
                + " ADDED, REMOVED, CHANGED or UNCHANGED, with string-diff's carry verdict given a pack, and host text"
                + " still in CJK. Identities and digests only; nothing is written into the host.")
final class GeneCarryCommand implements Callable<Integer> {
    private static final Logger LOG = LoggerFactory.getLogger(GeneCarryCommand.class);

    @Parameters(index = "0", description = "The gene.json or BridgeForge proof folder the export was made from.")
    private Path genes;

    @Parameters(index = "1", description = "The donor's gene-strings export (verdict PASS).")
    private Path export;

    @Parameters(index = "2", description = "The host mod folder (read only).")
    private Path hostRoot;

    @Option(names = "--report", required = true, description = "Report JSON path; must not exist, nor be inside the host.")
    private Path report;

    @Option(names = "--pack", description = "Project JSON holding translations written for the donor's strings (read only).")
    private Path pack;

    @Override
    public Integer call() {
        try {
            GeneEvidenceFile.checkTarget(report, hostRoot, "--report");
            ObjectMapper json = new ObjectMapper();
            byte[] exportBytes = Files.readAllBytes(export);
            ObjectNode document = BridgeForgeGeneCarry.report(GeneManifests.load(genes, json), json.readTree(exportBytes),
                    sha256(exportBytes), hostRoot, pack == null ? null : new LocalizationProjectService().read(pack),
                    GeneEvidenceFile.version());
            GeneEvidenceFile.write(report, json, document);
            JsonNode data = document.path("summary").path("data");
            JsonNode jar = document.path("summary").path("jar");
            LOG.info("Gene carry: {} gene(s); data {} unit(s): {} unchanged, {} changed, {} added, {} removed, {} still CJK;"
                    + " jar {} unit(s), {} still CJK; verdict {}", document.get("genes").size(), data.path("units").asInt(),
                    data.path("UNCHANGED").asInt(), data.path("CHANGED").asInt(), data.path("ADDED").asInt(),
                    data.path("REMOVED").asInt(), data.path("host_cjk").asInt(), jar.path("units").asInt(),
                    jar.path("host_cjk").asInt(), document.get("verdict").asText());
            return "PASS".equals(document.get("verdict").asText()) ? 0 : 1;
        } catch (ProjectException | IllegalArgumentException | IOException | UncheckedIOException exception) {
            LOG.error(CliDiagnostics.explain("Gene carry report", exception));
            return 1;
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
