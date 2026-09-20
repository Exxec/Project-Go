package com.ssmt.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssmt.extractor.bytecode.ClassStringExtractor;
import com.ssmt.extractor.csv.StandardCsvFileExtractor;
import com.ssmt.extractor.json.StandardJsonFileExtractor;
import com.ssmt.extractor.text.MissionTextExtractor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandardJsonGapAuditorTest {
    @TempDir Path root;

    @Test void reportsUnselectedLeavesWithoutPromotingThemToTranslatableText() throws Exception {
        Path hulls = Files.createDirectories(root.resolve("data/hulls"));
        Path hull = hulls.resolve("review.ship");
        Files.writeString(hull, "{hullName:'Visible',spriteName:'technical.png',"
                + "custom:{note:'Needs human review'}}");
        var report = new ExtractionCoordinator(List.of(new StandardCsvFileExtractor(),
                new StandardJsonFileExtractor(), new ClassStringExtractor(),
                new MissionTextExtractor())).extractMod("review", root);

        var findings = new StandardJsonGapAuditor().audit(root, "review", report);

        assertThat(report.strings()).extracting(com.ssmt.core.model.ExtractedString::originalText)
                .containsExactly("Visible");
        assertThat(findings).extracting(StandardJsonGapAuditor.Finding::pointer)
                .containsExactly("json:/custom/note", "json:/spriteName");
        assertThat(findings).extracting(StandardJsonGapAuditor.Finding::status)
                .containsOnly("UNSELECTED_TEXT_REVIEW");
    }
}
