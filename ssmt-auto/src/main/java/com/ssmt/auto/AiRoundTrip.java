package com.ssmt.auto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssmt.ai.AiProviderException;
import com.ssmt.ai.AiTranslationProvider;
import com.ssmt.ai.AiTranslationRequest;
import com.ssmt.core.model.TranslationProvenance;
import com.ssmt.project.LocalizationProject;
import com.ssmt.project.PolicyApproval;
import com.ssmt.project.ProjectEntry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Sends the strings still untranslated to a configured provider (opt-in, never by default) in chunks, one progress line
 * per chunk, appending each finished string to a checkpoint so a stopped run resumes instead of starting over. Results
 * are drafts: one is applied, as POLICY_APPROVED, only when its protected syntax survived and every approval rule
 * passes; the rest are listed for review and stay untranslated. The checkpoint is deleted once the whole round trip
 * has been processed.
 */
final class AiRoundTrip {
    /** Outcome of one round trip. */
    record Outcome(LocalizationProject project, int approved, List<String> review, String failure) {
        boolean complete() {
            return failure == null;
        }
    }

    static final int CHUNK_SIZE = 20;
    private static final ObjectMapper JSON = new ObjectMapper();
    private final AiTranslationProvider provider;
    private final String providerLabel;
    private final Consumer<String> progress;
    private final int chunkSize;

    AiRoundTrip(AiTranslationProvider provider, String providerLabel, Consumer<String> progress, int chunkSize) {
        this.provider = provider;
        this.providerLabel = providerLabel;
        this.progress = progress;
        this.chunkSize = chunkSize;
    }

    Outcome run(LocalizationProject project, List<ProjectEntry> pending, Path checkpoint, String glossary)
            throws IOException {
        String inputs = inputsHash(pending);
        Map<String, String> done = readCheckpoint(checkpoint, inputs);
        if (done == null) {
            done = new LinkedHashMap<>();
            Files.writeString(checkpoint, JSON.writeValueAsString(Map.of("kind", "header", "inputs", inputs)) + "\n",
                    StandardCharsets.UTF_8);
        }
        long started = System.nanoTime();
        String failure = null;
        int index = 0;
        for (int from = 0; from < pending.size() && failure == null; from += chunkSize) {
            List<ProjectEntry> chunk = pending.subList(from, Math.min(pending.size(), from + chunkSize));
            for (ProjectEntry entry : chunk) {
                index++;
                if (done.containsKey(entry.key())) {
                    continue;
                }
                try {
                    String draft = provider.translate(new AiTranslationRequest(entry.originalText(), "zh", "en",
                            entry.sourceFile().toString().replace('\\', '/'), glossary));
                    done.put(entry.key(), draft);
                    Files.writeString(checkpoint, JSON.writeValueAsString(Map.of("key", entry.key(),
                            "translation", draft)) + "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
                } catch (AiProviderException exception) {
                    failure = "Provider " + providerLabel + " failed at string " + index + " of " + pending.size()
                            + ": " + exception.getMessage() + ". Progress is kept; run again to resume.";
                    break;
                }
            }
            progress.accept(String.format(java.util.Locale.ROOT, "[%d/%d] AI chunk via %s (%.1fs)",
                    Math.min(index, pending.size()), pending.size(), providerLabel,
                    (System.nanoTime() - started) / 1e9));
        }
        if (failure != null) {
            return new Outcome(project, 0, List.of(), failure);
        }
        Outcome outcome = judge(project, pending, done);
        Files.deleteIfExists(checkpoint);
        return outcome;
    }

    private static Outcome judge(LocalizationProject project, List<ProjectEntry> pending, Map<String, String> drafts) {
        var rules = PolicyApproval.withDefaultRules().forProject(project);
        Map<String, String> accepted = new HashMap<>();
        List<String> review = new ArrayList<>();
        for (ProjectEntry entry : pending) {
            String draft = drafts.get(entry.key());
            if (draft == null || draft.isBlank()) {
                review.add(entry.key() + ": the provider returned nothing");
                continue;
            }
            String resolved = PolicyApproval.validated(entry.originalText(), draft);
            if (resolved == null) {
                review.add(entry.key() + ": protected syntax (placeholders, markers or option ids) differs");
                continue;
            }
            var decision = rules.evaluate(entry, resolved);
            if (decision.approved()) {
                accepted.put(entry.key(), resolved);
            } else {
                review.add(entry.key() + ": " + decision.reason());
            }
        }
        LocalizationProject result = project.withEntries(project.entries().stream()
                .map(entry -> accepted.containsKey(entry.key())
                        ? entry.withTranslation(accepted.get(entry.key()), TranslationProvenance.POLICY_APPROVED)
                        : entry)
                .toList());
        return new Outcome(result, accepted.size(), List.copyOf(review), null);
    }

    private static Map<String, String> readCheckpoint(Path checkpoint, String inputs) throws IOException {
        if (!Files.isRegularFile(checkpoint)) {
            return null;
        }
        List<String> lines = Files.readAllLines(checkpoint, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            return null;
        }
        JsonNode header = JSON.readTree(lines.getFirst());
        if (!"header".equals(header.path("kind").asText()) || !inputs.equals(header.path("inputs").asText())) {
            return null; // a checkpoint of other inputs is never reused
        }
        Map<String, String> done = new LinkedHashMap<>();
        for (String line : lines.subList(1, lines.size())) {
            try {
                ObjectNode item = (ObjectNode) JSON.readTree(line);
                done.put(item.path("key").asText(), item.path("translation").asText());
            } catch (IOException | ClassCastException truncated) {
                break; // a last line cut off by the interruption is simply sent again
            }
        }
        return done;
    }

    private String inputsHash(List<ProjectEntry> pending) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(providerLabel.getBytes(StandardCharsets.UTF_8));
            for (ProjectEntry entry : pending) {
                digest.update((byte) 0);
                digest.update((entry.key() + "\u0001" + entry.originalText()).getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
