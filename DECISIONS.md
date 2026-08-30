# Architecture Decision Log (ADR)

`Last updated: 2026-08-02 by Codex (added ADR-043 confidence-gated offline-to-AI escalation)`

## Overview

This document records significant architectural decisions for SSMT.

Use the following format:

- **Date**
- **Status:** Proposed | Accepted | Rejected | Deprecated | Superseded
- **Context**
- **Decision**
- **Consequences**

Existing ADRs 001–020 remain accepted unless explicitly superseded below.

---

## ADR-021: Fixture-Driven Real-World Compatibility

- **Date:** 2026-07-26
- **Status:** Accepted
- **Context:** Community Starsector mods use valid but inconsistent metadata, CSV, encoding, and JSON-like conventions. Broad leniency would hide errors and risk incorrect patches.
- **Decision:** Every new compatibility exception must originate from an observed real-world pattern and be backed by an anonymized fixture and automated regression test. Compatibility behavior must be scoped to the narrowest applicable parser or boundary.
- **Consequences:**
  - Real mods can drive robust compatibility without turning parsers permissive.
  - Corpus failures require analysis rather than blanket relaxation.
  - Maintaining fixture coverage becomes part of compatibility maintenance.

---

## ADR-022: Unicode Path Correctness Belongs to the Process Boundary

- **Date:** 2026-07-26
- **Status:** Accepted
- **Context:** Some Windows PowerShell/native launcher combinations can replace non-ASCII command-line path characters with `?` before Java receives them.
- **Decision:** SSMT will not reconstruct or “repair” corrupted paths inside extractors. Launchers and process invocation must preserve Unicode arguments. JavaFX and direct in-JVM path selection are preferred where they preserve the original path.
- **Consequences:**
  - Path handling remains deterministic.
  - Windows launchers become part of compatibility testing.
  - A corrupted argument fails visibly instead of risking access to the wrong path.

---

## ADR-023: Ordered Composite CSV Identities

- **Date:** 2026-07-26
- **Status:** Accepted
- **Context:** Phase 7 real-mod testing found CSVs where a single `id` value is not unique, but the tuple `(id, type)` is unique. Weakening duplicate protection would merge unrelated translation rows.
- **Decision:** CSV schemas may define one or more ordered identity columns. Stable keys encode every identity component and the localizable column. Duplicate complete identity tuples remain errors.
- **Consequences:**
  - Real-world composite-key CSVs are supported safely.
  - Existing single-column identities remain a one-element special case.
  - Stable keys survive row reordering.
  - Reinjection must resolve the same composite identity tuple used during extraction.

---

## ADR-024: Legacy Source Encodings Are an Input Compatibility Concern

- **Date:** 2026-07-26
- **Status:** Accepted
- **Context:** Phase 7 encountered legacy non-UTF-8 CSV content used by real mods. SSMT's internal/project formats must remain consistently UTF-8, but rejecting all legacy input would unnecessarily exclude valid mods.
- **Decision:** SSMT remains Unicode/UTF-8 internally and for project/output/interchange files. Source readers attempt strict UTF-8 first. Explicit, deterministic legacy fallback may be enabled for observed formats. Current compatibility includes GB18030 fallback for legacy CSV input.
- **Consequences:**
  - Legacy mods can be read without changing internal encoding policy.
  - Source bytes remain untouched.
  - Unlimited charset guessing is prohibited.
  - Encoding fallback use should be diagnosable.

---

## ADR-025: Do Not Auto-Correct Probable Source Typos

- **Date:** 2026-07-26
- **Status:** Accepted
- **Context:** A real corpus sample contains `"autofire": Ture`, which is likely intended to be `true`. Automatically accepting typo-like tokens would make SSMT silently reinterpret author data.
- **Decision:** Unknown or probable typo literals remain errors. SSMT may report a likely correction, but must not silently normalize or modify the source.
- **Consequences:**
  - SSMT distinguishes compatibility from repair.
  - Malformed source remains visible to the user.
  - A future opt-in repair assistant may suggest or generate a repaired copy outside the source mod.

---

## ADR-026: Corpus Success Does Not Imply Phase Completion

- **Date:** 2026-07-26
- **Status:** Accepted
- **Context:** Ten of eleven supplied mods now extract successfully, but Phase 7 also requires deterministic rebuilds, update reconciliation, GUI/CLI workflow validation, and generated-overlay smoke testing.
- **Decision:** Real-mod extraction compatibility is tracked as a distinct Phase 7 milestone. Phase 7 is not complete until all documented exit criteria pass.
- **Consequences:**
  - Progress remains measurable without prematurely declaring completion.
  - Corpus extraction results can improve independently of update-reconciliation work.

---

## ADR-027: Project Refresh Is Preview-First and Non-Applying

- **Date:** 2026-07-26
- **Status:** Accepted
- **Context:** Updated mods can change source text and stable identities. Carrying a
  fuzzy or moved-key translation directly into the refreshed project could silently
  mistranslate unrelated content.
- **Decision:** Refresh produces an in-memory candidate plus a deterministic report.
  Only unchanged exact stable-key/source-text matches preserve translations.
  Changed or moved candidates are suggestions only. Ambiguous candidates are
  conflicts. CLI and GUI require an explicit apply action after preview.
- **Consequences:**
  - Dry runs do not alter project or source files.
  - Fuzzy and moved-key suggestions cannot become accepted translations implicitly.
  - Project replacement is staged and transactional at the document boundary.

---

## ADR-028: Typed Translation Provenance and Conservative Author Localization

- **Date:** 2026-07-26
- **Status:** Accepted
- **Context:** The FSF Military Corporation localization test exposed parallel
  `aEP/...` and `aEP_En/...` content with author-provided English translations.
  Author translations are more reliable than AI drafts, but broad `_En`
  assumptions could pair unrelated data.
- **Decision:** Provenance is the shared typed enum `HUMAN_EDITED`,
  `AUTHOR_LOCALIZATION`, `MANUAL_IMPORT`, `AI_TRANSLATED`, and `FUZZY_MATCH`.
  Automatic preference is human, author, manual import, AI, then fuzzy. The
  first detector recognizes only the exact `aEP` / `aEP_En` namespace pair
  and requires identical relative suffix and stable extraction key. Project
  schema v1 gains an optional provenance field; missing provenance becomes
  `MANUAL_IMPORT`. Translation-memory schema v1 migrates in place to v2 by
  adding a non-null provenance column with the same legacy default.
- **Consequences:**
  - Existing projects and SQLite catalogs open without manual conversion.
  - Lower-confidence automatic imports cannot replace higher-confidence data.
  - Equal-confidence upserts retain existing catalog refresh behavior.
  - Explicit editing creates `HUMAN_EDITED` values.
  - Unmatched and ambiguous author candidates are reported, never guessed.
  - No extraction key or source mod file is changed.

---

## ADR-029: Separate Automated, Manual, and Host-Tool Release Evidence

- **Date:** 2026-07-26
- **Status:** Accepted
- **Context:** Command and controller acceptance can prove deterministic,
  source-safe workflows, but cannot prove JavaFX screen behavior, installed-game
  loading, installer behavior, signing, or a Windows OS sandbox.
- **Decision:** Release status names automated evidence, manual checks, and
  environment-blocked checks separately. The unsigned self-contained Windows
  testing ZIP may proceed after interactive GUI and game smoke acceptance. A
  signed installed release additionally requires WiX installer and signature
  evidence. macOS is deferred and Linux packaging is optional.
- **Consequences:**
  - Roadmap completion cannot be inferred from implementation alone.
  - `MANUAL_ACCEPTANCE.md` records human-only checks.
  - Process isolation is never described as an OS sandbox.

---

## ADR-030: Shared Persistent Catalog and Conservative Catalog Merge

- **Date:** 2026-07-26
- **Status:** Accepted
- **Context:** GUI and headless workflows must grow one reusable translation
  catalog, while users may also have older databases that need consolidation.
  Blind database replacement or equal-confidence overwrite could discard good
  translations.
- **Decision:** GUI and SSMT Auto default to the same per-user SQLite catalog.
  The GUI remembers an explicitly opened catalog. Catalog comparison inspects
  a temporary copy of the selected source database. Merge adds missing
  identities and accepts only strictly higher-preference provenance; all other
  differing translations remain conflicts.
- **Consequences:**
  - Restarting either program retains accumulated translations.
  - Compare never migrates or writes the selected source catalog.
  - Merge requires a preview and explicit approval.
  - Conflicting equal/lower-confidence values require later human resolution.
---

## ADR-031: Preserve Starsector-Significant CSV Structural Rows at Reinjection

- **Date:** 2026-07-26
- **Status:** Accepted; implemented 2026-08-01
- **Context:** A live Azure Federation overlay smoke test showed that generic CSV
  serialization changed blank sentinel rows from `,,,,,,` to `"",,,,,,` and a
  `#ships` structural/comment row to a quoted ordinary field. Starsector treated
  repeated quoted-empty rows as duplicate composite identities and aborted
  loading.
- **Decision:** CSV reinjection must preserve fixture-backed
  Starsector-significant structural/sentinel/comment row semantics at the
  narrowest output boundary. Unchanged structural rows should retain their raw
  representation where feasible. Blank sentinel rows and observed `#`
  structural/comment rows must not acquire synthetic identities. Composite
  identity validation remains strict for real data rows.
- **Consequences:**
  - Generic CSV normalization is not assumed semantically neutral.
  - Reinjection tests must cover output representation, not extraction alone.
  - Duplicate complete composite tuples remain errors.
  - Source files remain immutable.
- **Implementation note (2026-08-01):** `StandardFileInjector.injectCsv`
  (`ssmt-patcher`) now slices each `CSVRecord`'s original raw text via
  `CSVRecord.getCharacterPosition()` and re-emits unchanged blank/`#`-prefixed
  rows verbatim instead of through `CSVFormat.DEFAULT.format(...)`. Rows that
  are modified (a real translation match) still go through the normal
  formatted-write path unchanged. Regression coverage:
  `StandardFileInjectorTest.preservesBlankSentinelRowRawRepresentationDuringCsvInjection`,
  `...preservesHashPrefixedStructuralRowRawRepresentationDuringCsvInjection`, and
  the `csv-structural-rows` fixture in
  `LocalizationProjectServiceTest.structuralRowFixturePreservesSentinelAndCommentRowsThroughDeterministicWorkflow`.

---

## ADR-032: Localization Coverage Expands Only Through Confirmed Player-Visible Fields

- **Date:** 2026-07-26
- **Status:** Accepted; implemented 2026-08-01 (CSV columns partially — see note)
- **Context:** After repairing Azure Federation's structural CSV output, the
  overlay loaded and a campaign started, but untranslated Chinese remained in
  player-visible manufacturer/tech, weapon metadata/tooltip, and faction
  rank/role fields. Supported ship names and description prose were already
  English, demonstrating a coverage gap rather than global encoding failure.
- **Decision:** Expand standard extraction schemas only for fields confirmed
  player-visible by real-game evidence and backed by anonymized fixtures.
  SSMT will not translate arbitrary textual CSV/JSON fields merely because they
  contain strings.
- **Consequences:**
  - Coverage can grow without corrupting mechanics/identity fields.
  - Real-game smoke tests become evidence for schema additions.
  - Unsupported custom visible fields continue to use schema/plugin mechanisms
    or explicit future support.
- **Implementation note (2026-08-01):** `StandardCsvSchemas` (`ssmt-extractor`)
  now declares `tech/manufacturer` as an optional column for both
  `ship_data.csv` and `weapon_data.csv`, plus `primaryRoleStr` and
  `customAncillaryHL` for `weapon_data.csv` — the three columns directly
  confirmed non-English in the Azure Federation fixture. The remaining vanilla
  weapon tooltip-override columns (`speedStr`, `trackingStr`, `turnRateStr`,
  `accuracyStr`, `customPrimary`, `customPrimaryHL`, `customAncillary`) are
  intentionally deferred until directly observed, per this ADR's evidence bar.
  Separately, `JsonExtractionSpec` (`ssmt-extractor`) gained a bounded
  object-key wildcard `patterns` component (e.g. `/ranks/ranks/*/name`,
  `/ranks/posts/*/name`, `/fleetTypeNames/*`), used by
  `StandardJsonFileExtractor.FACTION_SPEC` to cover mod-defined rank/post/
  fleet-type display names without becoming a generic recursive text-leaf
  walker. Regression coverage: `StandardCsvSchemasTest`,
  `CsvExtractorTest.extractsWeaponAndShipOptionalColumnsWhenPresentButToleratesTheirAbsence`,
  `JsonExtractionSpecTest`, and
  `StandardJsonFileExtractorTest.factionExtractsRankPostAndFleetTypeDisplayNamesWhenPresent`.

---

## ADR-033: Existing SQLite Catalog Opening Is Explicit and Non-Destructive

- **Date:** 2026-07-26
- **Status:** Accepted; implemented 2026-08-01
- **Context:** Manual multi-mod use exposed ambiguity around resuming an existing
  SQLite catalog after restart. An explicit **Open Existing Database** action was
  added so the user can deliberately resume a catalog instead of selecting a
  location through a creation-oriented flow.
- **Decision:** Preserve distinct create-new and open-existing database
  semantics. Opening a valid existing catalog must never destructively
  reinitialize it. Invalid/incompatible catalogs fail visibly. The explicit
  **Open Existing Database** workflow remains part of the intended UX.
- **Consequences:**
  - Catalog data is expected to survive application restart.
  - Multi-mod project/TM/catalog data requires restart regression coverage.
  - ADR-030 shared-catalog and conservative-merge behavior remains in force.
  - Implementations must not remove the explicit open-existing action while
    repairing persistence lifecycle behavior.
- **Implementation note (2026-08-01):** Direct code reading found that this
  ADR's own guarantee was broken on a fresh install:
  `SsmtApplication.initializeTranslationMemory()`'s only helper called
  `ProjectWorkspaceController.verifyTranslationMemory`, which requires the
  catalog file to already exist — so on first launch, neither the preferred
  nor fallback default path exists yet, both branches threw, and the app ended
  up with no catalog at all (contradicting ADR-030's "GUI and SSMT Auto
  default to the same per-user SQLite catalog"). Fixed by adding
  `ProjectWorkspaceController.openOrCreateTranslationMemory(Path)` (create-if-
  missing) for the default-catalog startup path, while the explicit **Open
  Existing Database** button keeps calling `verifyTranslationMemory` unchanged
  — the two flows stay behaviorally distinct as this ADR requires. Separately,
  `TranslationMemoryMergeService.plan(...)` was tightened to require the
  destination catalog to already exist, closing a gap where `compare()`
  (meant to be read-only per ADR-030) could silently create an empty
  destination file. Regression coverage:
  `SqliteTranslationMemoryTest.restartingApplicationPreservesMultipleIndependentCatalogsAcrossReopen`,
  `...openingHealthyCatalogNeverReinitializesSchemaOrData`,
  `TranslationMemoryMergeServiceTest.refusesToCompareOrMergeWhenDestinationCatalogDoesNotExist`,
  `ProjectWorkspaceControllerTest.openOrCreateTranslationMemoryCreatesMissingDefaultCatalogThenPreservesDataAcrossReopen`,
  and the new `CatalogRestartResumeRegressionTest` (`ssmt-gui`).

---

## ADR-034: Protected-Syntax Validation Must Diff Against the Source, Not Scan the Translation Alone

- **Date:** 2026-08-01
- **Status:** Accepted; implemented 2026-08-01
- **Context:** AI draft translation import could reject a response with "AI
  response has invalid protected syntax" even when a translated entry was
  byte-for-byte identical to its own source text. Root cause:
  `TranslationValidator.hasMalformedMessageArgument`/`hasMalformedPrintf`
  scanned only the translated text for leftover `{`/`}`/`%` characters after
  stripping recognized placeholders, with no comparison against whether that
  same stray character was already present, unchanged, in the source. A
  source string containing a non-numeric brace (e.g. a keybind hint like
  `"{LMB}"`) or a `%` not adjacent to a digit could trip these checks even
  when nothing was translated.
- **Decision:** Malformed-syntax heuristics for protected tokens must be
  diff-based against the source: a translation is flagged only when it
  introduces *more* stray placeholder-like characters than the source itself
  already contained, never merely for containing a pattern that already
  existed unchanged in the source.
- **Consequences:**
  - `translatedText.equals(sourceText)` can never fail protected-syntax
    validation, by construction (both sides compute identical leftover
    counts).
  - Genuinely introduced malformed syntax (e.g. a translation that turns
    `%s` into a bare `%`) is still rejected exactly as before.
  - The existing symmetric token-count diff (`compare()`) was already correct
    and required no change.
- **Implementation note:** `ssmt-validation/.../TranslationValidator.java`:
  `hasMalformedPrintf`/`hasMalformedMessageArgument` now take both source and
  translated text and compare leftover stray-character counts
  (`strayPercentCount`/`strayBraceCount`) rather than scanning the translation
  alone. Regression coverage:
  `TranslationValidatorTest.identicalTranslationNeverFailsValidationEvenWithStrayBraceOrPercent`
  and
  `AiTranslationExchangeServiceTest.importsResponseWhenTranslationIdenticalToSourceContainingStraySyntax`.

---

## ADR-035: Opt-In CSV Extraction Schema Catalog, Mirroring JSON's

- **Date:** 2026-08-01
- **Status:** Accepted; implemented 2026-08-01
- **Context:** ADR-032 closed the three CSV/JSON fields directly confirmed
  non-English in the Azure Federation fixture, but CSV extraction remained a
  closed, hardcoded map (`StandardCsvSchemas`) with no per-mod extensibility.
  An unrecognized CSV file or column is silently invisible to extraction —
  no error, no diagnostic — exactly how BUG-002 went unnoticed until manual
  playtesting. JSON already has an opt-in mechanism
  (`OptInJsonSchemaCatalog`/`ConfiguredJsonFileExtractor`) for exactly this
  gap; CSV had no equivalent.
- **Decision:** Add `OptInCsvSchemaCatalog`/`OptInCsvFileSchema`/
  `ConfiguredCsvFileExtractor` (`ssmt-extractor`), mirroring the JSON opt-in
  mechanism's shape and bounds (versioned catalog, exact relative paths, no
  globs, no overlap with standard-handler paths, 256-file/256-column/1 MiB
  caps). Declared `textColumns` are used-when-present (missing → skipped, not
  an error), matching how opt-in JSON pointers already behave when absent —
  intentionally more lenient than a standard schema's required `textColumns`,
  since a user-authored one-off schema shouldn't hard-fail extraction over an
  unconfirmed column. This required relaxing `CsvExtractionSpec`'s
  constructor to allow an empty required `textColumns` when
  `optionalTextColumns` is non-empty (previously `textColumns` alone had to
  be non-empty); existing standard schemas are unaffected since they always
  declare a non-empty `textColumns`.
- **Consequences:**
  - A mod's extra CSV columns can be handled by the user authoring a small
    JSON catalog (see `CSV_SCHEMAS.md`), without waiting for an SSMT release
    or evidence-gated `StandardCsvSchemas` addition.
  - Opt-in schemas cannot override or weaken standard schemas — any path
    overlap is a construction-time error.
  - `LocalizationProjectService` gained `createWithCsvSchema` and a combined
    `createWithSchemas(jsonSchemaCatalog, csvSchemaCatalog)` that accepts
    either or both simultaneously; `createWithJsonSchema` now delegates to
    the same shared implementation without any change to its own signature
    or behavior.
  - CLI gained `--csv-schema` (usable alongside `--json-schema`); GUI gained
    a "Create with CSV Schema" button alongside the existing JSON one.
- **Implementation note:** new classes in
  `ssmt-extractor/.../csv/{OptInCsvFileSchema,OptInCsvSchema,OptInCsvSchemaCatalog,ConfiguredCsvFileExtractor}.java`;
  wiring in `LocalizationProjectService.java` (`ssmt-project`),
  `ProjectCommand.java` (`ssmt-cli`), and `ProjectWorkspaceController.java`/
  `SsmtApplication.java` (`ssmt-gui`). Regression coverage:
  `CsvExtractionSpecTest`, `OptInCsvFileSchemaTest`,
  `OptInCsvSchemaCatalogTest`,
  `LocalizationProjectServiceTest.createsProjectWithExplicitCustomCsvSchema`/
  `...createsProjectWithBothCustomJsonAndCsvSchemasTogether`,
  `ProjectWorkspaceControllerTest.realControllerCreatesProjectWithExplicitCustomCsvSchema`.

---

## ADR-036: Font Glyph Coverage Check and Translation Coverage Dashboard

- **Date:** 2026-08-01
- **Status:** Accepted; implemented 2026-08-01
- **Context:** BUG-002's `???` rendering happened because untranslated/missed
  text silently reached the in-game font with no glyph for it, discoverable
  only by manual playtesting. `FEATURE_BACKLOG.md` F1/F2 proposed two
  complementary, narrowly-scoped diagnostics: check whether translated text
  will actually render in a given Starsector font, and show translation
  completion percentage without cross-referencing files by hand. Both are
  warnings/reports, never build gates — consistent with "diagnose, never
  silently repair."
- **Decision:**
  - **F1 (glyph coverage):** Starsector's UI fonts are AngelCode BMFont
    text-format files (`.fnt` + PNG atlas), with the active font path named
    by `data/config/settings.json`'s `defaultFont` key in a real Starsector
    install. Add `BmFontGlyphSet` (`ssmt-validation`) to parse a `.fnt` file's
    declared codepoints and `FontCoverageAuditor`/`FontCoverageFinding`
    (`ssmt-project`) to check every translated entry's text against it,
    reporting entries containing codepoints the font can't render. This is
    necessarily a **partial** safety net: it only sees entries SSMT already
    extracted, so it cannot catch the ADR-032-class problem of fields never
    extracted at all (those are copied through unchanged and never become
    `ProjectEntry` instances). It does catch a translation that's incomplete,
    wrong-script, or introduces an exotic character (curly quotes, em-dash,
    emoji) the target font lacks.
  - **F2 (coverage dashboard):** Add `TranslationCoverageAuditor`/
    `TranslationCoverageReport`/`FileTranslationCoverage` (`ssmt-project`) —
    a stateless, read-only overall + per-file translated/total count,
    mirroring `TerminologyConsistencyAuditor`'s existing shape.
  - Both are exposed via new `ssmt project check-fonts`/`ssmt project
    coverage` CLI subcommands and a new GUI "Font Coverage" tab plus a
    coverage summary label in the Translation Editor tab (first structured-
    findings `TableView` in the GUI; reuses the existing `pluginTab`/`logTab`
    `column(label, extractor)` pattern).
- **Consequences:**
  - Neither check can fail a build or block `LocalizationProjectService.build()`
    — both are separate, opt-in, read-only operations.
  - F1's real-font parsing was verified against the actual Starsector
    installation's default font (`insignia15LTaa.fnt`, 233 glyphs): it
    correctly flags Chinese text as unrenderable while passing English and
    diacritic-free Polish text. No Starsector font asset is committed to the
    repository — only synthetic fixtures matching the BMFont text format.
  - F1's documented partial-coverage caveat must be preserved in any future
    UI copy — it is not a substitute for extraction-schema completeness
    (ADR-032).
- **Implementation note:** new classes in
  `ssmt-validation/.../font/BmFontGlyphSet.java`; `ssmt-project/.../{FontCoverageAuditor,FontCoverageFinding,TranslationCoverageAuditor,TranslationCoverageReport,FileTranslationCoverage}.java`;
  new `ProjectCommand.CheckFonts`/`ProjectCommand.Coverage` (`ssmt-cli`);
  `ProjectWorkspaceController.translationCoverage`/`checkFontCoverage` and a
  new font-coverage tab in `SsmtApplication.java` (`ssmt-gui`). Regression
  coverage: `BmFontGlyphSetTest`, `FontCoverageAuditorTest`,
  `TranslationCoverageAuditorTest`.

---

## ADR-037: AI-Assisted Image Region Regeneration and Tesseract Auto-Detection

- **Date:** 2026-08-01
- **Status:** Accepted; implemented 2026-08-01
- **Context:** The existing image-localization workflow (`ImageLocalizer`)
  only draws a solid panel plus retyped text over a region — adequate for
  simple UI labels, but not for text baked into shaded/textured artwork
  (e.g. a name painted on a curved 3D-rendered object), where a flat
  rectangle overlay looks wrong. The user asked for a workflow where an
  external AI image tool regenerates the artwork itself with translated
  text, mirroring how `AiTranslationExchangeService` already handles text:
  SSMT prepares an export, a human runs it through whatever AI they choose,
  and the result is imported back with validation — SSMT never calls an
  image-generation API itself. Separately, `TesseractOcrEngine` already
  existed in `ssmt-ocr` but was never wired into the GUI; every region
  required hand-typed pixel coordinates.
- **Decision:**
  - Export a **padded crop** per region, not the full image (user choice) —
    padding scales with the region's own size (50% of width/height, 16px
    floor) rather than a fixed margin, so small icons and large textures
    both get proportionate surrounding context for style-matching.
  - Import **enforces exact pixel dimensions** matching the exported crop
    (user choice) — a resized asset could break in-game texture/UI
    placement, so a mismatched regenerated image is rejected rather than
    silently accepted.
  - New `ImageRegionAiExchange` (`ssmt-ocr`) provides `exportRegions`
    (crop + padded bounds + plain-text instructions per region — plain text,
    not JSON, since the human pastes it directly into a chat/image tool),
    `validateRegeneratedRegionDimensions` (immediate per-region feedback),
    and `compositeRegeneratedRegions` (pastes validated crops into a copy of
    the source image at their recorded position; never modifies the
    source). Composite-side overlap/bounds validation mirrors
    `ImageLocalizer.validateRegions`.
  - `TesseractOcrEngine` is now wired into the GUI's Image Localization tab
    via a new "Auto-Detect Text" button (Tesseract executable path
    remembered via `Preferences`, same pattern as the remembered
    translation-memory path). Manual region entry remains available, since
    OCR is optional and user-supplied.
  - The existing Java2D "Render PNG" text-draw path is unchanged and
    remains available as the simpler alternative; the AI-regeneration path
    is additive, not a replacement.
- **Consequences:**
  - A visible seam at the crop boundary is possible if the AI's regenerated
    art doesn't perfectly match the surrounding padding context — documented
    as a known limitation, not solved by pixel-blending in this pass.
  - This workflow cannot verify the regenerated art is *correct*, only that
    it is readable and the right size — a human must still visually approve
    the result before it goes into a patch, consistent with "AI-assisted,
    not AI-dependent."
  - The Image Localization tab gained its first `TableView` of regions
    (previously regions were added blindly with no visible list), reusing
    the same `column(label, extractor)` pattern as `pluginTab`/`logTab`.
- **Implementation note:** new classes in
  `ssmt-ocr/.../{RegionCropExport,RegeneratedRegion,ImageRegionAiExchange}.java`;
  `ImageLocalizationEditorViewModel` (`ssmt-gui`) gained
  `exportForImageAi`/`importRegeneratedRegion`/`renderWithRegeneratedRegions`;
  `SsmtApplication.imageEditorTab` rewritten with a region `TableView`,
  "Auto-Detect Text", "Export for Image AI", "Import Regenerated Region",
  and "Render Localized Image (AI)" actions. Regression coverage:
  `ImageRegionAiExchangeTest`,
  `ImageLocalizationEditorViewModelTest.exportsImportsAndCompositesRegeneratedRegionsWithoutChangingSource`/
  `...rejectsRegeneratedImportWithWrongDimensions`/
  `...editingARegionAfterExportInvalidatesItsPriorExport`.

## ADR-038: GUI Usability Fixes — Toolbar Overflow and Image-Region Reload Data Loss

- **Date:** 2026-08-01
- **Status:** Accepted; implemented 2026-08-01
- **Context:** A user-requested usability review of this session's GUI
  additions (see `BUGS.md` BUG-006/BUG-007) found two real defects, both
  confirmed by direct source read rather than interactive click-through
  (no screenshot/UI-automation tool is available in this environment for a
  native JavaFX window):
  1. The Translation Editor toolbar had grown to 13 buttons plus a status
     label and the new coverage label (ADR-036) in one non-wrapping `HBox`
     with no `ScrollPane` anywhere in the file — comfortably exceeding the
     documented 900px minimum window width by a wide margin, with the
     newest controls (Create with CSV Schema from ADR-035, and the
     coverage label) at the end of the row and therefore most likely to be
     clipped off-screen.
  2. `ImageLocalizationEditorViewModel.load()` (added under ADR-037) always
     wiped `translations`, `lastExports`, and `regeneratedByIndex`, and was
     called not only by "Open Image" but also by "Add Region" and
     "Auto-Detect Text" — so adding one missed region, or re-running
     detection, after already hand-translating and AI-regenerating other
     regions silently discarded that work with no confirmation.
- **Decision:**
  - Split the toolbar into four stacked rows grouped by purpose (project
    lifecycle; translation-memory actions; AI/build/review actions; status
    labels) instead of one flat row, keeping every existing button
    unchanged (label, accessible text, and action) — just regrouped. No
    menu-bar consolidation and no `ScrollPane` were used, since the
    four-row split keeps every row comfortably under the 900px minimum
    width without hiding any control behind a click-to-reveal affordance.
  - Added `ImageLocalizationEditorViewModel.reload(List<OcrTextRegion>)`,
    a new method distinct from `load(Path, List)`, for the
    same-image "region set changed" case. It identifies a region by its
    exact pixel geometry (left/top/width/height) rather than list
    position — since detection can reorder or renumber regions — and
    carries forward the translated text and any imported
    `RegeneratedRegion` for every region whose geometry survives into the
    new set; a region whose geometry does not appear in the new set loses
    its carried-forward data along with it, since there is no longer an
    image region for that data to apply to. `load(Path, List)` itself is
    unchanged and still fully resets state, since opening a genuinely new
    image should not carry anything forward. "Add Region" and "Auto-Detect
    Text" now call `reload`; "Open Image" still calls `load`.
- **Consequences:**
  - Geometry-based identity means a region whose bounds shift even
    slightly between detection runs (e.g. Tesseract returning marginally
    different coordinates) is treated as a new region and loses its prior
    translation/regenerated art — an accepted limit of not tracking a
    separate persistent region ID, judged proportionate since exact
    hand-added regions and stable-geometry re-detection (the common case)
    are both handled correctly.
  - `lastExports` is still cleared on `reload` (as it already was on
    `edit`), so a region that already has an imported `RegeneratedRegion`
    keeps it, but any pending not-yet-imported export must be redone after
    a reload — judged acceptable since re-exporting a crop is cheap and the
    valuable, hard-to-reproduce artifact (the actual AI-regenerated pixels)
    is what `regeneratedByIndex` preserves.
  - No automated layout test exists for the toolbar split (this project has
    no TestFX/scene-graph-inspection dependency); verification relies on
    the label-width arithmetic in `BUGS.md` BUG-006 plus a manual build/launch
    check, consistent with this project's existing "manual GUI acceptance"
    boundary for things automated JUnit tests cannot observe.
- **Implementation note:** `SsmtApplication.editorTab` toolbar `HBox`
  replaced with four grouped `HBox` rows inside one `VBox`
  (`ssmt-gui`). `ImageLocalizationEditorViewModel.reload` and its private
  `RegionKey` geometry-identity record (`ssmt-gui`); `SsmtApplication`'s
  "Add Region"/"Auto-Detect Text" handlers now call `model.reload(...)`
  instead of `model.load(source[0].toPath(), ...)`. Regression coverage:
  `ImageLocalizationEditorViewModelTest.reloadPreservesTranslationWhenAddingANewRegion`/
  `...reloadPreservesImportedRegeneratedArtWhenAddingANewRegion`/
  `...reloadDropsPreservedDataForRegionsNoLongerPresent`/
  `...reloadRequiresSourceImageAlreadyLoaded`.
- **Update (2026-08-02):** the toolbar grouping above was refined after the
  user reviewed it against `UX_REVIEW.md`'s "group by workflow stage, not
  by feature area added-when" rule and pushed back on two specifics: the
  refresh/refresh-with-TM pair had been split across two different rows,
  and "Mark Reviewed" (which acts on the table's current search/filter
  selection, not on the project as a whole) had been placed in an
  AI/build row instead of next to the filter controls it actually applies
  to. Revised grouping, implemented 2026-08-02:
  - **Row 1 (project lifecycle):** a merged "Create Project" control,
    Open Project, Save, Refresh Project, Refresh with TM (now adjacent to
    its pair), Build Patch.
  - **Row 2 (translation-memory catalog):** Open Translation Memory,
    Compare / Merge Catalog.
  - **Row 3 (AI exchange):** Export for Online AI, Import AI Response.
  - **Row 4 (status):** unchanged — status label, coverage label.
  - **Filters row (existing, augmented):** Mark Reviewed moved here,
    next to the search field and review-status filter.
  - The three "Create…" buttons (Create Project / Create with JSON
    Schema / Create with CSV Schema) were merged into one
    `SplitMenuButton` — default click behaves like plain "Create
    Project," with the two schema variants as dropdown items — since
    they are mutually exclusive alternates of a single one-time-per-
    project action, not three things a user picks between repeatedly.
  - **Accessibility tradeoff, workaround attempted:** `MenuItem` has no
    `setAccessibleText` API (it is not a `Node`), which would otherwise
    have dropped the two schema variants' more-descriptive accessible
    strings (`accessible.createSchema`/`accessible.createCsvSchema`) down
    to whatever a screen reader infers from bare menu text alone. Rather
    than accept that unconditionally, the two dropdown entries are built
    as `CustomMenuItem`s wrapping a `Label` node, and `setAccessibleText`
    is set on that `Label` instead — JavaFX's accessibility peer for a
    `CustomMenuItem` is anchored to its content node, so the original,
    more-descriptive accessible strings are preserved rather than
    dropped. This was not independently verified with actual assistive
    technology in this environment (no such tool is available here); it
    is the best available workaround given the environment's
    constraints, accepted by the user as sufficient without that manual
    verification.
- **Implementation note (2026-08-02 update):** new private static helper
  `SsmtApplication.schemaVariantMenuItem(label, accessibleText, action)`
  builds each `CustomMenuItem`. Toolbar row composition and the
  `createSplit` `SplitMenuButton` are in `SsmtApplication.editorTab`.
  Verified via `:ssmt-gui:test`/`checkstyleMain`/`spotbugsMain` and a full
  `gradlew.bat build --offline --no-daemon --max-workers=2` (green), plus
  a manual `:ssmt-gui:installDist` build and launch (process stayed up
  cleanly, no exceptions). No behavior or label text changed for any
  existing control — regrouping and the one button-to-menu merge only.

## ADR-039: GUI Button-Naming Consistency Fixes (BUG-008)

- **Date:** 2026-08-02
- **Status:** Accepted; implemented 2026-08-02
- **Context:** `UX_REVIEW.md`'s naming-consistency section, run against
  every GUI tab, found three related label problems (`BUGS.md` BUG-008):
  `button.renderPng` ("Render PNG") and `button.renderImageAi` ("Render
  Localized Image (AI)") are two alternate techniques for the same
  conceptual action but use mismatched vocabulary (file format vs. feature
  concept); `button.exportImageAi` ("Export for Image AI") and
  `button.exportAi` ("Export for Online AI") are near-duplicate labels for
  two different export mechanisms; `button.refreshTm` ("Refresh with TM")
  abbreviates a term that its own toolbar's `button.openTm` ("Open
  Translation Memory") spells out fully.
- **Decision:** rename all three in `messages.properties` only —
  `button.renderPng` → "Render Localized Image (Text)",
  `button.exportImageAi` → "Export Image Regions for AI",
  `button.refreshTm` → "Refresh with Translation Memory". No resource key,
  handler, or `setAccessibleText` value changed — these are purely the
  user-visible/announced strings. Every doc that names these buttons
  (`BEGINNERS_GUIDE.md`, `USER_GUIDE.md`, `MANUAL_ACCEPTANCE.md`) was
  updated in the same pass so the docs and the running GUI never disagree.
- **Consequences:**
  - Historical records that quote the old label names as *evidence of what
    was found* (`BUGS.md`'s BUG-008 entry, this ADR, `UX_REVIEW.md`'s dated
    review log, `SESSION.md`'s dated updates) intentionally keep the old
    strings — they describe a past state, not current UI text, matching
    this project's convention of not rewriting history (see `BENCHMARKS.md`
    for the same principle applied to a dated benchmark run).
  - No test asserted the old literal label strings (checked before
    renaming), so no test changes were needed alongside the rename.
- **Implementation note:** three single-line changes in
  `ssmt-gui/src/main/resources/com/ssmt/gui/messages.properties`. Verified
  via a full `gradlew.bat build --offline --no-daemon --max-workers=2`
  (green) and a manual `:ssmt-gui:installDist` build/launch (both the
  Translation Editor and Image Localization tabs build eagerly at startup,
  so this exercises every renamed key).

## ADR-040: Generated Patch Overlays Must Deterministically Win CSV/JSON Merge Conflicts Against Their Source Mod

- **Date:** 2026-08-02
- **Status:** Superseded by ADR-041 for normal builds. ADR-041 (same date)
  sidesteps this ADR's whole question by publishing a pristine clone plus a
  translated clone of the entire source mod — the user enables the
  translated clone *instead of* the original, never both, so the two mods
  never coexist and the undefined cross-mod merge this ADR worried about
  never has an opportunity to occur. This entry is kept for its research
  (the live wiki/forum verification below is still accurate and useful
  background) and because ADR-041 itself still lists a live translated-clone
  smoke test as required before `BUGS.md` BUG-009 fully closes — not because
  any part of the Track A hedge below remains in production. The Track A
  hedge itself (`PatchNamingAuditor`, `patchId == sourceModId` rejection,
  overlay `gameVersion` writing) was removed 2026-08-02 once its premise
  no longer applied — see the removal note at the end of this entry.
- **Status (original, superseded):** Proposed — pending verification against current official Starsector modding documentation before implementation
- **Context:** Starsector merges CSV and JSON data files across all active mods
  that share the same relative file path, including the core game. When a mod
  entry's key (usually the ID field) matches a core game entry, the mod entry
  wins — this is well-defined. However, community documentation explicitly
  describes two *non-core* mods providing CSV entries with the same key as
  "undefined (extraordinarily bad)" behavior. SSMT's entire override/patch
  model depends on exactly this scenario: the generated overlay and the
  source mod are two separate mods (separate `mod_info.json`, separate IDs)
  that intentionally define rows with matching identities, relying on the
  overlay's translated text winning the merge. In practice this has already
  worked in the Azure Federation smoke test (translated ship names and
  `descriptions.csv` prose rendered correctly), which suggests the resolution
  is deterministic based on mod load order in the current engine version —
  but this project has never made that mechanism explicit or guaranteed it
  in the generated output.
- **Decision:**
  1. ~~The generated patch's `mod_info.json` must declare an explicit
     `dependencies` entry referencing the source mod's `id`~~ — **already
     implemented**, found while checking this ADR against current code:
     `PatchBuilder.writeMetadata` (`ssmt-patcher`) already writes
     `dependencies: [{"id": sourceModId}]`, and `PatchBuilderTest` already
     asserts `metadata.path("dependencies").get(0).path("id")` equals the
     source mod's id. No minimum-version constraint is set (only `id`),
     which is a real gap this ADR still leaves open, but the dependency
     declaration itself is not new work.
  2. The generated patch's `id` must be constructed to deterministically sort
     after the source mod's `id` under whatever load-order mechanism the
     current Starsector version uses — **status downgraded to unresolved,
     see research note below: available sources suggest load order is
     lexical by the mod's `name` field, not `id`, which would mean this
     point targets the wrong field.**
  3. Before implementing (2), verify current load-order semantics against
     up-to-date official Starsector documentation or direct testing —
     partially done 2026-08-02 (see research note below); the exact
     sort key remains unconfirmed from a primary source.
  4. Add a regression test/fixture asserting that a source mod and its
     generated overlay, when both loaded, resolve every shared identity to
     the overlay's (translated) value — not merely that each extracts/builds
     correctly in isolation. This is the first test that would actually
     exercise the two-mod merge scenario rather than just the single-mod
     patch-generation pipeline. **Still not done** — this requires an
     in-game or engine-level test double SSMT does not currently have.
- **Research note (2026-08-02):** attempted the verification this ADR asks
  for, via `WebSearch`/`WebFetch` against the current Starsector wiki
  (wiki.gg and Fandom mirrors). Findings, with sourcing:
  - **Confirmed, and more serious than this ADR originally assumed:** the
    live wiki (fetched today, not a cached/stale copy) states verbatim,
    "Two mods providing CSV entries with the same key is undefined
    (extraordinarily bad)" — [Getting Started with Starsector modding](https://starsector.wiki.gg/wiki/Getting_Started_with_Starsector_modding).
    This is the *current* documented position, not outdated wiki content as
    originally worried — meaning the engine's own documentation does not
    promise deterministic resolution at all, for any version. The Azure
    Federation smoke test's success is evidence of *current observed
    behavior*, not a documented guarantee.
  - **Unconfirmed, conflicting signals:** multiple web-search summaries
    (not independently verified via a direct primary-source quote — the
    specific wiki pages that likely state this either 402'd or 403'd on
    direct fetch) converge on "mod load order is lexical, ordered by the
    mod's **`name`** field (not `id`), and reorderable by prefixing the
    name (e.g., a leading `0` promotes a mod earlier)." If accurate, ADR
    point (2) as originally written targets the wrong field — the fix
    would need to affect the generated patch's `name`, not its `id`. This
    needs a primary-source confirmation (or direct in-game testing) before
    any implementation decision, not just another search-summary.
  - **Still unconfirmed:** whether declaring `dependencies` (already done,
    see point 1) has any effect on load order beyond gating enablement, or
    whether load order is entirely independent of the dependency graph.
- **Consequences:**
  - Users can no longer enable a generated overlay without the source mod
    also being present and enabled — surfacing a clear, game-level error
    instead of a silent or confusing failure. (Already true today, per
    point 1's finding.)
  - The reliability of every previously-shipped compatibility fix (BUG-001,
    BUG-002) depends on this merge resolving correctly; this ADR makes that
    dependency explicit rather than leaving it as an implicit assumption
    resting on one successful smoke test. The 2026-08-02 research note
    makes this a *confirmed current risk*, not a hypothetical one — the
    engine's own documentation disclaims a guarantee.
  - Decision (2) must be revisited once the correct sort key (name vs. id
    vs. something else) is confirmed from a primary source or direct
    testing — this ADR does not authorize shipping an unverified ordering
    assumption, and the 2026-08-02 research raises real doubt that `id` is
    even the right field to manipulate.
- **Invariant not to weaken:** this must not be "solved" by merging the
  overlay's content into the source mod's own files (violates Mod-Safe,
  Principle 4) or by silently assuming an untested load-order mechanism.
- **Implementation note (2026-08-02, Track A — hedge, not resolution):**
  escalated to `BUGS.md` BUG-009 at user request, with a two-track plan:
  code hardening now (this note) plus a human-executed empirical protocol
  (`MANUAL_ACCEPTANCE.md`, "Load-order empirical test protocol") to actually
  determine the sort key, kept separate so no code bets on an unconfirmed
  mechanism. Shipped, regardless of which hypothesis Track B eventually
  confirms:
  - `PatchRequest` (`ssmt-patcher`) gained `sourceModName`/`sourceGameVersion`
    components and a hard rejection of `patchId.equals(sourceModId)` — a
    real collision independent of the load-order question, since Starsector's
    mod registry is keyed by id.
  - `PatchBuilder.writeMetadata` now writes `gameVersion` when the source
    declares one (previously omitted entirely, a plain correctness gap found
    while checking point 1); `fingerprint` now includes the new fields so a
    source mod's declared name/gameVersion changing correctly triggers a
    republish.
  - New `PatchNamingAuditor`/`PatchNamingFinding` (`ssmt-project`, modeled on
    the existing `FontCoverageAuditor` pattern) non-blockingly warns (SLF4J,
    not a GUI dialog in this pass) when a candidate `patchId`/`patchName`
    does not extend the source mod's own `id`/`name` as a literal prefix —
    hedging both the id- and name-based load-order hypotheses at once
    without betting on either. Wired into `LocalizationProjectService`'s
    `create`/`build` call sites, the one choke point shared by CLI and GUI.
  - GUI's suggested `patchName` changed from prepend-style
    (`"Translation (" + name + ")"`, which bore no lexical relationship to
    the source name) to append-style (`name + " Translation"`), mirroring
    the already-correct `patchId` suggestion and matching what the new
    auditor expects by default.
  - Deliberately **not** touched this pass: the overlay's output *folder*
    name (a third, completely untested hypothesis — encoding an assumption
    there before Track B's data exists would repeat this ADR's original
    mistake of targeting `id` alone based on one confounded data point), and
    the `dependencies` entry's missing `name`/`version` sub-fields (real,
    smaller, separate follow-up).
  - Regression coverage: `PatchBuilderTest` (gameVersion written/omitted,
    fingerprint changes on gameVersion-only change, `rejectsPatchIdEqualToSourceModId`),
    new `PatchNamingAuditorTest` (including a fixture literally reproducing
    the real Azure Federation id/name values — passes on id, fails on name,
    exactly matching what shipped), and `LocalizationProjectServiceTest`
    (gameVersion propagates end-to-end through `build()`; `create()`/`build()`
    still succeed, never throw, when naming fails the extension check).
    Full `gradlew.bat build --offline --no-daemon --max-workers=2` green.
- **Removal note (2026-08-02, after ADR-041 landed):** with the clone-swap
  model in place, a generated artifact's `patchId`/`patchName` no longer
  determines a second mod's identity that has to coexist with the source —
  `PatchBuilder` (rewritten for ADR-041) preserves the source mod's own
  `mod_info.json` verbatim in both clones, so there is no longer a separate
  overlay identity for `PatchNamingAuditor` to check "extends the source"
  for, and no cross-mod merge for the `patchId == sourceModId` rejection to
  guard against. `PatchNamingFinding.java`/`PatchNamingAuditor.java`
  (`ssmt-project`) and `PatchNamingAuditorTest.java` were deleted as
  orphaned dead code — the concurrent session that implemented ADR-041 had
  already removed every call site (`LocalizationProjectService`'s
  `auditPatchNaming` calls, its `Logger` field, and the now-unused
  `slf4j-api`/`logback-classic` dependencies in
  `ssmt-project/build.gradle.kts`), leaving only the unreferenced source
  files themselves to clean up. The `gameVersion`/`sourceModName` fields on
  `PatchRequest` and their tests were independently adapted (not removed)
  by that same session to fit the new clone-fingerprinting model — see
  `PatchBuilderTest.preservesSourceMetadataInsteadOfWritingOverlayMetadata`
  and `LocalizationProjectServiceTest.gameVersionIsPreservedInTranslatedAndPristineClones`.
  Full `gradlew.bat build --offline --no-daemon --max-workers=2` green
  after removal.

## ADR-041: Publish a Pristine Source Clone and a Translated Clone

- **Date:** 2026-08-02
- **Status:** Accepted
- **Context:** ADR-040/BUG-009 established that a small translation overlay
  and its source mod intentionally supplying the same CSV/JSON identities is
  unsupported by Starsector's documented merge contract. Naming/load-order
  conventions can hedge observed behavior but cannot turn undefined behavior
  into a supported invariant. The user explicitly chose local translated-clone
  publication and requested a pristine clone alongside it as a backup.
- **Decision:** Every successful project build publishes two source-external
  sibling directories:
  1. `<output>-source-backup`, a byte-preserving clone of the selected source
     mod at build time;
  2. `<output>`, a second clone of that same snapshot with validated translated
     artifacts applied.

  The original source remains untouched. The translated clone preserves the
  source mod's original `mod_info.json`, ID, dependencies, scripts, JARs, and
  assets; users enable the translated clone instead of the original, never both
  simultaneously. Publication stages both trees before replacing either live
  destination and rolls back prior outputs if either replacement fails. Builds
  occur on explicit Build actions, not on each editor keystroke.
- **Consequences:** The normal local workflow no longer depends on cross-mod
  duplicate-key resolution. BUG-009 can close after automated clone invariants
  and one live translated-clone smoke test pass. Full clones may contain
  copyrighted source assets and therefore remain local/personal-use output
  unless the source author permits redistribution. A future shareable package
  must contain deltas and source hashes only, with recipients constructing
  their own clones locally.
- **Rejected alternatives:** modifying the source violates Mod-Safe; relying
  on lexical ID/name/folder ordering remains undocumented; cloning on every
  edit is expensive and risks publishing incomplete drafts.

## ADR-042: Glossary-First Offline Translation Provider Chain

- **Date:** 2026-08-02
- **Status:** Accepted
- **Context:** Users need fully offline draft translation using Argos Translate
  and TranslateLocally while preserving SSMT's rule that machine output is
  never silently accepted. Both projects expose local command-line interfaces,
  but use different model selection: Argos selects by language pair while
  TranslateLocally requires an installed model ID.
- **Decision:** Add bundled local-executable provider adapters and resolve each
  request in this order: one unambiguous approved exact translation-memory
  value; Argos Translate; TranslateLocally only if Argos fails. Execute without
  a shell, provide text by UTF-8 stdin, enforce a two-minute timeout and 1 MiB
  capture limit, and never download a model. Return provider provenance and the
  Argos failure when fallback occurs. Feed a result back to translation memory
  only through explicit approval, as `HUMAN_EDITED`.
- **Consequences:** Runtime translation can remain fully offline once users
  install binaries and models. Conflicting glossary values fall through for
  review rather than being selected arbitrarily. The adapters are concrete
  input to the future A1 plugin-API split, but do not yet create a public
  arbitrary-JAR provider surface. Executable/model installation and licensing
  remain the user's responsibility.
- **Rejected alternatives:** running both engines sequentially would translate
  already translated text and degrade quality; automatically learning every
  machine draft would poison the approved glossary; invoking through a shell
  would add command-injection risk.

## ADR-043: Confidence-Gated Offline-to-AI Escalation

- **Date:** 2026-08-02
- **Status:** Accepted; supersedes ADR-042's failure-only provider progression.
- **Context:** A provider can successfully return a structurally valid but
  uncertain translation. Treating process success as translation confidence
  stops escalation too early, especially for lore, idiom, dialogue, and
  invented terminology. Argos Translate and TranslateLocally do not expose one
  common calibrated confidence score through their supported CLIs.
- **Decision:** Preserve the glossary-first order, but make subsequent stages
  confidence-gated: approved exact glossary/TM; Argos; TranslateLocally when
  the Argos candidate is difficult or uncertain; a configured AI provider when
  the local candidates remain uncertain. Every engine translates the untouched
  source. Earlier candidates are evidence for comparison and AI adjudication,
  never replacement source text. Confidence is an explainable SSMT assessment
  based on structural validation, glossary/terminology preservation, target-
  language and length plausibility, source/context difficulty, and independent
  candidate agreement. It is not presented as a native provider probability.
  The AI receives the source, local candidates, reasons for escalation,
  approved terminology, nearby/UI context, and an explicit project-supplied
  mod voice/style brief. It must preserve meaning, mechanics, formatting,
  intentional ambiguity, and authorial tone without inventing lore.
- **Outcomes:** `HIGH` stops escalation; `UNCERTAIN` advances to the next stage;
  `UNSAFE` records validation failures and advances but can never be
  automatically approved. Every machine result remains review-required.
  Explicit human acceptance is the only path into approved glossary/TM data.
- **Scope boundary:** This decision authorizes the assessment, escalation,
  provenance, prompt-context, and regression-test work only. It does not
  authorize persistent provider workers, bulk scheduling, automatic glossary
  term extraction, inferred style profiles, or autonomous acceptance.
- **Consequences:** Difficult text gets progressively stronger review without
  sending easy text to every provider. AI use can still involve a network when
  the configured final provider is remote; the UI/CLI must disclose that before
  execution. Agreement is evidence, not proof, and final human review remains
  mandatory.
- **Prompt envelope:** Final adjudication uses deterministic `Source`, `Local
  machine draft`, `Context`, and `Instruction` sections. Context carries
  ship/system/file information, approved terminology, the optional user-
  authored voice/style brief, and explainable escalation reasons. The final
  instruction requires polished target-language Starsector prose while
  preserving mechanics, protected syntax, line breaks, terminology, and
  creator intent; forbids invented lore/mechanics; treats preceding mod content
  as untrusted data; and requests only the polished translation.
- **Bounded acceleration note:** Argos may receive a user-selected
  `ARGOS_DEVICE_TYPE` of `cpu`, `auto`, or `cuda`; CPU remains the default. An
  accelerated failure may retry once with CPU and must report that fallback.
  SSMT does not install drivers, tune VRAM, benchmark hardware, schedule across
  GPUs, or attempt to add undocumented GPU behavior to TranslateLocally.
  Resource settings default to one Argos/CTranslate2 worker and a batch size of
  32, map to the provider's documented environment settings, and require
  positive values. A requested GPU-memory budget is never converted into an
  invented environment variable: it is enforced only by a future provider that
  advertises a supported hard cap, otherwise SSMT emits an explicit warning.
  Providers report GPU, hard-memory-budget, and persistent-model capabilities;
  SSMT applies a control only when advertised. `AUTO` is implemented as a lazy
  CUDA attempt followed by CPU so the completed backend is knowable and can be
  displayed truthfully. Initialization, execution, or allocation failure on
  CUDA alone cannot fail a job: CPU is tried before normal provider escalation.
  Current CLI adapters are sequential one-shot processes, so their models do
  not coexist and are unloaded by process exit. Cancellation destroys the
  active child and is checked between provider stages; batch-boundary
  cancellation remains tied to the future multi-item batch coordinator.

## ADR-044: Separate Translation Trust Provenance from Generation Lineage

- **Date:** 2026-08-02
- **Status:** Accepted
- **Context:** Collapsing Argos, TranslateLocally, and final-AI output into
  `AI_TRANSLATED` loses auditability. A reviewed result can simultaneously be
  human-approved and descended from one or more machine providers, so one enum
  cannot accurately represent both trust and generation history.
- **Decision:** Add explicit `ARGOS_TRANSLATED` and `TRANSLATE_LOCALLY` values
  alongside `AI_TRANSLATED`, `HUMAN_EDITED`, `AUTHOR_LOCALIZATION`,
  `MANUAL_IMPORT`, and `FUZZY_MATCH`. Migrate SQLite to schema v3 with a
  companion `translation_generation_metadata` table keyed to the translation
  entry. Store provider ID, model/language package, provider version when known,
  generation timestamp, AI-refined flag, and `DRAFT`/`APPROVED`/`REJECTED`
  review status. Human approval may promote the entry provenance to
  `HUMAN_EDITED` while retaining its machine lineage in the companion row.
- **Consequences:** Approved glossary lookup continues to use trusted entry
  provenance and cannot mistake a draft for approval. Provider/model history
  remains queryable after review. Existing schema v1/v2 databases migrate in
  place; existing constructors and interchange remain compatible. Provider
  versions may be empty when the supported CLI exposes no cheap/version-stable
  value rather than recording a guess.
- **Scope boundary:** This slice stores and reads lineage in SQLite and threads
  it through local-provider approval. Portable JSON/CSV metadata interchange,
  automatic persistence of every unreviewed candidate, and multi-stage lineage
  arrays remain separate roadmap work because each changes retention/privacy or
  interchange compatibility.

## ADR-045: Non-Negotiable Translation Pipeline Safety Invariants

- **Date:** 2026-08-02
- **Status:** Accepted
- **Decision:** Plugin/provider execution must never automatically overwrite a
  human-preferred entry, accept a local/AI draft, write into a source mod,
  bypass protected-syntax validation, silently alter line-break sequences,
  contact AI without explicit provider configuration, require GPU support,
  download models without clear user consent, repeatedly translate an exact
  duplicate request within a session, or rerun an accepted exact translation
  unless explicitly requested. These are correctness boundaries, not optional
  UX preferences.
- **Implementation:** Translation-memory preference ranking protects human
  entries from lower-trust automatic replacement. Approval is explicit and now
  rejects `UNSAFE` candidates. `TranslationValidator` compares printf, brace,
  dollar-token, and exact CRLF/CR/LF sequences. The chain uses a bounded
  1,024-entry LRU keyed by the complete request and clears that draft cache on
  approval; approved exact glossary/TM lookup remains first. Source publication
  follows ADR-041's source-external clone workflow. CPU is the default and GPU
  failure falls back. Local adapters never download models; final AI remains
  unavailable until an explicitly configured provider is supplied.
- **Consequences:** A translator must correct unsafe output before approval.
  Exact duplicate drafts avoid repeat inference without being treated as
  accepted. Cache eviction may permit later re-generation, but accepted entries
  remain durable in translation memory. An explicit user action may deliberately
  revise a human entry or rerun an accepted translation; automation may not.

## ADR-046: Deterministic Translation Provider Routing

- **Date:** 2026-08-02
- **Status:** Accepted; consolidates ADR-043 routing intent without weakening
  ADR-044/045 provenance and safety boundaries.
- **Objective:** Reuse trusted work first, use one lightweight local provider by
  default, and reserve AI for observable cases where additional reasoning may
  help. SSMT remains a translation-management system; providers generate drafts
  and never control validation, persistence, acceptance, or publication.
- **Deterministic order:** author localization; exact approved TM; explicitly
  accepted fuzzy TM; approved glossary/terminology; selected local provider;
  validation/routing; configured AI when permitted; human review; SQLite TM.
  Fuzzy matches remain suggestions until accepted. Newly extractable fields
  enter untranslated while existing stable identities retain their work.
- **Local selection:** Argos is the default. A configured TranslateLocally model
  may be chosen as the preferred local provider. Do not execute multiple local
  providers for every string; request another only after observable routing
  signals justify escalation. Providers receive the complete extracted string
  and available mod/file/content-type/internal-ID/terminology context.
- **Routing heuristic:** Scores route work and are never presented as quality or
  acceptance confidence. Current implemented signals are protected-syntax/
  validation failure `+3`, likely unchanged source language `+3`, long source
  `+2`, independent local disagreement `+2`, and large length deviation `+1`.
  Scores `0–2` use local output, `3–4` queue optional AI review, and `5+` may
  invoke AI only when the selected mode permits it and a provider was explicitly
  configured. Lore/dialogue/mechanics/content-type, glossary conflicts,
  terminology inconsistency, and untranslated proper-noun signals require
  evidence-backed detectors before receiving score weight.
- **Modes:** `LOCAL_ONLY` never invokes AI; `SMART_DEFAULT` permits routed AI and
  is the intended default; `AI_ASSISTED` permits refinement of routed long-form
  or complex entries. All modes still require validation and human review and
  must operate without GPU, AI, or Internet access when those are unavailable.
- **Success criterion:** Correctness, determinism, reuse, context preservation,
  maintainability, and low resource usage take priority over cleverness or
  maximum throughput.

## ADR-047: Explicit Whole-Project AI Review and Validated Bulk Approval

- **Date:** 2026-08-02
- **Status:** Accepted
- **Context:** Translators may reasonably ask one configured AI to review a
  complete project and then accept the returned set without clicking every
  row. That convenience must not turn response arrival into autonomous trust.
- **Decision:** The existing AI export continues to include every project
  entry. Import offers two explicit policies: reviewable drafts (default), or
  **Approve all validated AI results** after a separate confirmation. Bulk
  approval is fail-closed: response identity/completeness, unchanged source,
  protected syntax, and required line breaks are validated before any project
  or translation-memory change. Any failure rejects the whole import.
- **Provenance:** Explicit bulk approval records trusted `HUMAN_EDITED`
  provenance while retaining `external-ai` (or response-supplied provider),
  model, provider version, generation time, AI-refined status, and `APPROVED`
  review state as companion lineage metadata. Draft imports remain
  `AI_TRANSLATED` with `DRAFT` review state.
- **Boundary:** This is one deliberate human approval over a validated batch,
  so it does not weaken ADR-045. SSMT does not approve on file arrival, skip
  confirmation, partially accept an invalid response, or write source mods.

## ADR-048: Manual Browser-Based AI Review Bridge

- **Date:** 2026-08-02
- **Status:** Accepted
- **Decision:** Provide a separate no-API workflow that exports
  `PROMPT.txt`, `TRANSLATION_REQUEST.json`, and `README.txt`. Oversized requests
  are sorted by stable entry identity and split by a user-selected maximum;
  each part repeats the three files and is independently importable, while the
  root adds `manifest.json`. Requests contain source, retained local draft,
  compact file/key context, terminology, stable IDs, provenance, and an empty
  translation field.
- **Browser boundary:** SSMT may pass a configured HTTP(S) URL to the operating
  system's default browser. It never logs in, manipulates a page, uploads,
  types, downloads, stores credentials, or scrapes responses. Provider names
  and URLs are user-configurable; ChatGPT, Claude, and Gemini names supply
  optional URL presets. API providers remain optional and separate.
- **Import boundary:** Each response is validated transactionally for project,
  source-mod, entry/source identity, complete expected entry set, duplicate or
  missing entries, nonblank translations, protected syntax, line breaks, and
  unchanged provenance. Results enter as `AI_TRANSLATED` drafts only into
  blank project entries; all existing local, human, and author text is retained.
- **Privacy:** Export displays a third-party disclosure. Only the user can
  decide to paste or upload the generated content.

## ADR-049: Bounded Final-AI Adjudication Coordinator

- **Date:** 2026-08-02
- **Status:** Accepted
- **Decision:** Connect the deterministic routing assessment, all retained
  independent local candidates, canonical adjudication prompt, and one
  explicitly configured AI provider. `LOCAL_ONLY` never invokes AI.
  `SMART_DEFAULT` invokes it only at the `INVOKE_AI_IF_ENABLED` threshold;
  `AI_ASSISTED` may also use `OPTIONAL_AI_REVIEW`.
- **Consent:** A remote provider cannot receive a request until its remote
  location is disclosed and explicit consent is supplied. No configured
  provider retains the local draft offline and reports unresolved uncertainty.
- **Validation/trust:** Blank or structurally invalid AI output is discarded in
  favor of the retained local candidate and remains unresolved. Valid output is
  always an unapproved `AI_DRAFT` requiring review; routing never accepts it.
- **Scope:** This coordinator handles one already-produced offline draft. The
  project-level router, GUI/CLI orchestration, batching, and persistence remain
  separate work and must reuse this contract.

## ADR-050: Unified Bounded Project Translation Workflow

- **Date:** 2026-08-02
- **Status:** Accepted
- **Decision:** Route blank project entries through existing accepted project
  text, explicitly accepted fuzzy choices, approved exact TM/glossary, the
  preferred local chain, deterministic final-AI adjudication when allowed, and
  human review. Existing nonblank entries are immutable to this operation.
- **Execution:** Process deterministic bounded batches sequentially, checking
  pause/resume and cancellation between batches. Sequential execution intentionally keeps the
  effective worker count at one and prevents simultaneous large models; the
  configured maximum remains a hard upper bound rather than a throughput goal.
  Report actual provider backends and unresolved counts.
- **Context:** Requests carry source/target languages, mod ID, normalized file,
  content type, internal ID, terminology, and optional style guidance. Argos is
  the default first local provider; TranslateLocally is explicitly selectable.
- **Retention/interchange:** Unreviewed generation lineage is retained only
  when enabled. Optional deterministic schema-v2 JSON and versioned-header CSV
  sidecars carry provider/model/version/time/refinement/review metadata; legacy
  documents import without inventing absent lineage.
- **Surfaces:** CLI `translate-project` and GUI **Translate Blank Entries** use
  the same coordinator. GUI work is cancellable between batches. Provider
  settings persist no secret values, only endpoint/model and credential
  environment-variable name. Remote AI requires per-run disclosure consent.

## ADR-051: Source-Bound Checkpoints and Non-Acting Review Diagnostics

- **Date:** 2026-08-02
- **Status:** Accepted
- **Decision:** Save a project-adjacent checkpoint after each completed
  translation batch. Resume is explicit and allowed only when source-mod ID and
  a deterministic digest of entry identities/source text match the open
  project. Checkpoints never write into the source mod or imply acceptance.
- **Diagnostics:** Local-provider preflight is read-only and never executes,
  installs, or downloads. Routing-evidence inspection is report-only with zero
  routing weight. Neither action may enable a provider or remote AI.
- **Review:** Provenance is filterable; lineage is loaded on demand. Approval
  remains an explicit human action, and rejection cannot overwrite accepted
  human or author localization.

## ADR-052: Data-Only Glossaries and Non-Applying Author Reports

- **Date:** 2026-08-02
- **Status:** Accepted
- **Decision:** Define a bounded, versioned JSON glossary as inert project
  content rather than an executable plugin. Duplicate/blank terms fail closed.
  Checking a glossary emits identity-bound warnings and never changes or
  approves translations.
- **Reporting:** Export deterministic CSV rows with stable identity, status,
  provenance, source, and translation. Export does not mutate the project or
  source mod.
- **Diagnostics:** Generic background failures use a tested presentation that
  states the failed operation, source immutability, and a recovery action while
  retaining the underlying reason.
