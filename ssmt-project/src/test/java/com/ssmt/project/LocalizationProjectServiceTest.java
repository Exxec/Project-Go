package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssmt.core.OperationCancelledException;
import com.ssmt.core.model.TranslationProvenance;
import com.ssmt.tm.SqliteTranslationMemory;
import com.ssmt.tm.TranslationDraft;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

class LocalizationProjectServiceTest {
    @Test
    void appliesUnambiguousFsfAuthorLocalizationWithoutMutatingSource()
            throws Exception {
        Path source = temporaryDirectory.resolve("fsf-layout");
        Path sourceCsv = source.resolve("aEP/data/strings/descriptions.csv");
        Path englishCsv = source.resolve("aEP_En/data/strings/descriptions.csv");
        Files.createDirectories(Objects.requireNonNull(sourceCsv.getParent()));
        Files.createDirectories(Objects.requireNonNull(englishCsv.getParent()));
        Files.writeString(source.resolve("mod_info.json"),
                "{\"id\":\"fixture.fsf\",\"name\":\"Fixture\",\"version\":\"1\"}");
        Files.writeString(sourceCsv,
                "id,type,text1,text2,text3,text4\nterm,SHIP,源,,,\n");
        Files.writeString(englishCsv,
                "id,type,text1,text2,text3,text4\nterm,SHIP,Author Term,,,\n");
        Map<Path, String> hashes = Map.of(
                sourceCsv, sha256(sourceCsv),
                englishCsv, sha256(englishCsv));
        Map<Path, java.nio.file.attribute.FileTime> times = Map.of(
                sourceCsv, Files.getLastModifiedTime(sourceCsv),
                englishCsv, Files.getLastModifiedTime(englishCsv));

        LocalizationProject project = new LocalizationProjectService().create(
                source, "fixture.fsf.english", "Fixture English");

        assertThat(project.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.sourceFile())
                    .isEqualTo(Path.of("aEP/data/strings/descriptions.csv"));
            assertThat(entry.translatedText()).isEqualTo("Author Term");
            assertThat(entry.provenance())
                    .isEqualTo(TranslationProvenance.AUTHOR_LOCALIZATION);
        });
        assertThat(sha256(sourceCsv)).isEqualTo(hashes.get(sourceCsv));
        assertThat(sha256(englishCsv)).isEqualTo(hashes.get(englishCsv));
        assertThat(Files.getLastModifiedTime(sourceCsv)).isEqualTo(times.get(sourceCsv));
        assertThat(Files.getLastModifiedTime(englishCsv)).isEqualTo(times.get(englishCsv));
        assertThat(Files.walk(source).filter(Files::isRegularFile)).hasSize(3);
    }
    @TempDir
    Path temporaryDirectory;

    @Test
    void ignoresLegacyWhitespaceOnlySourceCellsDuringBuild() throws Exception {
        Path source = temporaryDirectory.resolve("blank-source");
        Files.createDirectories(source);
        Files.writeString(
                source.resolve("mod_info.json"),
                "{\"id\":\"blank.mod\",\"name\":\"Blank\",\"version\":\"1\"}");
        LocalizationProject project = new LocalizationProject(
                1,
                "blank.mod",
                "blank.translation",
                "Blank Translation",
                List.of(new ProjectEntry(
                        Path.of("data/strings/descriptions.csv"),
                        "csv:id=blank:text2",
                        "   ",
                        "")));

        ProjectBuildResult result = new LocalizationProjectService().build(
                source,
                temporaryDirectory.resolve("blank-output"),
                project);

        assertThat(result.artifactCount()).isZero();
        assertThat(Files.isRegularFile(
                temporaryDirectory.resolve("blank-output/mod_info.json"))).isTrue();
    }

    @Test
    void exportsEditsAndBuildsACompleteSourceSafePatch() throws Exception {
        Path source = temporaryDirectory.resolve("source");
        Files.createDirectories(source.resolve("data/strings"));
        Files.writeString(
                source.resolve("mod_info.json"),
                "{\"id\":\"example.mod\",\"name\":\"Example\",\"version\":\"1\"}");
        Path strings = source.resolve("data/strings/strings.json");
        Files.writeString(strings, "{\"welcome\":\"Hello %s\"}");
        String sourceHash = sha256(strings);
        LocalizationProjectService service = new LocalizationProjectService();

        LocalizationProject project =
                service.create(source, "example.fr", "Example French");
        ProjectEntry extracted = project.entries().getFirst();
        LocalizationProject translated = project.withEntries(java.util.List.of(
                extracted.withTranslatedText("Bonjour %s")));
        Path projectFile = temporaryDirectory.resolve("translation.ssmt.json");
        service.write(projectFile, translated);
        LocalizationProject reread = service.read(projectFile);
        assertThat(reread.entries().getFirst().provenance())
                .isEqualTo(TranslationProvenance.HUMAN_EDITED);
        Path output = temporaryDirectory.resolve("output");

        ProjectBuildResult result = service.build(source, output, reread);
        String firstPatchHash = patchTreeHash(output);
        ProjectBuildResult rebuild = service.build(source, output, reread);

        assertThat(result.artifactCount()).isEqualTo(1);
        assertThat(rebuild.changed()).isFalse();
        assertThat(patchTreeHash(output)).isEqualTo(firstPatchHash);
        assertThat(Files.readString(
                        output.resolve("data/strings/strings.json"),
                        StandardCharsets.UTF_8))
                .contains("Bonjour %s")
                .doesNotContain("Hello %s");
        assertThat(Files.readString(output.resolve("mod_info.json")))
                .isEqualTo(Files.readString(source.resolve("mod_info.json")));
        assertThat(Files.readString(output.resolve("Project Go Changes.csv")))
                .contains("data/strings/strings.json")
                .contains("Bonjour %s");
        Path sourceBackup = com.ssmt.patcher.PatchBuilder.sourceBackupRoot(output);
        assertThat(Files.readString(sourceBackup.resolve("data/strings/strings.json")))
                .contains("Hello %s")
                .doesNotContain("Bonjour %s");
        assertThat(Files.readString(sourceBackup.resolve("mod_info.json")))
                .isEqualTo(Files.readString(source.resolve("mod_info.json")));
        assertThat(sha256(strings)).isEqualTo(sourceHash);
    }

    @Test
    void createAndBuildRoundTripEveryStandardExtractorTypeThroughTheDefaultPipeline()
            throws Exception {
        // Regression for a real wiring bug: MissionTextExtractor (and,
        // separately, jar support in ClassStringExtractor) was added to the
        // extractor list `createWithSchemas` builds locally, but a differently
        // indented copy of the same list literal on the `extraction` instance
        // field -- the one create()/build() actually use -- was missed by a
        // blanket find-and-replace, so the GUI and `project create` silently
        // never extracted mission text or jar-embedded strings at all despite
        // every extractor-level unit test passing. Only a real create()->
        // edit->build() round trip through this exact field catches that
        // class of bug.
        Path source = temporaryDirectory.resolve("mixed-source");
        Files.createDirectories(source.resolve("data/missions/aglaia"));
        Files.createDirectories(source.resolve("jars"));
        Files.writeString(
                source.resolve("mod_info.json"),
                "{\"id\":\"mixed.mod\",\"name\":\"Mixed\",\"version\":\"1\"}");
        Path missionText = source.resolve("data/missions/aglaia/mission_text.txt");
        Files.writeString(missionText, "Briefing text.");
        Path jar = source.resolve("jars/Mixed.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            zip.putNextEntry(new ZipEntry("example/Danger.class"));
            zip.write(dangerousClass());
            zip.closeEntry();
        }
        LocalizationProjectService service = new LocalizationProjectService();

        LocalizationProject project = service.create(source, "mixed.fr", "Mixed French");
        assertThat(project.entries())
                .extracting(ProjectEntry::sourceFile)
                .contains(
                        Path.of("data/missions/aglaia/mission_text.txt"),
                        Path.of("jars/Mixed.jar"));
        ProjectEntry missionEntry = project.entries().stream()
                .filter(entry -> entry.sourceFile().equals(
                        Path.of("data/missions/aglaia/mission_text.txt")))
                .findFirst().orElseThrow();
        ProjectEntry jarEntry = project.entries().stream()
                .filter(entry -> entry.sourceFile().equals(Path.of("jars/Mixed.jar")))
                .findFirst().orElseThrow();
        LocalizationProject translated = project.withEntries(List.of(
                missionEntry.withTranslatedText("Texte de briefing."),
                jarEntry.withTranslatedText(jarEntry.originalText() + " (translated)")));
        Path output = temporaryDirectory.resolve("mixed-output");

        ProjectBuildResult result = service.build(source, output, translated);

        assertThat(result.artifactCount()).isEqualTo(2);
        assertThat(Files.readString(
                        output.resolve("data/missions/aglaia/mission_text.txt"),
                        StandardCharsets.UTF_8))
                .isEqualTo("Texte de briefing.");
        try (ZipFile zip = new ZipFile(output.resolve("jars/Mixed.jar").toFile())) {
            ZipEntry entry = zip.getEntry("example/Danger.class");
            assertThat(entry).isNotNull();
            byte[] classBytes;
            try (var input = zip.getInputStream(entry)) {
                classBytes = input.readAllBytes();
            }
            assertThat(fieldConstant(classBytes)).isEqualTo("Hello field (translated)");
        }
    }

    private static String fieldConstant(byte[] classBytes) {
        String[] found = new String[1];
        new ClassReader(classBytes).accept(
                new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public FieldVisitor visitField(
                            int access, String name, String descriptor,
                            String signature, Object value) {
                        if (value instanceof String text) {
                            found[0] = text;
                        }
                        return null;
                    }
                }, 0);
        return found[0];
    }

    private static byte[] dangerousClass() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
                Opcodes.V17,
                Opcodes.ACC_PUBLIC,
                "example/Danger",
                null,
                "java/lang/Object",
                null);
        writer.visitField(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "LABEL",
                "Ljava/lang/String;",
                null,
                "Hello field").visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    @Test
    void previewsBuildWithoutWritingAnOutputDirectory() throws Exception {
        Path source = temporaryDirectory.resolve("preview-source");
        Files.createDirectories(source.resolve("data/strings"));
        Files.writeString(source.resolve("mod_info.json"),
                "{\"id\":\"preview.mod\",\"name\":\"Preview\"}");
        Files.writeString(source.resolve("data/strings/strings.json"),
                "{\"welcome\":\"Hello\",\"later\":\"Later\"}");
        LocalizationProjectService service = new LocalizationProjectService();
        LocalizationProject project = service.create(source, "preview.en", "Preview English");
        LocalizationProject edited = project.withEntries(List.of(
                project.entries().get(0).withTranslatedText("Welcome"),
                project.entries().get(1)));

        ProjectBuildPreview preview = service.previewBuild(source, edited);

        assertThat(preview.translatedEntries()).isEqualTo(1);
        assertThat(preview.untranslatedEntries()).isEqualTo(1);
        assertThat(preview.sourceFiles()).isEqualTo(1);
        assertThat(Files.exists(temporaryDirectory.resolve("preview-output"))).isFalse();
    }

    @Test
    void rejectsInvalidDraftBeforeWritingOutput() throws Exception {
        Path source = temporaryDirectory.resolve("source");
        Files.createDirectories(source.resolve("data/strings"));
        Files.writeString(
                source.resolve("mod_info.json"),
                "{\"id\":\"example.mod\",\"name\":\"Example\",\"version\":\"1\"}");
        Files.writeString(
                source.resolve("data/strings/strings.json"),
                "{\"welcome\":\"Hello %s\"}");
        LocalizationProjectService service = new LocalizationProjectService();
        LocalizationProject project =
                service.create(source, "example.fr", "Example French");
        ProjectEntry entry = project.entries().getFirst();
        LocalizationProject invalid = project.withEntries(java.util.List.of(
                entry.withTranslatedText("Bonjour")));
        Path output = temporaryDirectory.resolve("output");

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.build(source, output, invalid))
                .isInstanceOf(ProjectException.class);
        assertThat(output).doesNotExist();
    }

    @Test
    void createsProjectWithExplicitCustomJsonSchema() throws Exception {
        Path source = temporaryDirectory.resolve("custom-source");
        Files.createDirectories(source.resolve("data/config"));
        Files.writeString(
                source.resolve("mod_info.json"),
                "{\"id\":\"custom.mod\",\"name\":\"Custom\"}");
        Files.writeString(
                source.resolve("data/config/dialog.json"),
                "{\"id\":\"internal\",\"caption\":\"Visible\"}");
        Path schema = temporaryDirectory.resolve("schema.json");
        Files.writeString(schema, """
                {
                  "schemaVersion": 1,
                  "files": [{
                    "path": "data/config/dialog.json",
                    "pointers": ["/caption"]
                  }]
                }
                """);

        LocalizationProject project = new LocalizationProjectService()
                .createWithJsonSchema(source, "custom.fr", "Custom French", schema);

        assertThat(project.entries()).singleElement()
                .satisfies(entry -> {
                    assertThat(entry.key()).isEqualTo("json:/caption");
                    assertThat(entry.originalText()).isEqualTo("Visible");
                });
    }

    @Test
    void createsProjectWithExplicitCustomCsvSchema() throws Exception {
        Path source = temporaryDirectory.resolve("custom-csv-source");
        Files.createDirectories(source.resolve("data/hulls"));
        Files.writeString(
                source.resolve("mod_info.json"),
                "{\"id\":\"custom.csv.mod\",\"name\":\"Custom CSV\"}");
        Files.writeString(
                source.resolve("data/hulls/custom_hull_extra.csv"),
                "id,flavorText,internalNote\nhull_one,Visible flavor text,internal only\n");
        Path schema = temporaryDirectory.resolve("csv-schema.json");
        Files.writeString(schema, """
                {
                  "schemaVersion": 1,
                  "files": [{
                    "path": "data/hulls/custom_hull_extra.csv",
                    "identityColumns": ["id"],
                    "textColumns": ["flavorText"]
                  }]
                }
                """);

        LocalizationProject project = new LocalizationProjectService()
                .createWithCsvSchema(source, "custom.csv.fr", "Custom CSV French", schema);

        assertThat(project.entries()).singleElement()
                .satisfies(entry -> {
                    assertThat(entry.key()).isEqualTo("csv:id=hull_one:flavorText");
                    assertThat(entry.originalText()).isEqualTo("Visible flavor text");
                });
    }

    @Test
    void createsProjectWithBothCustomJsonAndCsvSchemasTogether() throws Exception {
        Path source = temporaryDirectory.resolve("custom-combined-source");
        Files.createDirectories(source.resolve("data/config"));
        Files.createDirectories(source.resolve("data/hulls"));
        Files.writeString(
                source.resolve("mod_info.json"),
                "{\"id\":\"custom.combined.mod\",\"name\":\"Custom Combined\"}");
        Files.writeString(
                source.resolve("data/config/dialog.json"),
                "{\"id\":\"internal\",\"caption\":\"Visible\"}");
        Files.writeString(
                source.resolve("data/hulls/custom_hull_extra.csv"),
                "id,flavorText\nhull_one,Visible flavor text\n");
        Path jsonSchema = temporaryDirectory.resolve("combined-json-schema.json");
        Files.writeString(jsonSchema, """
                {
                  "schemaVersion": 1,
                  "files": [{"path": "data/config/dialog.json", "pointers": ["/caption"]}]
                }
                """);
        Path csvSchema = temporaryDirectory.resolve("combined-csv-schema.json");
        Files.writeString(csvSchema, """
                {
                  "schemaVersion": 1,
                  "files": [{
                    "path": "data/hulls/custom_hull_extra.csv",
                    "identityColumns": ["id"],
                    "textColumns": ["flavorText"]
                  }]
                }
                """);

        LocalizationProject project = new LocalizationProjectService().createWithSchemas(
                source,
                "custom.combined.fr",
                "Custom Combined French",
                java.util.Optional.of(jsonSchema),
                java.util.Optional.of(csvSchema));

        assertThat(project.entries()).extracting(ProjectEntry::key)
                .containsExactlyInAnyOrder("json:/caption", "csv:id=hull_one:flavorText");
    }

    @Test
    void refreshClassifiesChangesWithoutApplyingSuggestions() throws Exception {
        Path source = temporaryDirectory.resolve("refresh-source");
        Files.createDirectories(source.resolve("data/strings"));
        Files.writeString(
                source.resolve("mod_info.json"),
                "{\"id\":\"refresh.mod\",\"name\":\"Refresh\"}");
        Path strings = source.resolve("data/strings/strings.json");
        Files.writeString(strings, """
                {"same":"Same","changed":"Before","removed":"Gone","move":"Moved"}
                """);
        LocalizationProjectService service = new LocalizationProjectService();
        LocalizationProject original = service.create(source, "refresh.fr", "Refresh French");
        List<ProjectEntry> translatedEntries = original.entries().stream()
                .map(entry -> entry.withTranslatedText("fr:" + entry.originalText()))
                .toList();
        LocalizationProject translated = original.withEntries(translatedEntries);
        Files.writeString(strings, """
                {"same":"Same","changed":"After","added":"New","moved":"Moved"}
                """);

        ProjectRefreshResult result = service.refresh(source, translated);

        assertThat(result.report().count(ReconciliationStatus.UNCHANGED)).isEqualTo(1);
        assertThat(result.report().count(ReconciliationStatus.CHANGED)).isEqualTo(2);
        assertThat(result.report().count(ReconciliationStatus.ADDED)).isEqualTo(1);
        assertThat(result.report().count(ReconciliationStatus.REMOVED)).isEqualTo(1);
        assertThat(result.project().entries())
                .filteredOn(entry -> entry.key().equals("json:/same"))
                .singleElement()
                .extracting(ProjectEntry::translatedText)
                .isEqualTo("fr:Same");
        assertThat(result.project().entries())
                .filteredOn(entry -> entry.key().equals("json:/changed"))
                .singleElement()
                .extracting(ProjectEntry::translatedText)
                .isEqualTo("");
        assertThat(result.report().entries())
                .filteredOn(entry -> entry.key().equals("json:/changed"))
                .singleElement()
                .satisfies(entry -> assertThat(entry.suggestions()).containsExactly("fr:Before"));
        assertThat(result.report().entries())
                .filteredOn(entry -> entry.key().equals("json:/moved"))
                .singleElement()
                .satisfies(entry -> assertThat(entry.suggestions()).containsExactly("fr:Moved"));
    }

    @Test
    void refreshReportsAmbiguousMovedTranslationAsConflict() throws Exception {
        Path source = temporaryDirectory.resolve("conflict-source");
        Files.createDirectories(source.resolve("data/strings"));
        Files.writeString(
                source.resolve("mod_info.json"),
                "{\"id\":\"conflict.mod\",\"name\":\"Conflict\"}");
        Path strings = source.resolve("data/strings/strings.json");
        Files.writeString(strings, "{\"one\":\"Shared\",\"two\":\"Shared\"}");
        LocalizationProjectService service = new LocalizationProjectService();
        LocalizationProject original = service.create(source, "conflict.fr", "Conflict French");
        LocalizationProject translated = original.withEntries(List.of(
                original.entries().get(0).withTranslatedText("Premier"),
                original.entries().get(1).withTranslatedText("Deuxième")));
        Files.writeString(strings, "{\"moved\":\"Shared\"}");

        ProjectRefreshResult result = service.refresh(source, translated);

        assertThat(result.report().count(ReconciliationStatus.CONFLICTED)).isEqualTo(1);
        assertThat(result.report().entries())
                .filteredOn(entry -> entry.status() == ReconciliationStatus.CONFLICTED)
                .singleElement()
                .satisfies(entry -> assertThat(entry.suggestions())
                        .containsExactly("Deuxième", "Premier"));
        assertThat(result.project().entries().getFirst().translatedText()).isEmpty();
    }

    @Test
    void refreshUsesOnlyContextSafeTranslationMemorySuggestions() throws Exception {
        Path source = temporaryDirectory.resolve("tm-source");
        Files.createDirectories(source.resolve("data/strings"));
        Files.writeString(
                source.resolve("mod_info.json"),
                "{\"id\":\"tm.mod\",\"name\":\"TM\"}");
        Files.writeString(
                source.resolve("data/strings/strings.json"),
                "{\"welcome\":\"Welcome aboard\"}");
        LocalizationProjectService service = new LocalizationProjectService();
        LocalizationProject project = service.create(source, "tm.fr", "TM French");
        Files.writeString(
                source.resolve("data/strings/strings.json"),
                "{\"welcome\":\"Welcome aboard!\"}");
        Path memoryPath = temporaryDirectory.resolve("memory.db");
        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(memoryPath)) {
            memory.create(new TranslationDraft(
                    "Welcome aboard!",
                    "en",
                    "fr",
                    "Bienvenue à bord !",
                    "data/strings/strings.json"));
            memory.create(new TranslationDraft(
                    "Welcome aboard!",
                    "en",
                    "fr",
                    "Wrong context",
                    "data/other.json"));
        }

        ProjectRefreshResult result = service.refreshWithTranslationMemory(
                source, project, memoryPath, "en", "fr", 0.8);

        assertThat(result.report().entries().getFirst().suggestions())
                .containsExactly("Bienvenue à bord !");
        assertThat(result.project().entries().getFirst().translatedText()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"json", "csv", "variant"})
    void repositoryOwnedFixturePassesCompleteDeterministicWorkflow(String fixture)
            throws Exception {
        Path source = Path.of("src/test/resources/workflow-fixtures").resolve(fixture);
        LocalizationProjectService service = new LocalizationProjectService();
        LocalizationProject project =
                service.create(source, "ssmt.fixture.patch", "SSMT Fixture Patch");
        LocalizationProject translated = project.withEntries(project.entries().stream()
                .map(entry -> entry.withTranslatedText(
                        entry.originalText().replace("Fixture", "Localized Fixture")))
                .toList());
        Path document = temporaryDirectory.resolve(fixture + ".ssmt.json");
        service.write(document, translated);
        Path output = temporaryDirectory.resolve(fixture + "-output");

        ProjectBuildResult first = service.build(source, output, service.read(document));
        String treeHash = patchTreeHash(output);
        ProjectBuildResult second = service.build(source, output, service.read(document));

        assertThat(project.entries()).isNotEmpty();
        assertThat(first.changed()).isTrue();
        assertThat(second.changed()).isFalse();
        assertThat(patchTreeHash(output)).isEqualTo(treeHash);
    }

    @Test
    void structuralRowFixturePreservesSentinelAndCommentRowsThroughDeterministicWorkflow()
            throws Exception {
        Path source = Path.of("src/test/resources/workflow-fixtures/csv-structural-rows");
        Path relativeCsv = Path.of("data/strings/descriptions.csv");
        String sourceCsv = Files.readString(source.resolve(relativeCsv), StandardCharsets.UTF_8);
        LocalizationProjectService service = new LocalizationProjectService();
        LocalizationProject project =
                service.create(source, "ssmt.fixture.patch", "SSMT Fixture Patch");
        LocalizationProject translated = project.withEntries(project.entries().stream()
                .map(entry -> entry.withTranslatedText(
                        entry.originalText().replace("Fixture", "Localized Fixture")))
                .toList());
        Path output = temporaryDirectory.resolve("csv-structural-rows-output");

        service.build(source, output, translated);
        String outputCsv = Files.readString(output.resolve(relativeCsv), StandardCharsets.UTF_8);

        assertThat(outputCsv).contains("Localized Fixture vessel");
        assertThat(sourceCsv.lines().anyMatch(",,,"::equals)).isTrue();
        assertThat(sourceCsv.lines().anyMatch("#ships,,,"::equals)).isTrue();
        assertThat(outputCsv.lines()).anyMatch(",,,"::equals);
        assertThat(outputCsv.lines()).anyMatch("#ships,,,"::equals);
        assertThat(outputCsv).doesNotContain("\"\",,,").doesNotContain("\"#ships\"");
    }

    @Test
    void cancellationDoesNotPublishPartialProjectOutput() throws Exception {
        Path source = temporaryDirectory.resolve("cancel-source");
        Files.createDirectories(source.resolve("data/strings"));
        Files.writeString(
                source.resolve("mod_info.json"),
                "{\"id\":\"cancel.mod\",\"name\":\"Cancel\"}");
        Files.writeString(
                source.resolve("data/strings/strings.json"),
                "{\"welcome\":\"Hello\"}");
        LocalizationProjectService service = new LocalizationProjectService();
        LocalizationProject project = service.create(source, "cancel.fr", "Cancel French")
                .withEntries(List.of(new ProjectEntry(
                        Path.of("data/strings/strings.json"),
                        "json:/welcome",
                        "Hello",
                        "Bonjour")));
        Path output = temporaryDirectory.resolve("cancel-output");

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.build(source, output, project, () -> true))
                .isInstanceOf(OperationCancelledException.class);
        assertThat(output).doesNotExist();
    }

    @Test
    void gameVersionIsPreservedInTranslatedAndPristineClones() throws Exception {
        Path source = temporaryDirectory.resolve("gameversion-source");
        Files.createDirectories(source.resolve("data/strings"));
        Files.writeString(
                source.resolve("mod_info.json"),
                "{\"id\":\"gv.mod\",\"name\":\"GV Mod\",\"gameVersion\":\"0.98a-RC8\"}");
        Files.writeString(
                source.resolve("data/strings/strings.json"),
                "{\"welcome\":\"Hello\"}");
        LocalizationProjectService service = new LocalizationProjectService();
        LocalizationProject project = service.create(source, "gv.mod.fr", "GV Mod French")
                .withEntries(List.of(new ProjectEntry(
                        Path.of("data/strings/strings.json"),
                        "json:/welcome",
                        "Hello",
                        "Bonjour")));
        Path output = temporaryDirectory.resolve("gameversion-output");

        service.build(source, output, project);

        assertThat(Files.readString(output.resolve("mod_info.json"), StandardCharsets.UTF_8))
                .contains("0.98a-RC8");
        assertThat(Files.readString(
                        com.ssmt.patcher.PatchBuilder.sourceBackupRoot(output)
                                .resolve("mod_info.json"),
                        StandardCharsets.UTF_8))
                .contains("0.98a-RC8");
    }

    @Test
    void createAndBuildSucceedEvenWhenPatchNamingDoesNotExtendSourceMod() throws Exception {
        Path source = temporaryDirectory.resolve("naming-source");
        Files.createDirectories(source.resolve("data/strings"));
        Files.writeString(
                source.resolve("mod_info.json"),
                "{\"id\":\"naming.mod\",\"name\":\"Naming Mod\"}");
        Files.writeString(
                source.resolve("data/strings/strings.json"),
                "{\"welcome\":\"Hello\"}");
        LocalizationProjectService service = new LocalizationProjectService();

        LocalizationProject project = service.create(source, "unrelated", "Unrelated Name")
                .withEntries(List.of(new ProjectEntry(
                        Path.of("data/strings/strings.json"),
                        "json:/welcome",
                        "Hello",
                        "Bonjour")));
        Path output = temporaryDirectory.resolve("naming-output");
        ProjectBuildResult result = service.build(source, output, project);

        assertThat(result.changed()).isTrue();
        assertThat(output.resolve("mod_info.json")).exists();
    }

    @Test
    void rejectsPastAndFutureProjectSchemasWithoutMigrationGuessing() throws Exception {
        Path past = temporaryDirectory.resolve("past.json");
        Path future = temporaryDirectory.resolve("future.json");
        String document = """
                {
                  "schemaVersion": SCHEMA_VERSION,
                  "sourceModId": "example",
                  "patchId": "example.fr",
                  "patchName": "Example French",
                  "entries": []
                }
                """;
        Files.writeString(past, document.replace("SCHEMA_VERSION", "0"));
        Files.writeString(future, document.replace("SCHEMA_VERSION", "2"));
        LocalizationProjectService service = new LocalizationProjectService();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.read(past))
                .isInstanceOf(ProjectException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.read(future))
                .isInstanceOf(ProjectException.class);
    }

    @Test
    void readsLegacyProjectWithoutProvenanceAsManualImport() throws Exception {
        Path legacy = temporaryDirectory.resolve("legacy.json");
        Files.writeString(legacy, """
                {
                  "schemaVersion": 1,
                  "sourceModId": "example",
                  "patchId": "example.fr",
                  "patchName": "Example French",
                  "entries": [{
                    "sourceFile": "data/strings/strings.json",
                    "key": "json:/welcome",
                    "originalText": "Hello",
                    "translatedText": "Bonjour"
                  }]
                }
                """);

        LocalizationProject project = new LocalizationProjectService().read(legacy);

        assertThat(project.entries().getFirst().provenance())
                .isEqualTo(TranslationProvenance.MANUAL_IMPORT);
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static String patchTreeHash(Path root) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .sorted()
                    .toList()) {
                digest.update(root.relativize(file).toString()
                        .replace('\\', '/')
                        .getBytes(StandardCharsets.UTF_8));
                digest.update(Files.readAllBytes(file));
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
