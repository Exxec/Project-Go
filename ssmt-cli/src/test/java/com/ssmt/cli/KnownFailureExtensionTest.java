package com.ssmt.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.ssmt.testsupport.KnownFailureExtension;
import com.ssmt.testsupport.KnownFailures;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.TestAbortedException;

class KnownFailureExtensionTest {
    @TempDir Path temporary;

    @AfterEach void clearProperty() {
        System.clearProperty(KnownFailures.PROPERTY);
    }

    private ExtensionContext context() throws NoSuchMethodException {
        ExtensionContext context = mock(ExtensionContext.class);
        Method method = KnownFailureExtensionTest.class.getDeclaredMethod("clearProperty");
        doReturn(KnownFailureExtensionTest.class).when(context).getRequiredTestClass();
        doReturn(method).when(context).getRequiredTestMethod();
        return context;
    }

    private void listed(String expires) throws Exception {
        Path file = temporary.resolve("known.tsv");
        Files.writeString(file, KnownFailureExtensionTest.class.getName() + "#clearProperty\t" + expires + "\tdisk\n");
        System.setProperty(KnownFailures.PROPERTY, file.toString());
    }

    @Test void aListedUnexpiredFailureIsAbortedWithItsReason() throws Exception {
        listed("2999-01-01");
        assertThatThrownBy(() -> new KnownFailureExtension()
                .handleTestExecutionException(context(), new AssertionError("boom")))
                .isInstanceOf(TestAbortedException.class).hasMessageContaining("disk");
    }

    @Test void anExpiredOrUnlistedFailureStaysAFailure() throws Exception {
        listed("2000-01-01");
        var failure = new AssertionError("boom");
        assertThatThrownBy(() -> new KnownFailureExtension().handleTestExecutionException(context(), failure))
                .isSameAs(failure);
        assertThat(failure.getSuppressed()).hasSize(1);
        System.clearProperty(KnownFailures.PROPERTY);
        var other = new AssertionError("other");
        assertThatThrownBy(() -> new KnownFailureExtension().handleTestExecutionException(context(), other))
                .isSameAs(other);
    }
}
