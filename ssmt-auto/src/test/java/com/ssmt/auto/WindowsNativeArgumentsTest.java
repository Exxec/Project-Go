package com.ssmt.auto;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class WindowsNativeArgumentsTest {
    @Test void ordinaryJavaInvocationPreservesSuppliedUnicodeWithoutNativeLookup() {
        String[] input = {"C:\\mods\\\u4e2d\u6587 folder", "--smoke-test"};
        assertThat(WindowsNativeArguments.resolve(input)).containsExactly(input).isNotSameAs(input);
    }
}
