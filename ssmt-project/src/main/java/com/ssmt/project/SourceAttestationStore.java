package com.ssmt.project;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeMap;

/** Serializes the latest successful source attestation for each workflow phase. */
public final class SourceAttestationStore {
    public static final String FILE_NAME = "source-attestations.json";
    private static final long MAX_BYTES = 64L * 1024 * 1024;
    private static final int SCHEMA_VERSION = 1;

    /** Machine-readable internal ledger; absence is never treated as success. */
    public record Ledger(int schemaVersion,
            List<SourceIntegrityGuard.Attestation> attestations) {
        public Ledger { attestations = List.copyOf(attestations); }
    }

    /** Merges by named phase and returns one transaction-ready document update. */
    public WorkflowPersistenceService.Update update(Path workspace,
            List<SourceIntegrityGuard.Attestation> additions) throws ProjectException {
        Path root = workspace.toAbsolutePath().normalize();
        Path target = root.resolve(FILE_NAME);
        var merged = new TreeMap<String, SourceIntegrityGuard.Attestation>();
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            for (var attestation : read(target).attestations()) {
                merged.put(attestation.operation(), attestation);
            }
        }
        for (var attestation : List.copyOf(additions)) {
            validate(attestation);
            merged.put(attestation.operation(), attestation);
        }
        try {
            byte[] bytes = mapper().writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(new Ledger(SCHEMA_VERSION, List.copyOf(merged.values())));
            if (bytes.length > MAX_BYTES) {
                throw new ProjectException("Source attestation ledger exceeds 64 MiB");
            }
            return new WorkflowPersistenceService.Update(target, bytes);
        } catch (IOException exception) {
            throw new ProjectException("Could not serialize source attestations", exception);
        }
    }

    /** Reads and validates a durable ledger. */
    public Ledger read(Path file) throws ProjectException {
        try {
            if (Files.isSymbolicLink(file)
                    || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(file) > MAX_BYTES) {
                throw new ProjectException("Source attestation ledger is not a bounded regular file");
            }
            Ledger ledger = mapper().readValue(file.toFile(), Ledger.class);
            if (ledger == null || ledger.schemaVersion() != SCHEMA_VERSION
                    || ledger.attestations().size() > 16) {
                throw new ProjectException("Source attestation ledger is invalid");
            }
            var names = new java.util.HashSet<String>();
            for (var attestation : ledger.attestations()) {
                validate(attestation);
                if (!names.add(attestation.operation())) {
                    throw new ProjectException("Repeated source attestation phase");
                }
            }
            return ledger;
        } catch (IOException | IllegalArgumentException exception) {
            throw new ProjectException("Could not read source attestations", exception);
        }
    }

    private static void validate(SourceIntegrityGuard.Attestation attestation)
            throws ProjectException {
        if (attestation.operation() == null || attestation.operation().isBlank()
                || !attestation.beforeSha256().matches("[0-9a-f]{64}")
                || !attestation.afterSha256().equals(attestation.beforeSha256())
                || !attestation.beforeSha256().equals(
                        SourceIntegrityGuard.manifestSha256(attestation.manifest()))
                || !attestation.status().equals("UNCHANGED_OBSERVED_BYTES_AND_METADATA")) {
            throw new ProjectException("Source attestation is invalid or records drift");
        }
    }

    private static JsonMapper mapper() {
        return JsonMapper.builder().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    }
}
