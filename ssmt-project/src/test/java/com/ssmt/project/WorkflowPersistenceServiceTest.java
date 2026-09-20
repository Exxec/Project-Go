package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkflowPersistenceServiceTest {
    @TempDir
    Path directory;

    @Test
    void publicationFailureRestoresEveryPreviousDocument() throws Exception {
        Path first = document("first.json", "old first");
        Path second = document("second.json", "old second");
        AtomicInteger publications = new AtomicInteger();
        var persistence = new WorkflowPersistenceService((staging, target) -> {
            if (publications.incrementAndGet() == 2) {
                throw new IOException("injected second publication failure");
            }
            move(staging, target);
        });

        assertThatThrownBy(() -> persistence.commit(directory, List.of(
                update(first, "new first"), update(second, "new second"))))
                .isInstanceOf(ProjectException.class)
                .hasMessageContaining("previous state was retained");

        assertThat(Files.readString(first)).isEqualTo("old first");
        assertThat(Files.readString(second)).isEqualTo("old second");
        assertThat(directory.resolve(".workflow-transaction")).doesNotExist();
    }

    @Test
    void nextProcessRollsBackAnInterruptedPartialPublication() throws Exception {
        Path first = document("first.json", "old first");
        Path second = document("second.json", "old second");
        AtomicInteger publications = new AtomicInteger();
        var interrupted = new WorkflowPersistenceService((staging, target) -> {
            if (publications.incrementAndGet() == 2) {
                throw new SimulatedProcessExit();
            }
            move(staging, target);
        });

        assertThatThrownBy(() -> interrupted.commit(directory, List.of(
                update(first, "new first"), update(second, "new second"))))
                .isInstanceOf(SimulatedProcessExit.class);
        assertThat(Files.readString(first)).isEqualTo("new first");
        assertThat(Files.readString(second)).isEqualTo("old second");

        new WorkflowPersistenceService().recover(directory);

        assertThat(Files.readString(first)).isEqualTo("old first");
        assertThat(Files.readString(second)).isEqualTo("old second");
        assertThat(directory.resolve(".workflow-transaction")).doesNotExist();
    }

    @Test
    void nextProcessKeepsACompleteInterruptedPublication() throws Exception {
        Path first = document("first.json", "old first");
        Path second = document("second.json", "old second");
        AtomicInteger publications = new AtomicInteger();
        var interrupted = new WorkflowPersistenceService((staging, target) -> {
            move(staging, target);
            if (publications.incrementAndGet() == 2) {
                throw new SimulatedProcessExit();
            }
        });

        assertThatThrownBy(() -> interrupted.commit(directory, List.of(
                update(first, "new first"), update(second, "new second"))))
                .isInstanceOf(SimulatedProcessExit.class);

        new WorkflowPersistenceService().recover(directory);

        assertThat(Files.readString(first)).isEqualTo("new first");
        assertThat(Files.readString(second)).isEqualTo("new second");
        assertThat(directory.resolve(".workflow-transaction")).doesNotExist();
    }

    @Test
    void rejectsTargetsOutsideTheWorkspace() {
        Path outside = directory.resolveSibling("outside.json");

        assertThatThrownBy(() -> new WorkflowPersistenceService().commit(
                directory, List.of(update(outside, "unsafe"))))
                .isInstanceOf(ProjectException.class)
                .hasMessageContaining("outside its workspace");
        assertThat(outside).doesNotExist();
    }

    @Test
    void rejectsExistingNonFileTargetWithoutRemovingIt() throws Exception {
        Path target = Files.createDirectory(directory.resolve("project.json"));

        assertThatThrownBy(() -> new WorkflowPersistenceService().commit(
                directory, List.of(update(target, "replacement"))))
                .isInstanceOf(ProjectException.class)
                .hasMessageContaining("regular file");
        assertThat(target).isDirectory();
    }

    @Test
    void removesInterruptedStagingThatNeverReachedAPreparedManifest() throws Exception {
        Path transaction = directory.resolve(".workflow-transaction");
        Files.createDirectories(transaction.resolve("new"));
        Files.writeString(transaction.resolve("new/0.document"), "uncommitted");

        new WorkflowPersistenceService().recover(directory);

        assertThat(transaction).doesNotExist();
    }

    private Path document(String name, String contents) throws IOException {
        Path file = directory.resolve(name);
        Files.writeString(file, contents);
        return file;
    }

    private static WorkflowPersistenceService.Update update(Path target, String contents) {
        return new WorkflowPersistenceService.Update(
                target, contents.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void move(Path staging, Path target) throws IOException {
        Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static final class SimulatedProcessExit extends Error {
        private static final long serialVersionUID = 1L;
    }
}
