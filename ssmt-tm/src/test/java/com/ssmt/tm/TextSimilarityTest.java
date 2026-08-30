package com.ssmt.tm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextSimilarityTest {
    @Test
    void normalizesCaseAndWhitespace() {
        assertThat(TextSimilarity.score("  HELLO   world ", "hello world")).isEqualTo(1.0);
    }

    @Test
    void scoresEditsRelativeToLongestText() {
        assertThat(TextSimilarity.score("kitten", "sitting")).isEqualTo(4.0 / 7.0);
        assertThat(TextSimilarity.score("", "")).isEqualTo(1.0);
    }
}
