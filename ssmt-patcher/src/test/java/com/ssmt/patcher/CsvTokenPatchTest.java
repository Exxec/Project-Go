package com.ssmt.patcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvTokenPatchTest {
    @TempDir Path root;

    @Test void preservesBomExtraCellsQuotedTechnicalCellsBlankLinesAndMixedSeparators() throws Exception {
        String text = "\uFEFF\"id\",name,other\r\n\r\n# short comment\n"
                + "\r\na,\"Old, name\",\"quoted technical\",extra,\r\n"
                + "b,Untouched,001.00\nc,Last,keep";
        Path file = root.resolve("data.csv");
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        Files.write(file, bytes);
        PatchArtifact artifact = new StandardFileInjector().inject(root, List.of(
                new TranslationReplacement(Path.of("data.csv"), "csv:id=a:name", "Old, name", "New\nname")));
        assertThat(artifact.content()).isEqualTo(text.replace("\"Old, name\"", "\"New\nname\"")
                .getBytes(StandardCharsets.UTF_8));
        assertThat(Files.readAllBytes(file)).isEqualTo(bytes);
    }

    @Test void preservesUntargetedMultilineAndEscapedQuotes() throws Exception {
        String text = "id,name,other\na,\"Old\"\" name\",\"technical\ntext\"\n";
        Files.writeString(root.resolve("data.csv"), text);
        PatchArtifact artifact = new StandardFileInjector().inject(root, List.of(
                new TranslationReplacement(Path.of("data.csv"), "csv:id=a:name", "Old\" name", "New")));
        assertThat(new String(artifact.content(), StandardCharsets.UTF_8))
                .isEqualTo(text.replace("\"Old\"\" name\"", "New"));
    }

    @Test void rejectsAmbiguousIdentityDuplicateHeaderAndMissingShortRowTarget() throws Exception {
        Path file = root.resolve("data.csv");
        for (String text : List.of("id,name\na,Old\na,Old\n", "id,name,name\na,Old,Old\n")) {
            Files.writeString(file, text);
            assertThatThrownBy(() -> new StandardFileInjector().inject(root, List.of(
                    new TranslationReplacement(Path.of("data.csv"), "csv:id=a:name", "Old", "New"))))
                    .isInstanceOf(PatchBuilderException.class).hasMessageContaining("Ambiguous");
            assertThat(Files.readString(file)).isEqualTo(text);
        }
        Files.writeString(file, "id,name\na\n");
        assertThatThrownBy(() -> new StandardFileInjector().inject(root, List.of(
                new TranslationReplacement(Path.of("data.csv"), "csv:id=a:name", "", "New"))))
                .isInstanceOf(PatchBuilderException.class).hasMessageContaining("Missing");
        assertThat(Files.readString(file)).isEqualTo("id,name\na\n");
    }
}
