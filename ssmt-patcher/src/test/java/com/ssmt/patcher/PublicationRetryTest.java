package com.ssmt.patcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PublicationRetryTest {
    private final Path staging = Path.of("staging");
    private final Path output = Path.of("output");

    @Test
    void retriesDeniedAtomicMoveWithoutOrdinaryFallback() throws Exception {
        var attempts = new AtomicInteger();
        var pauses = new AtomicInteger();
        PatchBuilder.publishPath(staging, output, (from, to) -> {
            if (attempts.incrementAndGet() < 3) {
                throw new AccessDeniedException(from.toString());
            }
        }, (from, to) -> {
            throw new AssertionError("Must keep atomic publication");
        },
                milliseconds -> {
                    assertThat(milliseconds).isEqualTo(100);
                    pauses.incrementAndGet();
                });
        assertThat(attempts.get()).isEqualTo(3);
        assertThat(pauses.get()).isEqualTo(2);
    }

    @Test
    void permanentDenialIsBoundedAndPreservesError() {
        var attempts = new AtomicInteger();
        var denied = new AccessDeniedException("staging");
        assertThatThrownBy(() -> PatchBuilder.publishPath(staging, output, (from, to) -> {
            attempts.incrementAndGet();
            throw denied;
        }, (from, to) -> {
            throw new AssertionError("No fallback on denial");
        }, milliseconds -> { }))
                .isSameAs(denied);
        assertThat(attempts.get()).isEqualTo(51);
    }

    @Test
    void otherIoErrorsAreNotRetried() {
        var failure = new IOException("Disk error");
        assertThatThrownBy(() -> PatchBuilder.publishPath(staging, output,
                (from, to) -> {
                    throw failure;
                }, (from, to) -> { },
                milliseconds -> {
                    throw new AssertionError("No retry");
                })).isSameAs(failure);
    }

    @Test
    void unsupportedAtomicMoveRetainsExistingOrdinaryFallback() throws Exception {
        var attempts = new AtomicInteger();
        PatchBuilder.publishPath(staging, output,
                (from, to) -> {
                    throw new AtomicMoveNotSupportedException("staging", "output", "unsupported");
                },
                (from, to) -> {
                    attempts.incrementAndGet();
                }, milliseconds -> { });
        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    void interruptedRetryPreservesInterruptAndOriginalDenial() {
        var denied = new AccessDeniedException("staging");
        try {
            assertThatThrownBy(() -> PatchBuilder.publishPath(staging, output,
                    (from, to) -> {
                        throw denied;
                    }, (from, to) -> { },
                    milliseconds -> {
                        throw new InterruptedException("Cancelled");
                    })).isSameAs(denied);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(denied.getSuppressed()).hasSize(1);
        } finally {
            Thread.interrupted();
        }
    }
}
