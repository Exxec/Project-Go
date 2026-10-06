package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BridgeForgeTranslationConformanceTest {
    private static final Path FIXTURE = Path.of("../fixtures/translation-conformance");
    @TempDir Path temporary;

    @Test void exportsExactlyTheReferenceUnitsAndHashesWithoutChangingSources() throws Exception {
        var expected = new ObjectMapper().readTree(FIXTURE.resolve("expected-export.json").toFile());
        var service = new BridgeForgeTranslationService();
        var actual = service.export(FIXTURE.resolve("input"));
        assertThat(actual.path("entries")).isEqualTo(expected.path("entries"));
        assertThat(actual.path("file_hashes")).isEqualTo(expected.path("file_hashes"));
        assertThat(actual.path("unreadable")).isEqualTo(expected.path("unreadable"));
        assertThat(actual.path("entry_count")).isEqualTo(expected.path("entry_count"));
        assertThat(service.export(FIXTURE.resolve("input"))).isEqualTo(actual);
    }

    @Test void rewritesOnlyReferencedLiteralsWithModifiedUtf8AndStableIndices() throws Exception {
        byte[] original;
        try (var jar = new java.util.zip.ZipFile(FIXTURE.resolve("input/jars/test.jar").toFile());
                var input = jar.getInputStream(jar.getEntry("Fixture.class"))) {
            original = input.readAllBytes();
        }
        var pool = BridgeForgeClassFile.parse(original);
        assertThat(pool.literals()).containsExactlyEntriesOf(Map.of(5, "中文字符串"));
        String target = "English\0\ud83d\ude80";
        byte[] rewritten = BridgeForgeClassFile.rewrite(original, Map.of(5, target));
        var after = BridgeForgeClassFile.parse(rewritten);
        assertThat(after.count()).isEqualTo(pool.count());
        assertThat(after.literals()).containsEntry(5, target);
        assertThat(after.utf8()).containsEntry(7, "中文成员名");
        assertThatThrownBy(() -> BridgeForgeClassFile.rewrite(original, Map.of(7, "renamed")))
                .isInstanceOf(java.io.IOException.class);
        assertThatThrownBy(() -> BridgeForgeClassFile.rewrite(original, Map.of(5, "a".repeat(65536))))
                .isInstanceOf(java.io.IOException.class);
    }

    @Test void readsBridgeForgeDocumentWithoutChangingItsTranslationOrExtensions() throws Exception {
        var exchange = new BridgeForgeTranslationDocument();
        var document = exchange.read(FIXTURE.resolve("translated.json"));
        Path destination = temporary.resolve("roundtrip.json");
        exchange.write(destination, document);
        assertThat(exchange.read(destination)).isEqualTo(document);
        assertThat(Files.size(destination)).isPositive();
    }

    @Test void appliesBridgeForgeDocumentByteForByteIncludingJarAndPreservesInput() throws Exception {
        var exchange = new BridgeForgeTranslationDocument();
        var document = exchange.read(FIXTURE.resolve("translated.json"));
        Path output = temporary.resolve("translated");
        var report = new BridgeForgeTranslationApply().apply(FIXTURE.resolve("input"), document, output, false);
        assertThat(report.path("problems")).isEmpty();
        try (var walk = Files.walk(FIXTURE.resolve("expected-output"))) {
            for (Path expected : walk.filter(Files::isRegularFile).toList()) {
                Path relative = FIXTURE.resolve("expected-output").relativize(expected);
                assertThat(Files.readAllBytes(output.resolve(relative))).as(relative.toString())
                        .isEqualTo(Files.readAllBytes(expected));
            }
        }
        var actual = new BridgeForgeTranslationService().export(FIXTURE.resolve("input"));
        var reference = new ObjectMapper().readTree(FIXTURE.resolve("expected-export.json").toFile());
        assertThat(actual.path("file_hashes")).isEqualTo(reference.path("file_hashes"));
    }

    @Test void refusesHashAndProtectedFieldDriftBeforeCreatingOutput() throws Exception {
        var exchange = new BridgeForgeTranslationDocument();
        var document = exchange.read(FIXTURE.resolve("translated.json"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) document.path("file_hashes"))
                .put("data/bare.csv", "0".repeat(64));
        Path output = temporary.resolve("refused");
        assertThatThrownBy(() -> new BridgeForgeTranslationApply().apply(FIXTURE.resolve("input"), document, output, false))
                .isInstanceOf(ProjectException.class).hasMessageContaining("changed since export");
        assertThat(output).doesNotExist();
        var forged = exchange.read(FIXTURE.resolve("translated.json"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) forged.path("entries").get(0)).put("source", "forged");
        assertThatThrownBy(() -> new BridgeForgeTranslationApply().apply(FIXTURE.resolve("input"), forged, output, false))
                .isInstanceOf(ProjectException.class).hasMessageContaining("Protected entry field");
        assertThat(output).doesNotExist();
    }

    @Test void placeholderRulesIncludeArgumentOrderAndAsciiVariableBoundaries() {
        assertThat(BridgeForgePlaceholders.tokens("50% faster, 50%%, $player.name. $NPC中文\u0001"))
                .containsExactlyInAnyOrderEntriesOf(Map.of("$player.name", 1, "$NPC", 1, "\u0001", 1));
        assertThat(BridgeForgePlaceholders.resolve("%s %s %d", "%s %d %s"))
                .isEqualTo("%1$s %3$d %2$s");
        assertThat(BridgeForgePlaceholders.resolve("%s %d", "%1$s %2$d")).isEqualTo("%1$s %2$d");
        assertThat(BridgeForgePlaceholders.resolve("%s $player", "%d $player")).isNull();
    }

    @Test void prefillMatchesAuthorRecordAndAlignedJarReferenceWithoutOverwritingDrafts() throws Exception {
        var document = new BridgeForgeTranslationService().export(FIXTURE.resolve("input"));
        var prefill = new BridgeForgeTranslationPrefill();
        var result = prefill.records(document, java.util.List.of(FIXTURE.resolve("input/ai/en")));
        assertThat(result.path("filled").asInt()).isEqualTo(6);
        assertThat(result.path("ambiguous").asInt()).isZero();
        for (var entry : document.path("entries")) {
            if (entry.path("source").asText().equals("中文厂商")) {
                assertThat(entry.path("translation").asText()).isEqualTo("Manufacturer");
                assertThat(entry.path("provenance").asText()).isEqualTo("record");
            }
        }
        assertThat(prefill.records(document, java.util.List.of(FIXTURE.resolve("input/ai/en")))
                .path("filled").asInt()).isZero();
        var reference = prefill.reference(document, FIXTURE.resolve("input"), FIXTURE.resolve("expected-output"));
        assertThat(reference.path("filled").asInt()).isPositive();
        var jarEntry = java.util.stream.StreamSupport.stream(document.path("entries").spliterator(), false)
                .filter(entry -> entry.path("kind").asText().equals("jar")).findFirst().orElseThrow();
        assertThat(jarEntry.path("translation").asText()).startsWith("Text ");
        assertThat(jarEntry.path("provenance").asText()).isEqualTo("reference");
    }

    @Test void explicitInPlaceAppliesOnlyToTheDesignatedWorkingCopy() throws Exception {
        Path working = temporary.resolve("working");
        Files.createDirectory(working);
        try (var walk = Files.walk(FIXTURE.resolve("input"))) {
            for (Path path : walk.toList()) {
                Path target = working.resolve(FIXTURE.resolve("input").relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(path, target);
                }
            }
        }
        var document = new BridgeForgeTranslationDocument().read(FIXTURE.resolve("translated.json"));
        new BridgeForgeTranslationApply().apply(working, document, null, true);
        assertThat(Files.readAllBytes(working.resolve("jars/test.jar")))
                .isEqualTo(Files.readAllBytes(FIXTURE.resolve("expected-output/jars/test.jar")));
        assertThatThrownBy(() -> new BridgeForgeTranslationApply().apply(working, document, null, true))
                .isInstanceOf(ProjectException.class).hasMessageContaining("changed since export");
    }

    @Test void normalWorkflowPersistsMethodHashesExchangesPureBridgeForgeAndPublishesAuditedClone() throws Exception {
        var workflow = new TranslationWorkflow(temporary.resolve("workspaces"));
        var session = workflow.loadMod(FIXTURE.resolve("input"));
        assertThat(session.project().methodDocument()).isNotNull();
        var serialized = new LocalizationProjectService();
        Path persisted = temporary.resolve("project.json");
        serialized.write(persisted, session.project());
        var restored = serialized.read(persisted);
        assertThat(restored.methodDocument()).isEqualTo(session.project().methodDocument());
        Path request = temporary.resolve("request.json");
        workflow.exportTranslation(session, request);
        var exported = new BridgeForgeTranslationDocument().read(request);
        assertThat(exported.path("entries").size()).isEqualTo(18);
        var complete = workflow.importTranslation(session, FIXTURE.resolve("translated.json"));
        Path destination = temporary.resolve("normal-output");
        workflow.buildPatch(complete, destination);
        try (var walk = Files.walk(FIXTURE.resolve("expected-output"))) {
            for (Path expected : walk.filter(Files::isRegularFile).toList()) {
                Path relative = FIXTURE.resolve("expected-output").relativize(expected);
                assertThat(Files.readAllBytes(destination.resolve(relative))).as(relative.toString())
                        .isEqualTo(Files.readAllBytes(expected));
            }
        }
        assertThat(destination.resolve(".ssmt-build-fingerprint")).isRegularFile();
        assertThat(complete.project().entries()).allMatch(entry -> !entry.translatedText().isBlank());
    }

    @Test void normalExchangeRefusesForgedHashesAndRetainsTheCommittedProject() throws Exception {
        var workflow = new TranslationWorkflow(temporary.resolve("workspaces"));
        var session = workflow.loadMod(FIXTURE.resolve("input"));
        var document = new BridgeForgeTranslationDocument().read(FIXTURE.resolve("translated.json"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) document.path("file_hashes"))
                .put("data/bare.csv", "0".repeat(64));
        Path forged = temporary.resolve("forged.json");
        new BridgeForgeTranslationDocument().write(forged, document);
        byte[] before = Files.readAllBytes(session.workspace().resolve("project.ssmt.json"));
        assertThatThrownBy(() -> workflow.importTranslation(session, forged))
                .isInstanceOf(ProjectException.class).hasMessageContaining("file_hashes");
        assertThat(Files.readAllBytes(session.workspace().resolve("project.ssmt.json"))).isEqualTo(before);
    }
}
