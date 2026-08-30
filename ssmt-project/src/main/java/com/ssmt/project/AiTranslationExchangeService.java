package com.ssmt.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssmt.core.model.TranslationProvenance;
import com.ssmt.tm.SqliteTranslationMemory;
import com.ssmt.tm.TranslationDraft;
import com.ssmt.tm.TranslationGenerationMetadata;
import com.ssmt.tm.TranslationMemoryException;
import com.ssmt.tm.TranslationReviewStatus;
import com.ssmt.validation.TranslationValidator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministic, ID-keyed exchange format for translations produced by an external AI.
 */
public final class AiTranslationExchangeService {
    private static final int SCHEMA_VERSION = 1;
    private static final ObjectMapper JSON = new ObjectMapper();
    private final TranslationValidator validator = new TranslationValidator();

    /**
     * Writes source strings and response instructions for an online AI.
     *
     * @param destination user-owned JSON destination
     * @param project active project
     * @param originalModName original metadata name
     * @param sourceLanguage source language
     * @param targetLanguage requested language
     * @throws ProjectException on write failure
     */
    public void exportPackage(
            Path destination,
            LocalizationProject project,
            String originalModName,
            String sourceLanguage,
            String targetLanguage) throws ProjectException {
        exportPackage(
                destination,
                project,
                project.entries(),
                originalModName,
                sourceLanguage,
                targetLanguage);
    }

    /**
     * Writes an AI package containing only the supplied missing entries.
     *
     * @param destination user-owned JSON destination
     * @param project owning project
     * @param selectedEntries entries still requiring translation
     * @param originalModName original metadata name
     * @param sourceLanguage source language
     * @param targetLanguage target language
     * @throws ProjectException on write failure
     */
    public void exportPackage(
            Path destination,
            LocalizationProject project,
            List<ProjectEntry> selectedEntries,
            String originalModName,
            String sourceLanguage,
            String targetLanguage) throws ProjectException {
        requireText(originalModName, "originalModName");
        requireText(sourceLanguage, "sourceLanguage");
        requireText(targetLanguage, "targetLanguage");
        ObjectNode root = JSON.createObjectNode();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("sourceModId", project.sourceModId());
        root.put("originalModName", originalModName);
        root.put("translatedModName", "");
        root.put("sourceLanguage", sourceLanguage);
        root.put("targetLanguage", targetLanguage);
        root.put("allowBlankTranslations", false);
        root.put("preserveLineBreaks", true);
        root.put("instructions", instructions(targetLanguage));
        ArrayNode entries = root.putArray("entries");
        List<String> exportedIds = new ArrayList<>();
        for (ProjectEntry entry : List.copyOf(selectedEntries)) {
            ObjectNode item = entries.addObject();
            String id = identity(entry);
            exportedIds.add(id);
            item.put("id", id);
            item.put("source", entry.originalText());
            item.put("translation", "");
            item.put("relativeFilePath",
                    entry.sourceFile().toString().replace('\\', '/'));
            item.put("internalId", entry.key());
            item.put("contentType", contentType(entry.sourceFile()));
            item.put("provenance", entry.provenance().name());
            item.put("existingTranslation", entry.translatedText());
            item.put("modName", originalModName);
            if (entry.provenance() == TranslationProvenance.AUTHOR_LOCALIZATION
                    && !entry.translatedText().isBlank()) {
                item.put("authorLocalization", entry.translatedText());
            }
        }
        root.put("entryCount", exportedIds.size());
        root.put("entryIdsSha256", identityDigest(exportedIds));
        writeAtomically(destination, root);
    }

    /**
     * Writes an AI package, splitting it across sibling numbered files when the project has more
     * than {@code maximumEntriesPerPart} entries so a single AI response is never truncated.
     * With one part or fewer, the file keeps {@code destination}'s exact name unchanged.
     *
     * @param destination user-owned JSON destination; sibling parts reuse its name plus an index
     * @param project owning project
     * @param originalModName original metadata name
     * @param sourceLanguage source language
     * @param targetLanguage target language
     * @param maximumEntriesPerPart maximum entries in one file; must be positive
     * @return every file written, in part order
     * @throws ProjectException on write failure
     */
    public List<Path> exportPackage(
            Path destination,
            LocalizationProject project,
            String originalModName,
            String sourceLanguage,
            String targetLanguage,
            int maximumEntriesPerPart) throws ProjectException {
        if (maximumEntriesPerPart < 1) {
            throw new IllegalArgumentException("maximumEntriesPerPart must be positive");
        }
        List<ProjectEntry> entries = project.entries();
        int partCount = Math.max(1,
                (entries.size() + maximumEntriesPerPart - 1) / maximumEntriesPerPart);
        if (partCount == 1) {
            exportPackage(destination, project, originalModName, sourceLanguage, targetLanguage);
            return List.of(destination);
        }
        List<Path> parts = new ArrayList<>();
        for (int index = 0; index < partCount; index++) {
            int from = index * maximumEntriesPerPart;
            int to = Math.min(entries.size(), from + maximumEntriesPerPart);
            Path part = numberedSibling(destination, index + 1);
            exportPackage(part, project, entries.subList(from, to),
                    originalModName, sourceLanguage, targetLanguage);
            parts.add(part);
        }
        return parts;
    }

    private static Path numberedSibling(Path destination, int partNumber) {
        Path fileName = Objects.requireNonNull(destination.getFileName(), "destination filename");
        String name = fileName.toString();
        int extension = name.lastIndexOf('.');
        String base = extension < 0 ? name : name.substring(0, extension);
        String suffix = extension < 0 ? "" : name.substring(extension);
        return destination.resolveSibling(base + partNumber + suffix);
    }

    /**
     * Validates and applies an AI response. Unknown, duplicate, or source-altered IDs fail closed.
     *
     * @param response response JSON
     * @param project active project
     * @param translationMemory optional SQLite pool
     * @return updated reviewable project and counts
     * @throws ProjectException on malformed or mismatched input
     */
    public AiTranslationImportResult importResponse(
            Path response,
            LocalizationProject project,
            Path translationMemory) throws ProjectException {
        return importResponse(
                response, project, translationMemory, AiImportPolicy.REVIEW_DRAFTS);
    }

    /**
     * Validates and applies an AI response under an explicit review policy.
     * Bulk approval is fail-closed: every supplied nonblank result must pass validation before
     * any project or translation-memory state is changed.
     */
    public AiTranslationImportResult importResponse(
            Path response,
            LocalizationProject project,
            Path translationMemory,
            AiImportPolicy policy) throws ProjectException {
        Objects.requireNonNull(policy, "policy");
        try {
            JsonNode root = JSON.readTree(response.toFile());
            if (root.path("schemaVersion").asInt(-1) != SCHEMA_VERSION) {
                throw invalid("SCHEMA_VERSION", "",
                        "AI response schema version is unsupported");
            }
            if (!project.sourceModId().equals(root.path("sourceModId").asText())) {
                throw invalid("SOURCE_MOD_ID", "",
                        "AI response does not match this project");
            }
            if (!root.path("entries").isArray()) {
                throw invalid("ENTRIES", "", "AI response entries must be an array");
            }
            Map<String, ProjectEntry> expected = new HashMap<>();
            for (ProjectEntry entry : project.entries()) {
                expected.put(identity(entry), entry);
            }
            Map<String, String> translations = new HashMap<>();
            Set<String> seen = new HashSet<>();
            boolean allowBlank = root.path("allowBlankTranslations").asBoolean(true);
            boolean preserveLineBreaks = root.path("preserveLineBreaks").asBoolean(false);
            for (JsonNode item : root.path("entries")) {
                String id = item.path("id").asText();
                ProjectEntry entry = expected.get(id);
                if (entry == null || !seen.add(id)) {
                    throw invalid("ENTRY_ID", id,
                            "AI response contains an unknown or duplicate id");
                }
                if (!entry.originalText().equals(item.path("source").asText())) {
                    throw invalid("SOURCE_CHANGED", id,
                            "AI response changed protected source text");
                }
                String translation = item.path("translation").asText("");
                if (item.has("provenance")) {
                    try {
                        TranslationProvenance responseProvenance = TranslationProvenance.valueOf(
                                item.path("provenance").asText());
                        if (responseProvenance != entry.provenance()) {
                            throw invalid("PROVENANCE", id,
                                    "AI response changed provenance at " + id);
                        }
                    } catch (IllegalArgumentException exception) {
                        throw invalid("PROVENANCE", id,
                                "AI response contains invalid provenance at " + id);
                    }
                }
                if (translation.isBlank()) {
                    if (!allowBlank) {
                        throw invalid("BLANK_TRANSLATION", id,
                                "AI response contains a blank translation at " + id);
                    }
                    continue;
                }
                var issues = validator.validate(entry.originalText(), translation);
                if (!issues.isEmpty()) {
                    throw invalid("PROTECTED_SYNTAX", id,
                            "AI response has invalid protected syntax at " + id
                                    + ": " + issues.getFirst().message());
                }
                if (preserveLineBreaks
                        && lineBreakCount(entry.originalText())
                                != lineBreakCount(translation)) {
                    throw invalid("LINE_BREAKS", id,
                            "AI response changed required line breaks at " + id);
                }
                translations.put(id, translation);
            }
            if (root.has("entryCount")
                    && root.path("entryCount").asInt(-1) != seen.size()) {
                throw invalid("ENTRY_COUNT", "",
                        "AI response entry count changed");
            }
            if (root.has("entryIdsSha256")
                    && !root.path("entryIdsSha256").asText()
                            .equals(identityDigest(new ArrayList<>(seen)))) {
                throw invalid("ENTRY_SET", "",
                        "AI response entry identity set changed");
            }
            List<ProjectEntry> updated = new ArrayList<>();
            List<TranslationDraft> drafts = new ArrayList<>();
            String sourceLanguage = root.path("sourceLanguage").asText();
            String targetLanguage = root.path("targetLanguage").asText();
            TranslationProvenance importedProvenance =
                    policy == AiImportPolicy.APPROVE_ALL_VALIDATED
                            ? TranslationProvenance.HUMAN_EDITED
                            : TranslationProvenance.AI_TRANSLATED;
            for (ProjectEntry entry : project.entries()) {
                String translated = translations.get(identity(entry));
                updated.add(translated == null
                        ? entry
                        : entry.withTranslation(
                                translated, importedProvenance));
                if (translated != null && translationMemory != null) {
                    drafts.add(new TranslationDraft(
                            entry.originalText(),
                            sourceLanguage,
                            targetLanguage,
                            translated,
                            entry.sourceFile() + "#" + entry.key(),
                            importedProvenance));
                }
            }
            if (translationMemory != null && !drafts.isEmpty()) {
                try (SqliteTranslationMemory memory =
                        SqliteTranslationMemory.open(translationMemory)) {
                    memory.upsertAll(drafts);
                    TranslationReviewStatus reviewStatus =
                            policy == AiImportPolicy.APPROVE_ALL_VALIDATED
                                    ? TranslationReviewStatus.APPROVED
                                    : TranslationReviewStatus.DRAFT;
                    String providerId = root.path("providerId")
                            .asText("external-ai").trim();
                    if (providerId.isBlank()) {
                        providerId = "external-ai";
                    }
                    String providerModel = root.path("providerModel").asText("");
                    String providerVersion = root.path("providerVersion").asText("");
                    Instant generatedAt = Instant.now();
                    for (TranslationDraft draft : drafts) {
                        memory.recordGenerationMetadata(
                                draft.sourceText(),
                                draft.sourceLanguage(),
                                draft.targetLanguage(),
                                draft.context(),
                                new TranslationGenerationMetadata(
                                        providerId,
                                        providerModel,
                                        providerVersion,
                                        generatedAt,
                                        true,
                                        reviewStatus));
                    }
                }
            }
            LocalizationProject result = project.withEntries(updated);
            String originalName = root.path("originalModName").asText();
            String translatedName = root.path("translatedModName").asText("");
            boolean nameUpdated = !translatedName.isBlank();
            if (nameUpdated) {
                result = result.withPatchName(
                        translatedName.trim() + " (" + originalName + ")");
            }
            return new AiTranslationImportResult(
                    result,
                    translations.size(),
                    project.entries().size() - translations.size(),
                    nameUpdated);
        } catch (IOException | TranslationMemoryException | IllegalArgumentException exception) {
            throw new ProjectException("Could not import AI translation response", exception);
        }
    }

    private static String identity(ProjectEntry entry) {
        return entry.sourceFile().toString().replace('\\', '/') + "#" + entry.key();
    }

    private static String contentType(Path sourceFile) {
        String name = sourceFile.toString();
        int extension = name.lastIndexOf('.');
        return extension < 0 ? "unknown" : name.substring(extension + 1)
                .toLowerCase(java.util.Locale.ROOT);
    }

    private static String identityDigest(List<String> identities)
            throws ProjectException {
        try {
            String value = List.copyOf(identities).stream()
                    .sorted()
                    .collect(java.util.stream.Collectors.joining("\n"));
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new ProjectException("SHA-256 is unavailable", exception);
        }
    }

    private static int lineBreakCount(String value) {
        int count = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\r') {
                count++;
                if (index + 1 < value.length() && value.charAt(index + 1) == '\n') {
                    index++;
                }
            } else if (character == '\n') {
                count++;
            }
        }
        return count;
    }

    private static AiImportValidationException invalid(
            String code,
            String entryId,
            String message) {
        return new AiImportValidationException(
                new AiImportDiagnostic(code, entryId, message));
    }

    private static String instructions(String targetLanguage) {
        String destination = "en".equalsIgnoreCase(targetLanguage)
                ? "natural Starsector English"
                : "natural Starsector " + targetLanguage;
        return ("Translate this SSMT AI export into " + destination
                + " and return an import-ready JSON. Preserve the schema, IDs, "
                + "source strings, tokens, formatting, and line breaks. Use internal "
                + "IDs and context to resolve names and terminology. Keep terminology "
                + "consistent across duplicate/repeated strings. Prefer polished "
                + "localization over overly literal translation, but do not invent lore "
                + "or mechanics absent from the source. Prefer bundled author localization "
                + "when supplied. Translate translatedModName and "
                + "every entries[].translation. Return the complete JSON object only, "
                + "without commentary or code fences. Do not change schemaVersion, "
                + "sourceModId, any id, or any source.");
    }

    private static void writeAtomically(Path destination, JsonNode root)
            throws ProjectException {
        Path normalized = destination.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(
                normalized.getParent(), "destination parent");
        Path fileName = Objects.requireNonNull(
                normalized.getFileName(), "destination filename");
        try {
            Files.createDirectories(parent);
            Path staged = Files.createTempFile(parent, fileName.toString(), ".tmp");
            try {
                JSON.writerWithDefaultPrettyPrinter().writeValue(staged.toFile(), root);
                try {
                    Files.move(
                            staged,
                            normalized,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                    Files.move(staged, normalized, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(staged);
            }
        } catch (IOException exception) {
            throw new ProjectException("Could not write AI translation package", exception);
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
