package com.ssmt.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Manual export/open/import bridge for browser-based AI review. */
public final class BrowserAiReviewService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PROMPT = """
            Review every entry in TRANSLATION_REQUEST.json.

            For each entry use:
            Source:
            [source]

            Local machine draft:
            [localMachineDraft]

            Context:
            [context and terminology]

            Instruction:
            Produce polished Starsector English. Preserve mechanics, protected syntax,
            line breaks, terminology, and creator intent. Correct the local draft where
            needed. Do not invent lore or mechanics.

            Fill every entries[].translation. Return the complete JSON object only.
            Do not change IDs, source text, provenance, schema fields, or entry order.
            """;
    private static final String README = """
            SSMT manual browser AI review

            1. Review PROMPT.txt and TRANSLATION_REQUEST.json.
            2. Open your chosen AI website using SSMT or your normal browser.
            3. You decide whether to paste/upload these files to that third party.
            4. Save the complete returned JSON and import it with SSMT.

            SSMT does not upload, log in, type, click, download, or scrape anything.
            Imported results are unapproved drafts and cannot replace existing text.
            """;

    private final AiTranslationExchangeService exchange = new AiTranslationExchangeService();

    public BrowserAiReviewExport export(
            Path destination,
            LocalizationProject project,
            String modName,
            String sourceLanguage,
            String targetLanguage,
            int maximumEntriesPerPart,
            String terminology) throws ProjectException {
        if (maximumEntriesPerPart < 1) {
            throw new IllegalArgumentException("maximumEntriesPerPart must be positive");
        }
        List<ProjectEntry> entries = project.entries().stream()
                .sorted(Comparator.comparing(BrowserAiReviewService::identity))
                .toList();
        int partCount = Math.max(1,
                (entries.size() + maximumEntriesPerPart - 1) / maximumEntriesPerPart);
        try {
            Files.createDirectories(destination);
            writeText(destination.resolve("PROMPT.txt"), PROMPT);
            writeText(destination.resolve("README.txt"), README);
            List<Path> parts = new ArrayList<>();
            for (int index = 0; index < partCount; index++) {
                Path part = partCount == 1
                        ? destination
                        : destination.resolve("part-%03d".formatted(index + 1));
                Files.createDirectories(part);
                if (partCount > 1) {
                    writeText(part.resolve("PROMPT.txt"), PROMPT);
                    writeText(part.resolve("README.txt"), README);
                }
                int from = index * maximumEntriesPerPart;
                int to = Math.min(entries.size(), from + maximumEntriesPerPart);
                Path request = part.resolve("TRANSLATION_REQUEST.json");
                exchange.exportPackage(request, project, entries.subList(from, to),
                        modName, sourceLanguage, targetLanguage);
                enrich(request, terminology == null ? "" : terminology);
                parts.add(part);
            }
            Optional<Path> manifest = partCount > 1
                    ? Optional.of(writeManifest(destination, project, parts))
                    : Optional.empty();
            return new BrowserAiReviewExport(destination, parts, manifest);
        } catch (IOException exception) {
            throw new ProjectException("Could not export browser AI review package", exception);
        }
    }

    public AiTranslationImportResult importResponse(
            Path response, LocalizationProject project) throws ProjectException {
        AiTranslationImportResult imported = exchange.importResponse(response, project, null);
        List<ProjectEntry> merged = new ArrayList<>();
        for (int index = 0; index < project.entries().size(); index++) {
            ProjectEntry existing = project.entries().get(index);
            ProjectEntry candidate = imported.project().entries().get(index);
            merged.add(existing.translatedText().isBlank() ? candidate : existing);
        }
        return new AiTranslationImportResult(
                imported.project().withEntries(merged), imported.importedEntries(),
                imported.skippedEntries(), imported.patchNameUpdated());
    }

    private static void enrich(Path request, String terminology) throws IOException {
        ObjectNode root = (ObjectNode) JSON.readTree(request.toFile());
        root.put("exchangeType", "manual-browser-review");
        ArrayNode entries = root.withArray("entries");
        for (var node : entries) {
            ObjectNode entry = (ObjectNode) node;
            entry.put("localMachineDraft", findExistingDraft(entry));
            entry.put("context", entry.path("relativeFilePath").asText()
                    + "#" + entry.path("internalId").asText());
            entry.put("terminology", terminology);
        }
        writeJson(request, root);
    }

    private static String findExistingDraft(ObjectNode entry) {
        return entry.has("authorLocalization")
                ? entry.path("authorLocalization").asText("")
                : entry.path("existingTranslation").asText("");
    }

    private static Path writeManifest(
            Path destination, LocalizationProject project, List<Path> parts) throws IOException {
        ObjectNode manifest = JSON.createObjectNode();
        manifest.put("schemaVersion", 1);
        manifest.put("sourceModId", project.sourceModId());
        manifest.put("partCount", parts.size());
        ArrayNode values = manifest.putArray("parts");
        for (Path part : parts) {
            ObjectNode value = values.addObject();
            Path partName = Objects.requireNonNull(part.getFileName(), "part name");
            value.put("part", partName.toString());
            value.put("request", partName + "/TRANSLATION_REQUEST.json");
        }
        Path result = destination.resolve("manifest.json");
        writeJson(result, manifest);
        return result;
    }

    private static void writeText(Path destination, String value) throws IOException {
        writeAtomically(destination, value.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeJson(Path destination, ObjectNode value) throws IOException {
        Path parent = Objects.requireNonNull(destination.getParent(), "destination parent");
        Path staged = Files.createTempFile(parent, "browser-ai-", ".json");
        try {
            JSON.writerWithDefaultPrettyPrinter().writeValue(staged.toFile(), value);
            Files.move(staged, destination, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    private static void writeAtomically(Path destination, byte[] value) throws IOException {
        Path parent = Objects.requireNonNull(destination.getParent(), "destination parent");
        Path staged = Files.createTempFile(parent, "browser-ai-", ".tmp");
        try {
            Files.write(staged, value);
            Files.move(staged, destination, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    private static String identity(ProjectEntry entry) {
        return entry.sourceFile().toString().replace('\\', '/') + "#" + entry.key();
    }
}
