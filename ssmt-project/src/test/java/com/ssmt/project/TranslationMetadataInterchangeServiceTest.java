package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssmt.tm.TranslationGenerationMetadata;
import com.ssmt.tm.TranslationReviewStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TranslationMetadataInterchangeServiceTest {
    @TempDir Path temporary;

    @Test
    void roundTripsVersionedJsonAndCsvAndAcceptsLegacyFiles() throws Exception {
        var metadata = new TranslationGenerationMetadata(
                "argos-translate", "zh-en", "1", Instant.EPOCH, false,
                TranslationReviewStatus.DRAFT);
        Map<String, TranslationGenerationMetadata> values = Map.of("data/a.json#/x", metadata);
        Path json = temporary.resolve("metadata.json");
        Path csv = temporary.resolve("metadata.csv");
        TranslationMetadataInterchangeService service =
                new TranslationMetadataInterchangeService();

        service.writeJson(json, values);
        service.writeCsv(csv, values);

        assertThat(service.readJson(json)).isEqualTo(values);
        assertThat(service.readCsv(csv)).isEqualTo(values);
        Path legacyJson = temporary.resolve("legacy.json");
        Path legacyCsv = temporary.resolve("legacy.csv");
        Files.writeString(legacyJson, "{\"schemaVersion\":1,\"entries\":[]}");
        Files.writeString(legacyCsv, "source,translation\nA,B\n");
        assertThat(service.readJson(legacyJson)).isEmpty();
        assertThat(service.readCsv(legacyCsv)).isEmpty();
    }
}
