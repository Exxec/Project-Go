# Assessment implementation checkpoint — 2026-09-13

Parent plan: NON_VALIDATION_COMPLETION.md. P2/P4 are not complete.

## Archive-embedded JAR payload inventory - 2026-09-14

`ssmt assess ARCHIVE.zip --jar-inventory` now inventories JAR entries stored
inside the selected ZIP root without extracting either container to disk. Outer
candidate inventory binds the embedded JAR hash; the JAR stream is re-hashed
while a bounded nested ZIP inventory observes its entries. Class entries remain
`CLASS_ENTRY_UNVERIFIED`, bundled `.java` remains `BUNDLED_SOURCE`, and no
source/JAR correspondence or execution claim is made.

The nested inventory reuses the 10,000-entry and 1 GiB decompressed-byte limits
and archive-path validation. After inspection, the outer archive is inventoried
again and must match the initial captured inventory. The CLI fixture proves an
invalid class payload can be observed from an archive, no wrapper is created,
and the original archive bytes remain unchanged. This does not authenticate the
archive, establish class loading behavior, or resolve source authority.

## Structured source-authority disposition - 2026-09-14

`ssmt assess` now emits `sourceAuthority` in both deterministic JSON and human
output. It records whether the one supplied input was a directory or an archive,
whether archive container and entries were observed and hashed, whether exactly
one metadata root was selected, whether JAR payload inspection occurred, and that
competing historical inputs were not assessed. Every status remains deliberately
non-authoritative: a caller-supplied archive is
`USER_SUPPLIED_ARCHIVE_UNVERIFIED`; a directory is
`USER_SUPPLIED_DIRECTORY_UNVERIFIED`; source/JAR correspondence remains
`NOT_ESTABLISHED` or `NOT_ASSESSED`.

The archive/directory assessment regression proves the dispositions are present
and that the candidate/archive bytes remain unchanged. Focused CLI tests plus
CLI Checkstyle and SpotBugs passed offline. This records provenance limits for a
single candidate; it does not authenticate an origin, resolve competing variants,
or establish dependency, runtime, persistence, or redistribution compatibility.

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

### JAR payload inventory tranche

`ssmt assess DIRECTORY --jar-inventory --json` inventories every JAR beneath the
uniquely selected mod root, without extracting files or defining/loading classes.
JarContents reports portable JAR path, expected-bound exact container SHA-256,
sorted entry path/size/hash/category and sourceJarCorrespondence NOT_ESTABLISHED.
Class entries are CLASS_ENTRY_UNVERIFIED, source entries BUNDLED_SOURCE and other
payloads RESOURCE. Same filenames do not prove source corresponds to bytecode;
bundled .java is not automatically authoritative. Payload streams are subject to
ArchiveInventory's per-container path/collision/10,000-entry/1-GiB safeguards.
After inspection the complete directory inventory must equal the initial one.
ZIP-embedded JAR inventory currently requires future stream support; the option
rejects outer ZIP input rather than extracting it silently. No global multi-JAR
budget/cancellation or bytecode semantic assurance is claimed yet.

Two scanner regressions cover class/source/resource classification, repeatable
read-only payloads with deliberately invalid executable bytes, expected-hash
binding and escaping path refusal. CLI regression confirms inventory succeeds
without loading invalid class payloads and remains NOT_ESTABLISHED. Scanner/CLI
tests, Checkstyle main/test and SpotBugs main PASS: BUILD SUCCESSFUL in 11s,
30 tasks (13 executed). Current changes
remain uncommitted after local source checkpoint 3795002; push approval is pending.

Deferred validation addition: use final rebuilt --jar-inventory on a separate
Nightcross directory, inspect every JAR's payload inventory and compare hashes
with independent ZIP tools. Confirm resources are listed, classes remain unverified,
and original JAR/candidate hashes are unchanged. This does not verify source/JAR
equivalence, loader ownership or runtime compatibility. Archive-embedded support,
authority/conflict findings and global bounded-work integration remain open.

### Portable fingerprint tranche — after source checkpoint 3795002

Assessment now records inventorySha256 for every observed file tuple,
candidateSha256 for files beneath the uniquely selected mod root with the wrapper
removed, and archiveSha256 for exact ZIP container bytes (empty for directories).
Root selection failures leave candidateSha256 empty; they do not choose arbitrary
metadata. Files outside the selected root remain in the complete inventory and
inventorySha256, not silently erased. Directory/ZIP candidate fingerprints match
when their selected relative file paths, sizes and payload hashes match.

InventoryFingerprint v1 hashes ASCII/UTF-8 `ProjectGo-inventory-v1` plus a NUL,
then tuples sorted by Java String path ordering. Each tuple contributes a 4-byte
big-endian UTF-8 path byte length, path bytes, 8-byte big-endian file size, and
32 raw SHA-256 bytes. No absolute path, timestamp, category or ZIP wrapper enters
the selected candidate digest. Duplicate paths and malformed tuples are rejected.
Length prefixes avoid delimiter ambiguity. This digest is not the ZIP-container
SHA-256, vendor authentication or semantic source/JAR equivalence.

Exact archive hashing refuses symbolic/non-canonical/non-regular paths and checks
size/mtime/file key before/after. Existing inventory checks and trust limits apply;
this is not an adversarial filesystem snapshot or cross-read race guarantee.

Two fingerprint regressions cover order independence and path/size/hash changes,
plus duplicate refusal. Extended direct ZIP CLI regression proves the selected
directory/ZIP fingerprints match and archive hash equals independent SHA-256 of
original bytes. Scanner/CLI tests, both Checkstyle main/test and both SpotBugs
main checks PASS after hash guards: BUILD SUCCESSFUL in 11s, 30 tasks
(10 executed). Local evidence remains module build/
XML/static reports; these changes are not yet committed or published.

Push of source checkpoint 3795002 was rejected by the approval check; explicit
approval to push it to Exxec/Project-Go main was requested. No push retry or new
release/promotion was attempted. Continue implementation independently; do not
mistake the local source checkpoint for remote CI/publication proof.

Deferred validation addition: compare selected candidate fingerprints for a known
directory/archive pair, independently hash the ZIP, and confirm an outside-wrapper
file affects inventory/package audit but not the selected-root fingerprint.

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
