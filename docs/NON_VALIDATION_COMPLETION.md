# Project Go non-validation completion checkpoint

## Scope and completion contract

Complete remaining product implementation across roadmap P0–P6. Automated
regression, static, build and package checks remain implementation requirements.
Defer hands-on desktop and live Starsector scenarios to the checklist below.
Do not claim runtime compatibility or full-release readiness from offline checks.
Do not change original mods, shared BridgeForge rigs or existing campaign saves.

## Verified starting state — 2026-09-13

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

Remaining integration: bounded ledger CLI/schema, independently verify evidence
references and hashes, runtime environment/process/log capture, required-scenario
coverage, final report/package hash binding and future-attempt feedback fixtures.
P5/P6 are not complete merely because the record model exists.

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

The goal remains active: Auto/shared workflow convergence, CSV format preservation,
assessment authority/global limits, independent clone audit/build binding and runtime
capture/final assurance integration are not complete. Push to Exxec/Project-Go main
is awaiting explicit user approval after the previous push was rejected; no retry or
alternate publication path has been attempted. No new release has been published.

Combined automated validation after the JSON compatibility and CSV test-helper fixes:
`check :ssmt-cli:installDist :ssmt-gui:installDist :ssmt-auto:installDist` with
`--rerun-tasks --no-build-cache --offline --console=plain` passed in 1m13s,
102/102 tasks executed, 514 tests and zero failures/errors/skips. LOCALAPPDATA began
absent at `.local/checks/json-preservation-final-20260913`. Checkstyle and SpotBugs
main/test gates passed. Native-access/Gradle-deprecation and the existing validation
analysis missing-class warning remain visible; no warning-free claim is made.
These are local working-source results, not exact-SHA CI or immutable release proof.

## Deferred validation checklist for later today

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

Record scenario, input/output hash, build/commit/version, game/JVM/mod versions,
observed result and evidence path. Add new scenarios when implementation changes
their scope. Earlier user-reported save/reload/localization results are retained
in LIVE_VALIDATION_0.8.0.md, not silently promoted to exhaustive final-build proof.
