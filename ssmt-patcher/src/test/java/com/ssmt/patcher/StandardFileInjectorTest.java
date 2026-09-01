package com.ssmt.patcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandardFileInjectorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void injectsCsvCellWithoutShiftingColumns() throws Exception {
        Path source = temporaryDirectory.resolve("weapon_data.csv");
        Files.writeString(source, """
                id,name,other
                laser,"Laser, Cannon",keep
                """, StandardCharsets.UTF_8);
        TranslationReplacement replacement = new TranslationReplacement(
                Path.of("weapon_data.csv"),
                "csv:id=laser:name",
                "Laser, Cannon",
                "Canon, laser");

        PatchArtifact artifact =
                new StandardFileInjector().inject(temporaryDirectory, List.of(replacement));
        String output = new String(artifact.content(), StandardCharsets.UTF_8);

        assertThat(output).contains("laser,\"Canon, laser\",keep");
    }

    @Test
    void injectsCsvSourceEncodedAsGb18030LikeCsvExtractorTolerates() throws Exception {
        Path source = temporaryDirectory.resolve("weapon_data.csv");
        String csv = "id,name,other\r\nlaser,激光炮,keep\r\n";
        Files.write(source, csv.getBytes(java.nio.charset.Charset.forName("GB18030")));
        TranslationReplacement replacement = new TranslationReplacement(
                Path.of("weapon_data.csv"),
                "csv:id=laser:name",
                "激光炮",
                "Laser Cannon");

        PatchArtifact artifact =
                new StandardFileInjector().inject(temporaryDirectory, List.of(replacement));
        String output = new String(artifact.content(), StandardCharsets.UTF_8);

        assertThat(output).contains("laser,Laser Cannon,keep");
    }

    @Test
    void injectsNestedJsonPointerWithoutChangingSiblings() throws Exception {
        Path source = temporaryDirectory.resolve("strings.json");
        Files.writeString(source, """
                {"menu":{"title":"Hello","id":"structural"}}
                """, StandardCharsets.UTF_8);
        TranslationReplacement replacement = new TranslationReplacement(
                Path.of("strings.json"), "json:/menu/title", "Hello", "Bonjour");

        PatchArtifact artifact =
                new StandardFileInjector().inject(temporaryDirectory, List.of(replacement));
        JsonNode output = new ObjectMapper().readTree(artifact.content());

        assertThat(output.at("/menu/title").asText()).isEqualTo("Bonjour");
        assertThat(output.at("/menu/id").asText()).isEqualTo("structural");
    }

    @Test
    void injectsHashCommentedFactionJson() throws Exception {
        Path source = temporaryDirectory.resolve("example.faction");
        Files.writeString(source, """
                {
                  # Starsector hash comment
                  "displayName": "原名",
                }
                """, StandardCharsets.UTF_8);
        TranslationReplacement replacement = new TranslationReplacement(
                Path.of("example.faction"),
                "json:/displayName",
                "原名",
                "Translated Name");

        PatchArtifact artifact =
                new StandardFileInjector().inject(temporaryDirectory, List.of(replacement));
        JsonNode output = new ObjectMapper().readTree(artifact.content());

        assertThat(output.path("displayName").asText()).isEqualTo("Translated Name");
    }

    @Test
    void injectsCsvCellUsingCompositeIdentity() throws Exception {
        Path source = temporaryDirectory.resolve("descriptions.csv");
        Files.writeString(source, """
                id,type,text1
                shared,SHIP,Ship text
                shared,WEAPON,Weapon text
                """, StandardCharsets.UTF_8);
        TranslationReplacement replacement = new TranslationReplacement(
                Path.of("descriptions.csv"),
                "csv:id%2Ctype=shared%00WEAPON:text1",
                "Weapon text",
                "Translated weapon");

        PatchArtifact artifact =
                new StandardFileInjector().inject(temporaryDirectory, List.of(replacement));
        String output = new String(artifact.content(), StandardCharsets.UTF_8);

        assertThat(output).contains("shared,SHIP,Ship text");
        assertThat(output).contains("shared,WEAPON,Translated weapon");
    }

    @Test
    void preservesIntentionallyBlankHeaderDuringCsvInjection() throws Exception {
        Path source = temporaryDirectory.resolve("ship_data.csv");
        Files.writeString(source, """
                name,id,designation,tech/manufacturer,,system id
                Original,fsf_ship,Destroyer,FSF,keep-position,fsf_system
                """, StandardCharsets.UTF_8);
        TranslationReplacement replacement = new TranslationReplacement(
                Path.of("ship_data.csv"),
                "csv:id=fsf_ship:name",
                "Original",
                "Translated");

        PatchArtifact artifact =
                new StandardFileInjector().inject(temporaryDirectory, List.of(replacement));
        String output = new String(artifact.content(), StandardCharsets.UTF_8);

        assertThat(output)
                .startsWith("name,id,designation,tech/manufacturer,,system id")
                .contains("Translated,fsf_ship,Destroyer,FSF,keep-position,fsf_system");
        assertThat(Files.readString(source)).contains("Original,fsf_ship");
    }

    @Test
    void preservesBlankSentinelRowRawRepresentationDuringCsvInjection() throws Exception {
        Path source = temporaryDirectory.resolve("descriptions.csv");
        Files.writeString(source, """
                id,type,text1,text2,text3,text4
                ship_one,SHIP,Ship text,,,
                ,,,,,
                ship_two,SHIP,Other text,,,
                """, StandardCharsets.UTF_8);
        TranslationReplacement replacement = new TranslationReplacement(
                Path.of("descriptions.csv"),
                "csv:id%2Ctype=ship_one%00SHIP:text1",
                "Ship text",
                "Translated ship text");

        PatchArtifact artifact =
                new StandardFileInjector().inject(temporaryDirectory, List.of(replacement));
        String output = new String(artifact.content(), StandardCharsets.UTF_8);

        assertThat(output).contains("Translated ship text");
        assertThat(output).contains("\r\n,,,,,\r\n");
        assertThat(output).doesNotContain("\"\",,,,,");
    }

    @Test
    void preservesHashPrefixedStructuralRowRawRepresentationDuringCsvInjection() throws Exception {
        Path source = temporaryDirectory.resolve("descriptions.csv");
        Files.writeString(source, """
                id,type,text1,text2,text3,text4
                ship_one,SHIP,Ship text,,,
                #ships,,,,,
                ship_two,SHIP,Other text,,,
                """, StandardCharsets.UTF_8);
        TranslationReplacement replacement = new TranslationReplacement(
                Path.of("descriptions.csv"),
                "csv:id%2Ctype=ship_one%00SHIP:text1",
                "Ship text",
                "Translated ship text");

        PatchArtifact artifact =
                new StandardFileInjector().inject(temporaryDirectory, List.of(replacement));
        String output = new String(artifact.content(), StandardCharsets.UTF_8);

        assertThat(output).contains("Translated ship text");
        assertThat(output).contains("\r\n#ships,,,,,\r\n");
        assertThat(output).doesNotContain("\"#ships\"");
    }

    @Test
    void rejectsStaleOriginalTextAndMixedFiles() throws Exception {
        Files.writeString(
                temporaryDirectory.resolve("strings.json"),
                "{\"title\":\"Updated\"}",
                StandardCharsets.UTF_8);
        TranslationReplacement stale = new TranslationReplacement(
                Path.of("strings.json"), "json:/title", "Old", "Nouveau");
        TranslationReplacement other = new TranslationReplacement(
                Path.of("other.json"), "json:/title", "Old", "Nouveau");
        StandardFileInjector injector = new StandardFileInjector();

        assertThatThrownBy(() -> injector.inject(temporaryDirectory, List.of(stale)))
                .isInstanceOf(PatchBuilderException.class);
        assertThatThrownBy(() -> injector.inject(temporaryDirectory, List.of(stale, other)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void injectsPlainTextFileAsOneWholeUnit() throws Exception {
        Path source = temporaryDirectory.resolve("data/missions/aglaia/mission_text.txt");
        Files.createDirectories(Objects.requireNonNull(source.getParent()));
        Files.writeString(source, "Line one.\n\nLine two.", StandardCharsets.UTF_8);
        TranslationReplacement replacement = new TranslationReplacement(
                Path.of("data/missions/aglaia/mission_text.txt"),
                "text:file",
                "Line one.\n\nLine two.",
                "Ligne un.\n\nLigne deux.");

        PatchArtifact artifact =
                new StandardFileInjector().inject(temporaryDirectory, List.of(replacement));

        assertThat(new String(artifact.content(), StandardCharsets.UTF_8))
                .isEqualTo("Ligne un.\n\nLigne deux.");
    }

    @Test
    void rejectsStalePlainTextAndMultipleReplacementsForOneFile() throws Exception {
        Path source = temporaryDirectory.resolve("data/missions/aglaia/mission_text.txt");
        Files.createDirectories(Objects.requireNonNull(source.getParent()));
        Files.writeString(source, "Current text.", StandardCharsets.UTF_8);
        TranslationReplacement stale = new TranslationReplacement(
                Path.of("data/missions/aglaia/mission_text.txt"),
                "text:file",
                "Old text.",
                "New text.");
        TranslationReplacement duplicate = new TranslationReplacement(
                Path.of("data/missions/aglaia/mission_text.txt"),
                "text:file",
                "Current text.",
                "New text.");
        StandardFileInjector injector = new StandardFileInjector();

        assertThatThrownBy(() -> injector.inject(temporaryDirectory, List.of(stale)))
                .isInstanceOf(PatchBuilderException.class);
        assertThatThrownBy(() ->
                        injector.inject(temporaryDirectory, List.of(duplicate, duplicate)))
                .isInstanceOf(PatchBuilderException.class);
    }
}
