package com.ssmt.testsupport;

import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.opentest4j.TestAbortedException;

/**
 * Reports a failing test that is listed in the known-failures file, and not yet expired, as aborted
 * (skipped with the reason) instead of failed. Unlisted and expired failures keep failing.
 */
public final class KnownFailureExtension implements TestExecutionExceptionHandler {
    @Override
    public void handleTestExecutionException(ExtensionContext context, Throwable failure) throws Throwable {
        String id = context.getRequiredTestClass().getName() + "#" + context.getRequiredTestMethod().getName();
        Optional<KnownFailures.Entry> entry = KnownFailures.load().find(id);
        if (entry.isPresent() && entry.get().activeOn(LocalDate.now())) {
            throw new TestAbortedException("Known failure until " + entry.get().expires() + ": "
                    + entry.get().reason(), failure);
        }
        if (entry.isPresent()) {
            failure.addSuppressed(new AssertionError("Known-failure entry expired on " + entry.get().expires()));
        }
        throw failure;
    }
}
