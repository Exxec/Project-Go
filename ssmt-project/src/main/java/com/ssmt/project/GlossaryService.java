package com.ssmt.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssmt.tm.SqliteTranslationMemory;
import com.ssmt.tm.TranslationMemoryException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Builds the glossary of a mod from what is already known instead of leaving the reviewer to write it:
 * terms the user approved (per mod and globally), proper names the mod itself spells in English in a parallel CSV row,
 * and units the translation memory holds one human-approved translation for are approved terms; units repeated often
 * in the mod are only proposed, for one-click approval. Approved terms fill the export's {@code glossary{}}.
 */
public final class GlossaryService {
    /** One term. {@code target} is empty for a proposal without a suggestion. */
    public record Term(String source, String target, String origin, int occurrences, String note) { }

    /** Approved terms (applied) and proposed terms (waiting for a person). */
    public record Proposal(List<Term> approved, List<Term> proposed) {
        public Proposal {
            approved = List.copyOf(approved);
            proposed = List.copyOf(proposed);
        }

        /** Approved terms as the export's glossary map. */
        public Map<String, String> approvedMap() {
            Map<String, String> map = new LinkedHashMap<>();
            approved.forEach(term -> map.put(term.source(), term.target()));
            return map;
        }
    }

    /** Default number of occurrences from which a unit is proposed as a term. */
    public static final int DEFAULT_REPEAT_THRESHOLD = 3;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern ENGLISH_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9 .,'&:+\\-!?()/]{0,79}");
    private static final Pattern GENERATED_ROW = Pattern.compile("row\\d+");
    private static final String SOURCE_LANGUAGE = "zh";
    private static final String TARGET_LANGUAGE = "en";
    private final Path modGlossary;
    private final Path globalGlossary;
    private final Path translationMemory;
    private final int repeatThreshold;

    /**
     * Creates a service.
     *
     * @param modGlossary this mod's approved terms (JSON, may not exist yet)
     * @param globalGlossary terms approved for every mod (may be null)
     * @param translationMemory the master library, or null to skip translation-memory terms
     * @param repeatThreshold occurrences from which a repeated unit is proposed
     */
    public GlossaryService(Path modGlossary, Path globalGlossary, Path translationMemory, int repeatThreshold) {
        this.modGlossary = modGlossary;
        this.globalGlossary = globalGlossary;
        this.translationMemory = translationMemory;
        this.repeatThreshold = repeatThreshold;
    }

    /** Approved and proposed terms for a project's method document and its source mod. */
    public Proposal propose(Path sourceRoot, LocalizationProject project) throws ProjectException {
        ObjectNode document = project.methodDocument();
        if (document == null) {
            return new Proposal(List.of(), List.of());
        }
        Map<String, Integer> counts = new TerminologyConsistencyAuditor().sourceOccurrences(project);
        Map<String, Term> approved = new LinkedHashMap<>();
        Map<String, Term> conflicts = new LinkedHashMap<>();
        for (Term term : mergedStored()) {
            approved.put(term.source(), term);
        }
        Map<String, Set<String>> english = englishTwins(sourceRoot, document);
        for (var twin : english.entrySet()) {
            if (twin.getValue().size() == 1) {
                offer(approved, conflicts, new Term(twin.getKey(), twin.getValue().iterator().next(), "MOD_ENGLISH",
                        counts.getOrDefault(twin.getKey(), 1), "the mod spells this in English in a parallel row"));
            } else {
                conflicts.put(twin.getKey(), new Term(twin.getKey(), "", "CONFLICT",
                        counts.getOrDefault(twin.getKey(), 1),
                        "the mod spells this several ways: " + String.join(" | ", twin.getValue())));
            }
        }
        for (var memory : memoryTerms(counts.keySet()).entrySet()) {
            offer(approved, conflicts, new Term(memory.getKey(), memory.getValue(), "TRANSLATION_MEMORY",
                    counts.getOrDefault(memory.getKey(), 1), "one human-approved translation in the library"));
        }
        List<Term> proposed = new ArrayList<>(conflicts.values());
        for (var count : counts.entrySet()) {
            if (count.getValue() >= repeatThreshold && !approved.containsKey(count.getKey())
                    && !conflicts.containsKey(count.getKey()) && !count.getKey().isBlank()) {
                proposed.add(new Term(count.getKey(), "", "REPEATED", count.getValue(),
                        "appears " + count.getValue() + " times in this mod"));
            }
        }
        proposed.sort(Comparator.comparingInt(Term::occurrences).reversed().thenComparing(Term::source));
        return new Proposal(new ArrayList<>(approved.values()), proposed);
    }

    /** Puts the approved terms that occur in the project into its method document's {@code glossary}. */
    public LocalizationProject apply(Path sourceRoot, LocalizationProject project) throws ProjectException {
        ObjectNode document = project.methodDocument();
        if (document == null) {
            return project;
        }
        Map<String, String> terms = propose(sourceRoot, project).approvedMap();
        ObjectNode glossary = document.putObject("glossary");
        terms.forEach((source, target) -> {
            for (JsonNode entry : document.path("entries")) {
                if (entry.path("source").asText().contains(source)
                        && !entry.path("role").asText("TEXT").equals("IDENTIFIER")) {
                    glossary.put(source, target);
                    return;
                }
            }
        });
        return new LocalizationProject(project.schemaVersion(), project.sourceModId(), project.patchId(),
                project.patchName(), project.entries(), document);
    }

    /** Records one approval for this mod or for every mod. */
    public void approve(String source, String target, boolean global) throws ProjectException {
        Path file = global ? globalGlossary : modGlossary;
        if (file == null) {
            throw new ProjectException("No " + (global ? "global" : "mod") + " glossary location is configured");
        }
        Map<String, GlossaryTerm> terms = new LinkedHashMap<>();
        if (Files.isRegularFile(file)) {
            new GlossaryInterchangeService().read(file).terms().forEach(term -> terms.put(term.source(), term));
        }
        terms.put(source, new GlossaryTerm(source, target, "approved by the user"));
        try {
            Files.createDirectories(java.util.Objects.requireNonNull(file.toAbsolutePath().getParent()));
            TranslationSnapshot.writeAtomic(file, JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(
                    new GlossaryDocument(GlossaryDocument.CURRENT_SCHEMA_VERSION, SOURCE_LANGUAGE, TARGET_LANGUAGE,
                            terms.values().stream().sorted(Comparator.comparing(GlossaryTerm::source)).toList())));
        } catch (IOException exception) {
            throw new ProjectException("Could not store the glossary term: " + exception.getMessage(), exception);
        }
    }

    private static void offer(Map<String, Term> approved, Map<String, Term> conflicts, Term candidate) {
        Term existing = approved.get(candidate.source());
        if (existing == null) {
            approved.put(candidate.source(), candidate);
        } else if (!existing.target().equals(candidate.target()) && !existing.origin().equals("STORED")) {
            approved.remove(candidate.source());
            conflicts.put(candidate.source(), new Term(candidate.source(), "", "CONFLICT", candidate.occurrences(),
                    existing.origin() + " says '" + existing.target() + "', " + candidate.origin() + " says '"
                            + candidate.target() + "'"));
        }
    }

    private List<Term> mergedStored() throws ProjectException {
        Map<String, Term> merged = new LinkedHashMap<>();
        for (Path file : new Path[] {globalGlossary, modGlossary}) {
            if (file != null && Files.isRegularFile(file)) {
                for (GlossaryTerm term : new GlossaryInterchangeService().read(file).terms()) {
                    merged.put(term.source(), new Term(term.source(), term.target(), "STORED", 0, term.note()));
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    private Map<String, String> memoryTerms(Set<String> sources) throws ProjectException {
        Map<String, String> terms = new LinkedHashMap<>();
        if (translationMemory == null || !Files.isRegularFile(translationMemory)) {
            return terms;
        }
        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(translationMemory)) {
            for (String source : sources) {
                List<String> matches = memory.findExactApprovedTranslations(source, SOURCE_LANGUAGE, TARGET_LANGUAGE);
                if (matches.size() == 1) {
                    terms.put(source, matches.getFirst());
                }
            }
        } catch (TranslationMemoryException exception) {
            throw new ProjectException("Could not read the translation library: " + exception.getMessage(), exception);
        }
        return terms;
    }

    /**
     * CJK CSV cells paired with an English cell of the same row id and column in another CSV of the mod
     * (the mod spells the name itself). A source with several different English spellings has several entries.
     */
    private Map<String, Set<String>> englishTwins(Path sourceRoot, ObjectNode document) throws ProjectException {
        Set<String> wanted = new HashSet<>();
        Map<String, List<JsonNode>> chinese = new HashMap<>();
        for (JsonNode entry : document.path("entries")) {
            if (entry.path("kind").asText().equals("csv") && !entry.path("role").asText("TEXT").equals("IDENTIFIER")) {
                String slot = entry.path("context").path("row").asText() + "\0" + entry.path("context").path("column")
                        .asText();
                chinese.computeIfAbsent(slot, ignored -> new ArrayList<>()).add(entry);
                wanted.add(slot);
            }
        }
        Map<String, Set<String>> twins = new TreeMap<>();
        try (var walk = Files.walk(sourceRoot)) {
            for (Path path : walk.filter(Files::isRegularFile).sorted().toList()) {
                String file = sourceRoot.relativize(path).toString().replace(java.io.File.separatorChar, '/');
                if (!BridgeForgeTranslationService.suffix(file).equals("csv")
                        || BridgeForgeTranslationService.excluded(file)) {
                    continue;
                }
                String text;
                try {
                    text = BridgeForgeTranslationService.readText(path);
                } catch (IOException unreadable) {
                    continue;
                }
                for (var unit : BridgeForgeTextUnits.csvAll(file, text)) {
                    String row = unit.context().path("row").asText();
                    String slot = row + "\0" + unit.context().path("column").asText();
                    if (!wanted.contains(slot) || GENERATED_ROW.matcher(row).matches()
                            || !ENGLISH_NAME.matcher(unit.source().strip()).matches()) {
                        continue;
                    }
                    for (JsonNode entry : chinese.get(slot)) {
                        if (!entry.path("file").asText().equals(file)) {
                            twins.computeIfAbsent(entry.path("source").asText(), ignored -> new java.util.TreeSet<>())
                                    .add(unit.source().strip());
                        }
                    }
                }
            }
        } catch (IOException exception) {
            throw new ProjectException("Could not scan the mod for English names: " + exception.getMessage(),
                    exception);
        }
        return twins;
    }
}
