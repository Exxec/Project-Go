# P1-P5 review follow-up, 2026-10-20

This checkpoint covers the working tree, including the unfinished audit/evidence files present before the review.
It does not replace historical release receipts. The owner explicitly left live tests pending on 2026-10-20.

| Phase | Implementation and regression evidence | Remaining acceptance |
| --- | --- | --- |
| P1 workflow | ZIP cache reuse now compares every file with a fresh bounded extraction. Changed, missing and added files cause refusal; cached work and the source ZIP are preserved. Current entry/expanded-byte limits also apply on reuse. `ModInputPreparationServiceTest`, `TranslationWorkflowTest`, Auto workflow tests. | Repeat visual/native acceptance for any future release assets. Historical GUI observation is not a new visual pass. |
| P2 assessment | Restored the original versioned `InventoryFingerprint.File`, `tree` and `archive` APIs and digest format. Existing assessment, authority and source-preservation regressions remain authoritative. | Source authority is still a candidate-specific review, not an inference from assessment success. |
| P3 coverage | Removed invented unit/gap counts and filename-based PASS from the unfinished scanner prototypes. They report metadata only, never parser coverage. Use `assess --coverage` for actual extraction/gap evidence. Existing base/extension conformance, `GapCoverageRoundTripTest` and `LeftoverClassifierTest` cover accepted formats and conservative leftovers. `AuditPrototypeTest` rejects the malformed-metadata/empty-locale false-pass case. | The ongoing fixture-first requirement remains open for newly accepted ecosystem formats. Nightcross's invalid nested JAR requires authoritative replacement or reviewed repair. The incomplete untracked `fixtures/p3` archives are not acceptance evidence. |
| P4 package audit | ZIP preparation verifies cached bytes. Final audits compare sizes and hashes, report payload outside the selected wrapper, reject duplicate/invalid manifest entries and coerced schema versions, and recheck archive/candidate stability. One bounded manifest byte array supplies both the parsed inventory and its recorded hash. `CandidateArchiveAuditTest`, `PackageIdentityAuditTest`. | Run against the exact final approved candidate and ZIP before a release claim. |
| P5 runtime | Pending profiles preserve an unobserved process exit as `null`; terminal runtime evidence requires an observed exit and verified logs. Scanner/structural observations never qualify runtime PASS. Existing runtime capture, context-conformance and ledger tests remain separate from gameplay evidence. | Boot, campaign registrations/consumers, combat, save/reload and approved upgrade tests are NOT_TESTED for a new candidate. Live testing is left pending at the owner's request. |

The repaired standalone structural writer produces escaped JSON and refuses to replace existing evidence. It is not the
assurance ledger: `AssuranceLedgerReader`/`AssuranceLedgerWriter`, candidate audits and runtime profiles in `ssmt-project`
remain the authoritative gate implementations. The extraction prototype delegates to the existing coordinator rather
than claiming to have captured an audit it did not perform. `LedgerWriterTest` covers first write, refusal to replace,
invalid SHA-256 and rejection of runtime PASS from counts alone.

Cache verification reads and decompresses the archive again on reuse, within the existing limits. This deliberate cost
prevents a hash-shaped cache directory name from being treated as proof of its contents. A mismatch is preserved for
review; no automatic deletion or source repair occurs.

## Validation

Run the build with a writable Gradle home and an initially absent user-data profile:

```powershell
$env:GRADLE_USER_HOME = Join-Path (Get-Location) '.gradle-user-home'
$env:LOCALAPPDATA = Join-Path (Get-Location) '.local/review-fixes-20261020/fresh-profile-final'
.\gradlew.bat build --offline --no-daemon --max-workers=2 --console=plain
```

The initial review build reproduced the invalid `Paths.Path` compilation error. During repair validation, sandboxed Java
also failed to access existing dependency JARs; the same focused regressions passed when run outside the sandbox.
The first full repair build found a SpotBugs null-parent warning in the new cache regression. That warning was fixed.
An independent review also found that manifest parsing and hashing used separate reads; the final build includes the
single-byte-array fix and strict numeric schema-version regressions.

Final validation of the combined working tree:

- `build --offline --no-daemon --max-workers=2 --console=plain`: PASS, 116 actionable tasks (54 executed, 62 up-to-date),
  732 reported tests, zero failures/errors, two intentional skips (`GuiChecklistTest`, `RealModAcceptanceTest`).
  Compile with warnings as errors, Checkstyle, SpotBugs, distributions and `verifyWindowsLauncher` passed.
- `:ssmt-auto:verifyNativeUnicode`: PASS; native folder/metadata Unicode arguments, arbitrarily named response,
  exact fixture output and unchanged source. This is automated native evidence, not owner GUI observation.
- Rebuilt CLI `assess .local/inputs/nightcross.zip --coverage --jar-inventory --json`: expected exit 1,
  `ASSESSMENT_ONLY`, `NOT_ASSESSED_INVALID_JAR`, `INCOMPLETE_JAR_INTEGRITY`, and blocking
  `JAR_ENTRY_INTEGRITY_FAILED` for `jars/nightcross.jar` entry `.idea/.gitignore`.
  Archive SHA-256 before and after: `a6baabc3c935c99cf881312f738fa044bc7b8b6fed6fbf9ed569e77e3fe2b65b`.
- `git diff --check` and local links in the edited Markdown: PASS. `graphify update .` completed using AST extraction.

Local logs stay under `.local/review-fixes-20261020/`: `final-build.log`, `native-unicode.log`,
`nightcross-assessment.json`, `nightcross-assessment-receipt.json` and `test-totals.json`.
Earlier failed diagnostic logs are retained and are not green-build receipts. The owner subsequently requested
commit/push on 2026-10-20; that source publication does not authorize a version tag or product release.
Staging checks found that default Git line-ending conversion would change the hash-bound runtime fixtures.
`.gitattributes` now preserves their exact bytes, and every staged runtime fixture was compared byte-for-byte with the
tested working copy before commit. This protects Windows/Linux checkout parity without changing fixture content.

No source mod, game installation, save, provider endpoint, tag or product release is modified by these repairs.
