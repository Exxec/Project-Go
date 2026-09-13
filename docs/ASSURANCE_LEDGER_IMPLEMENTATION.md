# Assurance ledger checkpoint — 2026-09-13

Parent plan: NON_VALIDATION_COMPLETION.md. P5/P6 remain incomplete.

AssuranceLedgerReader reads schemaVersion 1, candidateSha256, results (the
AssuranceSummary.Result records) and references (portable relative path/sha256
pairs). Unknown fields, duplicate JSON keys, trailing JSON, missing/invalid hashes,
unsupported schemas, missing/repeated/foreign gates and missing/repeated/unused
references are rejected. Input JSON is bounded at 1 MiB, references at 128 and
combined actual evidence bytes at 64 MiB. Evidence stays beneath the ledger parent;
linked/noncanonical/nonregular files are rejected. Hash mismatch fails the ledger.
The reader does not modify ledger, evidence or candidate bytes.

Expected candidate hash is an independent API argument, not inherited from an
untrusted ledger declaration. Evidence references must exist for every nonempty
result evidence string; PASS/FAIL requires a reference. Runtime dispositions stay
independent. References can be shared across gates only if the same artifact really
contains each named scenario's evidence. Contents are not semantically interpreted:
a hash-matched arbitrary log is not proof that a scenario passed, source authority
is valid or rights exist. The derived status describes recorded dispositions;
release/runtime certification requires reviewing actual scenario evidence.

Four regressions cover immutable valid input and tampering, contained paths and
foreign candidate, unknown/duplicate/trailing completion declarations, missing
candidate hash and actual oversized input. Project tests plus Checkstyle main/test
and SpotBugs main PASS: BUILD SUCCESSFUL in 12s, 21 tasks (6 executed).
AssuranceLedgerReaderTest: 4 tests, zero failures/errors/skips. Local XML/static reports are under
ssmt-project/build/; tests remain durable source. Current additions are uncommitted
after local source checkpoint 3795002. Push remains approval-paused.

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

Two CLI regressions cover complete pending template, independent source binding,
immutable source, non-READY pending ledger, explicit semantic trust limit, stale
candidate and explicit operation choice. CLI tests/Checkstyle main/test/SpotBugs
main PASS: BUILD SUCCESSFUL in 9s, 24 tasks (7 executed). Source remains uncommitted
after 3795002, whose push is still approval-paused. Local evidence: module build/
XML/static reports. The earlier Next paragraph below predates this CLI integration.

Deferred validation is now runnable after final rebuild: generate pending template
outside the mod source, inspect it, record actual independent scenarios/references,
check that untested gates remain explicit and changed candidate rejects the ledger.
Review actual contents of every evidence artifact before accepting recorded PASS.
Runtime capture, scenario coverage and final package/report binding remain open.

Next: runtime capture and scenario assurance integration, independently recomputed current candidate
binding, runtime environment/process/log capture, evidence scenario coverage and
final report/package binding. No live test or published-release claim here.
Filesystem races restoring all observed state are not a snapshot guarantee.

Deferred validation once the final ledger frontend exists: record every required
scenario against the final candidate hash; leave untested scenarios NOT_TESTED.
Inspect actual referenced evidence, not filenames. Confirm stale/tampered/missing
files, foreign candidate and contradictory/missing gates reject readiness. Final
manual scenario list also lives in NON_VALIDATION_COMPLETION.md and the assessment,
coverage and package audit checkpoints; no new game test was performed here.
