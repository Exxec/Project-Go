package com.ssmt.cli;

import com.ssmt.ai.AiProviderLocation;
import com.ssmt.ai.AiProviderSettings;
import com.ssmt.ai.AiProviderType;
import com.ssmt.ai.ArgosDevice;
import com.ssmt.ai.ArgosTranslatePlugin;
import com.ssmt.ai.OfflineTranslationChain;
import com.ssmt.ai.TranslateLocallyPlugin;
import com.ssmt.ai.TranslationMode;
import com.ssmt.ai.TranslationResourceLimits;
import com.ssmt.project.ChainedProjectTranslationEngine;
import com.ssmt.project.LocalizationProjectService;
import com.ssmt.project.PreferredLocalProvider;
import com.ssmt.project.ProjectTranslationCoordinator;
import com.ssmt.project.ProjectTranslationSettings;
import com.ssmt.project.TranslationMemoryApprovedGlossary;
import com.ssmt.project.TranslationMetadataInterchangeService;
import com.ssmt.tm.SqliteTranslationMemory;
import java.net.URI;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

/** Translates blank entries through the bounded project-level provider router. */
@Command(name = "translate-project", mixinStandardHelpOptions = true,
        description = "Create unapproved drafts for blank entries in a project.")
public final class ProjectTranslateCommand implements Callable<Integer> {
    @Spec private CommandSpec spec;
    @Parameters(index = "0") private Path projectFile;
    @Option(names = "--memory", required = true) private Path memory;
    @Option(names = "--source-language", defaultValue = "zh") private String sourceLanguage;
    @Option(names = "--target-language", defaultValue = "en") private String targetLanguage;
    @Option(names = "--mode", defaultValue = "SMART_DEFAULT") private TranslationMode mode;
    @Option(names = "--preferred-local", defaultValue = "ARGOS")
    private PreferredLocalProvider preferredLocal;
    @Option(names = "--argos", defaultValue = "argos-translate") private Path argos;
    @Option(names = "--argos-device", defaultValue = "CPU") private ArgosDevice argosDevice;
    @Option(names = "--translate-locally", defaultValue = "translateLocally")
    private Path translateLocally;
    @Option(names = "--translate-locally-model",
            defaultValue = TranslateLocallyPlugin.DEFAULT_ZH_EN_MODEL)
    private String translateLocallyModel;
    @Option(names = "--maximum-worker-threads", defaultValue = "1") private int workers;
    @Option(names = "--maximum-batch-size", defaultValue = "32") private int batchSize;
    @Option(names = "--maximum-gpu-memory-mib") private Long gpuMemory;
    @Option(names = "--terminology", defaultValue = "") private String terminology;
    @Option(names = "--style", defaultValue = "") private String style;
    @Option(names = "--discard-lineage") private boolean discardLineage;
    @Option(names = "--ai-type") private Optional<AiProviderType> aiType = Optional.empty();
    @Option(names = "--ai-base-url") private Optional<URI> aiBaseUrl = Optional.empty();
    @Option(names = "--ai-model") private Optional<String> aiModel = Optional.empty();
    @Option(names = "--ai-credential-env")
    private Optional<String> aiCredentialEnvironment = Optional.empty();
    @Option(names = "--allow-remote-ai",
            description = "Consent to send routed text to the configured remote provider.")
    private boolean allowRemoteAi;

    @Override
    public Integer call() {
        try (SqliteTranslationMemory translationMemory = SqliteTranslationMemory.open(memory)) {
            LocalizationProjectService projects = new LocalizationProjectService();
            var project = projects.read(projectFile);
            ProjectTranslationSettings settings = new ProjectTranslationSettings(
                    sourceLanguage, targetLanguage, mode, preferredLocal, workers, batchSize,
                    gpuMemory == null ? OptionalLong.empty() : OptionalLong.of(gpuMemory),
                    terminology, style, !discardLineage, allowRemoteAi);
            var argosProvider = new ArgosTranslatePlugin(
                    argos, argosDevice,
                    new TranslationResourceLimits(workers, batchSize,
                            settings.maximumGpuMemoryMiB()));
            var locallyProvider = new TranslateLocallyPlugin(
                    translateLocally, translateLocallyModel);
            OfflineTranslationChain offline = new OfflineTranslationChain(
                    new TranslationMemoryApprovedGlossary(translationMemory),
                    argosProvider, locallyProvider,
                    preferredLocal == PreferredLocalProvider.TRANSLATE_LOCALLY);
            Optional<com.ssmt.ai.AiTranslationProvider> finalProvider = createAiProvider();
            AiProviderLocation location = aiType.orElse(AiProviderType.OLLAMA)
                    == AiProviderType.OLLAMA ? AiProviderLocation.LOCAL : AiProviderLocation.REMOTE;
            var engine = new ChainedProjectTranslationEngine(
                    offline, settings, finalProvider, location);
            var result = new ProjectTranslationCoordinator().translate(
                    project, settings, Map.of(), engine, com.ssmt.core.CancellationToken.NONE);
            projects.write(projectFile, result.project());
            if (!result.retainedMetadata().isEmpty()) {
                Path base = projectFile.toAbsolutePath().normalize();
                new TranslationMetadataInterchangeService().writeJson(
                        base.resolveSibling(base.getFileName() + ".generation.json"),
                        result.retainedMetadata());
            }
            spec.commandLine().getOut().println("translated=" + result.translatedEntries()
                    + "; preserved=" + result.preservedEntries()
                    + "; unresolved=" + result.unresolvedEntries()
                    + "; backends=" + String.join(",", result.backendsUsed()));
            return 0;
        } catch (com.ssmt.project.ProjectException
                | com.ssmt.tm.TranslationMemoryException
                | IllegalArgumentException exception) {
            spec.commandLine().getErr().println(
                    CliDiagnostics.explain("Project translation", exception));
            return 1;
        }
    }

    private Optional<com.ssmt.ai.AiTranslationProvider> createAiProvider() {
        if (aiType.isEmpty() && aiBaseUrl.isEmpty() && aiModel.isEmpty()) {
            return Optional.empty();
        }
        if (aiType.isEmpty() || aiBaseUrl.isEmpty() || aiModel.isEmpty()) {
            throw new IllegalArgumentException(
                    "AI type, base URL, and model must be configured together");
        }
        AiProviderSettings settings = new AiProviderSettings(
                aiType.orElseThrow(), aiBaseUrl.orElseThrow(), aiModel.orElseThrow(),
                aiCredentialEnvironment);
        return Optional.of(settings.create(System::getenv));
    }
}
