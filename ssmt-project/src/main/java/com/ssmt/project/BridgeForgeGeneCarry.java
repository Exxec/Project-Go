package com.ssmt.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssmt.core.model.ExtractedString;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Project Go P9 item 2, slice 1 (BridgeForge 94.G4): how a gene's strings carry into a host, as a read-only report.
 *
 * <p>The donor side is a gene-strings export (P9 item 1); the host side is the host's units that the same gene members
 * select, by the export's own rule ({@link BridgeForgeGeneStrings#members}). Both go through string-diff's comparison
 * ({@link RootStringDiffService}), so every unit is ADDED, REMOVED, CHANGED or UNCHANGED. Given a translation pack written
 * for the donor's strings, each unit also gets string-diff's carry verdict: CARRIED_OVER, STALE, NEEDS_TRANSLATION,
 * ORPHANED or NONE. A host text that still holds CJK characters is flagged, because Project Go translates Chinese into
 * English. A CHANGED CSV cell that differs only in CRLF against LF is flagged {@code crlf_only}: the game reads it as the
 * same text.
 *
 * <p>Jar units are raw string constants, not reviewed as player-visible, so they are counted apart and the verdict does
 * not judge them. The verdict is FAIL when, in any gene, a data unit is REMOVED or a host data unit holds CJK, or when a
 * host member file cannot be read; otherwise it is PASS. The summary counts each identity once, keeping its worst row.
 * Rows hold identities and SHA-256 digests only, never text. Nothing is written into the host, and no approval is made:
 * this slice reports, it does not carry.
 */
public final class BridgeForgeGeneCarry {
    /** The report's format name. */
    public static final String FORMAT = "project-go-gene-carry";
    /** The report's format version. */
    public static final int FORMAT_VERSION = 1;
    /** The evidence kind written in the header. */
    public static final String EVIDENCE_KIND = "gene-carry";
    private static final ObjectMapper JSON = new ObjectMapper();

    private BridgeForgeGeneCarry() { }

    /**
     * The carry report of {@code genes} from the donor described by {@code export} into the host at {@code hostRoot}.
     *
     * @param genes the gene manifests the export was made from (the first is the one the header is about)
     * @param export the gene-strings export of the donor (P9 item 1), verdict PASS
     * @param exportSha256 the export file's SHA-256, recorded so the report is bound to it
     * @param hostRoot the host mod folder (read only)
     * @param pack translations written for the donor's strings, or null for none
     * @param producedBy Project Go's version
     * @return the report document
     * @throws ProjectException when the export or a manifest is refused, or the host cannot be read
     */
    public static ObjectNode report(List<JsonNode> genes, JsonNode export, String exportSha256, Path hostRoot,
            LocalizationProject pack, String producedBy) throws ProjectException {
        Map<String, JsonNode> exported = checkInputs(genes, export);
        Set<String> hostUnreadable = new TreeSet<>();
        Map<String, List<ExtractedString>> host = BridgeForgeGeneStrings.unitsByFile(hostRoot, hostUnreadable);
        RootStringDiffService.Translations translations = pack == null ? null
                : new RootStringDiffService.Translations().withProject(pack);
        Set<String> inputs = new TreeSet<>();
        Set<String> unreadable = new TreeSet<>();
        Map<String, ObjectNode> distinct = new TreeMap<>();
        boolean dataFails = false;
        ArrayNode geneEntries = JSON.createArrayNode();
        for (JsonNode gene : genes) {
            List<ExtractedString> hostUnits = hostUnits(gene, host, hostUnreadable, inputs, unreadable);
            List<ExtractedString> donorUnits = donorUnits(exported.get(gene.get("identity").asText()));
            Map<String, String> hostText = texts(hostUnits);
            Map<String, String> donorText = texts(donorUnits);
            ArrayNode rows = JSON.createArrayNode();
            for (RootStringDiffService.Unit unit : new RootStringDiffService().diff(donorUnits, hostUnits, translations).units()) {
                String text = hostText.get(identity(unit.file(), unit.key()));
                ObjectNode row = row(unit, text != null && BridgeForgeTextUnits.cjk(text),
                        crlfOnly(unit, donorText.get(identity(unit.file(), unit.key())), text));
                rows.add(row);
                // Review 2026-11-22: genes sharing a unit can see it differently (one gene's member may not map in the
                // host), so every gene's rows are judged, and the summary keeps the worst row whatever the gene order.
                dataFails |= failsData(row);
                distinct.merge(identity(unit.file(), unit.key()), row, (kept, next) -> worse(next, kept) ? next : kept);
            }
            ObjectNode entry = geneEntries.addObject();
            entry.put("anchor", gene.get("anchors").get(0).asText());
            entry.put("identity", gene.get("identity").asText());
            entry.set("counts", counts(rows));
            entry.set("units", rows);
        }
        ObjectNode document = header(genes.getFirst(), producedBy);
        ArrayNode rows = document.putArray("inputs");
        Set<String> missing = new TreeSet<>();
        for (String path : inputs) {
            Path file = hostRoot.resolve(path);
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                missing.add(path);  // its units are REMOVED in the rows; listed so the reason is plain
                continue;
            }
            String digest;
            try {
                digest = sha256(file);
            } catch (ProjectException exception) {
                unreadable.add(path);  // a member file that exists but cannot be read fails the report, not aborts it
                continue;
            }
            ObjectNode row = rows.addObject();
            row.put("role", "host-member");
            row.put("path", path);
            row.put("sha256", digest);
        }
        ArrayNode all = JSON.createArrayNode();
        distinct.values().forEach(all::add);
        ObjectNode summary = counts(all);
        document.put("verdict", unreadable.isEmpty() && !dataFails ? "PASS" : "FAIL");
        document.put("format", FORMAT);
        document.put("format_version", FORMAT_VERSION);
        document.put("gene_strings_sha256", exportSha256);
        document.put("translations", pack == null ? "none: carry verdicts NOT_EVALUATED" : "pack");
        document.put("jar_units", "raw string constants, not reviewed as player-visible: counted, not judged");
        document.set("unreadable_member_files", JSON.valueToTree(List.copyOf(unreadable)));
        document.set("missing_member_files", JSON.valueToTree(List.copyOf(missing)));
        document.set("summary", summary);
        document.set("genes", geneEntries);
        List<String> problems = BridgeForgeEvidenceHeader.validate(document);
        if (!problems.isEmpty()) {
            throw new ProjectException("The report's evidence header is malformed: " + String.join("; ", problems));
        }
        return document;
    }

    /** The export's genes by identity, after checking the export and that it is about exactly these manifests. */
    private static Map<String, JsonNode> checkInputs(List<JsonNode> genes, JsonNode export) throws ProjectException {
        if (genes.isEmpty()) {
            throw new ProjectException("No gene manifest given");
        }
        for (JsonNode gene : genes) {
            List<String> problems = BridgeForgeGeneManifest.validate(gene);
            if (!problems.isEmpty()) {
                throw new ProjectException("Not a gene manifest under the pinned contract: " + String.join("; ", problems));
            }
        }
        if (!BridgeForgeGeneStrings.FORMAT.equals(export.path("format").asText())
                || export.path("format_version").asInt() != BridgeForgeGeneStrings.FORMAT_VERSION) {
            throw new ProjectException("Not a gene-strings export (format " + BridgeForgeGeneStrings.FORMAT + " "
                    + BridgeForgeGeneStrings.FORMAT_VERSION + ")");
        }
        List<String> problems = BridgeForgeEvidenceHeader.validate(export);
        if (!problems.isEmpty()) {
            throw new ProjectException("The export's evidence header is malformed: " + String.join("; ", problems));
        }
        if (!"PASS".equals(export.get("verdict").asText())) {
            throw new ProjectException("The export's verdict is " + export.get("verdict").asText()
                    + ": carry only from a complete export");
        }
        Map<String, JsonNode> exported = new HashMap<>();
        export.path("genes").forEach(entry -> exported.put(entry.path("identity").asText(), entry));
        Set<String> wanted = new TreeSet<>();
        genes.forEach(gene -> wanted.add(gene.get("identity").asText()));
        if (!wanted.equals(new TreeSet<>(exported.keySet()))) {
            throw new ProjectException("The export is about other genes than the manifests given");
        }
        for (JsonNode entry : exported.values()) {
            for (JsonNode unit : entry.path("units")) {  // review 2026-11-22: a hand-edited unit is refused, not an NPE
                if (!unit.path("file").isTextual() || !unit.path("key").isTextual() || !unit.path("text").isTextual()
                        || BridgeForgeEvidenceHeader.pathProblem(unit.get("file")) != null) {
                    throw new ProjectException("An export unit needs a relative file, a key and a text: " + unit.path("key"));
                }
            }
        }
        return exported;
    }

    private static List<ExtractedString> hostUnits(JsonNode gene, Map<String, List<ExtractedString>> host,
            Set<String> hostUnreadable, Set<String> inputs, Set<String> unreadable) {
        List<ExtractedString> units = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (BridgeForgeGeneStrings.Member member : BridgeForgeGeneStrings.members(gene, host)) {
            if (member.file() == null) {
                continue;  // a malformed member path: the export listed it as not mappable
            }
            inputs.add(member.file());
            if (hostUnreadable.contains(member.file())) {
                unreadable.add(member.file());
            }
            for (ExtractedString string : member.units()) {
                if (seen.add(identity(member.file(), string.key()))) {
                    units.add(string);
                }
            }
        }
        return units;
    }

    private static List<ExtractedString> donorUnits(JsonNode entry) {
        List<ExtractedString> units = new ArrayList<>();
        for (JsonNode unit : entry.path("units")) {
            units.add(new ExtractedString("donor", Path.of(unit.get("file").asText()), unit.get("key").asText(),
                    unit.get("text").asText(), -1));
        }
        return units;
    }

    /** A data row the verdict fails on: a donor string the host lacks, or a host string still in CJK. */
    private static boolean failsData(JsonNode row) {
        return "data".equals(row.get("kind").asText())
                && ("REMOVED".equals(row.get("change").asText()) || row.get("host_cjk").asBoolean());
    }

    /** Whether {@code row} is worse than {@code other} for the same identity: failing beats passing, REMOVED beats others. */
    private static boolean worse(JsonNode row, JsonNode other) {
        if (failsData(row) != failsData(other)) {
            return failsData(row);
        }
        return "REMOVED".equals(row.get("change").asText()) && !"REMOVED".equals(other.get("change").asText());
    }

    private static Map<String, String> texts(List<ExtractedString> units) {
        Map<String, String> texts = new HashMap<>();
        units.forEach(string -> texts.put(identity(posix(string.sourceFile()), string.key()), string.originalText()));
        return texts;
    }

    /**
     * Whether a CHANGED CSV unit differs only in CRLF against LF. The game's CSV parser replaces CRLF with LF before it
     * reads a table (RC7 {@code com.fs.starfarer.loading.void.o00000}, javap 2026-11-22), so such a cell is the same text
     * to the game. BridgeForge's carrier writes LF, so its multi-line cells differ this way.
     */
    private static boolean crlfOnly(RootStringDiffService.Unit unit, String donor, String host) {
        return unit.change() == RootStringDiffService.Change.CHANGED && unit.key().startsWith("csv:") && donor != null
                && host != null && donor.replace("\r\n", "\n").equals(host.replace("\r\n", "\n"));
    }

    private static ObjectNode row(RootStringDiffService.Unit unit, boolean hostCjk, boolean crlfOnly) {
        ObjectNode row = JSON.createObjectNode();
        row.put("file", unit.file());
        row.put("key", unit.key());
        row.put("kind", unit.key().startsWith("class:") ? "jar" : "data");
        row.put("change", unit.change().name());
        row.put("carry", unit.carry().name());
        row.put("donor_sha256", unit.oldDigest());
        row.put("host_sha256", unit.newDigest());
        row.put("host_cjk", hostCjk);
        row.put("crlf_only", crlfOnly);
        return row;
    }

    /** Per kind (data, jar): units per change and per carry verdict, and host units that still hold CJK. */
    private static ObjectNode counts(ArrayNode rows) {
        ObjectNode counts = JSON.createObjectNode();
        for (JsonNode row : rows) {
            ObjectNode kind = counts.has(row.get("kind").asText()) ? (ObjectNode) counts.get(row.get("kind").asText())
                    : counts.putObject(row.get("kind").asText());
            kind.put("units", kind.path("units").asInt() + 1);
            kind.put(row.get("change").asText(), kind.path(row.get("change").asText()).asInt() + 1);
            ObjectNode carry = kind.has("carry") ? (ObjectNode) kind.get("carry") : kind.putObject("carry");
            carry.put(row.get("carry").asText(), carry.path(row.get("carry").asText()).asInt() + 1);
            kind.put("host_cjk", kind.path("host_cjk").asInt() + (row.get("host_cjk").asBoolean() ? 1 : 0));
            kind.put("changed_crlf_only", kind.path("changed_crlf_only").asInt() + (row.get("crlf_only").asBoolean() ? 1 : 0));
        }
        return counts;
    }

    private static ObjectNode header(JsonNode first, String producedBy) {
        ObjectNode document = JSON.createObjectNode();
        document.put("evidence", BridgeForgeEvidenceHeader.EVIDENCE);
        document.put("schema_version", BridgeForgeEvidenceHeader.SCHEMA_VERSION);
        document.put("kind", EVIDENCE_KIND);
        ObjectNode about = document.putObject("gene");
        about.put("identity", first.get("identity").asText());
        about.set("schema_version", first.get("schema_version"));
        ObjectNode produced = document.putObject("produced_by");
        produced.put("tool", "project-go");
        produced.put("version", producedBy);
        document.put("created_at", OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).toString());
        document.putObject("build").set("game_version", first.path("build").path("game_version").isTextual()
                ? first.get("build").get("game_version") : JSON.nullNode());
        return document;
    }

    private static String identity(String file, String key) {
        return file + "\0" + key;
    }

    private static String posix(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static String sha256(Path file) throws ProjectException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        } catch (IOException exception) {
            throw new ProjectException("Could not hash host member file " + file, exception);
        }
    }
}
