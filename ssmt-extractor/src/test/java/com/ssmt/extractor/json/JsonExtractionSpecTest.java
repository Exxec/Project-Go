package com.ssmt.extractor.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JsonExtractionSpecTest {

    @Test
    void defensivelyCopiesAndSortsPointers() {
        Set<String> pointers = new LinkedHashSet<>(Set.of("/z", "/a"));

        JsonExtractionSpec spec = JsonExtractionSpec.selectedPointers(pointers);
        pointers.add("/later");

        assertThat(spec.pointers()).containsExactly("/a", "/z");
        assertThatThrownBy(() -> spec.pointers().add("/forbidden"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsInvalidPointersAndEmptySelection() {
        assertThatThrownBy(() -> JsonExtractionSpec.selectedPointers(Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JsonExtractionSpec.selectedPointers(Set.of("missing-slash")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allTextLeavesHasNoExplicitPointers() {
        JsonExtractionSpec spec = JsonExtractionSpec.allTextLeaves();

        assertThat(spec.extractsAllTextLeaves()).isTrue();
        assertThat(spec.pointers()).isEmpty();
        assertThat(spec.patterns()).isEmpty();
    }

    @Test
    void defensivelyCopiesAndSortsPatterns() {
        Set<String> patterns = new LinkedHashSet<>(Set.of("/z/*", "/a/*"));

        JsonExtractionSpec spec = JsonExtractionSpec.selected(Set.of(), patterns);
        patterns.add("/later/*");

        assertThat(spec.patterns()).containsExactly("/a/*", "/z/*");
        assertThatThrownBy(() -> spec.patterns().add("/forbidden"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsInvalidPatternsAndAllTextLeavesCombinedWithPatterns() {
        assertThatThrownBy(() -> JsonExtractionSpec.selected(Set.of(), Set.of("missing-slash")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JsonExtractionSpec.selected(Set.of(), Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JsonExtractionSpec(true, List.of(), List.of("/ranks/*")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
