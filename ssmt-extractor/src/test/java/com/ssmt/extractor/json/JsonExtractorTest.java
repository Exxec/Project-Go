package com.ssmt.extractor.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssmt.core.exception.SsmtParseException;
import com.ssmt.core.model.ExtractedString;
import com.ssmt.core.plugin.ExtractionRequest;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonExtractorTest {

    @Test
    void extractsAllTextLeavesWithEscapedSortedPointers() throws Exception {
        JsonExtractor extractor = new JsonExtractor(JsonExtractionSpec.allTextLeaves());

        List<ExtractedString> strings = extractor.extract(request("nested.json"));

        assertThat(strings)
                .extracting(ExtractedString::key, ExtractedString::originalText)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("json:/array/0", "First"),
                        org.assertj.core.groups.Tuple.tuple("json:/array/1/name", "Second"),
                        org.assertj.core.groups.Tuple.tuple("json:/menu/a~1b", "Slash"),
                        org.assertj.core.groups.Tuple.tuple("json:/menu/tilde~0key", "Tilde"),
                        org.assertj.core.groups.Tuple.tuple("json:/title", "标题"));
    }

    @Test
    void extractsOnlySelectedTextPointers() throws Exception {
        JsonExtractor extractor = new JsonExtractor(
                JsonExtractionSpec.selectedPointers(Set.of("/displayName", "/nested/text")));

        List<ExtractedString> strings = extractor.extract(request("selected.faction"));

        assertThat(strings).extracting(ExtractedString::key, ExtractedString::originalText)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("json:/displayName", "Example"),
                        org.assertj.core.groups.Tuple.tuple("json:/nested/text", "Visible"));
    }

    @Test
    void toleratesStarsectorLooseJsonFeatures() throws Exception {
        JsonExtractor extractor = new JsonExtractor(JsonExtractionSpec.allTextLeaves());

        List<ExtractedString> strings = extractor.extract(request("lenient.variant"));

        assertThat(strings).extracting(ExtractedString::originalText)
                .containsExactly("Assault");
    }

    @Test
    void toleratesUppercaseStarsectorBooleanWithoutChangingStringText(
            @TempDir Path modRoot
    ) throws Exception {
        Path source = modRoot.resolve("uppercase.variant");
        Files.writeString(source, """
                {"enabled": FALSE, "label": "FALSE alarm"}
                """);
        JsonExtractor extractor = new JsonExtractor(JsonExtractionSpec.allTextLeaves());

        List<ExtractedString> strings = extractor.extract(
                new ExtractionRequest("test_mod", modRoot, source));

        assertThat(strings).extracting(ExtractedString::originalText)
                .containsExactly("FALSE alarm");
    }

    @Test
    void ignoresMissingSelectedPointerButRejectsNonTextSelection() throws Exception {
        JsonExtractor missing = new JsonExtractor(
                JsonExtractionSpec.selectedPointers(Set.of("/notPresent")));
        JsonExtractor nonText = new JsonExtractor(
                JsonExtractionSpec.selectedPointers(Set.of("/number")));

        assertThat(missing.extract(request("selected.faction"))).isEmpty();
        assertThatThrownBy(() -> nonText.extract(request("selected.faction")))
                .isInstanceOf(SsmtParseException.class)
                .hasMessageContaining("not textual");
    }

    @Test
    void reportsMalformedJsonWithLineNumber() {
        JsonExtractor extractor = new JsonExtractor(JsonExtractionSpec.allTextLeaves());

        assertThatThrownBy(() -> extractor.extract(request("malformed.json")))
                .isInstanceOf(SsmtParseException.class)
                .hasMessageContaining("Malformed JSON")
                .satisfies(error ->
                        assertThat(((SsmtParseException) error).lineNumber()).isPresent());
    }

    @Test
    void reportsProbableTureTypoWithoutCorrectingOrContinuing(@TempDir Path modRoot)
            throws Exception {
        Path source = modRoot.resolve("typo.variant");
        String original = """
                {
                  "enabled": Ture,
                  "label": "Ture remains valid inside a string"
                }
                """;
        Files.writeString(source, original);
        JsonExtractor extractor = new JsonExtractor(JsonExtractionSpec.allTextLeaves());

        assertThatThrownBy(() -> extractor.extract(
                        new ExtractionRequest("test_mod", modRoot, source)))
                .isInstanceOf(SsmtParseException.class)
                .hasMessageContaining("did you mean 'true'")
                .satisfies(error -> {
                    SsmtParseException parse = (SsmtParseException) error;
                    assertThat(parse.diagnosticCode())
                            .contains("JSON_PROBABLE_LITERAL_TYPO");
                    assertThat(parse.suggestion()).hasValueSatisfying(
                            value -> assertThat(value).contains("Replace 'Ture'"));
                    assertThat(parse.lineNumber()).hasValue(2);
                });
        assertThat(Files.readString(source)).isEqualTo(original);
    }

    @Test
    void malformedMutationCorpusFailsOnlyAtTypedParserBoundary(@TempDir Path root)
            throws Exception {
        JsonExtractor extractor = new JsonExtractor(JsonExtractionSpec.allTextLeaves());
        String seed = "{\"title\":\"Visible\",\"nested\":{\"text\":\"Value\"}}";
        for (int index = 0; index < seed.length(); index++) {
            String mutated = seed.substring(0, index) + '\u0000' + seed.substring(index + 1);
            Path source = root.resolve("mutation-" + index + ".json");
            Files.writeString(source, mutated);
            try {
                extractor.extract(new ExtractionRequest("test_mod", root, source));
            } catch (SsmtParseException expected) {
                assertThat(expected.getMessage()).contains("JSON");
            }
        }
    }

    private static ExtractionRequest request(String fixtureName) throws URISyntaxException {
        Path source = Path.of(Objects.requireNonNull(
                JsonExtractorTest.class.getResource("/fixtures/json/" + fixtureName)).toURI());
        Path root = Objects.requireNonNull(source.getParent());
        return new ExtractionRequest("test_mod", root, source);
    }
}
