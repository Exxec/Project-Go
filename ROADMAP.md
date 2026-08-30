# Project Roadmap & Milestones

`Last updated: 2026-08-02 by Codex (ADR-043 confidence-gated offline-to-AI chain scheduled)`

**Reconciliation note (2026-08-01):** this file previously carried a version
based on the 2026-07-26 snapshot, written before BUG-001/002/003 were
code-fixed. Per `AI_CONTRACT.md` §11a, `BUGS.md` is the single source of truth
for bug status — this file has been updated to match it. The new structural
suggestions from that pass (round-trip differential test, corpus expansion,
`PLAYTEST_LOG.md`, the Post-Release Incremental Coverage section) were kept
since they're still good ideas, independent of the stale bug statuses.

## Overview

SSMT development is phase-gated. A phase is complete only when its implementation, tests, documentation, and exit criteria are satisfied.

---

# Phases 1–6

Phases 1 through 6 and the original product-hardening milestone are implemented.

Current completed capabilities include:

- modular Java 25 architecture;
- CLI and JavaFX GUI;
- CSV/JSON-like/faction/variant extraction;
- non-executing ASM bytecode extraction;
- SQLite translation memory;
- protected-token validation;
- transactional pristine/translated clone publication;
- plugin catalog and bounded worker activation;
- optional OS sandbox profiles;
- optional Ollama/Gemini/OpenAI draft providers;
- OCR and deterministic image localization;
- portable localization-project format;
- exact-path custom JSON schema catalogs;
- native application image/installer configuration.

---

# Phase 7: Real-Mod Compatibility & Update Reconciliation

**Priority: Current required milestone**

## Real-mod extraction compatibility

- [x] Run a broad local extraction corpus against structurally different community mods.
- [x] Verify source SHA-256 hashes and timestamps remain unchanged during final extraction corpus run.
- [x] Add focused regressions for observed metadata variants.
- [x] Add focused regressions for CSV sentinel/header variants.
- [x] Support ordered composite CSV identities without weakening duplicate protection.
- [x] Support deterministic legacy GB18030 fallback for observed source CSVs.
- [x] Support observed loose-JSON compatibility cases without accepting arbitrary typo-like tokens.
- [x] Preserve strict rejection of malformed source such as `"autofire": Ture`.
- [x] Verify 10 of 11 supplied samples extract successfully.
- [ ] **NEW (partially satisfied 2026-08-01):** Add automated round-trip
      differential test: an unchanged-translation build (zero edits) must
      produce output byte-identical to source for every structural/sentinel/
      comment row across the full corpus — not just extraction success. A
      single-fixture version of this now exists
      (`LocalizationProjectServiceTest.structuralRowFixturePreservesSentinelAndCommentRowsThroughDeterministicWorkflow`,
      `StandardFileInjectorTest`), which is what actually fixed BUG-001 — but
      it does not yet run across the full real-mod corpus below. This item
      stays open until the corpus-wide version exists. See `TEST_PLAN.md` §15
      and `BUGS.md`#BUG-001.
- [ ] **NEW:** Expand the real-mod corpus with structurally diverse samples,
      prioritizing structural diversity over raw sample count:
  - a mod with a large custom JSON schema;
  - a mod with baked-in image text (OCR path);
  - a mod bundling a plugin/JAR;
  - a large modpack (performance/scale).

  Rationale: one real playtest (Azure Federation) found three bugs a clean
  11-mod corpus missed. More structurally different real-game data points are
  higher-value right now than more synthetic fixtures of the same shape.

## Full workflow compatibility

- [x] Obtain at least three permission-compatible/redistributable fixtures suitable for committed acceptance tests.
- [ ] Run full GUI workflow: scan, extract, edit, validate, build, rebuild.
- [x] Run automated full CLI workflow: scan, extract, edit/project, validate,
  build, unchanged rebuild, refresh dry-run/apply, and transactional failure.
- [x] Verify two unchanged builds produce byte-identical patch trees.
- [ ] Perform manual Starsector loading smoke tests for generated overlays where licensing/local installation permit.
- [ ] **NEW:** Perform manual smoke tests against the expanded, structurally
      diverse corpus above, not only Azure Federation. Log each session in
      `PLAYTEST_LOG.md` (once created) with mod name, date, screens checked,
      and findings.

## Update reconciliation

- [x] Implement project refresh against an updated source mod.
- [x] Preserve completed translations through exact stable-key matches.
- [x] Classify entries as:
  - unchanged;
  - changed;
  - added;
  - removed;
  - conflicted.
- [x] Provide context-safe translation-memory/fuzzy suggestions for changed entries.
- [x] Never auto-apply fuzzy suggestions.
- [x] Add transactional dry-run update report.
- [x] Expose dry-run update report in CLI.
- [x] Expose dry-run update report in GUI.
- [x] Cover moved keys, changed text, deletions, additions, and ambiguous fuzzy matches.

## Diagnostics

- [x] Add structured “probable source typo” diagnostics where confidence is high enough to be useful.
- [x] Diagnostics may suggest a correction such as `Ture` → `true`.
- [x] Diagnostics must never modify source files.
- [ ] Any future repair workflow must write to a user-owned copy or patch and remain explicitly opt-in.

---

## Phase 7 local compatibility snapshot: 2026-07-26

- 11 supplied community-mod samples tested.
- 10 extract successfully.
- 17,237 source files remained unchanged by SHA-256 and modification timestamp.
- The remaining sample contains:

```json
"autofire": Ture
```

in a `.variant` file and is intentionally rejected as malformed input.

Observed compatibility work includes:

- hash-comment metadata;
- structured metadata versions;
- optional CSV columns;
- harmless sentinel rows;
- ordered composite CSV identities;
- GB18030 legacy source decoding;
- supported uppercase loose-JSON literals;
- Unicode-path investigation on Windows launchers.

### Phase 7 exit criteria

Phase 7 is complete only when:

- at least three permission-compatible representative mods pass the complete workflow;
- unchanged rebuilds are byte-identical;
- update reconciliation is deterministic and transactional;
- unsupported/malformed content is reported rather than silently omitted or repaired;
- generated patches load successfully in Starsector smoke tests where permitted;
- **NEW:** the round-trip differential test above passes across the full (expanded) corpus.

**Not required for Phase 7 exit — tracked separately, see "Post-Release / Incremental Localization Coverage" below:** full player-visible field coverage (BUG-002-class gaps). Per project direction, functional and non-crashing takes priority over complete; coverage gaps are acceptable to ship and iterate on, as long as nothing breaks or corrupts data.

---

# Phase 8: Desktop Workflow & Localization UX

- [x] Autosave and crash-recovery snapshots outside source directories.
- [x] Unsaved-change warnings.
- [x] Search/filter/status/file grouping.
- [x] Keyboard navigation and bulk review.
- [x] Translation-memory exact/fuzzy suggestion UI.
- [x] Progress and cancellation for long operations.
- [x] GUI editor for custom JSON schema catalogs.
- [x] Visual image-localization editor.
- [x] **NEW (2026-08-01, implemented same day):** AI-assisted image region
      regeneration — export a padded crop + plain-text instructions per
      region for an external AI image tool, then validate (exact pixel
      dimensions enforced) and composite the regenerated art back into a
      copy of the source image. Complements, does not replace, the existing
      Java2D text-draw render path. Also wired the previously-unused
      `TesseractOcrEngine` into the GUI as an "Auto-Detect Text" button. See
      ADR-037.
- [x] Plugin discovery/failure and sandbox-boundary details UI (read-only
  discovery; activation is intentionally not offered by this screen).
- [x] Provider settings through environment-variable references.
- [x] Accessibility review.
- [x] Externalize SSMT GUI strings.
- [x] First-run guidance and author-permission/distribution acknowledgment.

### Exit criteria

- Full normal workflow without hand-editing JSON or requiring CLI.
- Long operations cancellable without partial project/patch state.
- Keyboard-only and high-DPI smoke tests pass.

---

# Phase 9: Cross-Platform Release & Security

- [x] Windows/Linux CI on JDK 25.
- [x] Self-contained Windows application-image smoke test.
- [ ] Native installer tests.
- [ ] Complete icon families.
- [x] Release version/changelog/checksum naming rules.
- [ ] Windows/macOS signing.
- [x] SHA-256 release checksums.
- [x] SBOM generation.
- [x] Dependency/archive security scanning.
- [ ] External Windows sandbox evaluation.
- [x] Clear sandbox-capability reporting.
- [x] Security review of plugin archives, worker command construction, schemas, patch publication, and credentials.
- [x] Responsible-disclosure documentation.

---

# Phase 10: Scale, Resilience & Maintenance

- [x] Establish resource budgets.
- [x] Assess streaming JSON and introduce it only where measurement justifies it.
- [x] Expand fuzz/property testing.
- [x] Cancellation/timeout tests.
- [x] Database backup/integrity/recovery procedures.
- [x] Cross-version project/plugin compatibility tests.
- [x] Redacted structured diagnostic export.
- [x] Large-modpack benchmarks.
- [x] Deprecation and migration policy.
- [x] Compatibility matrix for Starsector/Java/OS/Tesseract/provider APIs.

Phase 10 implementation and local verification completed 2026-07-26.

---

# Release-Candidate Status Audit: 2026-07-26

| Item | Classification | Evidence / remaining action |
|---|---|---|
| Automated CLI full workflow | COMPLETE | Command-level acceptance covers source hashes/timestamps, external output, unchanged rebuild, refresh, no implicit AI/fuzzy provenance, and failed-build non-publication. |
| Automated GUI workflow | COMPLETE | Real controller/service acceptance covers create, edit, save, refresh, build, unchanged rebuild, and source bytes/timestamps. |
| Interactive GUI workflow | MANUAL TEST REQUIRED | Run `MANUAL_ACCEPTANCE.md`; controller coverage is not a screen-driver claim. |
| Generated overlay in Starsector | MANUAL TEST REQUIRED | `BUGS.md`#BUG-001 (the crash-causing reinjection bug) is code-fixed and regression-tested as of 2026-08-01 — no longer blocked on a fix, only on the manual re-test itself (`BUGS.md`#BUG-005). |
| `Ture` diagnostic and strict rejection | COMPLETE | Typed code, line, suggestion, rejection, and source-preservation regression. |
| Plugin discovery/failure UX | COMPLETE | Read-only discovery, compatible metadata table, and visible failures. |
| Windows plugin OS sandbox | ENVIRONMENT BLOCKED | Process isolation exists; no verified Windows OS sandbox is available. |
| Self-contained Windows app images | COMPLETE | GUI and Auto images have packaged smoke tasks and prior passing evidence. |
| Windows native installer | ENVIRONMENT BLOCKED | WiX is not installed on this host; configuration exists but installer evidence does not. |
| Windows signing | ENVIRONMENT BLOCKED | `signtool` and a release certificate are unavailable on this host. |
| macOS packaging/signing | DEFERRED | macOS is not a release target. |
| Linux native package | NON-BLOCKING | Optional best-effort target. |
| Complete icon families | NON-BLOCKING | Current PNG/ICO assets support the Windows image; more families are polish. |
| Opt-in repaired-copy workflow | DEFERRED | This release diagnoses and never repairs source. |
| Live hooks/hot reload/publishing/automatic draft acceptance | DEFERRED | Outside current product scope. |

**Release blockers, in priority order (see `BUGS.md` for live status):**
1. **BUG-005** (manual Starsector smoke test re-run against Azure Federation) —
   BUG-001/002/003 below are code-fixed and regression-tested as of
   2026-08-01, but nobody has re-launched the game with a fresh unmodified
   overlay since. This is now the actual blocker, not more code.
2. **BUG-009 verification:** ADR-041 removes the undefined cross-mod merge
   from normal builds by publishing a pristine backup and translated clone;
   the original and translated clone are never enabled together. Automated
   clone/source-safety tests pass. One live translated-clone smoke test remains
   before moving BUG-009 to Resolved; ADR-040's ordering matrix is now optional
   historical research rather than the product fix.
3. Manual interactive GUI acceptance (`MANUAL_ACCEPTANCE.md`), independent of #1/#2.

~~BUG-001 (CSV reinjection crash)~~ — resolved 2026-08-01, regression-tested.
~~BUG-003 (SQLite reopen/data-loss risk)~~ — resolved 2026-08-01 (also fixed a
previously-undocumented first-launch catalog-creation bug found along the
way), regression-tested.

Installer/signing block a signed installed release, but not a clearly labeled unsigned self-contained testing ZIP.

**Explicitly not a release blocker:** BUG-002 (localization field coverage gaps). Per project direction, ship functional-and-non-crashing first; expand coverage incrementally afterward. See "Post-Release / Incremental Localization Coverage" below.

## Post-Phase FSF Localization Workflow Hardening

- [x] Detect the explicit FSF `aEP` / `aEP_En` parallel layout conservatively.
- [x] Preserve extraction keys and report unmatched/ambiguous relationships.
- [x] Add typed project and translation-memory provenance.
- [x] Migrate SQLite translation memory from schema v1 to v2 in place.
- [x] Preserve project and AI schema-v1 backward compatibility.
- [x] Rank translation candidates by provenance without replacing human edits.
- [x] Add deterministic, non-applying terminology consistency findings.
- [x] Add compact AI context and identity-set integrity fields.
- [x] Validate AI placeholders, `$tokens`, provenance, blank policy, and line
  breaks transactionally.
- [x] Verify anonymized FSF-style source hashes, timestamps, and file count.

---

# Post-Phase Usability Batches 1/2

- [x] Derive `ssmt-cli --version` from Gradle's `ssmtVersion` through packaged
      `Implementation-Version` metadata with a generated resource fallback for
      test/development classpaths (FEATURE_BACKLOG F10).
- [x] Add a resettable, synthetic **Open Sample Project** workflow that copies
      licensed practice content into a user-selected workspace (F11).
- [x] Add a state-derived workflow checklist and consolidated **Project Info**
      tab (F12/F15).
- [x] Show source, project, output, translation-memory, schema, and centralized
      crash-recovery paths, with safe **Open Folder** actions for paths that
      exist (F16/F18).
- [x] Show a first-use AI export explanation covering privacy, untrusted drafts,
      transactional validation, and manual review (F14).
- [x] Explain during first run that builds create pristine and translated
      clones and users enable the translated clone instead of the original
      (F19, superseded by ADR-041).
- [x] Complete the repository-wide source-level plain-language diagnostic audit
      (F13) and record its inventory and remediation boundary in
      `DIAGNOSTIC_AUDIT.md`. Rendered/manual acceptance remains separate.
- [x] Add source-bound, per-batch translation checkpoints with explicit,
      compatibility-validated resume; never modify the source mod.
- [x] Add non-downloading local-provider/model preflight diagnostics.
- [x] Add fixture-backed, report-only routing evidence without changing routing
      weights or enabling AI.
- [x] Add provenance review filters and on-demand generation-lineage details.
- [x] Add a tested plain-language background-failure presentation contract and
      apply it to generic GUI project operations; BUG-010 remains open for the
      operation-specific diagnostic backlog.
- [x] Add bounded, versioned, data-only glossary JSON interchange and
      non-applying pre-save conflict findings (F5/F25).
- [x] Add deterministic CSV translation-report export for author review (F6).
- [x] Split Image Localization controls into manual-entry, OCR, text-render,
      and AI-render workflow rows (F9).
- [x] Supersede F17's load-order-based folder suggestion: ADR-041 removes the
      two-active-mod ordering dependency and uses an explicit translated-clone
      destination.
- [x] Add the ADR-042 glossary-first offline provider foundation: Argos
      Translate and TranslateLocally adapters, bounded execution, provenance,
      and explicit accepted-term feedback.
- [x] Implement ADR-043's explainable `HIGH` / `UNCERTAIN` / `UNSAFE`
      local assessment using existing structural validation plus conservative
      unchanged-source, length, multiline/difficulty, and independent-candidate
      agreement gates. Terminology and target-language detection remain part of
      the final adjudication slice.
- [x] Replace failure-only local progression with approved exact glossary/TM →
      Argos → independent TranslateLocally candidate only for uncertain,
      unsafe, difficult, or failed Argos output. Retain both candidates,
      assessments, and original-source provenance.
- [x] Add configured AI adjudication only when the independent local candidates
      remain uncertain or unsafe.
- [x] Define and test the canonical final-AI prompt envelope: untouched source,
      identified local-machine draft, source/UI/file context, approved
      terminology, escalation reasons, optional user-authored mod voice/style
      brief, and a final non-invention/preservation instruction. Prepared
      prompts pass through provider adapters without a second wrapper.
- [x] Supply that envelope with both retained local candidates during final-AI
      adjudication and explicitly disclose when the selected AI is remote.
- [x] Export every project entry for whole-project AI review and offer a
      separate, explicit **Approve all validated AI results** import choice.
      Bulk approval is transactional/fail-closed for identity, completeness,
      protected syntax, and line breaks; ordinary draft import remains the
      default. Approved trust provenance and external-AI generation lineage
      remain distinct (ADR-047).
- [x] Set the initial TranslateLocally Chinese-to-English model default to
      `Helsinki-NLP/opus-mt-zh-en`; retain `--translate-locally-model` as an
      override and never download the model implicitly.
- [x] Add a no-API manual browser-review bridge (ADR-048): deterministic
      `PROMPT.txt`, `TRANSLATION_REQUEST.json`, and `README.txt` exports;
      independently importable stable batches with `manifest.json`; GUI actions
      to open a configured website, copy the prompt, open/reopen the export,
      and import a response. No browser automation or automatic upload.
- [x] Add local-chain regression coverage proving high-confidence early exit,
      uncertainty escalation, unsafe-result non-approval, original-source
      preservation, retained attempts, and explicit-only glossary feedback.
- [x] Add final-AI coverage for prompt evidence, offline behavior when no AI is
      configured, remote disclosure, and unresolved uncertainty.
- [x] Add bounded optional Argos acceleration: user-selected `CPU` (default),
      `AUTO`, or `CUDA`; expose the actual requested device and fallback reason;
      cache the session capability result; allow one Argos job at a time; retry
      once on CPU after accelerated startup/execution failure; and expose
      positive maximum-worker and maximum-batch limits. Accept an optional GPU-
      memory budget only as capability-aware configuration and report it as
      unenforced when the selected provider exposes no supported hard cap.
- [x] Add provider capability reporting and truthful backend diagnostics.
      `AUTO` lazily attempts CUDA only for a GPU-capable provider, confirms
      `CUDA` on success, and confirms `CPU` after automatic fallback. A GPU
      failure alone must never fail the translation job.
- [x] Keep current local engines sequential and one-shot: no Argos and
      TranslateLocally model processes coexist, and process exit unloads each
      inactive model. Destroy an active child when thread cancellation
      interrupts execution and honor cancellation between provider stages.
- [x] When a true multi-item batch coordinator is introduced, expose
      cancellation between its bounded batches and retain the current
      worker/batch/resource limits. Do not add a persistent-model cache merely
      to satisfy this item.
- [x] Accept ADR-044 and migrate translation memory to schema v3 with explicit
      Argos/TranslateLocally provenance plus companion provider ID,
      model/package, provider version, generation timestamp, AI-refined flag,
      and review-status lineage. Preserve machine lineage when explicit review
      promotes an entry to `HUMAN_EDITED`.
- [x] Version JSON/CSV interchange to optionally carry ADR-044 generation
      metadata while continuing to import legacy documents. Decide an explicit
      retention policy before automatically persisting every unreviewed local
      or AI candidate.
- [x] Enforce ADR-045 pipeline invariants for human-preference protection,
      explicit-only acceptance, source-external writes, protected syntax and
      exact line-break validation, CPU compatibility, consent-only model
      installation, bounded exact-request draft caching, and accepted-exact
      glossary/TM short-circuiting.
- [x] When final-AI execution is wired, add an integration test proving no text
      leaves the machine unless a provider was explicitly configured and the
      remote-provider disclosure/consent path completed.
- [x] Accept ADR-046 and add deterministic AI-routing contracts: observable
      weighted reasons, `0–2` local / `3–4` optional review / `5+` AI-if-enabled
      thresholds, and `LOCAL_ONLY` / `SMART_DEFAULT` / `AI_ASSISTED` modes. The
      score is routing-only and cannot approve a translation.
- [x] Build the unified project-level router in this exact order: author
      localization → approved exact TM → explicitly accepted fuzzy TM →
      glossary/terminology → one selected local provider → validation/routing →
      explicitly configured AI when allowed → human review → SQLite TM. Reuse
      existing author/TM services rather than duplicating them in `ssmt-ai`.
- [x] Add preferred-local-provider configuration (Argos default; configured
      TranslateLocally model optional) and full extracted-string context metadata.
- [ ] Add evidence-backed detectors before assigning routing weight to lore,
      dialogue, mechanics, glossary conflict, terminology inconsistency, or
      untranslated proper nouns.

# Explicitly Deferred / Out of Scope

- plugin-API extraction and conversion of built-in providers into external
  plugins (explicitly placed on hold 2026-08-02; retain the existing bounded
  provider adapters and worker-JVM sandbox without expanding the API);
- live in-game hooks;
- hot reload;
- automatic mod-repository publishing;
- automatic acceptance of AI drafts;
- automatic acceptance of fuzzy translations;
- silent source repair;
- executing genuinely untrusted Windows plugins without a verified sandbox;
- GPU driver/toolkit installation, benchmarking, VRAM tuning, multi-GPU
  scheduling, and GPU changes to TranslateLocally;
- general-purpose mod authoring;
- non-Starsector game support.

Unscheduled but not rejected feature/architecture ideas (font/glyph
checking, translation coverage dashboard, XLIFF/PO import-export, glossary
enforcement, multi-language support, plugin-API module split, etc.) live in
`FEATURE_BACKLOG.md` — that list is pre-commitment ideation, distinct from
the "deferred/out of scope" items above which are deliberately rejected for
now.

---

# Post-Release / Incremental Localization Coverage

**NEW section.** Per project direction: functional and non-crashing takes
priority over complete. Field-coverage gaps (BUG-002-class items) belong
here, separate from the hard release-blocker list above, so the release gate
doesn't grow indefinitely every time a new untranslated field is discovered
in the wild.

- [x] `ship_data.csv`/`weapon_data.csv` `tech/manufacturer` display text
      (BUGS.md#BUG-002, resolved 2026-08-01).
- [x] `weapon_data.csv` `primaryRoleStr`/`customAncillaryHL` role/tooltip text
      (BUGS.md#BUG-002, resolved 2026-08-01 — the three fields directly
      confirmed non-English in the fixture; remaining vanilla tooltip columns
      like `speedStr`/`trackingStr`/etc. are intentionally still deferred
      until directly observed, per this section's own evidence-based policy).
- [x] Faction rank/role display names (BUGS.md#BUG-002, resolved 2026-08-01 via
      a bounded `JsonExtractionSpec` wildcard-pattern mechanism covering
      `ranks.ranks.*.name`/`ranks.posts.*.name`/`fleetTypeNames.*`).
- [ ] Any further fields surfaced by ongoing playtesting — log in `PLAYTEST_LOG.md` (once created), then add here as a fixture-backed schema addition per `REAL_MOD_COMPATIBILITY.md` policy. Never generalize to "translate every textual column."
- [x] **NEW (2026-08-01, implemented same day):** CSV had no per-mod opt-in
      schema mechanism analogous to JSON's `OptInJsonSchemaCatalog` — an
      unrecognized CSV file or column was silently invisible to extraction,
      with no diagnostic. Added `OptInCsvSchemaCatalog`/
      `ConfiguredCsvFileExtractor` mirroring the JSON mechanism, wired
      through `LocalizationProjectService`, the CLI `--csv-schema` flag, and
      a GUI "Create with CSV Schema" button. See ADR-035 and
      `CSV_SCHEMAS.md`.
- [x] **NEW (2026-08-01, implemented same day):** Font glyph coverage checker
      (`FEATURE_BACKLOG.md` F1) — checks translated text against a Starsector
      BMFont file's actual glyph coverage and warns (never blocks) on
      unrenderable characters. Verified against the real Starsector default
      font (`insignia15LTaa.fnt`): correctly flags Chinese text, passes
      English/Polish. This is a partial safety net only — it can't catch
      fields never extracted at all (that's still ADR-032's scope), only
      entries that were extracted but are incomplete/wrong-script. Available
      via `ssmt project check-fonts` (CLI) and a new GUI "Font Coverage" tab.
      See ADR-036.
- [x] **NEW (2026-08-01, implemented same day):** Translation coverage
      dashboard (`FEATURE_BACKLOG.md` F2) — overall and per-file
      translated/total counts, available via `ssmt project coverage` (CLI)
      and a live summary label in the GUI's Translation Editor tab. See
      ADR-036.

This section is expected to grow over time as real-world playtesting
surfaces more fields. That's expected and fine — it is not evidence that
Phase 7 regressed.

---

# Post-RC Real-Game Findings: Azure Federation

Manual Starsector smoke testing reopened part of the Phase 7 release gate.

**Status tracking lives in `BUGS.md`** (BUG-001 through BUG-005) as the single
ledger — this section is historical/derived, not authoritative. Current state
as of 2026-08-01: BUG-001, BUG-002, BUG-003, and BUG-004 (found afterward) are
all Resolved with regression tests; BUG-005 (the manual smoke-test re-run) is
the one remaining Open item.

## Required fixes before generated-overlay acceptance

- [x] Preserve Starsector-significant blank CSV sentinel rows during reinjection;
      do not serialize `,,,,,,` as `"",,,,,,`. *(BUG-001, resolved 2026-08-01.)*
- [x] Preserve fixture-backed `#` structural/comment rows in a
      Starsector-compatible representation; do not quote them into ordinary data
      records. *(BUG-001, same fix.)*
- [x] Add regression coverage proving structural rows do not create duplicate
      composite `(id,type)` identities. *(`StandardFileInjectorTest`,
      `LocalizationProjectServiceTest` fixture round trip.)*
- [x] Extend explicit localization coverage for observed player-visible Azure
      Federation fields, including relevant `ship_data.csv`, `weapon_data.csv`,
      and faction rank/role display fields. *(BUG-002, resolved 2026-08-01 for
      the three confirmed fields + faction patterns; also tracked as
      non-blocking in "Post-Release / Incremental Localization Coverage" above
      for any further fields.)*
- [ ] Repeat Azure Federation Starsector smoke test with an unmodified SSMT
      generated overlay and verify campaign start plus expected English UI text.
      **Still open — see BUG-005. This is the one remaining item in this section.**
- [x] Verify SQLite catalog create -> close -> reopen persistence and multi-mod
      survival across restarts. *(BUG-003, resolved 2026-08-01;
      `CatalogRestartResumeRegressionTest` and related `ssmt-tm` tests.)*
- [x] Preserve the explicit **Open Existing Database** workflow while repairing
      any underlying catalog initialization/reopen ambiguity. *(BUG-003 — a
      previously-undocumented first-launch catalog-creation bug was also found
      and fixed in the same pass.)*

### Revised release interpretation

The previous manual generated-overlay requirement is **not complete** merely
because Starsector recognizes the generated mod. Acceptance requires an
unmodified generated overlay to load without structural CSV repair and for
that to be re-verified in an actual manual smoke test (BUG-005) — passing
automated tests for BUG-001/002/003 is necessary but not sufficient on its
own. Full player-visible field coverage beyond the confirmed fields is not a
condition of this acceptance criterion — see "Post-Release / Incremental
Localization Coverage."
