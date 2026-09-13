# Assessment implementation checkpoint — 2026-09-13

Parent plan: NON_VALIDATION_COMPLETION.md. P2/P4 are not complete.

## Implemented inventory foundation

ssmt-scanner CandidateInventory.capture records a deterministic sorted regular
file inventory: relative normalized path, size, SHA-256 and category (CSV,
JSON-like, Java source, loose class, JAR, text, other). It does not write beneath
the candidate, follow symbolic links or silently accept canonical-path aliases.
Non-regular entries and over-limit inventories fail explicitly. File size,
modification time and file key are checked before/after hashing to detect ordinary
concurrent replacement. This is not a filesystem snapshot or adversarial race
guarantee; same-size writes with restored metadata need stronger verification.
The limit is 100,000 regular files, not an archive expansion limit.

Tests cover repeated identical sorted reports, independent expected file hash,
unchanged source content/modification time and rejecting non-directory input.
CandidateInventoryTest: 2 tests, zero failures/errors/skips. Full scanner test,
Checkstyle main/test and SpotBugs main command PASS:

    gradlew.bat :ssmt-scanner:test :ssmt-scanner:checkstyleMain :ssmt-scanner:checkstyleTest :ssmt-scanner:spotbugsMain --offline --console=plain

BUILD SUCCESSFUL in 13s. Local reports: ssmt-scanner/build/test-results/test and
ssmt-scanner/build/reports. Current work is uncommitted on e32f1f6; no release
contains this foundation yet. Re-run full checks after integration.

## Next implementation

### Metadata/dependency tranche

Assessment now parses the selected metadata via ModInfoReader's bounded-byte
entry point, sharing existing normalization for versions/dependencies rather than
extracting ZIP metadata or guessing it from filenames. Metadata reading is bounded
at 1 MiB and SHA-256-bound to the captured inventory. Reports distinguish selected
root from VALID/INVALID/NOT_ASSESSED metadata, include declared mod/game versions,
JARs and dependencies, and still label installed dependency compatibility,
authority, runtime/persistence and rights unassessed. Missing, ambiguous or invalid
metadata now exits 1; unique valid declarations exit 0 for assessment only.
Older inventory CLI checkpoint descriptions below predate this validity extension.

Added direct ZIP regression for declared MagicLib dependency, numeric version
normalization, unchanged archive bytes and no extracted wrapper. Existing invalid
metadata fixture now asserts INVALID despite successful root selection. Scanner
and CLI tests, scanner/CLI Checkstyle main, CLI Checkstyle test and both SpotBugs
main checks PASS: BUILD SUCCESSFUL in 10s, 29 tasks (13 executed).
Metadata normalization also rejects JSON null as a
missing required id rather than throwing a null dereference.

Deferred checks: inspect directory/ZIP declared dependencies and validity separately
from root selection; verify an invalid mod_info yields an assessment report with
INVALID and exit 1, not a runtime compatibility claim. Rebuild final distributions
before user CLI scenarios. Candidate/archive root hash, JAR-internal inventory,
source authority, gap findings and complete report integration remain open.

### Inventory CLI tranche

Registered `ssmt assess CANDIDATE [--json]`: directory or ZIP inventories go to
stdout, never a file beneath the candidate. Reports are deterministic and contain
all discovered mod_info.json paths, a selected root only when exactly one exists,
separate SELECTED/MISSING/AMBIGUOUS root-selection state and ASSESSMENT_ONLY status.
Missing/ambiguous selection exits 1 with an inventory report; successful unique
selection exits 0, which means inventory/root selection only, not valid metadata.
Metadata/dependency parsing, source/JAR authority, extraction coverage, archive
authentication/extractability, runtime/save and redistribution checks are explicitly
NOT_ASSESSED/NOT_TESTED. No runtime compatibility or revival completion is inferred.
CLI Jackson is now an explicit existing-version dependency (initial compilation
identified that it was not exposed through transitive project implementations).

AssessCommandTest covers repeatable wrapper-root JSON output with invalid metadata
remaining explicitly unassessed, unchanged candidate bytes and multiple-root
rejection. CLI tests, Checkstyle main/test and SpotBugs main are run with:

    gradlew.bat :ssmt-cli:test :ssmt-cli:checkstyleMain :ssmt-cli:checkstyleTest :ssmt-cli:spotbugsMain --offline --console=plain

Final CLI check result: BUILD SUCCESSFUL in 8s, 24 tasks (5 executed).
SpotBugs initially found report mutable-list exposure; defensive List.copyOf
construction fixed it before the final passing run.

Next extend the report to parse selected metadata/dependencies, candidate/archive
root hash, JAR/class inventory and advisory findings; add archive CLI/unsafe-input
tests. Root selection alone does not close P2. Source still uncommitted on e32f1f6.

Deferred CLI scenario is now runnable once distributions are rebuilt: assess the
original Nightcross ZIP and a separate directory copy twice with --json, compare
reports, check selected root and original hash/metadata preservation. Also inspect
human output and explicitly ambiguous roots. Do not confuse exit 0 with a runtime
or metadata-validity PASS.

### ZIP inventory tranche

ArchiveInventory.capture now hashes ZIP entry streams without extracting them.
It sorts archive-relative entries, rejects traversal/absolute/backslash/colon and
ambiguous path components, rejects duplicate/case-colliding entries, bounds all
entries at 10,000 and actual decompression at 1 GiB, checks declared entry size
against actual bytes and checks archive size/mtime/file key after reading.
It refuses linked/non-canonical archive inputs rather than following them.
No archive authentication, CRC validation, Unix ZIP symlink-attribute inspection
or proof of extractability is claimed. File/implicit-directory collisions and
Windows reserved filenames still require explicit archive assessment handling;
never pass this inventory as authorization for extraction. No bytes are extracted.

ArchiveInventoryTest adds three regressions: repeatable sorted archive inventory
with unchanged archive bytes/mtime and no extracted wrapper, traversal rejection
and case-collision rejection. Scanner tests plus Checkstyle main/test and
SpotBugs main PASS, BUILD SUCCESSFUL in 5s using the scanner command above.
Current source remains uncommitted on e32f1f6. Full assessment CLI, root selection,
metadata/authority/coverage reports and full-suite release evidence remain open.

Implement safe read-only ZIP inventory with traversal/duplicate/bomb guards,
exactly-one-mod-root selection distinct from metadata validity, deterministic
CLI JSON/human reports and explicit authority/trust/coverage dispositions.
Inventory JAR entries/classes and matching source paths without claiming semantic
source/JAR correspondence. Add link and archive hostile-input regressions.
Integrate pre/post source manifests and independent clone/ZIP identity audits.
An inventory category is not proof the extractor supports every string in it.

## Deferred user validation additions

After the assessment CLI exists, run it twice on a separate ordinary directory
and the original Nightcross archive; compare deterministic outputs and verify
source hashes and metadata remain unchanged. Test ordinary deep Windows paths
and explicit review diagnostics for linked/aliased roots. No current CLI or
archive-assessment completion claim is made by this checkpoint.
