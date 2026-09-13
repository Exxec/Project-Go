package com.ssmt.scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class InventoryFingerprintTest {
    private static final String HASH = "a".repeat(64);

    @Test void orderDoesNotMatterButPathsSizesAndHashesDo() {
        var a = new InventoryFingerprint.File("a.txt", 3, HASH);
        var b = new InventoryFingerprint.File("b.txt", 4, HASH);
        String fingerprint = InventoryFingerprint.tree(List.of(a, b));
        assertThat(fingerprint).isEqualTo(InventoryFingerprint.tree(List.of(b, a)))
                .matches("[0-9a-f]{64}");
        assertThat(fingerprint).isNotEqualTo(InventoryFingerprint.tree(List.of(a,
                new InventoryFingerprint.File("b.txt", 5, HASH))));
        assertThat(fingerprint).isNotEqualTo(InventoryFingerprint.tree(List.of(a,
                new InventoryFingerprint.File("c.txt", 4, HASH))));
        assertThat(fingerprint).isNotEqualTo(InventoryFingerprint.tree(List.of(a,
                new InventoryFingerprint.File("b.txt", 4, "b".repeat(64)))));
    }

    @Test void duplicatePathsAreRejected() {
        var file = new InventoryFingerprint.File("a.txt", 3, HASH);
        assertThatThrownBy(() -> InventoryFingerprint.tree(List.of(file, file)))
                .hasMessageContaining("Repeated");
    }
}
