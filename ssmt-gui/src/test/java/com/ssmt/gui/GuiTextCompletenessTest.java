package com.ssmt.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Regression test for BUG-010: {@code SsmtApplication.java} once referenced
 * {@code GuiText.get("error.aiImport")} for a key that did not exist in
 * {@code messages.properties}, silently throwing {@code MissingResourceException}
 * instead of ever showing the intended dialog. This scans the source for
 * every literal {@code GuiText.get("...")} key and confirms each one
 * actually resolves.
 */
class GuiTextCompletenessTest {
    private static final Pattern KEY_PATTERN = Pattern.compile("GuiText\\.get\\(\"([^\"]+)\"\\)");

    @Test
    void everyLiteralGuiTextKeyReferencedInSourceResolves() throws IOException {
        Path source = Path.of("src/main/java/com/ssmt/gui/SsmtApplication.java");
        String content = Files.readString(source, StandardCharsets.UTF_8);
        Matcher matcher = KEY_PATTERN.matcher(content);
        List<String> keys = new ArrayList<>();
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }

        assertThat(keys).isNotEmpty();
        List<String> missing = new ArrayList<>();
        for (String key : keys) {
            try {
                GuiText.get(key);
            } catch (java.util.MissingResourceException exception) {
                missing.add(key);
            }
        }
        assertThat(missing).isEmpty();
    }
}
