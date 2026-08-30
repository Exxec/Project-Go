# Starsector Mod Toolkit: Vision & Principles

## Mission Statement

To build the definitive, high-performance, standalone toolkit for analyzing, translating, validating, and maintaining Starsector mods—empowering players and creators to localize and modify game content seamlessly without ever altering original mod files or impacting game runtime performance.

---

## Problem Statement

The *Starsector* modding ecosystem relies on a diverse, fragmented web of data formats:
* `.csv` spreadsheets defining ships, weapons, and campaign entities.
* `.json`, `.variant`, and `.faction` files configuring AI behavior and fleet loadouts.
* Compiled Java `.class` bytecode containing hardcoded dialogue strings and UI text.

When players or localization teams attempt to translate or modify these mods, they face significant hurdles:
1. **Destructive Overwrites:** Directly editing source files breaks mod updates and risks corrupting original installations.
2. **Parsing Fragility:** A single missing comma in a `.json` file or an unescaped string in a `.csv` table can crash the entire game.
3. **Format Dispersion:** Text is scattered across raw tables, JSON trees, and compiled code, making full coverage tedious and error-prone.
4. **Maintenance Drift:** When a mod updates, manually reconciling changes between the old translation and new source files becomes a massive ordeal.

---

## The Vision Solution

The **Starsector Mod Toolkit (SSMT)** solves these challenges by acting as a non-destructive translation and maintenance layer. It treats target mods strictly as **read-only source inputs**, extracting text into a structured, unified environment, verifying formatting integrity, and compiling the output into a standalone **override patch mod** that loads natively alongside the original.

---

## Non-Negotiable Principles

Every feature, architecture decision, and code commit in this project must adhere to these eight core principles:

1. **Zero-Touch Source Policy**
   Original mod files are sacred. The toolkit must never write to, edit, rename, or delete files inside the source mod directory under any circumstances.

2. **Runtime Performance Is Paramount**
   The toolkit must run as an external utility. It should never introduce heavy runtime hooks, memory bloat, or micro-stutters to Starsector's core gameplay loop.

3. **Deterministic Output Engine**
   Given identical source inputs and translation memory states, the build engine must produce bit-for-bit identical patch outputs every single time.

4. **AI-Assisted, Not AI-Dependent**
   Optional AI integration (LLM translation, context analysis) serves strictly as a developer assistant. 100% of the extraction, parsing, local translation lookup, and patch-building pipeline must execute offline without AI dependencies.

5. **Offline-First Architecture**
   Core operations—scanning, parsing, local database queries, verification, and patch compiling—must work completely without an active internet connection.

6. **Plugin-Driven Extensibility**
   No file format logic is hardcoded into the core workflow engine. Support for new file extensions, custom script parsers, or foreign game structures must be added via decoupled plugins (`PLUGIN_API.md`).

7. **User Content Ownership**
   All extracted strings, translation memory databases, and generated patch mods belong entirely to the user.

8. **Respect for Mod Authors**
   The toolkit encourages respectful distribution practices by prioritizing non-destructive overlay patches that preserve original mod attribution and license compliance.

---

## Target Audience

* **Localization Teams & Translators:** Individuals or groups translating large-scale total overhaul mods into other languages.
* **Mod Developers:** Creators wanting to manage multi-language support or audit their own mods for missing string keys and broken references.
* **Players & Customizers:** Enthusiasts who want to tweak in-game text, descriptions, or names without breaking their mod installations.