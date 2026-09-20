# Assurance ledger checkpoint — 2026-09-13

Parent plan: NON_VALIDATION_COMPLETION.md. The P5/P6 machine-readable gate,
capture, derived-status, feedback, and final-byte binding foundations are complete;
live scenarios and actual per-attempt/release evidence remain open.

AssuranceLedgerReader reads schemaVersion 1, candidateSha256, results (the
AssuranceSummary.Result records) and references (portable relative path/sha256
pairs). Unknown fields, duplicate JSON keys, trailing JSON, missing/invalid hashes,
unsupported schemas, missing/repeated/foreign gates and missing/repeated/unused
references are rejected. Input JSON is bounded at 1 MiB, references at 128 and
combined actual evidence bytes at 64 MiB. Evidence stays beneath the ledger parent;
linked/nonregular files are rejected. Windows canonical path aliases are accepted
after component-link rejection. Hash mismatch fails the ledger.
The reader does not modify ledger, evidence or candidate bytes.

Expected candidate hash is an independent API argument, not inherited from an
untrusted ledger declaration. Evidence references must exist for every nonempty
result evidence string; PASS/FAIL requires a reference. Runtime dispositions stay
independent. References can be shared across runtime gates only when a schema-2
runtime capture contains an exact gate, scenario-name, and PASS/FAIL match for every
referencing ledger result. Schema-1 captures remain readable as historical launch
context, but cannot substantiate a terminal runtime gate. A terminal runtime record
also requires at least one hashed log, and PASS requires process exit zero. Contents
are not semantically interpreted:
a hash-matched arbitrary log is not proof that a scenario passed, source authority
is valid or rights exist. BUILD `PASS`/`FAIL` is the exception at the structural
boundary: its evidence must be a candidate-bound build-evidence record with verified
input/classpath/output hashes and explicit authority dispositions. BUILD PASS also
requires process exit zero and VERIFIED source, compiled-JAR, and loader/provider
authority; unresolved authority cannot reach READY. The derived status describes
recorded dispositions;
release/runtime certification requires reviewing actual scenario evidence.

Regressions cover immutable valid input and tampering, contained paths and foreign
candidates, unknown/duplicate/trailing completion declarations, missing candidate
hashes, oversized input, failed/unresolved builds, and incorrect evidence types.
Tests remain the durable evidence; local XML/static reports under
`ssmt-project/build/` are disposable build output.

## CLI/template tranche

The following are available after rebuilding the CLI distribution:

    ssmt assurance --candidate MOD_DIRECTORY --template
    ssmt assurance LEDGER.json --candidate MOD_DIRECTORY --json

Candidate must be the actual valid mod root, not a wrapper or ZIP. The command
computes InventoryFingerprint v1 from current directory bytes, independently of
the ledger. Template emits schemaVersion 1 and all 13 gates NOT_TESTED with no
evidence references; it writes no file and makes no READY claim. The reader then
requires complete hash-bound gates and contained hashed references. Candidate
bytes are checked again after inspection; observed change fails. Human output
calls the state Recorded status; JSON nests recordedSummary and always sets
evidenceSemanticsVerified false. Exit 0 on inspection means recorded READY only,
not semantic certification; other dispositions/errors exit 1. Template generation
exits 0 for successfully generated pending data. Supply ledger or --template,
never both/neither. No game launch or release promotion happens.

CLI regressions cover the complete pending template, independent source binding,
immutable source, non-READY pending ledger, explicit semantic trust limit, stale
candidate and explicit operation choice. CLI tests/Checkstyle main/test/SpotBugs
main are part of the repository check. Local `build/` XML/static reports remain
disposable evidence. The earlier Next paragraph below predates this CLI integration.

Deferred validation is now runnable after final rebuild: generate pending template
outside the mod source, inspect it, record actual independent scenarios/references,
check that untested gates remain explicit and changed candidate rejects the ledger.
Review actual contents of every evidence artifact before accepting recorded PASS.
Runtime capture, scenario coverage and final package/report binding remain open.

## Attempt feedback and final-byte binding - 2026-09-20

`ssmt attempt-feedback --candidate MOD_DIRECTORY --template` emits a review-pending,
candidate-bound record. Inspection accepts only the four roadmap classifications and
requires every recorded surprise to reference exact hashed evidence, a reusable
fixture, and a concrete tool or documentation change. Repeated surprises, unused or
missing references, escaping/linked paths, tampering, and foreign candidates fail.
Only an explicitly completed review derives `READY_FOR_NEXT_CANDIDATE`; the tool
cannot prove that a reviewer disclosed every real-world surprise.

`ssmt finalize-evidence` consumes the exact candidate, final ZIP, READY assurance
ledger, and completed feedback record. It reruns candidate-to-ZIP identity after
those reports are final, rechecks candidate/package stability, and writes a new
non-overwriting JSON report containing candidate, package, ledger, and feedback
hashes. The printed report SHA-256 can be archived with release checksums. Its status
is deliberately `FINAL_BYTES_AND_RECORDED_EVIDENCE_AGREE` and
`evidenceSemanticsIndependentlyVerified` remains false; publication, rights, and
actual scenario review stay external gates.

The earlier "Next" work is now implemented by the runtime capture, ledger,
attempt-feedback, and finalize-evidence commands described above. No live test or
published-release claim follows. Filesystem races restoring all observed state are
not a snapshot guarantee.

Deferred validation once the final ledger frontend exists: record every required
scenario against the final candidate hash; leave untested scenarios NOT_TESTED.
Inspect actual referenced evidence, not filenames. Confirm stale/tampered/missing
files, foreign candidate and contradictory/missing gates reject readiness. Final
manual scenario list also lives in NON_VALIDATION_COMPLETION.md and the assessment,
coverage and package audit checkpoints; no new game test was performed here.
