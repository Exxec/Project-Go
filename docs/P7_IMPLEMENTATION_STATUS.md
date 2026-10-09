# P7 implementation status

Implemented 2026-10-05 against Project Go baseline `3e2cc26`, version `0.8.0-rc.1`.
Scope: ROADMAP.md P7 items 1-7 and BRIDGEFORGE_METHOD_CONVERSION.md.
These are local development builds; no commit, publication or live-game readiness is implied.

## Result

Project Go now uses the BridgeForge translation method for new CJK projects in
GUI, CLI and Auto. Schema catalogs are advisory on that path. Persisted projects
carry a source-bound `bridgeforgeTranslation` snapshot; older project files,
non-CJK extraction and read-only assessment retain their existing supported paths.
Those paths still have callers and were not removed as presumed dead code.
Translation memory storage and draft approval policy remain in place.

The direct `ssmt translate` commands exchange schema-v1 documents losslessly,
including extension fields. Discovery covers raw CSV cells, lenient JSON-like
values and keys, loose Java literals and referenced JAR string constants. IDs,
contexts, discovery order and SHA-256 hashes match the current Python reference.

Apply verifies source hashes, mod identity and protected entry fields before
writing. It preflights every replacement, rejects path traversal/symlink source
paths and refuses output overwrites. `--out` publishes a separate translated
copy; `--in-place` is an explicit selection of a disposable working copy.
GUI/Auto publication continues through the audited transaction publisher.
Translated metadata cannot change protected game-loading fields.
CSV row shapes, JSON key collisions/structure, class constants and archive CRCs
are checked. Limits reject excessive documents/archives and unsupported ZIP64,
encrypted or duplicate-member archives.

Class rewriting preserves constant-pool indices and modified UTF-8, changing
only constants referenced by CONSTANT_String. JAR output matches Python ZipFile
header, flag, attribute and compression behavior, including non-target members.
Placeholder checks handle Java conversions, reindexing, ASCII dollar variables,
highlight markers and literal percent signs. Partial translation/unreadable
results retain a non-success disposition. Record and aligned-reference prefill
preserve ambiguities rather than choosing an arbitrary candidate.

## Shared corrections and fixtures

`fixtures/translation-conformance/VERSION` is `1` in both repositories. The
repository-owned corpus contains 18 CJK units and 12 output files, covering
ragged/duplicate/comment CSV rows, legacy GB18030 CSV, JSON keys/barewords/single
quotes and CRLF, Java comments/literals and line endings, exclusions, translator
records, modified UTF-8 and long constant-pool slots, and ZIP metadata edge cases.
All copied fixture paths and bytes must match.

Three narrowly scoped reference corrections also landed in BridgeForge:
CSV compatibility decoding/output preserves GB18030; Java span application
preserves input line endings; JSON-like discovery/application preserves CRLF.
Each observed failure gained a regression before the correction. They avoid
normalizing untouched source text and make cross-platform exact-byte application
possible. No upstream mod or game installation was edited.

## Validation evidence

| Gate | Result and boundary |
|---|---|
| Source review / compile / static checks | PASS: Java `-Xlint:all -Werror`, Checkstyle and SpotBugs through full Gradle check. |
| Project Go full suite | PASS: 594 tests, no failures/errors/skips, uncached fresh-profile run, all 107 tasks executed. Final log: `build-p7-final-launcher.log`. |
| Shared fixture export/apply | PASS: identical IDs, hashes, contexts and complete output bytes, including JAR bytes; tampering/source drift refused. |
| FlowerGod historical original-copy discovery | PASS: 1,479 units in 101 files, no missing/extra/different entries; source hashes match. This input differs from the older 1,296-unit working-copy snapshot. |
| FlowerGod isolated apply | PASS: all 412 files byte-identical; JSON 132, CSV 504, JAR 843 applied; zero leftovers/problems. Neutral conformance translations, not an English release. |
| Nightcross author-record prefill | PASS: both full documents equal; 1,671 units, 1,501 filled, 167 ambiguous. Does not close Nightcross integrity/runtime gates. |
| Packaged Windows CLI | PASS: spaces in installation/JAVA_HOME, CJK source/output paths, 18 units, all 12 apply-output files exact, zero leftovers, preserved source and error exit codes; durable `:ssmt-cli:verifyWindowsLauncher` gate runs under `check`. |
| GUI package engine | PASS: actual rebuilt native-image dependency jars through TranslationWorkflow load/export/import/build: 18 units, all 12 exact output files, source hashes unchanged. |
| Native Auto package | PASS on ASCII input path: initial request, arbitrary-named pure schema-v1 response import, publication, all 12 exact output files, source unchanged. Unicode display names/output directories work. |
| Native image smoke | PASS: rebuilt GUI and Auto images, smoke/version tasks. |
| BridgeForge focused checks | PASS: 25 translation/conformance tests; Ruff on the three changed Python files. |
| BridgeForge full test guard | 1,509 tests, two unrelated failures, three skips; hermeticity PASS. Existing scanner edits make docs/CHECKS.md stale (198 vs 200 findings); unchanged process-CWD test could not observe a child. No unrelated repair was made. |
| Interactive GUI chooser/drop | NOT_TESTED in this implementation session. Existing P1 gates remain open. |
| Live Starsector / save behavior / rights / release | LIVE TEST NOT PERFORMED; existing P1-P6 candidate/release gates remain open. |

Small real-mod/package receipts are in `docs/evidence/p7/`; full local evidence
is under BridgeForge `In operation/_p7/evidence/`. Original source trees remain
untouched. Build logs are local evidence, not committed release artifacts.

## Windows launcher boundary and follow-up

The generated CLI batch file quotes assignments and delegates to the packaged
PowerShell helper, which transfers arguments through a temporary UTF-8 Picocli
argument file. This avoids the observed JVM ANSI conversion before Java sees
CJK arguments; it does not guess a replacement for corrupted paths. Temporary
arguments are removed and the JVM exit code is propagated. Windows PowerShell
is required by this Windows launcher; the Unix launcher remains generated normally.

The native `Project Go Auto.exe` still replaces CJK **input command arguments**
with question marks on this Windows/JDK installation. Backend Unicode tests pass,
and native workflow acceptance passed on an ASCII source path, but dropping a
CJK input path onto that executable is not cleared. A separate native launcher
fix and interactive regression are required before claiming unrestricted Unicode
native Auto readiness. GUI picker/drop interaction also requires visual acceptance.
P7's translation implementation and bounded package gates are complete; these
explicit launcher/interactive limitations remain review items, not live or release
approval. Final disposition: `READY_WITH_REVIEW_ITEMS`.

## Local outputs

Follow-up 2026-10-08: P8 now closes the native Auto Unicode **command-argument**
limitation described above. The actual packaged executable passed the CJK,
spaced/ampersand folder and metadata-path workflow, arbitrary-named response,
exact-output and unchanged-source regression. See
[GUI_WORKFLOW_ENHANCEMENTS.md](GUI_WORKFLOW_ENHANCEMENTS.md). Explorer drag/drop,
visual GUI acceptance, live behavior and release/rights gates remain separate.

- CLI: `ssmt-cli/build/install/ssmt-cli/` (batch plus PowerShell helper).
- GUI distribution: `ssmt-gui/build/install/ssmt-gui/`.
- Auto distribution: `ssmt-auto/build/install/ssmt-auto/`.
- Native GUI: `ssmt-gui/build/jpackage/Project Go/`.
- Native Auto: `ssmt-auto/build/jpackage/Project Go Auto/`.
