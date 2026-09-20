# Extraction coverage checkpoint — 2026-09-13

Parent plan: NON_VALIDATION_COMPLETION.md. P3 is not complete.

## Archive CSV advisory review - 2026-09-14

`ssmt assess ARCHIVE.zip --csv-audit` now reviews CSV entries beneath the
uniquely selected archive root without extracting the archive. Each selected CSV
is bounded at 16 MiB, decoded with the existing UTF-8 then GB18030 fallback, and
passed to the unchanged structural/identity review rules. The complete outer ZIP
inventory is captured again afterward; any observed byte change fails the review.

The new CLI fixture confirms extra-column and duplicate-identity findings are
reported as REVIEW from an archive, no wrapper directory is created, and archive
bytes remain unchanged. This is not extraction coverage, schema approval,
player-visibility evidence, archive authentication, or authorization to
translate any newly reported cell.

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

This inventory covers every discovered regular file. With directory `--coverage`
and `--jar-inventory` together, every nested JAR entry also receives an exact
handling disposition. Class names are read from bytecode and mapped to the existing
allowlisted extracted keys, producing `EXTRACTED` with a selected count or
`SUPPORTED_NO_STRINGS`; bundled source and resources are `UNSUPPORTED` with distinct
reasons. JAR inventory without directory coverage remains explicitly `NOT_ASSESSED`.
These dispositions are not player-visibility or bytecode-semantic claims.

StandardJsonGapAuditor now inventories all textual leaves in JSON-like files handled
by the standard extractor and reports every unselected pointer as
`UNSELECTED_TEXT_REVIEW`. The ordinary extract command logs the same review queue;
`assess --coverage` includes it in deterministic JSON. It does not infer that a
technical path, identifier, sprite name, or custom value is player-visible.
Existing CoverageGapAuditor and CsvGapSchemaSuggester remain advisory; generated
catalogs only take effect when a user reviews and explicitly supplies one through
`--csv-schema`.

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

Format-preserving reinjection now covers whole-file encoding/BOM and byte-identical
non-target JAR resources in addition to JSON/CSV token preservation. The continuing
fixture-first gate for each newly accepted ecosystem format remains open. Full-suite
and exact version/source release artifacts remain required. Inventory and advisory
findings do not authorize a schema.

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
