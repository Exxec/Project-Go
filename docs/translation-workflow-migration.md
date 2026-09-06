# Shared translation workflow migration

The normal desktop path is **Choose Mod → Export Translation File → Import
Translated File → Build**. It uses `TranslationWorkflow` in `ssmt-project`, also
used by the CLI `translation` command. The previous desktop workflow remains
available under Advanced. The pre-redesign snapshot is unchanged.

## Current implementation (phases 1–5)

- `TranslationWorkflow.loadMod` discovers saved work before creating anything and
  calls `LocalizationProjectService.refresh()` on every load, regardless of the
  mod version. There is no second reconciliation implementation.
- Workspaces default to `%USERPROFILE%/.project-go/workspaces` on Windows (the
  corresponding user-home directory elsewhere). `projectgo.workspace` or the
  CLI's `--workspace` can override this location for isolated testing.
- One workspace per source mod ID and English target is stored under a hashed
  directory. Renaming the mod folder or upgrading Project-Go does not change that
  identity. The simple path currently targets English; the existing advanced
  interchange supports other languages.
- On first load, existing `.ssmt.json` projects in sibling `Project Go - ...`
  directories are discovered and copied into the owned workspace. Originals are
  not modified. Multiple matching legacy projects fail visibly rather than
  selecting one arbitrarily; projects in arbitrary unrelated directories are
  still available through the existing Advanced workflow.
- The active project stays compatible with the portable project schema. An
  additional `workspace` object records workspace version, source association,
  language, review findings, and lightweight history in the same JSON document.
  Removed entries are absent from the active project and remain in history.
  Changed entries retain prior source/translation/provenance as historical data,
  while their active translation is blank.
- A single atomic replacement commits project and workspace metadata together.
  Validation or publication failures do not replace the active GUI session.
  Workspace locking and revision checks reject competing/stale writes. A file
  system without atomic replacement support fails closed.
- Export uses the existing single-file AI exchange overload and unchanged schema.
  Existing translations fill both `translation` and `existingTranslation`.
  Import uses the existing validators without a translation-memory side effect.
- Required build reports are artifacts staged before clone publication. Prior
  output and report roll back together on a handled publication failure. Missing
  artifacts invalidate the build cache. Leftover prior-output recovery directories
  are preserved and block another publication rather than being deleted blindly.

The workspace is authoritative for the normal path. The Advanced editor remains
an independent legacy workflow during migration; edits to a legacy project after
initial adoption are not automatically merged into an already-owned workspace.
Do not open the owned workspace JSON for writing in older application versions:
their portable-project writer does not retain workspace metadata.

## CLI

```text
ssmt translation load <mod-folder>
ssmt translation export <mod-folder> <translation.json>
ssmt translation import <mod-folder> <translated.json>
ssmt translation build <mod-folder> <output-folder>
```

Each invocation loads the mod through the shared facade. No catalog, schema,
provider, GPU, or reconciliation arguments are required.

## Permanent regression gates

- `EdmundFacadeReleaseGateTest`: original 187 translated entries, exact 207-entry
  corpus, automatic legacy discovery, restart, single-file export, import, build.
- Existing `EdmundCorpusWorkflowTest` and `ExistingProjectAiExportWorkflowTest`
  remain unchanged as baseline workflow gates.
- `TranslationWorkflowTest`: refresh with unchanged version; removed history;
  persistent changed-source review; invalid import and injected publication
  failure; stale sessions; corrupt workspace; source/output overlap; returning
  an older response after extraction growth; source-safe builds.
- `TranslationWorkflowControllerTest` and `TranslationCommandTest`: real entry
  point adapters using the same durable facade, including failed GUI import.
- `PatchBuilderTest`: required-report rollback, missing-report rebuild, and
  rejection of source/staging overlap without cleanup deleting source files.

## Identity limitations retained intentionally

CSV identity columns remain independent of row order. JSON array elements still
use positional pointers. JAR/class method strings still use instruction-local
ordinals. The facade tests explicitly reorder JSON arrays and insert bytecode
constants: changed source must remain untranslated rather than inherit stale
text. These tests characterize existing limitations; they do not claim semantic
identity across arbitrary array reorderings or bytecode recompilation. Repeated
identical source strings can remain indistinguishable under positional identity.

## Separate release gates (phases 6–7)

Build currently creates the proven translated clone and pristine-backup sibling.
It is **not a standalone translation overlay**. GUI wording says translated copy.
Incomplete translations prevent publication, leaving previous output intact.

Standalone patch output must demonstrate game-loading behavior for CSV, JSON,
variants, factions, `.ship`, loose classes and JAR translations before becoming
the default. No game validation or claim of standalone-patch support is included
in this migration. The clone path lets normal workflow testing proceed meanwhile.

Deletion of legacy orchestration and controls follows equivalent coverage of the
behaviors still needed from Advanced. They remain available for now, including
custom schemas, recovery tools, providers and translation-memory management.
Power-loss recovery of multi-directory clone publication is not a demonstrated
guarantee; current regression coverage exercises handled failures and preserves
leftover recovery directories after interruption.

## Verified implementation checkpoint — 2026-09-06

`gradlew build :ssmt-cli:installDist :ssmt-gui:installDist --offline --no-daemon
--max-workers=1 --console=plain` passed, including Checkstyle and SpotBugs.
The repository test results contain 383 tests, zero failures and zero errors.
The packaged GUI resource smoke test and CLI `translation --help` also passed.
Evidence: `build/redesign-full-build.log` and module test-result XML files.

Preview GUI and CLI ZIPs are in `releases/*-workflow-preview.zip`, with hashes in
`releases/workflow-preview-sha256.txt`. Native file-picker/mouse automation and
in-game validation were not performed. The pre-redesign baseline is preserved.
