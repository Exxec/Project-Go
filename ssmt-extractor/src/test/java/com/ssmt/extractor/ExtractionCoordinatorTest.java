package com.ssmt.extractor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssmt.core.OperationCancelledException;
import com.ssmt.core.RuntimeBudgets;
import com.ssmt.core.exception.SsmtParseException;
import com.ssmt.core.model.ExtractedString;
import com.ssmt.core.plugin.ExtractionRequest;
import com.ssmt.core.plugin.FileExtractor;
import com.ssmt.extractor.csv.StandardCsvFileExtractor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExtractionCoordinatorTest {

    @Test
    void discoversSupportedFilesAndOrdersCombinedResults(@TempDir Path modRoot)
            throws Exception {
        write(modRoot.resolve("data/weapons/weapon_data.csv"), """
                name,id
                Pulse Laser,pulse_laser
                """);
        write(modRoot.resolve("data/strings/descriptions.csv"), """
                id,type,text1,text2,text3,text4,notes
                pulse_laser,WEAPON,Description,,,,
                """);
        write(modRoot.resolve("data/custom/ignored.xyz"), "ignored");

        ExtractionReport report = new ExtractionCoordinator(
                List.of(new StandardCsvFileExtractor())).extractMod("test_mod", modRoot);

        assertThat(report.strings())
                .extracting(ExtractedString::sourceFile, ExtractedString::key)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                Path.of("data/strings/descriptions.csv"),
                                "csv:id%2Ctype=pulse_laser%00WEAPON:text1"),
                        org.assertj.core.groups.Tuple.tuple(
                                Path.of("data/weapons/weapon_data.csv"),
                                "csv:id=pulse_laser:name"));
        assertThat(report.skippedFiles())
                .containsExactly(Path.of("data/custom/ignored.xyz"));
    }

    @Test
    void rejectsAmbiguousHandlerSelection(@TempDir Path modRoot) throws Exception {
        Path source = modRoot.resolve("data/strings/descriptions.csv");
        write(source, "id,type,text1,text2,text3,text4\nid,TYPE,Text,,,\n");
        FileExtractor first = new EmptyExtractor();
        FileExtractor second = new EmptyExtractor();

        ExtractionCoordinator coordinator = new ExtractionCoordinator(List.of(first, second));

        assertThatThrownBy(() -> coordinator.extractMod("test_mod", modRoot))
                .isInstanceOf(SsmtParseException.class)
                .hasMessageContaining("Multiple extractors");
    }

    @Test
    void rejectsMissingModRoot() {
        ExtractionCoordinator coordinator =
                new ExtractionCoordinator(List.of(new StandardCsvFileExtractor()));

        assertThatThrownBy(() -> coordinator.extractMod("test_mod", Path.of("missing-mod")))
                .isInstanceOf(SsmtParseException.class)
                .hasMessageContaining("Not a directory");
    }

    @Test
    void cancellationAndFileBudgetsFailAtDiscoveryBoundary(@TempDir Path modRoot)
            throws Exception {
        write(modRoot.resolve("one.txt"), "one");
        write(modRoot.resolve("two.txt"), "two");
        ExtractionCoordinator coordinator =
                new ExtractionCoordinator(List.of(new StandardCsvFileExtractor()));

        assertThatThrownBy(() -> coordinator.extractMod(
                        "test_mod",
                        modRoot,
                        () -> true,
                        RuntimeBudgets.DEFAULTS))
                .isInstanceOf(OperationCancelledException.class);
        assertThatThrownBy(() -> coordinator.extractMod(
                        "test_mod",
                        modRoot,
                        () -> false,
                        new RuntimeBudgets(1, 1024, Duration.ofSeconds(1), 10)))
                .isInstanceOf(SsmtParseException.class)
                .hasMessageContaining("file count");
    }

    private static void write(Path path, String content) throws Exception {
        Files.createDirectories(Objects.requireNonNull(path.getParent()));
        Files.writeString(path, content);
    }

    private static final class EmptyExtractor implements FileExtractor {
        @Override
        public boolean supports(Path sourceFile) {
            return sourceFile.toString().endsWith("descriptions.csv");
        }

        @Override
        public List<ExtractedString> extract(ExtractionRequest request) {
            return List.of();
        }
    }
}
