package com.ssmt.ai;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Offline Argos Translate CLI adapter. Models must be installed by the user. */
public final class ArgosTranslatePlugin implements ResourceAwareTranslationProvider {
    private final Path executable;
    private final LocalTranslationProcess process;
    private final Duration timeout;
    private final ArgosDevice requestedDevice;
    private final TranslationResourceLimits limits;
    private boolean accelerationUnavailable;
    private ArgosExecutionStatus lastExecution;

    public ArgosTranslatePlugin(Path executable) {
        this(executable, ArgosDevice.CPU);
    }

    public ArgosTranslatePlugin(Path executable, ArgosDevice device) {
        this(executable, device, TranslationResourceLimits.defaults());
    }

    public ArgosTranslatePlugin(
            Path executable,
            ArgosDevice device,
            TranslationResourceLimits limits) {
        this(executable, device, limits,
                new BoundedLocalTranslationProcess(), Duration.ofMinutes(2));
    }

    ArgosTranslatePlugin(Path executable, LocalTranslationProcess process, Duration timeout) {
        this(executable, ArgosDevice.CPU, process, timeout);
    }

    ArgosTranslatePlugin(
            Path executable,
            ArgosDevice device,
            LocalTranslationProcess process,
            Duration timeout) {
        this(executable, device, TranslationResourceLimits.defaults(), process, timeout);
    }

    ArgosTranslatePlugin(
            Path executable,
            ArgosDevice device,
            TranslationResourceLimits limits,
            LocalTranslationProcess process,
            Duration timeout) {
        this.executable = Objects.requireNonNull(executable, "executable");
        this.requestedDevice = Objects.requireNonNull(device, "device");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.process = Objects.requireNonNull(process, "process");
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.timeout = timeout;
    }

    @Override
    public synchronized String translate(AiTranslationRequest request) throws AiProviderException {
        List<String> command = List.of(executable.toString(), "--from-lang",
                request.sourceLanguage(), "--to-lang", request.targetLanguage());
        ArgosDevice firstDevice = accelerationUnavailable || requestedDevice == ArgosDevice.CPU
                ? ArgosDevice.CPU
                : ArgosDevice.CUDA;
        try {
            String translated = execute(command, request.sourceText(), firstDevice);
            lastExecution = new ArgosExecutionStatus(
                    requestedDevice, backend(firstDevice), "");
            return translated;
        } catch (AiProviderException acceleratedFailure) {
            if (firstDevice == ArgosDevice.CPU) {
                lastExecution = new ArgosExecutionStatus(
                        requestedDevice, TranslationBackend.CPU, "");
                throw acceleratedFailure;
            }
            accelerationUnavailable = true;
            String reason = acceleratedFailure.getMessage();
            try {
                String translated = execute(command, request.sourceText(), ArgosDevice.CPU);
                lastExecution = new ArgosExecutionStatus(
                        requestedDevice, TranslationBackend.CPU, reason);
                return translated;
            } catch (AiProviderException cpuFailure) {
                lastExecution = new ArgosExecutionStatus(
                        requestedDevice, TranslationBackend.CPU, reason);
                throw new AiProviderException(
                        "Argos accelerated attempt failed (" + reason
                                + "); CPU retry failed (" + cpuFailure.getMessage() + ")",
                        cpuFailure);
            }
        }
    }

    /** Returns device/fallback diagnostics for the last completed attempt. */
    public synchronized ArgosExecutionStatus lastExecution() {
        if (lastExecution == null) {
            return new ArgosExecutionStatus(
                    requestedDevice, TranslationBackend.CPU, "not run");
        }
        return lastExecution;
    }

    /** Argos/CTranslate2 does not expose a supported hard GPU-memory cap. */
    public boolean gpuMemoryBudgetEnforced() {
        return false;
    }

    public TranslationResourceLimits resourceLimits() {
        return limits;
    }

    @Override
    public TranslationProviderCapabilities capabilities() {
        return new TranslationProviderCapabilities(true, false, false);
    }

    @Override
    public ProviderGenerationMetadata attribution(AiTranslationRequest request) {
        return new ProviderGenerationMetadata(
                "argos-translate",
                request.sourceLanguage() + "-" + request.targetLanguage(),
                "",
                Instant.now(),
                false);
    }

    private String execute(List<String> command, String input, ArgosDevice device)
            throws AiProviderException {
        try {
            LocalProcessResult result = process.execute(
                    command, input, timeout,
                    Map.of(
                            "ARGOS_DEVICE_TYPE", device.environmentValue(),
                            "ARGOS_INTER_THREADS",
                            Integer.toString(limits.maximumWorkerThreads()),
                            "ARGOS_BATCH_SIZE", Integer.toString(limits.maximumBatchSize())));
            if (result.exitCode() != 0) {
                throw new AiProviderException("Argos Translate exited with exit code "
                        + result.exitCode()
                        + ": " + result.standardError().strip());
            }
            return result.standardOutput().strip();
        } catch (IOException exception) {
            throw new AiProviderException("Could not execute Argos Translate", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("Argos Translate was interrupted", exception);
        }
    }

    private static TranslationBackend backend(ArgosDevice device) {
        return device == ArgosDevice.CUDA ? TranslationBackend.CUDA : TranslationBackend.CPU;
    }
}
