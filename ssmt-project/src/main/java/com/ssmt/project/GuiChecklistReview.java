package com.ssmt.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Owner review of the GUI visual checklist ({@code checklist.json} plus its screenshots, written by
 * {@code GuiChecklistTest}). The owner's decisions live in {@code owner-review.json} next to the checklist, so a new
 * checklist run cannot overwrite them. Each decision is bound to the SHA-256 of the row's evidence (automated result,
 * program observation and every screenshot's bytes). A row needs review when it has no decision, when its last decision
 * is {@code NOT_TESTED}, or when its evidence differs from the hash recorded with the decision. A decision recorded
 * without a hash (an older receipt) counts as current. Nothing here decides anything: only {@link #record} writes,
 * and only when called with an explicit owner status.
 */
public final class GuiChecklistReview {
    /** File name of the owner's receipt, beside {@code checklist.json}. */
    public static final String RECEIPT_FILE = "owner-review.json";
    private static final String CHECKLIST_FILE = "checklist.json";
    private static final ObjectMapper JSON = new ObjectMapper();

    /** The owner's allowed statuses. */
    public enum Status { PASS, FAIL, NOT_TESTED }

    /** Why a row is (or is not) in the inbox. */
    public enum State { UNREVIEWED, EVIDENCE_CHANGED, CURRENT }

    /** A recorded owner decision. {@code evidenceSha256} is empty for receipts written before evidence binding. */
    public record Decision(Status status, String note, String decidedAt, String evidenceSha256) { }

    /** One checklist row with its evidence hash, prior decision and state. */
    public record Row(int number, String scenario, String expected, String automated, String observed,
            List<Path> screenshots, String evidenceSha256, Optional<Decision> decision, State state) {
        public Row {
            screenshots = List.copyOf(screenshots);
        }

        /** Immutable copy, so the record exposes no mutable state. */
        @Override public List<Path> screenshots() {
            return List.copyOf(screenshots);
        }

        /** True when the row belongs in the review inbox. */
        public boolean needsReview() {
            return state != State.CURRENT;
        }
    }

    private final Path directory;

    /** Reviews the checklist in {@code directory} (normally {@code ssmt-gui/build/gui-checklist}). */
    public GuiChecklistReview(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    /** All checklist rows in checklist order. */
    public List<Row> rows() throws IOException {
        Path checklist = directory.resolve(CHECKLIST_FILE);
        if (!Files.isRegularFile(checklist)) {
            throw new IOException("No " + CHECKLIST_FILE + " in " + directory + "; run gradlew :ssmt-gui:guiChecklist");
        }
        JsonNode rows = JSON.readTree(checklist.toFile()).path("rows");
        if (!rows.isArray()) {
            throw new IOException(CHECKLIST_FILE + " has no rows array");
        }
        JsonNode decisions = readReceipt().path("decisions");
        List<Row> result = new ArrayList<>();
        for (JsonNode row : rows) {
            result.add(toRow(result.size() + 1, row, decisions));
        }
        return List.copyOf(result);
    }

    /** Only the rows that are unreviewed, deferred as NOT_TESTED, or whose evidence changed. */
    public List<Row> inbox() throws IOException {
        return rows().stream().filter(Row::needsReview).toList();
    }

    /**
     * Records the owner's decision for the row with {@code number} (1-based, checklist order). The caller must pass an
     * explicit status; FAIL requires a note. The receipt is replaced atomically, keeping other rows' decisions.
     */
    public Row record(int number, Status status, String note) throws IOException {
        List<Row> all = rows();
        if (number < 1 || number > all.size()) {
            throw new IllegalArgumentException("Row number must be 1.." + all.size());
        }
        String cleanNote = note == null ? "" : note.strip();
        if (status == Status.FAIL && cleanNote.isEmpty()) {
            throw new IllegalArgumentException("A FAIL decision needs a note saying what was wrong");
        }
        Row target = all.get(number - 1);
        ObjectNode receipt = readReceipt();
        ArrayNode kept = JSON.createArrayNode();
        for (JsonNode existing : receipt.path("decisions")) {
            if (!target.scenario().equals(existing.path("scenario").asText())) {
                kept.add(existing);
            }
        }
        kept.addObject().put("scenario", target.scenario()).put("owner_status", status.name())
                .put("note", cleanNote).put("decided_at", Instant.now().toString())
                .put("evidence_sha256", target.evidenceSha256());
        ObjectNode updated = JSON.createObjectNode().put("schema_version", 1);
        updated.set("decisions", kept);
        writeAtomically(directory.resolve(RECEIPT_FILE),
                JSON.writerWithDefaultPrettyPrinter().writeValueAsString(updated));
        return rows().get(number - 1);
    }

    private ObjectNode readReceipt() throws IOException {
        Path file = directory.resolve(RECEIPT_FILE);
        if (!Files.isRegularFile(file)) {
            return JSON.createObjectNode();
        }
        JsonNode node = JSON.readTree(file.toFile());
        if (!(node instanceof ObjectNode object) || !node.path("decisions").isArray()) {
            throw new IOException(RECEIPT_FILE + " is not a decisions receipt; not overwriting it");
        }
        return object;
    }

    private Row toRow(int number, JsonNode row, JsonNode decisions) throws IOException {
        String scenario = row.path("scenario").asText();
        List<Path> shots = new ArrayList<>();
        for (JsonNode shot : row.path("screenshots")) {
            Path resolved = directory.resolve(shot.asText()).normalize();
            if (!resolved.startsWith(directory)) {
                throw new IOException("Screenshot path leaves the checklist folder: " + shot.asText());
            }
            shots.add(resolved);
        }
        String hash = evidenceHash(row, shots);
        Optional<Decision> decision = Optional.empty();
        for (JsonNode candidate : decisions) {
            if (scenario.equals(candidate.path("scenario").asText())) {
                decision = Optional.of(new Decision(Status.valueOf(candidate.path("owner_status").asText()),
                        candidate.path("note").asText(), candidate.path("decided_at").asText(),
                        candidate.path("evidence_sha256").asText("")));
            }
        }
        return new Row(number, scenario, row.path("expected").asText(), row.path("automated").asText(),
                row.path("observed_by_program").asText(), List.copyOf(shots), hash, decision,
                stateOf(decision, hash));
    }

    private static State stateOf(Optional<Decision> decision, String currentHash) {
        if (decision.isEmpty() || decision.get().status() == Status.NOT_TESTED) {
            return State.UNREVIEWED;
        }
        String bound = decision.get().evidenceSha256();
        return bound.isEmpty() || bound.equals(currentHash) ? State.CURRENT : State.EVIDENCE_CHANGED;
    }

    private static String evidenceHash(JsonNode row, List<Path> shots) throws IOException {
        StringBuilder text = new StringBuilder();
        text.append(row.path("automated").asText()).append('\n').append(row.path("observed_by_program").asText());
        for (Path shot : shots) {
            text.append('\n').append(shot.getFileName()).append(':')
                    .append(Files.isRegularFile(shot) ? sha256(Files.readAllBytes(shot)) : "MISSING");
        }
        return sha256(text.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void writeAtomically(Path target, String content) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temporary, content, StandardCharsets.UTF_8);
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
