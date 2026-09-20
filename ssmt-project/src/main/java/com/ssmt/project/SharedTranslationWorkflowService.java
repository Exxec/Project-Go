package com.ssmt.project;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Shared workflow operations used by GUI, simple CLI, and Auto.
 *
 * <p>Entry-point-specific storage, catalog reuse, response discovery, and
 * presentation remain adapters around this service.</p>
 */
public final class SharedTranslationWorkflowService {
    private final LocalizationProjectService projects = new LocalizationProjectService();
    private final AiTranslationExchangeService exchange = new AiTranslationExchangeService();
    private final WorkflowTransitionContract transitions = new WorkflowTransitionContract();
    private final SourceIntegrityGuard sourceIntegrity = new SourceIntegrityGuard();

    /** Prepared project plus its source-bound transition state and optional refresh report. */
    public record Prepared(LocalizationProject project,
            WorkflowTransitionContract.State state,
            Optional<ProjectRefreshResult> refresh,
            List<SourceIntegrityGuard.Attestation> sourceAttestations) {
        public Prepared {
            refresh = refresh == null ? Optional.empty() : refresh;
            sourceAttestations = List.copyOf(sourceAttestations);
        }
    }

    /** Imported project plus its accepted transition state and import counts. */
    public record Imported(AiTranslationImportResult result,
            WorkflowTransitionContract.State state,
            List<SourceIntegrityGuard.Attestation> sourceAttestations) {
        public Imported { sourceAttestations = List.copyOf(sourceAttestations); }
    }

    /** Published clone result plus its terminal transition state. */
    public record Built(ProjectBuildResult result,
            WorkflowTransitionContract.State state,
            List<SourceIntegrityGuard.Attestation> sourceAttestations) {
        public Built { sourceAttestations = List.copyOf(sourceAttestations); }
    }

    /** Creates and binds a new project from accepted source input. */
    public Prepared create(Path source, String patchId, String patchName)
            throws ProjectException {
        var attested = sourceIntegrity.runAttested("CREATE_EXTRACTION", source,
                () -> projects.create(source, patchId, patchName));
        return bind(attested.result(), List.of(attested.attestation()));
    }

    /** Refreshes existing work and binds the resulting candidate. */
    public Prepared refresh(Path source, LocalizationProject previous)
            throws ProjectException {
        var attested = sourceIntegrity.runAttested("REFRESH_EXTRACTION", source,
                () -> projects.refresh(source, previous));
        ProjectRefreshResult refresh = attested.result();
        Prepared prepared = bind(refresh.project());
        return new Prepared(prepared.project(), prepared.state(), Optional.of(refresh),
                List.of(attested.attestation()));
    }

    /** Binds an adapter-modified project, such as one with exact catalog matches. */
    public Prepared bind(LocalizationProject project) throws ProjectException {
        return bind(project, List.of());
    }

    /** Rebinds an adapter-modified project while retaining source attestations. */
    public Prepared bind(LocalizationProject project,
            List<SourceIntegrityGuard.Attestation> attestations) throws ProjectException {
        WorkflowTransitionContract.State accepted =
                transitions.inputAccepted(project.sourceModId());
        return new Prepared(project, transitions.projectReady(accepted, project),
                Optional.empty(), attestations);
    }

    /** Verifies a durable state boundary before an entry point resumes it. */
    public void verifyPersisted(WorkflowTransitionContract.State persisted,
            LocalizationProject project) throws ProjectException {
        transitions.verifyPersisted(persisted, project);
    }

    /** Exports every project entry under the shared transition contract. */
    public WorkflowTransitionContract.State exportAll(Path destination,
            Prepared prepared, String modName, String sourceLanguage,
            String targetLanguage) throws ProjectException {
        WorkflowTransitionContract.State pending =
                transitions.responsePending(prepared.state(), prepared.project());
        exchange.exportPackage(destination, prepared.project(), modName,
                sourceLanguage, targetLanguage);
        return pending;
    }

    /** Exports an adapter-selected subset under the shared transition contract. */
    public WorkflowTransitionContract.State exportSelected(Path destination,
            Prepared prepared, List<ProjectEntry> selected, String modName,
            String sourceLanguage, String targetLanguage) throws ProjectException {
        WorkflowTransitionContract.State pending =
                transitions.responsePending(prepared.state(), prepared.project());
        exchange.exportPackage(destination, prepared.project(), selected, modName,
                sourceLanguage, targetLanguage);
        return pending;
    }

    /** Validates and imports one response without persisting entry-point state. */
    public Imported importResponse(Path source, Path response, Prepared prepared, Path catalog)
            throws ProjectException {
        var attested = sourceIntegrity.runAttested("IMPORT_RESPONSE", source,
                () -> exchange.importResponse(response, prepared.project(), catalog));
        AiTranslationImportResult imported = attested.result();
        WorkflowTransitionContract.State state = transitions.responseImported(
                prepared.state(), prepared.project(), imported.project());
        var attestations = new java.util.ArrayList<>(prepared.sourceAttestations());
        attestations.add(attested.attestation());
        return new Imported(imported, state, attestations);
    }

    /** Authorizes and builds one complete translated clone. */
    public Built build(Path source, Path destination, Prepared prepared)
            throws ProjectException {
        WorkflowTransitionContract.State published =
                transitions.outputPublished(prepared.state(), prepared.project());
        var attested = sourceIntegrity.runAttested("BUILD_CLONE", source,
                () -> projects.buildTranslatedCopy(source, destination, prepared.project()));
        var attestations = new java.util.ArrayList<>(prepared.sourceAttestations());
        attestations.add(attested.attestation());
        return new Built(attested.result(), published, attestations);
    }
}
