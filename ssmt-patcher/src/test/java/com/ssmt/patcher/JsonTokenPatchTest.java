package com.ssmt.patcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonTokenPatchTest {
    @TempDir
    Path directory;

    @Test
    void preservesCommentsBomLineEndingsNumbersAndEscapedPointerSiblings() throws Exception {
        String text = "\uFEFF{ # header\r\n 'a/b': [ { '~name': 'old', number: 1e+03, }, ],"
                + " /* keep */ untouched: \"\\u4e00\", }\r\n, // retained source suffix\r\n";
        Path relative = Path.of("strings.json");
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        Files.write(directory.resolve(relative), bytes);
        PatchArtifact result = new StandardFileInjector().inject(directory, List.of(
                new TranslationReplacement(relative, "json:/a~1b/0/~0name", "old", "new\n\"value")));
        assertThat(new String(result.content(), StandardCharsets.UTF_8))
                .isEqualTo(text.replace("'old'", "\"new\\n\\\"value\""));
        assertThat(Files.readAllBytes(directory.resolve(relative))).isEqualTo(bytes);
    }

    @Test
    void preservesGb18030BytesOutsideMultipleTargetTokens() throws Exception {
        Charset charset = Charset.forName("GB18030");
        String text = "{name: '中文', array: [\"old\", 'old'], # 中文\r\n id: 'technical',}";
        Path relative = Path.of("strings.json");
        byte[] bytes = text.getBytes(charset);
        Files.write(directory.resolve(relative), bytes);
        PatchArtifact result = new StandardFileInjector().inject(directory, List.of(
                new TranslationReplacement(relative, "json:/name", "中文", "English"),
                new TranslationReplacement(relative, "json:/array/1", "old", "second")));
        assertThat(result.content()).isEqualTo(text.replace("'中文'", "\"English\"")
                .replace("'old'", "\"second\"").getBytes(charset));
        assertThat(Files.readAllBytes(directory.resolve(relative))).isEqualTo(bytes);
    }

    @Test
    void rejectsDuplicateFieldsAndStaleTextWithoutTouchingSource() throws Exception {
        Path relative = Path.of("strings.json");
        String text = "{name:'old', name:'old'}";
        Files.writeString(directory.resolve(relative), text);
        TranslationReplacement item = new TranslationReplacement(relative, "json:/name", "old", "new");
        assertThatThrownBy(() -> new StandardFileInjector().inject(directory, List.of(item)))
                .isInstanceOf(PatchBuilderException.class);
        assertThat(Files.readString(directory.resolve(relative))).isEqualTo(text);
        Files.writeString(directory.resolve(relative), "{name:'different'}");
        assertThatThrownBy(() -> new StandardFileInjector().inject(directory, List.of(item)))
                .isInstanceOf(PatchBuilderException.class).hasMessageContaining("Stale");
        assertThat(Files.readString(directory.resolve(relative))).isEqualTo("{name:'different'}");
    }
}
