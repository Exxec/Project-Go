package com.ssmt.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvStructureAuditorTest {
    @TempDir Path root;

    private Path csv(String name, String text) throws Exception {
        Path relative = Path.of(name);
        Files.createDirectories(java.util.Objects.requireNonNull(root.resolve(relative).getParent()));
        Files.writeString(root.resolve(relative), text);
        return relative;
    }

    @Test void reportsIndependentStructureAndIdentityIssuesWithoutMutation() throws Exception {
        String text = "id,name\na,One,Extra\na,Two\n,Blank\nb\n";
        Path path = csv("data/weapons/weapon_data.csv", text);
        var auditor = new CsvStructureAuditor();
        var findings = auditor.audit(root, List.of(path));
        assertThat(findings).extracting(CsvStructureAuditor.Finding::code)
                .containsExactly("EXTRA_COLUMNS", "DUPLICATE_IDENTITY", "BLANK_IDENTITY", "SHORT_ROW");
        assertThat(findings).isEqualTo(auditor.audit(root, List.of(path)));
        assertThat(Files.readString(root.resolve(path))).isEqualTo(text);
        assertThat(findings).allMatch(finding -> finding.disposition().equals("REVIEW"));
    }

    @Test void compositeIdentityDoesNotMistakeDifferentTypesForDuplicates() throws Exception {
        Path path = csv("data/strings/descriptions.csv", "id,type,text1\na,SHIP,One\na,WEAPON,Two\n");
        assertThat(new CsvStructureAuditor().audit(root, List.of(path))).isEmpty();
    }

    @Test void reviewsBoundedArchiveEntriesWithoutFilesystemWrites() {
        var findings = new CsvStructureAuditor().auditBytes(java.util.Map.of(
                Path.of("data/weapons/weapon_data.csv"),
                "id,name\na,One,Extra\na,Two\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        assertThat(findings).extracting(CsvStructureAuditor.Finding::code)
                .containsExactly("EXTRA_COLUMNS", "DUPLICATE_IDENTITY");
        assertThat(root.resolve("data/weapons/weapon_data.csv")).doesNotExist();
    }

    @Test void malformedAndUnknownIdentityRemainExplicitReview() throws Exception {
        Path malformed = csv("custom.csv", "id,text\na,\"unterminated");
        var findings = new CsvStructureAuditor().audit(root, List.of(malformed));
        assertThat(findings).extracting(CsvStructureAuditor.Finding::code)
                .contains("IDENTITY_NOT_ASSESSED", "MALFORMED_CSV");
    }
}
