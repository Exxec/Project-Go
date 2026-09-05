package com.ssmt.extractor.json;

import com.ssmt.core.exception.SsmtParseException;
import com.ssmt.core.model.ExtractedString;
import com.ssmt.core.plugin.ExtractionRequest;
import com.ssmt.core.plugin.FileExtractor;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Applies conservative standard schemas to supported Starsector JSON-like files.
 */
public final class StandardJsonFileExtractor implements FileExtractor {

    private static final JsonExtractionSpec FACTION_SPEC =
            JsonExtractionSpec.selected(
                    Set.of(
                            "/displayName",
                            "/displayNameWithArticle",
                            "/displayNameLong",
                            "/displayNameLongWithArticle"),
                    Set.of(
                            "/ranks/ranks/*/name",
                            "/ranks/posts/*/name",
                            "/fleetTypeNames/*"));
    private static final JsonExtractionSpec VARIANT_SPEC =
            JsonExtractionSpec.selectedPointers(Set.of("/displayName"));
    // Hull files under data/hulls/: "hullName" is the player-visible display
    // name and "description" the hull flavor text. Every other field
    // ("hullId", "spriteName", "bounds", "weaponSlots", "engineSlots", ...)
    // is structural and must not be extracted.
    private static final JsonExtractionSpec HULL_SPEC =
            JsonExtractionSpec.selectedPointers(Set.of("/hullName", "/description"));
    // MagicLib's bounty-board format (a declared dependency of any mod using
    // it, not a per-mod convention): a fixed file keyed by arbitrary bounty
    // ids, each holding several confirmed player-visible fields --
    // job_difficultyDescription's only observed value is the sentinel "auto",
    // never real text, so it stays out of this list. Found missing via a real
    // mod's `ssmt extract` diagnostic (AzureFederation).
    private static final JsonExtractionSpec MAGIC_BOUNTY_SPEC =
            JsonExtractionSpec.selected(
                    Set.of(),
                    Set.of(
                            "/*/job_name",
                            "/*/job_description",
                            "/*/job_comm_reply",
                            "/*/job_intel_success"));
    // The "chatter" combat-dialogue character format: "name" is a fixed
    // display name, and "lines" holds arbitrarily-named situational
    // categories (e.g. "start", "retreat", "death") each an array of
    // {"text": "..."} objects -- an array of objects the pattern selector
    // cannot reach (array indices are never wildcard-matched), so its whole
    // subtree is walked instead. Sibling fields ("personalities", "gender",
    // "categoryTags") are fixed engine keywords, not text, and are
    // deliberately excluded -- allTextLeaves() on the whole document would
    // wrongly grab those too. Found missing the same way.
    private static final JsonExtractionSpec CHATTER_CHARACTER_SPEC =
            JsonExtractionSpec.selectedWithSubtrees(Set.of("/name"), Set.of("/lines"));
    private static final JsonExtractionSpec EXERELIN_CUSTOM_STARTS_SPEC =
            JsonExtractionSpec.selected(
                    Set.of(), Set.of("/starts/*/name", "/starts/*/difficulty", "/starts/*/desc"));
    private static final JsonExtractionSpec EXERELIN_FACTION_CONFIG_SPEC =
            JsonExtractionSpec.selectedWithSubtrees(
                    Set.of(
                            "/rebelFleetSuffix",
                            "/invasionSupportFleetName",
                            "/responseFleetName",
                            "/defenceFleetName"),
                    Set.of(
                            "/vengeanceLevelNames",
                            "/vengeanceFleetNames",
                            "/vengeanceFleetNamesSingle"));
    private static final JsonExtractionSpec MISSION_DESCRIPTOR_SPEC =
            JsonExtractionSpec.selectedPointers(Set.of("/title", "/difficulty"));

    @Override
    public boolean supports(Path sourceFile) {
        Objects.requireNonNull(sourceFile, "sourceFile must not be null");
        String normalized = normalize(sourceFile);
        return normalized.equals("data/strings/strings.json")
                || normalized.endsWith("/data/strings/strings.json")
                || normalized.equals("data/strings/tips.json")
                || normalized.endsWith("/data/strings/tips.json")
                || normalized.equals("data/strings/ship_names.json")
                || normalized.endsWith("/data/strings/ship_names.json")
                || normalized.equals("data/config/modfiles/magicbounty_data.json")
                || normalized.endsWith("/data/config/modfiles/magicbounty_data.json")
                || isChatterCharacterFile(normalized)
                || isExerelinCustomStartsFile(normalized)
                || isExerelinFactionConfigFile(normalized)
                || isMissionDescriptorFile(normalized)
                || isHullFile(normalized)
                || normalized.endsWith(".faction")
                || normalized.endsWith(".variant");
    }

    private static boolean isChatterCharacterFile(String normalized) {
        int lastSlash = normalized.lastIndexOf('/');
        String directory = lastSlash < 0 ? "" : normalized.substring(0, lastSlash);
        return normalized.endsWith(".json")
                && (directory.equals("data/config/chatter/characters")
                        || directory.endsWith("/data/config/chatter/characters"));
    }

    private static boolean isExerelinCustomStartsFile(String normalized) {
        return normalized.equals("data/config/exerelin/customstarts.json")
                || normalized.endsWith("/data/config/exerelin/customstarts.json");
    }

    private static boolean isExerelinFactionConfigFile(String normalized) {
        int lastSlash = normalized.lastIndexOf('/');
        String directory = lastSlash < 0 ? "" : normalized.substring(0, lastSlash);
        return normalized.endsWith(".json")
                && (directory.equals("data/config/exerelinfactionconfig")
                        || directory.endsWith("/data/config/exerelinfactionconfig"));
    }

    private static boolean isMissionDescriptorFile(String normalized) {
        return normalized.matches("(?:.*/)?data/missions/[^/]+/descriptor\\.json");
    }

    private static boolean isHullFile(String normalized) {
        int lastSlash = normalized.lastIndexOf('/');
        String directory = lastSlash < 0 ? "" : normalized.substring(0, lastSlash);
        return normalized.endsWith(".ship")
                && (directory.equals("data/hulls")
                        || directory.endsWith("/data/hulls"));
    }

    @Override
    public List<ExtractedString> extract(ExtractionRequest request)
            throws SsmtParseException {
        String relative = normalize(request.relativeSourceFile());
        JsonExtractionSpec spec;
        // tips.json and ship_names.json are loading-tip and ship-name-generator
        // lists: arrays of plain strings (tips.json also nests one {"freq","tip"}
        // object form) keyed under arbitrary/variable top-level keys, which the
        // pointer/pattern selection modes cannot express (patterns never match
        // array indices) -- allTextLeaves is the only spec that reaches every
        // element without an exact schema per mod. Both found missing, alongside
        // strings.json's existing coverage, via a real mod's `ssmt extract`
        // diagnostic (AzureFederation).
        if (relative.equals("data/strings/strings.json")
                || relative.equals("data/strings/tips.json")
                || relative.equals("data/strings/ship_names.json")) {
            spec = JsonExtractionSpec.allTextLeaves();
        } else if (relative.endsWith(".faction")) {
            spec = FACTION_SPEC;
        } else if (relative.endsWith(".variant")) {
            spec = VARIANT_SPEC;
        } else if (isHullFile(relative)) {
            spec = HULL_SPEC;
        } else if (relative.equals("data/config/modfiles/magicbounty_data.json")) {
            spec = MAGIC_BOUNTY_SPEC;
        } else if (isChatterCharacterFile(relative)) {
            spec = CHATTER_CHARACTER_SPEC;
        } else if (isExerelinCustomStartsFile(relative)) {
            spec = EXERELIN_CUSTOM_STARTS_SPEC;
        } else if (isExerelinFactionConfigFile(relative)) {
            spec = EXERELIN_FACTION_CONFIG_SPEC;
        } else if (isMissionDescriptorFile(relative)) {
            spec = MISSION_DESCRIPTOR_SPEC;
        } else {
            throw new SsmtParseException(
                    "No standard JSON schema for source", request.sourceFile());
        }
        return new JsonExtractor(spec).extract(request);
    }

    private static String normalize(Path path) {
        return path.normalize().toString()
                .replace('\\', '/')
                .toLowerCase(Locale.ROOT);
    }
}
