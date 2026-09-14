# Independent package audit checkpoint — 2026-09-13

Parent scope: NON_VALIDATION_COMPLETION.md. P4 is not fully complete.

## Pre-publication translated-clone attestation - 2026-09-14

`PatchBuilder` now performs an independent `TranslatedCloneAudit` after writing
the staging clone and before replacing an existing output. It compares every
source file to the staged clone. A file may differ only when it is one of the
complete, explicit translation artifacts; `.ssmt-build-fingerprint` is the only
declared generated file. Missing source files, undeclared outputs, or bytes that
do not match either the source or their declared artifact abort publication.

The patcher regression covers an accepted translated target plus preserved file,
then detects an extra output and an unexpected change to the preserved path.
Focused patcher tests passed offline. This is a pre-publication clone guarantee,
not a substitute for the final directory/ZIP comparison, build-input attestation,
runtime validation, source authority, or redistribution-rights evidence.

The attestation compares streamed SHA-256 and byte counts for source and staged
files; it does not retain whole mod files in memory. Declared patch artifacts are
already complete content by the existing patch contract. The audit still rejects
symbolic links and unsupported filesystem nodes rather than following or ignoring
them.

## Implemented

PackageIdentityAudit compares regular candidate directory files against ZIP
entries using relative paths, actual entry byte sizes and SHA-256. Result lists
missing, extra and changed paths deterministically and independently. Empty lists
mean byte identity only, not runtime assurance or distribution permission.
Archive wrapper is explicitly supplied; files outside it remain extra, never
silently excluded. Both inventories are read-only; existing ZIP input safeguards
and trust limits apply (see ASSESSMENT_IMPLEMENTATION.md).

CLI invocation after rebuilding the distribution:

    ssmt assess CANDIDATE_DIRECTORY --compare-zip PACKAGE.zip --archive-root Wrapper --json

Omit --archive-root when ZIP files are directly rooted. Comparison covers the
entire supplied directory, not only a detected nested mod root. Human output
reports a separate package identity PASS/FAIL and all three difference lists.
JSON includes packageIdentity missing/extra/changed arrays. Identity mismatch
exits 1 even when metadata validity is VALID. --compare-zip requires a directory;
--archive-root requires comparison. No package is created or source modified.

## Automated evidence

Three scanner regressions: repeatable identical bytes under explicit wrapper with
unchanged inputs; missing/extra/same-size changed content including an outside
wrapper file; unsafe wrapper refusal. One new CLI regression verifies nonzero
exit and independent JSON difference arrays. Scanner tests plus Checkstyle
main/test and SpotBugs main PASS (BUILD SUCCESSFUL in 5s). CLI tests and the same
static checks PASS (BUILD SUCCESSFUL in 8s). Existing regression source is durable;
local XML/static reports under module build/ directories remain disposable.
Current changes uncommitted on e32f1f6; no release publication claim.

## Remaining P4 implementation

### Observed source-state manifest tranche

`ssmt assess DIRECTORY --source-manifest --json` records SourceTreeManifest
nodes before assessment and requires an identical capture afterwards. It includes
root and empty directories, regular file sizes/hashes, creation/modification time,
file keys, supported DOS read-only/hidden/system/archive flags and POSIX permission
sets. Nodes sort by relative path. Node count is bounded at 100,000; linked or
noncanonical nodes and observed size/content/tree changes are rejected. Portable
candidate fingerprints remain independent of these machine-local metadata fields.

Access time is excluded because source reads may update it. ACLs, ownership,
extended attributes, alternate streams and an adversarial atomic snapshot are not
claimed. This observes specific metadata, not every conceivable metadata field.
The option currently requires a directory, not an archive-container metadata
manifest. Default assessment leaves sourceManifestStatus NOT_ASSESSED. Successful
requested attestation reports UNCHANGED_OBSERVED_BYTES_AND_METADATA; observed
change fails rather than issuing a success report. SourceTreeManifest's inventory
is rechecked after metadata reads, but races restoring all observed values are
not detectable by this protocol.

Two scanner regressions cover deterministic empty/root directories and source
preservation, plus metadata-only change while portable byte inventory stays the
same. Scanner tests/Checkstyle main/test/SpotBugs main PASS: BUILD SUCCESSFUL in
6s, 9 tasks (6 executed). CLI regression verifies manifest/status and unchanged
source with independent captures. CLI tests and equivalent static checks PASS:
BUILD SUCCESSFUL in 8s, 24 tasks (7 executed). Local XML/static evidence is under
module build/; current additions remain uncommitted after 3795002.

Deferred scenario: final rebuilt directory assessment with --source-manifest
should show all nodes and unchanged observed metadata; compare before/after
independently. On a separate disposable copy, change only last-modified time;
source manifests should differ while byte identity remains unchanged. Never alter
original timestamps to test this. An access-time change alone does not constitute
an attested write violation. Archive-container metadata support and other metadata
dispositions remain implementation work.

Source pre/post manifests including metadata disposition; independently verify
expected translated targets and all non-target clone files; build input/JDK/
classpath authority records; integrate final package identity into evidence-driven
completion after reports are final. Inventories detect ordinary observed races,
not an adversarial filesystem snapshot. Directory empty entries and permissions
are not file-byte identity. Do not mark those stronger gates complete.

## Deferred validation for later today

Run final rebuilt assess against a known exact packaged directory/ZIP pair with
explicit wrapper; expect empty differences and exit 0. On separate disposable
copies remove one ZIP file, add another and alter one same-size file; expect
separate missing/extra/changed lists and exit 1. Confirm a file outside wrapper
remains extra. Hash original inputs before/after and preserve known-good packages.
Repeat the audit after final report packaging; report changes legitimately require
new package hashes. This does not replace native UI/live game validation listed
in NON_VALIDATION_COMPLETION.md and ASSESSMENT_IMPLEMENTATION.md.
