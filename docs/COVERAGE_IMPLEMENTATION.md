# Extraction coverage checkpoint — 2026-09-13

Parent plan: NON_VALIDATION_COMPLETION.md. P3 is not complete.

## Standard CSV selection-gap assessment - 2026-09-23

`ssmt assess INPUT --coverage --json` now includes `csvGapStatus` and
`csvGapFindings` for directory and ZIP candidates. The existing standard CSV
auditor reports non-ASCII text in columns outside the selected schema, using
mod-relative paths. These are review findings, not evidence of player
visibility or authorization to translate technical columns. The independent
`--csv-audit` structure review was already available for ZIP inputs.

The auditor now examines later rows when earlier cells in the same column are
ASCII; it previously skipped the column after the first row. Reads stop at
16 MiB plus one byte, unsafe or missing paths receive an unavailable finding,
and samples are capped at 160 characters. A failed standard extraction keeps
`csvGapStatus=NOT_ASSESSED` and publishes no CSV gap findings. Repository
regressions cover later-row text in directory and nested-root ZIP candidates,
source immutability, bounded samples, and unavailable inputs. No custom schema
was accepted.

The assessment serializes CSV and JSON gap source paths as explicit
mod-relative strings; Java's default serialization of ZIP-backed `Path`
objects had exposed absolute `jar:file:` URIs. The rebuilt packaged CLI
reported `groupTag` with sample `技术` at
`data/weapons/weapon_data.csv` from a nested-root ZIP whose first data row was
ASCII. Its SHA-256 stayed
`001122506b3c47071a2ed448df8b40aae0fd27b3747288d5ce4099d0f81d740a`;
no wrapper directory was extracted. A fresh-profile offline full check and
CLI/GUI/Auto distribution rebuild passed 578 tests in 131 suites, with zero
failures/errors/skips and all 102 tasks executed. This is local development
evidence, not an accepted new schema or release result.

## Read-only ZIP coverage checkpoint - 2026-09-23

`ssmt assess ARCHIVE.zip --coverage --jar-inventory --json` now mounts an
already validated ZIP through Java's ZIP filesystem and runs the same four
standard extractors as directory coverage under the uniquely selected mod
root. It does not materialize a mod tree. Outer file handling paths remain
relative to that root; nested JAR entry handling uses the same source-relative
class keys and reports unsupported resources separately. The command compares
the complete entry inventory and archive SHA-256 before and after coverage.
Invalid metadata still leaves coverage `NOT_ASSESSED`.

A repository-owned nested-root ZIP fixture covers selected JSON, unselected
`.ship` text, unsupported files, a nested JAR resource, and a real `.class`
entry. It verifies read-only bytes and no extracted wrapper directory. The
focused CLI/extractor tests and static checks passed. A later fresh-profile
full check and CLI/GUI/Auto distribution rebuild passed 574 tests with zero
failures/errors/skips and all 102 tasks executed. The native GUI/Auto
development bundle and smoke tasks also passed. These are local working-source
results; actual player visibility and bytecode behavior remain unverified.

The Nightcross ZIP remains SHA-256
`a6baabc3c935c99cf881312f738fa044bc7b8b6fed6fbf9ed569e77e3fe2b65b`.
Basic read-only assessment selects `Nightcross` and inventories 2,374 files.
`--jar-inventory --coverage --json` now returns a parseable partial assessment
with exit code 1, `INCOMPLETE_JAR_INTEGRITY`, `NOT_ASSESSED_INVALID_JAR`, and a
`BLOCKING` `JAR_ENTRY_INTEGRITY_FAILED` finding. It names the CRC mismatch in
`jars/nightcross.jar` entry `.idea/.gitignore` (recorded CRC zero). The scanner
still refuses to claim Nightcross coverage or package success; repair or
authority review belongs to a separate candidate copy. A regression also
proves that embedded-JAR hashing includes bytes after its payload entry list.
The coverage-only ZIP command now checks nested JAR integrity before extraction
as well. On this archive, `--coverage --json` exits 1 with the same blocking
finding and `NOT_ASSESSED_INVALID_JAR`, while `jarInventoryStatus` remains
`NOT_ASSESSED` and no JAR contents are claimed. The rebuilt packaged CLI report
was parsed and the source ZIP hash remained unchanged. A focused regression
covers both command variants.
Malformed selected JSON in a valid candidate now yields a blocking partial
assessment for directory and ZIP coverage. `coverageStatus=INCOMPLETE_SOURCE_PARSE`
names the selected-root-relative source path and stable parse code, with no
extraction counts or JSON gap review claimed. The command verifies the candidate
inventory and ZIP hash are unchanged before emitting that report, and exits 1.
A regression covers both input kinds, unchanged source bytes, and the absence
of an extracted wrapper tree.
After this partial-report change, the full fresh-profile check and three
distribution rebuilds passed 576 tests with zero failures/errors/skips; all
102 tasks executed. The Windows native GUI/Auto development bundle and image
smoke tasks passed. A later malformed-selected-JSON regression brought the
latest full fresh-profile check and CLI/GUI/Auto distribution rebuild to 577
tests in 131 suites, with zero failures/errors/skips and all 102 tasks
executed. Native GUI/Auto image smoke tasks passed again. The modified source
still has no exact-source release artifact.

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
NOT_ASSESSED coverage. At this earlier checkpoint, ZIP --coverage failed with a
clear directory-only diagnostic rather than silently extracting content or
claiming coverage; see the 2026-09-23 ZIP checkpoint above.
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
schema approval. ZIP structure review was implemented in the 2026-09-14
checkpoint; the later standard CSV selection-gap assessment is recorded above.

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
Confirm ordinary assess of the original ZIP remains non-extracting. The
2026-09-23 ZIP checkpoint above records the later coverage implementation and
its Nightcross integrity gate. Native/game scenarios remain
listed in NON_VALIDATION_COMPLETION.md; package/hash scenarios in the assessment
and package-audit checkpoints. No exhaustive live assurance inferred.
