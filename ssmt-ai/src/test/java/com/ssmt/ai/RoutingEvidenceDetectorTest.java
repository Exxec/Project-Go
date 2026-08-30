package com.ssmt.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RoutingEvidenceDetectorTest {
    @Test
    void reportsFixtureBackedSignalsWithoutProducingWeights() {
        AiTranslationRequest request = new AiTranslationRequest(
                "描述", "zh", "en", "ship system description", "Flux");

        assertThat(new RoutingEvidenceDetector().inspect(request, "保留文本"))
                .containsExactly(
                        "context suggests long-form description or lore",
                        "context suggests mechanics-sensitive text",
                        "approved terminology may be absent from the draft",
                        "draft may retain untranslated Han-script text or a proper noun");
    }
}
