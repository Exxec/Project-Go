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
  Six evidence/fixture/change references are hash-bound. Its initial status
  was `REVIEW_PENDING`; the 2026-09-24 review below verifies it as
  `READY_FOR_NEXT_CANDIDATE` without clearing Nightcross's integrity gate.

## Offline gate checkpoint - 2026-09-24

- The Nightcross JAR inside the source ZIP matches the separate original mod
  directory byte-for-byte (SHA-256
  `31e7706c6719cf1304bb3aea3e5246242224560ea4c5f6d8c356afe3ed97f6b4`).
  The CRC defect was present in both supplied copies. Historical authority and
  a safe replacement or repair remain unverified.
- The two recorded archive-assessment surprises were reviewed. The hash-bound
  local packet now passes `attempt-feedback` as `READY_FOR_NEXT_CANDIDATE`;
  this only closes feedback for that assessment attempt. P3 Nightcross coverage,
  a current translated candidate, final package audit, game scenarios, rights,
  and release finalization remain open. See `ROADMAP_PHASE_STATUS_2026-09-23.md`.

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

### P1-P5 review follow-up — 2026-10-20

The review repairs restore the fingerprint API, remove filename/count-based evidence claims, verify ZIP cache contents
before reuse, tighten final archive/manifest comparison, and preserve unknown runtime exits as null. Regression names,
validation and remaining acceptance gates: [P1_P5_REVIEW_FIXES.md](P1_P5_REVIEW_FIXES.md).
P3's fixture-first rule for newly accepted formats remains open. The owner explicitly left P5 live tests pending;
no campaign, combat, persistence or upgrade checkbox is closed by these offline repairs.

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
  archive-assessment packet records two surprises and was reviewed on
  2026-09-24 as `READY_FOR_NEXT_CANDIDATE`. Archive that local packet with
  future candidate evidence; continue to review each later attempt separately.
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

## P7 — adopt BridgeForge's translation method (implemented 2026-10-05)

Owner request: one translated mod, with its language replaced in place, using BridgeForge's method
(`bridgeforge/translation.py` is the reference). Design, evidence and the interchange contract are in
[BRIDGEFORGE_METHOD_CONVERSION.md](BRIDGEFORGE_METHOD_CONVERSION.md). Measured on FlowerGod 1.1.9 (2026-09-29):
Project Go skipped 5 of the 101 files where BridgeForge found Chinese text, 108 of its 1,296 entries.

1. [x] Import and export BridgeForge's translation document (schema v1: `entries[id,file,kind,context,source,
   translation]`, `glossary`, `file_hashes`). Exit: a document exported by either tool imports into the other, and
   applying it gives identical bytes on the shared conformance fixtures.
2. [x] Lenient unit discovery, with no normalisation and no schema gate on text:
   - CSV raw cell spans, including padded and short rows;
   - JSON-like tokenizer with comments, single quotes, barewords and object keys as `json-key` units;
   - loose `.java` literals outside `src/`;
   - jar `CONSTANT_String` literals;
   - excluded: `src-decompiled*/`, `build/`, `reports/`, `scratch/`, `disabled_files/`.

   Exit: zero units that BridgeForge finds and Project Go misses, on the fixture corpus and on FlowerGod
   (`trapNames.csv`, `reports.csv`, `channels.json`, `FG_shipblackList.csv`, `mod_info.json`). Differences in
   either direction are recorded with a reason.
3. [x] Span-exact apply, with `--out DIR` or `--in-place` on a working copy the user designates. Re-verify CSV row
   shape, JSON-like parse and class-file parse, and refuse on a source-hash mismatch. The input mod is never
   edited. Exit: FlowerGod's document applied in place matches BridgeForge's result byte for byte.
4. [x] Jar constant-pool rewrite parity (mUTF-8; referenced literals only), plus aligned-reference prefill (at least
   90% class-layout match) and zh/en translator-record prefill. Exit: the Nightcross record fixture prefills the
   same entries in both tools.
5. [x] Placeholder parity:
   - `%` conversions without the space flag;
   - ASCII `$variables`, dotted only before a name;
   - ``;
   - `%%` treated as literal (a full-width `％` becomes `%%` in `hull_mods.csv`).

   Also leftover-CJK check parity, including jar strings. Exit: the BridgeForge placeholder fixtures pass unchanged.
6. [x] Launcher paths: quote `JAVA_HOME` and `APP_HOME` in `ssmt-cli.bat` (it fails when `JAVA_HOME` contains a
   space, 2026-09-29), and accept non-ASCII mod paths (`?` substitution, 2026-09-13). Exit: CLI tests from a
   spaced `JAVA_HOME` and a CJK path.
7. [x] Retire the duplicated extraction path: after items 2 to 5 pass, schema catalogs become advisory metadata
   only. GUI, Auto and TM are unchanged. Exit: the full suite, source-immutability checks and packaged GUI/Auto/CLI
   acceptance all pass.

Shared fixtures: `fixtures/translation-conformance/` with a `VERSION` file, copied into both repositories. A method
change lands in both, each with a fixture that fails first.

**Done 2026-10-05 (implementation and bounded acceptance).** See
[P7_IMPLEMENTATION_STATUS.md](P7_IMPLEMENTATION_STATUS.md) for exact receipts and
limitations. The full suite and shared/real-mod parity pass. Packaged CLI CJK
paths, GUI engine and native Auto ASCII-path workflow pass. P8.4 now closes native
Auto CJK command arguments with packaged evidence; interactive GUI chooser/drop
and Explorer drop remain review items. P1-P6 live,
rights, exact-release and publication gates remain unchanged.

## Sister repository interoperability (proposed 2026-09-27)

Design and acceptance gates: [SISTER_REPO_INTEROPERABILITY_DESIGN.md](SISTER_REPO_INTEROPERABILITY_DESIGN.md). These are future work, separate from the open P1-P6 validation gates.

1. [ ] Export a versioned localization coverage manifest for BridgeForge with exact source hash, file-level coverage/skip reasons, and partial-integrity status; use neutral fixtures and never infer complete coverage from absent entries.
2. [ ] Map existing release checks to a source-to-published-asset evidence contract; add only missing fields and keep packaged native, live, rights, and publication gates separate.

**Implementation checkpoint 2026-09-27:** `ssmt assess --coverage --coverage-manifest FILE` writes versioned, text-free coverage evidence outside the candidate. Its per-file handling and aggregate supported/skipped-file and extracted-string counts are observed; total supported strings remain unknown. `ssmt release-chain --source-record FILE --archive FILE [--downloaded-asset FILE]` reports source-record, package, and optional asset hashes with independent unknown gates. The release workflow now generates chain reports for distribution ZIPs and compares downloaded published assets byte-for-byte after upload. CLI tests cover overwrite refusal and mismatched assets. A hosted workflow run, native interactive testing, and rights evidence remain open; both items stay unchecked.

## P8 - GUI, workflow, and gap coverage (approved 2026-10-08)

Owner approved the recommendations following the extractor comparison. Implement
these slices in order; automated completion and visual/runtime acceptance are
independent. The normal flow keeps original mods read-only and publishes one copy.
Acceptance details: [GUI_WORKFLOW_ENHANCEMENTS.md](GUI_WORKFLOW_ENHANCEMENTS.md).

1. [x] **Remembered installation:** install to the validated remembered destination;
   offer Change destination separately; prompt when absent or unusable.
   Benefit: repeat installs take one click.
2. [x] **Review Changes:** show changed source, previous translations, conflicts,
   missing translations and validation findings in the normal flow; preserve history
   and prevent unresolved validation from being called ready.
   Benefit: users can resolve refresh problems without hunting through Advanced.
3. [x] **Output quality:** show remaining CJK units and unreadable inputs by file,
   retain evidence internally, and distinguish publication from translation quality.
   Benefit: exposes missed text before a mod is used.
4. [x] **Native Auto Unicode:** fix CJK arguments in the executable; add a packaged
   Unicode-path export/import/build regression with source hashes.
   Benefit: removes the ASCII-copy workaround.
5. [ ] **Native GUI acceptance:** maintain a chooser/drop/renamed-response/rejection/
   cancellation checklist and record visual observations. A checklist alone is not PASS.
   Benefit: verifies Windows interactions beyond engine tests.
6. [x] **Progress and cancellation:** show operation stages and cooperative Cancel;
   stop before commit/publication at safe boundaries, preserve active work, and let
   an already-started atomic publication finish.
   Benefit: large-mod operations remain understandable without damaging output.
7. [x] **Advanced extractor comparison:** choose source/reference and display
   differences by file/reason through the existing service.
   Benefit: makes coverage investigations accessible without CLI knowledge.
8. [x] **Diagnostic export:** export application version/commit, operation, findings
   and only user-selected logs; refuse overwrite and source-contained destinations.
   Benefit: reproducible bug reports with less manual collection.
9. [x] **Installed-copy verification:** retain a publication inventory internally,
   compare later bytes/paths, and provide bounded game-test instructions.
   Benefit: detects post-build edits and connects installation to runtime gates.
10. [ ] **JSON-key policy:** classify displayed labels versus identifiers on neutral
    fixtures; preserve direct schema-v1 parity and protected game-loading fields.
    Benefit: reduces accidental translation of identifiers.
    Implemented advisory: all discovered JSON keys are visibly role-UNKNOWN;
    direct interchange stays unchanged. Candidate consumer evidence is still needed.
    G2.4 (2026-10-18): a key that a value, CSV design-type/groupTag cell or code literal repeats is now LINKED with them and
    translated together; `id`/`*Id`/`tags` fields are IDENTIFIER (see `unit-roles.json`). A bare key that nothing uses stays UNKNOWN
    (BridgeForge conformance translates every key); flip `bare_json_keys` to IDENTIFIER once BridgeForge agrees. Not yet closed.
11. [ ] **Interoperability acceptance:** exercise the existing coverage-manifest and
    release-chain contracts, including hosted workflow/downloaded-asset evidence,
    before closing the sister-repository items above.
    Benefit: closes evidence gaps without duplicate implementations.
    Branch CI now runs native Auto Unicode and retains source/package chain reports;
    sister-tool joins and actual published/downloaded assets remain acceptance gates.
    Incremental checksum freshness is fixed: ZIPs are declared checksum inputs,
    and `check` verifies SHA256SUMS against the actual current distribution bytes.
12. [ ] **Candidate acceptance:** execute existing P5/P6 campaign, combat,
    persistence, rights and final-package gates on an authorized candidate.
    Benefit: ties readiness to exact bytes and observed behavior.

### Reconciled earlier proposals

**Local implementation checkpoint 2026-10-08:** eight implementation slices above
passed 610 tests, Checkstyle/SpotBugs, Unicode CLI verification, rebuilt native
GUI/Auto smoke tests, native Auto Unicode exact-output/source-hash acceptance,
SBOM/checksums and archive checks. All 118 Gradle tasks executed against a fresh
profile with no build cache. Implementation commit `55bf2f8` subsequently passed
Windows and Ubuntu [CI run 37876311841](https://github.com/Exxec/Project-Go/actions/runs/37876311841),
with uploaded branch evidence. Visual, candidate and release gates remain open.

- Extractor cross-check: implemented by `translate compare` on 2026-10-08;
  [EXTRACTOR_COMPARISON.md](EXTRACTOR_COMPARISON.md) defines its observed-unit scope.
- Tolerant CSV/JSON-like discovery and span-exact apply: implemented in P7.
  Future misses belong to P3's minimal-fixture gate, not a second parser.
- Spaced JAVA_HOME and CLI CJK paths: covered by P7's launcher regression.
  P8.4 separately verifies native Auto Unicode command arguments; Explorer
  drag/drop still requires visual acceptance.
- JSON-key discovery: implemented in P7; displayed-label versus identifier policy
  remains P8.10, rather than a missing extraction implementation.
- Leftover CJK scanning: exists in direct apply/check; P8.3 exposes its quality
  boundary and durable findings to the normal workflow.

P1-P6 manual, candidate, rights and release gates remain authoritative. P8 does
not imply a new release, live compatibility, or permission to redistribute mods.

**Owner confirmation 2026-10-09:** the latest version was live-tested, including
save/reload. This is recorded as owner-reported acceptance in
[0.8.0-rc.2 release notes](RELEASE_0.8.0_RC2.md). Candidate identity and executable
hash were not supplied, so the historical per-candidate evidence gates remain
separate from that confirmation. Version 0.8.0-rc.2 packages the current code.

## Lessons carried over from BridgeForge's hardening (item 70) and regression pass (2026-10-12; proposed 2026-10-12, not an owner-approved plan; the in-place item is now done, see below)

Source: BridgeForge `ROADMAP.md` item 70, `docs/HARDENING_PLAN.md`, `docs/RC8_BEHAVIOUR.md` (RC8-30 to RC8-33). Each line says what it would change here and what it buys; only the in-place item below has since been built (automation plan G0.1).

- **Atomic writes and guarded deletes.** Write through a temp file and `os.replace`; delete a tree only inside an allowed root and never through a junction (BridgeForge `safeio.py`, with a ratchet test whose baseline may only shrink). Benefit: an interrupted write or a wrong path cannot leave a half-written or vanished file.
- **Bounded reads of untrusted archives.** Cap a zip or jar entry (200 MB; class files 32 MB) before reading it (`zipsafe.read_limited`); a hostile or corrupt archive then fails that entry instead of exhausting memory.
- **Parse structured files with a real (lenient) parser, never a regex for the first field.** BridgeForge's master log took the first `"name"` and `"version"` in each `mod_info.json` and listed 17 mods as "LazyLib" with LazyLib's version, because those files list dependencies first. Benefit: wrong labels and versions stop appearing in reports.
- **Guard the contract, not just the code.** A hash of every command's `--help` (a golden test) let agents split a 4,100-line `cli.py` and an 8,800-line `scanner.py` with the interface unchanged; a known-failure file with an expiry keeps an environmental failure from hiding new ones; baselines of accepted findings expire when the mod version changes.
- **Check an agent's branch before merging it** (`agent-diff-check`: files deleted or cut by more than a fifth) and rerun the whole suite on the merged tree; do not edit tracked files while the suite runs (its hermeticity check reports the checkout as changed).
- **Line endings and backups.** LF everywhere (`.gitattributes`; batch files CRLF). An append-only snapshot of the irreplaceable state is taken after each major new revision, not on a weekly clock (owner rule 2026-10-12).
- **Say PREDICTED or OBSERVED.** BridgeForge keeps a prediction ledger: of two forecasts about a mod pair, one held live and one was refuted. Record the method, date and run id with each fact.
- **Paths.** Fixed here: the launcher passes arguments through a UTF-8 argfile and quotes `JAVA_HOME`, so non-ASCII paths and a `JAVA_HOME` with spaces work (gates `verifyWindowsLauncher` and `verifyNativeUnicode`, `ssmt-cli/build.gradle.kts`). BridgeForge's `rig-doctor` still warns about non-ASCII paths and a disabled Windows long-path setting on its side; check both before an in-place run there.
- **In-place translation (`translate-apply --in-place`) should use the atomic write and a pre-apply snapshot** of the target mod, and read mod archives through a size-capped reader. The atomic write, snapshot and `translate restore` are done (G0.1); the size-capped reader is not part of that change.
- **Read a name or version with a JSON parser, not a regex over the whole file** (see above); use a lenient parser for `mod_info.json`.
- **Long runs** should print one progress line per item, offer `--quiet`, and resume from a checkpoint (BridgeForge rule since 2026-09-27); `translate-batch` should follow it.
- **Conformance fixtures keep their own line endings.** BridgeForge's LF policy exempts `fixtures/translation-conformance` through its own attributes; do the same here so the golden files do not change.

## Automation plan (2026-10-14; implemented 2026-10-14 to 2026-10-18 and merged to `main`)

Owner request 2026-10-14: further automation, fixes, checks and less hand work, written so any AI can implement it. Spec:
`docs/AUTOMATION_PLAN.md`. Phases: G0 atomic in-place apply with a pre-apply snapshot and restore, the stale ASCII/`JAVA_HOME`
line above corrected, golden `--help` and line-ending guards; G1 coverage the user cannot see (mission and string `.txt`, loose
`.class`, unreadable files no longer stop a project, a reason for every leftover unit, custom dialogue CSVs); G2 identifiers found
by the tool (unit roles TEXT/IDENTIFIER/LINKED, linked groups translated together, bytecode allowlist proposed from call sites,
closes P8 item 10); G3 approval by rules (glossary from the mod and TM, per-unit policy approval, ranked fuzzy/prefill matches, API
round trip in Auto); G4 gates that run themselves (candidate ZIP audit, BridgeForge run records as runtime evidence, GUI checklist
screenshots). Owner decisions 2026-10-14 ('follow your recommendations') are recorded in the plan. Same day: `AGENTS.md` (agent contract, version 1) and a pointer `CLAUDE.md` added.

Status (one commit per item on `main`):

- **G0.1 Done 2026-10-14.** `TranslationSnapshot`, atomic writes in `BridgeForgeTranslationApply`, `translate restore`. Tests: `BridgeForgeInPlaceSnapshotTest`, `BridgeForgeTranslationRestoreCommandTest`.
- **G0.2 Done 2026-10-14.** The stale ASCII/`JAVA_HOME` line above now states the fix.
- **G0.3 Done 2026-10-14.** `CliGoldenHelpTest` pins a hash of every command's `--help` (`ssmt-cli/src/test/resources/golden-help.tsv`; accept a deliberate change with `gradlew :ssmt-cli:writeGoldenHelp` and review the diff). `.gitattributes`: LF everywhere, `.bat`/`.cmd` CRLF, `fixtures/translation-conformance/**` exempt. `config/known-failures.tsv` (`Class#method`, expiry date, reason) is read by `KnownFailureExtension`, auto-registered in every module through `config/test-support/`; a listed, unexpired failure is reported as aborted with its reason, an expired or unlisted one still fails. Tests: `CliGoldenHelpTest`, `KnownFailuresTest`, `KnownFailureExtensionTest`.
- **G1.3 Done 2026-10-18.** `createUsingBridgeForge` continues with the readable files and lists each unreadable file with its error in the project's method document; a build refuses until the list is acknowledged (`LocalizationProjectService.acknowledgeUnreadable`, `project create --accept-unreadable`); `translate check` exits 2 until `--accept-unreadable`. No second "lenient retry" was added: the shared readers already are the lenient ones (raw CSV cells, comment/bareword JSON), and the remaining failures are undecodable bytes. The GUI checkbox was added afterwards (see the GUI/Auto follow-up line below). Tests: `UnreadableInputProjectTest`, `BridgeForgeTranslationCheckCommandTest`.
- **G1.4 Done 2026-10-18.** `LeftoverClassifier`: reasons UNSUPPORTED_TYPE, UNREADABLE, UNTRANSLATED, REJECTED in the apply report (`leftover`, only when non-empty) and `translate check --json FILE`. PROTECTED_IDENTIFIER stays reserved for G2. GUI Output quality view not changed (NOT_TESTED, needs a GUI session). Tests: `LeftoverClassifierTest`, `BridgeForgeTranslationCheckCommandTest`.
- **G1.1 Done 2026-10-18.** Kind `text-line` (one unit per CJK line, id `text:<file>@<line>`) for `.txt` in `data/missions/<name>/` and under `data/strings/`; same decoding as the legacy plain-text extractor (UTF-8, GB18030 fallback, BOM kept). Deviation: the new kinds `text-line` and `class` are accepted by this repository's document validator only; BridgeForge must add them before it can read such exports, and the conformance fixture was left unchanged (no `.txt` in it) so both sides still agree. Tests: `TextLineUnitsTest`.
- **G1.2 Done 2026-10-18.** Loose `.class` files are read like jar entries (kind `class`, id `class:<file>#<cp_index>`, referenced `CONSTANT_String` only, same 64 MiB cap via `BridgeForgeClassFile`); excluded folders (`src`, `build`, ...) still skipped. Tests: `LooseClassUnitsTest` (uses the conformance jar's synthetic `Fixture.class`).
- **G1.5 Done 2026-10-18.** A CSV of unknown schema is already exported cell by cell; new here is syntax protection: a cell in `id:label|id:label` form must keep the same ids, order and `|` separators in its translation (otherwise refused with "option syntax differs"), on top of the existing `$variable`/`%s` placeholder check. The real `zgrstuff.csv` syntax is unproven (runtime use unresolved per `RESIDUAL_CHINESE_DIAGNOSIS.md`), so the rule follows the plan's description with a synthetic fixture only; confirm against the real file with a private run. Tests: `CustomOptionCsvTest`.
- **GUI/Auto parts of G1.3 and G1.4 Done 2026-10-18.** The normal GUI shows the unreadable list (PARTIAL) with an acceptance checkbox that gates installing (`TranslationWorkflow.acknowledgeUnreadable`, persisted in the workspace), and the Output quality line carries leftover reasons (`InstalledCopyEvidence.Quality.reasons`). Auto stops with `UNREADABLE_INPUTS_NEED_ACCEPTANCE` until run with `--accept-unreadable`. Tests: `WorkflowUnreadableAcknowledgeTest`, `UnreadableAcknowledgementTest`, `UnreadableInputAutoTest`. The checkbox itself is a JavaFX control exercised only by compilation here: NOT_TESTED on a real window.
- **G2.1, G2.2, G2.4 Done 2026-10-18 (G2.4 partly, see P8 item 10).** `UnitRoleClassifier` + data rules `ssmt-project/src/main/resources/com/ssmt/project/unit-roles.json` give every unit TEXT, IDENTIFIER, LINKED or UNKNOWN (CSV columns `id`, `*_id`, `tags`, `hints`, `groupTag`, `design type`; a column whose values are all ids elsewhere; JSON `id`/`*Id`/`tags`; a map key or class/jar literal sharing a value with other units forms a LINKED group). Export fields `role`, `role_rule`, `group` (non-text only; `exportWithRoles`). Apply refuses to translate an IDENTIFIER, applies one text to a whole LINKED group (a member without text takes the group's), refuses inconsistent groups and two groups ending with one text (`LinkedGroupEnforcer`); the project layer offers no IDENTIFIER entries, and import refuses inconsistent or colliding linked translations. `LeftoverClassifier` now yields PROTECTED_IDENTIFIER. Deliberate behaviour changes: `tags`/`groupTag` cells are no longer offered (WeaponTooltipWorkflowTest 7 to 5 entries) and one conformance JSON key is LINKED rather than UNKNOWN (WorkflowEnhancementsTest 2 to 1). Group members stay separate entries (the plan said one entry per group): collapsing them would change the 18-entry conformance export; the same-value rule makes one translation apply to all instead. Tests: `UnitRoleClassifierTest`, `UnitRoleIntegrationTest`.
- **G2.3 Done 2026-10-18.** `BytecodeCallSites` reads classes with ASM (never loads them) and gives every CJK literal a verdict from the first method call after it is loaded, by data rules in `unit-roles.json` (`call_sites`): PROTECT (settings/memory/map lookups: the unit becomes an IDENTIFIER in export and apply), TEXT (display calls), REVIEW (everything else, including string compares and literals stored without a call). `translate bytecode-audit <mod> --out proposal.tsv [--json report.json]` writes a proposal in the real `ssmt-bytecode-allowlist-v1` format using the legacy extractor's keys: only TEXT rows are active, PROTECT/REVIEW rows are comments. Limits: the call that follows is a heuristic (a literal stored in a variable or field is not followed), and the rules list is small; extend it from real mods. Tests: `BytecodeCallSitesTest` (ASM-generated classes, legacy key parity, loadable by `BytecodeTextAllowlist`), `BridgeForgeTranslationCheckCommandTest`.
- **G3.2 Done 2026-10-18.** `PolicyApproval` (`approval-rules.json`, rules version `policy-1`) and import policy `APPROVE_BY_RULES`: per unit, TEXT only, validators (already fail-closed in the import), line breaks, glossary terms, no CJK left, length ratio per kind; passing units become provenance `POLICY_APPROVED` (new `TranslationProvenance`, ranked like AI in the TM, never counted as a human-approved TM entry), others stay `AI_TRANSLATED` drafts with the failed rule in `policyReview`; `PolicyApproval.revoke` reverses. The normal GUI/Auto/workflow import now uses it (`SharedTranslationWorkflowService`); CLI `project import-ai-response --approve-by-rules`. `ARCHITECTURE.md` AI Boundary updated in the same change. The GUI review view lists POLICY_APPROVED through its provenance filter; a one-click revoke button is NOT built (service call only). Tests: `PolicyApprovalTest`.
- **G3.1 Done 2026-10-18.** `GlossaryService`: approved terms = user approvals (per mod `glossary.json` in the workspace, global `global-glossary.json`; the mod wins) + names the mod spells in English in a parallel CSV row (same row id and column in another file; origin MOD_ENGLISH) + units with exactly one human-approved translation in the library (origin TRANSLATION_MEMORY, read only); disagreeing sources become a CONFLICT proposal. Units repeated 3 or more times are only proposed (REPEATED). Approved terms that occur in the mod fill the method document's `glossary{}` (so they reach the AI request and the policy rules) when a mod is loaded or refreshed in the GUI workflow and in Auto. CLI `translate glossary` / `glossary-approve`. GUI: "Glossary terms..." dialog with one-click approval (NOT_TESTED on a real window; the controller is tested). Not done: counting repeats through `TerminologyConsistencyAuditor` (it audits translated entries, so it cannot run before translation; plain counting is used) and fuzzy suggestions for proposed terms. Tests: `GlossaryServiceTest`, controller test in `UnreadableAcknowledgementTest`, CLI test.
- **G3.3 Done 2026-10-18.** `CandidateRanker` + data `ranking-rules.json` (weights same_file 0.30, same_field 0.25, neighbours 0.20, similarity 0.25; threshold 0.85, margin 0.05, floor 0.60; an unmeasurable feature is left out and the weights renormalised). `MemoryMatchSuggester` ranks conflicting exact and fuzzy human-approved library matches for blank entries; Auto applies the leader as POLICY_APPROVED only when the score passes the threshold, the margin holds and every approval rule passes, and writes every proposal with its score breakdown to `suggestions.json` beside the project. Deviation: a fuzzy match (different source text) is only proposed unless `auto_apply_fuzzy` is set to true in the config, because a similar sentence is not the same sentence (numbers differ); the plan's threshold applies to conflicting exact matches by default. Ambiguous author-record prefill is ranked by neighbouring units and file-name overlap (`candidates_ranked`, `suggestion`, `record-ranked` when applied). Tests: `CandidateRankingTest`, `RankedMatchAutoTest`.
- **G3.4 Done 2026-10-18.** `AutoWorkflow.aiProvider/confirmAiSend`, `ai-provider.json` beside the master library (type, baseUri, model, credentialEnvironmentVariable name; no key in the file), `--confirm-ai-send`. Off unless configured; with a provider but no confirmation Auto reports provider and string count and sends nothing (`AI_SEND_NEEDS_CONFIRMATION`). `AiRoundTrip` sends 20 strings per chunk, one progress line per chunk, `ai-checkpoint.jsonl` header names the inputs (provider + pending keys) so a stopped run resumes (`AI_ROUND_TRIP_INTERRUPTED`), deleted when done. Results are applied only as POLICY_APPROVED after the protected-syntax validators and every G3.2 rule; the rest stay untranslated and are listed in `ai-review.json`. Deviation: the plan's "GUI says which provider and how many strings" is done in Auto (the result text and log) because only Auto has the round trip; the GUI has no provider settings storage. Real providers (Ollama/OpenAI/Gemini) were not called: `NOT_TESTED` against a live endpoint. Tests: `AiRoundTripAutoTest` (fake provider: no send without confirmation, chunks and progress, resume after a failure, rule failures listed).
- **G4.1 Done 2026-10-18.** `CandidateArchiveAudit` and `ssmt audit-candidate ZIP --manifest installed-copy.json --ledger LEDGER [--expect-id ID] [--expect-version V]`: bounded, traversal-safe extraction (`ModInputPreparationService`), every file hash compared with the publication manifest the build wrote (`installed-copy.json` in the workspace; this is the "apply manifest"), leftover check on the extracted tree, mod id and version read. Writes PACKAGE_IDENTITY (PASS, or FAIL on any added/missing/changed file or id/version mismatch) and STATIC_VALIDATION (PASS, REVIEW_REQUIRED for leftovers or unreadable inputs, NOT_TESTED after a package failure) into an assurance ledger bound to the extracted candidate's inventory fingerprint, with `candidate-audit.json` as the hashed evidence beside it; an existing ledger of the same candidate is merged. Exit 0 both PASS, 2 review needed, 1 failure. Deviation: a new command instead of an `assess` option, because `assess` is read-only inventory and the ledger needs its own writer. Bytes and static text only; no game behaviour is claimed. Tests: `CandidateArchiveAuditTest`, `AuditCandidateCommandTest`.
- **G4.2 follow-up 2026-10-18 (`fix-import-run-schema`):** `RunRecordImport` now reads BridgeForge's real R1 record shape (milestones under `evidence.milestones`, identity from `manifest` and `core_baseline_sha256`, `evidence.status` ACCEPTABLE required, triage and `jvm_crashes` for failures); the test fixture is a record BridgeForge's `write_run_record` produced for a synthetic tree, and the Java shipped-hash port matches it. BridgeForge does not record a Java version or a process exit code, so all three gates stay NOT_TESTED with that reason until it does. The paragraph below describes the first version and its guessed field names, now superseded.
- **G4.2 Done 2026-10-18 on the Project Go side; LIVE PENDING on the BridgeForge side.** `assurance --candidate DIR --import-run <queue>/_live/<TESTID>.json --ledger-out LEDGER` (`RunRecordImport`, `AssuranceLedgerWriter`, `BridgeForgeShippedHash`). A record is evidence only when it has a `manifest_id` (BridgeForge item 75 R1), a member with provenance `BOUND` whose `shipped_sha256` equals the candidate's BridgeForge shipped hash (the copy-drift file-set hash, ported to Java and pinned against a value BridgeForge computed on a synthetic tree), and the launch environment (Starsector build, Java, process exit code). Legacy records (today's format, no manifest or provenance), unbound members, other bytes or missing environment leave AUTOMATED_BOOT, CAMPAIGN and SAVE_RELOAD NOT_TESTED with the reason. For a matching BOUND member: boot PASS when the main menu was reached with no FATAL, MOD-ERROR, JVM crash or non-zero exit (FAIL on a FAIL verdict or those signs), CAMPAIGN PASS when a campaign loaded, SAVE_RELOAD PASS only with a save and an explicit `milestones.reload_after_save` (the log counters alone cannot show a reload). The record is copied verbatim beside the ledger as the hashed log of a schema-2 runtime capture (`runtime-<TESTID>.json`), so `AssuranceLedgerReader` verifies it. Caveats: R1 is specified but not built in BridgeForge, so the field names `manifest_id`, `members[].provenance`, `milestones`, `environment` are this side's reading contract and must be reconciled when R1 lands; no real BOUND record exists to test against. Tests: `BridgeForgeShippedHashTest`, `RunRecordImportTest`, `AssuranceImportRunCommandTest`.
- **G4.3 Done 2026-10-18 (harness; owner marks the rows).** `GuiChecklistTest` + `gradlew :ssmt-gui:guiChecklist`: the real `TranslationWorkflowPane` in a real JavaFX window, one screenshot per step in `ssmt-gui/build/gui-checklist/`, `checklist.json` with what was asserted. Run here on 2026-10-18 on a Windows desktop: 10 rows AUTOMATED_PASS (drops of folder/ZIP/mod_info.json, Unicode folder, renamed response, malformed response keeps the project, changed source held for review, first install remembers its destination, a different destination, quality plus a later edit named, a missing remembered destination falls back, and the unreadable-input checkbox), 5 NOT_AUTOMATED with reasons. Deviation: plain JavaFX `Node.snapshot` instead of TestFX/Monocle: no new dependency, and Monocle's latest build (21.0.2) targets JavaFX 21, not the 25 used here; so no headless CI run (Ubuntu runners have no display). Production change: the pane takes an injectable controller and a few methods are package-private for the harness. Not covered: native file choosers, the error dialog (it writes a diagnostic into the user's app data), Explorer drops, cancellation timing.

## G2 safety follow-up (2026-10-19)

Owner request: "review roadmap then begin working on next phase. set as goal and if possible delegate to lowest recommended agent. optimize token usage plase."
The next implementation slice closes two independently reproduced G2.1/G2.2/G2.3 defects before new schema or runtime work.
It does not approve the proposed recommendations below or close P8.10.

- A class constant with any detected lookup-key use stays IDENTIFIER even when other sites display it; the bytecode allowlist
  proposal excludes every site of that same file/class/text constant. Synthetic loose-class and jar tests verify export,
  source-preserving apply, protected leftovers, proposal exclusion and independence from scan order (`BytecodeCallSitesTest`).
- A format-specific rejection of a LINKED member must refuse the operation before publication, including existing JSON-key
  collisions and invalid protected syntax. Regression coverage: `LinkedGroupCollisionSafetyTest`.
- Validation receipt and remaining checkout blockers are recorded in `G2_SAFETY_VALIDATION.md`. This is static translation
  safety work: LIVE TEST NOT PERFORMED; runtime, native visual acceptance and cross-repository schema gates remain open.

## Schema alignment follow-up (2026-10-19)

Owner: "If able continue same process in next phase." This authorizes the next translation-schema slice; the remaining
recommendations below stay separate. Schema v1 is retained with additive text-line/class kinds and optional role/group
metadata. The shared contract and fixtures are in `fixtures/translation-conformance/SCHEMA_V1_EXTENSIONS.md`.

BridgeForge now discovers/applies/checks these kinds, checks source identities, consumes declared roles, protects
IDENTIFIER units and stages complete linked copies before publication. Its linked in-place path is explicitly refused;
it does not implement Project Go's classifier. Project Go's exchange validator rejects malformed role/group metadata.
`TranslationExtensionConformanceTest` exercises both new shared fixtures and compares exact exported units/hashes/output
bytes. The original 18-unit corpus is unchanged. Source/static/package evidence: see `SCHEMA_ALIGNMENT_VALIDATION.md`.
Real-mod acceptance, runtime records, native owner GUI review, release approval and P8.10 remain open.

## Remaining near-term follow-ups (2026-10-19)

Owner: "Repeat for all next phases if able." Feasible implementation and evidence collection proceeded in recommendation
order; the local-only report is `REMAINING_PHASES_STATUS.md` (kept outside version control).
Runtime context fields and explicit capture are implemented and synthetic-tested, with unknown exits left null. The
real-mod task measured FlowerGod at 908/1,620 policy approvals, with 712 review items and unchanged source. Its real build
is BLOCKED_REVIEW. The GUI harness refreshed 10 automated passes and 5 non-automated rows; all 15 owner reviews stay
NOT_TESTED. Static inventories do not establish JSON-key consumers, so P8.10 stays open. The unfinished runtime capture
files were backed up and their compile/publication defects repaired. No live test, owner review or release was invented.

## Recommendations (2026-10-18; individual near-term follow-ups authorized above; long-term remains proposed)

Follow-up 2026-10-20: G3.1 auditor-based glossary counts and G3.2 durable GUI revocation are implemented and validated.
The grouped real-mod review packet, broader Nightcross CRC diagnosis, refreshed GUI checklist and cross-tool/hosted
evidence are recorded in [NON_LIVE_ROADMAP_FOLLOWUP.md](NON_LIVE_ROADMAP_FOLLOWUP.md). Nightcross remains blocked by owner
choice; missing translation drafts, JSON-key consumer proof, owner GUI review and final release gates remain explicit.

Written after the automation plan (G0-G4) merged and CI went green on Windows and Ubuntu. The owner asked for
recommendations and long-term recommendations. The near-term follow-ups are authorized as recorded above; long-term
directions remain proposed. `AGENTS.md` and the
`ARCHITECTURE.md` boundaries bind all of them.

### Near term: close the cross-project loops and get real-mod evidence

1. **Schema-v1 alignment with BridgeForge.** Project Go now exports unit kinds `text-line` and `class` and fields
   `role`/`role_rule`/`group`; BridgeForge's `translate-apply` must accept them, with shared conformance fixtures, before the two
   tools can exchange such documents (recorded in BridgeForge's `docs/ROADMAP_STATUS.md`). Agree the schema change once,
   in both repositories, rather than each side extending v1 alone; consider calling it schema 2.
   Implemented offline 2026-10-19 as the additive schema-v1 extension profile above, with explicit conservative refusal
   boundaries. No schema-2 migration is needed for this backward-readable profile; older consumers lacking these kinds
   must not be used to apply it.
2. **Runtime evidence from BridgeForge.** `assurance --import-run` reads BridgeForge's real run record, but every gate stays
   NOT_TESTED until BridgeForge records `manifest.java_version` and a `process_exit_code` (BridgeForge follow-up). Once it
   does, one bound BridgeForge live run of a translated mod closes AUTOMATED_BOOT and CAMPAIGN for that candidate.
3. **One real translation end to end with the new rules**: a Chinese mod through roles, glossary, policy approval and the
   candidate audit, including a `zgrstuff.csv`-shaped file and `translate bytecode-audit` on its jars. Record how many units
   policy approval took and how many went to review: that ratio is the measure of the automation's worth.
4. **Owner GUI pass** over the 15 checklist rows using the screenshots `guiChecklist` produces, then mark them; then decide
   rc.4 (or 0.8.0 final), since `main` now carries 20+ feature commits beyond `v0.8.0-rc.3`.
5. **Close P8.10** (JSON key labels vs identifiers) together with BridgeForge, using `bare_json_keys` in `unit-roles.json`.

### Long term: what Project Go should become

1. **A translation memory that compounds.** Each approved translation should make the next mod cheaper: shared glossary
   terms by faction and mod family, ranked reuse, and a measured review rate that falls over time. Track that rate per mod.
2. **Identifier safety as a proven property.** Grow `unit-roles.json` from real-mod evidence (each rule with its fixture) until
   no translated build changes a lookup key; make "no identifier changed" a release gate checked against the source build.
3. **One translation method with BridgeForge, two front ends.** Keep a single versioned exchange schema and shared
   conformance fixtures; Project Go is the player-facing GUI/Auto tool, BridgeForge the revival pipeline. Neither should
   drift into the other's job.
4. **Translated builds that survive mod updates.** When a mod updates, re-apply approved translations by stable identity and
   send only changed units for review, with the save-safety rules of the translated copy kept.
5. **Optional AI, never required.** Keep extraction, validation and builds offline; AI stays an opt-in draft source behind
   policy approval and owner review.
