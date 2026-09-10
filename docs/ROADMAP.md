# Project Go roadmap

This roadmap turns verified failures and real-mod lessons into implementation
gates. A checked item requires reproducible evidence; a successful extraction,
compile, or main-menu boot must not be promoted into a broader compatibility or
release claim.

## Review checkpoint — 2026-09-10

- The working tree was clean after refreshing `origin`.
- `HEAD`, `origin/main`, and the peeled `v0.7.0` tag all resolve to
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
  already exists. `SqliteTranslationMemory.open()` currently opens the SQLite
  path without first creating its parent.
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
- [ ] Publish a new version rather than moving the existing `v0.7.0` tag. Require
  exact commit/tag/version agreement, green tag CI, launcher and executable smoke
  tests, SBOM/checksums, and a GitHub Release whose assets match those checksums.
- [ ] Update release documentation only after the observable remote evidence is
  complete.

Exit criteria: a fresh user profile passes the full suite on both supported CI
systems, and a new release can be traced from source commit to tag, workflow,
artifact hash, embedded version/commit, and published release asset.

## P1 — one simple workflow and file model

Implement [the simple file workflow](designs/simple-file-workflow.md) before
adding more normal-path controls.

- [x] Replace the four always-visible normal actions with a three-stage
  choose/translate/install presentation and one context-sensitive primary action.
- [x] Move Auto's sibling project/state/extraction layout into the same internal
  application-data workspace used by the normal GUI.
- [x] Let the desktop import an AI response under any filename, using its embedded
  identity and integrity fields instead of its path.
- [ ] Extend filename-independent response import to Auto; it still watches for
  one documented response name beside the selected input.
- [ ] Remember one validated Starsector `mods` destination instead of asking on
  every desktop build.
- [x] Publish one translated folder, keep output rollback staging internal, and
  stop publishing a permanent pristine-backup sibling in normal GUI, simple CLI,
  and Auto flows.
- [x] Keep `Project Go Changes.csv`, project JSON, state, history, reports,
  translation memory, extraction cache, and recovery data internal by default.
  Offer explicit exports and paths under Advanced.
- [ ] Add bounded cache cleanup, interrupted-output recovery, and legacy-project
  adoption without deleting existing workspaces or copies.
- [ ] Make GUI, Auto, and simple CLI exercise the same workflow service and
  state-transition contract.
- [x] Rewrite the normal user guide around the visible task; keep database,
  schema, report, and portable-project instructions in Advanced sections.

Exit criteria: a clean-profile acceptance test goes from a ZIP to one translated
output without requiring the user to name or manage an internal file, while
cancelled and failed operations preserve source, project state, and prior output.

## P2 — evidence-first revival assessment

- [ ] Add a read-only assessment command for a directory or archive. Normalize
  archive wrappers, require exactly one selected `mod_info.json`, reject unsafe
  entries, and never write beneath the candidate or original archive.
- [ ] Emit deterministic JSON plus human-readable output containing candidate and
  archive SHA-256, selected root, file inventory, mod/game versions, declared
  dependencies, JAR/class/source inventory, extraction coverage, and trust limits.
- [ ] Record source authority explicitly: origin, archive hash, archive coverage,
  selected variant, source/JAR correspondence, and conflicts among competing
  historical inputs.
- [ ] Distinguish supported, review, manual, and blocking findings. Any uncertain
  bytecode-only behavior, save-state migration, internal API use, undeclared
  library ownership, or architecture redesign must remain an escalation gate.
- [ ] Report nested wrapper selection separately from mod validity so scanning the
  wrong directory cannot masquerade as a broken mod.

Exit criteria: two runs over identical bytes produce identical reports, the
candidate remains byte-for-byte unchanged, and the report cannot label a mod
revived or runtime-compatible.

## P3 — close localization coverage gaps safely

- [ ] Produce a complete supported/extracted/skipped inventory for CSV, JSON-like,
  `.ship`, plain text, loose classes, and JAR entries.
- [ ] Extend advisory gap detection to malformed CSV rows, duplicate or blank
  identities, text in extra columns, unrecognized JSON subtrees, and player-visible
  strings that current standard schemas intentionally skip.
- [ ] Require human confirmation of player visibility and stable identity before
  accepting a generated custom schema.
- [ ] For each accepted ecosystem format, add a synthetic failing fixture first,
  implement the narrowest compatible rule, then rerun module, full-suite, and
  source-immutability checks.
- [ ] Preserve technical cells, row shape, comments, encoding, non-target JAR
  entries, and original text checks during reinjection.

Exit criteria: every skipped candidate file has a deterministic reason, accepted
coverage round-trips through a repository-owned fixture, and uncertain content is
reported rather than guessed.

## P4 — reproducible candidate and package audit

- [ ] Record pre/post source tree manifests and prove that assessment, extraction,
  translation, and build did not change source bytes or metadata.
- [ ] Attest the output clone independently: expected translations changed, every
  non-target file remained byte-identical, and no undeclared output appeared.
- [ ] Compare the final candidate directory with its ZIP entry-by-entry using
  normalized relative paths, sizes, and SHA-256. Report missing, extra, and changed
  files separately from stale report bookkeeping.
- [ ] Require the selected JDK, dependency/classpath hashes, build inputs, exact
  commands, and source/JAR/loader authority for any compilation evidence.
- [ ] Keep clone output as the supported path until a standalone overlay passes
  equivalent format and game-loading tests.

Exit criteria: another machine can reproduce the assessment and package identity
from the recorded inputs without access to the original working directory.

## P5 — runtime and persistence gates

- [ ] Define separate machine-readable gates for offline validation, automated
  launcher/main-menu boot, interactive campaign behavior, combat behavior,
  save/load persistence, and upgrade compatibility.
- [ ] Capture the exact Starsector build, enabled mods and versions, load order,
  JVM, candidate hash, direct Java process exit state, logs, and any modal dialog.
- [ ] Exercise registrations and consumers: faction/market ownership, variant and
  wing lookups, scripts/plugins, campaign creation, representative combat, and
  content refresh paths.
- [ ] Test a new campaign and, when the mod owns persistent state, save/reload and
  an explicitly approved upgrade path. Absence of a crash at the main menu is not
  persistence evidence.
- [ ] Keep unsupported semantics at a review gate; do not replace IDs, providers,
  dependencies, or historical weights merely to suppress a load error.

Exit criteria: each claimed behavior maps to a recorded scenario and result;
untested behavior remains visibly `NOT_TESTED` rather than inheriting success from
another gate.

## P6 — completion and future-attempt feedback

- [ ] Generate the final status and summary from per-gate evidence. Reject missing,
  repeated, contradictory, or non-final completion declarations.
- [ ] Require source review, compile/build evidence when applicable, static
  validation, dependency and API checks, package validation, save-compatibility
  disposition, live-test disposition, and redistribution-rights disposition.
- [ ] Run the candidate-to-ZIP identity audit after all reports and packaging are
  final, then archive the report with the release hashes.
- [ ] After every attempt, classify each surprise as candidate-specific, detector
  gap, workflow gap, or documentation gap. Add the smallest reusable fixture and
  tool change before starting the next candidate.
- [ ] Never move an assessment copy to a completed state while a manual,
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
