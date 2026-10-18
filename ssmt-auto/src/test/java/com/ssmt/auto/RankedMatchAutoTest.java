package com.ssmt.auto;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssmt.core.model.TranslationProvenance;
import com.ssmt.tm.SqliteTranslationMemory;
import com.ssmt.tm.TranslationDraft;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RankedMatchAutoTest {
    private static final String SOURCE = "高级装甲";
    @TempDir Path temporary;

    @Test void conflictingLibraryTranslationsAreRankedAndTheClearWinnerIsAppliedWithAProposalRecord() throws Exception {
        Path source = temporary.resolve("mod");
        Files.createDirectories(source.resolve("data"));
        Files.writeString(source.resolve("mod_info.json"), "{\"id\":\"rk\",\"name\":\"Rk\",\"version\":\"1\"}");
        Files.writeString(source.resolve("data/ships.csv"), "id,name\nship1," + SOURCE + "\n");
        Path catalog = temporary.resolve("internal/catalog.db");
        Files.createDirectories(java.util.Objects.requireNonNull(catalog.getParent()));
        try (var memory = SqliteTranslationMemory.open(catalog)) {
            memory.upsertAll(List.of(
                    new TranslationDraft(SOURCE, "zh", "en", "Advanced armor",
                            "data\\ships.csv#bf:csv:data/ships.csv#ship1:name", TranslationProvenance.HUMAN_EDITED),
                    new TranslationDraft(SOURCE, "zh", "en", "Elite plating",
                            "data\\other.csv#bf:csv:data/other.csv#zzz:title", TranslationProvenance.HUMAN_EDITED)));
        }
        var workflow = new AutoWorkflow(catalog, temporary.resolve("internal/workspaces"));
        var result = workflow.run(source);
        assertThat(result.status()).isEqualTo(AutoRunResult.Status.PATCH_PUBLISHED);
        assertThat(result.workspace().resolve("suggestions.json")).isRegularFile();
        assertThat(Files.readString(result.workspace().resolve("suggestions.json")))
                .contains("Advanced armor").contains("\"applied\" : true");
        try (var paths = Files.list(temporary)) {
            Path output = paths.filter(path -> Files.isRegularFile(path.resolve(".ssmt-build-fingerprint")))
                    .findFirst().orElseThrow();
            assertThat(Files.readString(output.resolve("data/ships.csv"))).contains("Advanced armor");
        }
        assertThat(Files.readString(source.resolve("data/ships.csv"))).contains(SOURCE);
    }
}
