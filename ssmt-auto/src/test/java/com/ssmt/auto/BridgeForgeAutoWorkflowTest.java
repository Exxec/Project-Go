package com.ssmt.auto;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BridgeForgeAutoWorkflowTest {
    @TempDir Path temporary;

    @Test void discoversPureBridgeForgeResponseAndPublishesExactTranslatedJar() throws Exception {
        Path fixture = Path.of("../fixtures/translation-conformance");
        Path source = temporary.resolve("用户源模组");
        Files.createDirectory(source);
        try (var walk = Files.walk(fixture.resolve("input"))) {
            for (Path path : walk.toList()) {
                Path destination = source.resolve(fixture.resolve("input").relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(path, destination);
                }
            }
        }
        var workflow = new AutoWorkflow(temporary.resolve("internal/catalog.db"), temporary.resolve("internal/workspaces"));
        var pending = workflow.run(source);
        assertThat(pending.status()).isEqualTo(AutoRunResult.Status.MASTER_LIBRARY_NEEDED);
        Files.copy(fixture.resolve("translated.json"), temporary.resolve("arbitrary-response.json"));
        var completed = workflow.run(source);
        assertThat(completed.status()).isEqualTo(AutoRunResult.Status.PATCH_PUBLISHED);
        try (var files = Files.list(completed.workspace())) {
            var project = new com.ssmt.project.LocalizationProjectService().read(
                    files.filter(path -> path.toString().endsWith(".ssmt.json")).findFirst().orElseThrow());
            assertThat(project.methodDocument()).isNotNull();
        }
        try (var paths = Files.list(temporary)) {
            Path output = paths.filter(path -> Files.isRegularFile(path.resolve(".ssmt-build-fingerprint")))
                    .findFirst().orElseThrow();
            assertThat(Files.readAllBytes(output.resolve("jars/test.jar")))
                    .isEqualTo(Files.readAllBytes(fixture.resolve("expected-output/jars/test.jar")));
        }
        assertThat(Files.readAllBytes(source.resolve("jars/test.jar")))
                .isEqualTo(Files.readAllBytes(fixture.resolve("input/jars/test.jar")));
    }
}
