package com.ssmt.extractor.json;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Declares whether to extract every textual leaf, selected JSON pointers,
 * bounded object-key wildcard patterns, or every textual leaf beneath one or
 * more specific subtrees.
 *
 * @param extractsAllTextLeaves whether every textual leaf in the whole document is eligible
 * @param pointers sorted RFC 6901 pointers when selection is explicit
 * @param patterns sorted pointer-shaped patterns where a segment may be the
 *     literal {@code *}, meaning "any object field name at this level"; array
 *     indices are never matched by a wildcard
 * @param allTextLeavesUnder sorted RFC 6901 pointers to subtrees (of arbitrary,
 *     mixed object/array shape) whose every textual leaf is eligible -- for
 *     content shaped like an array of {@code {"text": "..."}} objects nested
 *     under an otherwise-known object, where neither a fixed pointer (the
 *     array length varies) nor a {@code *} pattern (arrays are never
 *     wildcard-matched) can select every element
 */
public record JsonExtractionSpec(
        boolean extractsAllTextLeaves,
        List<String> pointers,
        List<String> patterns,
        List<String> allTextLeavesUnder
) {

    public JsonExtractionSpec {
        Objects.requireNonNull(pointers, "pointers must not be null");
        Objects.requireNonNull(patterns, "patterns must not be null");
        Objects.requireNonNull(allTextLeavesUnder, "allTextLeavesUnder must not be null");
        pointers = pointers.stream().sorted().toList();
        patterns = patterns.stream().sorted().toList();
        allTextLeavesUnder = allTextLeavesUnder.stream().sorted().toList();
        if (extractsAllTextLeaves
                && (!pointers.isEmpty() || !patterns.isEmpty() || !allTextLeavesUnder.isEmpty())) {
            throw new IllegalArgumentException(
                    "all-text extraction cannot also declare pointers, patterns, "
                            + "or subtree pointers");
        }
        if (!extractsAllTextLeaves
                && pointers.isEmpty() && patterns.isEmpty() && allTextLeavesUnder.isEmpty()) {
            throw new IllegalArgumentException(
                    "selected pointers, patterns, or subtree pointers must not be empty");
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
        for (String pointer : allTextLeavesUnder) {
            Objects.requireNonNull(pointer, "subtree pointer must not be null");
            if (!pointer.startsWith("/")) {
                throw new IllegalArgumentException(
                        "JSON subtree pointer must start with '/': " + pointer);
            }
        }
    }

    public static JsonExtractionSpec allTextLeaves() {
        return new JsonExtractionSpec(true, List.of(), List.of(), List.of());
    }

    public static JsonExtractionSpec selectedPointers(Set<String> pointers) {
        Objects.requireNonNull(pointers, "pointers must not be null");
        return new JsonExtractionSpec(false, List.copyOf(pointers), List.of(), List.of());
    }

    public static JsonExtractionSpec selected(Set<String> pointers, Set<String> patterns) {
        Objects.requireNonNull(pointers, "pointers must not be null");
        Objects.requireNonNull(patterns, "patterns must not be null");
        return new JsonExtractionSpec(
                false, List.copyOf(pointers), List.copyOf(patterns), List.of());
    }

    /**
     * Selects fixed pointers plus one or more subtrees to extract every
     * textual leaf from, e.g. a known {@code /name} field alongside an
     * arbitrarily-shaped {@code /lines} subtree.
     *
     * @param pointers fixed textual pointers
     * @param allTextLeavesUnder subtree roots to recurse fully
     * @return the combined spec
     */
    public static JsonExtractionSpec selectedWithSubtrees(
            Set<String> pointers, Set<String> allTextLeavesUnder) {
        Objects.requireNonNull(pointers, "pointers must not be null");
        Objects.requireNonNull(allTextLeavesUnder, "allTextLeavesUnder must not be null");
        return new JsonExtractionSpec(
                false, List.copyOf(pointers), List.of(), List.copyOf(allTextLeavesUnder));
    }

    @Override
    public List<String> pointers() {
        return List.copyOf(pointers);
    }

    @Override
    public List<String> patterns() {
        return List.copyOf(patterns);
    }

    @Override
    public List<String> allTextLeavesUnder() {
        return List.copyOf(allTextLeavesUnder);
    }
}
