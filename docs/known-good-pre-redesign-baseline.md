# Known-good pre-redesign baseline

Recorded at the user's request on 2026-09-05, before any redesign.

This baseline is the current working tree, including staged and unstaged changes,
based on Git commit `e8259dca483765633f79a60f722376b604fc6cfc`. That commit alone
does **not** contain the baseline fixes. The source snapshot and SHA-256 manifest
are stored in `releases/known-good-pre-redesign-baseline/`. Recording this baseline
does not alter the Git index or create a commit.

## Behavior to preserve

- Extraction covers descriptions, JAR strings, ship data, variants, factions,
  JSON, and `.ship` fields. Ship identifiers provide translation context.
- Desktop Start New Translation against an existing project destination routes
  through `ProjectWorkspaceController` and the workflow to the existing
  `LocalizationProjectService.refresh()` reconciliation. It preserves matching
  translations, including unsaved edits when continuing the active destination.
- AI export receives the resulting project and preserves translations in both
  `translation` and `existingTranslation`.
- Changed-source entries do not inherit stale text. The refresh report identifies
  them for review; the Start workflow retains that report and exposes suggestions.
- The actual Edmund corpus produces **207 entries, 187 preserved translations,
  and 20 genuinely new blank entries**. The older response matches all 187 by
  stable ID and exact source text. The changed-source regression produces 186
  preserved translations and 21 blanks, with one CHANGED finding and 20 ADDED.
- Preserve the reviewed Void-Tec r13 and SSMT fixes already in this working tree,
  including CSV coverage, bytecode allowlist protections, and highlight validation.

The full desktop trace and corpus provenance are documented in
[edmund-desktop-workflow-fix.md](edmund-desktop-workflow-fix.md).

## Verification at baseline

- All 39 GUI tests passed, including `EdmundCorpusWorkflowTest` and
  `ExistingProjectAiExportWorkflowTest`.
- `:ssmt-gui:test :ssmt-gui:installDist :ssmt-gui:distZip` completed successfully
  with offline dependencies and one worker. Evidence: `build/edmund-corpus-final.log`.
- Earlier full build and CLI/GUI install distributions passed; evidence:
  `build/voidtec-r13/ssmt-final-build.log`.
- Recovered project and export were independently counted: 207 total, 187
  translated, 20 blank. Original Downloads evidence files were left intact.
- `git diff --check` passed. Native GUI mouse/file-chooser automation and in-game
  Starsector testing were not performed.

## Reference artifacts

- `releases/Edmund-Recovered/Edmund Church recovered project.ssmt.json`
- `releases/Edmund-Recovered/Edmund Church recovered words.json`
- `releases/ssmt-gui-0.6.0-edmund-fix.zip` (latest desktop package)
- `releases/Void-Tec-0.98a-revival-r13.zip`
- `ssmt-gui/src/test/resources/edmund/workflow.zip` (exact regression corpus)

The baseline directory includes a source ZIP, captured Git status/diffs, copied
verification logs and GUI test result XML, a source-file hash manifest, and an
artifact hash manifest. The source ZIP contains tracked and nonignored untracked
working files, excluding release outputs and the baseline directory itself.
Build caches and installed dependencies are not part of the source snapshot.

Any redesign should retain these regression tests and reproduce these invariants
before it is considered a replacement for this baseline.
