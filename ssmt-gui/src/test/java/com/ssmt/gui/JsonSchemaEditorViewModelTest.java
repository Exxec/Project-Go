package com.ssmt.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssmt.extractor.json.OptInJsonSchemaCatalog;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonSchemaEditorViewModelTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void editsValidatesAndSavesCatalog() throws Exception {
        JsonSchemaEditorViewModel model = new JsonSchemaEditorViewModel();
        model.add(Path.of("data/config/dialog.json"), "/caption");
        model.add(Path.of("data/config/dialog.json"), "/title");
        model.remove(Path.of("data/config/dialog.json"), "/title");
        Path output = temporaryDirectory.resolve("schema.json");

        model.save(output);

        assertThat(new OptInJsonSchemaCatalog().read(output).files())
                .singleElement()
                .satisfies(file -> assertThat(file.pointers())
                        .containsExactly("/caption"));
        assertThatThrownBy(() -> model.add(Path.of("../escape.json"), "/title"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
