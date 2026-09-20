package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BuildEvidenceReaderTest {
    private static final String CANDIDATE_HASH = "a".repeat(64);
    @TempDir Path root;

    @Test void verifiesExactInputsClasspathOutputsAndAuthorities() throws Exception {
        Path input = Files.writeString(root.resolve("source.zip"), "input");
        Path dependency = Files.writeString(root.resolve("api.jar"), "dependency");
        Path output = Files.writeString(root.resolve("mod.jar"), "output");
        Path record = write(profile(reference(input), reference(dependency), reference(output)));

        var checked = new BuildEvidenceReader().read(record, CANDIDATE_HASH);

        assertThat(checked.command()).containsExactly("gradlew.bat", "build", "--offline");
        assertThat(checked.authorities()).extracting(BuildEvidenceReader.Authority::subject)
                .containsExactly("SOURCE", "COMPILED_JAR", "LOADER_PROVIDER");
    }

    @Test void rejectsChangedOrRepeatedReferencedFiles() throws Exception {
        Path input = Files.writeString(root.resolve("source.zip"), "input");
        Path output = Files.writeString(root.resolve("mod.jar"), "output");
        Path repeatedRecord = write(profile(reference(input), reference(input), reference(output)));
        assertThatThrownBy(() -> new BuildEvidenceReader().read(repeatedRecord, CANDIDATE_HASH))
                .hasMessageContaining("Repeated build file reference");

        Path distinct = Files.writeString(root.resolve("api.jar"), "dependency");
        Path record = write(profile(reference(input), reference(distinct), reference(output)));
        Files.writeString(output, "changed");
        Path finalRecord = record;
        assertThatThrownBy(() -> new BuildEvidenceReader().read(finalRecord, CANDIDATE_HASH))
                .hasMessageContaining("Build file hash mismatch");
    }

    @Test void passingBuildRequiresZeroExitAndVerifiedAuthorities() throws Exception {
        Path input = Files.writeString(root.resolve("source.zip"), "input");
        Path dependency = Files.writeString(root.resolve("api.jar"), "dependency");
        Path output = Files.writeString(root.resolve("mod.jar"), "output");
        var valid = profile(reference(input), reference(dependency), reference(output));
        var reader = new BuildEvidenceReader();

        assertThatThrownBy(() -> reader.verifySuccessful(new BuildEvidenceReader.Profile(
                valid.schemaVersion(), valid.candidateSha256(), valid.jdkExecutable(),
                valid.jdkVersion(), valid.command(), valid.workingDirectory(), 1,
                valid.buildInputs(), valid.classpath(), valid.outputs(), valid.authorities())))
                .hasMessageContaining("exit code 0");
        var review = new BuildEvidenceReader.Authority("SOURCE",
                BuildEvidenceReader.AuthorityDisposition.REVIEW_REQUIRED, "pending review");
        assertThatThrownBy(() -> reader.verifySuccessful(new BuildEvidenceReader.Profile(
                valid.schemaVersion(), valid.candidateSha256(), valid.jdkExecutable(),
                valid.jdkVersion(), valid.command(), valid.workingDirectory(), 0,
                valid.buildInputs(), valid.classpath(), valid.outputs(),
                List.of(review, valid.authorities().get(1), valid.authorities().get(2)))))
                .hasMessageContaining("VERIFIED authority: SOURCE");
    }

    private Path write(BuildEvidenceReader.Profile profile) throws Exception {
        Path record = root.resolve("build-evidence.json");
        new ObjectMapper().writeValue(record.toFile(), profile);
        return record;
    }

    private BuildEvidenceReader.Profile profile(BuildEvidenceReader.FileReference input,
            BuildEvidenceReader.FileReference dependency, BuildEvidenceReader.FileReference output) {
        var verified = BuildEvidenceReader.AuthorityDisposition.VERIFIED;
        return new BuildEvidenceReader.Profile(1, CANDIDATE_HASH, "jdk/bin/java", "25.0.1",
                List.of("gradlew.bat", "build", "--offline"), ".", 0,
                List.of(input), List.of(dependency), List.of(output),
                List.of(new BuildEvidenceReader.Authority("SOURCE", verified, "Reviewed source input"),
                        new BuildEvidenceReader.Authority("COMPILED_JAR", verified, "Built output hash recorded"),
                        new BuildEvidenceReader.Authority("LOADER_PROVIDER", verified, "Reviewed provider ownership")));
    }

    private BuildEvidenceReader.FileReference reference(Path file) throws Exception {
        String hash = java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        return new BuildEvidenceReader.FileReference(root.relativize(file).toString().replace('\\', '/'), hash);
    }
}
