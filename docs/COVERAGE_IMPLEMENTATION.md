# Extraction coverage checkpoint — 2026-09-13

Parent plan: NON_VALIDATION_COMPLETION.md. P3 is not complete.

## Implemented

ExtractionCoordinator now emits FileCoverage for every discovered regular file,
with relative path, handler class, observed status, selected-string count and
deterministic reason. UNSUPPORTED/NO_EXTRACTOR_MATCH is distinct from
SUPPORTED_NO_STRINGS/NO_STRINGS_SELECTED_BY_HANDLER and EXTRACTED/SELECTED_STRINGS_ONLY.
Existing extracted strings, key ordering and skipped-file lists are preserved.
ExtractionReport retains its two-argument constructor for legacy callers, which
does not invent observed coverage for manually constructed reports.

`ssmt assess DIRECTORY --coverage --json` runs the existing four standard handlers
only under the uniquely selected valid mod root and emits portable coverage paths.
It compares complete candidate inventories before/after extraction and fails on
observed change. Coverage is optional; missing/invalid metadata retains explicit
NOT_ASSESSED coverage. ZIP --coverage currently fails with a clear directory-only
diagnostic rather than silently extracting content or claiming coverage.
No custom schema is approved, no strings are translated, no source bytes written.

This inventory covers discovered regular files, not individual JAR resource entries
or every skipped JSON subtree/CSV field. JAR counts represent selected class strings
after existing policy, not all player-visible text. Existing CoverageGapAuditor and
CsvGapSchemaSuggester remain advisory; they are not automatically accepted here.

## Automated evidence

Two FileCoverageTest regressions distinguish unsupported, supported-empty and
extracted files, repeatability and unchanged source, and preserve legacy report
construction. Extractor and dependent project tests plus extractor Checkstyle
main/test and SpotBugs main PASS: BUILD SUCCESSFUL in 8s, 24 tasks (10 executed).
AssessCommandTest adds observed count/status/portable path checks and independently
compares source inventory before/after. CLI tests and Checkstyle main/test plus
SpotBugs main PASS: BUILD SUCCESSFUL in 8s, 24 tasks (6 executed).
Local module build/test-results/test and build/reports are disposable evidence;
repository-owned tests are reproducible contracts. Current work is uncommitted
after source checkpoint 3795002; push remains approval-paused.

## Remaining implementation

### CSV advisory tranche

`ssmt assess DIRECTORY --csv-audit --json` independently reviews CSV structure,
without requiring extraction to succeed. CsvStructureAuditor reports malformed
CSV, duplicate named headers, extra columns, short rows, blank/duplicate standard
schema identities and explicit IDENTITY_NOT_ASSESSED for unknown/missing identity
schemas. Composite identities follow StandardCsvSchemas; repeated description id
with different types is not a duplicate. Every finding is REVIEW, including rows
that an existing schema intentionally skips. No guessed custom identity or
player-visible status is accepted. CSV record numbers are not physical line
numbers when quoted fields span lines. Parser semantics use CSVFormat.DEFAULT,
matching existing extraction. Strict UTF-8 then GB18030 fallback and UTF-8 BOM
handling preserve input bytes. Each CSV is bounded at 16 MiB and 100,000 rows;
there is not yet a global multi-file work budget or cancellation contract.
Unsafe/linked/unreadable/invalid-encoding input yields explicit review rather than
being silently omitted. Assessment compares complete source inventories after
CSV review and fails on observed change.

Three extractor regressions cover multiple independent findings and immutable
source, composite identity correctness, malformed input and unknown identity.
Extractor tests, Checkstyle main/test and SpotBugs main PASS: BUILD SUCCESSFUL in
8s, 9 tasks (5 executed); two initial Checkstyle statement-layout findings were
fixed before the final run. CLI regression proves review runs independently with
extraction NOT_ASSESSED and preserves malformed source. CLI tests and equivalent
static checks PASS: BUILD SUCCESSFUL in 9s, 24 tasks (7 executed).

Deferred scenario: after rebuilding final tools, run --csv-audit without --coverage
on separate malformed/extra-column/duplicate/blank fixtures and inspect REVIEW
codes and source immutability. Combining with --coverage still requires the actual
extraction to parse successfully; coverage parse failure is not hidden by advisory
success. Unknown-schema IDENTITY_NOT_ASSESSED requires human confirmation, not a
schema approval. ZIP CSV advisory support and gap visibility remain open.

Individual JAR class/resource entry inventory; bounded advisory malformed CSV,
extra-column and duplicate/blank identity findings; skipped JSON subtree evidence;
gap/suggestion integration with explicit human approval; format-preserving
reinjection including comments/encoding where current JSON serialization normalizes
them. Accepted formats need synthetic round-trip fixtures. Full suite and exact
version/source release artifacts remain required. Do not close P3 on file counts.

## Deferred validation for later today

After final distribution rebuild, run assess --coverage on a separate Nightcross
directory copy; inspect handler names, extracted counts and unsupported/empty
reasons. Confirm source hashes/metadata remain unchanged and proper mod root was
selected. Unsupported text is a review queue, not proof it is technical or dead.
Confirm ordinary assess of the original ZIP remains non-extracting and ZIP
--coverage explains its current limitation. Repeat these scenarios if subsequent
archive coverage support replaces that limitation. Native/game scenarios remain
listed in NON_VALIDATION_COMPLETION.md; package/hash scenarios in the assessment
and package-audit checkpoints. No exhaustive live assurance inferred.
