package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TranslationWorkflowTest {
    @TempDir Path directory;
    private final ObjectMapper json = new ObjectMapper();

    private Path source() throws Exception {
        Path source = directory.resolve("mod");
        Files.createDirectories(source.resolve("data/strings"));
        Files.writeString(source.resolve("mod_info.json"), "{\"id\":\"example\",\"name\":\"Example\",\"version\":\"1\"}");
        strings(source, "{\"a\":\"Hello\",\"b\":\"Goodbye\"}");
        return source;
    }

    private void strings(Path source, String text) throws Exception {
        Files.writeString(source.resolve("data/strings/strings.json"), text);
    }

    private Path response(TranslationWorkflow workflow, TranslationWorkflow.Session session) throws Exception {
        Path file = directory.resolve("translation.json");
        workflow.exportTranslation(session, file);
        var root = json.readTree(file.toFile());
        for (var entry : root.path("entries")) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) entry)
                .put("translation", "Translated " + entry.path("source").asText());
        }
        json.writeValue(file.toFile(), root);
        return file;
    }

    @Test void defaultApplicationRootUsesTheSamePlatformRootAsTheSharedCatalog() {
        Path localAppData = directory.resolve("local-app-data");
        Path home = directory.resolve("home");
        assertThat(TranslationWorkflow.defaultApplicationRoot(
                java.util.Optional.of(localAppData.toString()), home))
                .isEqualTo(localAppData.resolve("Project Go").toAbsolutePath().normalize());
        assertThat(TranslationWorkflow.defaultApplicationRoot(
                java.util.Optional.empty(), home))
                .isEqualTo(home.resolve(".project-go").toAbsolutePath().normalize());
    }

    @Test void loadInputAcceptsAnArchiveWithoutChangingIt() throws Exception {
        Path archive = directory.resolve("mod.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (var entry : Map.of(
                    "wrapper/mod_info.json", "{\"id\":\"archive\",\"name\":\"Archive\",\"version\":\"1\"}",
                    "wrapper/data/strings/strings.json", "{\"a\":\"Hello\"}").entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        byte[] before = Files.readAllBytes(archive);

        var session = new TranslationWorkflow(directory.resolve("workspaces")).loadInput(archive);

        assertThat(session.modName()).isEqualTo("Archive");
        assertThat(session.source()).startsWith(directory.resolve("input-cache").toRealPath());
        assertThat(Files.readAllBytes(archive)).isEqualTo(before);
    }

    @Test void legacyProjectsRequireExplicitBytePreservingAdoption() throws Exception {
        Path source = source();
        var projects = new LocalizationProjectService();
        LocalizationProject extracted = projects.create(
                source, "example.translation", "Older Example");
        LocalizationProject translated = extracted.withEntries(extracted.entries().stream()
                .map(entry -> entry.withTranslatedText("Old " + entry.originalText())).toList());
        Path legacyDirectory = Files.createDirectories(directory.resolve("Project Go - Example"));
        Path first = legacyDirectory.resolve("first.ssmt.json");
        Path second = legacyDirectory.resolve("second.ssmt.json");
        projects.write(first, translated);
        projects.write(second, translated.withEntries(translated.entries().subList(0, 1)));
        byte[] firstBefore = Files.readAllBytes(first);
        byte[] secondBefore = Files.readAllBytes(second);

        var finder = new TranslationWorkflow(directory.resolve("finder-workspaces"));
        assertThat(finder.legacyProjects(source)).extracting(LegacyProjectCandidate::file)
                .containsExactly(first.toRealPath(), second.toRealPath());
        LegacyProjectCandidate firstCandidate = finder.legacyProjects(source).getFirst();
        var fresh = finder.loadMod(source);
        assertThat(fresh.project().entries()).allMatch(entry -> entry.translatedText().isBlank());
        // Internal work wins: legacy discovery stops and adoption is refused.
        assertThat(finder.legacyProjects(source)).isEmpty();
        assertThatThrownBy(() -> finder.adoptLegacy(source, firstCandidate))
                .hasMessageContaining("already exists");

        var adopter = new TranslationWorkflow(directory.resolve("adopted-workspaces"));
        var adopted = adopter.adoptLegacy(source.resolve("mod_info.json"),
                adopter.legacyProjects(source).getFirst());
        assertThat(adopted.project().entries()).allMatch(entry -> !entry.translatedText().isBlank());
        assertThat(Files.readAllBytes(first)).isEqualTo(firstBefore);
        assertThat(Files.readAllBytes(second)).isEqualTo(secondBefore);
        var changedWorkflow = new TranslationWorkflow(directory.resolve("other-workspaces"));
        LegacyProjectCandidate stale = changedWorkflow.legacyProjects(source).getFirst();
        Files.writeString(first, Files.readString(first) + "\n");
        assertThatThrownBy(() -> changedWorkflow.adoptLegacy(source, stale))
                .hasMessageContaining("changed after preview");
        assertThat(changedWorkflow.legacyProjects(source)).isNotEmpty();
    }

    @Test void restartRefreshesWithoutVersionChangeAndRetainsRemovedHistory() throws Exception {
        Path source = source();
        var workflow = new TranslationWorkflow(directory.resolve("workspaces"));
        var first = workflow.loadMod(source);
        var translated = workflow.importTranslation(first, response(workflow, first));
        strings(source, "{\"a\":\"Hello\",\"b\":\"Goodbye\",\"c\":\"New\"}");
        var restart = new TranslationWorkflow(directory.resolve("workspaces"));
        var grown = restart.loadMod(source);
        assertThat(grown.project().entries()).hasSize(3);
        assertThat(grown.project().entries().stream().filter(e -> !e.translatedText().isBlank())).hasSize(2);
        strings(source, "{\"a\":\"Changed\",\"c\":\"New\"}");
        var changed = restart.loadMod(source);
        assertThat(changed.project().entries()).hasSize(2).allMatch(e -> e.translatedText().isBlank());
        assertThat(changed.needsReview()).isEqualTo(1);
        assertThat(restart.loadMod(source).needsReview()).isEqualTo(1);
        var metadata = json.readTree(changed.workspace().resolve("project.ssmt.json").toFile()).path("workspace");
        assertThat(metadata.path("history").toString()).contains("Translated Goodbye", "Translated Hello");
        assertThat(translated.project().entries()).allMatch(e -> !e.translatedText().isBlank());
    }

    @Test void invalidImportAndPersistenceFailureLeaveCommittedStateUnchanged() throws Exception {
        var workflow = new TranslationWorkflow(directory.resolve("workspaces"));
        var session = workflow.loadMod(source());
        Path file = session.workspace().resolve("project.ssmt.json");
        byte[] before = Files.readAllBytes(file);
        Path response = response(workflow, session);
        var body = json.readTree(response.toFile());
        ((com.fasterxml.jackson.databind.node.ObjectNode) body.path("entries").get(1)).put("source", "tampered");
        json.writeValue(response.toFile(), body);
        assertThatThrownBy(() -> workflow.importTranslation(session, response)).isInstanceOf(ProjectException.class);
        assertThat(Files.readAllBytes(file)).isEqualTo(before);
        Path valid = response(workflow, session);
        try (var channel = FileChannel.open(session.workspace().resolve(".lock"), StandardOpenOption.WRITE);
                var lock = channel.lock()) {
            assertThat(lock.isValid()).isTrue();
            assertThatThrownBy(() -> workflow.importTranslation(session, valid)).isInstanceOf(ProjectException.class);
        }
        assertThat(Files.readAllBytes(file)).isEqualTo(before);
        assertThat(session.project().entries()).allMatch(e -> e.translatedText().isBlank());
    }

    @Test void rejectsStaleSessionCorruptWorkspaceAndSourceOutputOverlap() throws Exception {
        Path source = source();
        var workflow = new TranslationWorkflow(directory.resolve("workspaces"));
        var session = workflow.loadMod(source);
        Path response = response(workflow, session);
        workflow.importTranslation(session, response);
        assertThatThrownBy(() -> workflow.importTranslation(session, response)).hasMessageContaining("another window");
        assertThatThrownBy(() -> workflow.exportTranslation(session, source.resolve("words.json")))
                .hasMessageContaining("overlap");
        Files.writeString(session.workspace().resolve("project.ssmt.json"), "broken");
        assertThatThrownBy(() -> workflow.loadMod(source)).isInstanceOf(ProjectException.class);
        assertThat(Files.readString(session.workspace().resolve("project.ssmt.json"))).isEqualTo("broken");
    }

    @Test void currentArrayIdentityRemainsPositionalAndSourceMismatchNeverReusesText() throws Exception {
        Path source = source();
        strings(source, "{\"list\":[\"First\",\"Second\"]}");
        var workflow = new TranslationWorkflow(directory.resolve("workspaces"));
        var first = workflow.loadMod(source);
        assertThat(first.project().entries()).extracting(ProjectEntry::key).containsExactly("json:/list/0", "json:/list/1");
        workflow.importTranslation(first, response(workflow, first));
        strings(source, "{\"list\":[\"Second\",\"First\"]}");
        var reordered = workflow.loadMod(source);
        assertThat(reordered.project().entries()).allMatch(e -> e.translatedText().isBlank());
        assertThat(reordered.needsReview()).isEqualTo(2);
    }

    @Test void failedPublicationAfterValidationPreservesProjectAndHistory() throws Exception {
        var fail = new java.util.concurrent.atomic.AtomicBoolean();
        var workflow = new TranslationWorkflow(directory.resolve("owned"), (stage, target) -> {
            if (fail.get()) { throw new java.io.IOException("Injected publication failure"); }
            Files.move(stage, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        });
        var session = workflow.loadMod(source());
        Path response = response(workflow, session);
        Path saved = session.workspace().resolve("project.ssmt.json");
        byte[] before = Files.readAllBytes(saved);
        fail.set(true);
        assertThatThrownBy(() -> workflow.importTranslation(session, response))
                .hasMessageContaining("previous state was retained");
        assertThat(Files.readAllBytes(saved)).isEqualTo(before);
        assertThat(session.project().entries()).allMatch(e -> e.translatedText().isBlank());
    }

    @Test void oldResponseImportsAfterCoverageGrowthAndBuildPublishesOnlyTranslatedCopy() throws Exception {
        Path source = source();
        var workflow = new TranslationWorkflow(directory.resolve("owned"));
        var session = workflow.loadMod(source);
        Path response = response(workflow, session);
        strings(source, "{\"a\":\"Hello\",\"b\":\"Goodbye\",\"c\":\"New\"}");
        var grown = workflow.importTranslation(session, response);
        assertThat(grown.project().entries()).hasSize(3);
        assertThat(grown.project().entries().stream().filter(e -> !e.translatedText().isBlank())).hasSize(2);
        var completed = workflow.importTranslation(grown, response(workflow, grown));
        byte[] before = Files.readAllBytes(source.resolve("data/strings/strings.json"));
        Path output = directory.resolve("output");
        workflow.buildPatch(completed, output);
        assertThat(output.resolve("Project Go Changes.csv")).doesNotExist();
        assertThat(com.ssmt.patcher.PatchBuilder.sourceBackupRoot(output)).doesNotExist();
        assertThat(Files.readAllBytes(source.resolve("data/strings/strings.json"))).isEqualTo(before);
        assertThat(workflow.buildPatch(completed, output).changed()).isFalse();
    }

    @Test void bytecodeOrdinalsRemainPositionalAndInsertedConstantsDoNotInheritTranslations() throws Exception {
        Path source = source();
        Files.delete(source.resolve("data/strings/strings.json"));
        Path file = source.resolve("Example.class");
        Files.write(file, bytecode("First", "Second"));
        var workflow = new TranslationWorkflow(directory.resolve("owned"));
        var first = workflow.loadMod(source);
        assertThat(first.project().entries()).extracting(ProjectEntry::key)
                .containsExactly("class:Example#method:run()V:ldc:0", "class:Example#method:run()V:ldc:1");
        workflow.importTranslation(first, response(workflow, first));
        Files.write(file, bytecode("Inserted", "First", "Second"));
        var shifted = workflow.loadMod(source);
        assertThat(shifted.project().entries()).hasSize(3).allMatch(e -> e.translatedText().isBlank());
    }

    private static byte[] bytecode(String... strings) {
        var writer = new org.objectweb.asm.ClassWriter(0);
        writer.visit(org.objectweb.asm.Opcodes.V1_8, org.objectweb.asm.Opcodes.ACC_PUBLIC, "Example", null, "java/lang/Object", null);
        var method = writer.visitMethod(org.objectweb.asm.Opcodes.ACC_PUBLIC | org.objectweb.asm.Opcodes.ACC_STATIC,
                "run", "()V", null, null);
        method.visitCode();
        for (String text : strings) {
            method.visitLdcInsn(text);
            method.visitInsn(org.objectweb.asm.Opcodes.POP);
        }
        method.visitInsn(org.objectweb.asm.Opcodes.RETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
