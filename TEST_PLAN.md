# Test Plan & Verification Matrix

`Last updated: 2026-08-02 by Claude (test count refreshed to 292 after ADR-041 reconciliation and PatchNamingAuditor removal; "Generated-overlay naming/version robustness" section marked superseded, see the "ADR-041 pristine/translated clone coverage" section for current coverage)`

## Overview

SSMT processes third-party community content. Testing must therefore cover both strict correctness and real-world ecosystem compatibility.

Every parser and builder change must be verified before merge.

Current verified build: **292 tests** (`@Test` methods across `*Test.java`,
excluding `build/` and the proprietary `Test mods/` tree). Coverage includes
the automated headless wait/import/publish/unchanged/version-refresh
lifecycle and the GUI's source-safe consolidated artifact-directory rules.

ADR-042 coverage verifies glossary short-circuiting, Argos-first ordering,
TranslateLocally fallback, combined diagnostics, stdin/argument construction,
nonzero-exit rejection, explicit-only feedback, `HUMAN_EDITED` persistence,
conflict handling, and exclusion of unreviewed `AI_TRANSLATED` entries.

External-AI exchange tests also cover the explicit whole-project bulk-approval
policy: validated results become trusted entries, provider/model lineage is
retained with `APPROVED` review status, and the existing fail-closed response
validation prevents partial acceptance. Draft import remains the default.

Manual browser-review tests cover deterministic stable-identity batching,
required artifact names, manifest creation only for multi-part exports,
source/local-draft/context/terminology fields, independent part import,
missing-entry rejection, and preservation of every existing translation.

Final-AI coordinator tests prove local-only and unconfigured-provider offline
behavior, zero calls before remote consent, inclusion of both retained local
candidates and style evidence, valid unapproved AI-draft output, and fallback
to unresolved local text when AI output fails structural validation.

Unified project-routing tests prove accepted-text preservation, explicitly
accepted fuzzy application, complete mod/file/type/ID context, cancellation at
bounded batch boundaries, backend/unresolved reporting, optional lineage
retention, versioned JSON/CSV metadata round trips with legacy acceptance, and
zero remote-provider calls without disclosure consent at the project-engine
boundary.

ADR-043 local-chain coverage now verifies explainable confidence reasons,
high-confidence early exit, difficult/uncertain escalation, independent use of
the untouched source, retained local candidates, disagreement reporting,
unsafe-result non-approval, and explicit-only glossary feedback. The final-AI
slice must add context composition, offline operation without configured AI,
and disclosure before any remote provider call.

The canonical final-AI prompt test uses Chinese source text and verifies the
exact section order, local-provider identity, ship/file context, approved terms,
style brief, escalation reason, protected-syntax/line-break/creator-intent
instruction, non-invention rule, untrusted-data boundary, English language-name
rendering, and prepared-prompt pass-through.

ADR-044 coverage verifies schema v1/v2-to-v3 migration, explicit Argos and
TranslateLocally provenance parsing/ranking, provider/model/version/timestamp/
AI-refined/review-status storage, and preservation of machine lineage when an
explicitly approved result is stored with `HUMAN_EDITED` trust provenance.

ADR-045 coverage verifies CRLF/CR/LF preservation, unsafe approval rejection,
no glossary mutation on rejection, exact duplicate session-cache reuse, and
accepted glossary short-circuiting. Existing TM preference, source-immutability,
CPU fallback, and no-download tests jointly cover the remaining invariants.

ADR-046 coverage verifies deterministic score reasons and thresholds, a simple
valid label remaining local, combined long/disagreement signals queuing optional
review, unsafe long text reaching AI-if-enabled without acceptance, and mode
contracts that keep `LOCAL_ONLY` AI-free. Project-router integration remains
pending until the existing author/TM/fuzzy/glossary services are composed.

Bounded Argos acceleration coverage verifies the default CPU environment,
requested `AUTO` acceleration, one CPU retry, requested/used-device diagnostics,
fallback reason retention, and session-scoped avoidance of repeated failed
accelerated attempts.

Resource-limit coverage verifies conservative defaults, positive-value
validation, documented Argos worker/batch environment mapping, and that a GPU-
memory request is not passed through or described as enforced when unsupported.

Capability/lifecycle coverage verifies that only Argos advertises GPU support,
TranslateLocally remains CPU-only, `AUTO` attempts and confirms CUDA explicitly,
accelerated failure confirms CPU fallback, backend diagnostics do not claim an
opaque provider choice, cancellation stops before another stage, and current
providers declare one-shot rather than persistent-model behavior.

---

# 1. Test Categories

## Unit Tests

Isolated behavior using JUnit 5 and AssertJ.

## Integration Tests

Pipeline behavior across module boundaries.

## Regression Tests

Every real-mod compatibility bug becomes an anonymized fixture and permanent test.

## Corpus Tests

Run SSMT against a local set of structurally different real mods without modifying them.

## Fuzz / Property Tests

Use malformed, deeply nested, truncated, or adversarial input to verify bounded failure behavior.

## Performance Tests

Added when measurements establish a budget or identify a hotspot.

---

# 2. Global Invariants

## Source Immutability

After scan/extract/build operations that are expected to be read-only:

- file count unchanged;
- SHA-256 hashes unchanged;
- timestamps unchanged;
- no new files in source;
- no deletions;
- no renames.

## Determinism

Repeated extraction of identical input must produce:

- identical extracted string count;
- identical unsupported-file count;
- identical stable keys;
- identical ordering;
- identical normalized text.

Repeated unchanged patch builds must produce byte-identical patch trees.

---

# 3. Scanner Acceptance

Must cover:

- valid metadata;
- missing metadata;
- malformed metadata;
- dependency resolution;
- missing dependencies;
- dependency cycles;
- deterministic ordering;
- `#` metadata comments;
- structured version objects;
- Unicode mod paths when the launcher preserves them.

Suggested/required regressions:

- `MetadataHashCommentTest`
- `StructuredVersionMetadataTest`
- `UnicodeWindowsPathTest`

---

# 4. CSV Acceptance

Must cover:

- quoted commas;
- escaped quotes;
- multiline cells;
- literal `\n`;
- trailing spaces;
- Unicode text;
- blank cells;
- optional columns;
- harmless sentinel rows;
- malformed input;
- duplicate identities;
- composite identities;
- row reordering;
- deterministic stable keys;
- legacy input encoding;
- unchanged source bytes.

Required regressions include:

- `CsvBlankAuxHeaderTest`
- `CsvBlankSentinelRowTest`
- `CompositeCsvIdentityTest`
- `CompositeCsvIdentityCollisionTest`
- `CompositeCsvIdentityReorderedRowsTest`
- `LegacyEncodingRoundTripTest`

Composite identity reinjection must resolve the same tuple used during extraction.
CSV reinjection must accept and preserve fixture-backed blank auxiliary headers
such as FSF `ship_data.csv`; header positions and cell data must not shift.

---

# 5. JSON-Like Acceptance

Must cover:

- standard JSON;
- comments;
- trailing commas;
- unquoted names where explicitly supported;
- single-quoted strings where explicitly supported;
- known loose literals where fixture-backed;
- RFC 6901 key escaping;
- Unicode;
- selected-pointer extraction;
- selected non-text rejection;
- malformed token diagnostics;
- line-number reporting where available.

Probable typo literals such as:

```json
"autofire": Ture
```

must remain rejected.

Add a regression proving that the parser does not silently reinterpret unknown typo-like literals.
The `Ture` regression also asserts the typed diagnostic code, line,
non-mutating suggestion, unchanged source bytes, and aborted flow.

---

# 6. Bytecode Acceptance

Must verify:

- `.class` parsing without class loading;
- no static initializer execution;
- stable bytecode keys;
- exact source-text verification before reinjection;
- no class invocation or reflection.

---

# 7. Translation Memory Acceptance

Must verify:

- schema migration;
- CRUD;
- deterministic ordering;
- context-safe fuzzy matching;
- transactional import;
- duplicate handling;
- UTF-8 interchange.
- SQLite schema-v1 provenance migration;
- deterministic provenance ranking and lower-confidence replacement rejection;
- legacy project/TM provenance defaulting to `MANUAL_IMPORT`.
- shared GUI/headless default catalog resolution and remembered GUI selection;
- read-only catalog comparison;
- conservative merge of missing and strictly higher-confidence values;
- equal/lower-confidence merge conflicts left unchanged.

## FSF author-localization regression

An anonymized parallel-tree fixture must verify:

- exact `aEP` / `aEP_En` suffix-and-key pairing;
- unrelated `_En` roots are ignored;
- unmatched and mismatched keys are reported rather than guessed;
- author translations receive `AUTHOR_LOCALIZATION`;
- source file count, SHA-256 hashes, and timestamps remain unchanged.

Consistency-audit tests cover exact repeated-source conflicts,
normalized-term conflicts in equivalent contexts, context-difference
false-positive avoidance, consistent translations, and blanks.

---

# 8. Validation Acceptance

Must verify protected syntax:

- Java Formatter placeholders;
- numeric brace arguments;
- Starsector `$token` markers;
- duplicate placeholder counts.

Validation must not rewrite source or translated text.

AI response import additionally verifies current optional entry-count and
identity-set fingerprints, source identity/text, provenance vocabulary, blank
policy, protected syntax, and configured line-break preservation before any
project or translation-memory mutation.

---

# 9. Patch Builder Acceptance

Must verify:

- source/output overlap rejection;
- staging behavior;
- no partial output publication;
- deterministic fingerprint;
- unchanged build no-op;
- exact source verification;
- deterministic patch bytes;
- valid generated `mod_info.json`.

---

# 10. Launcher / Path Acceptance

Must verify that supported launch paths preserve Unicode arguments.

Where Windows batch or PowerShell invocation can corrupt non-ASCII input:

- the failure must be reproducible;
- the Java core must not attempt to repair the corrupted path;
- a Unicode-safe invocation path must be documented/tested.

---

# 11. Phase 7 Real-Mod Corpus

Current local corpus status as of 2026-07-26:

- 11 supplied community-mod samples tested.
- 10 extract successfully.
- 17,237 source files verified unchanged by SHA-256 and timestamp in the final run.
- 1 sample remains rejected because the source contains the malformed token:

```json
"autofire": Ture
```

SSMT intentionally does not normalize this probable typo.

Observed compatibility cases now covered include:

- hash-comment metadata;
- structured metadata versions;
- optional CSV columns;
- harmless sentinel rows;
- composite CSV identities;
- GB18030 fallback;
- uppercase loose-JSON literals where explicitly supported.

The corpus is an acceptance input, not a license to weaken parser correctness.

---

# 12. Named Regression Set

The Phase 7 compatibility suite should include or map to equivalent tests for:

- `MetadataHashCommentTest`
- `StructuredVersionMetadataTest`
- `CsvBlankAuxHeaderTest`
- `CsvBlankSentinelRowTest`
- `CompositeCsvIdentityTest`
- `CompositeCsvIdentityCollisionTest`
- `CompositeCsvIdentityReorderedRowsTest`
- `LegacyEncodingRoundTripTest`
- `UnicodeWindowsPathTest`
- `SourceImmutabilityTest`
- `DeterministicExtractionTest`
- `RealWorldCorpusCompatibilityTest`
- malformed loose-literal rejection

Exact Java class names may differ, but each behavior must remain covered.

---

# 13. Current Build Gate

Current verified state:

- Java: Temurin 25.0.3
- Gradle: 9.1.0
- 130+ Gradle tasks in the expanded release-evidence, dual packaged-smoke, and
  development-bundle gate (task count not independently re-verified in this
  pass; see this file's own "Last updated" note)
- 292 JUnit tests
- zero test failures/errors
- `-Xlint:all -Werror`
- Checkstyle
- SpotBugs
- CLI/GUI distributions
- module JAR packaging

The authoritative normal Windows gate is:

```powershell
gradlew.bat build --offline --no-daemon --max-workers=1
```

The expanded release-evidence gate is:

```powershell
gradlew.bat build generateSbom releaseChecksums scanReleaseArchives checkReleaseMetadata :ssmt-gui:smokeTestAppImage --offline --no-daemon --max-workers=1
```

`clean build` is not required when Windows file locking makes clean nondeterministically fail after a worker exits.

---

# 14. Phase 7 Remaining Acceptance

Before Phase 7 is complete:

- at least three permission-compatible fixtures must pass the full workflow;
- unchanged builds must produce byte-identical patch trees;
- project refresh/update reconciliation must be transactional;
- exact, changed, added, removed, and conflicted entries must be classified;
- fuzzy suggestions must not auto-apply;
- CLI and GUI dry-run update reports must exist;
- generated overlays must receive a manual Starsector load smoke test where permitted.

Current update-reconciliation regressions cover exact unchanged preservation,
changed text, additions, removals, moved keys, ambiguous moved-key conflicts,
non-applied suggestions, and byte-identical unchanged patch-tree rebuilds.

The command-level CLI release-candidate acceptance additionally covers
scan/extract/create/edit/validate/build/unchanged rebuild/refresh dry-run and
apply, source SHA-256 and timestamp preservation, output separation, absence of
implicit AI/fuzzy provenance, and failed-build non-publication. The GUI
acceptance uses the real controller and project service; full JavaFX screen
interaction remains in `MANUAL_ACCEPTANCE.md`.
---

# 15. Regressions From First Live Starsector Overlay Smoke Test

`Status (2026-08-01): all three subsections below are implemented and
regression-tested — see BUGS.md BUG-001/002/003 (Resolved) and DECISIONS.md
ADR-031/032/033 for exact detail. The one remaining step is re-running the
actual manual Starsector smoke test (BUGS.md BUG-005), which these automated
tests do not substitute for.`

## CSV structural-row reinjection — implemented

Required assertions (all satisfied):

- source `,,,,,,` remains a Starsector-compatible blank sentinel row in output;
- source `#ships,,,,,,` remains a Starsector-compatible `#` structural/comment
  row rather than being quoted into an ordinary data record;
- translated neighboring cells still reinject correctly;
- complete composite identities remain strict and duplicate complete tuples
  remain errors (unchanged — `replaceCsv`'s matching logic was not modified);
- source bytes and timestamps remain unchanged;
- repeated unchanged builds remain byte-identical.

Implemented at the **output serialization boundary** in
`StandardFileInjector.injectCsv` (`ssmt-patcher`), which now preserves each
unchanged structural row's original raw text via `CSVRecord.getCharacterPosition()`
instead of a naive line-based approach. Regression tests:
`StandardFileInjectorTest.preservesBlankSentinelRowRawRepresentationDuringCsvInjection`,
`...preservesHashPrefixedStructuralRowRawRepresentationDuringCsvInjection`, and
the `csv-structural-rows` workflow fixture in
`LocalizationProjectServiceTest.structuralRowFixturePreservesSentinelAndCommentRowsThroughDeterministicWorkflow`.

## Explicit player-visible schema coverage — implemented (initial scope)

Verified extraction and reinjection of the observed player-visible fields
directly confirmed non-English in the Azure Federation fixture:

- `ship_data.csv`/`weapon_data.csv` `tech/manufacturer` (optional column);
- `weapon_data.csv` `primaryRoleStr`, `customAncillaryHL` (optional columns);
- faction `ranks.ranks.*.name`, `ranks.posts.*.name`, `fleetTypeNames.*`
  (bounded object-key wildcard `patterns` in `JsonExtractionSpec`).

This was not generalized into extraction of every textual column — remaining
vanilla `weapon_data.csv` tooltip columns are deliberately deferred until
directly observed (see ADR-032). Regression tests: `StandardCsvSchemasTest`,
`CsvExtractorTest.extractsWeaponAndShipOptionalColumnsWhenPresentButToleratesTheirAbsence`,
`JsonExtractionSpecTest`,
`StandardJsonFileExtractorTest.factionExtractsRankPostAndFleetTypeDisplayNamesWhenPresent`
(all synthetic/anonymized fixtures, not real mod text).

## SQLite restart/resume persistence — implemented

All 8 scenario steps plus the "also verify" list are covered by
`CatalogRestartResumeRegressionTest` (`ssmt-gui`, new file) and
`SqliteTranslationMemoryTest.restartingApplicationPreservesMultipleIndependentCatalogsAcrossReopen`/
`...openingHealthyCatalogNeverReinitializesSchemaOrData`. A previously
undocumented first-launch catalog-creation bug was found and fixed along the
way (see ADR-033) — `ProjectWorkspaceController.openOrCreateTranslationMemory`
now handles the create-if-missing default-catalog path, while
`verifyTranslationMemory` (used by the explicit Open Existing Database button)
still requires the file to exist. `TranslationMemoryMergeService` was also
tightened to require the destination to exist, covered by
`TranslationMemoryMergeServiceTest.refusesToCompareOrMergeWhenDestinationCatalogDoesNotExist`.

## Generated-overlay naming/version robustness — superseded by ADR-041, see below

BUG-009/ADR-040 originally found that SSMT's generated overlay winning
Starsector's CSV/JSON merge against its source mod rests on undefined
engine behavior, and a same-day Track A pass hedged it defensively
(`PatchNamingAuditor`, an overlay-only `gameVersion` write, a
`patchId == sourceModId` rejection). ADR-041 (same day) then removed the
need for the hedge entirely by publishing a pristine clone plus a
translated clone of the whole source mod instead of a small overlay with
its own identity — so there is no longer a second mod identity for a
naming hedge to protect. `PatchNamingAuditor`/`PatchNamingFinding` and
their tests were deleted as orphaned dead code once every call site was
already gone. The `gameVersion`/`sourceModName` fields on `PatchRequest`
survived, adapted to the clone-fingerprinting model — see the "ADR-041
pristine/translated clone coverage" section directly below for the actual
current tests. `MANUAL_ACCEPTANCE.md`'s load-order protocol is kept as
optional engine research, explicitly marked "not a release prerequisite."

## ADR-041 pristine/translated clone coverage

- `PatchBuilderTest.publishesPristineAndTranslatedClonesWithoutChangingSource`
  verifies source hashes/timestamps, pristine source bytes, preserved JARs and
  metadata/ID, and translated artifact replacement.
- `...preservesSourceMetadataInsteadOfWritingOverlayMetadata` proves normal
  builds no longer synthesize a second mod identity/dependency declaration.
- `...rebuildsBothClonesWhenAnUntranslatedSourceFileChanges` proves all source
  bytes participate in incremental publication, not only translated artifacts.
- `...restoresBothPreviousClonesWhenSecondPublicationFails` injects failure
  between the two publications and proves both prior live trees are restored.
- Existing unchanged/replacement, overlap/traversal, cancellation, and
  transactional-failure coverage remains in force. End-to-end
  `LocalizationProjectServiceTest.exportsEditsAndBuildsACompleteSourceSafePatch`
  now verifies both clones and the untouched source.
- Required manual evidence: enable only the translated clone in Starsector and
  complete BUG-005. Never enable the source and translated clone together.

## Usability Batch 1/2 regression coverage

- `BackendCommandTest.reportsCurrentReleaseVersion` compares picocli output to
  the generated/manifest-backed version provider rather than another literal.
- `ProjectWorkspaceControllerTest.exposesCentralizedWorkspaceAndRecoveryLocations`
  verifies normalized source/project/output/TM paths and the project-adjacent,
  source-external `.ssmt-recovery` location.
- `SyntheticSampleProjectTest` proves the bundled fixture is installed beneath
  the explicitly selected workspace and can be deterministically reset after a
  practice edit.
- GUI strings continue to be covered by `GuiTextTest`; interactive folder
  reveal, first-use dialogs, and rendered layout remain manual acceptance items.

## Checkpoint and review-diagnostic coverage

- `ProjectTranslationCheckpointServiceTest` verifies compatible resume and
  rejects a checkpoint after source text changes.
- `LocalProviderPreflightTest` verifies missing/configured executables and model
  reporting without execution, installation, or download.
- `RoutingEvidenceDetectorTest` uses multilingual fixture content and verifies
  deterministic report-only findings; routing weights remain unchanged.
- Focused GUI/controller tests cover review persistence; provenance filters and
  rendered lineage/preflight/evidence dialogs remain manual acceptance items.

## Glossary/report and diagnostic-presentation coverage

- `GlossaryInterchangeServiceTest` round-trips the versioned data-only format
  and proves a conflict is reported without applying text.
- `TranslationReportExporterTest` verifies deterministic row order and RFC-like
  CSV escaping for commas and quotes.
- `UserDiagnosticTest` requires the failure reason, source-unchanged statement,
  retry guidance, and diagnostic-export next action.
- Manually verify the new glossary/report choosers and the four Image
  Localization workflow rows at the documented 900 px minimum width.
