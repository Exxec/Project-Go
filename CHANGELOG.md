# Changelog

`Last updated: 2026-08-02 by Claude (BUG-010 diagnostic-language remediation across GUI/CLI; AI fallback in ChainedProjectTranslationEngine when local providers are unavailable)`

## Unreleased

- Fixed BUG-012: translated-clone builds failed on source CSVs encoded as
  GB18030/legacy Chinese charsets instead of UTF-8 (`Input length = 1`), even
  though extraction already tolerated them. `StandardFileInjector` now
  shares `CsvExtractor`'s UTF-8-then-GB18030-fallback decoder.
- Added a read-only `CoverageGapAuditor` (`ssmt-extractor`), wired into
  `ssmt-cli extract`: scans every `.csv` file left unsupported by
  `StandardCsvSchemas` for likely non-English text and reports it as a
  reviewable finding (file path plus a short excerpt). It never extracts,
  translates, or approves anything — it only makes a missing schema entry
  visible instead of silently shipping untranslated, the same class of gap
  BUG-011 found by hand. Deliberately does not add a blanket "translate
  everything found" fallback, per the evidence-gated coverage policy
  (`DECISIONS.md` ADR-032): a human still decides whether a flagged file is
  a genuine standard-schema candidate or needs the existing opt-in CSV
  schema mechanism instead.
- Fixed BUG-011: the first real live Starsector smoke test against Azure
  Federation (`BUGS.md` BUG-005) found one ship system still rendering
  `????` in the loadout UI. `data/shipsystems/ship_systems.csv`'s `name`
  column — the ship system's display name — had no entry in
  `StandardCsvSchemas` at all, so it was never extracted or translated.
  A follow-up sweep (manual, then via the new `CoverageGapAuditor`) across
  every mod in `Test mods/` found the identical gap in eight more standard
  files, all now added to `StandardCsvSchemas` with evidence-confirmed real
  content: `data/hullmods/hull_mods.csv`, `data/characters/skills/skill_data.csv`,
  `data/hulls/wing_data.csv`, and `data/campaign/commodities.csv`/`industries.csv`/
  `market_conditions.csv`/`special_items.csv`/`submarkets.csv`.
- **Export for Online AI** now splits the request into numbered sibling files
  (`<Mod Name> words1.json`, `<Mod Name> words2.json`, ...) once the project
  has more entries than a configurable batch size (default 250, prompted at
  export time), instead of always writing one unbounded file. This was found
  after a large real mod (2087 entries) got a truncated AI response back —
  1845 of 2087 translations came back in an incorrect schema shaped like a
  project file rather than the requested exchange format, and 242 entries
  were silently dropped. A project with fewer entries than the batch size
  still gets the exact original single filename, unchanged.
- Added a headless `ssmt-cli project import-ai-response` command, wrapping the
  same reviewable-draft `AiTranslationExchangeService` validation the GUI's
  Import AI Response uses (unknown/duplicate ids, changed source text,
  invalid protected syntax) so an AI response can be applied to a project
  without launching the GUI. Results stay `AI_TRANSLATED` drafts unless
  `--approve` is passed.
- Extended BUG-010's plain-language diagnostic presentation (`UserDiagnostic`
  in the GUI, new `CliDiagnostics` helper in the CLI) to every remaining
  failure dialog and CLI error message that previously showed only a bare
  operation heading plus the raw exception text. Fixed two bugs found along
  the way: a missing `error.aiImport` resource key that silently threw
  instead of showing the intended dialog, and a file-chooser title
  incorrectly reused as an error heading in "Open Translation Memory."
- Added a fallback in the automated project-translation router: if both
  Argos Translate and TranslateLocally fail (e.g. neither is installed), a
  configured AI provider is now tried directly on the source instead of the
  whole translation failing outright, so AI translation remains usable
  without the local engines present.
- Added source-bound translation checkpoints and explicit compatible resume.
- Added read-only local-provider preflight and report-only routing evidence.
- Added provenance filters, explicit draft approval/rejection, and on-demand
  generation-lineage viewing.
- Added a bounded data-only glossary format with advisory conflict checks and
  deterministic CSV translation-report export.
- Improved generic background-failure guidance and separated Image
  Localization controls by workflow stage.

All notable changes use semantic versions and are recorded before a release.

## 0.6.0

- Replaced normal delta-overlay publication with ADR-041 transactional clone
  publication. Every build now creates a pristine `<output>-source-backup` and
  a complete translated `<output>` clone while leaving the source untouched.
- Translated clones preserve the source mod's original metadata, ID, scripts,
  JARs, assets, and untranslated files. Users enable the translated clone
  instead of the original, eliminating BUG-009's unsupported cross-mod
  duplicate-key merge from the normal workflow.
- Clone publication fingerprints all source and translated bytes, detects
  source changes during staging, rejects links/special files, restores prior
  clone outputs when replacement fails, and skips byte-identical rebuilds.
- Updated GUI, CLI, SSMT Auto, Project Info, safety acknowledgments, and manuals
  for the pristine/translated clone workflow. Full clones remain personal-use
  artifacts unless the source author permits redistribution.

## 0.5.3

- Removed recurring CLI version drift: packaged JARs now receive Gradle's
  `Implementation-Version`, and class-directory runs use a generated version
  resource instead of a hardcoded Java literal.
- Added a Project Info tab with live workflow guidance, active source/project/
  output/TM/schema/recovery locations, and Open Folder actions.
- Added a resettable synthetic sample-project workflow, clearer separate-patch
  first-run guidance, and a one-time explanation of AI export/import safety.

- Hardened the generated overlay's `mod_info.json` against BUG-009's
  unresolved Starsector cross-mod merge-order risk (ADR-040): now writes
  `gameVersion` when the source mod declares one; rejects a generated
  `patchId` equal to its source mod's id outright; and non-blockingly warns
  when a chosen `patchId`/`patchName` doesn't extend the source mod's own
  id/name (a defense-in-depth hedge against multiple plausible load-order
  mechanisms — not a resolution of which one is real, which still requires
  human verification in an actual Starsector install, see
  `MANUAL_ACCEPTANCE.md`'s new load-order test protocol).
- The GUI's suggested patch name now appends "Translation" after the source
  mod's name instead of prepending it, so the tool's own default suggestion
  passes its own new naming check.

## 0.5.2

- Refined the BUG-006 Translation Editor toolbar grouping: the refresh/
  refresh-with-TM pair now stay in the same row, "Mark Reviewed" moved next
  to the search/filter controls it acts on, and the three "Create…" buttons
  are now one merged dropdown control instead of three separate buttons.
- Documentation freshness pass across README.md, BEGINNERS_GUIDE.md,
  AUTO_GUIDE.md, and ~20 other root docs: fixed stale phase/version claims,
  a dangling ADR cross-reference in PLUGIN_API.md, an unimplemented "DeepL"
  mention in GLOSSARY.md, and a `.cursor/rules/AI_CONTRACT.md` that had
  drifted from the root contract without disclosing it was a reduced
  summary. Also fixed `ssmt-cli`'s `--version` output, which had silently
  drifted to "SSMT 0.2.0" (hardcoded, unrelated to `ssmtVersion`) across
  five version bumps before this pass caught it — see `FEATURE_BACKLOG.md`
  F10 for the recurring-drift risk this exposed.
- Fixed BUG-008: three GUI button-naming consistency issues. "Render PNG"
  is now "Render Localized Image (Text)" (parallels "Render Localized
  Image (AI)"); "Export for Image AI" is now "Export Image Regions for AI"
  (no longer a near-duplicate of "Export for Online AI"); "Refresh with TM"
  is now "Refresh with Translation Memory" (matches "Open Translation
  Memory" in the same toolbar). Display strings only, no behavior change.

## 0.5.1

- Fixed the Translation Editor toolbar overflowing the documented minimum
  window width: 13 buttons plus two labels in one flat, non-wrapping row are
  now four grouped rows (BUG-006).
- Fixed the Image Localization tab's "Add Region" and "Auto-Detect Text"
  actions silently discarding already-typed translations and already-imported
  AI-regenerated art for existing regions; they now preserve that work for
  any region whose geometry survives (BUG-007).

## 0.5.0

- Added AI-assisted image region regeneration: export a padded crop and
  plain-text instructions per region for an external AI image tool, then
  validate (exact pixel dimensions enforced) and composite the regenerated
  art back into a copy of the source image. Complements the existing
  Java2D text-draw render path.
- Wired the existing (previously unused) Tesseract OCR engine into the GUI
  as an "Auto-Detect Text" button, so regions no longer require hand-typed
  pixel coordinates.
- The Image Localization tab now shows a live table of all current regions
  with inline-editable translated text, instead of adding regions blindly
  with no visible list.

## 0.4.0

- Added a font glyph coverage checker: parses a Starsector BMFont `.fnt` file
  and warns when translated text contains characters the font cannot render
  (verified against the real Starsector default font). Available via
  `ssmt project check-fonts` and a new GUI "Font Coverage" tab.
- Added a translation coverage dashboard: overall and per-file
  translated/total counts via `ssmt project coverage` and a live summary in
  the GUI Translation Editor toolbar.

## 0.3.0

- Fixed CSV reinjection corrupting Starsector-significant blank sentinel and
  `#`-comment rows (BUG-001); unchanged structural rows now preserve their
  original raw representation instead of being generically re-quoted.
- Extended standard localization coverage for `ship_data.csv`/`weapon_data.csv`
  manufacturer/role/tooltip text and faction rank/post/fleet-type display
  names, confirmed by real-mod evidence (BUG-002).
- Fixed a first-launch bug where the GUI could never create a default SQLite
  translation-memory catalog, and hardened SQLite catalog restart/resume with
  new regression coverage (BUG-003).
- Fixed AI draft translation import rejecting responses with "invalid
  protected syntax" even when the translation was identical to its source
  text (BUG-004).
- Added an opt-in CSV extraction schema catalog (`OptInCsvSchemaCatalog`),
  mirroring the existing JSON one, so a mod's extra/unrecognized CSV columns
  can be handled without an SSMT release. Available via the CLI
  `--csv-schema` flag and a new GUI "Create with CSV Schema" button.

## 0.2.0

- Added real-mod compatibility boundaries and fixture-backed regressions.
- Added deterministic project refresh and non-applying translation suggestions.
- Added source-safe autosave, recovery, diagnostics, and update reports.
- Added release SBOM, archive scanning, and SHA-256 checksum evidence.

## 0.1.0

- Established the modular Java 25 extraction, validation, project, and patching
  foundation.
