package com.ssmt.project;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Resumable draft recovery for review units whose target text is missing. It reads a review packet and never writes to
 * it: batch files, the checkpoint manifest and the imported drafts live only in a separate workspace directory. Drafts
 * are imported by identity (unit id plus source digest) and stay {@code DRAFT_NEEDS_REVIEW}; nothing is approved, and
 * the approved-entry copy is checked by digest on every operation. No drafts are generated and nothing is sent anywhere.
 */
public final class DraftRecoveryService {
    /** Status of a draft that still needs validation and review. */
    public static final String DRAFT_STATUS = "DRAFT_NEEDS_REVIEW";
    static final String MANIFEST = "recovery-manifest.json";
    static final String DRAFTS = "drafts.json";
    private static final String BATCH_DIR = "batches";
    private static final String EXPORTED = "EXPORTED";
    private static final String IMPORTED = "IMPORTED";
    private static final int MAX_REPORTED_PROBLEMS = 50;
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Counts reported by {@link #status}.
     *
     * @param units planned units
     * @param batches batches in the manifest
     * @param importedBatches batches already imported
     * @param draftedUnits units with an imported draft
     * @param remainingUnits units still without a draft
     */
    public record Status(int units, int batches, int importedBatches, int draftedUnits, int remainingUnits) { }

    /**
     * Counts reported by {@link #export}.
     *
     * @param written batch files written by this call
     * @param skipped batches already recorded in the manifest
     * @param total batches in the plan
     */
    public record ExportResult(int written, int skipped, int total) { }

    /**
     * Outcome of {@link #importBatch}.
     *
     * @param batch batch id
     * @param units drafts accepted by this call
     * @param alreadyImported true when the batch had been imported before and nothing changed
     */
    public record ImportResult(String batch, int units, boolean alreadyImported) { }

    /**
     * Reads the packet, writes {@code plan.json} into the workspace and returns the plan.
     *
     * @param packet review packet directory (read only)
     * @param workspace recovery workspace (created if absent)
     * @param batchSize maximum units per batch
     * @return the plan
     * @throws ProjectException when the packet or workspace cannot be used
     */
    public DraftRecoveryPlan plan(Path packet, Path workspace, int batchSize) throws ProjectException {
        requireSeparate(packet, workspace);
        DraftRecoveryPlan plan = DraftRecoveryPlan.read(packet, batchSize);
        ObjectNode root = header(plan, batchSize);
        ArrayNode units = root.putArray("units");
        Map<String, Integer> batchOf = new java.util.HashMap<>();
        for (int index = 0; index < plan.batches().size(); index++) {
            for (String id : plan.batches().get(index)) {
                batchOf.put(id, index + 1);
            }
        }
        plan.units().values().forEach(unit -> units.addObject()
                .put("id", unit.id()).put("sourceDigest", unit.sourceDigest()).put("role", unit.role())
                .put("group", unit.group()).put("batch", batchOf.get(unit.id())));
        writeAtomic(workspace.resolve("plan.json"), root);
        return plan;
    }

    /**
     * Writes every batch not yet in the manifest, recording each in the manifest as it is written. Batches already
     * recorded (exported or imported) are left untouched, so a file the owner is filling in is never overwritten.
     *
     * @param packet review packet directory (read only)
     * @param workspace recovery workspace
     * @param batchSize maximum units per batch (must equal the manifest's when one exists)
     * @return counts
     * @throws ProjectException when the inputs changed since the manifest was created or a write fails
     */
    public ExportResult export(Path packet, Path workspace, int batchSize) throws ProjectException {
        requireSeparate(packet, workspace);
        DraftRecoveryPlan plan = DraftRecoveryPlan.read(packet, batchSize);
        ObjectNode manifest = Files.isRegularFile(workspace.resolve(MANIFEST))
                ? (ObjectNode) DraftRecoveryPlan.readJson(workspace.resolve(MANIFEST)) : header(plan, batchSize);
        verifyInputs(manifest, plan);
        if (manifest.path("batchSize").asInt() != batchSize) {
            throw new ProjectException("Batch size " + batchSize + " differs from the recorded "
                    + manifest.path("batchSize").asInt() + "; use a new workspace to re-batch");
        }
        ArrayNode recorded = manifest.has("batches") ? (ArrayNode) manifest.get("batches") : manifest.putArray("batches");
        int written = 0;
        for (int index = recorded.size(); index < plan.batches().size(); index++) {
            String name = batchName(index);
            ObjectNode batch = batchDocument(plan, index, name);
            Path file = workspace.resolve(BATCH_DIR).resolve(name + ".json");
            writeAtomic(file, batch);
            recorded.addObject().put("batch", name).put("file", BATCH_DIR + "/" + name + ".json")
                    .put("units", plan.batches().get(index).size()).put("status", EXPORTED)
                    .put("sha256", DraftRecoveryPlan.sha256File(file));
            writeAtomic(workspace.resolve(MANIFEST), manifest);
            written++;
        }
        writeAtomic(workspace.resolve(MANIFEST), manifest);
        return new ExportResult(written, plan.batches().size() - written, plan.batches().size());
    }

    /**
     * Imports one filled-in batch by identity. The whole batch is accepted or rejected: every planned unit of the
     * batch must be present with its current source digest and a non-blank draft, and every LINKED group must carry
     * one identical text. Accepted drafts are stored as {@value #DRAFT_STATUS}; nothing else is modified.
     *
     * @param packet review packet directory (read only)
     * @param workspace recovery workspace
     * @param batchFile filled-in batch file
     * @return the outcome
     * @throws ProjectException when the batch is stale, has unknown or partial identities, or inputs changed
     */
    public ImportResult importBatch(Path packet, Path workspace, Path batchFile) throws ProjectException {
        requireSeparate(packet, workspace);
        ObjectNode manifest = readManifest(workspace);
        DraftRecoveryPlan plan = DraftRecoveryPlan.read(packet, manifest.path("batchSize").asInt());
        verifyInputs(manifest, plan);
        JsonNode document = DraftRecoveryPlan.readJson(batchFile);
        String name = document.path("batch").asText("");
        JsonNode record = recordOf(manifest, name);
        if (record == null) {
            throw new ProjectException("Batch is not in the manifest: '" + name + "'");
        }
        int index = Integer.parseInt(name.substring(name.indexOf('-') + 1)) - 1;
        if (IMPORTED.equals(record.path("status").asText())) {
            return new ImportResult(name, 0, true);
        }
        Map<String, String> accepted = validate(plan, index, document);
        ObjectNode drafts = Files.isRegularFile(workspace.resolve(DRAFTS))
                ? (ObjectNode) DraftRecoveryPlan.readJson(workspace.resolve(DRAFTS)) : newDrafts(plan);
        Map<String, ObjectNode> merged = new TreeMap<>();
        drafts.path("drafts").forEach(node -> merged.put(node.path("id").asText(), (ObjectNode) node));
        accepted.forEach((id, text) -> merged.put(id, JSON.createObjectNode().put("id", id)
                .put("sourceDigest", plan.units().get(id).sourceDigest()).put("target", text)
                .put("status", DRAFT_STATUS).put("batch", name)));
        ArrayNode list = drafts.putArray("drafts");
        merged.values().forEach(list::add);
        writeAtomic(workspace.resolve(DRAFTS), drafts);
        ((ObjectNode) record).put("status", IMPORTED);
        writeAtomic(workspace.resolve(MANIFEST), manifest);
        return new ImportResult(name, accepted.size(), false);
    }

    /**
     * Reads the workspace counts.
     *
     * @param workspace recovery workspace
     * @return counts
     * @throws ProjectException when there is no manifest
     */
    public Status status(Path workspace) throws ProjectException {
        ObjectNode manifest = readManifest(workspace);
        int imported = 0;
        for (JsonNode batch : manifest.path("batches")) {
            imported += IMPORTED.equals(batch.path("status").asText()) ? 1 : 0;
        }
        int drafted = Files.isRegularFile(workspace.resolve(DRAFTS))
                ? DraftRecoveryPlan.readJson(workspace.resolve(DRAFTS)).path("drafts").size() : 0;
        int units = manifest.path("unitCount").asInt();
        return new Status(units, manifest.path("batches").size(), imported, drafted, units - drafted);
    }

    private static Map<String, String> validate(DraftRecoveryPlan plan, int index, JsonNode document)
            throws ProjectException {
        List<String> problems = new ArrayList<>();
        if (index >= plan.batches().size()) {
            throw new ProjectException("Batch index outside the plan");
        }
        Set<String> expected = new HashSet<>(plan.batches().get(index));
        Map<String, String> texts = new TreeMap<>();
        for (JsonNode unit : document.path("units")) {
            String id = unit.path("id").asText("");
            DraftRecoveryPlan.Unit planned = plan.units().get(id);
            if (planned == null) {
                problems.add("unknown identity: " + id);
            } else if (!expected.contains(id)) {
                problems.add("identity belongs to a different batch: " + id);
            } else if (!planned.sourceDigest().equals(unit.path("sourceDigest").asText())) {
                problems.add("stale source digest: " + id);
            } else if (texts.containsKey(id)) {
                problems.add("duplicate identity: " + id);
            } else if (unit.path("target").asText("").isBlank() || unit.path("target").asText().indexOf('\0') >= 0) {
                problems.add("blank or invalid draft: " + id);
            } else {
                texts.put(id, unit.path("target").asText());
            }
        }
        for (String id : expected) {
            if (!texts.containsKey(id) && problems.stream().noneMatch(problem -> problem.endsWith(": " + id))) {
                String group = plan.units().get(id).group();
                problems.add((group.isEmpty() ? "missing unit: " : "partial LINKED group " + group + ", missing: ") + id);
            }
        }
        plan.groups().forEach((group, members) -> {
            if (expected.contains(members.getFirst()) && texts.keySet().containsAll(members)
                    && members.stream().map(texts::get).distinct().count() > 1) {
                problems.add("LINKED group " + group + " carries different texts");
            }
        });
        if (!problems.isEmpty()) {
            List<String> shown = problems.size() > MAX_REPORTED_PROBLEMS
                    ? problems.subList(0, MAX_REPORTED_PROBLEMS) : problems;
            throw new ProjectException("Batch rejected, nothing imported (" + problems.size() + " problem(s)):\n"
                    + String.join("\n", shown));
        }
        return texts;
    }

    private static ObjectNode header(DraftRecoveryPlan plan, int batchSize) {
        ObjectNode root = JSON.createObjectNode();
        root.put("schemaVersion", 1);
        root.put("kind", "DRAFT_RECOVERY");
        root.put("reviewSha256", plan.reviewSha256());
        root.put("approvedSha256", plan.approvedSha256());
        root.put("planDigest", plan.planDigest());
        root.put("batchSize", batchSize);
        root.put("unitCount", plan.units().size());
        root.put("groupCount", plan.groupCount());
        root.put("batchCount", plan.batches().size());
        return root;
    }

    private static ObjectNode newDrafts(DraftRecoveryPlan plan) {
        ObjectNode root = JSON.createObjectNode();
        root.put("schemaVersion", 1);
        root.put("kind", "DRAFT_RECOVERY_DRAFTS");
        root.put("planDigest", plan.planDigest());
        root.put("note", "Drafts only. Nothing here is approved; normal validation and approval still apply.");
        return root;
    }

    private static ObjectNode batchDocument(DraftRecoveryPlan plan, int index, String name) {
        ObjectNode root = JSON.createObjectNode();
        root.put("schemaVersion", 1);
        root.put("kind", "DRAFT_RECOVERY_BATCH");
        root.put("batch", name);
        root.put("planDigest", plan.planDigest());
        root.put("instructions", "Fill every target. Units of one LINKED group need one identical target. "
                + "Do not edit id or sourceDigest. Targets are imported as drafts needing review.");
        ArrayNode units = root.putArray("units");
        for (String id : plan.batches().get(index)) {
            DraftRecoveryPlan.Unit unit = plan.units().get(id);
            ObjectNode node = units.addObject();
            node.put("id", id).put("sourceDigest", unit.sourceDigest()).put("role", unit.role())
                    .put("kind", unit.kind()).put("file", unit.file()).put("group", unit.group());
            node.set("context", unit.context());
            node.put("source", unit.source()).put("target", "");
        }
        return root;
    }

    private static void verifyInputs(ObjectNode manifest, DraftRecoveryPlan plan) throws ProjectException {
        if (!manifest.path("reviewSha256").asText().equals(plan.reviewSha256())
                || !manifest.path("approvedSha256").asText().equals(plan.approvedSha256())
                || !manifest.path("planDigest").asText().equals(plan.planDigest())) {
            throw new ProjectException("The review packet changed since the manifest was created (stale); "
                    + "the earlier project and approved entries must stay untouched. Use a new workspace.");
        }
    }

    private static ObjectNode readManifest(Path workspace) throws ProjectException {
        Path file = workspace.resolve(MANIFEST);
        if (!Files.isRegularFile(file)) {
            throw new ProjectException("No recovery manifest in " + workspace + "; run export first");
        }
        return (ObjectNode) DraftRecoveryPlan.readJson(file);
    }

    private static JsonNode recordOf(ObjectNode manifest, String name) {
        for (JsonNode batch : manifest.path("batches")) {
            if (batch.path("batch").asText().equals(name)) {
                return batch;
            }
        }
        return null;
    }

    private static String batchName(int index) {
        return String.format("batch-%04d", index + 1);
    }

    private static void requireSeparate(Path packet, Path workspace) throws ProjectException {
        Path packetPath = packet.toAbsolutePath().normalize();
        Path workspacePath = workspace.toAbsolutePath().normalize();
        if (workspacePath.startsWith(packetPath) || packetPath.startsWith(workspacePath)) {
            throw new ProjectException("The workspace must be separate from the review packet, which stays read only");
        }
    }

    private static void writeAtomic(Path file, JsonNode content) throws ProjectException {
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
        DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
        printer.indentObjectsWith(indenter);
        printer.indentArraysWith(indenter);
        try {
            Files.createDirectories(java.util.Objects.requireNonNull(file.toAbsolutePath().getParent()));
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(temporary, (JSON.writer(printer).writeValueAsString(content) + "\n")
                    .getBytes(StandardCharsets.UTF_8));
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new ProjectException("Could not write " + file, exception);
        }
    }
}
