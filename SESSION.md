# Current Development Session

> Historical handoff: this is the final recorded development-session snapshot
> from 2026-08-02, not a live release dashboard. For current release work, use
> `BUGS.md`, `MANUAL_ACCEPTANCE.md`, and `ROADMAP.md`. References to local test
> mods describe a private corpus that is intentionally excluded from this
> repository.

`Last updated: 2026-08-02 by Claude (BUG-005's first live smoke test ran against Azure Federation and found BUG-011/BUG-012; both fixed, plus a new CoverageGapAuditor; AzureFederation, ApproLightPlus, and Goat_Aviation_Bureau fully translated/rebuilt; 5 more mods have projects created but untranslated; BlueSeaFisher refreshed; Moci correctly left blocked by a source typo)`

## Repository state

- Git status was not inspected because `git` is unavailable on this host's
  command path. Preserve unrelated/pre-existing workspace changes.
- Workspace changes are saved on disk. No commit was created because the Git
  executable remains unavailable on the host command path.
- Version is now 0.6.0 because normal publication behavior changed.
- Focused AI, translation-memory, project, and CLI tests pass.
- The authoritative full offline build passes: 119 actionable tasks, 31
  executed and 88 up-to-date, including all tests, Checkstyle, SpotBugs, JARs,
  and distributions.

## Changes this session

- **Claude (2026-08-02), first live smoke test + real-mod translation pass:**
  the user ran BUG-005's live Starsector smoke test against Azure Federation
  for the first time: no structural CSV errors, campaign starts, every
  previously-`???` field translated **except one ship system**. Traced this
  to a real gap and fixed it as **BUG-011**: `StandardCsvSchemas` had no
  entry at all for `data/shipsystems/ship_systems.csv`. A user-requested
  follow-up audit (manual, then a new read-only `CoverageGapAuditor`
  wired into `ssmt-cli extract`) found the identical gap in eight more
  standard files, all fixed the same evidence-gated way:
  `hull_mods.csv`, `skill_data.csv`, `wing_data.csv`, and
  `commodities.csv`/`industries.csv`/`market_conditions.csv`/
  `special_items.csv`/`submarkets.csv`. Testing against every real mod in
  `Test mods/` (not just synthetic fixtures) caught a self-review
  regression in `wing_data.csv` (a real mod's "column-comment" row with
  blank identity broke extraction — fixed with `skipUnidentifiedRows`) and
  a second real bug, **BUG-012**: `StandardFileInjector.injectCsv` required
  strict UTF-8 at build time even though `CsvExtractor` already tolerates
  GB18030 at extraction time, so a legacy-encoded source CSV (confirmed in
  `ApproLightPlus`) built cleanly at extraction but failed at clone-build.
  Fixed by sharing the same decode-with-fallback routine.
  - Also implemented, earlier in the same session: `ssmt-cli project
    import-ai-response` (headless equivalent of the GUI's Import AI
    Response, same validation), and **Export for Online AI** batch
    splitting into numbered sibling files once a project exceeds a
    configurable entry count (found after a 2087-entry mod's AI response
    came back truncated and in the wrong schema).
  - **Rebuilt and fully translated** (100%, pristine backup byte-verified):
    **AzureFederation** (485 entries — the live-tested mod; confirmed the
    fixed ship system now shows English text in the rebuilt clone),
    **ApproLightPlus** (313 entries, 95% already English), and
    **Goat_Aviation_Bureau** (976 entries, mostly long lore paragraphs).
  - **Project created but deliberately left untranslated** (per explicit
    user direction once the true scale became clear — thousands of strings
    per mod, much of it narrative prose): **ApproLight** (1027),
    **MikanInstituteofKnowledge** (769), **ArcLightBureau** (1072),
    **FSF_MilitaryCorporation** (1492), **J GEEK Federation** (1948).
  - **BlueSeaFisher**: refreshed its existing project to pick up the new
    schema fields (1845/2234 now, up from 1845/2087) but did not translate
    the 132 new entries.
  - **TraverserDesignBureau**: already had a project (2426 entries, 0%
    translated) from a prior session; left untouched.
  - **`Moci的随意之作`**: confirmed still blocked by a real, pre-existing
    source typo (`"autofire": Ture`) that this project intentionally never
    auto-corrects (`CLAUDE.md` Do-NOT list) — not something this session
    could or should fix silently.
  - Full `gradlew.bat build --offline --no-daemon --max-workers=2` green
    throughout (multiple runs across the fixes).

- **Claude (2026-08-02), AI fallback + BUG-010 remediation:** checked
  `SESSION.md`/`CHANGELOG.md`/`ROADMAP.md` per user request; both explicit
  "Continue with" steps at the time (installing real Argos/TranslateLocally
  binaries, running the live Starsector clone smoke test) were
  human/environment-gated, so picked up **BUG-010**
  (`DIAGNOSTIC_AUDIT.md`'s plain-language diagnostic gaps), the one item
  explicitly flagged as AI-doable. Mid-investigation the user asked to
  deprioritize Argos/TranslateLocally installation "as long as AI can still
  be used," then — after finding `ChainedProjectTranslationEngine.translate()`
  had no fallback when both local providers fail — asked for one anyway, as
  a safety net.
  - **AI fallback** (`ssmt-project/.../ChainedProjectTranslationEngine.java`):
    on total local-chain failure, now calls the configured AI provider
    directly on the untouched source (bypassing `FinalAiAdjudicator`'s
    escalation heuristic, which assumes an existing local candidate),
    respecting `LOCAL_ONLY` mode, "no provider configured," and remote-consent
    exactly like the normal path does. New `ChainedProjectTranslationEngineTest`
    cases cover all branches (success, `LOCAL_ONLY` rethrows, no provider
    rethrows, remote-without-consent throws, AI-also-fails throws combined).
  - **BUG-010 remediation:** extended the existing, already-tested
    `UserDiagnostic.failed(operation, throwable)` GUI presentation helper
    (previously wired into only 2 of ~30 failure paths) to every remaining
    raw dialog call site, gave `runProjectAction` an `operation` parameter
    (10 call sites updated), converted the `buildProject`/`runRefreshTask`
    hand-rolled background-task handlers that had bypassed it, and added a
    new `CliDiagnostics.explain(operation, exception)` helper reused across
    every CLI command's error output. Found and fixed two concrete bugs
    along the way: a missing `error.aiImport` resource key that silently
    threw `MissingResourceException` instead of ever showing the intended
    dialog, and `openTranslationMemory` using a file-chooser title as an
    error heading. Removed now-orphaned `messages.properties` keys. New
    tests: `GuiTextCompletenessTest` (scans source for every literal
    `GuiText.get("...")` key and asserts it resolves — direct regression
    coverage for the `error.aiImport` class of bug), `CliDiagnosticsTest`,
    and an `OfflineTranslateCommandTest` case. BUG-010 stays **open** — its
    own closure requirement explicitly needs the human-executed rendered-dialog
    check in `MANUAL_ACCEPTANCE.md`, which now has checklist items for both
    the GUI and CLI diagnostic text.
  - Full `gradlew.bat build --offline --no-daemon --max-workers=2` green
    throughout; manual `:ssmt-gui:installDist` build/launch confirmed no
    `MissingResourceException` or other startup regression.

- **Claude (2026-08-02), reconciling against this session's ADR-041:** while
  working BUG-009 independently (before discovering this file's much larger
  concurrent ADR-041-050 body of work), added a same-day Track A hedge to
  `ssmt-patcher`/`ssmt-project` (`PatchNamingAuditor`, an overlay-only
  `gameVersion` write, a `patchId == sourceModId` rejection). Once ADR-041's
  pristine/translated-clone model was found to already remove the need for
  that hedge entirely (no second mod identity exists to protect once the
  source mod's own `mod_info.json` is preserved verbatim in both clones),
  deleted the now-orphaned `PatchNamingFinding.java`/`PatchNamingAuditor.java`/
  `PatchNamingAuditorTest.java` (their call sites, `Logger` field, and the
  `slf4j-api`/`logback-classic` `ssmt-project` build dependency had already
  been removed by this session's own ADR-041 work — only the unreferenced
  source files were left). Updated `DECISIONS.md` ADR-040's status to
  "Superseded by ADR-041" with a removal note, `BUGS.md`, `TEST_PLAN.md`,
  and `CLAUDE.md` to match. Did **not** review or reconcile ADR-042 through
  ADR-050, `DIAGNOSTIC_AUDIT.md`, `BUG-010`, or the 0.6.0 version bump beyond
  confirming the full `gradlew.bat build --offline --no-daemon --max-workers=2`
  is still green (119 tasks) after the removal — that is a much larger body
  of work than this note covers, and a future session should not treat this
  entry as having audited it.

- **Claude (2026-08-02), `/init` CLAUDE.md refresh:** re-read `DECISIONS.md`
  in full (confirmed ADR-001–052 now exist), `README.md`, `AI_CONTRACT.md`
  (now has a numbered §0 "Never Do These" quick reference), `PROJECT_MANIFEST.md`,
  `BUGS.md` (BUG-010 confirmed), `settings.gradle.kts` (still 13 modules — all
  the new ADR-042–052 work landed inside existing modules, `ssmt-ai`/
  `ssmt-project`/`ssmt-gui`/`ssmt-cli` mainly), and the actual
  `ProjectTranslateCommand`/`OfflineTranslateCommand` CLI classes to get the
  real `translate-project`/`offline-translate` command syntax right rather
  than guessing. Rewrote `CLAUDE.md`'s module map, architecture-pipeline
  diagram, Do-NOT list, and current-status/bug sections to match the
  ADR-041 clone-publication model and the ADR-042–052 offline/AI translation
  pipeline (local Argos/TranslateLocally chain, confidence-gated routing,
  bounded final-AI adjudication, browser-AI bridge, glossary-as-data,
  checkpoints). Added an explicit "concurrent-session" note to `CLAUDE.md`
  itself, since this file's own history is the clearest evidence future
  sessions need that reading state fresh (not assuming it matches what you
  last wrote) matters in this repository. Did not attempt a similarly deep
  pass over `ARCHITECTURE.md`/`STANDARDS.md`/`WORKFLOW.md`/`TEST_PLAN.md` —
  those may also warrant reconciliation against ADR-042–052 in a future session.

- Completed five additional non-plugin slices: tested generic failure wording,
  GUI background-operation adoption, bounded shareable glossary JSON plus
  advisory conflict checks, deterministic translation-report CSV export, and
  Image Localization workflow-row separation.

- Added source-bound per-batch translation checkpoints and explicit resume,
  read-only/non-downloading provider preflight, fixture-backed report-only
  routing evidence, provenance filters, and on-demand lineage details.
- Completed the F13 source-level diagnostic inventory in
  `DIAGNOSTIC_AUDIT.md`; tracked remaining cross-cutting recovery-language work
  as BUG-010 without claiming rendered UI verification.

- Put plugin-API extraction on explicit hold. Replaced it in the next-five plan
  with the F13 plain-language diagnostic audit.
- Completed the first replacement slice: the editor now displays provenance
  and provides explicit Approve Draft and Reject Draft actions. Approval is
  limited to valid nonblank rows and promotes trust to `HUMAN_EDITED`; rejection
  clears the selected draft. Added read-only, non-downloading local-provider
  preflight and report-only routing-evidence foundations for later GUI/fixture
  completion; neither changes routing yet.

- Completed ADR-050 across five slices: unified blank-entry project routing;
  Argos/TranslateLocally preference and complete context; sequential bounded
  batches with cancellation/backend reporting; opt-in schema-v2 JSON and CSV
  generation-lineage interchange with legacy reads; and shared GUI/CLI
  orchestration. Remote AI requires per-run consent, settings retain no secret
  values, and project-level tests prove zero remote calls without consent.

- Accepted ADR-049 and implemented the bounded one-entry final-AI adjudication
  coordinator. It consumes deterministic routing, supplies every retained local
  candidate in the canonical prompt, requires consent before remote invocation,
  stays offline without configuration, validates returned structure, and never
  marks AI output accepted. Project-level scheduling/persistence remains the
  next larger router slice.

- Accepted ADR-048 and implemented the no-API manual browser AI bridge. It
  exports prompt/request/readme artifacts, deterministically batches with a
  manifest, imports each part through fail-closed validation, and preserves
  every existing translation. GUI actions open only the default browser or
  local export folder, copy the prompt, import responses, and reopen the last
  export; no browser automation or credential handling was added.

- Added `Helsinki-NLP/opus-mt-zh-en` as the current TranslateLocally `zh` to
  `en` default. The CLI override remains available, and SSMT still does not
  download models automatically.

- Accepted ADR-047. The existing AI export is explicitly documented as a
  whole-project review package. AI response import now offers a separate
  **Approve all validated AI results** confirmation; draft import remains the
  default. Bulk approval fails closed before writes and records human-approved
  trust separately from external-AI provider/model/generation lineage.

- Accepted ADR-046 from the attached conservative routing proposal. Added
  routing-only score/decision contracts and the three user-mode contracts
  without enabling AI calls or duplicating existing project/TM services.
- Scheduled the unified project router, preferred local-provider setting, and
  context propagation. Speculative lore/dialogue/mechanics/terminology/proper-
  noun detectors remain F27 until fixtures justify their precision and weight.

- Accepted ADR-045 and closed two safety gaps: validation now compares exact
  CRLF/CR/LF sequences and `UNSAFE` drafts cannot be approved; a bounded
  1,024-entry request LRU prevents duplicate session inference and is cleared
  after explicit approval so durable glossary/TM lookup becomes authoritative.
- Codified the full provider-pipeline prohibition list across the roadmap,
  contract, plugin specification, user guide, and test plan. Final-AI wiring
  still requires an integration test proving explicit provider configuration
  and remote disclosure before any network call.

- Accepted ADR-044. Added explicit `ARGOS_TRANSLATED` and
  `TRANSLATE_LOCALLY` provenance and SQLite schema v3 companion lineage storage
  for provider ID, model/language package, provider version, generation time,
  AI-refined state, and review status.
- Local candidates now carry provider attribution. Explicit approval stores
  `HUMAN_EDITED` trust provenance without erasing the selected provider's
  lineage. Portable metadata interchange and automatic retention of every
  unreviewed candidate remain explicit follow-ups to avoid accidental privacy/
  retention and compatibility expansion.

- Added and regression-tested ADR-043's canonical final-AI prompt envelope:
  untouched source, identified Argos/TranslateLocally draft, ship/system/file
  context, approved terminology, optional style brief, escalation reasons, and
  a final instruction preserving mechanics/syntax/line breaks/terminology/
  creator intent while forbidding invented lore or mechanics. Prepared prompts
  bypass the generic provider wrapper.

- Added provider capability reporting and truthful backend selection. Argos
  advertises CUDA; TranslateLocally stays CPU-only. `AUTO` now lazily tries
  CUDA, confirms it only after success, and falls back to confirmed CPU after
  initialization/execution/allocation failure without failing solely because
  acceleration failed.
- Preserved sequential one-shot provider execution so large local models do not
  coexist and unload on process exit. Interrupted translation now destroys the
  active child and cancellation is checked between provider stages. True
  between-batch cancellation remains scoped to the future multi-item batch
  coordinator rather than creating a persistent model/cache subsystem now.

- Added explicit local-provider resource limits: maximum worker threads
  (default 1), maximum batch size (default 32), and an optional GPU-memory MiB
  budget. Argos receives its documented worker/batch environment settings.
  Because its current CTranslate2 CLI exposes no hard memory cap, SSMT warns
  that a requested GPU budget is unenforced and passes no fictional variable.

- Completed ADR-043's local confidence gate: `HIGH`, `UNCERTAIN`, and `UNSAFE`
  assessments use existing placeholder/token validation plus conservative
  unchanged-text, length, long/multiline difficulty, and independent-candidate
  agreement signals. Diagnostics state reasons and never call them native
  provider probabilities.
- Argos now stops only on a high assessment. Other successful candidates, plus
  failures, escalate to TranslateLocally using the untouched original request;
  all produced candidates and assessments are retained. Final AI adjudication
  and the project-authored style brief remain the next bounded roadmap slice.

- Completed the bounded Argos acceleration roadmap subtask: `CPU` default,
  opt-in `AUTO`/`CUDA`, child-process-only environment configuration, one CPU
  retry, session-cached acceleration failure, serialized Argos calls, CLI
  selection, and requested/used-device fallback diagnostics. TranslateLocally
  remains CPU-only and SSMT does not manage drivers or VRAM.

- Accepted ADR-043, superseding ADR-042's failure-only progression with a
  narrowly scoped confidence-gated roadmap: glossary/TM, Argos, independent
  TranslateLocally candidate for difficult/uncertain text, then configured AI
  adjudication only when uncertainty remains.
- Defined explainable `HIGH`, `UNCERTAIN`, and `UNSAFE` outcomes; original-
  source preservation at every engine; mod voice/style context; remote-provider
  disclosure; and the unchanged explicit-human-approval boundary.
- Kept feature creep out of the active work: persistent workers, bulk
  scheduling, inferred style profiles, automatic term extraction, and
  autonomous acceptance remain unscheduled. F26 records only the future
  project-authored style-profile concept.

- Accepted ADR-042 and added a glossary-first offline translation chain:
  approved exact translation-memory match, Argos Translate, then
  TranslateLocally on Argos failure.
- Added bounded, shell-free UTF-8 CLI adapters with timeout/output limits,
  provider provenance, combined fallback diagnostics, and no model downloads.
- Added explicit approval feedback to translation memory as `HUMAN_EDITED`;
  ambiguous entries and unreviewed `AI_TRANSLATED` drafts are not glossary
  hits.
- Added the `offline-translate` CLI command plus provider, chain, persistence,
  conflict, trust-boundary, and CLI regression coverage. GUI provider selection
  remains a future UX slice.

- Accepted ADR-041. Every explicit build now publishes `<output>` as a complete
  translated clone and `<output>-source-backup` as a pristine source clone.
- The original source remains untouched. The translated clone preserves the
  source mod's runtime ID, metadata, JARs, scripts, assets, and untranslated
  files; users enable it instead of the original, never both.
- Publication stages both trees, rejects links/special files, fingerprints all
  source bytes and translated artifacts, detects concurrent source changes,
  skips unchanged builds, and restores both prior clone trees after injected
  second-publication failure.
- GUI Project Info shows both clone paths; GUI safety/first-run/build language,
  CLI logging, and SSMT Auto output were migrated. Auto clones now stay inside
  its workspace so the pristine backup is not exposed as another immediate mod
  directory.
- Removed ADR-040 load-order naming warnings from active project builds and
  graduated/superseded FEATURE_BACKLOG F17. Historical ADR-040 evidence and its
  auditor types remain for traceability/compatibility.
- Updated architecture, roadmap, bug ledger, acceptance protocol, test plan,
  changelog, manifest, distribution warning, and user/beginner/Auto guides.

## Known issues

- BUG-009 is code-fixed but remains open for one live translated-clone smoke
  test. The old load-order matrix is optional research, not a prerequisite.
- BUG-005 now tracks that live Azure Federation clone test. It requires a human
  with a licensed Starsector installation.
- Full clone folders contain source assets/code and are personal-use output
  unless the source author permits redistribution.

## Continue with

**Updated 2026-08-02 (Claude), after the first live smoke test:**

1. **Re-run the live Starsector smoke test against the rebuilt AzureFederation
   clone** (`SSMT Auto - 1130的蔚蓝联邦\1130的蔚蓝联邦 translated`) to confirm
   the previously-`????` ship system now renders correctly, and check
   whether the newly-covered hull mods/skills/wings/campaign-economy fields
   also render correctly (they are code-fixed and regression-tested but not
   yet independently confirmed in-game — see `BUGS.md` BUG-011). This is the
   same human-with-a-licensed-install requirement `BUG-005`/`BUG-009` always
   had — just one more pass, now with a much higher chance of a clean result.
2. **Translate the remaining mods, if/when wanted:** `ApproLight`,
   `MikanInstituteofKnowledge`, `ArcLightBureau`, `FSF_MilitaryCorporation`,
   and `J GEEK Federation` all have a fresh project file ready
   (`SSMT Auto - .../<name> project.ssmt.json`) but zero entries translated;
   `BlueSeaFisher`'s project has 132 new blank entries since its refresh;
   `TraverserDesignBureau`'s project (2426 entries) was already 0%
   translated before this session. None of this was done by hand-translating
   further this session, per explicit user direction once the true per-mod
   scale (hundreds to thousands of entries, much of it narrative prose) was
   clear — use the normal AI-assisted workflow (Export for Online AI /
   Browser AI Review / `translate-project`) rather than treating this as an
   AI-session line-by-line task again.
3. **`Moci的随意之作` cannot be extracted at all** until its source typo
   (`"autofire": Ture` in a `.variant` file) is fixed — in a copy, never the
   original, and never auto-corrected by SSMT itself.
4. Run `MANUAL_ACCEPTANCE.md`'s diagnostic-language checklist items (closes
   BUG-010's remaining "rendered dialog wording" gap — the source-level
   remediation is done, see the BUG-010 entry below).
5. Installing real Argos Translate/TranslateLocally binaries remains
   deprioritized per earlier user direction ("put Argos/TranslateLocally on
   the backburner as long as AI can still be used") — do not treat that as
   the next step unless asked again. AI translation remains usable via the
   manual Export/Import paths and the automated router's AI fallback (added
   an earlier session as a safety net for when both local providers fail).
