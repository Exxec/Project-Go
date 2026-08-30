package com.ssmt.project;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Versioned portable localization work product.
 *
 * @param schemaVersion document schema version
 * @param sourceModId exact source mod id
 * @param patchId generated patch mod id
 * @param patchName generated patch display name
 * @param entries stable-key translations
 */
public record LocalizationProject(
        int schemaVersion,
        String sourceModId,
        String patchId,
        String patchName,
        List<ProjectEntry> entries) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public LocalizationProject {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported project schema version " + schemaVersion);
        }
        requireText(sourceModId, "sourceModId");
        requireText(patchId, "patchId");
        requireText(patchName, "patchName");
        entries = List.copyOf(entries).stream()
                .sorted(Comparator
                        .comparing((ProjectEntry entry) ->
                                entry.sourceFile().toString().replace('\\', '/'))
                        .thenComparing(ProjectEntry::key))
                .toList();
        Set<String> identities = new HashSet<>();
        for (ProjectEntry entry : entries) {
            String identity = entry.sourceFile() + "\u0000" + entry.key();
            if (!identities.add(identity)) {
                throw new IllegalArgumentException("Duplicate project entry " + entry.key());
            }
        }
    }

    @Override
    public List<ProjectEntry> entries() {
        return List.copyOf(entries);
    }

    /**
     * Returns a project copy with replacement entries.
     *
     * @param values new entries
     * @return updated project
     */
    public LocalizationProject withEntries(List<ProjectEntry> values) {
        return new LocalizationProject(
                schemaVersion, sourceModId, patchId, patchName, values);
    }

    /**
     * Returns a project copy with a replacement patch display name.
     *
     * @param value translated patch display name
     * @return updated project
     */
    public LocalizationProject withPatchName(String value) {
        return new LocalizationProject(
                schemaVersion, sourceModId, patchId, value, entries);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
