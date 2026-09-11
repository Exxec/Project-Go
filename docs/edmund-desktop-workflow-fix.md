# Edmund Church desktop workflow regression

The destructive route is repeat use of **Start New Translation** (or a schema-start
variant), followed by **Export for Online AI**. The source tree does not contain a
historical click log, so the exact past click sequence cannot be recovered; the route
below reproduces the reported output using the actual desktop workflow.

Before the fix:

1. `SsmtApplication.startTab` binds the Start button to `createProject`.
2. `createProject` computes `Project Go - <mod name>/<mod name> project.ssmt.json`
   using `artifactDirectory` and `artifactBaseName`. Selecting the same mod selects
   the same destination. Its background action calls `workspace.create`.
3. `ProjectWorkspaceController.create` called `ProjectWorkflow.create`, whose real
   `ServiceWorkflow` calls `LocalizationProjectService.create`/`createWithSchemas`.
   It never read the destination project or retained the active translated project.
4. `workflow.write` overwrote the saved project with fresh extraction, and
   `setWorkspace` replaced the in-memory project and editor rows.
5. The Export button calls `SsmtApplication.exportAiPackage`, then
   `ProjectWorkspaceController.exportAiPackage`, then `AiTranslationExchangeService`.
   Export does not re-extract: it exports the replacement project from step 4.
6. Independently, AI export wrote `translation: ""` for every entry, retaining any
   existing translation only in `existingTranslation`. Even a correct explicit
   Refresh therefore produced blank response slots.

The explicit **Refresh Project** path is different: `refreshProject` →
`runRefreshTask(workspace::previewRefresh)` → `ProjectWorkflow.refresh` →
`LocalizationProjectService.refresh`. On acceptance, `applyRefresh` saves and loads
the returned project. Its unchanged-entry reconciliation already preserves the
translation and provenance, and that matching behavior was not changed.

After the fix, the controller routes an existing destination to the existing refresh
service, reading the saved project or applying the active editor's unsaved edits.
Only a genuinely new destination calls create. A schema-aware refresh delegates to
the same reconciliation body with the requested extraction configuration. Source-mod
identity validation occurs before writing. Existing project patch identity/name are
preserved. Export prepopulates `translation` as well as `existingTranslation`, and
the prompt requests filling only blank translations.

`ExistingProjectAiExportWorkflowTest` exercises the real GUI controller, real service
adapter, editor, project files, and AI export, using the same GUI destination helpers.
It creates and translates 187 entries, adds 20 `.ship` names, repeats the Start action,
and verifies 207 saved/editor/export entries with exactly 187 translations preserved
and 20 blank entries. It also covers an unsaved active-editor change, the explicit
Refresh path, and adding 20 entries through the Start-with-CSV path.

Before the fix the two Start-route cases reproduced 0 preserved translations; the
explicit Refresh case preserved the project but failed the export assertion. These
are workflow regression tests, not mocked reconciliation tests or a second matching
implementation. They invoke the same controller methods as the GUI after the file
chooser and prompt inputs; they do not automate native file choosers or mouse clicks.

## Verification against the reported files

The September 5 export in the Edmund mod's `Project Go - Edmund Church[a16709513_wkt]`
folder has SHA-256 `7D47C214A567D0B096F614F4946C5BA1992A132EC41380ED4719B91E24DA2C67`.
It contains 207 entries and zero nonblank `existingTranslation` values. The adjacent
saved project also contains 207 blank translations, locating the loss before export.

The earlier response was found at
`C:\Users\exxec\Downloads\Edmund Church - English import-ready.json`.
All 187 entries contain translations and match the new export by stable ID **and
exact source text**. No changed-source exceptions exist in this actual pair.

`EdmundCorpusWorkflowTest` uses an archived fixture containing that exact export,
that exact response, and only the original mod files necessary for extraction.
It reconstructs the old 187-entry project from the response's original fields,
imports the response through the desktop controller, closes the controller, and
invokes the same Start action against the existing destination. The real service
extracts 207 entries and refreshes the old project; the real AI exporter preserves
all 187 translations and leaves precisely 20 entries blank. A second test changes
one source, verifies CHANGED in the refresh report, repeats Start, and verifies
186 preserved translations plus 21 blanks (20 new and one changed).

Start now retains the applied refresh report rather than dropping it when taking
`result.project()`. The GUI lists changed-source IDs for review and makes previous
translations available as suggestions without applying them to the new source.

Recovered artifacts were written to the ignored local
`releases/Edmund-Recovered` directory during the investigation and may be absent
after cleanup. Recovery used the same controller import, refresh, apply, and
export operations; no extraction behavior or reconciliation matching rules were
changed. The blank original files were retained at the time of recovery.
The code proves the repeat-Start loss route; no historical click log establishes
which actions actually produced the supplied files.
