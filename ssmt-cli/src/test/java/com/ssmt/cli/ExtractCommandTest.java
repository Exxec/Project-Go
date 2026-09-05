package com.ssmt.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssmt.extractor.csv.OptInCsvFileSchema;
import com.ssmt.extractor.csv.OptInCsvSchema;
import com.ssmt.extractor.csv.OptInCsvSchemaCatalog;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class ExtractCommandTest {

    private static final Path SPECIAL_ITEMS = Path.of("data/hulls/special_items.csv");

    @Test
    void extractsStandardFilesFromValidMod(@TempDir Path modRoot) throws Exception {
        Files.writeString(modRoot.resolve("mod_info.json"), """
                {"id":"test_mod","name":"Test Mod"}
                """);
        Path strings = modRoot.resolve("data/strings");
        Files.createDirectories(strings);
        Files.writeString(strings.resolve("descriptions.csv"), """
                id,type,text1,text2,text3,text4
                test,WEAPON,Localizable text,,,
                """);

        int exitCode = new CommandLine(new Main())
                .execute("extract", modRoot.toString());

        assertThat(exitCode).isZero();
    }

    @Test
    void invalidModDirectoryFails(@TempDir Path temporaryDirectory) {
        int exitCode = new CommandLine(new Main())
                .execute("extract", temporaryDirectory.resolve("missing").toString());

        assertThat(exitCode).isEqualTo(1);
    }

    @Test
    void suggestCsvSchemaWritesReviewableDraftCatalog(@TempDir Path modRoot) throws Exception {
        writeGapMod(modRoot);
        Path draft = modRoot.resolve("draft.csv.json");

        int exitCode = new CommandLine(new Main())
                .execute("extract", modRoot.toString(),
                        "--suggest-csv-schema", draft.toString());

        assertThat(exitCode).isZero();
        OptInCsvSchema catalog = new OptInCsvSchemaCatalog().read(draft);
        assertThat(catalog.schemaVersion()).isEqualTo(OptInCsvSchema.CURRENT_SCHEMA_VERSION);
        assertThat(catalog.files()).hasSize(1);
        OptInCsvFileSchema file = catalog.files().getFirst();
        assertThat(file.path()).isEqualTo(SPECIAL_ITEMS);
        assertThat(file.identityColumns()).containsExactly("id");
        assertThat(file.textColumns()).containsExactly("name", "desc");
    }

    @Test
    void extractWithoutSuggestFlagWritesNoCatalog(@TempDir Path modRoot) throws Exception {
        writeGapMod(modRoot);

        int exitCode = new CommandLine(new Main())
                .execute("extract", modRoot.toString());

        assertThat(exitCode).isZero();
        assertThat(Files.exists(modRoot.resolve("draft.csv.json"))).isFalse();
    }

    @Test
    void mergeIntoWithoutSuggestFlagIsUsageError(@TempDir Path modRoot) throws Exception {
        writeGapMod(modRoot);

        int exitCode = new CommandLine(new Main())
                .execute("extract", modRoot.toString(),
                        "--merge-into", modRoot.resolve("existing.json").toString());

        assertThat(exitCode).isEqualTo(CommandLine.ExitCode.USAGE);
    }

    @Test
    void mergeIntoKeepsExistingCatalogEntries(@TempDir Path modRoot) throws Exception {
        writeGapMod(modRoot);
        Path existingFile = modRoot.resolve("existing.json");
        new OptInCsvSchemaCatalog().write(existingFile, new OptInCsvSchema(
                OptInCsvSchema.CURRENT_SCHEMA_VERSION,
                List.of(new OptInCsvFileSchema(
                        Path.of("data/hulls/other.csv"), List.of("id"), List.of("name")))));
        Path draft = modRoot.resolve("draft.csv.json");

        int exitCode = new CommandLine(new Main())
                .execute("extract", modRoot.toString(),
                        "--suggest-csv-schema", draft.toString(),
                        "--merge-into", existingFile.toString());

        assertThat(exitCode).isZero();
        assertThat(new OptInCsvSchemaCatalog().read(draft).files())
                .extracting(OptInCsvFileSchema::path)
                .containsExactly(Path.of("data/hulls/other.csv"), SPECIAL_ITEMS);
    }

    private static void writeGapMod(Path modRoot) throws Exception {
        Files.writeString(modRoot.resolve("mod_info.json"), """
                {"id":"gap_mod","name":"Gap Mod"}
                """);
        Path hulls = modRoot.resolve("data/hulls");
        Files.createDirectories(hulls);
        Files.writeString(hulls.resolve("special_items.csv"), """
                id,name,desc,cost
                #舰船特殊物品表：结构注释行
                church_relic,圣物,古老的教会圣物,500
                holy_water,圣水,经过祝福的清水,250
                """);
    }
}
