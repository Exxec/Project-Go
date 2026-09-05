package com.ssmt.extractor.json;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssmt.core.model.ExtractedString;
import com.ssmt.core.plugin.ExtractionRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandardJsonFileExtractorTest {

    private final StandardJsonFileExtractor extractor = new StandardJsonFileExtractor();

    @Test
    void recognizesOnlyStandardStringJsonFactionAndVariantPaths() {
        assertThat(extractor.supports(Path.of("data/strings/strings.json"))).isTrue();
        assertThat(extractor.supports(Path.of("data/strings/tips.json"))).isTrue();
        assertThat(extractor.supports(Path.of("data/strings/ship_names.json"))).isTrue();
        assertThat(extractor.supports(
                Path.of("data/config/modFiles/magicBounty_data.json"))).isTrue();
        assertThat(extractor.supports(
                Path.of("data/config/chatter/characters/AF_yuyuyumao.json"))).isTrue();
        assertThat(extractor.supports(Path.of("data/config/exerelin/customStarts.json"))).isTrue();
        assertThat(extractor.supports(
                Path.of("data/config/exerelinFactionConfig/example.json"))).isTrue();
        assertThat(extractor.supports(Path.of("data/missions/example/descriptor.json"))).isTrue();
        assertThat(extractor.supports(Path.of("data/world/factions/test.faction"))).isTrue();
        assertThat(extractor.supports(Path.of("data/variants/test.variant"))).isTrue();
        assertThat(extractor.supports(Path.of("data/hulls/example.ship"))).isTrue();
        assertThat(extractor.supports(Path.of("data/hulls/Example.SHIP"))).isTrue();
        assertThat(extractor.supports(Path.of("data/config/settings.json"))).isFalse();
        assertThat(extractor.supports(Path.of("data/config/chatter/boss_ships.csv"))).isFalse();
        assertThat(extractor.supports(Path.of("data/config/example.ship"))).isFalse();
    }

    @Test
    void magicBountyExtractsOnlyConfirmedPlayerVisibleFieldsAcrossArbitraryBountyIds(
            @TempDir Path modRoot) throws Exception {
        Path source = modRoot.resolve("data/config/modFiles/magicBounty_data.json");
        Files.createDirectories(Objects.requireNonNull(source.getParent()));
        Files.writeString(source, """
                {
                  "adversary_Example": {
                    "trigger_marketFaction_any": ["independent"],
                    "job_name": "Placeholder Job",
                    "job_description": "Placeholder description.",
                    "job_comm_reply": "Placeholder reply.",
                    "job_intel_success": "Placeholder success text.",
                    "job_difficultyDescription": "auto",
                    "job_credit_reward": 1000000
                  }
                }
                """);

        List<ExtractedString> strings =
                extractor.extract(new ExtractionRequest("test", modRoot, source));

        assertThat(strings).extracting(ExtractedString::key)
                .containsExactlyInAnyOrder(
                        "json:/adversary_Example/job_name",
                        "json:/adversary_Example/job_description",
                        "json:/adversary_Example/job_comm_reply",
                        "json:/adversary_Example/job_intel_success");
    }

    @Test
    void chatterCharacterExtractsNameAndEveryLineTextButNotFixedKeywords(
            @TempDir Path modRoot) throws Exception {
        Path source = modRoot.resolve("data/config/chatter/characters/example.json");
        Files.createDirectories(Objects.requireNonNull(source.getParent()));
        Files.writeString(source, """
                {
                  "name": "Placeholder Name",
                  "personalities": ["timid", "cautious"],
                  "gender": ["f"],
                  "categoryTags": ["othermedia"],
                  "lines": {
                    "start": [
                      {"text": "Placeholder line one."},
                      {"text": "Placeholder line two."}
                    ],
                    "death": [
                      {"text": "Placeholder death line."}
                    ]
                  }
                }
                """);

        List<ExtractedString> strings =
                extractor.extract(new ExtractionRequest("test", modRoot, source));

        assertThat(strings).extracting(ExtractedString::originalText)
                .containsExactlyInAnyOrder(
                        "Placeholder Name",
                        "Placeholder line one.",
                        "Placeholder line two.",
                        "Placeholder death line.");
    }

    @Test
    void extractsOnlyPlayerVisibleExerelinAndMissionFields(@TempDir Path modRoot) throws Exception {
        Path starts = modRoot.resolve("data/config/exerelin/customStarts.json");
        Path faction = modRoot.resolve("data/config/exerelinFactionConfig/example.json");
        Path mission = modRoot.resolve("data/missions/example/descriptor.json");
        Files.createDirectories(Objects.requireNonNull(starts.getParent()));
        Files.createDirectories(Objects.requireNonNull(faction.getParent()));
        Files.createDirectories(Objects.requireNonNull(mission.getParent()));
        Files.writeString(starts, """
                {"starts":[{"id":"internal","name":"Start","difficulty":"Hard","desc":"Description"}]}
                """);
        Files.writeString(faction, """
                {"rebelFleetSuffix":"Raiders","vengeanceLevelNames":["One","Two"],
                 "invasionSupportFleetName":"Expedition","marketSpawnWeight":2}
                """);
        Files.writeString(mission, """
                {"title":"Mission","difficulty":"Easy","icon":"icon.jpg"}
                """);

        assertThat(extractor.extract(new ExtractionRequest("test", modRoot, starts)))
                .extracting(ExtractedString::originalText)
                .containsExactlyInAnyOrder("Start", "Hard", "Description");
        assertThat(extractor.extract(new ExtractionRequest("test", modRoot, faction)))
                .extracting(ExtractedString::originalText)
                .containsExactlyInAnyOrder("Raiders", "One", "Two", "Expedition");
        assertThat(extractor.extract(new ExtractionRequest("test", modRoot, mission)))
                .extracting(ExtractedString::originalText)
                .containsExactlyInAnyOrder("Mission", "Easy");
    }

    @Test
    void tipsExtractsBothPlainAndFrequencyWrappedTipStrings(@TempDir Path modRoot)
            throws Exception {
        Path source = modRoot.resolve("data/strings/tips.json");
        Files.createDirectories(Objects.requireNonNull(source.getParent()));
        Files.writeString(source, """
                {
                  "tips": [
                    {"freq": "0", "tip": "Wrapped tip."},
                    "Plain tip."
                  ]
                }
                """);

        List<ExtractedString> strings =
                extractor.extract(new ExtractionRequest("test", modRoot, source));

        assertThat(strings).extracting(ExtractedString::originalText)
                .contains("Wrapped tip.", "Plain tip.");
    }

    @Test
    void shipNamesExtractsEveryNameAcrossAnArbitraryTopLevelKey(@TempDir Path modRoot)
            throws Exception {
        Path source = modRoot.resolve("data/strings/ship_names.json");
        Files.createDirectories(Objects.requireNonNull(source.getParent()));
        Files.writeString(source, """
                {"AF": ["Placeholder One", "Placeholder Two"]}
                """);

        List<ExtractedString> strings =
                extractor.extract(new ExtractionRequest("test", modRoot, source));

        assertThat(strings).extracting(ExtractedString::originalText)
                .containsExactlyInAnyOrder("Placeholder One", "Placeholder Two");
    }

    @Test
    void factionExtractsOnlyVerifiedDisplayFields(@TempDir Path modRoot) throws Exception {
        Path source = modRoot.resolve("data/world/factions/test.faction");
        Files.createDirectories(Objects.requireNonNull(source.getParent()));
        Files.writeString(source, """
                {
                  id:"internal_id",
                  displayName:"Example",
                  displayNameWithArticle:"the Example",
                  displayNameLong:"Example Directorate",
                  displayNameLongWithArticle:"the Example Directorate",
                  logo:"graphics/example.png"
                }
                """);

        List<ExtractedString> strings =
                extractor.extract(new ExtractionRequest("test", modRoot, source));

        assertThat(strings).extracting(ExtractedString::key)
                .containsExactly(
                        "json:/displayName",
                        "json:/displayNameLong",
                        "json:/displayNameLongWithArticle",
                        "json:/displayNameWithArticle");
    }

    @Test
    void factionExtractsRankPostAndFleetTypeDisplayNamesWhenPresent(@TempDir Path modRoot)
            throws Exception {
        Path source = modRoot.resolve("data/world/factions/test.faction");
        Files.createDirectories(Objects.requireNonNull(source.getParent()));
        Files.writeString(source, """
                {
                  id:"internal_id",
                  displayName:"Example",
                  ranks:{
                    ranks:{
                      factionLeader:{id:"factionLeader",name:"Placeholder Rank One"}
                    },
                    posts:{
                      baseCommander:{id:"baseCommander",name:"Placeholder Post One"}
                    }
                  },
                  fleetTypeNames:{
                    trade:"Placeholder Fleet Type"
                  }
                }
                """);

        List<ExtractedString> strings =
                extractor.extract(new ExtractionRequest("test", modRoot, source));

        assertThat(strings).extracting(ExtractedString::key)
                .containsExactly(
                        "json:/displayName",
                        "json:/fleetTypeNames/trade",
                        "json:/ranks/posts/baseCommander/name",
                        "json:/ranks/ranks/factionLeader/name");
    }

    @Test
    void variantExtractsOnlyDisplayName(@TempDir Path modRoot) throws Exception {
        Path source = modRoot.resolve("data/variants/test.variant");
        Files.createDirectories(Objects.requireNonNull(source.getParent()));
        Files.writeString(source, """
                {"displayName":"Assault","hullId":"hound","variantId":"hound_Assault"}
                """);

        List<ExtractedString> strings =
                extractor.extract(new ExtractionRequest("test", modRoot, source));

        assertThat(strings).singleElement()
                .extracting(ExtractedString::originalText)
                .isEqualTo("Assault");
    }

    @Test
    void hullExtractsOnlyHullNameAndDescription(@TempDir Path modRoot) throws Exception {
        Path source = modRoot.resolve("data/hulls/example.ship");
        Files.createDirectories(Objects.requireNonNull(source.getParent()));
        Files.writeString(source, """
                {
                  "hullId": "example_hull",
                  "hullName": "Example Hull",
                  "description": "A sturdy example hull.",
                  "spriteName": "graphics/ships/example.png",
                  "bounds": [[-10, -5], [10, 5]],
                  "weaponSlots": [{"id": "WS001", "type": "BUILT_IN", "size": "SMALL"}],
                  "engineSlots": [{"location": [-12, 0], "width": 6, "length": 14}]
                }
                """);

        List<ExtractedString> strings =
                extractor.extract(new ExtractionRequest("test", modRoot, source));

        assertThat(strings).extracting(ExtractedString::key)
                .containsExactly("json:/description", "json:/hullName");
        assertThat(strings).extracting(ExtractedString::originalText)
                .containsExactly("A sturdy example hull.", "Example Hull");
        assertThat(strings).extracting(ExtractedString::originalText)
                .doesNotContain(
                        "example_hull", "graphics/ships/example.png", "WS001", "BUILT_IN");
    }
}
