# Known Issues / Bugs

`Last updated: 2026-08-02 by Claude (BUG-012 added and Resolved: StandardFileInjector.injectCsv now shares CsvExtractor's UTF-8/GB18030 fallback, found while building a real translated clone for ApproLightPlus; BUG-011 expanded with a systematic CoverageGapAuditor check plus hull_mods/skill_data/wing_data.csv and five data/campaign/*.csv files, all evidence-confirmed across every Test mods/ mod; BUG-005/BUG-009 still waiting on a live re-test after these fixes)`

This file is the source of truth for **confirmed, evidence-backed bugs**.
`ROADMAP.md` tracks phase checklists; this file tracks what's actually broken
and why. If a bug is fixed, move it to the "Resolved" section with the fix
commit/test reference — don't delete the history.

Do not restate this list in `SESSION.md`, `ROADMAP.md`, or elsewhere — link
here instead, so there's exactly one place to update.

---

## Open

### BUG-005: Manual Starsector translated-clone smoke test not yet run against Azure Federation
- **Found:** 2026-08-01, while closing out BUG-001/002/003 below
- **Symptom:** BUG-001/002/003 are code-fixed and regression-tested (see
  Resolved section), but nobody has actually launched Starsector with a
  freshly generated, unmodified Azure Federation overlay since the fixes
  landed. `ROADMAP.md`'s "Post-RC Real-Game Findings" exit criterion requires
  this manual repeat, not just passing unit/integration tests.
- **Required:** run the full manual workflow (scan → extract → translate →
  build) against `Test mods/AzureFederation/`; verify the pristine backup;
  install/enable only the ADR-041 translated clone (disable the original),
  launch Starsector, and verify: (a) it loads without structural CSV repair,
  (b) a campaign starts, and (c) the previously-`???` manufacturer/tooltip/
  rank fields show translated text.
- **Status:** blocked on a human with a licensed Starsector install; not
  something an AI session can complete unattended.
- **First run completed 2026-08-02:** user ran the full manual workflow
  against Azure Federation. Result: no structural CSV repair needed, a
  campaign starts, and every previously-`???` field showed translated text
  **except one ship system**, traced and fixed as BUG-011 (Resolved). A
  second pass (refresh the project, translate the 6 newly-extracted
  `ship_systems.csv` entries, rebuild, reinstall, and re-launch) is still
  needed to confirm that specific fix in-game before this bug and BUG-009
  can close.

### BUG-009: Generated overlay's CSV/JSON merge-win behavior rests on undefined Starsector engine behavior
- **Code resolution (2026-08-02):** ADR-041 changes normal publication to a
  pristine source clone plus a translated clone that preserves the source mod's
  ID and complete content. Users enable the translated clone *instead of* the
  original, so no two active non-core mods provide the same identity. The
  publisher stages both trees, rejects links/special files, detects source
  changes during staging, rolls back prior outputs on replacement failure, and
  fingerprints all source bytes plus translation artifacts. Patcher and
  end-to-end project/CLI/Auto/GUI tests pass.
- **Remaining before Resolved:** complete BUG-005's live translated-clone smoke
  test. ADR-040's ten-pair load-order experiment is no longer required to make
  normal builds safe, though it remains useful optional engine research.
- **Found:** 2026-08-02, drafted as `DECISIONS.md` ADR-040 (status: Proposed)
  and escalated here at explicit user request after research confirmed the
  underlying risk is live, not stale.
- **Symptom:** SSMT's entire override/patch model depends on the generated
  overlay mod and the source mod — two separate mods with two separate
  `mod_info.json` files and IDs — defining CSV/JSON rows with the same
  identity key, relying on the overlay's translated row winning the merge.
  A live fetch of the current Starsector wiki (2026-08-02, not a cached or
  outdated copy) states verbatim: "Two mods providing CSV entries with the
  same key is undefined (extraordinarily bad)." This is the *current*
  documented engine position — there is no documented guarantee the overlay
  wins, only empirical observation that it did in one smoke test (Azure
  Federation). Every previously-shipped compatibility fix that depends on
  overlay text actually appearing in-game (BUG-001, BUG-002) inherits this
  same unverified reliance.
- **Confirmed already true, found while checking this:** the generated
  patch's `mod_info.json` already declares an explicit `dependencies` entry
  naming the source mod's `id` (`PatchBuilder.writeMetadata`,
  `PatchBuilderTest` — see `DECISIONS.md` ADR-040 point 1), so a patch
  cannot be enabled without its source mod present. This gates
  *enablement*, not confirmed to affect *load order* — see below.
- **Still open / unconfirmed:**
  1. Whether Starsector's mod load order is determined by the mod's `id`,
     its `name`, folder name, or the dependency graph. Web research
     converged on "lexical order of the mod's `name` field, reorderable by
     prefix" but this could not be confirmed against a primary source (two
     candidate wiki pages returned HTTP 402/403 on direct fetch) — only via
     AI-summarized search results, which is not sufficient confidence for a
     shipped behavior guarantee.
  2. Whether declaring `dependencies` has any effect on load order beyond
     gating enablement.
  3. Whether the resolution is genuinely deterministic in the currently
     targeted engine version at all, or only "usually works" — the wiki's
     own "undefined (extraordinarily bad)" language suggests the engine
     authors do not consider it safe to rely on, regardless of what's
     empirically observed today.
- **Required to close:** complete the ADR-041 live translated-clone workflow
  in BUG-005 with the original mod disabled. The older ADR-040 ordering
  protocol is optional research because normal publication no longer creates
  the conflicting two-active-mod condition.
- **Status:** code-fixed, verification open — the empirical load-order question is untouched (see
  below). Code-hardening closed 2026-08-02: `PatchRequest` now rejects
  `patchId == sourceModId` (a plain collision, not a load-order guess);
  `PatchBuilder` now writes `gameVersion` when known; new
  `PatchNamingAuditor` non-blockingly warns when a candidate `patchId`/
  `patchName` doesn't extend the source mod's own `id`/`name`, hedging both
  leading hypotheses at once; the GUI's suggested `patchName` was fixed to
  append (not prepend), so the tool's own default no longer trips its own
  new warning. None of this resolves *which* mechanism is real — it's
  defense in depth, not a fix for the underlying unknown. See
  `DECISIONS.md` ADR-040's 2026-08-02 implementation note for full detail
  and regression test references.
- **Still blocked:** only the translated-clone live-game verification now
  requires a human with a licensed Starsector install. Establishing the old
  overlay load-order mechanism is no longer a release prerequisite.
- **Invariant not to weaken:** do not "solve" this by merging the overlay's
  content into the source mod's own files (violates Mod-Safe, Principle 4),
  and do not ship an ordering fix based on an unconfirmed assumption about
  which field Starsector actually sorts by.

---

## Resolved

### BUG-012: Translated-clone build fails on non-UTF-8-encoded source CSVs
- **Found:** 2026-08-02, while building a translated clone for
  `ApproLightPlus 0.3.9RC1` (part of the same real-mod translation pass as
  BUG-011). Build failed with `Could not inject CSV file
  data\weapons\weapon_data.csv: Input length = 1`.
- **Symptom:** `CsvExtractor` (`ssmt-extractor`, extraction time) already
  tolerates legacy-encoded source CSVs: it tries strict UTF-8 first and
  falls back to GB18030 on `CharacterCodingException`, per the documented
  compatibility boundary. `StandardFileInjector.injectCsv`
  (`ssmt-patcher`, build/reinjection time) never got the same treatment —
  it called `Files.readString(source, StandardCharsets.UTF_8)` directly, so
  any mod whose CSV source is legacy-encoded (confirmed: `ApproLightPlus`'s
  `weapon_data.csv` contains byte `0xA3` at offset 5856, invalid strict
  UTF-8) extracted fine but failed at build time. This is a real
  extraction/build asymmetry bug, not a source problem — the file loads and
  displays correctly in Starsector today under whatever encoding the game's
  own loader uses.
- **Fixed:** 2026-08-02. `StandardFileInjector` now decodes CSV sources with
  the identical UTF-8-then-GB18030-fallback routine `CsvExtractor` already
  uses (including the same leading-BOM strip), instead of a hardcoded
  strict-UTF-8 read.
- **Regression test:**
  `StandardFileInjectorTest.injectsCsvSourceEncodedAsGb18030LikeCsvExtractorTolerates`
  (new, GB18030-encoded fixture).
- **Invariant preserved:** output is still always written as UTF-8
  (`PatchArtifact.utf8`); only the *source* read gained the fallback — this
  mirrors extraction exactly rather than inventing new tolerant behavior.

### BUG-011: Ship system, hull mod, skill, and fighter-wing role display text are unextracted player-visible fields
- **Found:** 2026-08-02, the actual BUG-005 live Starsector smoke test —
  user ran the full manual workflow against Azure Federation and reported
  no structural CSV errors, a campaign starts, and all previously-`???`
  fields showed translated text **except one ship system**, which still
  rendered `????` in the loadout UI.
- **Symptom (ship systems, directly observed):** `data/shipsystems/ship_systems.csv`
  — a real, standalone Starsector CSV file distinct from
  `ship_data.csv`/`weapon_data.csv` — had no entry at all in
  `StandardCsvSchemas` (`ssmt-extractor`). Its `name` column holds each ship
  system's Chinese display name (e.g. `新星巡航驱动`, `火力限制解除`), keyed by a
  unique `id` with no `type` column. Because the file matched no schema, its
  `name` values were never extracted, never translated, and copied
  byte-for-byte into the translated clone unchanged — rendering as `????`
  since the active Starsector font lacks the glyphs. Confirmed against the
  real fixture: all 6 rows in Azure Federation's `ship_systems.csv` were
  affected identically.
- **Symptom (three more fields, found by a proactive follow-up audit across
  every mod in `Test mods/` at explicit user request** — "check if anything
  includes extra sections just like this" **— not independently observed
  in-game yet):** the same "whole file has no schema at all" gap, for three
  more standard Starsector files present with an essentially identical
  header in **every single mod checked** (9+ mods):
  - `data/hullmods/hull_mods.csv` — `name` (hull mod display name),
    `tech/manufacturer`, `desc`, and `short` (tooltip text) all confirmed
    with real Chinese content in the Azure Federation fixture (e.g. `偏光耗散镀层`
    / `此镀层有效调整了伤害抗性` for the `AeCoat` hull mod). `sModDesc` exists in the
    header of most mods but no non-blank content was found in the fixtures
    checked, so it was **not** added — same "confirmed only" bar as below.
  - `data/characters/skills/skill_data.csv` — `name`, `description`, and
    `author` (an in-fiction attribution line, itself player-visible in the
    skill tooltip) confirmed with real content (e.g. skill `天穹之剑`,
    attributed to `Selenara 联邦海军学院`).
  - `data/hulls/wing_data.csv` — `role desc` (the fighter wing's role
    tooltip, e.g. `重型战斗机`) confirmed with real content.
- **Symptom (five more fields, found the same way after building a
  systematic `CoverageGapAuditor` check at explicit user request — "add a
  check to see if anything else present" — see below):** the same gap for
  five `data/campaign/*.csv` files, present with an essentially identical
  header across every mod checked and confirmed with real Chinese `name`
  content (e.g. `industries.csv`'s `轨道站 - 秘甘智库`, `submarkets.csv`'s
  `联邦后勤保障基地`): `commodities.csv`, `industries.csv`,
  `market_conditions.csv`, `special_items.csv`, `submarkets.csv`.
- **Fixed:** 2026-08-02. `StandardCsvSchemas` now declares all nine files:
  `ship_systems.csv` (identity `id`, required `name`), `hull_mods.csv`
  (identity `id`, required `name`, optional `tech/manufacturer`/`desc`/`short`),
  `skill_data.csv` (identity `id`, required `name`/`description`, optional
  `author`), `wing_data.csv` (identity `id`, optional `role desc` only — no
  required column, since role text is supplemental, not the entry's own
  name), and `commodities.csv`/`industries.csv`/`market_conditions.csv`/`submarkets.csv`
  (identity `id`, required `name`, optional `desc`) plus `special_items.csv`
  (same, plus optional `tech/manufacturer`, present in some mods' variant of
  the file but not others). No other column in any of the nine files was
  directly observed player-visible, so none else was added — same
  evidence-gated policy as BUG-002/ADR-032, no new ADR needed.
- **Added a systematic check so the next gap doesn't need another manual
  audit:** `CoverageGapAuditor` (`ssmt-extractor`), wired into `ssmt-cli
  extract`, scans every `.csv` file `StandardCsvSchemas` doesn't recognize
  and flags the ones containing likely non-English text as a reviewable
  finding (file + short excerpt) — read-only, never extracts or translates
  anything itself. Re-running it after this fix confirms all nine files
  above are now silent across every mod in `Test mods/`.
- **Self-review correction (2026-08-02):** running the new schemas against
  every real mod in `Test mods/` (not just synthetic unit fixtures) found
  the initial `wing_data.csv` entry was a regression for
  `Goat_Aviation_Bureau`: that mod's file has a second, header-like row
  restating each column's meaning as a `#`-prefixed comment (e.g. `#名字` in
  the `role desc` position) with every identity cell blank. The extractor's
  existing blank-identity tolerance didn't cover this shape (the row isn't a
  pure `#`-prefixed row, and its one optional text column is non-blank), so
  extraction hard-failed for that mod. Fixed by setting
  `skipUnidentifiedRows = true` on the `wing_data.csv` schema, the same flag
  `descriptions.csv`/`weapon_data.csv` already use for exactly this reason.
  Regression test:
  `CsvExtractorTest.skipsWingDataColumnCommentRowWithBlankIdentityButNonBlankRoleDesc`,
  fixture shaped after the real file. Re-verified extraction succeeds
  cleanly across all 11 `Test mods/` mods (one, `Moci的随意之作`, is
  correctly blocked by an unrelated, pre-existing, by-design source-typo
  rejection — not a regression).
- **Explicitly checked and NOT added (insufficient evidence or wrong tool for
  the job):** `data/campaign/rules.csv` and its sibling variants
  (`rules_ENG.csv`, `rules1.csv`, `UNGP_rules.csv`, etc.) and similar
  dialogue/bar-event files (`abilities.csv`, `character_backgrounds.csv`,
  `officer_name_to_character.csv`, `enb_rooms.csv`) carry extensive embedded
  text but use a nested rule-scripting structure, not a flat
  id/name/description row shape — a real future candidate, but out of scope
  for the conservative flat-schema model without dedicated design work.
  `【2.2.8b】Moci的随意之作`'s `data/strings/all_generated_descriptions.csv`
  is structurally identical to `descriptions.csv` but under a non-standard,
  mod-specific filename — correctly left uncovered by a *standard* schema;
  the existing opt-in CSV schema catalog (`--csv-schema` / GUI "Create with
  CSV Schema") is the right tool for that mod specifically, not a new global
  default. Several mods ship sibling `_ENG`/`_EN` CSVs (`hull_mods_ENG.csv`,
  `ship_data_ENG.csv`, `weapon_data_EN.csv`, `descriptions_ENG.csv`, etc.)
  that still contain Chinese text despite the name — **not** confirmed to be
  author-provided English localization (the content itself is non-English),
  so their actual purpose is unconfirmed; deliberately left alone rather
  than guessed at. `LunaSettings.csv` (mod-settings-menu labels),
  `magicTrail_data.csv`/`trail_data.csv` (particle trail names/comments), and
  a handful of other mod-specific config files were also flagged but not
  investigated further this pass — lower player-visibility priority than
  the campaign-economy fields above.
- **Regression tests:**
  `StandardCsvSchemasTest.definesConservativeStandardStarsectorSchemas`
  (extended for all nine files),
  `CsvExtractorTest.extractsShipSystemDisplayNameAndPreservesMechanicsColumns`,
  `...extractsHullModNameAndOptionalTooltipColumnsWhenPresent`,
  `...extractsSkillNameDescriptionAndOptionalAuthor`,
  `...extractsWingRoleDescWhenPresentButToleratesItsAbsence`,
  `...extractsCampaignEconomyFileNamesAndOptionalDescriptions` (all new,
  fixtures shaped after each real file's header),
  `CoverageGapAuditorTest` (new, `ssmt-extractor`).
- **Verification:** re-created the Azure Federation project against the real
  fixture after the fix — the 6 `ship_systems.csv` `name` values now extract
  correctly with their original Chinese text intact (`ssmt-cli project
  create`, manually inspected). Full `gradlew.bat build --offline --no-daemon
  --max-workers=2` green.
- **Still needed before BUG-005/BUG-009 can close:** the existing, already
  100%-translated Azure Federation project must be refreshed (picks up
  these newly-extracted entries plus other schema improvements landed since
  it was first created), the new entries translated, and the clone rebuilt
  and re-tested in a live Starsector session to confirm the specific ship
  system (and, opportunistically, hull mods/skills/wings) now render
  correctly. This is the same human-executed step BUG-005 already requires
  — just one more pass. The three fields found by the follow-up audit are
  code-fixed and regression-tested but **not yet independently confirmed
  in-game** the way the original ship-system finding was — treat them as
  high-confidence but unverified until a live re-test.

### BUG-001: CSV structural/sentinel row reinjection corrupts identity
- **Found:** 2026-07-26, manual Starsector smoke test, Azure Federation mod
- **Symptom:** Source `data/strings/descriptions.csv` contains harmless
  structural rows (`,,,,,,`) and a `#` sentinel/comment row
  (`#ships,,,,,,`). On reinjection these got serialized as quoted empty
  strings (`"",,,,,,` and `"#ships",,,,,,`), which Starsector does **not**
  treat as equivalent to the original. Multiple quoted-empty rows collapsed
  into a duplicate composite identity `["" | ""]` and the game aborted on
  load.
- **Fixed:** 2026-08-01. `StandardFileInjector.injectCsv`
  (`ssmt-patcher`) now slices each row's original raw source text via
  `CSVRecord.getCharacterPosition()` and re-emits unchanged blank/`#`-prefixed
  rows verbatim, instead of through generic `CSVFormat.DEFAULT` per-cell
  quoting. Composite identity duplicate validation was **not** weakened — see
  `DECISIONS.md` ADR-031.
- **Regression tests:**
  `StandardFileInjectorTest.preservesBlankSentinelRowRawRepresentationDuringCsvInjection`,
  `...preservesHashPrefixedStructuralRowRawRepresentationDuringCsvInjection`,
  `LocalizationProjectServiceTest.structuralRowFixturePreservesSentinelAndCommentRowsThroughDeterministicWorkflow`.
- **Still outstanding:** the actual Starsector smoke test re-run — see BUG-005.

### BUG-002: Incomplete player-visible localization coverage
- **Found:** 2026-07-26, same smoke test, after BUG-001 manual repair
- **Symptom:** Overlay loads, but Chinese text remains visible (renders as
  `???` where the game font lacks glyphs) in fields not covered by
  extraction schemas: `ship_data.csv` manufacturer/tech text,
  `weapon_data.csv` manufacturer/role/tooltip text, faction rank/role
  display names.
- **Fixed (initial confirmed-evidence scope):** 2026-08-01.
  `StandardCsvSchemas` (`ssmt-extractor`) now declares `tech/manufacturer` as
  an optional column for `ship_data.csv`/`weapon_data.csv`, plus
  `primaryRoleStr`/`customAncillaryHL` for `weapon_data.csv` — the three
  fields directly confirmed non-English in the fixture. `JsonExtractionSpec`
  gained a bounded object-key wildcard `patterns` component, used by
  `StandardJsonFileExtractor.FACTION_SPEC` to cover
  `ranks.ranks.*.name`/`ranks.posts.*.name`/`fleetTypeNames.*`. No general
  "translate all CSV/JSON columns" fallback was added — see `DECISIONS.md`
  ADR-032.
- **Regression tests:** `StandardCsvSchemasTest`,
  `CsvExtractorTest.extractsWeaponAndShipOptionalColumnsWhenPresentButToleratesTheirAbsence`,
  `JsonExtractionSpecTest`,
  `StandardJsonFileExtractorTest.factionExtractsRankPostAndFleetTypeDisplayNamesWhenPresent`.
- **Deliberately deferred, not a gap:** the remaining vanilla `weapon_data.csv`
  tooltip columns (`speedStr`, `trackingStr`, `turnRateStr`, `accuracyStr`,
  `customPrimary`, `customPrimaryHL`, `customAncillary`) were not directly
  observed non-English in the fixture; add them only when real-game evidence
  confirms it, per ADR-032's evidence bar.
- **Still outstanding:** the actual Starsector smoke test re-run — see BUG-005.

### BUG-003: SQLite reopen/resume workflow risk
- **Found:** 2026-07-26, user-observed during manual testing
- **Symptom:** Restarting SSMT and selecting a database location could, in
  some paths, initialize/replace the intended catalog instead of resuming
  it.
- **Mitigation (pre-existing):** the explicit **Open Existing Database**
  action was preserved unchanged — it remains the correct way to
  deliberately resume a catalog.
- **Additional bug found and fixed 2026-08-01:** tracing this bug's
  persistence lifecycle found that `SsmtApplication.initializeTranslationMemory()`'s
  default-catalog startup path only called the file-must-already-exist
  `ProjectWorkspaceController.verifyTranslationMemory`, so on a genuinely
  fresh install (no remembered path, no default catalog file yet) it could
  **never create a first catalog at all** — contradicting ADR-030's shared
  default-catalog guarantee. Fixed by adding
  `ProjectWorkspaceController.openOrCreateTranslationMemory(Path)`
  (create-if-missing) for the startup path only; the explicit Open Existing
  Database button is untouched. Also tightened
  `TranslationMemoryMergeService.plan(...)` to require the destination
  catalog to already exist, closing a silent-empty-catalog-creation gap in
  the supposedly read-only `compare()`. See `DECISIONS.md` ADR-033.
- **Regression tests:**
  `SqliteTranslationMemoryTest.restartingApplicationPreservesMultipleIndependentCatalogsAcrossReopen`,
  `...openingHealthyCatalogNeverReinitializesSchemaOrData`,
  `TranslationMemoryMergeServiceTest.refusesToCompareOrMergeWhenDestinationCatalogDoesNotExist`,
  `ProjectWorkspaceControllerTest.openOrCreateTranslationMemoryCreatesMissingDefaultCatalogThenPreservesDataAcrossReopen`,
  `CatalogRestartResumeRegressionTest` (all scenarios, `ssmt-gui`).

### BUG-004: AI-import protected-syntax validation false positive on identical text
- **Found:** 2026-08-01, tracing an external handoff note describing repeated
  "AI response has invalid protected syntax" rejections during a Blue Sea
  Fisheries import, including in a constructed case where every translated
  entry was set identical to its own source text.
- **Symptom:** `TranslationValidator.hasMalformedMessageArgument`/
  `hasMalformedPrintf` (`ssmt-validation`) scanned only the *translated* text
  for leftover `{`/`}`/`%` characters after stripping recognized
  placeholders, with no comparison against the source. A source string
  containing a non-numeric brace (e.g. a keybind hint like `"{LMB}"`) or a
  `%` not adjacent to a digit could fail validation even when the
  "translation" was an untouched copy of that same source, aborting the
  entire transactional AI import on the first such entry.
- **Fixed:** 2026-08-01. Both heuristics now take source and translated text
  and compare leftover stray-character *counts* between them
  (`strayBraceCount`/`strayPercentCount`), flagging only when the
  translation introduces more than the source already had. See
  `DECISIONS.md` ADR-034 (new).
- **Regression tests:**
  `TranslationValidatorTest.identicalTranslationNeverFailsValidationEvenWithStrayBraceOrPercent`,
  `AiTranslationExchangeServiceTest.importsResponseWhenTranslationIdenticalToSourceContainingStraySyntax`.
- **Not independently re-verified against the original Blue Sea Fisheries
  files** (not present in this repo's `Test mods/`) — the fix is confirmed
  correct by direct source-code analysis and a synthetic reproduction, not by
  re-running the original failing import.
- **Self-review correction (2026-08-01):** the first version of this fix
  aggregated stray `{` and `}` counts into one number, which could let a
  translation swap "extra unmatched opens" for "extra unmatched closes" (same
  total count) past detection. Fixed to count each character separately.
  Regression test:
  `TranslationValidatorTest.detectsSwappedStrayBraceKindEvenWhenTotalStrayCountIsUnchanged`.

### Self-review corrections to BUG-002/BUG-003 fixes (2026-08-01)
- **BUG-002:** `JsonExtractor`'s new pattern-matching literal segments (e.g.
  `"ranks"`, `"name"` in `/ranks/ranks/*/name`) were not being RFC 6901-unescaped
  before JSON field lookup, unlike the pre-existing `pointers` mechanism
  (which resolves through `JsonNode.at(pointer)`, and `StandardFileInjector`'s
  `decodePointer`). Harmless for the three shipped patterns (none contain
  `~0`/`~1`), but would have silently mismatched a future pattern segment
  needing escaping. Fixed for consistency; no fixture currently exercises the
  escaped case since none is needed yet.
- **BUG-003:** `ProjectWorkspaceController.openOrCreateTranslationMemory`'s
  javadoc promised it creates a catalog "when the path does not yet exist,"
  but it only worked because its one caller (`SsmtApplication`) happened to
  pre-create the parent directory. The method now creates parent directories
  itself, so it honors its own contract for any future caller. Regression
  test: `ProjectWorkspaceControllerTest.openOrCreateTranslationMemoryCreatesMissingParentDirectories`.

### BUG-006: Translation Editor toolbar overflows the default/minimum window width
- **Found:** 2026-08-01, usability review of this session's GUI changes
  (own-initiative code review, not user-reported)
- **Symptom:** `SsmtApplication.java` (Translation Editor tab) packed 13
  buttons plus a status label and the live coverage label into one `HBox`
  toolbar with no wrapping and no `ScrollPane` anywhere in the file.
  Estimated preferred width from the button labels alone was roughly
  2000-2500px, far exceeding the default 1200px scene width and the
  documented 900px minimum window width. The two controls added earlier
  this session — the "Create with CSV Schema" button (ADR-035) and the
  coverage label (ADR-036) — sat at/near the end of the row, so they were
  the most likely to be clipped off-screen.
- **Fixed:** 2026-08-01. The toolbar is now four stacked `HBox` rows inside
  one `VBox`, grouped by purpose (project lifecycle; translation-memory
  actions; AI/build/review actions; status labels), instead of one flat
  row. Every button's label, accessible text, and action are unchanged —
  only the grouping/layout changed. See `DECISIONS.md` ADR-038.
- **Verification:** no automated layout test exists for this (the project
  has no TestFX/scene-graph-inspection dependency); verified by the
  per-row label-width arithmetic in ADR-038 plus a manual
  `:ssmt-gui:installDist` build and launch (process stayed up cleanly, no
  exceptions).
- **Invariant preserved:** every action reachable from the old toolbar
  remains reachable — nothing was moved behind a menu or dropped.
- **Grouping refined, 2026-08-02:** the initial four-row split above grouped
  by container-fit rather than strictly by workflow stage. Per direct user
  review (and the newly added `UX_REVIEW.md` checklist's "group by
  workflow stage" rule), the refresh/refresh-with-TM pair is now kept
  adjacent in one row, "Mark Reviewed" moved next to the search/filter
  controls it actually acts on (it was previously grouped with AI/build
  actions it has no relation to), and the three "Create…" buttons were
  merged into one `SplitMenuButton` (default click = plain create; the two
  schema variants are dropdown items), since they are mutually-exclusive
  one-time-per-project alternates rather than three independently-reached-
  for actions. See `DECISIONS.md` ADR-038's 2026-08-02 update for the full
  before/after and the accessibility-workaround note (a `CustomMenuItem`
  wrapping an accessible-text-bearing `Label`, since `MenuItem` itself has
  no accessible-text API).

### BUG-007: Image Localization "Add Region"/"Auto-Detect Text" silently discarded in-progress translations and imported AI-regenerated art
- **Found:** 2026-08-01, usability review of the ADR-037 image AI
  regeneration feature (own-initiative code review, not user-reported)
- **Symptom:** `ImageLocalizationEditorViewModel.load()` unconditionally
  cleared `translations`, `lastExports`, and `regeneratedByIndex`, and
  re-seeded every surviving region's translated text back to its raw
  source text. This method was called not only from "Open Image"
  (expected/correct) but also from "Add Region" and "Auto-Detect Text" —
  so adding one missed region, or re-running detection, after already
  hand-translating and AI-regenerating other regions silently discarded
  that work with no confirmation.
- **Fixed:** 2026-08-01. Added
  `ImageLocalizationEditorViewModel.reload(List<OcrTextRegion>)`, used by
  "Add Region"/"Auto-Detect Text" instead of `load(Path, List)`. It
  identifies a region by exact pixel geometry (not list position, since
  detection can reorder/renumber regions) and carries forward translated
  text and any imported `RegeneratedRegion` for every region whose
  geometry survives into the new set. `load(Path, List)` itself is
  unchanged — "Open Image" still fully resets state, correctly, since a
  genuinely new image shouldn't carry anything forward. See `DECISIONS.md`
  ADR-038.
- **Regression tests:**
  `ImageLocalizationEditorViewModelTest.reloadPreservesTranslationWhenAddingANewRegion`,
  `...reloadPreservesImportedRegeneratedArtWhenAddingANewRegion`,
  `...reloadDropsPreservedDataForRegionsNoLongerPresent`,
  `...reloadRequiresSourceImageAlreadyLoaded`.
- **Invariant preserved:** the existing strict pixel-dimension validation
  on regenerated-region import (`ImageRegionAiExchange`, ADR-037) was not
  loosened.

### BUG-008: Naming-consistency issues found by the UX_REVIEW.md checklist
- **Found:** 2026-08-02, first run of `UX_REVIEW.md`'s naming-consistency
  section against every GUI tab (own-initiative structural review, not
  user-reported) — confirmed by direct source/`messages.properties` read;
  not independently verified via an actual click-through, since no
  UI-automation tool is available in this environment.
- **Symptom (three related findings):**
  1. `button.renderPng` ("Render PNG") and `button.renderImageAi` ("Render
     Localized Image (AI)") are two alternate techniques for the same
     conceptual action — produce a translated output image
     (`SsmtApplication.java`'s Image Localization tab) — but use
     inconsistent vocabulary for the result: one names the file format
     ("PNG"), the other names the feature concept ("Localized Image").
  2. `button.exportAi` ("Export for Online AI," Translation Editor tab) and
     `button.exportImageAi` ("Export for Image AI," Image Localization
     tab) are near-duplicate labels for two different export mechanisms
     (a text translation package vs. padded region crops).
  3. `button.refreshTm` ("Refresh with TM") abbreviates "Translation
     Memory" to "TM," while the adjacent toolbar button
     `button.openTm` ("Open Translation Memory") spells the same term out
     fully in the same container.
- **Fixed:** 2026-08-02. All three are `messages.properties` string-only
  renames, no behavior change: `button.renderPng` → "Render Localized Image
  (Text)" (parallels `button.renderImageAi`'s "Render Localized Image
  (AI)"); `button.exportImageAi` → "Export Image Regions for AI" (no longer
  textually near-duplicates `button.exportAi`'s "Export for Online AI");
  `button.refreshTm` → "Refresh with Translation Memory" (spelled out,
  matching `button.openTm`'s "Open Translation Memory" in the same
  toolbar). See `DECISIONS.md` ADR-039.
- **Invariant preserved:** `GuiText` resource keys, `setAccessibleText`
  wiring, and every handler's behavior are unchanged — only the visible/
  announced label text changed. Every doc referencing the old labels
  (`BEGINNERS_GUIDE.md`, `USER_GUIDE.md`, `MANUAL_ACCEPTANCE.md`) was
  updated to match; historical records describing the pre-fix state
  (this entry, `DECISIONS.md`, `UX_REVIEW.md`'s dated review log,
  `SESSION.md`) intentionally keep quoting the old label names as
  evidence of what was found, not as current UI text.
- **Verification:** full `gradlew.bat build --offline --no-daemon
  --max-workers=2` green; no test asserted the old literal label strings
  (checked before renaming). Manual `:ssmt-gui:installDist` build/launch
  confirmed no exceptions (the Translation Editor and Image Localization
  tabs are both built eagerly at startup, so this exercises every renamed
  key).

---

### BUG-010: User-facing failures do not consistently explain recovery or unchanged state

- **Found:** 2026-08-02, repository-wide F13 source audit covering GUI alerts,
  CLI diagnostics, and domain exceptions; see `DIAGNOSTIC_AUDIT.md`.
- **Symptom:** operation-specific headings are usually present, but several GUI
  paths display only `exception.getMessage()`, and some validation/CLI errors
  omit the affected identity, whether a transaction wrote anything, or the
  next safe action.
- **Confirmed vs. unverified:** confirmed by source inventory. Exact wrapping,
  focus order, screen-reader announcement, and wording in rendered dialogs are
  not verified because this environment has no UI automation.
- **Invariant:** remediation must preserve original exception causes and stable
  codes, fail-closed validation, source immutability, and transactional writes;
  it must not expose credentials or proprietary text in generic diagnostics.
- **Closure requirement:** add focused formatter/presentation tests plus the
  diagnostic-language checks in `MANUAL_ACCEPTANCE.md`; do not close this bug
  from string replacement alone.
- **Source-level remediation (2026-08-02, Claude):** extended the existing,
  already-tested `UserDiagnostic.failed(operation, throwable)` presentation
  helper (`ssmt-gui`, previously wired into only 2 of ~30 failure paths) to
  every remaining raw `showError(header, exception.getMessage())` GUI dialog
  call site, `runProjectAction`'s catch block (gained a new `operation`
  parameter, all 10 call sites updated), and the two hand-rolled
  `buildProject`/`runRefreshTask` background-task failure handlers that had
  bypassed it. Also fixed two concrete bugs found in the process: a missing
  `error.aiImport` resource key that caused `MissingResourceException`
  instead of ever showing the intended "Import AI response" dialog, and
  `openTranslationMemory` using a file-chooser title (`chooser.tm`) as an
  error heading. Added a new `CliDiagnostics.explain(operation, exception)`
  helper (`ssmt-cli`) reused by all CLI commands that previously printed a
  bare operation-name-plus-raw-message line, without changing which output
  mechanism (SLF4J logger vs. Picocli's injected `PrintWriter`) each command
  already used. Removed now-orphaned `messages.properties` keys the
  `UserDiagnostic` conversion superseded. New regression tests:
  `GuiTextCompletenessTest` (scans `SsmtApplication.java` for every literal
  `GuiText.get("...")` key and asserts it resolves — the regression test for
  the exact `error.aiImport` class of bug), `CliDiagnosticsTest`, and an
  `OfflineTranslateCommandTest` case asserting the printed failure text
  states the operation and a next action, not just the raw exception
  message. Full `gradlew.bat build --offline --no-daemon --max-workers=2`
  green; manual `:ssmt-gui:installDist` build/launch confirmed no
  `MissingResourceException` or other startup regression.
- **Still open:** per the closure requirement above, this bug is **not**
  closed — the remaining gap is purely the human-executed rendered-dialog
  wording/focus-order/screen-reader check already listed in "Confirmed vs.
  unverified," which needs `MANUAL_ACCEPTANCE.md` run in a real GUI session.

## Rules for this file

1. Every open bug needs: how it was found, exact symptom, what's confirmed
   vs. still unverified, and any invariant that must **not** be weakened to
   fix it.
2. Never close a bug here without naming the regression test that would
   catch it coming back.
3. If real-mod testing reopens a "complete" phase-gate item in `ROADMAP.md`,
   note that cross-reference here, not just in `ROADMAP.md`.
