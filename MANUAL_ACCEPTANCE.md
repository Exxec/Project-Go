# SSMT Release-Candidate Manual Acceptance

`Last updated: 2026-08-02 by Codex (ADR-041 translated-clone acceptance workflow)`

Use this checklist on the exact unsigned release-candidate bundle. Record the
tester, date, Windows version, Starsector version, fixture, artifact checksum,
and pass/fail evidence. A blank box is not evidence.

## Desktop workflow

- [ ] Launch `SSMT.exe` from the self-contained image on a clean Windows host.
- [ ] Create a project from `mod_info.json`; confirm neighboring project,
  words, translated-words, pristine-backup, and translated-clone artifacts
  follow the documented names.
- [ ] Open an existing project and an existing SQLite translation memory.
- [ ] Confirm the GUI automatically opens the shared headless catalog, remembers
  an explicitly opened catalog after restart, and safely previews a second
  catalog before merge.
- [ ] Edit, validate, save, close, reopen, and confirm the edit persists.
- [ ] Export for online AI, inspect its language suggestion/instructions,
  import a valid response, and confirm drafts require review.
- [ ] Build twice and confirm the second build reports unchanged.
- [ ] Refresh after a controlled update; confirm suggestions are not
  auto-applied and apply only after explicit confirmation.
- [ ] Cancel build/refresh and confirm no partial output is published.
- [ ] Discover an empty plugin directory and a malformed archive; confirm
  discovery is read-only and failure detail is visible.
- [ ] Create a project with the "Create Project" split button's JSON-schema
  and CSV-schema dropdown variants; confirm both extract the schema's
  declared fields without affecting standard extraction.
- [ ] Check font coverage against a real Starsector `.fnt` file with
  untranslated non-Latin text present; confirm findings name the file,
  entry, and missing characters. Confirm the toolbar's live
  "Translated X/Y (Z%)" label updates as you translate.
- [ ] Open an image with baked-in text: auto-detect regions with a real
  Tesseract install, translate them, and produce a localized image both
  ways — the Java2D "Render Localized Image (Text)" path and the AI-regeneration round trip
  (export crop + instructions, hand it to an external AI image tool,
  import the result, confirm wrong-dimension imports are rejected,
  composite). Confirm adding a region or re-running auto-detect after
  translating others does not discard existing translations/imported art.
- [ ] Complete the keyboard-only and high-DPI checks in `ACCESSIBILITY.md`.

## Headless and CLI workflow

- [ ] Drag `mod_info.json` onto `SSMT Auto.exe`; confirm documented artifact
  naming and shared SQLite catalog location.
- [ ] Repeat with an unchanged mod and with a newer mod version.
- [ ] Run the full CLI example in `USER_GUIDE.md` from a path containing spaces
  and non-ASCII characters.

## Source safety

- [ ] Capture source count, SHA-256 hashes, and timestamps before and after all
  workflows; confirm they are identical.
- [ ] Use a copy containing `"autofire": Ture`; confirm SSMT suggests
  “did you mean true,” does not modify it, and does not build output.
- [ ] Confirm both clones are outside the source mod directory. Compare every
  pristine-backup file byte-for-byte with the original source.

## Installed-game smoke test

- [ ] Place the translated clone in Starsector's `mods` directory and keep the
  pristine backup outside `mods`.
- [ ] Disable the original source mod. Enable only the translated clone, start
  the game, and load a suitable save or new game.
- [ ] Confirm translated strings appear and inspect `starsector.log` for errors.

## Load-order empirical test protocol (BUG-009)

**Status after ADR-041:** optional engine research, not a release prerequisite.
Normal builds no longer enable a source and delta overlay together.

**Historical question:** SSMT's old override model relied on an overlay winning
Starsector's cross-mod CSV/JSON merge against its source mod — the engine's
own modding documentation calls two non-core mods sharing a key "undefined
(extraordinarily bad)." Nobody has determined what (if anything) actually
controls that resolution. See `BUGS.md` BUG-009 and `DECISIONS.md` ADR-040
for full context; this section is the only place the step-by-step protocol
lives — don't restate the rationale here.

Ten minimal mod pairs (a "source" and its "overlay"), no real content beyond
`mod_info.json` plus (Phase B only) one `descriptions.csv` row each, reusing
the exact same real-in-Codex-text technique already validated by the Azure
Federation smoke test (`REAL_MOD_COMPATIBILITY.md`). All values below are
literal — copy them exactly, don't compute your own ordering.

| Pair | Source id / name / folder | Overlay id / name / folder | id ordering | name ordering | folder ordering |
|---|---|---|---|---|---|
| 1 | `zprobe01` / `Zprobe One` / `ZProbe01Source` | `zprobe01-patch` / `Zprobe One Patch` / `ZProbe01Source-Patch` | after | after | after |
| 2 | `zprobe02` / `Zprobe Two` / `ZProbe02Source` | `zprobe02-patch` / `0Zprobe Two Patch` / `0ZProbe02Source-Patch` | after | before | before |
| 3 | `zprobe03` / `Zprobe Three` / `ZProbe03Source` | `0zprobe03-patch` / `Zprobe Three Patch` / `0ZProbe03Source-Patch` | before | after | before |
| 4 | `zprobe04` / `Zprobe Four` / `ZProbe04Source` | `0zprobe04-patch` / `0Zprobe Four Patch` / `ZProbe04Source-Patch` | before | before | after |
| 5 | `zprobe05` / `Zprobe Five` / `ZProbe05Source` | `zprobe05-patch` / `Zprobe Five Patch` / `0ZProbe05Source-Patch` | after | after | before |
| 6 | `zprobe06` / `Zprobe Six` / `ZProbe06Source` | `zprobe06-patch` / `0Zprobe Six Patch` / `ZProbe06Source-Patch` | after | before | after |
| 7 | `zprobe07` / `Zprobe Seven` / `ZProbe07Source` | `0zprobe07-patch` / `Zprobe Seven Patch` / `ZProbe07Source-Patch` | before | after | after |
| 8 | `zprobe08` / `Zprobe Eight` / `ZProbe08Source` | `0zprobe08-patch` / `0Zprobe Eight Patch` / `0ZProbe08Source-Patch` | before | before | before |
| 9 (dependency present) | `zprobe09` / `Zprobe Nine` / `ZProbe09Source` | `0zprobe09-patch` / `0Zprobe Nine Patch` / `0ZProbe09Source-Patch` | before | before | before |
| 10 (dependency absent) | `zprobe10` / `Zprobe Ten` / `ZProbe10Source` | `0zprobe10-patch` / `0Zprobe Ten Patch` / `0ZProbe10Source-Patch` | before | before | before |

Every overlay's `mod_info.json` declares `"dependencies": [{"id": "<its source's id>"}]`
**except pair 10**, which omits `dependencies` entirely — isolating whether
the dependency declaration itself affects load order, independent of the
id/name/folder factors (pair 9 is the same lexical shape as pair 10, with
`dependencies` present, as the control).

- [ ] **Phase A (cheap, do first):** enable all 20 mods (`mod_info.json`
  only, no CSV yet) simultaneously, launch Starsector once, and read
  `starsector.log`'s reported load order for each pair. This only proves
  what the loader *reports*, not what governs merge resolution — record it,
  then continue to Phase B regardless of what Phase A shows.
- [ ] **Phase B (ground truth, run regardless of Phase A's result):** add one
  `descriptions.csv` row per pair — a unique custom id per pair (e.g.
  `zprobe01row`) so pairs never cross-contaminate — with the source
  declaring one text value and the overlay declaring a distinguishably
  different value for the same id, exactly as `REAL_MOD_COMPATIBILITY.md`'s
  Azure Federation section already documents. Enable all 20 mods, start a
  campaign, and check each pair's row in the Codex: record which mod's text
  actually won for each pair.
- [ ] Conclusion: identify which single factor (id, name, folder,
  dependency presence), if any, consistently predicts the winner across all
  10 pairs. If no single factor predicts it, record that explicitly — this
  is itself a finding for `BUGS.md` BUG-009, not a failed test.

## Glossary, report, diagnostics, and image-layout checks

- [ ] Open a valid glossary from **Check Glossary** and confirm conflicts name
  the stable file/key and expected term without changing any translation.
- [ ] Try an oversized, malformed, duplicate-term, and blank-term glossary;
  confirm each fails clearly and the project/source remain unchanged.
- [ ] Export a translation report twice from an unchanged project and confirm
  byte-identical CSV with file/key, status, provenance, source, and translation.
- [ ] Trigger a background project failure and confirm the dialog states the
  failed operation, that source-mod files were unchanged, and a useful next
  action without exposing credentials.
- [ ] Trigger a handful of other GUI dialog failures across different tabs
  (e.g. an invalid font file on Check Font Coverage, a malformed schema entry,
  a bad Tesseract path on Auto-Detect Text) and confirm each now names the
  operation plus a next action instead of a bare exception message (BUG-010).
  Also run a CLI command against an invalid input (e.g.
  `ssmt-cli project build` against a broken project file) and confirm the
  printed failure text is similarly plain-language, not just the raw
  exception message.
- [ ] At the 900 px minimum window width, confirm Image Localization presents
  distinct manual-entry, OCR, text-render, and AI-render rows with every action
  keyboard reachable and no clipped controls.

## Installer, signing, and security boundary

- [ ] On Windows with WiX, build `:ssmt-gui:jpackageInstaller`.
- [ ] Install, launch, upgrade/reinstall, and uninstall; verify shortcuts and
  preservation of user-owned projects/catalogs.
- [ ] Sign the final installer and verify its signature/timestamp on a clean host.
- [ ] Do not activate genuinely untrusted Windows plugins: process isolation is
  not an OS sandbox.

## Release decision

- [ ] Classify every failure as release-blocking, non-blocking, deferred, or
  environment-blocked.
- [ ] Confirm the published checksum matches the tested artifact.
