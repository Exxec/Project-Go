package com.ssmt.core.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SsmtParseExceptionTest {

    @Test
    void includesSourceAndLineInMessage() {
        SsmtParseException exception =
                new SsmtParseException("Malformed JSON", Path.of("mod_info.json"), 7, null);

        assertThat(exception.getMessage()).isEqualTo("Malformed JSON (mod_info.json:7)");
        assertThat(exception.lineNumber()).hasValue(7);
    }

    @Test
    void representsMissingLineWithoutNull() {
        SsmtParseException exception =
                new SsmtParseException("Missing file", Path.of("mod_info.json"));

        assertThat(exception.lineNumber()).isEmpty();
    }
}
