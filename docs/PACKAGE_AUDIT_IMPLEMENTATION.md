# Independent package audit checkpoint — 2026-09-13

Parent scope: NON_VALIDATION_COMPLETION.md. P4 is not fully complete.

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
