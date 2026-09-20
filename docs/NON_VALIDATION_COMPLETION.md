# Project Go non-validation completion checkpoint

## Scope and completion contract

Complete remaining product implementation across roadmap P0–P6. Automated
regression, static, build and package checks remain implementation requirements.
Defer hands-on desktop and live Starsector scenarios to the checklist below.
Do not claim runtime compatibility or full-release readiness from offline checks.
Do not change original mods, shared BridgeForge rigs or existing campaign saves.

## Current phased roadmap checkpoint - 2026-09-20

Working source after `54fbce7` now closes the automated implementation boundary
for P1 and P2. P3 has complete outer-file and nested-JAR handling inventories,
review-only CSV/JSON gap detection, and explicit user opt-in for generated schemas.
P4 has independent translated-clone and directory/ZIP audits, clone-only supported
publication, durable source-manifest attestations around shared create, refresh,
response-import, and build operations, and candidate-bound build evidence.
Assurance BUILD `PASS` or `FAIL` rejects a
generic log and requires the exact JDK, argv, input, classpath, output, exit-state,
and authority record. P5/P6 have independent runtime gates, bounded runtime capture,
strict hash-bound ledgers, derived status, and escalation-preserving completion.

Authoritative local verification used initially absent
`.local/checks/roadmap-phases-final-9b8e580f30bf4cc7972fcc915876edcd` as
`LOCALAPPDATA`. The offline full check and CLI/GUI/Auto `installDist` rebuild passed
564 tests with zero failures, errors, or skips; all 102 Gradle tasks executed.
Checkstyle and SpotBugs gates passed with the existing native-access, Gradle 10,
and missing-analysis-class warnings still visible. The rebuilt CLI launcher reported
`SSMT 0.8.0-rc.1`; rebuilt GUI and Auto launchers passed `--smoke-test`. These are working-tree results;
the rebuilt version metadata still describes committed source, so it is not
publishable exact-source evidence.

The remaining unchecked roadmap items are execution gates rather than missing
general-purpose automation: fixture-first work applies when a future ecosystem
format is accepted; final package binding and surprise classification require the
actual attempt bytes; and runtime scenarios require the game. Native UI, campaign,
combat, save/reload, upgrade,
rights review, exact-commit CI, tag, hashes, and release publication remain manual or
external gates and must not inherit success from this offline run.

## Verified starting state — 2026-09-13

## Current P1 transition checkpoint - 2026-09-19

Working source after `54fbce7` adds the shared source/entry-bound transition
contract and create/refresh/export/import/build service to `TranslationWorkflow`
and Auto. Auto state schema 2 persists its phase and binding, migrates schema 1
workspaces, and rejects binding drift before changing source or project bytes.
Auto also defers project persistence until response validation and its next
external operation succeed; a rejected response after refresh retains the prior
project bytes. Shared hash-manifested persistence now commits Auto project/state
as a pair and the normal workspace through the same recovery rules; injected
failure and simulated process-exit fixtures cover rollback/roll-forward decisions.
The initially absent fresh-profile full check plus CLI/GUI/Auto
`installDist` rebuild executed all 102 tasks and passed 547 tests with zero failures, errors,
or skips. This is uncommitted local evidence;
rebuilt manifests still identify committed source `54fbce7` and version
`0.8.0-rc.1`, so they are not publishable source-matching artifacts.

P1 automated convergence is implemented. Native UI and live-game validation,
exact-commit CI, tag, checksums, and release publication remain separate gates.

## Current local release-preparation checkpoint - 2026-09-14

Local main `cdc03016f67e522148d992fac23f01780c9bc761` rebuilt every module and
all current CLI, GUI and Auto distributions with:

    gradlew.bat check :ssmt-cli:installDist :ssmt-gui:installDist :ssmt-auto:installDist --rerun-tasks --no-build-cache --offline --console=plain

The command completed successfully. Rebuilt installation trees are under
`ssmt-cli/build/install/ssmt-cli`, `ssmt-gui/build/install/ssmt-gui`, and
`ssmt-auto/build/install/ssmt-auto`; disposable test and static reports remain
under each module's `build/` directory. Gradle emitted known native-access and
static-analysis missing-class warnings. No warning-free, CI, tag, asset-hash, or
published-release claim follows from this local run.

Remote `origin/main` remains `17c41a5`; local commits `4a6427c`, `5f89f2d`,
`a592839`, `a0d093c`, and `cdc0301` are unpushed. P1 Auto/shared state-machine
convergence and other roadmap items remain implementation work. Native UI,
campaign, combat, save/reload, upgrade, rights, and semantic evidence review
remain deferred validation/release gates.

The same local checkpoint passed `generateSbom`, `releaseChecksums`,
`scanReleaseArchives`, and `checkReleaseMetadata` offline. The generated SBOM is
`build/reports/ssmt-sbom.cdx.json`; checksums and distributions are disposable
local build outputs. A tag-triggered workflow must regenerate and upload them
from the exact pushed source commit before their hashes can support a release.

## Current code-verification checkpoint - 2026-09-14

Local main `6a099e3` adds `ssmt runtime-evidence`: a candidate-bound, portable
JSON capture for the observed Starsector build, enabled-mod versions, load order,
JVM, process exit code, log hashes, and modal dialogs. It validates bounded,
contained log evidence and the candidate binding only; it does not interpret a
process exit or log as campaign, combat, persistence, upgrade, or release proof.

`gradlew.bat check --offline --console=plain` passed after this change (93 tasks).
The earlier release-preparation distributions were built at `cdc0301`, not this
new head, so a fresh full install-distribution rebuild remains required before a
release checkpoint can cover `6a099e3`. Remote `origin/main` remains deliberately
at `17c41a5`; local commits from `4a6427c` through `6a099e3` are unpushed.

## Verified starting state - 2026-09-13

Main HEAD: e32f1f6949ebf4a262082b110e8f0b57643a7d0a. Reviewed live fixes remain
uncommitted. See LIVE_VALIDATION_0.8.0.md for the 473-test clean-check evidence,
native development build identity and completed proper-home relocation.

Roadmap reconciliation: explicit legacy-project adoption is already implemented
in TranslationWorkflow.adoptLegacy, TranslationCommand --adopt-legacy and
TranslationWorkflowPane.showLegacyChoice. TranslationWorkflowTest covers
byte-preserved old files, multiple candidates, existing internal state and stale
hash refusal; TranslationWorkflowControllerTest covers explicit GUI choice.
AutoWorkflow still implements its own state/project/catalog transitions and does
not call TranslationWorkflow. Therefore full cross-entry-point convergence is
not complete; do not close P1 or implement a duplicate adoption mechanism.

## Remaining implementation phases

### Combined source checkpoint checks — 2026-09-13

All live fixes and roadmap foundations pass the complete suite: 490 tests,
zero failures/errors/skips; all module Checkstyle and SpotBugs tasks succeed.
CLI/GUI/Auto installDist rebuilt. Full command from the main checkout:

    gradlew.bat check :ssmt-cli:installDist :ssmt-gui:installDist :ssmt-auto:installDist --rerun-tasks --no-build-cache --offline --console=plain

LOCALAPPDATA began absent at .local/checks/roadmap-fresh-20260913-1611.
BUILD SUCCESSFUL in 1m 14s, 102 tasks all executed. Reports are local under each
module's build/test-results/test and build/reports. Gradle emitted native-access
and future Gradle 10 deprecation warnings; static analysis also printed a missing
SsmtParseException analysis-class warning. Build success is not a claim those
toolchain warnings are resolved. Rebuilt distributions still use the current
configured version; they are not a newly published immutable release.

Next save a source checkpoint commit/push; then continue the open P1–P6 phases
below. Final-version exact-commit CI/artifact verification and all deferred user
validation remain gates. No broad roadmap-completion claim from 490 tests.

### P5/P6 evidence model tranche — 2026-09-13

AssuranceSummary introduces independent source review/authority, build/static,
dependency/API, package identity, redistribution rights, boot, campaign, combat,
save/reload and upgrade gates. Each record binds a named scenario to exact
candidate SHA-256; PASS/FAIL requires an evidence reference, REVIEW_REQUIRED and
NOT_APPLICABLE require written reasons, and rights cannot be waived as N/A.
Summary rejects missing, repeated/contradictory and foreign-candidate gates and
orders records deterministically. Failed results derive FAILED; unresolved
non-runtime gates derive ESCALATION_REQUIRED; pending runtime gates derive
READY_FOR_LIVE_TEST; only a complete recorded passing/applicability ledger derives
READY. This model validates declarations, not referenced files or real behavior;
its READY is not independent release/runtime proof or permission to publish.

Four repository regressions cover build/live separation, complete/unique/candidate
binding, unresolved authority and unsupported PASS/rights waiver. Project tests,
Checkstyle main/test and SpotBugs main PASS: BUILD SUCCESSFUL in 13s, 21 tasks
(6 executed). Local reports: ssmt-project/build/test-results/test and build/reports;
regression source is durable. Current changes remain uncommitted on e32f1f6.

This historical remaining-integration list is superseded by the current 2026-09-19
checkpoint at the top of this file. Tooling now covers the bounded ledger, runtime
capture, final-byte binding, and feedback records; actual scenario evidence and
per-attempt fixtures remain required.

Deferred validation addition: on the final tooling version record boot/campaign/
combat/save/upgrade scenarios separately against the actual candidate hash, leave
unobserved names NOT_TESTED, and confirm missing/contradictory evidence cannot
produce release readiness. Verify the referenced logs, not only their filenames.

### P1 source freshness tranche — 2026-09-13

AutoWorkflow now refreshes existing entries on every pass, using the same
LocalizationProjectService.refresh operation as the normal shared workflow.
Previously an unchanged declared mod version suppressed refresh even when source
text changed. AutoWorkflowTest.refreshesChangedSourceEvenWhenDeclaredVersionIsUnchanged
covers changing one translated source and adding a new entry without a version
bump: both reappear untranslated in the request, source bytes remain unchanged,
and the stale translation is not reused. Existing Auto workspace/state paths are
preserved. This is not full Auto/shared-workflow convergence.

Automated check command: gradlew.bat :ssmt-auto:test :ssmt-auto:checkstyleMain
:ssmt-auto:checkstyleTest :ssmt-auto:spotbugsMain --offline --console=plain.
Result: BUILD SUCCESSFUL in 26s, 22 tasks (14 executed). Test XML and static
reports are under ssmt-auto/build/; they are disposable local evidence, while
the regression source remains the reproducible contract. Current source remains
uncommitted on starting HEAD e32f1f6. Full-suite and exact-commit release checks
remain required after subsequent integration.

Additional deferred scenario: change one text and add another in a separate mod
copy without bumping mod_info.version, rerun Auto and confirm the new request
contains both current source strings, never the stale translation. Preserve the
old output until a complete validated response is available.

1. P1: converge Auto with the shared workflow while retaining master-library
   reuse, filename-independent response discovery, one-output presentation,
   existing-workspace recovery and explicit choices for legacy/lineage conflicts.
   Add cross-entry-point regression fixtures before changing the state model.
2. P2: read-only deterministic assessment command and reports, safe archive-root
   selection, complete inventories, dependencies and explicit authority/trust
   dispositions. Never imply source/JAR equivalence from filenames.
3. P3: complete extraction/skipping inventory and advisory gaps; accepted schema
   fixtures and format-preserving reinjection. Unknown visibility remains review,
   not automatic translation or custom-schema acceptance.
4. P4: source manifests and non-mutation attestation, independent output-clone
   audit, directory/ZIP identity, reproducible build-input and authority records.
5. P5–P6: machine-readable independent evidence gates, runtime capture facilities,
   evidence-derived final status, strict contradictory/missing evidence rejection,
   future-attempt feedback classification and fixtures. Unperformed scenarios and
   unresolved authority/rights must remain explicit.
6. Reconcile roadmap checkboxes against actual implementation/test evidence;
   commit/push reviewed changes, rebuild versioned artifacts from the exact commit,
   verify exact-SHA CI and prepare/publish a prerelease only when automated release
   gates pass. Full-release promotion is deferred until validation succeeds.

After each phase record changed paths, tests and actual results, commit/build
identity, evidence locations, unresolved implementation and additional manual
scenarios. This file is a resume checkpoint, not proof those phases are complete.

## Latest implementation checkpoint — JSON preservation

The local main checkpoint remains 3795002175e123841af19ed339381f7e54e15314.
Subsequent assessment fingerprints, source metadata manifests, file/CSV/JAR coverage,
hash-bound assurance ledger reader/CLI and JSON token-preserving reinjection remain
part of the next local checkpoint. See ASSESSMENT_IMPLEMENTATION.md,
COVERAGE_IMPLEMENTATION.md, PACKAGE_AUDIT_IMPLEMENTATION.md,
ASSURANCE_LEDGER_IMPLEMENTATION.md and REINJECTION_IMPLEMENTATION.md.

The historical goal statement above has been superseded by the 2026-09-19 current
phased roadmap checkpoint. Push to Exxec/Project-Go main is awaiting explicit user
approval; no retry or alternate publication path has been attempted. No new release
has been published.

Combined automated validation after the JSON compatibility and CSV test-helper fixes:
`check :ssmt-cli:installDist :ssmt-gui:installDist :ssmt-auto:installDist` with
`--rerun-tasks --no-build-cache --offline --console=plain` passed in 1m13s,
102/102 tasks executed, 514 tests and zero failures/errors/skips. LOCALAPPDATA began
absent at `.local/checks/json-preservation-final-20260913`. Checkstyle and SpotBugs
main/test gates passed. Native-access/Gradle-deprecation and the existing validation
analysis missing-class warning remain visible; no warning-free claim is made.
These are local working-source results, not exact-SHA CI or immutable release proof.

## Subsequent CSV implementation checkpoint

Token-only CSV reinjection preserves header/technical-cell spelling, extra cells,
record shape, comments, blank lines, multiline fields, BOM and UTF-8/GB18030 encoding.
Missing target cells and ambiguous identities/headers reject safely. See
REINJECTION_IMPLEMENTATION.md for the blank-prefix failing fixture and correction.
Full uncached `check` plus GUI/CLI/Auto installDist passed in 1m8s, all 102 tasks
executed, using initially absent `.local/checks/csv-preservation-fresh-20260913`.
This run passed 517 tests with zero failures/errors/skips and all Checkstyle/SpotBugs
main/test gates. The previous 514-test run remains historical.
Whole-file text encoding, broad schema/JAR coverage and other roadmap work remain
implementation tasks; the goal is not complete. Push approval is still pending.

## Weapon coverage / export-scope implementation checkpoint

Seven missing public weapon tooltip columns are now optional standard extraction
fields. Synthetic extraction and GB18030 shared-workflow tests cover source,
placeholder/highlight and technical-cell preservation. Edmund fixture source stayed
unchanged; six exact new tooltip IDs grew current coverage from 207 to 213.
The added Nightcross workspace retains 12,745 translations and now has 12,912
entries, with 167 new entries blank. Earlier output is not overwritten. Existing
review flags remain explicit. See WEAPON_TOOLTIP_COVERAGE.md.

AI exports report selected-project scope, snapshot counts and full-mod coverage
NOT_ESTABLISHED, with legacy/growth-compatible import checks. Final combined full
uncached check plus GUI/CLI/Auto installDist passed in 38s with all 102 tasks
executed and initially absent `.local/checks/coverage-summary-final-fresh-20260913`.
All 522 tests passed with zero failures/errors/skips, plus Checkstyle/SpotBugs
main/test. New handoff artifact:
`.local/live-tests/nightcross-added-20260913/Nightcross-Tooltip-Gaps-with-Coverage-to-English.json`,
SHA-256 `6cf5b7b144551742cb9933ebd0a88384a8c16acc46243adfd9ac83e5b20da277`.
It contains 12,912 entries, 12,745 preserved translations and 167 blanks; no updated
English clone is built until missing translations are supplied and validated.
The full roadmap remains active: residual/unknown-format diagnostics, Auto
convergence, build/package assurance, runtime capture and final-gate integration.

### Residual standard-CSV diagnostic tranche â€” 2026-09-13

Recognized standard CSV files now receive a separate read-only review for
non-selected columns containing non-ASCII text. Findings name the path and column,
or explicitly report that the review was unavailable because the source was unsafe,
oversized, unreadable, malformed, or header-ambiguous. The normal CLI presents
these as review-only omissions; it neither changes a schema nor creates translated
entries. A failing fixture proves Chinese `groupTag` and `tags` cells are reported
but stay excluded from extraction and opt-in schema generation.

Focused extractor/CLI regression passed after the change. Full project validation,
workflow convergence, independent clone attestation, assessment hardening and
completion-gate integration remain required before any roadmap item is closed.

### Design-type JSON-key preservation tranche — 2026-09-14

`data/config/settings.json` now has a bounded standard extraction rule for
`/designTypeColors/*` field names. These entries are exported as `json-key:`
units because Starsector matches them literally to player-visible
`tech/manufacturer` text. Token-preserving reinjection renames the field rather
than adding a value or a second field, retaining the color array and non-target
bytes. Destination collisions, including a proposed Chinese-to-English rename
when the English key is already present, fail before output is produced.

Extractor, patcher and shared TranslationWorkflow fixtures cover selection,
Nightcross-style matched manufacturer/key translation, source preservation and
duplicate rejection. Focused module tests and a full offline `gradlew check`
passed. Pre-existing historical duplicate pairs remain REVIEW_REQUIRED; Project
Go does not guess which key to delete. Record this check in the final native/game
validation run; it does not establish live loading or rendering behavior.

## Deferred validation checklist for later today

The added Nightcross source was subsequently run through normal export/import/build
into a new independent output, with selected-string and source/output inventory
attestation. See NIGHTCROSS_ADDED_RUN.md and RESIDUAL_CHINESE_DIAGNOSIS.md.
Weapon tooltip/custom-CSV coverage omissions and native Unicode argument handling
remain implementation findings; no runtime validation/full coverage claim is made.

- Native desktop on a fresh profile: drop the original Nightcross ZIP, pick the
  ZIP, pick its mod folder and mod_info.json; confirm loading/progress and the
  expected entry count without short-path workarounds.
- Invalid input/cancellation: Desktop ancestor folder and invalid/dropped files
  explain the problem; cancellation and failed operations preserve active work;
  owned error details and the local diagnostic report are usable.
- Legacy choice: import selected old work, start fresh or cancel; multiple old
  candidates require explicit selection; originals stay unchanged and translated
  entries survive import. Repeat through each entry point changed by P1.
- GUI/Auto/CLI handoff: use a renamed valid AI response; wrong identity/language,
  ambiguous candidates and invalid responses are rejected without state loss.
- Native build/install: one translated output, remembered valid destination,
  recoverable interruption and explicit ambiguous-recovery handling; no source
  modification or unwanted visible internal state files.
- Fresh translated campaign: faction, station/planet names and types, contacts,
  representative dialogue, ship/fleet names and representative Nightcross combat.
  Names not actually observed are NOT_TESTED, not a successful name check.
- Save/reload the new translated campaign; upgrade compatibility only with an
  explicitly approved separate save-copy scenario. Preserve baseline saves.
- Repeat relevant native/game checks after relocation using the final exact
  commit/versioned prerelease, not the earlier dirty development build.
- JSON preservation: representative permissive/GB18030 JSON retains comments and
  technical values, loads correctly, and rejects stale/duplicate fields with useful
  native feedback and no source or active-work loss.
- Design-type colors: each translated `tech/manufacturer` label has exactly one
  matching `designTypeColors` key, RGBA arrays remain unchanged, and an existing
  English destination causes a safe rejection rather than a duplicate.
- CSV preservation: extra/short rows, multiline text, technical cells, comments,
  original separators and GB18030 survive build; game accepts the output. Duplicate
  identity/header and absent-target errors preserve active work. Record hashes,
  exact final build/commit and evidence in `.local/checks/reinjection-validation/`.
- Weapon tooltip overrides/highlights: rendered placeholders, English highlights,
  GB18030 loading and unchanged tags/IDs/stats; inspect the selected-only export
  warning and invalid-response feedback on the final exact build. Evidence:
  `.local/checks/weapon-tooltip-validation/` with source/response/output hashes.

Record scenario, input/output hash, build/commit/version, game/JVM/mod versions,
observed result and evidence path. Add new scenarios when implementation changes
their scope. Earlier user-reported save/reload/localization results are retained
in LIVE_VALIDATION_0.8.0.md, not silently promoted to exhaustive final-build proof.
