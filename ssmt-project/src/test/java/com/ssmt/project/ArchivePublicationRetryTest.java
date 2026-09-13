package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchivePublicationRetryTest {
    @TempDir Path directory;

    @Test
    void temporaryDenialRetriesSameAtomicOperation() throws Exception {
        var calls = new AtomicInteger();
        var waits = new AtomicInteger();
        boolean published = ModInputPreparationService.publish(Path.of("staging"), Path.of("cache"),
                (from, to, atomic) -> {
                    assertThat(atomic).isTrue();
                    if (calls.incrementAndGet() < 3) {
                        throw new AccessDeniedException(from.toString());
                    }
                }, milliseconds -> {
                    assertThat(milliseconds).isEqualTo(100);
                    waits.incrementAndGet();
                });
        assertThat(published).isTrue();
        assertThat(calls.get()).isEqualTo(3);
        assertThat(waits.get()).isEqualTo(2);
    }

    @Test
    void permanentDenialRemainsBoundedAndKeepsOriginalException() {
        var denied = new AccessDeniedException("staging");
        var calls = new AtomicInteger();
        assertThatThrownBy(() -> ModInputPreparationService.publish(Path.of("staging"), Path.of("cache"),
                (from, to, atomic) -> {
                    calls.incrementAndGet();
                    throw denied;
                }, milliseconds -> { })).isSameAs(denied);
        assertThat(calls.get()).isEqualTo(51);
    }

    @Test
    void unsupportedAtomicMoveFallsBackAndRetainsConcurrentCache() throws Exception {
        Path cache = Files.createDirectory(directory.resolve("cache"));
        var calls = new AtomicInteger();
        assertThat(ModInputPreparationService.publish(directory.resolve("staging"), cache,
                (from, to, atomic) -> {
                    calls.incrementAndGet();
                    if (atomic) {
                        throw new AtomicMoveNotSupportedException("staging", "cache", "unsupported");
                    }
                    throw new FileAlreadyExistsException(to.toString());
                }, milliseconds -> { })).isFalse();
        assertThat(calls.get()).isEqualTo(2);
        assertThat(cache).isDirectory();
    }

    @Test
    void existingNonDirectoryAndOtherIoErrorsDoNotRetry() throws Exception {
        Path cache = Files.writeString(directory.resolve("cache"), "keep");
        var race = new FileAlreadyExistsException(cache.toString());
        assertThatThrownBy(() -> ModInputPreparationService.publish(directory.resolve("staging"), cache,
                (from, to, atomic) -> {
                    throw race;
                }, milliseconds -> {
                    throw new AssertionError("No retry");
                })).isSameAs(race);
        var disk = new IOException("Disk error");
        assertThatThrownBy(() -> ModInputPreparationService.publish(Path.of("staging"), Path.of("cache"),
                (from, to, atomic) -> {
                    throw disk;
                }, milliseconds -> {
                    throw new AssertionError("No retry");
                })).isSameAs(disk);
        assertThat(cache).hasContent("keep");
    }

    @Test
    void interruptedRetryRetainsInterruptAndOriginalFailure() {
        var denied = new AccessDeniedException("staging");
        try {
            assertThatThrownBy(() -> ModInputPreparationService.publish(Path.of("staging"), Path.of("cache"),
                    (from, to, atomic) -> {
                        throw denied;
                    }, milliseconds -> {
                        throw new InterruptedException("Cancelled");
                    })).isSameAs(denied);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(denied.getSuppressed()).hasSize(1);
        } finally {
            Thread.interrupted();
        }
    }
}
