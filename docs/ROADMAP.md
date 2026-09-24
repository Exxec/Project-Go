# Project Go roadmap

This roadmap turns verified failures and real-mod lessons into implementation
gates. A checked item requires reproducible evidence; a successful extraction,
compile, or main-menu boot must not be promoted into a broader compatibility or
release claim.

## Pre-implementation review checkpoint — 2026-09-10

- At the start of this review, the working tree was clean after refreshing
  `origin`.
- At that point, `HEAD`, `origin/main`, and the peeled `v0.7.0` tag resolved to
  `c32fe437c7bedf1da16ee23d73123ad507d61567`. There are no source changes since
  the 2026-09-06 checkpoint.
- The local 0.7.0 verification remains useful evidence: 383 tests, Checkstyle,
  SpotBugs, launcher smoke tests, and committed-source version checks passed on
  the prepared development machine.
- Exact-commit GitHub runs `34034890871`, `34034890878`, and `34034890877`
  failed. `BackendCommandTest.completeCliWorkflowIsDeterministicZeroTouchAndTransactional`
  and `ProjectCommandTest.importsAiResponseIntoProjectFile` fail when the default
  per-user translation-library parent directory does not already exist.
- The failure was reproduced locally by pointing `LOCALAPPDATA` at an absent
  directory. The same tests pass when the ordinary Project Go user directory
  already exists. At that checkpoint, `SqliteTranslationMemory.open()` opened
  the SQLite path without first creating its parent.
- The tag exists, but no GitHub Release exists for `v0.7.0`. The release workflow
  never reached its packaging or publication jobs. Version/tag/source alignment
  therefore does not establish a published release.
- Native file-picker behavior, interactive in-game behavior, save/load behavior,
  and standalone translation-overlay compatibility remain unverified.

## Implementation checkpoint — 2026-09-10

- The first-run translation-library failure is fixed and covered by a regression
  whose normalized database parent starts absent, plus a blocked-parent failure
  case that retains the domain exception contract.
- Normal GUI and simple CLI builds now publish one translated copy without a
  permanent source-backup sibling or user-visible changes CSV. Advanced builds
  retain the paired backup and explicit report behavior.
- The desktop normal path presents one context-sensitive action across Choose,
  Translate, and Install stages. Auto now keeps project state and ZIP extraction
  beneath the application-data workspace while leaving only the AI handoff and
  translated result beside the selected source.
- A clean, offline, uncached Windows `check` passed 390 tests with zero failures
  or skips, together with Checkstyle and SpotBugs.
- GitHub Actions run `34542852609` passed the exact pushed implementation commit
  `c9c0987fbd90d75868f268e0c0780669cd3588c4` on Windows and Ubuntu. Windows also
  built and smoke-tested the application image; both jobs generated release
  evidence. This is branch-build evidence, not a tag or published-release claim.

## Lessons retained

1. A prepared developer profile can hide first-run defects. Tests that use a
   default cache, catalog, workspace, or application-data path must run against
   an explicitly isolated, initially absent parent directory.
2. A local green build is evidence for that machine, not release evidence.
   Release completion requires green CI for the exact commit and tag, uploaded
   artifacts, verified hashes and embedded versions, and an observable release
   record.
3. Source immutability, deterministic extraction, and successful reinjection are
   necessary but do not prove that a revived mod loads or behaves correctly.
4. A partial compile, a source-to-JAR match, or an automated main-menu boot proves
   only its named gate. Interactive campaign/combat behavior and persistence are
   separate gates.
5. Every real-mod miss should become a minimal repository-owned fixture and a
   narrow detector or diagnostic improvement. Third-party mod bytes stay outside
   normal source history.
6. The selected source root, archive, source/JAR relationship, dependency
   classpath, loader/provider ownership, and build command are evidence inputs.
   Do not choose among historical or variant artifacts by filename or recency
   alone.
7. IDs can occupy distinct namespaces. Variant IDs, wing IDs, faction IDs,
   registrations, consumers, and reciprocal references must be checked in both
   directions before a migration is called complete.
8. Narrative status files become stale. Completion status should be derived from
   machine-readable per-gate evidence and must contain exactly one final state.
9. Redistribution permission is independent from technical success. A private,
   source-safe personal copy is not authorization to publish a revived mod.
10. Ignored build, cache, and local release directories are disposable, not
    durable evidence. Historical documents must label them as local and point to
    tracked fixtures, commits, or remote runs for reproducible claims.
11. Normal and Advanced file contracts must be named explicitly in documentation;
    a paired backup/report guarantee for maintainers is not the normal user's
    one-output workflow.
12. Cleanup is a two-step evidence operation: render and persist a sorted,
    hash-backed preview, then execute only those exact unchanged candidates.
    Re-scanning at execution time silently expands user approval and is unsafe.
13. Interrupted publication can be recovered automatically only when exactly one
    last-known-good tree exists and the destination is absent. Competing previous,
    staging, or published trees remain an explicit review decision.
14. Filename-independent discovery must bind workflow identity, source and target
    languages, protected source text, entry-set integrity, size/count limits, and
    ambiguity. A familiar filename is a priority hint, not authority.
15. A drop target should represent the user's current task, not one file type.
    Route inputs by validated type and workflow state, retain pickers as a
    fallback, and keep rejected drops from replacing active work.

## P1 implementation evidence — 2026-09-11

- Auto accepts completed responses under arbitrary sibling JSON names using a
  bounded 128-file/16 MiB discovery window and embedded identity/integrity. It
  rejects ambiguity and wrong-language responses, and a stale documented-name
  response no longer masks a newer renamed response.
- The desktop remembers a validated install parent only after a successful build;
  stale, linked, missing, or unwritable paths are ignored.
- Application storage cleanup persists a sorted file-count/byte-count/SHA-256
  preview. `storage clean` consumes only that manifest, reclassifies every target,
  and stops when an approved candidate changed. Newly aged paths are not included.
- Interrupted translated-copy recovery restores automatically only when output is
  absent and one unchanged previous tree survives. Every ambiguous state reports
  review required and preserves all trees.
- A clean, uncached Windows `check` passed 405 tests with zero failures or skips,
  together with Checkstyle and SpotBugs. The same exact implementation commit,
  `10f274bb982a28f01fc4bbf5297026da25a005bc`, passed GitHub Actions run
  `34605294776` on Ubuntu and Windows; Windows built the native application image
  and both jobs generated release evidence. Native interactive picker and
  live-game evidence are still pending.
- These cleanup/recovery contracts adapt tracked BridgeForge patterns for sorted
  inventories, containment, hashes, and explicit actions. BridgeForge's inspected
  checkout was dirty/diverged, so untracked or modified high-level files were not
  treated as authoritative inputs.
- The normal desktop now accepts ZIPs, mod folders, `mod_info.json`, and returned
  AI JSON on one drop surface. GUI and Auto use the same bounded, hash-keyed
  archive preparation service; failed preparation cannot replace the active GUI
  session. Settings exposes exact cleanup candidates and only enables unambiguous
  recovery. A first-ever process crash before any install destination was
  remembered still requires an explicit output-path recovery entry point.
- Committed implementation `c3ddd2e976790e3a15ec54934d366f61e759f62f`
  passed a forced rebuild of all 418 tests with zero failures or skips, Checkstyle,
  SpotBugs, archive scanning, SBOM/checksum generation, and both Windows native
  application smoke tests. The rebuilt GUI and Auto JAR manifests contain that
  exact commit and version `0.8.0-dev`. Interactive drag/drop and live-game
  behavior remain manual acceptance gates.

### Shared transition-contract checkpoint - 2026-09-19

- `WorkflowTransitionContract` now defines the source-bound phases used by the
  normal facade and Auto: input accepted, project ready, response pending,
  response imported, and output published. The contract binds the exact mod id,
  protected entry/source-text digest, entry count, and untranslated count.
- `SharedTranslationWorkflowService` now owns create, refresh, export, import,
  and build orchestration. GUI and simple CLI reach it through
  `TranslationWorkflow`; Auto keeps catalog reuse and bounded response discovery
  as adapters around the same service. Incomplete output and response entry-set
  drift fail before publication.
- Auto state schema 2 persists the shared boundary. Schema 1 workspaces migrate
  in place on the next successful pass; a forged or stale schema 2 binding is
  rejected before source or project bytes change.
- Auto now defers refreshed/imported project persistence until response
  discovery/import and request export or clone publication succeed. A rejected
  response after source refresh leaves the last committed project byte-identical.
- `WorkflowPersistenceService` publishes the normal workspace document and
  Auto's project/state pair through one hash-manifested transaction. In-process
  failure restores every previous document; restart recovery rolls partial
  publication back, accepts an entirely published set, and removes staging that
  never reached a prepared manifest. Targets are bounded, direct workspace
  children and may not traverse symbolic links.
- A fresh, initially absent `LOCALAPPDATA` full check and CLI/GUI/Auto
  distribution rebuild passed 547 tests with zero failures, errors, or skips;
  all 102 Gradle tasks executed. This is local working-source evidence, not
  exact-commit CI or release evidence. Known native-access, Gradle 10, and
  static-analysis missing-class warnings remain visible.
- P1 automated implementation is converged through the shared operation,
  transition, and persistence services with normal and Auto injected-failure
  coverage. Native drag/drop and picker acceptance remains a deferred manual
  gate and is not inferred from automated tests.

## Phased roadmap application checkpoint - 2026-09-20

- P2 is closed at the read-only assessment boundary with deterministic severity
  findings; `ASSESSMENT_ONLY` remains mandatory.
- P3 now records handling for every outer file and nested JAR entry, reports
  unselected standard JSON leaves and CSV structural/column gaps for review, and
  requires an explicitly supplied opt-in catalog before generated schemas apply.
- P4 now enforces independent clone and directory/ZIP audits plus structured,
  candidate-bound build evidence. Assessment records independent pre/post
  manifests, while shared create, refresh, response-import, and clone-build
  operations reject source-byte or metadata drift and transactionally retain a
  bounded manifest attestation ledger in the internal workspace.
- P5/P6 machine-readable gates, runtime capture, strict evidence references,
  evidence-derived status, and escalation-preserving completion are implemented.
  Candidate-bound feedback now requires classified surprises with hashed evidence,
  fixture, and change artifacts. Finalization reruns package identity after READY
  assurance/feedback records and emits release hashes. Live execution and actual
  per-attempt review/archive work remain open.
- A fresh-profile offline full check and CLI/GUI/Auto distribution rebuild passed
  564 tests with zero failures, errors, or skips; all 102 Gradle tasks executed.
  The rebuilt CLI launcher reported `SSMT 0.8.0-rc.1`; GUI and Auto launchers
  passed `--smoke-test`. This is working-source evidence, not exact-commit CI or
  release evidence.

## Phase execution checkpoint - 2026-09-23

- The published `v0.8.0-rc.1` release and its exact-source CI, checksums,
  downloaded assets, and embedded versions satisfy P0's historical new-version
  publication items. See `RELEASE_0.8.0_RC1_VERIFICATION.md`. This does not
  publish later commits on `main` or promote the pre-release.
- At `1e084c03c31d5616cde333861621fff8999f2227`, a fresh-profile, offline,
  uncached full check and CLI/GUI/Auto distribution rebuild passed 572 tests,
  zero failures/errors/skips, and all 102 Gradle tasks. The three rebuilt app JAR
  manifests report that commit and `0.8.0-rc.1`; they are local development
  packages, not assets from the published RC1 tag.
- Packaged CLI and Auto each accepted a nested-root synthetic ZIP, imported a
  response under an arbitrary filename, and published one translated folder.
  The source ZIP stayed hash-identical and source text stayed unchanged. The GUI
  launcher passed `--smoke-test`; its real Windows ZIP picker later loaded the
  same fixture and showed a ready project from an initially absent profile.
  At this initial checkpoint, GUI export/import/install and actual drop
  interaction remained open; the later P1 evidence below closes them locally.
- A real Nightcross ZIP copy remained SHA-256
  `a6baabc3c935c99cf881312f738fa044bc7b8b6fed6fbf9ed569e77e3fe2b65b`.
  Current CLI export selected 12,918 entries, while the saved response belongs
  to an older 12,745-entry export. No stale response was imported or real-mod
  output published in this checkpoint.
- Read-only `assess --coverage` now works on uniquely rooted ZIPs via a ZIP
  filesystem without materializing a mod tree. The synthetic ZIP fixture
  covers standard extraction, JSON review gaps, and nested JAR entry handling.
  Nightcross basic assessment still inventories 2,374 files, but its
  `jars/nightcross.jar` has a CRC mismatch at `.idea/.gitignore`; ZIP
  coverage stops without changing the source. Requested JAR inventory now
  emits a partial JSON assessment, `BLOCKING` integrity finding, and nonzero
  exit rather than losing the candidate report. Coverage-only ZIP assessment
  now reports the same integrity finding without claiming a JAR inventory.
  Malformed selected JSON now produces a blocking partial coverage report for
  directory and ZIP inputs, with a portable path and no claimed extraction.
  The latest fresh-profile full
  check and distributions passed 577 tests with all 102 tasks executed; the
  native development bundle and smoke tasks passed. These are local working
  source checks, not release evidence. See `COVERAGE_IMPLEMENTATION.md`.
- `ROADMAP_PHASE_STATUS_2026-09-23.md` records commands, trust limits, and
  resume actions for P0-P6. Candidate-specific, interactive, runtime,
  persistence, rights, and later-release gates remain open where marked below.
- A Nightcross archive-assessment feedback packet classifies the CRC mismatch
  as candidate-specific and the former report-loss failure as a detector gap.
  Six evidence/fixture/change references are hash-bound; `attempt-feedback`
  verifies the record as `REVIEW_PENDING`, not ready for the next candidate.

## P0 — restore trustworthy build and release evidence

- [x] Make translation-memory creation safely create its normalized parent
  directory without weakening path or source/output boundaries.
- [x] Give every test that invokes a default user path an isolated temporary
  catalog/workspace. Also retain a regression that begins with the parent absent,
  so test isolation cannot conceal first-run behavior.
- [x] Run the authoritative clean, offline, uncached checks on Windows and retain
  the failing clean-home case as a permanent regression gate.
- [x] Run the authoritative clean, uncached checks on Linux CI for the exact
  pushed implementation commit.
- [x] Avoid redundant branch-and-tag build runs when the release workflow already
  performs the tag checks; keep pull-request and branch verification intact.
- [x] Publish a new version rather than moving the existing `v0.7.0` tag. Require
  exact commit/tag/version agreement, green tag CI, launcher and executable smoke
  tests, SBOM/checksums, and a GitHub Release whose assets match those checksums.
  Met for the historical `v0.8.0-rc.1` release only; later `main` changes are
  unreleased.
- [x] Update release documentation only after the observable remote evidence is
  complete. The RC1 receipt records exact-tag CI and downloaded-asset hashes.

Exit criteria: a fresh user profile passes the full suite on both supported CI
systems, and a new release can be traced from source commit to tag, workflow,
artifact hash, embedded version/commit, and published release asset.

## P1 — one simple workflow and file model

Implement [the simple file workflow](designs/simple-file-workflow.md) before
adding more normal-path controls.

- [x] Replace the four always-visible normal actions with a three-stage
  choose/translate/install presentation and one context-sensitive primary action.
- [x] Move Auto's sibling project/state/extraction layout into an internal
  application-data workspace. Unifying it with the normal GUI root remains part
  of the shared-workflow item below.
- [x] Let the desktop import an AI response under any filename, using its embedded
  identity and integrity fields instead of its path.
- [x] Extend filename-independent response import to Auto using bounded sibling
  discovery, embedded identity/integrity, ambiguity rejection, and continued
  priority for the documented response name.
- [x] Remember one validated Starsector `mods` destination instead of asking on
  every desktop build.
- [x] Publish one translated folder, keep output rollback staging internal, and
  stop publishing a permanent pristine-backup sibling in normal GUI, simple CLI,
  and Auto flows.
- [x] Keep `Project Go Changes.csv`, project JSON, state, history, reports,
  translation memory, extraction cache, and recovery data internal by default.
  Offer explicit exports and paths under Advanced.
- [x] Add bounded cache cleanup, interrupted-output recovery, and legacy-project
  adoption without deleting existing workspaces or copies. Hash-bound preview and
  cleanup plus unambiguous prior-output recovery are implemented in CLI and GUI;
  the GUI reports ambiguous states without changing them. Explicit legacy
  adoption is implemented through hash-bound GUI/CLI choices and covered by
  TranslationWorkflowTest. Auto now persists and validates the shared
  source/entry-bound transition state while migrating its schema 1 workspaces.
- [x] Make GUI, Auto, and simple CLI exercise the same workflow service and
  state-transition contract. Auto retains catalog and response-discovery
  adapters around the shared project-module service.
- [x] Run the native clean-profile ZIP-to-output acceptance path through GUI,
  Auto, and simple CLI on the supported packaged applications. Automated shared
  service/state-machine and isolated Auto round-trip coverage do not replace this
  exit test.
- [x] Rewrite the normal user guide around the visible task; keep database,
  schema, report, and portable-project instructions in Advanced sections.

Exit criteria: a clean-profile acceptance test goes from a ZIP to one translated
output without requiring the user to name or manage an internal file, while
cancelled and failed operations preserve source, project state, and prior output.

Evidence: the isolated Auto ZIP round trip now accepts an arbitrarily named
response, publishes one translated folder, preserves the archive hash, and exposes
no project, backup, or report sibling. Destination persistence and hash-bound
cleanup/recovery have isolated regression coverage. GUI, Auto, and simple CLI now
converge on the shared workflow service and state-transition contract. Explicit
GUI/CLI legacy adoption is implemented. Packaged native Windows GUI runs on
2026-09-23 completed both picker intake and a fresh-profile file drag from ZIP
through request export, renamed-response import, and one translated-folder
install with unchanged source hash. A cancelled export and rejected invalid
response preserved the prior output. This closes local synthetic P1 acceptance;
exact-source release acceptance and real-mod runtime gates remain separate.
See `ROADMAP_PHASE_STATUS_2026-09-23.md`.

## P2 — evidence-first revival assessment

- [x] Add a read-only assessment command for a directory or archive. Normalize
  archive wrappers, require exactly one selected `mod_info.json`, reject unsafe
  entries, and never write beneath the candidate or original archive.
- [x] Emit deterministic JSON plus human-readable output containing candidate and
  archive SHA-256, selected root, file inventory, mod/game versions, declared
  dependencies, JAR/class/source inventory, extraction coverage, and trust limits.
- [x] Record source authority explicitly: origin, archive hash, archive coverage,
  selected variant, source/JAR correspondence, and conflicts among competing
  historical inputs.
  The optional read-only `assess --compare-input <directory-or-zip>` records whether
  a second uniquely rooted input matches the selected candidate bytes or differs;
  neither outcome asserts historical origin authority.
- [x] Distinguish supported, review, manual, and blocking findings. Any uncertain
  bytecode-only behavior, save-state migration, internal API use, undeclared
  library ownership, or architecture redesign must remain an escalation gate.
- [x] Report nested wrapper selection separately from mod validity so scanning the
  wrong directory cannot masquerade as a broken mod.

Exit criteria: two runs over identical bytes produce identical reports, the
candidate remains byte-for-byte unchanged, and the report cannot label a mod
revived or runtime-compatible.

P2 checkpoint - 2026-09-19: the existing deterministic directory/ZIP assessment,
portable candidate/archive fingerprints, metadata/dependency report, JAR payload
inventory, source-authority disposition and competing-input comparison satisfy
the read-only report boundary. Findings now use an explicit `SUPPORTED`, `REVIEW`,
`MANUAL`, or `BLOCKING` severity. Bytecode without observed source, save-state
migration, internal API use, library ownership and architecture redesign remain
manual escalation findings. Nested wrapper selection is a review finding separate
from metadata validity. Assessment status remains `ASSESSMENT_ONLY`; exit zero is
not a revival, runtime, persistence, authority, or redistribution claim.

## P3 — close localization coverage gaps safely

Implementation checkpoint 2026-09-13: seven public weapon tooltip override fields
have synthetic extraction/shared-workflow round trips, including GB18030 and
placeholder preservation. AI exports explicitly state selected-entry coverage,
not complete mod coverage. File and nested-JAR handling inventories plus advisory
CSV/JSON gap detection are now explicit; uncertain custom-format acceptance and
complete reinjection preservation remain open. See WEAPON_TOOLTIP_COVERAGE.md and
COVERAGE_IMPLEMENTATION.md; P3 is not closed.

- [x] Produce a complete supported/extracted/skipped inventory for CSV, JSON-like,
  `.ship`, plain text, loose classes, and JAR entries.
- [x] Extend advisory gap detection to malformed CSV rows, duplicate or blank
  identities, text in extra columns, unrecognized JSON subtrees, and player-visible
  strings that current standard schemas intentionally skip.
  Recognized standard CSVs now report non-ASCII text in unselected columns as
  review-only findings, including an explicit unavailable status for unsafe,
  oversized, unreadable, malformed, or ambiguous-header inputs. This does not
  infer visibility or authorize schema expansion. `assess --coverage` now
  includes these findings for directories and ZIPs, including text first seen
  in a later CSV row; the samples and reads are bounded, and JSON/CSV gap paths
  in the assessment report are mod-relative rather than ZIP backing URIs.
- [x] Require human confirmation of player visibility and stable identity before
  accepting a generated custom schema.
- [ ] For each accepted ecosystem format, add a synthetic failing fixture first,
  implement the narrowest compatible rule, then rerun module, full-suite, and
  source-immutability checks.
- [x] Preserve technical cells, row shape, comments, encoding, non-target JAR
  entries, and original text checks during reinjection.
  JSON and CSV now use token-only edits with strict source encoding/BOM and original
  text guards; extra/short CSV rows and ambiguous identities have regressions.
  Whole-file mission text now retains strict UTF-8/GB18030 encoding and UTF-8 BOM;
  JSON/CSV use token-only same-encoding edits, and JAR injection preserves every
  non-target entry's content bytes. See `REINJECTION_IMPLEMENTATION.md`.

Exit criteria: every skipped candidate file has a deterministic reason, accepted
coverage round-trips through a repository-owned fixture, and uncertain content is
reported rather than guessed.

## P4 — reproducible candidate and package audit

- [x] Record pre/post source tree manifests and prove that assessment, extraction,
  translation, and build did not change source bytes or metadata.
  Assessment JSON retains its two captures. Normal and Auto workspaces retain the
  latest equal-manifest attestation for `CREATE_EXTRACTION`, `REFRESH_EXTRACTION`,
  `IMPORT_RESPONSE`, and `BUILD_CLONE`; project/state and attestations publish in
  the same recoverable transaction where those documents change.
- [x] Attest the output clone independently: expected translations changed, every
  non-target file remained byte-identical, and no undeclared output appeared.
- [x] Compare the final candidate directory with its ZIP entry-by-entry using
  normalized relative paths, sizes, and SHA-256. Report missing, extra, and changed
  files separately from stale report bookkeeping.
- [x] Require the selected JDK, dependency/classpath hashes, build inputs, exact
  commands, and source/JAR/loader authority for any compilation evidence.
- [x] Keep clone output as the supported path until a standalone overlay passes
  equivalent format and game-loading tests.

Exit criteria: another machine can reproduce the assessment and package identity
from the recorded inputs without access to the original working directory.

## P5 — runtime and persistence gates

- [x] Define separate machine-readable gates for offline validation, automated
  launcher/main-menu boot, interactive campaign behavior, combat behavior,
  save/load persistence, and upgrade compatibility.
- [x] Capture the exact Starsector build, enabled mods and versions, load order,
  JVM, candidate hash, direct Java process exit state, logs, and any modal dialog.
  `ssmt runtime-evidence --candidate <mod> --template` emits a candidate-bound pending
  capture schema. A returned capture is structurally verified with bounded, contained log
  hashes; its gameplay semantics deliberately remain unverified.
- [ ] Exercise registrations and consumers: faction/market ownership, variant and
  wing lookups, scripts/plugins, campaign creation, representative combat, and
  content refresh paths.
- [ ] Test a new campaign and, when the mod owns persistent state, save/reload and
  an explicitly approved upgrade path. Absence of a crash at the main menu is not
  persistence evidence.
- [x] Keep unsupported semantics at a review gate; do not replace IDs, providers,
  dependencies, or historical weights merely to suppress a load error.

Exit criteria: each claimed behavior maps to a recorded scenario and result;
untested behavior remains visibly `NOT_TESTED` rather than inheriting success from
another gate.

## P6 — completion and future-attempt feedback

- [x] Generate the final status and summary from per-gate evidence. Reject missing,
  repeated, contradictory, or non-final completion declarations.
- [x] Require source review, compile/build evidence when applicable, static
  validation, dependency and API checks, package validation, save-compatibility
  disposition, live-test disposition, and redistribution-rights disposition.
- [ ] Run the candidate-to-ZIP identity audit after all reports and packaging are
  final, then archive the report with the release hashes.
  `ssmt finalize-evidence` now enforces this order and emits the hashes without
  overwriting an existing report; executing it against the eventual release bytes
  remains required.
- [ ] After every attempt, classify each surprise as candidate-specific, detector
  gap, workflow gap, or documentation gap. Add the smallest reusable fixture and
  tool change before starting the next candidate.
  `ssmt attempt-feedback` now enforces these classifications and hash-bound evidence,
  fixture, and change artifacts for each recorded surprise. The Nightcross
  archive-assessment packet records two surprises but remains `REVIEW_PENDING`;
  real-attempt review remains a procedural gate.
- [x] Never move an assessment copy to a completed state while a manual,
  bytecode-only, authority, persistence, runtime, or rights gate remains open.

Exit criteria: “complete” means the exact published bytes and their evidence agree;
it is not inferred from directory names, unchecked plans, partial compilation, or
an earlier package.

## Protocol for future revival attempts

1. Preserve the original archive/tree and create a byte-preserving assessment
   copy. Record hashes before analysis.
2. Establish which artifact is authoritative. Record incomplete archives,
   alternate JARs, edited chronology, and conflicts instead of silently merging.
3. Select the actual nested mod root and run the read-only assessment. Stop for
   manual review on unknown authority, bytecode-only core behavior, save-state, or
   architecture uncertainty.
4. Turn every newly observed format or failure into a minimal failing fixture and
   detector/diagnostic improvement before repairing the candidate.
5. Perform approved work only in a separate candidate copy. Re-scan and compare
   source, candidate, and build-input manifests after each phase.
6. Run offline, build, boot, interactive, combat, and persistence gates separately.
   Record `PASS`, `FAIL`, `NOT_TESTED`, or `NOT_APPLICABLE` with evidence for each.
7. Verify final directory-to-archive byte identity, artifact provenance, version,
   dependency declarations, and rights. Generate status from those results.
8. Publish only from a green exact commit/tag workflow; verify the remote release
   and downloaded asset hashes before calling the attempt complete.
