package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceAttestationStoreTest {
    @TempDir Path root;

    @Test void mergesLatestSuccessfulRecordPerPhase() throws Exception {
        Path source = Files.createDirectory(root.resolve("source"));
        Files.writeString(source.resolve("data.txt"), "source");
        var guard = new SourceIntegrityGuard();
        var create = guard.runAttested("CREATE_EXTRACTION", source, () -> "created").attestation();
        var build = guard.runAttested("BUILD_CLONE", source, () -> "built").attestation();
        Path workspace = Files.createDirectory(root.resolve("workspace"));
        var store = new SourceAttestationStore();
        var persistence = new WorkflowPersistenceService();
        persistence.commit(workspace, List.of(store.update(workspace, List.of(create))));
        persistence.commit(workspace, List.of(store.update(workspace, List.of(build))));

        var ledger = store.read(workspace.resolve(SourceAttestationStore.FILE_NAME));

        assertThat(ledger.attestations()).extracting(SourceIntegrityGuard.Attestation::operation)
                .containsExactly("BUILD_CLONE", "CREATE_EXTRACTION");
        assertThat(ledger.attestations()).allMatch(attestation ->
                attestation.beforeSha256().equals(attestation.afterSha256()));
    }
}
