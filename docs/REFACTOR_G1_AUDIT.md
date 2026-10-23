# G1 structural refactor evidence

Checkpoint: 2026-10-22, baseline main `a1d32c6`. Pending owner review and merge.
This implements G1 of BridgeForge's [cross-repository refactor plan](https://github.com/Exxec/Project-BridgeForge/blob/main/docs/REFACTOR_PLAN_2026-10.md).

## Responsibilities

- `SsmtApplication` is a 65-line application shell. `AdvancedWorkspace` owns the per-window state and shared coordination.
- Eight tab builders have separate classes: welcome, translation editor, tools, project info, schema editor,
  provider settings, font coverage and log dashboard. Existing layout, order, labels and callbacks are preserved.
- `WorkspaceProjectActions`, `WorkspaceAiActions` and `WorkspaceMemoryActions` hold the existing advanced-workspace actions.
- `ProjectWorkspaceController` delegates its independent catalog operations to `WorkspaceTranslationMemory`.
- `TranslationWorkflowPane` delegates review/glossary/text dialogs to `WorkflowReviewDialogs`; the checklist's
  package-level entry points, approval-revoke callback and normal-workflow controller are retained.
- `LocalizationProjectService` delegates deterministic document persistence to `ProjectDocumentCodec`, and build
  publication/validation to `ProjectBuildService`, sharing the same service instances.
- `TranslationWorkflow` delegates bounded lineage registration to `WorkflowLineageStore`; its public session and
  lineage-choice types, publisher hook, file formats, logger category and source-safety checks remain unchanged.

These are responsibility extractions. Existing source paths and public methods remain; there are no whole-file renames.
The new helper classes are package-private. Package access is used only where extracted classes need the same per-window
state or existing callbacks; no public API is added. Preferences still use `SsmtApplication.class` as their namespace.

## Evidence

| Check | Before | After |
|---|---:|---:|
| Full `gradlew build` JUnit total | 765 | 765 |
| Failures / errors | 0 / 0 | 0 / 0 |
| Existing default-test skips | 2 | 2 |
| GUI checklist rows | 16 | 16 |
| Program-asserted checklist rows | 11 PASS | 11 PASS |
| Checklist rows needing a person | 5 | 5 |

All 16 generated checklist row objects compare equal, including their expected behavior, program observations,
owner statuses and screenshot names. No owner/visual gate was upgraded. Existing review-inbox tests run in the full build
and are unchanged. The post-refactor application was also opened on the real JavaFX toolkit with disposable application data:
2 modes, 3 navigation tabs, 7 tool tabs, unchanged title and the inbox in its original position.

Checkstyle, SpotBugs, `-Xlint:all -Werror`, Windows launcher verification and native Auto Unicode verification pass.
The CLI golden file is byte-identical; SHA-256:
`7cf541cf2c7e224240b3bcf10a5a59484744c3cc1b6b1d556136d02a3b87695e`.

A source comparison verifies all 58 moved method bodies preserve code/literal/comment tokens after removing only owner
qualifications; their control flow and algorithms are unchanged. Public source signatures in all five named G1 classes
compare equal to their original source, including nested public types. Original snapshots and comparison receipts are
kept in the ignored `.local/` validation directory. The AST-only knowledge graph is refreshed with `graphify update .`.

This does not change translation identity, approval policy, network opt-in, source-writing rules or evidence gates.
No real mod, game, save or release was modified. LIVE TEST NOT PERFORMED. Hosted CI is recorded in the PR.
