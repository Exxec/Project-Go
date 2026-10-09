package com.ssmt.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class BridgeForgeTranslationComparisonCommandTest {
    @TempDir Path temporary;

    @Test void comparesSavedReferenceAndPropagatesReportAndFailureExitCodes() throws Exception {
        Path source = Path.of("../fixtures/translation-conformance/input");
        Path reference = Path.of("../fixtures/translation-conformance/expected-export.json");
        Path output = temporary.resolve("comparison.json");
        String[] arguments = {"compare", source.toString(), "--reference", reference.toString(),
            "--out", output.toString()};
        assertThat(new CommandLine(new BridgeForgeTranslationCommand()).execute(arguments)).isZero();
        assertThat(new ObjectMapper().readTree(output.toFile()).path("status").asText()).isEqualTo("MATCH");
        assertThat(new CommandLine(new BridgeForgeTranslationCommand()).execute(arguments)).isEqualTo(1);
        var changed = new ObjectMapper().readTree(reference.toFile());
        ((com.fasterxml.jackson.databind.node.ArrayNode) changed.path("unreadable")).add("partial input");
        Path partial = temporary.resolve("partial.json");
        new ObjectMapper().writeValue(partial.toFile(), changed);
        assertThat(new CommandLine(new BridgeForgeTranslationCommand()).execute("compare", source.toString(),
                "--reference", partial.toString(), "--out", temporary.resolve("partial-report.json").toString()))
                .isEqualTo(2);
    }
}
