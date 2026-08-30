package com.ssmt.extractor.json;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Declares whether to extract every textual leaf, selected JSON pointers, or
 * bounded object-key wildcard patterns.
 *
 * @param extractsAllTextLeaves whether every textual leaf is eligible
 * @param pointers sorted RFC 6901 pointers when selection is explicit
 * @param patterns sorted pointer-shaped patterns where a segment may be the
 *     literal {@code *}, meaning "any object field name at this level"; array
 *     indices are never matched by a wildcard
 */
public record JsonExtractionSpec(
        boolean extractsAllTextLeaves,
        List<String> pointers,
        List<String> patterns
) {

    public JsonExtractionSpec {
        Objects.requireNonNull(pointers, "pointers must not be null");
        Objects.requireNonNull(patterns, "patterns must not be null");
        pointers = pointers.stream().sorted().toList();
        patterns = patterns.stream().sorted().toList();
        if (extractsAllTextLeaves && (!pointers.isEmpty() || !patterns.isEmpty())) {
            throw new IllegalArgumentException(
                    "all-text extraction cannot also declare pointers or patterns");
        }
        if (!extractsAllTextLeaves && pointers.isEmpty() && patterns.isEmpty()) {
            throw new IllegalArgumentException("selected pointers or patterns must not be empty");
        }
        for (String pointer : pointers) {
            Objects.requireNonNull(pointer, "pointer must not be null");
            if (!pointer.startsWith("/")) {
                throw new IllegalArgumentException(
                        "JSON pointer must start with '/': " + pointer);
            }
        }
        for (String pattern : patterns) {
            Objects.requireNonNull(pattern, "pattern must not be null");
            if (!pattern.startsWith("/")) {
                throw new IllegalArgumentException(
                        "JSON pointer pattern must start with '/': " + pattern);
            }
        }
    }

    public static JsonExtractionSpec allTextLeaves() {
        return new JsonExtractionSpec(true, List.of(), List.of());
    }

    public static JsonExtractionSpec selectedPointers(Set<String> pointers) {
        Objects.requireNonNull(pointers, "pointers must not be null");
        return new JsonExtractionSpec(false, List.copyOf(pointers), List.of());
    }

    public static JsonExtractionSpec selected(Set<String> pointers, Set<String> patterns) {
        Objects.requireNonNull(pointers, "pointers must not be null");
        Objects.requireNonNull(patterns, "patterns must not be null");
        return new JsonExtractionSpec(false, List.copyOf(pointers), List.copyOf(patterns));
    }

    @Override
    public List<String> pointers() {
        return List.copyOf(pointers);
    }

    @Override
    public List<String> patterns() {
        return List.copyOf(patterns);
    }
}
