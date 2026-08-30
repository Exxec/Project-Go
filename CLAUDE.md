# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Starsector Mod Toolkit (SSMT)

`Last updated: 2026-08-02 by Claude (reconciled against a large concurrent Codex session: DECISIONS.md now runs through ADR-052, version is 0.6.0, BUGS.md has BUG-010; module map, architecture pipeline, and Do-NOT list refreshed to match ADR-041 clone publication and ADR-042–052's offline/AI translation pipeline; BUG-009's Track A hedge code was removed as obsolete once ADR-041 shipped)`

This file gives Claude Code the same grounding as the SSMT Claude.ai Project.
Read this before writing any code in a new session.

## Do NOT (full detail in `AI_CONTRACT.md` §0 and `DECISIONS.md` ADR-045)

- Do NOT weaken duplicate composite-identity validation to fix a bug.
- Do NOT auto-correct a probable source typo (e.g. `Ture` → `true`) — report it, never fix it silently.
- Do NOT write inside, rename, or delete a file in a source mod — output is always the sibling pristine-backup + translated-clone pair (`ADR-041`).
- Do NOT add a blanket "translate all CSV/JSON columns" fallback — coverage is explicit, field by field.
- Do NOT auto-apply/auto-approve an AI draft, local-provider draft, or fuzzy translation-memory suggestion — every machine result is reviewable and requires an explicit human action to become `HUMAN_EDITED` (`ADR-043`–`ADR-050`).
- Do NOT contact a remote AI provider without explicit per-run consent and prior disclosure of its remote location, and never persist a secret/credential value in provider settings (only endpoint/model and an environment-variable name) (`ADR-049`/`ADR-050`).
- Do NOT download a model, require GPU, or install a driver on the user's behalf — CPU is always the default and a GPU failure falls back silently to CPU (`ADR-043`).
- Do NOT invent APIs, dependencies, or a fabricated environment variable/config value that a provider doesn't actually document supporting.
- Do NOT treat a glossary as an executable plugin — it is inert, versioned, bounded JSON data; checking it only emits warnings, never changes or approves a translation (`ADR-052`).
- Do NOT end a session without updating `SESSION.md` and `BUGS.md` per the checklist in `AI_CONTRACT.md` §13.

## Read order (per AI_CONTRACT.md §11)

Before generating code in a new session, read:

1. `PROJECT_MANIFEST.md`
2. `STANDARDS.md`
3. `WORKFLOW.md`
4. `AI_CONTRACT.md` — full hard-rules list and mandatory session-end checklist
5. `BUGS.md` — confirmed open bugs and the invariants that must not be bent to fix them
6. `SESSION.md` — current state only, rewritten each session
7. `REAL_MOD_COMPATIBILITY.md` — when working on Phase 7 or parsers

For architectural changes, also review `ARCHITECTURE.md`, `DECISIONS.md`
(currently ADR-001–052 — the ADR-042–052 range covers a large offline/AI
translation-pipeline body of work; skim at least ADR-041, ADR-045, and
ADR-050 before touching `ssmt-ai`/`ssmt-patcher`/translation-routing code,
since they carry the load-bearing invariants), `TEST_PLAN.md`, and `ROADMAP.md`.

Do not redo completed compatibility work unless a regression demonstrates it's incorrect.

**Source-of-truth rule (full table in `AI_CONTRACT.md` §11a):**
`ROADMAP.md` is authoritative for "what's implemented." `BUGS.md` is
authoritative for "what's broken." `DECISIONS.md` is append-only for
tradeoffs — never re-decide a settled ADR without a new entry, even if a
concurrent session's decision surprises you. Other files should link to
these, not restate them.

**Concurrent-session note:** this repository has repeatedly been edited by
more than one AI session in parallel (this file's own history includes a
"Codex" byline alongside Claude's). Before assuming a file you last touched
is still in the state you left it, re-read it — `DECISIONS.md`'s ADR count,
`gradle.properties`' version, and `SESSION.md`'s structure have all changed
underneath a session mid-task before. Don't silently overwrite unfamiliar
content; reconcile it, note the reconciliation in `SESSION.md`, and prefer
marking older material superseded (see ADR-040's status line for the
pattern) over deleting it.

---

## What SSMT is

An offline-first Java toolkit that scans, extracts, translates, validates, and
patches Starsector mods **without ever modifying the source mod's files**.
Every build publishes a pristine source-backup clone plus a separate
translated clone (`ADR-041`); the user enables the translated clone instead
of the original, never both at once.

**What it is NOT:**
- Not a mod-authoring framework
- Not a translation SaaS (AI is optional; core must work 100% offline)
- Not a live/runtime mod loader (never hooks into the game process)
- Not a file editor for source mods (Mod-Safe is absolute)

## Guiding principles (non-negotiable)

1. **Performance first** — never slow Starsector startup or cause stutters.
2. **Deterministic output** — identical inputs → bit-for-bit identical clones. No randomness in the core build engine.
3. **Offline first** — scanning, TM lookup, verification, and clone publication all work with zero network access; the default local translation chain (Argos/TranslateLocally) never requires one either.
4. **Mod-safe** — never edit/overwrite/delete original source mod files. Output is always the pristine-backup + translated-clone pair, external to the source.
5. **AI-assisted, not AI-dependent** — local providers (Argos Translate, TranslateLocally) and remote AI providers (Ollama/Gemini/OpenAI) are optional, reviewable draft helpers only; remote AI additionally requires explicit per-run consent.
6. **Plugin-based** — file handlers (csv, json, variant, faction, class bytecode, images) are decoupled plugins; new formats never require touching core.
7. **Test everything** — every module/parser/builder ships with tests before merge.
8. **Documentation is code** — doc updates land in the same commit as the code change.
9. **Respect creators** — respect mod authors' licenses/permissions for assets and translated text.
10. **Build once, maintain forever** — favor strong typing and explicit interfaces over shortcuts.

## Tech stack

Java 25 · Gradle (Kotlin DSL) · JavaFX 25 (MVVM) · Jackson · Apache Commons CSV
· OW2 ASM · SQLite/JDBC/HikariCP · SLF4J/Logback · JUnit 5/AssertJ/Mockito
· bundled local CLI adapters for Argos Translate and TranslateLocally (both
user-installed, invoked without a shell, never auto-downloaded)

Gradle wrapper uses **9.1.0** (first release with full Java 25 support). No system Gradle install needed.

## Module map

| Module | Responsibility |
|---|---|
| `ssmt-core` | Dependency-free domain models, typed exceptions, plugin contracts. Never depends on `ssmt-gui` or a specific extractor. |
| `ssmt-scanner` | Mod discovery, `mod_info.json` parsing, dependency ordering. |
| `ssmt-extractor` | CSV / JSON-like / faction / variant / bytecode (ASM, non-executing) extraction. |
| `ssmt-tm` | SQLite + HikariCP translation memory; deterministic fuzzy (Levenshtein) lookup; schema now at v3 with a companion generation-lineage table (`ADR-044`). |
| `ssmt-validation` | Placeholder/protected-token validation (`%s`, `{0}`, `$color`, etc). |
| `ssmt-patcher` | Non-destructive reinjection; transactional pristine-backup + translated-clone publication (`ADR-041`, rewritten from the old single-overlay model). |
| `ssmt-plugin-manager` | Bounded plugin JAR metadata inspection (no class loading) + worker-process activation. |
| `ssmt-ai` | Local offline translation chain (Argos Translate default, TranslateLocally alternate; glossary-first, then confidence-gated escalation), deterministic provider routing, a bounded final-AI adjudication coordinator, and a no-API manual browser-AI export/import bridge, plus the original optional Ollama/Gemini/OpenAI draft adapters. Every machine result stays a reviewable draft — see `ADR-042`–`ADR-050`. |
| `ssmt-ocr` | Optional Tesseract OCR + deterministic translated-image rendering (Java2D draw path, plus an AI-assisted region-regeneration export/import round trip). |
| `ssmt-project` | Portable versioned project schema + end-to-end orchestration; hosts the unified bounded project-translation workflow (`ADR-050`), source-bound checkpoint/resume (`ADR-051`), and the data-only glossary/report auditors (`ADR-052`). |
| `ssmt-gui` | JavaFX 25 MVVM shell — zero business logic in views. Includes a Project Info tab (active source/project/output/TM/schema/recovery paths) and an Open Sample Project entry point for first-time users. |
| `ssmt-cli` | Picocli headless entry point, including `translate-project` (unified workflow) and `offline-translate` (direct local-chain invocation). |
| `ssmt-auto` | Drag-and-drop/headless automation state machine. |

## Architecture (pipeline)

```
Source Mod → Scanner → Compatibility Boundary → Format Parser/Extractor
  → Normalization + Stable Identity → Portable Project
  → Deterministic Translation Routing (author localization → exact TM/glossary
      → accepted fuzzy → local chain [Argos → TranslateLocally] → bounded
      final-AI adjudication when explicitly permitted+configured) → Validation
  → Reinjection → Transactional Clone Publication → Pristine Backup + Translated Clone
```

Key boundaries to respect:
- **Compatibility boundary**: narrow — normalizes *observed* ecosystem conventions (e.g. `#` comment rows, structured metadata versions, optional CSV columns, GB18030 legacy fallback) into strict internal representations. It is not a generic lenient mode. Probable typos (e.g. `"autofire": Ture`) must stay rejected, never silently coerced.
- **CSV identity**: ordered tuple `[id]` or `[id, type]`, must be unique; row order never participates in identity; sentinel/blank rows may only be skipped when they match an observed safe convention **and preserve their Starsector-compatible representation on reinjection** (do not quote `,,,,,,` into `"",,,,,,`).
- **JSON-like extraction**: conservative by default (standard `strings.json` leaves, faction display name, `.variant` `/displayName`); anything else requires an explicit opted-in schema catalog.
- **Bytecode**: ASM reads raw bytes only — never loads/defines classes, never calls `Class.forName`, never runs static initializers or constructors.
- **Translation memory/generation provenance** (highest to lowest trust preference): `HUMAN_EDITED` > `AUTHOR_LOCALIZATION` > `MANUAL_IMPORT` > `AI_TRANSLATED` > `ARGOS_TRANSLATED`/`TRANSLATE_LOCALLY` > `FUZZY_MATCH`. Trust provenance is stored separately from generation lineage (provider, model, version, timestamp, review state) so an approved entry never loses its machine-generation history (`ADR-044`). AI/local output is never trusted just because it was produced by a provider — it is always a draft until an explicit human approval action.
- **Clone publication** (`ADR-041`, supersedes the old single-overlay model and `BUG-009`'s original hedge): never writes inside the source mod; publishes a byte-preserving pristine clone plus a translated clone with the same `mod_info.json`/id/dependencies/JARs as the source; stages both fully before replacing either; a failed build never exposes partial output; the user enables the translated clone instead of the original, never both.
- **Provider/consent boundary** (`ADR-046`/`ADR-049`/`ADR-050`): deterministic routing tries author localization, exact TM/glossary, and the local chain before ever considering AI; a remote provider requires explicit per-run consent and prior disclosure; `LOCAL_ONLY` mode never invokes AI at all; provider settings persist no secret values.
- **Glossary/report boundary** (`ADR-052`): a glossary is inert bounded JSON data, not a plugin — checking it only emits identity-bound warnings, never mutates a translation; translation-report export is read-only and never mutates the project.

## Current status (as of 2026-08-02)

- **Phases 1–10**: implementation complete. Version is `0.6.0`.
- **Release candidate**: blocked on manual acceptance testing and two open bugs, not general code — see below.

### Release blockers / open bugs (`BUGS.md`)
1. **BUG-005** — manual Starsector smoke test against Azure Federation, now
   using the ADR-041 translated-clone workflow (enable the translated clone,
   disable the original). Blocked on a human with a licensed Starsector install.
2. **BUG-009** — originally "does the generated overlay reliably win
   Starsector's undefined cross-mod CSV/JSON merge" (`ADR-040`). ADR-041
   removed the need to win that merge at all (translated clones replace the
   original rather than coexisting with it), so this is resolved
   *architecturally*; it stays open only pending the same human-executed
   live translated-clone smoke test as BUG-005 — the two should be run together.
3. **BUG-010** — user-facing failures don't consistently state recovery/
   unchanged-state and a next action; see the inventory in
   `DIAGNOSTIC_AUDIT.md`. Not a correctness bug — does not justify weakening
   transactional validation, protected-syntax checks, or exception chaining.
4. Manual interactive GUI acceptance test (`MANUAL_ACCEPTANCE.md`) not yet fully run.

BUG-001 through BUG-004 and BUG-006 through BUG-008 are resolved — see
`BUGS.md`'s Resolved section for detail; do not restate it here.

### Known limitations (by design, not bugs)
- Standard JSON eligibility stays conservative; mod-specific fields need explicit schema/plugin support.
- CSV extracted line numbers may be unavailable.
- Malformed source tokens (e.g. `Ture`) are never auto-corrected.
- Windows process isolation for plugins is not an OS security sandbox; `REQUIRED` sandbox mode is unavailable on Windows until a real external boundary exists.
- Some Windows PowerShell/native launchers can mangle non-ASCII CLI paths with `?` — JavaFX/in-JVM path selection is fine, the launcher layer is the risk.
- Author-localization auto-detection only recognizes the explicit `aEP` / `aEP_En` pairing — no general `_En` convention is inferred.
- Argos Translate/TranslateLocally are user-installed; SSMT never downloads a model or a binary on its own, and CPU is always the default (a GPU attempt falls back to CPU on any failure, never fails the job).

## Common commands

Authoritative full build gate (Windows) — compiles every module, runs all
JUnit tests, Checkstyle, SpotBugs, and builds CLI/GUI distributions:
```powershell
.\gradlew.bat build --offline --no-daemon --max-workers=1
```
Verified toolchain: Temurin 25.0.3, Gradle 9.1.0.
Gate: 130+ Gradle tasks (release-evidence, packaged-smoke, dev-bundle), `-Xlint:all -Werror`, Checkstyle, SpotBugs. `--max-workers=2` is safe and faster on this machine; the authoritative CI value is `1`.

Note: a `clean build` can hit transient Windows file locks; the normal non-destructive `build` gate is authoritative unless investigating the lock itself.

Test one module only (fast inner loop — skips Checkstyle/SpotBugs):
```powershell
.\gradlew.bat :ssmt-extractor:test --offline --no-daemon
```

Run a single test class or method (`--tests` accepts a class or `Class.method` pattern):
```powershell
.\gradlew.bat :ssmt-extractor:test --tests "com.ssmt.extractor.csv.CsvExtractorTest" --offline --no-daemon
.\gradlew.bat :ssmt-project:test --tests "com.ssmt.project.LocalizationProjectServiceTest.createsProjectWithExplicitCustomCsvSchema" --offline --no-daemon
```

Checkstyle/SpotBugs only, for one module:
```powershell
.\gradlew.bat :ssmt-tm:checkstyleMain :ssmt-tm:spotbugsMain --offline --no-daemon
```

Run the CLI or GUI locally for manual verification:
```powershell
.\gradlew.bat :ssmt-cli:installDist
.\ssmt-cli\build\install\ssmt-cli\bin\ssmt-cli.bat scan "C:\path\to\starsector\mods"
.\ssmt-cli\build\install\ssmt-cli\bin\ssmt-cli.bat translate-project "C:\work\translation.ssmt.json" --memory "C:\work\catalog.db"
.\ssmt-cli\build\install\ssmt-cli\bin\ssmt-cli.bat offline-translate --help

.\gradlew.bat :ssmt-gui:installDist
.\ssmt-gui\build\install\ssmt-gui\bin\ssmt-gui.bat
```

Cross-module dependency note: after changing a lower-level module (e.g.
`ssmt-extractor`), a dependent module's IDE view may show stale "method
undefined" errors until Gradle recompiles it — this is IDE staleness, not a
real error; run that module's own `:module:test` to confirm.

## Explicitly out of scope

Live in-game hooks, hot reload, automatic mod-repository publishing, automatic
acceptance of AI/local-provider drafts or fuzzy translations, silent source
repair, executing untrusted Windows plugins without a verified sandbox,
general-purpose mod authoring, non-Starsector game support, browser
automation for the manual AI-review bridge (it only opens the OS default
browser to a configured URL — never logs in, types, uploads, or scrapes).

## When picking up work

Prioritize, in order:
1. `BUGS.md`'s `## Open` section: as of 2026-08-02, three remain — BUG-005
   and BUG-009 (both need the same human-executed live translated-clone
   Starsector smoke test, blocked on a licensed install, not more code —
   run them together) and BUG-010 (plain-language diagnostic pass, tracked
   in `DIAGNOSTIC_AUDIT.md`, ordinary code work an AI session can pick up).
2. Manual acceptance/smoke tests (`MANUAL_ACCEPTANCE.md`) once BUG-005/BUG-009 close.
3. Everything else in `ROADMAP.md`'s "Non-blocking" / "Deferred" rows is explicitly lower priority — don't start there unless asked.

Always add a fixture-backed regression test for any real-mod compatibility fix; never loosen an existing invariant (duplicate identity checks, source immutability, strict-rejection-of-malformed-input, review-before-acceptance) to make a fix "pass."
