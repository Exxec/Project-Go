# Non-live roadmap follow-up, 2026-10-20

Receipt refresh: 2026-10-21. Earlier measurements below remain dated checkpoint evidence; they were not rerun
for this documentation update.

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
from the retained review project, so recovering the original translation document is necessary before reusing those targets.
The owner noted on 2026-10-21 that the drafts may need to be restarted. If recovery is unavailable, prepare a fresh
draft pass from the preserved source and current review packet, retaining the earlier project and approved-entry copy.
This is a possible next step, not a completed restart or approval: no new drafts were generated or sent to a provider.
Any regenerated drafts still require validation and the applicable approval rules; LINKED groups remain all-or-none.

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

## Resumable draft recovery (roadmap H1)

`ssmt-cli recover-drafts` re-drafts review units whose target text is missing, in bounded, resumable batches. It reads a
review packet (`review.json`, plus `approved.json` when present) and writes only into a separate `--workspace`; it makes no
network call, generates no draft, and approves nothing.

```
ssmt-cli recover-drafts plan   --packet DIR --workspace DIR [--batch-size 50]   # counts + plan.json
ssmt-cli recover-drafts export --packet DIR --workspace DIR [--batch-size 50]   # batches/batch-NNNN.json + manifest
ssmt-cli recover-drafts import --packet DIR --workspace DIR BATCH.json          # one filled batch
ssmt-cli recover-drafts status --workspace DIR
```

- Selected units: every non-IDENTIFIER review unit whose current and retained targets are blank. Identity is the unit id plus
  the SHA-256 of its exact source text. Order is deterministic (sorted ids, no timestamps).
- A LINKED group is one item and is never split across batches (a group larger than the batch size gets its own batch).
- Each exported batch is recorded in `recovery-manifest.json` (written atomically). Re-running `export` skips recorded
  batches and never overwrites a batch file the owner is filling in; a different `--batch-size` needs a new workspace.
- `import` accepts or rejects the whole batch: unknown identities, identities from another batch, a changed source digest
  (stale), duplicates, blank drafts, a missing member of a LINKED group, or different texts inside one group reject it with
  nothing written. Accepted text goes to `drafts.json` as `DRAFT_NEEDS_REVIEW`; the normal validation and approval rules
  still apply afterwards. Importing an already imported batch changes nothing.
- The manifest records digests of `review.json` and `approved.json`; every later operation fails as stale if either changed,
  and the workspace may not sit inside the packet, so the earlier project and the approved entries stay untouched.

Tests: `DraftRecoveryServiceTest` (batching with LINKED groups, determinism, resume after interruption, stale/unknown/partial
rejection, approved and review files byte-identical) and `RecoverDraftsCommandTest`. The retained FlowerGod project file is not
on disk, so the review packet is the input; a project-document adapter is future work. Gate status is unchanged.

## JSON keys and interoperability

The prior Mirfak inventories contain three CJK `designTypeColors` keys in each of two versions. Same-text CSV/JAR units
are useful review context but do not establish all consumers. No evidence justifies changing the conservative bare-key
UNKNOWN policy; P8.10 stays open. The review packet keeps group membership and consumer uncertainty explicit.

At the 2026-10-20 local checkpoint, BridgeForge extension/runtime-context suites passed **seven tests**.
Both repositories' extension profile
and two fixture JSON documents are byte-identical. A neutral Project Go `assess --coverage --coverage-manifest` run
successfully produced `ssmt-localization-coverage-1`, bound to source fingerprint
`8fedc6b762488dd280d540cf058b727ab798a74b9b968c326850637547387f04`.
BridgeForge now has a read-only `localization_coverage.observe` API and regression tests. Actual exported-manifest
acceptance matches that fingerprint and preserves the source. Strict schema/count/path validation, changed bytes,
Unicode ordering, bounded reads and hardlink rejection are covered. Its focused run reported 13 tests, zero failures,
one skipped symlink test because Windows denied symlink creation; Ruff passed. CLI wiring and end-to-end hosted
acceptance of an actual exported coverage manifest remain pending; the hosted shared-fixture gate below has passed.
H3 (2026-10-22) adds the contract check for the latter: see `fixtures/coverage-manifest/README.md`.
Identity MATCH never establishes complete coverage, approval, rights, archive identity or runtime proof.

Fresh-checkout follow-up: the hosted shared-fixture detector exposed runtime fixture line-ending/hash drift missed by
the earlier working-copy comparison. Project Go now uses BridgeForge's committed LF bytes and synthetic shipped hash
`5d50ffbb82d40be9ebc957cc9e6bd36ca9ba92a27f72aca7d5ed93ad09ce1fab`. Both shared fixture trees, including their file sets,
match exactly. The earlier CRLF-bound receipt is historical; it does not supersede this producer-bound comparison.
The earlier importer branch receipt (`cc7d6919`, 2,284 local tests with 12 skips and hermeticity PASS) is historical.
The importer and required shared-fixture gate are integrated into BridgeForge main, with exact-commit
[CI run 39510390276](https://github.com/Exxec/Project-BridgeForge/actions/runs/39510390276) passing at `ae825d61`.
Project Go main at `dd9c2fb` passed Windows and Ubuntu in
[CI run 39502317335](https://github.com/Exxec/Project-Go/actions/runs/39502317335). These are source/CI receipts,
not live-game or release acceptance.

BridgeForge main moved to Python 3.15 on 2026-10-21 (PR #54, `cf4879a7`): `requires-python >=3.15`, CI on 3.15 only,
2,516 local tests OK. The earlier `build/python315-20261020` branch (`c82e2d54`) is superseded and was not merged.

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

Historical 2026-10-20 local Project Go command (not rerun for the receipt refresh):

```powershell
.\gradlew.bat build :ssmt-gui:guiChecklist :ssmt-auto:verifyNativeUnicode --offline --no-daemon --max-workers=2 --console=plain
```

PASS: **735 tests reported, zero failures/errors, two intentional skips**; full static checks, Windows launcher, native
Auto Unicode and the separate real-window checklist. The final run completed 120 actionable tasks (42 executed,
78 up-to-date). Earlier focused failures are retained as diagnostic history, not green receipts.

Local evidence, intentionally outside version control, is under `.local/roadmap-next-20261020/`:
`final-build.log`, `test-totals.json`, `bridgeforge-conformance.log`, `flowergod-review/`, `gui-review/`,
`nightcross-independent-check.json`, and `hosted-evidence-50aa17a/`.
