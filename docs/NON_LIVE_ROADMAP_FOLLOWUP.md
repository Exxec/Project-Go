# Non-live roadmap follow-up, 2026-10-20

Scope: the owner requested work through the remaining non-live roadmap items in the recommended order.
Game testing remains pending. Nightcross was explicitly left blocked by the owner after the archive diagnosis.
Existing source mods, saves, translation libraries, scratch work and unrelated dirty files are preserved.

## Completed coding work

- **G3.1:** `GlossaryService` now obtains repeat counts from `TerminologyConsistencyAuditor.sourceOccurrences`.
  Exact untranslated sources count. Blank and IDENTIFIER sources do not; case/punctuation variants stay separate.
  Counts are immutable and ordered. A repeated term is still a proposal, never an approval.
- **G3.2:** Review Changes offers **Revoke automatic approvals** when the project contains rule-approved entries.
  The project transaction keeps the text and other provenance, withdraws policy approvals, and persists pending review.
  Installing stays blocked after restart; failed/locked/stale operations do not replace the active session.
- Independent review found that retained nonblank text could clear a pending flag after importing an unrelated response.
  Imports now report the exact identities with validated nonblank supplied targets. Only those identities resolve a
  pending flag; revoked flags additionally need qualifying approval provenance. Blank responses, omitted identities and
  policy-rejected drafts cannot restore approval. A fresh valid correction can qualify under the rules again.

Tests: `GlossaryServiceTest`, `TerminologyConsistencyAuditorTest`, `TranslationWorkflowControllerTest`, the existing AI
exchange/policy suites, and an actual revoke-button action in `GuiChecklistTest`.

## Real-mod review and source authority

The retained FlowerGod report has 908 previously approved entries and 712 review entries. Inspection separates the review
work into **350 TEXT units missing drafts** (141 mission lines, 208 JAR units, one JSON unit) and **362 LINKED units in 118
groups**. Of the LINKED units, 163 had a draft reported in the earlier run and 199 did not. Those draft targets are absent
from the retained review project, so recovering the original translation document is necessary before reusing them.

A local review packet contains stable IDs, exact source/context, reasons, complete group membership, blank target fields,
and a separate copy of the previously approved entries. Its 412-file source inventory matches
`1dd55f3c96566ab4f11e99f0c81fe92834e07b92d1f6b13e9edf30fd7a31a3a0`.
No new translation, glossary term or LINKED group was approved. FlowerGod remains BLOCKED_REVIEW; no real candidate ZIP
or release was produced. No license/permission/readme-named file was found in that source inventory; external permission
is not established by this observation and the redistribution-rights gate remains UNKNOWN.

Nightcross's outer ZIP is unchanged at
`a6baabc3c935c99cf881312f738fa044bc7b8b6fed6fbf9ed569e77e3fe2b65b`.
Its nested JAR is `31e7706c6719cf1304bb3aea3e5246242224560ea4c5f6d8c356afe3ed97f6b4` (1,119,023 bytes).
All **442 entries**, including **436 classes**, fail standard CRC verification; their central CRC fields are zero.
This was independently reproduced with bounded standard ZIP reads. No failed payload was treated as valid, no CRC was
bypassed, no class was loaded/executed and no archive was repaired. An authoritative replacement remains necessary.

## JSON keys and interoperability

The prior Mirfak inventories contain three CJK `designTypeColors` keys in each of two versions. Same-text CSV/JAR units
are useful review context but do not establish all consumers. No evidence justifies changing the conservative bare-key
UNKNOWN policy; P8.10 stays open. The review packet keeps group membership and consumer uncertainty explicit.

The current BridgeForge extension/runtime-context suites passed **seven tests**. Both repositories' extension profile
and two fixture JSON documents are byte-identical. A neutral Project Go `assess --coverage --coverage-manifest` run
successfully produced `ssmt-localization-coverage-1`, bound to source fingerprint
`8fedc6b762488dd280d540cf058b727ab798a74b9b968c326850637547387f04`.
BridgeForge now has a read-only `localization_coverage.observe` API and regression tests. Actual exported-manifest
acceptance matches that fingerprint and preserves the source. Strict schema/count/path validation, changed bytes,
Unicode ordering, bounded reads and hardlink rejection are covered. Its focused run reported 13 tests, zero failures,
one skipped symlink test because Windows denied symlink creation; Ruff passed. CLI wiring and hosted consumer acceptance
remain pending. Identity MATCH never establishes complete coverage, approval, rights, archive identity or runtime proof.

## GUI and release preparation

The refreshed real-window checklist has **16 rows: 11 AUTOMATED_PASS and five NOT_AUTOMATED**, with 15 screenshots.
The owner marked **Revoke automatic approvals PASS** on 2026-10-20 after reviewing its dialog and result screenshots.
The other 15 owner statuses remain NOT_TESTED. A local review index lists each expected result, program observation and screenshot.
Native chooser/Explorer interactions, cancellation timing and the other manual rows need actual owner observation.
Screenshots and assertions are not substituted for that observation.

The previously pushed `50aa17a` passed Windows and Ubuntu build jobs in
[CI run 39433687431](https://github.com/Exxec/Project-Go/actions/runs/39433687431).
Both hosted evidence artifacts were downloaded locally. Their source records match that commit and their CLI/GUI/Auto
chain reports retain downloaded-asset NOT_RUN and independent UNKNOWN rights/live/native/publication gates.
Those historical hosted results do not certify subsequent local changes or establish a new product release.

Release readiness still requires the selected version, exact-commit workflow, final approved candidate/package audit,
rights disposition, attempt feedback, and independently downloaded release-asset hashes. No version tag or product
release is authorized by merely committing/pushing source. Game scenarios remain untested as requested.

## Validation and retained evidence

Final Project Go command:

```powershell
.\gradlew.bat build :ssmt-gui:guiChecklist :ssmt-auto:verifyNativeUnicode --offline --no-daemon --max-workers=2 --console=plain
```

PASS: **735 tests reported, zero failures/errors, two intentional skips**; full static checks, Windows launcher, native
Auto Unicode and the separate real-window checklist. The final run completed 120 actionable tasks (42 executed,
78 up-to-date). Earlier focused failures are retained as diagnostic history, not green receipts.

Local evidence, intentionally outside version control, is under `.local/roadmap-next-20261020/`:
`final-build.log`, `test-totals.json`, `bridgeforge-conformance.log`, `flowergod-review/`, `gui-review/`,
`nightcross-independent-check.json`, and `hosted-evidence-50aa17a/`.
