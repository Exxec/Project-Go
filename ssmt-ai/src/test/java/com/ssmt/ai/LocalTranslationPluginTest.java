package com.ssmt.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class LocalTranslationPluginTest {
    private static final AiTranslationRequest REQUEST =
            new AiTranslationRequest("Hello world", "en", "es", "", "");

    @Test
    void argosUsesStdinAndLanguageArguments() throws Exception {
        RecordingProcess process = new RecordingProcess(new LocalProcessResult(0, "Hola\n", ""));
        var plugin = new ArgosTranslatePlugin(
                Path.of("argos-translate"), process, Duration.ofSeconds(5));

        assertThat(plugin.translate(REQUEST)).isEqualTo("Hola");
        assertThat(process.command).containsExactly(
                "argos-translate", "--from-lang", "en", "--to-lang", "es");
        assertThat(process.input).isEqualTo("Hello world");
        assertThat(process.environment).containsEntry("ARGOS_DEVICE_TYPE", "cpu");
    }

    @Test
    void translateLocallyUsesConfiguredInstalledModel() throws Exception {
        RecordingProcess process = new RecordingProcess(new LocalProcessResult(0, "Hola local\n", ""));
        var plugin = new TranslateLocallyPlugin(
                Path.of("translateLocally"), TranslateLocallyPlugin.DEFAULT_ZH_EN_MODEL,
                process, Duration.ofSeconds(5));

        assertThat(plugin.translate(REQUEST)).isEqualTo("Hola local");
        assertThat(process.command).containsExactly(
                "translateLocally", "-m", "Helsinki-NLP/opus-mt-zh-en");
        assertThat(process.input).isEqualTo("Hello world");
    }

    @Test
    void providerFailureDoesNotReturnPartialOutput() {
        RecordingProcess process = new RecordingProcess(new LocalProcessResult(2, "partial", "model absent"));
        var plugin = new ArgosTranslatePlugin(
                Path.of("argos-translate"), process, Duration.ofSeconds(5));

        assertThatThrownBy(() -> plugin.translate(REQUEST))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("exit code 2")
                .hasMessageContaining("model absent");
    }

    @Test
    void acceleratedArgosFailureRetriesOnceOnCpuAndCachesFallback() throws Exception {
        SequenceProcess process = new SequenceProcess(
                new LocalProcessResult(1, "", "CUDA unavailable"),
                new LocalProcessResult(0, "Hola CPU", ""),
                new LocalProcessResult(0, "Hola otra vez", ""));
        var plugin = new ArgosTranslatePlugin(
                Path.of("argos-translate"), ArgosDevice.AUTO,
                process, Duration.ofSeconds(5));

        assertThat(plugin.translate(REQUEST)).isEqualTo("Hola CPU");
        assertThat(plugin.lastExecution().requestedDevice()).isEqualTo(ArgosDevice.AUTO);
        assertThat(plugin.lastExecution().usedBackend()).isEqualTo(TranslationBackend.CPU);
        assertThat(plugin.lastExecution().fallbackReason()).contains("CUDA unavailable");
        assertThat(process.environments).extracting(map -> map.get("ARGOS_DEVICE_TYPE"))
                .containsExactly("cuda", "cpu");

        assertThat(plugin.translate(REQUEST)).isEqualTo("Hola otra vez");
        assertThat(process.environments).extracting(map -> map.get("ARGOS_DEVICE_TYPE"))
                .containsExactly("cuda", "cpu", "cpu");
    }

    @Test
    void argosPassesSupportedWorkerAndBatchLimitsButDoesNotClaimGpuBudgetEnforcement()
            throws Exception {
        RecordingProcess process = new RecordingProcess(new LocalProcessResult(0, "Hola", ""));
        TranslationResourceLimits limits =
                new TranslationResourceLimits(2, 16, OptionalLong.of(2048));
        var plugin = new ArgosTranslatePlugin(
                Path.of("argos-translate"), ArgosDevice.AUTO, limits,
                process, Duration.ofSeconds(5));

        plugin.translate(REQUEST);

        assertThat(process.environment)
                .containsEntry("ARGOS_INTER_THREADS", "2")
                .containsEntry("ARGOS_BATCH_SIZE", "16")
                .doesNotContainKey("ARGOS_GPU_MEMORY_MIB");
        assertThat(plugin.gpuMemoryBudgetEnforced()).isFalse();
        assertThat(plugin.capabilities().gpuAcceleration()).isTrue();
        assertThat(plugin.capabilities().persistentModel()).isFalse();
    }

    @Test
    void translateLocallyReportsCpuOnlyOneShotCapability() {
        RecordingProcess process = new RecordingProcess(new LocalProcessResult(0, "Hola", ""));
        var plugin = new TranslateLocallyPlugin(
                Path.of("translateLocally"), "en-es-tiny", process, Duration.ofSeconds(5));

        assertThat(plugin.capabilities())
                .isEqualTo(new TranslationProviderCapabilities(false, false, false));
    }

    private static final class RecordingProcess implements LocalTranslationProcess {
        private final LocalProcessResult result;
        private List<String> command;
        private String input;
        private Map<String, String> environment;

        private RecordingProcess(LocalProcessResult result) {
            this.result = result;
        }

        @Override
        public LocalProcessResult execute(
                List<String> command,
                String input,
                Duration timeout,
                Map<String, String> environment) {
            this.command = command;
            this.input = input;
            this.environment = environment;
            return result;
        }
    }

    private static final class SequenceProcess implements LocalTranslationProcess {
        private final java.util.ArrayDeque<LocalProcessResult> results;
        private final java.util.ArrayList<Map<String, String>> environments =
                new java.util.ArrayList<>();

        private SequenceProcess(LocalProcessResult... results) {
            this.results = new java.util.ArrayDeque<>(List.of(results));
        }

        @Override
        public LocalProcessResult execute(
                List<String> command,
                String input,
                Duration timeout,
                Map<String, String> environment) {
            environments.add(Map.copyOf(environment));
            return results.removeFirst();
        }
    }
}
