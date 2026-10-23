package com.ssmt.project;

import com.ssmt.core.CancellationToken;
import com.ssmt.core.BytecodeTextAllowlist;
import com.ssmt.core.exception.SsmtParseException;
import com.ssmt.core.model.ModInfo;
import com.ssmt.patcher.ClassFileInjector;
import com.ssmt.patcher.PatchArtifact;
import com.ssmt.patcher.PatchBuildResult;
import com.ssmt.patcher.PatchBuilder;
import com.ssmt.patcher.PatchBuilderException;
import com.ssmt.patcher.PatchRequest;
import com.ssmt.patcher.StandardFileInjector;
import com.ssmt.patcher.TranslationReplacement;
import com.ssmt.scanner.ModInfoReader;
import com.ssmt.validation.TranslationValidator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Validates replacements and publishes translated copies or overlays. */
final class ProjectBuildService {
    private final ModInfoReader modInfoReader;
    private final TranslationValidator validator;
    private final StandardFileInjector standardInjector;
    private final ClassFileInjector classInjector;
    private final PatchBuilder patchBuilder;

    ProjectBuildService(ModInfoReader modInfoReader, TranslationValidator validator, StandardFileInjector standardInjector, ClassFileInjector classInjector, PatchBuilder patchBuilder) {
        this.modInfoReader = modInfoReader;
        this.validator = validator;
        this.standardInjector = standardInjector;
        this.classInjector = classInjector;
        this.patchBuilder = patchBuilder;
    }

    ProjectBuildResult buildInternal(
            Path sourceRoot,
            Path outputRoot,
            LocalizationProject project,
            CancellationToken cancellation,
            boolean publishSupportArtifacts) throws ProjectException {
        if (cancellation == null) {
            throw new IllegalArgumentException("cancellation must not be null");
        }
        cancellation.throwIfCancellationRequested();
        ModInfo mod;
        try {
            mod = modInfoReader.read(sourceRoot);
        } catch (SsmtParseException exception) {
            throw new ProjectException("Could not read source mod", exception);
        }
        if (!mod.id().equals(project.sourceModId())) {
            throw new ProjectException("Project source mod id does not match selected source");
        }
        if (!publishSupportArtifacts) {
            requireOwnedNormalOutput(outputRoot, mod.id());
        }
        Map<Path, List<TranslationReplacement>> grouped = new LinkedHashMap<>();
        for (ProjectEntry entry : project.entries()) {
            cancellation.throwIfCancellationRequested();
            WorkflowOperation.checkCancellation();
            if (entry.originalText().isBlank()
                    && entry.translatedText().isBlank()) {
                continue;
            }
            validate(entry);
            grouped.computeIfAbsent(entry.sourceFile(), ignored -> new ArrayList<>())
                    .add(new TranslationReplacement(
                            entry.sourceFile(),
                            entry.key(),
                            entry.originalText(),
                            entry.translatedText()));
        }
        List<PatchArtifact> artifacts = new ArrayList<>();
        try {
            if (project.methodDocument() != null) {
                artifacts.addAll(new BridgeForgeProjectAdapter().artifacts(sourceRoot, project));
            }
            for (List<TranslationReplacement> replacements : project.methodDocument() == null
                    ? grouped.values() : List.<List<TranslationReplacement>>of()) {
                cancellation.throwIfCancellationRequested();
                Path relative = replacements.getFirst().sourceFile();
                String lowerCaseName = relative.toString().toLowerCase(java.util.Locale.ROOT);
                if (lowerCaseName.endsWith(".class") || lowerCaseName.endsWith(".jar")) {
                    artifacts.add(classInjector.inject(sourceRoot, replacements));
                } else {
                    artifacts.add(standardInjector.inject(sourceRoot, replacements));
                }
            }
            cancellation.throwIfCancellationRequested();
            if (Files.exists(sourceRoot.resolve(BytecodeTextAllowlist.FILE_NAME))) {
                Map<String, String> emitted = new LinkedHashMap<>();
                for (List<TranslationReplacement> replacements : grouped.values()) {
                    for (TranslationReplacement replacement : replacements) {
                        emitted.put(replacement.sourceFile().toString().replace('\\', '/')
                                + "\t" + replacement.key(), replacement.translatedText());
                    }
                }
                artifacts.add(PatchArtifact.utf8(Path.of(BytecodeTextAllowlist.FILE_NAME),
                        BytecodeTextAllowlist.read(sourceRoot).translatedCatalog(emitted)));
            }
            if (publishSupportArtifacts) {
                artifacts.add(PatchArtifact.utf8(Path.of("Project Go Changes.csv"),
                        new TranslationReportExporter().render(project)));
            }
            PatchRequest request = new PatchRequest(
                    sourceRoot,
                    outputRoot,
                    project.patchId(),
                    project.patchName(),
                    project.sourceModId(),
                    mod.name(),
                    mod.gameVersion(),
                    artifacts);
            if (!publishSupportArtifacts) {
                requireOwnedNormalOutput(outputRoot, mod.id());
            }
            WorkflowOperation.beginPublication();
            PatchBuildResult result = publishSupportArtifacts
                    ? patchBuilder.build(request)
                    : patchBuilder.buildTranslatedCopy(request);
            int supportArtifacts = publishSupportArtifacts ? 1 : 0;
            return new ProjectBuildResult(
                    result.changed(), result.artifactCount() - supportArtifacts);
        } catch (PatchBuilderException | IllegalArgumentException | IOException exception) {
            throw new ProjectException(
                    "Could not build localization project: " + exception.getMessage(),
                    exception);
        }
    }

    void requireOwnedNormalOutput(Path outputRoot, String sourceModId) throws ProjectException {
        Path output = outputRoot.toAbsolutePath().normalize();
        if (!Files.exists(output, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            if (!Files.isDirectory(output, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(output)
                    || !output.toRealPath(java.nio.file.LinkOption.NOFOLLOW_LINKS).equals(output.toRealPath())) {
                throw new ProjectException("The existing output must be a real translated-copy directory");
            }
            ModInfo existing = modInfoReader.read(output);
            if (!existing.id().equals(sourceModId)) {
                throw new ProjectException(
                        "The existing translated-copy folder belongs to a different mod; choose another destination");
            }
            Path fingerprint = output.resolve(".ssmt-build-fingerprint");
            if (!Files.isRegularFile(fingerprint, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(fingerprint) || Files.size(fingerprint) != 64
                    || !Files.readString(fingerprint, StandardCharsets.UTF_8).matches("[0-9a-f]{64}")) {
                throw new ProjectException(
                        "The existing folder is not a Project Go translated copy; choose another destination");
            }
        } catch (IOException | SsmtParseException exception) {
            throw new ProjectException("Could not verify the existing translated-copy destination", exception);
        }
    }

    void validate(ProjectEntry entry) throws ProjectException {
        if (entry.translatedText().isBlank()) {
            throw new ProjectException("Blank translation at " + entry.key());
        }
        if (entry.key().startsWith("bf:")) {
            if (BridgeForgePlaceholders.resolve(entry.originalText(), entry.translatedText()) == null) {
                throw new ProjectException("Invalid protected placeholders at " + entry.key());
            }
            return;
        }
        var issues = validator.validate(entry.originalText(), entry.translatedText());
        if (!issues.isEmpty()) {
            throw new ProjectException(
                    "Invalid translation at " + entry.key() + ": " + issues.getFirst().message());
        }
    }
}
