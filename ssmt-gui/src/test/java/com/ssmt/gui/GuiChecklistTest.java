package com.ssmt.gui;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssmt.project.ProjectException;
import com.ssmt.project.TranslationWorkflow;
import com.ssmt.project.WorkflowPreferences;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * Walks the visual acceptance checklist of {@code docs/GUI_WORKFLOW_ENHANCEMENTS.md} on the real normal-workflow pane
 * in a real JavaFX window, saves a screenshot per step into {@code build/gui-checklist/} and writes
 * {@code checklist.json}. The rows a program can assert are asserted; the others are listed with the reason they need a
 * person. A row stays NOT_TESTED in the document until the owner has looked at its screenshots. Run with
 * {@code gradlew :ssmt-gui:guiChecklist}; it needs a desktop session and is skipped by the normal test task.
 */
@EnabledIfSystemProperty(named = "ssmt.guiChecklist.out", matches = ".+")
class GuiChecklistTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long WAIT_SECONDS = 60;
    private static Path out;
    @TempDir Path temporary;
    private final ArrayNode rows = JSON.createArrayNode();
    private int counter;

    @BeforeAll static void startToolkit() throws Exception {
        out = Path.of(System.getProperty("ssmt.guiChecklist.out")).toAbsolutePath();
        Files.createDirectories(out);
        try {
            CompletableFuture<Void> started = new CompletableFuture<>();
            Platform.startup(() -> started.complete(null));
            started.get(30, TimeUnit.SECONDS);
        } catch (IllegalStateException alreadyRunning) {
            // The toolkit was started by an earlier test in this JVM.
        } catch (Exception | UnsatisfiedLinkError noDisplay) {
            Assumptions.abort("JavaFX could not start (no desktop session): " + noDisplay);
        }
        Platform.setImplicitExit(false);
    }

    /** One isolated pane, workspace, settings file and disposable copy of the fixture mod. */
    private final class Fixture {
        final Path source;
        final Path destinations;
        final TranslationWorkflowController controller;
        final TranslationWorkflowPane pane;
        final Stage stage;

        Fixture(String folderName) throws Exception {
            Path root = Files.createDirectories(temporary.resolve("fixture-" + (++counter)));
            source = Files.createDirectories(root.resolve(folderName));
            Files.createDirectories(source.resolve("data"));
            Files.writeString(source.resolve("mod_info.json"),
                    "{\"id\":\"checklist\",\"name\":\"Checklist Mod\",\"version\":\"1\"}");
            Files.writeString(source.resolve("data/a.csv"), "id,name\nx,中文名称\n");
            destinations = Files.createDirectories(root.resolve("mods"));
            controller = new TranslationWorkflowController(new TranslationWorkflow(root.resolve("workspaces")),
                    new WorkflowPreferences(root.resolve("settings.json")));
            var holder = new Object[2];
            onFx(() -> {
                Stage window = new Stage();
                TranslationWorkflowPane workflowPane = new TranslationWorkflowPane(window, () -> { }, controller);
                window.setScene(new Scene(workflowPane, 920, 720));
                window.setTitle("Project Go checklist");
                window.show();
                holder[0] = window;
                holder[1] = workflowPane;
                return null;
            });
            stage = (Stage) holder[0];
            pane = (TranslationWorkflowPane) holder[1];
        }

        void close() throws Exception {
            onFx(() -> {
                stage.close();
                return null;
            });
        }

        void drop(Path item) throws Exception {
            onFx(() -> {
                pane.acceptDropped(item);
                return null;
            });
            waitIdle();
        }

        void waitIdle() throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
            while (onFx(pane::busy)) {
                if (System.nanoTime() > deadline) {
                    throw new IllegalStateException("The pane stayed busy for " + WAIT_SECONDS + " seconds");
                }
                Thread.sleep(50);
            }
            Thread.sleep(150);
        }

        String status() throws Exception {
            return onFx(pane::statusText);
        }

        String shot(String name) throws Exception {
            String file = String.format("%02d-%s.png", rows.size() + 1, name);
            WritableImage image = onFx(() -> stage.getScene().snapshot(null));
            writePng(image, out.resolve(file));
            return file;
        }

        /** Exports the request, fills every translation with English text, saves it under an unrelated name. */
        Path answeredRequest(String fileName) throws Exception {
            Path request = java.util.Objects.requireNonNull(source.getParent()).resolve("request.json");
            onFx(() -> {
                pane.exportRequestFile(request);
                return null;
            });
            waitIdle();
            ObjectNode document = (ObjectNode) JSON.readTree(request.toFile());
            int number = 0;
            for (var entry : document.path("entries")) {
                ((ObjectNode) entry).put("translation", "English text " + (++number));
            }
            Path answer = java.util.Objects.requireNonNull(source.getParent()).resolve(fileName);
            JSON.writeValue(answer.toFile(), document);
            return answer;
        }
    }

    private static <T> T onFx(Callable<T> work) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(work.call());
            } catch (Exception | Error failure) {
                result.completeExceptionally(failure);
            }
        });
        return result.get(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    private static void writePng(WritableImage image, Path file) throws IOException {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        BufferedImage converted = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        PixelReader reader = image.getPixelReader();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                converted.setRGB(x, y, reader.getArgb(x, y));
            }
        }
        ImageIO.write(converted, "png", file.toFile());
    }

    private void row(String scenario, String expected, String automated, List<String> shots, String observed) {
        ObjectNode row = rows.addObject();
        row.put("scenario", scenario).put("expected", expected).put("automated", automated)
                .put("observed_by_program", observed);
        ArrayNode files = row.putArray("screenshots");
        shots.forEach(files::add);
        row.put("owner_status", "NOT_TESTED");
    }

    private static void zip(Path directory, Path archive) throws IOException {
        try (var output = new ZipOutputStream(Files.newOutputStream(archive)); var walk = Files.walk(directory)) {
            for (Path path : walk.filter(Files::isRegularFile).sorted().toList()) {
                output.putNextEntry(new ZipEntry(directory.relativize(path).toString()
                        .replace(java.io.File.separatorChar, '/')));
                output.write(Files.readAllBytes(path));
                output.closeEntry();
            }
        }
    }

    private void notAutomated(String scenario, String expected, String reason) {
        row(scenario, expected, "NOT_AUTOMATED", List.of(), reason);
    }

    @Test void walksTheChecklistAndSavesAScreenshotPerStep() throws Exception {
        List<String> failures = new ArrayList<>();
        // 1. folder, ZIP and metadata drops
        var first = new Fixture("Checklist Mod");
        try {
            List<String> shots = new ArrayList<>();
            first.drop(first.source);
            shots.add(first.shot("drop-folder"));
            boolean folder = first.controller.session().orElseThrow().modName().equals("Checklist Mod");
            Path archive = java.util.Objects.requireNonNull(first.source.getParent()).resolve("Checklist Mod.zip");
            zip(first.source, archive);
            first.drop(archive);
            shots.add(first.shot("drop-zip"));
            boolean zipped = first.controller.session().orElseThrow().modName().equals("Checklist Mod");
            first.drop(first.source.resolve("mod_info.json"));
            shots.add(first.shot("drop-mod-info"));
            boolean metadata = first.controller.session().orElseThrow().modName().equals("Checklist Mod");
            row("First chooser and ZIP/folder/metadata drop", "Correct mod appears; one active project.",
                    verdict(folder && zipped && metadata, failures, "drop folder/zip/metadata"), shots,
                    "folder=" + folder + " zip=" + zipped + " metadata=" + metadata
                            + "; the OS file chooser itself needs a person");
        } finally {
            first.close();
        }

        // 2. Unicode path
        String unicodeName = "测试 模组 & Checklist";
        var unicode = new Fixture(unicodeName);
        try {
            unicode.drop(unicode.source);
            String shot = unicode.shot("unicode-folder");
            boolean exact = String.valueOf(unicode.controller.session().orElseThrow().source().getFileName())
                    .equals(unicodeName);
            row("Unicode chooser/drop", "Exact selected source loads without an ASCII copy.",
                    verdict(exact, failures, "unicode source name"), List.of(shot),
                    "loaded source folder name is exactly the dropped one: " + exact);
        } finally {
            unicode.close();
        }

        // 3 and 4. renamed response, then a malformed one
        var response = new Fixture("Checklist Mod");
        try {
            response.drop(response.source);
            Path answer = response.answeredRequest("whatever-the-ai-called-it.json");
            response.drop(answer);
            String imported = response.shot("renamed-response");
            boolean translated = response.controller.session().orElseThrow().project().entries().stream()
                    .allMatch(entry -> entry.translatedText().startsWith("English text"));
            row("Renamed response picker/drop", "Valid response imports by identity.",
                    verdict(translated, failures, "renamed response import"), List.of(imported),
                    "every entry translated: " + translated);

            var active = response.controller.session().orElseThrow();
            Path broken = java.util.Objects.requireNonNull(response.source.getParent()).resolve("broken.json");
            Files.writeString(broken, "{ not json", StandardCharsets.UTF_8);
            boolean refused = false;
            try {
                response.controller.importTranslation(broken);
            } catch (ProjectException expected) {
                refused = true;
            }
            String kept = response.shot("malformed-response-project-kept");
            boolean sameSession = response.controller.session().orElseThrow() == active;
            row("Wrong response or malformed input", "Clear finding; active project and source retained.",
                    verdict(refused && sameSession, failures, "malformed response refused and project kept"),
                    List.of(kept), "refused=" + refused + " activeProjectUnchanged=" + sameSession
                            + "; the error dialog wording needs a person (the pane saves a diagnostic report there)");
        } finally {
            response.close();
        }

        // 5. changed source with prior translations
        var changed = new Fixture("Checklist Mod");
        try {
            changed.drop(changed.source);
            changed.drop(changed.answeredRequest("answer.json"));
            Files.writeString(changed.source.resolve("data/a.csv"), "id,name\nx,变更后的文字\n");
            changed.drop(changed.source);
            String shot = changed.shot("changed-source");
            var review = changed.controller.review();
            boolean showsOld = review.findings().stream().anyMatch(finding ->
                    finding.previousSource().equals("中文名称")
                            && finding.previousTranslation().startsWith("English text"));
            boolean notReady = !changed.controller.readyToBuild();
            row("Changed source with prior translations", "Review shows old/new text; stale translation is not installed.",
                    verdict(showsOld && notReady, failures, "stale translation held for review"), List.of(shot),
                    "review shows previous source and translation: " + showsOld + "; install blocked: " + notReady);
        } finally {
            changed.close();
        }

        // 6, 7, 8 and 11. installs, destinations, quality
        var install = new Fixture("Checklist Mod");
        try {
            install.drop(install.source);
            install.drop(install.answeredRequest("answer.json"));
            boolean noneYet = install.controller.modsDestination().isEmpty();
            Path firstMods = Files.createDirectories(install.destinations.resolve("first"));
            onFx(() -> {
                install.pane.installBelow(firstMods);
                return null;
            });
            install.waitIdle();
            String installed = install.shot("first-install");
            boolean remembered = install.controller.modsDestination().map(firstMods.toRealPath()::equals).orElse(false);
            boolean firstOk = noneYet && remembered && install.controller.lastOutput().isPresent();
            row("First install, then repeat install", "First chooser; later install uses remembered destination.",
                    verdict(firstOk, failures, "first install remembers its destination"), List.of(installed),
                    "no destination before: " + noneYet + "; remembered after success: " + remembered
                            + "; the chooser dialog itself needs a person");

            Path secondMods = Files.createDirectories(install.destinations.resolve("second"));
            onFx(() -> {
                install.pane.installBelow(secondMods);
                return null;
            });
            install.waitIdle();
            String second = install.shot("different-destination");
            boolean moved = install.controller.modsDestination().map(secondMods.toRealPath()::equals).orElse(false);
            row("Explicit different destination", "Chooser appears; new location remembered only after success.",
                    verdict(moved, failures, "new destination remembered after success"), List.of(second),
                    "remembered destination is the new one: " + moved);

            Path output = install.controller.lastOutput().orElseThrow();
            var quality = install.controller.quality();
            Files.writeString(output.resolve("data/a.csv"), "id,name\nx,edited after install\n");
            var findings = install.controller.verifyInstalled();
            String edited = install.shot("quality-and-edit");
            boolean named = findings.stream().anyMatch(finding -> finding.contains("data/a.csv"));
            row("Output quality and subsequent byte edit", "Findings visible; verification names changed file.",
                    verdict(quality.isPresent() && named, failures, "quality shown, edit named"), List.of(edited),
                    "quality evidence present: " + quality.isPresent() + "; verification findings: " + findings
                            + "; the verification dialog needs a person");

            org.junit.jupiter.api.Assertions.assertTrue(Files.isDirectory(secondMods));
            deleteTree(secondMods);
            boolean fallback = install.controller.modsDestination().isEmpty();
            String missing = install.shot("remembered-destination-missing");
            row("Missing/linked remembered destination", "Chooser fallback; no silent wrong-location install.",
                    verdict(fallback, failures, "missing remembered destination falls back"), List.of(missing),
                    "remembered destination ignored when its folder is gone: " + fallback
                            + "; a linked destination and the chooser need a person");
        } finally {
            install.close();
        }

        // Added with the automation plan (G1.3): unreadable inputs and their acceptance checkbox
        var partial = new Fixture("Checklist Mod");
        try {
            Files.createDirectories(partial.source.resolve("data/config"));
            Files.write(partial.source.resolve("data/config/bad.json"),
                    new byte[] {'{', '"', (byte) 0xb5, (byte) 0xe7, (byte) 0xff, '"', '}'});
            partial.drop(partial.source);
            partial.drop(partial.answeredRequest("answer.json"));
            String before = partial.shot("unreadable-before-acceptance");
            boolean listed = partial.status().contains("PARTIAL") && partial.status().contains("bad.json");
            boolean blocked = !partial.controller.readyToBuild();
            onFx(() -> {
                ((javafx.scene.control.CheckBox) partial.stage.getScene().getRoot().lookup(".check-box")).fire();
                return null;
            });
            partial.waitIdle();
            String after = partial.shot("unreadable-after-acceptance");
            boolean ready = partial.controller.readyToBuild() && partial.controller.unreadableAcknowledged();
            row("Unreadable inputs and their acceptance (added by the automation plan)",
                    "PARTIAL list is shown; installing waits for the checkbox.",
                    verdict(listed && blocked && ready, failures, "unreadable list, gate and checkbox"),
                    List.of(before, after), "listed=" + listed + " installBlockedBefore=" + blocked
                            + " readyAfterTicking=" + ready);
        } finally {
            partial.close();
        }

        notAutomated("Cancel during preparation/validation",
                "Task stops at a safe boundary; previous project/output retained.",
                "needs a long-running operation to cancel mid-way; covered by unit tests of WorkflowOperation, not by this walk");
        notAutomated("Publication begins", "Cancel disabled; commit finishes and result is shown.",
                "the publication window is too short to observe from outside the process");
        notAutomated("Advanced comparison", "File/reason groups and incomplete/hash-mismatch states visible.",
                "lives in the Advanced tab of the full application, not in the normal workflow pane");
        notAutomated("Diagnostics without and with selected logs", "Only selected logs exported; overwrite refused.",
                "uses native file choosers");
        notAutomated("Native Auto Explorer drop", "Unicode folder and ZIP reach the same packaged workflow.",
                "needs the packaged executable and Windows Explorer; see ssmt-auto verifyNativeUnicode for the command-line part");

        JSON.writerWithDefaultPrettyPrinter().writeValue(out.resolve("checklist.json").toFile(),
                JSON.createObjectNode().put("schema_version", 1).put("generated_by", "GuiChecklistTest")
                        .put("note", "owner_status stays NOT_TESTED until a person has looked at the screenshots")
                        .set("rows", rows));
        assertThat(failures).as("automated checklist assertions").isEmpty();
    }

    private static String verdict(boolean passed, List<String> failures, String what) {
        if (!passed) {
            failures.add(what);
        }
        return passed ? "AUTOMATED_PASS" : "AUTOMATED_FAIL";
    }

    private static void deleteTree(Path directory) throws IOException {
        try (var walk = Files.walk(directory)) {
            for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
