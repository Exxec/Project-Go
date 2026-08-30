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
        assertThat(extractor.supports(Path.of("data/world/factions/test.faction"))).isTrue();
        assertThat(extractor.supports(Path.of("data/variants/test.variant"))).isTrue();
        assertThat(extractor.supports(Path.of("data/config/settings.json"))).isFalse();
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
}
