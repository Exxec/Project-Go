# Roadmap phase handoff - 2026-09-23

This checkpoint began from commit
`1e084c03c31d5616cde333861621fff8999f2227` and records the subsequent
local work. It separates completed tooling from actions that require a person,
a real candidate, or a new release.
Do not infer a runtime or release result from an offline pass.

GitHub release metadata was checked again on 2026-09-23: the public
`v0.8.0-rc.1` pre-release still has uploaded assets, including the Windows
ZIP with SHA-256 `c837c3b5b9434fb84474be3ec570883370785ca023f7e00ed20c3f8b0159d49a`.
The local tag resolves to `8e9e55f6f61e9861ba69cd22325142d2b1bbb36b`.

| Phase | Current state | Next evidence required |
| --- | --- | --- |
| P0 release | Historical `v0.8.0-rc.1` publication verified by `RELEASE_0.8.0_RC1_VERIFICATION.md`. Later main commits are unreleased. | For the next version: exact-commit/tag CI, rebuilt native assets, downloaded hashes, embedded identity, and release record. |
| P1 simple workflow | Shared implementation complete. Packaged CLI and Auto synthetic ZIP-to-output acceptance passed. Native GUI picker and fresh-profile file drag/drop each completed ZIP-to-output; export cancellation and invalid-response preservation passed. | Repeat native acceptance against the exact source and assets of a future release; this development-image pass is local P1 evidence only. |
| P2 assessment | Read-only deterministic tooling complete at assessment boundary. | Run per candidate and review authority/escalation findings; do not infer revival. |
| P3 coverage | Inventories, advisory gaps, opt-in schemas, preservation, and read-only ZIP coverage implemented. Standard CSV selection-gap findings now appear in directory and ZIP `assess --coverage` reports, including later-row text. Nightcross nested JAR integrity blocks its coverage run; the archived JAR bytes match the separate original directory. | Obtain an authoritative replacement or reviewed repair for the invalid Nightcross JAR, and human acceptance of any new ecosystem format before fixture-first implementation. |
| P4 package audit | Source, clone, ZIP and build-evidence tooling implemented. | Run audits on the final candidate and release bytes. |
| P5 runtime | Capture and separate gates implemented. | Execute real boot, campaign, combat, save/reload and upgrade scenarios as applicable. |
| P6 completion | Evidence-derived status, feedback, and finalization tooling implemented. The two classified Nightcross archive-assessment surprises were reviewed; the hash-bound local packet now verifies as `READY_FOR_NEXT_CANDIDATE`. | Archive feedback with a future candidate record, then finalize only against actual candidate and release bytes after manual and rights gates. |

## Local validation

### Offline candidate gate review - 2026-09-24

The read-only Nightcross integrity check compared the nested
`Nightcross/jars/nightcross.jar` from `.local/inputs/nightcross.zip` with the
separate original mod directory. Both JARs are SHA-256
`31e7706c6719cf1304bb3aea3e5246242224560ea4c5f6d8c356afe3ed97f6b4`
(1,119,023 bytes). The containing ZIP remained SHA-256
`a6baabc3c935c99cf881312f738fa044bc7b8b6fed6fbf9ed569e77e3fe2b65b`.
The prior translated output's JAR is different
(`5b6dbedb98eeba08ef876415c1057c6985de94b996c17f4847d186a55f0ff6ca`).
This establishes that this ZIP copy did not introduce the observed CRC defect;
it does not authenticate the original JAR, validate its entries, or authorize
substituting the older translated output. The current partial assessment still
blocks on `.idea/.gitignore` CRC mismatch and claims no Nightcross coverage.

The two surprises in the ignored
`.local/acceptance/nightcross-feedback-20260923/` packet were reviewed against
the hash-bound evidence, regression fixtures, and source-change snapshots.
The packaged `attempt-feedback` verifier returned exit 0 and
`READY_FOR_NEXT_CANDIDATE` for the same 2,374-file selected-root fingerprint
`4f5552b3a6b1aec13ec15046d99337975f5cade6a05447a34fe3a8598f19d655`.
The reviewed packet is SHA-256
`9d23baed71123a816be42bcf3aa47b5001195852f3b8eb69edd092c2eb392cea`.
Its local review permits another independent candidate assessment, not
Nightcross translation completion or release finalization. The packet remains
ignored, disposable local evidence until archived with a future candidate.

No further pre-live phase can be closed for Nightcross from current inputs:
the current export has 12,918 selected entries while the available response
covers 12,745; no current translated final candidate or matching final ZIP
exists for P4. P3 requires JAR authority and format acceptance, and P5 requires
actual game scenarios. P6 finalization requires the resulting candidate and
release bytes, live-test and persistence dispositions, and rights review.

The next P3 gap tranche adds read-only standard CSV selection-gap findings to
directory and ZIP `assess --coverage` reports. A recognized column whose first
row is ASCII but a later row contains non-ASCII text now receives a review
finding; the prior detector skipped it. Reads and samples are bounded, and a
failed extraction claims no CSV gap result. ZIP-backed JSON and CSV gap paths
are now serialized as mod-relative strings, not absolute `jar:file:` URIs.
Focused extractor and CLI tests plus Checkstyle passed with all 26 tasks
executed. An initially absent profile full check and CLI/GUI/Auto distribution
rebuild passed 578 tests in 131 suites, zero failures/errors/skips, and all
102 tasks executed; see `.local/checks/csv-gap-portable-full.log`. The rebuilt
packaged CLI reported a later-row `groupTag` sample `技术` at
`data/weapons/weapon_data.csv` from a nested-root ZIP. The fixture remained
SHA-256 `001122506b3c47071a2ed448df8b40aae0fd27b3747288d5ce4099d0f81d740a`
and no wrapper tree was extracted. This is advisory coverage, not approval to
translate the column or a real-candidate/release result.

Before committing this checkpoint, an initially absent `LOCALAPPDATA` profile
and offline uncached full `check` plus CLI/GUI/Auto `installDist` rebuild
completed all 102 tasks. The 131 JUnit XML suites report 577 tests, zero
failures/errors/skips; see `.local/checks/commit-20260923-full.log`. The rebuilt
CLI reported `SSMT 0.8.0-rc.1`, and the GUI and Auto packaged launcher smoke
checks exited zero. These pre-commit distribution manifests still identify the
base commit; they are not release assets.

A second native GUI run started with an absent profile at
`.local/acceptance/gui-native-drop-fresh-20260923/profile/`. A Windows file
drag of the synthetic fixture ZIP returned `Copy`; the GUI showed `Example
Mod`, `0 of 1 translated`, and a ready project. From that dropped ZIP it
exported a request, imported the arbitrarily named response through the
native picker, and installed one `Example Mod - English` folder through the
native folder picker. The visible GUI reported `All 1 texts translated` and
`Your English copy is installed`. The request, response, unchanged source
ZIP, and translated JSON SHA-256 values match the first native GUI run below;
the translated file contains `{"welcome":"Greetings"}`. The output parent
contains exactly one folder and no backup sibling. Windows Save As initially
put the synthetic request in Downloads; an identical hash-verified copy was
kept in the isolated evidence root and that generated Downloads file was
removed. This completes the local P1 native drop-to-output path on a fresh
profile. It does not establish game runtime behavior or release asset identity.

The resumed native Windows GUI run at
`.local/acceptance/gui-native-resume-20260923/` completed the synthetic
ZIP-to-output path on an isolated profile. The GUI selected the fixture ZIP
through the real Windows picker, exported `request.json` through Save As,
imported the arbitrarily named `answer-any-name.json`, and installed one
`Example Mod - English` folder through the native folder picker. The visible
GUI showed `All 1 texts translated` and then `Your English copy is installed`.
The request SHA-256 is
`76ec72dc7c5520cb02dccb59ea676fefebf05be22c781d8d4596735afaac127b`;
the response SHA-256 is
`1e7877eb952c45e836c4778a7ba7d644a6575459df02f83c98d3a3d776b3ad34`.
The source ZIP stayed SHA-256
`6175504d9ade037886a5429d479faa3821ef01c3ca211b47422dc47b7f3e8b25`.
The one translated JSON file contains `{"welcome":"Greetings"}` and is
SHA-256 `7cfde43c6dd2141a47425f5ba228251e73c7fa6bdc463a3254a913cf20e28494`.
The output parent contains exactly one folder and no backup sibling.

Cancelling a second native Save As dialog left that output intact. Importing
an isolated invalid `{}` response produced the visible `AI response schema
version is unsupported` error and a diagnostic report under the isolated
profile. The GUI retained `All 1 texts translated`; the source ZIP and
translated JSON hashes and the single output-folder count were unchanged.
Native file drag/drop was not exercised in this first run; the fresh-profile
run above covers it. The first sandboxed GUI
launch failed in JavaFX runtime/cache access; the successful native run used
the unsandboxed local desktop session. This is development-image evidence,
not an exact-source release acceptance result.

An earlier native GUI acceptance attempt advanced one boundary: the rebuilt Windows image opened
with an initially absent profile at `.local/acceptance/gui-native-20260923/`.
Its real Windows ZIP picker selected the synthetic fixture ZIP, after which
the visible GUI reported `Example Mod`, `0 of 1 translated`, and a ready
project. The ZIP remained SHA-256
`6175504d9ade037886a5429d479faa3821ef01c3ca211b47422dc47b7f3e8b25`.
The export dialog attempt did not retain Project Go as the foreground window,
so no path was entered and no GUI request or output was produced in that attempt. Only the
two Project Go processes launched for this run were closed. This proves ZIP
picker intake for that attempt. The resumed run above completed GUI
ZIP-to-output; the later fresh-profile run covers drag/drop.

Earlier checkpoint after the structured JAR-integrity finding and complete
embedded-JAR hash fix: an initially absent `LOCALAPPDATA` full check plus
CLI/GUI/Auto `installDist` rebuild passed 576 tests in 131 suites, with zero
failures/errors/skips and all 102 tasks executed. The packaged CLI reported
`SSMT 0.8.0-rc.1`; GUI and Auto launcher smoke exits were zero. The Windows
native GUI/Auto development bundle checksum, image builds, and image smoke
tasks then passed. These are local working-source checks; the modified source
has no exact-commit CI or new release artifact.

Coverage-only ZIP assessment now checks embedded JAR integrity before standard
extraction, then emits a partial report for the same Nightcross CRC failure.
The rebuilt packaged CLI returned exit 1, `NOT_ASSESSED_INVALID_JAR`, a
`BLOCKING JAR_ENTRY_INTEGRITY_FAILED` finding, and an explicit
`jarInventoryStatus=NOT_ASSESSED`; it did not claim JAR contents. The source ZIP
SHA-256 was unchanged. The final fresh-profile check and CLI/GUI/Auto
`installDist` rebuild passed 576 tests in 131 suites with zero
failures/errors/skips and all 102 tasks executed. Evidence:
`.local/checks/roadmap-20260923-coverage-only-full.log` and
`.local/checks/nightcross-coverage-only-20260923.json`. The native development
bundle checksum passed again; the GUI and Auto image smoke tasks executed, while
the unchanged image builds and checksum were up to date. See
`.local/checks/roadmap-20260923-coverage-only-native.log`.

A valid candidate with malformed selected JSON now also retains a partial
`assess --coverage` report for both directories and ZIPs. It names the portable
source path in a `BLOCKING COVERAGE_SOURCE_PARSE_FAILED` finding, marks coverage
`INCOMPLETE_SOURCE_PARSE`, and claims no extraction or JSON gap counts. The
focused regression checks exit 1, source immutability, and no extracted ZIP
wrapper. The full-suite checkpoint for this additional change is recorded
in `.local/checks/roadmap-20260923-parse-partial-full.log`: a fresh-profile
CLI/GUI/Auto `installDist` rebuild and full check passed 577 tests in 131
suites with zero failures/errors/skips, all 102 tasks executed. The native
development bundle checksum and GUI/Auto image smoke tasks passed again; see
`.local/checks/roadmap-20260923-parse-partial-native.log`. These development
artifacts do not have exact-source release identity.

After the ZIP coverage and CRC diagnostic changes, a second fresh-profile
full check and CLI/GUI/Auto `installDist` rebuild succeeded: 102 tasks
executed, 574 tests in 131 JUnit XML suites, zero failures/errors/skips.
The rebuilt CLI reported `SSMT 0.8.0-rc.1`; GUI and Auto packaged launcher
smoke tests exited zero. `:ssmt-gui:developmentBundleChecksum` then built
and smoke-tested the Windows GUI and Auto native images and completed its
checksum task. The resulting manifests still name committed HEAD while the
source is modified, so these are development binaries, not exact-source
release assets. The known native-access, Gradle 10, and static-analysis
missing-class warnings remain visible.

The checkout was clean before this documentation update. With
`GRADLE_USER_HOME` set to the repository's `.gradle-user-home` and an initially
absent `LOCALAPPDATA` under `.local/checks/`, this command succeeded outside
the filesystem sandbox:

```powershell
.\gradlew.bat check :ssmt-cli:installDist :ssmt-gui:installDist :ssmt-auto:installDist --rerun-tasks --no-build-cache --offline --max-workers=1 --no-daemon --console=plain
```

All 102 tasks executed. The 131 JUnit XML suites total 572 tests, zero
failures/errors/skips. The three packaged app JAR manifests report version
`0.8.0-rc.1` and commit `1e084c03c31d5616cde333861621fff8999f2227`.
The GUI packaged launcher passed `--smoke-test`. Native-access, Gradle 10,
and SpotBugs missing-class warnings remain visible.

An initial sandboxed full run failed on `AccessDeniedException` at the core
JAR; another reached Auto tests but all 14 failed to resolve JUnit temporary
workspaces, including when temp was under `.local/checks/`. Running the Auto
module outside this session's filesystem sandbox passed all 14 tests, then
the full rebuild passed. These sandbox failures are not product test passes
or a confirmed application defect.

The ignored `.local/acceptance/roadmap-20260923/` folder contains the
synthetic inputs, CLI/Auto logs, and outputs. The ZIP has SHA-256
`6175504d9ade037886a5429d479faa3821ef01c3ca211b47422dc47b7f3e8b25`.
Both packaged paths exported one selected entry, imported an arbitrarily
named response, and published one translated folder. The source remains
`{"welcome":"Hello"}`; each output has `{"welcome":"Greetings"}`.
Neither path published a source backup or Changes CSV sibling. This proves
only a synthetic offline path, not interactive GUI or game behavior.

A separate, byte-matched copy of Nightcross ZIP was loaded through packaged
CLI. Its SHA-256 before and after was
`a6baabc3c935c99cf881312f738fa044bc7b8b6fed6fbf9ed569e77e3fe2b65b`.
The current export has 12,918 selected entries. The older saved response
was for 12,745 entries, so it was not imported and no Nightcross clone was
published. Do not fill the 173-entry difference by guessing translations.

ZIP coverage now mounts the validated archive without writing an extracted
tree. A nested-root synthetic fixture passes standard coverage, JSON gap
review, and nested JAR class/resource handling. On Nightcross, basic
assessment still selects `Nightcross` and inventories 2,374 files without
changing the ZIP; `--jar-inventory` and `--coverage` fail closed on
`jars/nightcross.jar` entry `.idea/.gitignore` because its stored CRC is zero
and does not match its bytes. See `COVERAGE_IMPLEMENTATION.md`. This is a
candidate-specific integrity gate, not evidence that its other payloads are
safe or unsafe.

The packaged CLI now emits a partial JSON assessment for that gate with exit
code 1, `INCOMPLETE_JAR_INTEGRITY` and `NOT_ASSESSED_INVALID_JAR`, plus a
`BLOCKING` finding naming the JAR and entry. The report was parsed directly
from the packaged launcher with the host's `DEBUG` environment variable
temporarily unset; that variable otherwise makes Gradle's Windows batch
launcher echo commands around its JSON. The Nightcross ZIP hash stayed the
same. A repository regression covers the partial report and a separate
regression binds the complete nested-JAR bytes, including trailing ZIP data.

An ignored `.local/acceptance/nightcross-feedback-20260923/` packet now binds
the selected Nightcross tree hash
`4f5552b3a6b1aec13ec15046d99337975f5cade6a05447a34fe3a8598f19d655`
to two classified surprises: the candidate-specific JAR CRC mismatch and the
detector gap that previously lost the candidate report on requested JAR
inventory. It includes six distinct SHA-256 references to the observed
reports, repository-owned regression fixtures, and source changes. The
packaged `attempt-feedback` verifier initially checked it with expected exit
1 and status `REVIEW_PENDING`. The source ZIP's selected-root hash and the old
extraction cache's 2,374-file tree hash match. The 2026-09-24 review above
changed the local packet to `READY_FOR_NEXT_CANDIDATE`; it remains disposable
until archived and does not establish Nightcross authority or completion.

## Resume

1. Review `ROADMAP.md` and this handoff against the then-current HEAD and
   worktree before continuing. `.local` evidence is disposable, not tracked
   proof of a future release.
2. For Nightcross or another real candidate, export from the exact selected
   archive, translate/review every current entry, then run source/clone/ZIP
   audits and independent runtime and persistence gates. Keep original mods
   and saves unchanged.
3. Build and publish any later version only from its exact source commit and
   tag after full tests and native acceptance; verify downloaded release
   assets and rights separately.
