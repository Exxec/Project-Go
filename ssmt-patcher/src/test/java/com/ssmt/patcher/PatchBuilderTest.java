package com.ssmt.patcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PatchBuilderTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void publishesPristineAndTranslatedClonesWithoutChangingSource() throws Exception {
        Path source = temporaryDirectory.resolve("source");
        Path sourceFile = source.resolve("data/strings/strings.json");
        Files.createDirectories(Objects.requireNonNull(sourceFile.getParent()));
        Files.writeString(sourceFile, "{\"hello\":\"Hello\"}", StandardCharsets.UTF_8);
        Files.writeString(
                source.resolve("mod_info.json"),
                "{\"id\":\"source\",\"name\":\"Source\",\"gameVersion\":\"0.98a-RC8\"}",
                StandardCharsets.UTF_8);
        Path jar = source.resolve("jars/source.jar");
        Files.createDirectories(Objects.requireNonNull(jar.getParent()));
        Files.write(jar, new byte[] {0, 1, 2, 3});
        FileTime timestamp = Files.getLastModifiedTime(sourceFile);
        byte[] hash = hash(sourceFile);
        Path output = temporaryDirectory.resolve("output");
        PatchArtifact artifact = PatchArtifact.utf8(
                Path.of("data/strings/strings.json"), "{\"hello\":\"Bonjour\"}");

        new PatchBuilder().build(new PatchRequest(
                source, output, "source.fr", "Source French", "source", "Source",
                "0.98a-RC8", List.of(artifact)));

        assertThat(Files.readString(output.resolve(artifact.relativePath()), StandardCharsets.UTF_8))
                .isEqualTo("{\"hello\":\"Bonjour\"}");
        Path sourceBackup = PatchBuilder.sourceBackupRoot(output);
        assertThat(Files.readString(
                sourceBackup.resolve(artifact.relativePath()), StandardCharsets.UTF_8))
                .isEqualTo("{\"hello\":\"Hello\"}");
        assertThat(Files.readAllBytes(sourceBackup.resolve("jars/source.jar")))
                .containsExactly(0, 1, 2, 3);
        JsonNode metadata = new ObjectMapper().readTree(output.resolve("mod_info.json").toFile());
        assertThat(metadata.path("id").asText()).isEqualTo("source");
        assertThat(metadata.path("gameVersion").asText()).isEqualTo("0.98a-RC8");
        assertThat(hash(sourceFile)).isEqualTo(hash);
        assertThat(Files.getLastModifiedTime(sourceFile)).isEqualTo(timestamp);
    }

    @Test
    void preservesSourceMetadataInsteadOfWritingOverlayMetadata() throws Exception {
        Path source = temporaryDirectory.resolve("source");
        Files.createDirectories(source);
        Files.writeString(source.resolve("mod_info.json"),
                "{\"id\":\"source\",\"name\":\"Original Name\"}");
        Path output = temporaryDirectory.resolve("output");

        new PatchBuilder().build(new PatchRequest(
                source, output, "source.fr", "Source French", "source", "Source",
                null, List.of()));

        JsonNode metadata = new ObjectMapper().readTree(output.resolve("mod_info.json").toFile());
        assertThat(metadata.path("id").asText()).isEqualTo("source");
        assertThat(metadata.path("name").asText()).isEqualTo("Original Name");
        assertThat(metadata.has("gameVersion")).isFalse();
        assertThat(metadata.has("dependencies")).isFalse();
    }

    @Test
    void rejectsTraversalOverlapAndDuplicatePaths() {
        Path source = temporaryDirectory.resolve("source").toAbsolutePath();
        PatchArtifact valid = PatchArtifact.utf8(Path.of("data/file.csv"), "x");

        assertThatThrownBy(() -> new PatchArtifact(Path.of("../escape"), new byte[] {1}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PatchRequest(
                        source, source.resolve("patch"), "id", "name", "source", "Source",
                        null, List.of(valid)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PatchRequest(
                        source,
                        temporaryDirectory.resolve("output"),
                        "id",
                        "name",
                        "source",
                        "Source",
                        null,
                        List.of(valid, valid)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void skipsUnchangedBuildAndReplacesChangedOutput() throws Exception {
        Path source = temporaryDirectory.resolve("source");
        Files.createDirectories(source);
        Path output = temporaryDirectory.resolve("output");
        PatchRequest first = new PatchRequest(
                source,
                output,
                "source.fr",
                "Source French",
                "source",
                "Source",
                "0.98a-RC8",
                List.of(PatchArtifact.utf8(Path.of("data/value.txt"), "one")));
        PatchBuilder builder = new PatchBuilder();

        assertThat(builder.build(first).changed()).isTrue();
        Path generated = output.resolve("data/value.txt");
        FileTime timestamp = Files.getLastModifiedTime(generated);
        assertThat(builder.build(first).changed()).isFalse();
        assertThat(Files.getLastModifiedTime(generated)).isEqualTo(timestamp);

        PatchRequest changed = new PatchRequest(
                source,
                output,
                "source.fr",
                "Source French",
                "source",
                "Source",
                "0.98a-RC8",
                List.of(PatchArtifact.utf8(Path.of("data/value.txt"), "two")));
        assertThat(builder.build(changed).changed()).isTrue();
        assertThat(Files.readString(generated, StandardCharsets.UTF_8)).isEqualTo("two");
    }

    @Test
    void rebuildsWhenOnlySourceGameVersionChanges() throws Exception {
        Path source = temporaryDirectory.resolve("source");
        Files.createDirectories(source);
        Path output = temporaryDirectory.resolve("output");
        List<PatchArtifact> artifacts =
                List.of(PatchArtifact.utf8(Path.of("data/value.txt"), "one"));
        PatchBuilder builder = new PatchBuilder();

        assertThat(builder.build(new PatchRequest(
                source, output, "source.fr", "Source French", "source", "Source",
                "0.98a-RC8", artifacts)).changed()).isTrue();
        assertThat(builder.build(new PatchRequest(
                source, output, "source.fr", "Source French", "source", "Source",
                "0.99a", artifacts)).changed()).isTrue();
    }

    @Test
    void rebuildsBothClonesWhenAnUntranslatedSourceFileChanges() throws Exception {
        Path source = temporaryDirectory.resolve("source");
        Files.createDirectories(source);
        Path sourceOnly = source.resolve("readme.txt");
        Files.writeString(sourceOnly, "one");
        Path output = temporaryDirectory.resolve("output");
        PatchRequest request = new PatchRequest(
                source, output, "source.fr", "Source French", "source", "Source",
                null, List.of());
        PatchBuilder builder = new PatchBuilder();

        assertThat(builder.build(request).changed()).isTrue();
        Files.writeString(sourceOnly, "two");
        assertThat(builder.build(request).changed()).isTrue();

        assertThat(Files.readString(output.resolve("readme.txt"))).isEqualTo("two");
        assertThat(Files.readString(
                PatchBuilder.sourceBackupRoot(output).resolve("readme.txt")))
                .isEqualTo("two");
    }

    @Test
    void restoresBothPreviousClonesWhenSecondPublicationFails() throws Exception {
        Path source = temporaryDirectory.resolve("source");
        Files.createDirectories(source);
        Files.writeString(source.resolve("value.txt"), "new source");
        Path output = temporaryDirectory.resolve("output");
        Path sourceBackup = PatchBuilder.sourceBackupRoot(output);
        Files.createDirectories(output);
        Files.createDirectories(sourceBackup);
        Files.writeString(output.resolve("value.txt"), "old translated");
        Files.writeString(sourceBackup.resolve("value.txt"), "old source");
        java.util.concurrent.atomic.AtomicBoolean failed =
                new java.util.concurrent.atomic.AtomicBoolean();
        PatchBuilder builder = new PatchBuilder((staging, destination) -> {
            if (destination.equals(output) && failed.compareAndSet(false, true)) {
                throw new IOException("injected translated publication failure");
            }
            PatchBuilder.publishPath(staging, destination);
        });
        PatchRequest request = new PatchRequest(
                source, output, "source.fr", "Source French", "source", "Source",
                null, List.of(PatchArtifact.utf8(Path.of("value.txt"), "translated")));

        assertThatThrownBy(() -> builder.build(request))
                .isInstanceOf(PatchBuilderException.class)
                .hasMessageContaining("Could not build translated clone");

        assertThat(Files.readString(output.resolve("value.txt")))
                .isEqualTo("old translated");
        assertThat(Files.readString(sourceBackup.resolve("value.txt")))
                .isEqualTo("old source");
    }

    private static byte[] hash(Path file) throws IOException, NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
    }
}
