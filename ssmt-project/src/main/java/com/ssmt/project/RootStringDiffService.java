package com.ssmt.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssmt.core.RuntimeBudgets;
import com.ssmt.core.exception.SsmtParseException;
import com.ssmt.core.model.ExtractedString;
import com.ssmt.core.plugin.ExtractionRequest;
import com.ssmt.core.plugin.FileExtractor;
import com.ssmt.extractor.csv.StandardCsvFileExtractor;
import com.ssmt.extractor.json.StandardJsonFileExtractor;
import com.ssmt.extractor.text.MissionTextExtractor;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Side-by-side comparison of the translatable strings of two game (or mod) roots, for example Starsector 0.98a-RC8
 * against 0.98.5a. Strings come from the existing standard extractors (CSV, JSON-like, mission text; never bytecode) and
 * are matched by normalized source file plus stable key. A string is ADDED, REMOVED, CHANGED (same identity, different
 * source text) or UNCHANGED.
 *
 * <p>Given a translation pack or glossary written for the OLD root, each string also gets a carry-over verdict: a
 * translation carries over only when the old source text is exactly the text the translation was written for, so an
 * UNCHANGED string with a translation is CARRIED_OVER, a CHANGED one is STALE, and ADDED strings (and any string without
 * a usable translation) NEED_TRANSLATION. Pure and read-only: neither root is modified, and the report holds identities
 * and SHA-256 digests only, never source or translated text.
 */
public final class RootStringDiffService {
    /** How a string differs between the two roots. */
    public enum Change { ADDED, REMOVED, CHANGED, UNCHANGED }

    /** What the supplied translations mean for a string. */
    public enum Carry {
        /** Unchanged source with a translation written for exactly that text. */
        CARRIED_OVER,
        /** Source changed since the translation was written. */
        STALE,
        /** No usable translation for the new string. */
        NEEDS_TRANSLATION,
        /** The string was removed but a translation exists for it. */
        ORPHANED,
        /** Removed string without a translation: nothing to do. */
        NONE,
        /** No pack or glossary was supplied. */
        NOT_EVALUATED
    }

    /**
     * One compared string.
     *
     * @param file normalized relative source file
     * @param key extractor stable key
     * @param change difference between the roots
     * @param carry translation verdict
     * @param oldDigest SHA-256 of the old text, empty for ADDED
     * @param newDigest SHA-256 of the new text, empty for REMOVED
     */
    public record Unit(String file, String key, Change change, Carry carry, String oldDigest, String newDigest) { }

    /**
     * Comparison result.
     *
     * @param units sorted by file then key
     * @param translationsSupplied whether a pack or glossary was supplied
     * @param packEntriesForOtherText pack entries whose recorded source text differs from the old root's text
     * @param duplicateIdentities identities that occurred more than once in one root (first occurrence kept)
     * @param unreadableFiles files an extractor could not read in either root; left out of the comparison
     */
    public record Result(List<Unit> units, boolean translationsSupplied, int packEntriesForOtherText,
            int duplicateIdentities, List<String> unreadableFiles) {
        public Result {
            units = List.copyOf(units);
            unreadableFiles = List.copyOf(unreadableFiles);
        }

        /** Number of strings with this change. */
        public long count(Change change) {
            return units.stream().filter(unit -> unit.change() == change).count();
        }

        /** Number of strings with this verdict. */
        public long count(Carry carry) {
            return units.stream().filter(unit -> unit.carry() == carry).count();
        }
    }

    /** Translations written for the old root: by identity (pack) and by whole old source text (glossary). */
    public static final class Translations {
        private final Map<String, String> digestByIdentity = new HashMap<>();
        private final Set<String> glossaryDigests = new HashSet<>();

        /** Adds the non-blank translations of a project whose entries were extracted from the old root. */
        public Translations withProject(LocalizationProject project) {
            for (ProjectEntry entry : project.entries()) {
                if (!entry.translatedText().isBlank()) {
                    digestByIdentity.put(identity(entry.sourceFile().toString().replace('\\', '/'), entry.key()),
                            CarryForwardService.digest(entry.originalText()));
                }
            }
            return this;
        }

        /** Adds glossary terms; a term applies to a string whose whole old text equals the term source. */
        public Translations withGlossary(GlossaryDocument glossary) {
            for (GlossaryTerm term : glossary.terms()) {
                glossaryDigests.add(CarryForwardService.digest(term.source()));
            }
            return this;
        }

        private boolean has(String identity, String oldDigest) {
            return oldDigest.equals(digestByIdentity.get(identity)) || glossaryDigests.contains(oldDigest);
        }

        private boolean hasIdentityOnly(String identity, String oldDigest) {
            String recorded = digestByIdentity.get(identity);
            return recorded != null && !recorded.equals(oldDigest) && !glossaryDigests.contains(oldDigest);
        }
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SEPARATOR = "\u0000";

   private static final List<FileExtractor> EXTRACTORS = List.of(
            new StandardCsvFileExtractor(),
            new StandardJsonFileExtractor(),
            new MissionTextExtractor());

    private record Extracted(List<ExtractedString> strings, Set<String> unreadable) { }

    /**
     * Extracts both roots with the standard extractors and compares them. A file an extractor cannot read is listed
     * as unreadable and left out of the comparison on both sides, so it never shows up as ADDED or REMOVED.
     *
     * @param oldRoot old game or mod root (read only)
     * @param newRoot new game or mod root (read only)
     * @param translations translations for the old root, or null for none
     * @return comparison
     * @throws ProjectException when a root cannot be walked
     */
    public Result diff(Path oldRoot, Path newRoot, Translations translations) throws ProjectException {
        Extracted before = extract(oldRoot);
        Extracted after = extract(newRoot);
        Set<String> unreadable = new TreeSet<>(before.unreadable());
        unreadable.addAll(after.unreadable());
        return compare(without(before.strings(), unreadable), without(after.strings(), unreadable), translations,
                List.copyOf(unreadable));
    }

    /**
     * Compares two already extracted string lists.
     *
     * @param oldStrings strings of the old root
     * @param newStrings strings of the new root
     * @param translations translations for the old root, or null for none
     * @return comparison
     */
    public Result diff(List<ExtractedString> oldStrings, List<ExtractedString> newStrings,
            Translations translations) {
        return compare(oldStrings, newStrings, translations, List.of());
    }

    private static List<ExtractedString> without(List<ExtractedString> strings, Set<String> files) {
        return strings.stream().filter(string -> !files.contains(normalized(string.sourceFile()))).toList();
    }

    private static String normalized(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static Extracted extract(Path root) throws ProjectException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new ProjectException("Not a directory: " + normalizedRoot);
        }
        List<Path> files;
        try (Stream<Path> walk = Files.walk(normalizedRoot)) {
            files = walk.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).sorted().toList();
        } catch (java.io.IOException exception) {
            throw new ProjectException("Could not walk " + normalizedRoot, exception);
        }
        if (files.size() > RuntimeBudgets.DEFAULTS.maximumSourceFiles()) {
            throw new ProjectException("Source file count exceeds the configured budget");
        }
        List<ExtractedString> strings = new ArrayList<>();
        Set<String> unreadable = new TreeSet<>();
        for (Path file : files) {
            List<FileExtractor> matches = EXTRACTORS.stream().filter(extractor -> extractor.supports(file)).toList();
            if (matches.size() != 1) {
                continue;
            }
            try {
                if (Files.size(file) > RuntimeBudgets.DEFAULTS.maximumInputBytes()) {
                    throw new SsmtParseException("Source file exceeds configured input budget", file);
                }
                strings.addAll(matches.getFirst().extract(new ExtractionRequest("root", normalizedRoot, file)));
            } catch (SsmtParseException | java.io.IOException | RuntimeException exception) {
                unreadable.add(normalized(normalizedRoot.relativize(file)));
            }
        }
        return new Extracted(strings, unreadable);
    }

    private Result compare(List<ExtractedString> oldStrings, List<ExtractedString> newStrings,
            Translations translations, List<String> unreadable) {
        int[] duplicates = {0};
        Map<String, String> old = byIdentity(oldStrings, duplicates);
        Map<String, String> current = byIdentity(newStrings, duplicates);
        TreeSet<String> identities = new TreeSet<>(old.keySet());
        identities.addAll(current.keySet());
        List<Unit> units = new ArrayList<>();
        TreeMap<String, Boolean> otherText = new TreeMap<>();
        for (String identity : identities) {
            String[] parts = identity.split(SEPARATOR, 2);
            String before = old.get(identity);
            String after = current.get(identity);
            String oldDigest = before == null ? "" : CarryForwardService.digest(before);
            String newDigest = after == null ? "" : CarryForwardService.digest(after);
            Change change = before == null ? Change.ADDED : after == null ? Change.REMOVED
                    : before.equals(after) ? Change.UNCHANGED : Change.CHANGED;
            if (translations != null && before != null && translations.hasIdentityOnly(identity, oldDigest)) {
                otherText.put(identity, Boolean.TRUE);
            }
            units.add(new Unit(parts[0], parts[1], change, carry(change, identity, oldDigest, translations),
                    oldDigest, newDigest));
        }
        return new Result(units, translations != null, otherText.size(), duplicates[0], unreadable);
    }

    private static Carry carry(Change change, String identity, String oldDigest, Translations translations) {
        if (translations == null) {
            return Carry.NOT_EVALUATED;
        }
        boolean translated = change != Change.ADDED && translations.has(identity, oldDigest);
        return switch (change) {
            case ADDED -> Carry.NEEDS_TRANSLATION;
            case REMOVED -> translated ? Carry.ORPHANED : Carry.NONE;
            case CHANGED -> translated ? Carry.STALE : Carry.NEEDS_TRANSLATION;
            case UNCHANGED -> translated ? Carry.CARRIED_OVER : Carry.NEEDS_TRANSLATION;
        };
    }

    private static Map<String, String> byIdentity(List<ExtractedString> strings, int[] duplicates) {
        Map<String, String> result = new TreeMap<>();
        for (ExtractedString string : strings) {
            String identity = identity(string.sourceFile().toString().replace('\\', '/'), string.key());
            if (result.putIfAbsent(identity, string.originalText()) != null) {
                duplicates[0]++;
            }
        }
        return result;
    }

    private static String identity(String file, String key) {
        return file + SEPARATOR + key;
    }

    /**
     * Deterministic machine-readable report: identities and digests only.
     *
     * @param result comparison
     * @return report document
     */
    public static ObjectNode toJson(Result result) {
        ObjectNode root = JSON.createObjectNode();
        root.put("schema", "project-go-root-string-diff-1");
        root.put("translationsSupplied", result.translationsSupplied());
        root.put("packEntriesForOtherText", result.packEntriesForOtherText());
        root.put("duplicateIdentities", result.duplicateIdentities());
        ArrayNode unreadable = root.putArray("unreadableFiles");
        result.unreadableFiles().forEach(unreadable::add);
        ObjectNode changes = root.putObject("counts");
        for (Change change : Change.values()) {
            changes.put(change.name(), result.count(change));
        }
        ObjectNode carries = root.putObject("carryCounts");
        for (Carry carry : Carry.values()) {
            carries.put(carry.name(), result.count(carry));
        }
        ArrayNode units = root.putArray("units");
        for (Unit unit : result.units()) {
            units.addObject().put("file", unit.file()).put("key", unit.key()).put("change", unit.change().name())
                    .put("carry", unit.carry().name()).put("oldDigest", unit.oldDigest())
                    .put("newDigest", unit.newDigest());
        }
        return root;
    }

    /**
     * Short human summary, counts only.
     *
     * @param result comparison
     * @return plain text
     */
    public static String summary(Result result) {
        StringBuilder text = new StringBuilder("String comparison (old root against new root)\n");
        for (Change change : Change.values()) {
            text.append(String.format(Locale.ROOT, "  %-20s %d%n", change, result.count(change)));
        }
        if (result.translationsSupplied()) {
            text.append("Translations written for the old root:\n");
            for (Carry carry : Carry.values()) {
                if (carry != Carry.NOT_EVALUATED) {
                    text.append(String.format(Locale.ROOT, "  %-20s %d%n", carry, result.count(carry)));
                }
            }
            text.append(String.format(Locale.ROOT, "  pack entries whose source text is not the old root's: %d%n",
                    result.packEntriesForOtherText()));
        } else {
            text.append("No pack or glossary supplied: carry-over not evaluated.\n");
        }
        if (!result.unreadableFiles().isEmpty()) {
            text.append(String.format(Locale.ROOT, "Unreadable files left out of the comparison: %d%n",
                    result.unreadableFiles().size()));
        }
        if (result.duplicateIdentities() > 0) {
            text.append(String.format(Locale.ROOT, "Duplicate identities ignored: %d%n",
                    result.duplicateIdentities()));
        }
        return text.toString();
    }
}
