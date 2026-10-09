package com.ssmt.project;

import com.ssmt.core.CancellationToken;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** Per-worker cooperative cancellation; publication is a non-interruptible critical section. */
public final class WorkflowOperation {
    private static final ThreadLocal<Context> ACTIVE = new ThreadLocal<>();
    private WorkflowOperation() { }
    private static final class Context {
        private final CancellationToken cancellation;
        private final Consumer<String> progress;
        private boolean publishing;
        Context(CancellationToken cancellation, Consumer<String> progress) {
            this.cancellation = cancellation;
            this.progress = progress;
        }
    }

    /** Runs work on the caller's worker thread, always clearing the operation context. */
    public static <T> T run(CancellationToken cancellation, Consumer<String> progress, Callable<T> work)
            throws Exception {
        if (ACTIVE.get() != null) { throw new IllegalStateException("Nested workflow operations are unsupported"); }
        ACTIVE.set(new Context(java.util.Objects.requireNonNull(cancellation),
                java.util.Objects.requireNonNull(progress)));
        try {
            stage("Preparing input");
            return work.call();
        } finally {
            ACTIVE.remove();
        }
    }

    /** Checks at a read-only or staging boundary; cancellation never interrupts publication. */
    public static void checkCancellation() {
        Context context = ACTIVE.get();
        if (context != null && !context.publishing) { context.cancellation.throwIfCancellationRequested(); }
    }

    /** Reports a bounded stage, not an invented percentage. */
    public static void stage(String stage) {
        checkCancellation();
        Context context = ACTIVE.get();
        if (context != null) { context.progress.accept(stage); }
    }

    /** Last cancellable boundary before any authoritative document or output publication. */
    public static void beginPublication() {
        checkCancellation();
        Context context = ACTIVE.get();
        if (context != null) {
            context.publishing = true;
            context.progress.accept("Publishing safely; cancellation is no longer available");
        }
    }
}
