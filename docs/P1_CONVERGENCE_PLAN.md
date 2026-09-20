# P1 workflow convergence plan

## Boundary

Unify the state-transition contract used by GUI, simple CLI, and Auto without
removing Auto's catalog reuse or bounded sibling-response discovery.

## Implementation checkpoint - 2026-09-19

The project module now owns `WorkflowTransitionContract`, including deterministic
phase, mod-id, protected entry-set, entry-count and untranslated-count guards,
and `SharedTranslationWorkflowService` for create, refresh, export, import and
build orchestration. `TranslationWorkflow` (and therefore GUI/simple CLI) and
Auto both use the service. Auto persists the contract boundary in state schema
2, migrates schema 1 after a successful pass, and rejects persisted binding drift
without changing source or project bytes.

Durable mutation now uses `WorkflowPersistenceService`. The normal workspace
document and Auto project/state pair share a bounded, hash-manifested transaction;
partial publication rolls back in-process or on restart, while a complete
interrupted publication is retained. Normal and Auto injected-failure fixtures
prove previous documents survive. Auto's catalog and response discovery remain
adapters around the shared operations. Native drag/drop and picker acceptance is
still a separate manual gate; do not infer it from service convergence.

## Required shared states

1. Input accepted and source bytes bound.
2. Project refreshed or created.
3. Pending AI response exported.
4. Response identity verified and imported.
5. Complete translated clone published.
6. Failure/cancellation leaves prior source, project, and output intact.

## Migration order

Introduce a project-module transition contract with deterministic guards and
tests. Adapt TranslationWorkflow first, then Auto around its catalog and
response-discovery adapters. Only then verify GUI and simple CLI use the same
contract. Do not delegate Auto directly to TranslationWorkflow before those
adapters exist.
