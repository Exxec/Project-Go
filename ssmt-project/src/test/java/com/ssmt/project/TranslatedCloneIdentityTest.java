package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Permanent gate: a full translated clone is the same mod, not a new addon. Its
 * {@code mod_info.json} keeps the original id and name, and only the folder name
 * distinguishes it. Every user-visible name stays readable.
 */
class TranslatedCloneIdentityTest {
    private static final Pattern DIGEST_RUN = Pattern.compile("[0-9a-f]{16,}");
    @TempDir Path directory;
    private final ObjectMapper json = new ObjectMapper();

    private Path source(String folder, String id, String name) throws Exception {
        Path mod = directory.resolve(folder);
        Files.createDirectories(mod.resolve("data/strings"));
        Files.writeString(mod.resolve("mod_info.json"),
                "{\"id\":\"" + id + "\",\"name\":\"" + name + "\",\"version\":\"1\","
                        + "\"gameVersion\":\"0.98a\"}", StandardCharsets.UTF_8);
        Files.writeString(mod.resolve("data/strings/strings.json"),
                "{\"hello\":\"Hello\",\"bye\":\"Goodbye\"}", StandardCharsets.UTF_8);
        return mod;
    }

    private TranslationWorkflow.Session translated(TranslationWorkflow workflow, Path mod)
            throws Exception {
        TranslationWorkflow.Session session = workflow.loadMod(mod);
        Path request = directory.resolve(session.presentation().aiRequestFilename());
        workflow.exportTranslation(session, request);
        ObjectNode response = (ObjectNode) json.readTree(request.toFile());
        for (var entry : response.withArray("entries")) {
            ((ObjectNode) entry).put("translation", "Translated " + entry.path("source").asText());
        }
        json.writeValue(request.toFile(), response);
        return workflow.importTranslation(session, request);
    }

    @Test void installedCloneKeepsTheOriginalModIdAndNameAndUsesAReadableFolder() throws Exception {
        Path mod = source("Edmund Church", "a16709513_wkt", "Edmund Church");
        byte[] metadataBefore = Files.readAllBytes(mod.resolve("mod_info.json"));
        var workflow = new TranslationWorkflow(directory.resolve("owned"));
        var completed = translated(workflow, mod);
        Path mods = Files.createDirectories(directory.resolve("mods"));
        Path output = mods.resolve(completed.presentation().translatedFolderName());

        workflow.buildPatch(completed, output);

        assertThat(output.getFileName()).hasToString("Edmund Church - English");
        var installed = json.readTree(output.resolve("mod_info.json").toFile());
        assertThat(installed.path("id").asText()).isEqualTo("a16709513_wkt");
        assertThat(installed.path("name").asText()).isEqualTo("Edmund Church");
        assertThat(installed.path("gameVersion").asText()).isEqualTo("0.98a");
        // The original stays pristine and the two copies differ only by folder.
        assertThat(Files.readAllBytes(mod.resolve("mod_info.json"))).isEqualTo(metadataBefore);
        assertThat(mod.getFileName()).hasToString("Edmund Church");
        assertThat(output.resolve("data/strings/strings.json")).isRegularFile();
    }

    @Test void projectIdentityAndPresentationNeverCarryAGeneratedIdOrDigest() throws Exception {
        Path mod = source("Edmund Church", "a16709513_wkt", "Edmund Church");
        var workflow = new TranslationWorkflow(directory.resolve("owned"));

        var session = workflow.loadMod(mod);

        assertThat(session.project().sourceModId()).isEqualTo("a16709513_wkt");
        assertThat(session.project().patchId()).isEqualTo("a16709513_wkt");
        assertThat(session.project().patchName()).isEqualTo("Edmund Church");
        assertThat(session.modName()).isEqualTo("Edmund Church");
        assertThat(session.identity()).isEqualTo(new SourceModIdentity(
                "a16709513_wkt", "Edmund Church", "Edmund Church", "0.98a"));
        assertThat(session.presentation().translatedFolderName())
                .isEqualTo("Edmund Church - English");
        assertThat(session.presentation().aiRequestFilename())
                .isEqualTo("Edmund Church - Translate to English.json");
        assertThat(DIGEST_RUN.matcher(session.presentation().translatedFolderName()).find()).isFalse();
        assertThat(DIGEST_RUN.matcher(session.presentation().aiRequestFilename()).find()).isFalse();
        // Hashes stay backstage: the hidden workspace is digest-named and never shown.
        assertThat(session.workspace().getFileName().toString()).matches("[0-9a-f]{64}");
    }

    @Test void renamingTheSourceFolderKeepsTheSameSavedWork() throws Exception {
        Path mod = source("Original Folder", "rename.mod", "Renamed Mod");
        var workflow = new TranslationWorkflow(directory.resolve("owned"));
        var first = translated(workflow, mod);
        Path renamed = Files.move(mod, directory.resolve("Renamed Later"));

        var resumed = new TranslationWorkflow(directory.resolve("owned")).loadMod(renamed);

        assertThat(resumed.workspace()).isEqualTo(first.workspace());
        assertThat(resumed.project().entries()).isEqualTo(first.project().entries());
        assertThat(resumed.identity().originalFolderName()).isEqualTo("Renamed Later");
        assertThat(resumed.presentation().translatedFolderName()).isEqualTo("Renamed Mod - English");
    }

    @Test void twoModsWithTheSameDisplayNameGetSeparateHiddenWorkspaces() throws Exception {
        Path first = source("one/Shared", "first.id", "Shared Mod");
        Path second = source("two/Shared", "second.id", "Shared Mod");
        var workflow = new TranslationWorkflow(directory.resolve("owned"));

        var firstSession = workflow.loadMod(first);
        var secondSession = workflow.loadMod(second);

        assertThat(firstSession.workspace()).isNotEqualTo(secondSession.workspace());
        assertThat(firstSession.presentation().translatedFolderName())
                .isEqualTo(secondSession.presentation().translatedFolderName());
    }

    @Test void readableNameCollisionCannotOverwriteAnotherModsTranslatedCopy() throws Exception {
        Path first = source("one/Shared", "first.id", "Shared Mod");
        Path second = source("two/Shared", "second.id", "Shared Mod");
        var workflow = new TranslationWorkflow(directory.resolve("owned"));
        var firstSession = translated(workflow, first);
        var secondSession = translated(workflow, second);
        Path output = directory.resolve("mods").resolve(firstSession.presentation().translatedFolderName());
        workflow.buildPatch(firstSession, output);
        byte[] metadata = Files.readAllBytes(output.resolve("mod_info.json"));
        byte[] strings = Files.readAllBytes(output.resolve("data/strings/strings.json"));

        assertThatThrownBy(() -> workflow.buildPatch(secondSession, output))
                .isInstanceOf(ProjectException.class).hasMessageContaining("different mod");
        assertThat(Files.readAllBytes(output.resolve("mod_info.json"))).isEqualTo(metadata);
        assertThat(Files.readAllBytes(output.resolve("data/strings/strings.json"))).isEqualTo(strings);
    }

    @Test void unmanagedSameIdCopyIsNotAnApprovedReplacementDestination() throws Exception {
        Path original = source("Original", "same.id", "Shared Mod");
        Path other = source("mods/Shared Mod - English", "same.id", "Shared Mod");
        var workflow = new TranslationWorkflow(directory.resolve("owned"));
        var session = translated(workflow, original);
        byte[] before = Files.readAllBytes(other.resolve("data/strings/strings.json"));

        assertThatThrownBy(() -> workflow.buildPatch(session, other))
                .isInstanceOf(ProjectException.class).hasMessageContaining("existing");
        assertThat(Files.readAllBytes(other.resolve("data/strings/strings.json"))).isEqualTo(before);
    }
}
