# Starsector Mod Toolkit: Project Manifest

`Last updated: 2026-08-02 by Codex (ADR-041 pristine/translated clone publication)`

## Overview
The **Starsector Mod Toolkit (SSMT)** is a specialized, high-performance developer and player utility designed to analyze, extract, localize, translate, validate, and maintain Starsector mods. Its core mission is to empower players and mod developers to localize and modify content seamlessly without ever breaking original mod files or compromising game performance.

Per `AI_CONTRACT.md`, this file must be read (alongside `STANDARDS.md`) before any AI assistant generates code in a new session.

---

## Guiding Principles

1. **Performance First**
   Never slow down Starsector's startup times or cause runtime micro-stutters. The toolkit operates primarily outside the game runtime as a standalone desktop utility.
2. **Deterministic Output**
   Given identical source inputs, the toolkit must always generate bit-for-bit identical patch mods. Randomness and non-deterministic logic are strictly prohibited in the core build engine.
3. **Offline First**
   All core functions—including scanning, parsing, translation memory lookup, verification, and patch generation—must execute entirely offline without requiring an active internet connection.
4. **Mod-Safe**
   Never edit, overwrite, modify, or delete original source mod files. Local builds publish a pristine source clone and a separately staged translated clone; users enable the translated clone instead of the original.
5. **AI-Assisted, Not AI-Dependent**
   AI API integrations are strictly optional helper tools for draft translations or natural language analysis. The core parsing, extraction, and validation engine must be 100% functional without AI dependencies.
6. **Plugin-Based Architecture**
   All file handlers (`.csv`, `.json`, `.variant`, `.faction`, compiled `.class` bytecode, images) must implement clean, decoupled plugin interfaces. Extending support to new formats must never require modifying the core engine code.
7. **Test Everything**
   Every module, parser, and builder must ship with automated unit and integration tests before being merged into the primary branch.
8. **Documentation Is Code**
   Documentation is a first-class citizen. Code changes and their corresponding specification updates must occur within the same development cycle and commit.
9. **Respect Creators**
   Support personal use by default and strictly respect mod authors' licenses, permissions, and redistribution rules regarding assets and translated text.
10. **Build Once, Maintain Forever**
    Favor long-term maintainability, strong typing, explicit interfaces, and clear architecture over quick hacks, loose dynamic scripting, or obscure shortcuts.

---

## What SSMT Is Not
* Not a game modding framework or mod-authoring tool.
* Not a general-purpose translation SaaS — AI translation is optional and offline operation must remain 100% functional without it (Principle 5).
* Not a live/runtime mod loader — SSMT never hooks into or runs alongside the game process (Principle 1).
* Not a file editor for source mods — Mod-Safe is absolute (Principle 4, Risk CR-01).

## Module Map
Mirrors the Gradle multi-module layout established in `ROADMAP.md` "Phases 1–6" and reinforced in `STANDARDS.md` §2:

| Module | Responsibility |
| :--- | :--- |
| `ssmt-core` | Domain models (`ModInfo`, `ExtractedString`, `PluginContext`), shared contracts. No UI or extractor-specific logic. |
| `ssmt-scanner` | Directory discovery, `mod_info.json` parsing, dependency graph resolution. |
| `ssmt-extractor` | CSV / JSON / Faction / bytecode (`.class` via ASM) string extraction. |
| `ssmt-tm` | Translation Memory: SQLite + HikariCP, fuzzy matching (Levenshtein). |
| `ssmt-validation` | Format/variable-specifier verification (`%s`, `{0}`, `$color`). |
| `ssmt-patcher` | Transactionally publishes pristine and translated source clones. |
| `ssmt-plugin-manager` | Bounded plugin catalog and worker-process activation. |
| `ssmt-gui` | JavaFX 25 MVVM shell; zero business logic (`ARCHITECTURE.md`, `STANDARDS.md` §4). |
| `ssmt-project` | Portable project document and end-to-end orchestration. |
| `ssmt-ai` | Optional Ollama, Gemini, and OpenAI draft providers. |
| `ssmt-ocr` | Optional OCR regions and deterministic image localization. |
| `ssmt-cli` | Picocli entry point for the complete headless workflow. |
| `ssmt-auto` | Self-contained drag-and-drop automation state machine. |

`ssmt-core` must never depend on `ssmt-gui` or any specific extractor implementation.

## Tech Stack
(authoritative — see `STANDARDS.md` for full rules)

Java 25 · Gradle (Kotlin DSL) · JavaFX 25 (MVVM) · Jackson · Apache Commons CSV · OW2 ASM · SQLite/JDBC/HikariCP · SLF4J/Logback · JUnit 5/AssertJ/Mockito.

## Phase Gate Criteria
Per `ROADMAP.md`, a phase does not start until the previous phase is fully tested, documented, and functional. "Done" for a phase means:
* All checklist items in `ROADMAP.md` for that phase are implemented.
* Corresponding JUnit 5 tests exist per `TEST_PLAN.md`'s acceptance criteria for the modules touched.
* `SESSION.md` reflects the phase's completion, known bugs, and next steps.
* No open static-analysis warnings (`WORKFLOW.md` §4).

## Success Criteria (project-level)
* A real, community-distributed Starsector mod can be scanned, extracted, translated (manually or via optional AI), validated, and published as a working translated clone with zero modification to the source mod's files or timestamps.
* Re-running the pipeline on unchanged inputs produces byte-identical pristine and translated clones.
* An unrecognized file format is skipped with a logged warning, never a crash (Risk TR-04).

## Out of Scope (for now)
* Live in-game overlay or hot-reloading of translations.
* Automated mass-distribution/publishing of patches to mod repositories (Risk CR-03 requires explicit user acknowledgment before public export — not automation of it).
* Non-Starsector games.
