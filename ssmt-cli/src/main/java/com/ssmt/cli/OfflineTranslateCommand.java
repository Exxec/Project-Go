package com.ssmt.cli;

import com.ssmt.ai.AiProviderException;
import com.ssmt.ai.AiTranslationRequest;
import com.ssmt.ai.ArgosTranslatePlugin;
import com.ssmt.ai.ArgosDevice;
import com.ssmt.ai.OfflineTranslationChain;
import com.ssmt.ai.OfflineTranslationDraft;
import com.ssmt.ai.TranslateLocallyPlugin;
import com.ssmt.ai.TranslationResourceLimits;
import com.ssmt.project.TranslationMemoryApprovedGlossary;
import com.ssmt.tm.SqliteTranslationMemory;
import com.ssmt.tm.TranslationMemoryException;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.OptionalLong;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

/** Runs the glossary-first offline translation chain for one string. */
@Command(name = "offline-translate", mixinStandardHelpOptions = true,
        description = "Draft one translation using glossary, Argos, then TranslateLocally.")
public final class OfflineTranslateCommand implements Callable<Integer> {
    @Spec private CommandSpec spec;

    @Parameters(index = "0", description = "Source text.")
    private String sourceText;

    @Option(names = "--source-language", required = true)
    private String sourceLanguage;

    @Option(names = "--target-language", required = true)
    private String targetLanguage;

    @Option(names = "--memory", required = true, description = "Approved glossary/TM database.")
    private Path memoryPath;

    @Option(names = "--argos", defaultValue = "argos-translate",
            description = "Argos Translate executable path.")
    private Path argosExecutable;

    @Option(names = "--argos-device", defaultValue = "CPU",
            description = "Argos device: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    private ArgosDevice argosDevice;

    @Option(names = "--maximum-worker-threads", defaultValue = "1",
            description = "Maximum Argos/CTranslate2 workers (default: ${DEFAULT-VALUE}).")
    private int maximumWorkerThreads;

    @Option(names = "--maximum-batch-size", defaultValue = "32",
            description = "Maximum Argos sentence batch size (default: ${DEFAULT-VALUE}).")
    private int maximumBatchSize;

    @Option(names = "--maximum-gpu-memory-mib",
            description = "Requested GPU-memory budget in MiB, when a provider supports it.")
    private Long maximumGpuMemoryMiB;

    @Option(names = "--translate-locally", defaultValue = "translateLocally",
            description = "TranslateLocally executable path.")
    private Path translateLocallyExecutable;

    @Option(names = "--translate-locally-model",
            defaultValue = TranslateLocallyPlugin.DEFAULT_ZH_EN_MODEL,
            description = "Installed TranslateLocally model ID (default: ${DEFAULT-VALUE}).")
    private String translateLocallyModel;

    @Option(names = "--context", defaultValue = "")
    private String context;

    @Option(names = "--approve",
            description = "Explicitly approve the result and feed it into the glossary/TM.")
    private boolean approve;

    @Override
    public Integer call() {
        try (SqliteTranslationMemory memory = SqliteTranslationMemory.open(memoryPath)) {
            ArgosTranslatePlugin argos = new ArgosTranslatePlugin(
                    argosExecutable,
                    argosDevice,
                    new TranslationResourceLimits(
                            maximumWorkerThreads,
                            maximumBatchSize,
                            maximumGpuMemoryMiB == null
                                    ? OptionalLong.empty()
                                    : OptionalLong.of(maximumGpuMemoryMiB)));
            if (maximumGpuMemoryMiB != null && !argos.gpuMemoryBudgetEnforced()) {
                spec.commandLine().getErr().println(
                        "warning: Argos/CTranslate2 does not expose a supported hard GPU-memory "
                                + "budget; the requested limit is recorded but not enforced");
            }
            OfflineTranslationChain chain = new OfflineTranslationChain(
                    new TranslationMemoryApprovedGlossary(memory),
                    argos,
                    new TranslateLocallyPlugin(
                            translateLocallyExecutable, translateLocallyModel));
            OfflineTranslationDraft draft = chain.translate(new AiTranslationRequest(
                    sourceText, sourceLanguage, targetLanguage, context, ""));
            spec.commandLine().getOut().println(draft.translatedText());
            spec.commandLine().getErr().println("provider=" + draft.origin()
                    + "; confidence=" + draft.assessment().confidence()
                    + (draft.requiresReview() ? "; review-required" : "; approved-glossary"));
            for (String reason : draft.assessment().reasons()) {
                spec.commandLine().getErr().println("confidence-reason=" + reason);
            }
            if (draft.origin() != com.ssmt.ai.OfflineTranslationOrigin.APPROVED_GLOSSARY) {
                var device = argos.lastExecution();
                spec.commandLine().getErr().println(
                        "argos-device-requested=" + device.requestedDevice()
                                + "; backend-used=" + device.usedBackend()
                                + (device.fallbackReason().isBlank()
                                        ? ""
                                        : "; fallback=" + device.fallbackReason()));
            }
            if (approve && draft.requiresReview()) {
                chain.approve(draft);
                spec.commandLine().getErr().println(
                        "approved and saved to glossary/translation memory");
            }
            return 0;
        } catch (TranslationMemoryException | AiProviderException | IllegalArgumentException exception) {
            spec.commandLine().getErr().println(
                    CliDiagnostics.explain("Offline translation", exception));
            return 1;
        }
    }
}
