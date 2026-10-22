package com.ssmt.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Read-only selection of review units whose target text is missing, grouped and cut into bounded batches. A LINKED
 * group is one indivisible item, so it is never split across batches (a group larger than the batch size gets a batch
 * of its own). Identity is the unit id plus the SHA-256 of its exact source text.
 *
 * @param units selected units in deterministic order, keyed by unit id
 * @param batches batches of unit ids; each LINKED group stays inside one batch
 * @param groupCount number of LINKED groups among the selected units
 * @param reviewSha256 digest of the review document the plan was read from
 * @param approvedSha256 digest of the approved-entry copy next to it, or empty when absent
 */
public record DraftRecoveryPlan(
        Map<String, Unit> units,
        List<List<String>> batches,
        int groupCount,
        String reviewSha256,
        String approvedSha256) {
    /** Name of the review document inside a review packet. */
    public static final String REVIEW_FILE = "review.json";
    /** Name of the approved-entry copy inside a review packet. */
    public static final String APPROVED_FILE = "approved.json";
    private static final long MAX_BYTES = 128L * 1024 * 1024;
    private static final ObjectMapper JSON = new ObjectMapper();

    public DraftRecoveryPlan {
        units = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(units));
        batches = List.copyOf(batches);
    }

    @Override
    public Map<String, Unit> units() {
        return java.util.Collections.unmodifiableMap(units);
    }

    @Override
    public List<List<String>> batches() {
        return List.copyOf(batches);
    }

    /**
     * One unit that needs a draft.
     *
     * @param id stable unit identity
     * @param sourceDigest SHA-256 of the exact source text
     * @param role unit role (TEXT or LINKED)
     * @param kind source kind
     * @param file source file
     * @param source exact source text
     * @param group LINKED group id, or empty
     * @param context kind-specific location
     */
    public record Unit(String id, String sourceDigest, String role, String kind, String file, String source,
            String group, JsonNode context) { }

    /**
     * Reads a review packet and plans batches.
     *
     * @param packet directory holding {@code review.json} (read only)
     * @param batchSize maximum units per batch (a LINKED group may exceed it, never split)
     * @return the plan
     * @throws ProjectException when the packet is unreadable or malformed
     */
    public static DraftRecoveryPlan read(Path packet, int batchSize) throws ProjectException {
        if (batchSize < 1) {
            throw new ProjectException("Batch size must be at least 1");
        }
        Path review = packet.resolve(REVIEW_FILE);
        Path approved = packet.resolve(APPROVED_FILE);
        JsonNode entries = readJson(review).path("entries");
        if (!entries.isArray()) {
            throw new ProjectException("Review packet has no entries array: " + review);
        }
        Map<String, Unit> selected = new TreeMap<>();
        for (JsonNode entry : entries) {
            if (!missing(entry)) {
                continue;
            }
            Unit unit = unit(entry);
            if (selected.put(unit.id(), unit) != null) {
                throw new ProjectException("Duplicate unit identity in review packet: " + unit.id());
            }
        }
        Map<String, List<String>> groups = new TreeMap<>();
        List<List<String>> items = new ArrayList<>();
        for (Unit unit : selected.values()) {
            if (unit.group().isEmpty()) {
                items.add(List.of(unit.id()));
            } else {
                groups.computeIfAbsent(unit.group(), ignored -> new ArrayList<>()).add(unit.id());
            }
        }
        groups.values().forEach(items::add);
        items.sort(Comparator.comparing(item -> item.getFirst()));
        return new DraftRecoveryPlan(selected, pack(items, batchSize), groups.size(), sha256File(review),
                Files.isRegularFile(approved) ? sha256File(approved) : "");
    }

    /**
     * @return digest over every planned identity, independent of batch size
     */
    public String planDigest() {
        StringBuilder text = new StringBuilder();
        units.values().forEach(unit -> text.append(unit.id()).append('|').append(unit.sourceDigest()).append('\n'));
        return sha256(text.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @return units grouped by LINKED group id, in plan order
     */
    public Map<String, List<String>> groups() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        units.values().stream().filter(unit -> !unit.group().isEmpty())
                .forEach(unit -> result.computeIfAbsent(unit.group(), ignored -> new ArrayList<>()).add(unit.id()));
        return result;
    }

    /**
     * @param sourceText exact source text
     * @return lower-case hex SHA-256 of its UTF-8 bytes
     */
    public static String sourceDigestOf(String sourceText) {
        return sha256(sourceText.getBytes(StandardCharsets.UTF_8));
    }

    static JsonNode readJson(Path file) throws ProjectException {
        try {
            if (Files.size(file) > MAX_BYTES) {
                throw new ProjectException("File is larger than the " + MAX_BYTES + " byte limit: " + file);
            }
            return JSON.readTree(file.toFile());
        } catch (IOException exception) {
            throw new ProjectException("Could not read " + file, exception);
        }
    }

    static String sha256File(Path file) throws ProjectException {
        try {
            return sha256(Files.readAllBytes(file));
        } catch (IOException exception) {
            throw new ProjectException("Could not hash " + file, exception);
        }
    }

    private static boolean missing(JsonNode entry) {
        String role = entry.path("role").asText("");
        return !"IDENTIFIER".equals(role)
                && entry.path("current_target").asText("").isBlank()
                && entry.path("retained_target").asText("").isBlank();
    }

    private static Unit unit(JsonNode entry) throws ProjectException {
        String id = entry.path("id").asText("");
        if (id.isBlank() || !entry.path("source").isTextual()) {
            throw new ProjectException("Review entry without id or source text");
        }
        String source = entry.path("source").asText();
        boolean linked = "LINKED".equals(entry.path("role").asText());
        String group = linked ? entry.path("group").asText("") : "";
        if (linked && group.isEmpty()) {
            throw new ProjectException("LINKED unit without a group: " + id);
        }
        return new Unit(id, sourceDigestOf(source), entry.path("role").asText(), entry.path("kind").asText(),
                entry.path("file").asText(), source, group, entry.path("context").deepCopy());
    }

    private static List<List<String>> pack(List<List<String>> items, int batchSize) {
        List<List<String>> batches = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (List<String> item : items) {
            if (!current.isEmpty() && current.size() + item.size() > batchSize) {
                batches.add(List.copyOf(current));
                current = new ArrayList<>();
            }
            current.addAll(item);
        }
        if (!current.isEmpty()) {
            batches.add(List.copyOf(current));
        }
        return List.copyOf(batches);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
