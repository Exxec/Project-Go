# System Architecture

`Last updated: 2026-08-02 by Codex (ADR-046 deterministic provider routing)`

## Overview

The Starsector Mod Toolkit (SSMT) is a standalone, modular, pipeline-driven application for analyzing and localizing Starsector mods without modifying source mods or adding game-runtime hooks.

The core pipeline is:

```text
Source Mod
   Ã¢â€ â€œ
Scanner
   Ã¢â€ â€œ
Compatibility Boundary
   Ã¢â€ â€œ
Format Parser / Extractor
   Ã¢â€ â€œ
Normalization + Stable Identity
   Ã¢â€ â€œ
Portable Project
   Ã¢â€ â€œ
Translation Memory / Optional AI Drafts
   Ã¢â€ â€œ
Validation
   Ã¢â€ â€œ
Reinjection
   Ã¢â€ â€œ
Staged Patch Builder
   Ã¢â€ â€œ
Pristine Source Clone + Translated Clone
```

The compatibility boundary is deliberately narrow. It accepts documented ecosystem conventions while preserving strictness where ambiguity could corrupt translation identity or data.

---

## Module Boundaries

- `ssmt-core`
  - dependency-free shared records;
  - typed exceptions;
  - extractor/plugin contracts;
  - stable domain models.

- `ssmt-scanner`
  - mod discovery;
  - `mod_info.json` parsing;
  - dependency ordering;
  - metadata compatibility.

- `ssmt-extractor`
  - CSV extraction;
  - JSON-like extraction;
  - faction/variant extraction;
  - bytecode extraction via ASM;
  - input-compatibility decoding.

- `ssmt-tm`
  - SQLite translation memory;
  - deterministic fuzzy lookup;
  - transactional interchange.

- `ssmt-validation`
  - placeholder and protected-token validation;
  - structured findings.

- `ssmt-patcher`
  - format reinjection;
  - transactional pristine/translated clone publication;
  - deterministic fingerprints;
  - source/output overlap protection.

- `ssmt-ai`
  - optional provider-neutral AI draft generation;
  - no core dependency on AI.

- `ssmt-project`
  - portable project schema;
  - end-to-end orchestration.

- `ssmt-gui`
  - JavaFX 25 MVVM shell;
  - no extraction or persistence logic in views.

- `ssmt-cli`
  - command/process wiring;
  - headless workflow.

- `ssmt-auto`
  - drag-and-drop/headless orchestration;
  - sibling workspace and version state;
  - exact translation-memory reuse and missing-string exchange;
  - no writes beneath source mods.

---

# Compatibility Boundary

The compatibility layer is not a generic Ã¢â‚¬Å“lenient mode.Ã¢â‚¬Â

It exists to normalize known, observed Starsector ecosystem conventions into strict internal representations.

Examples currently supported from the Phase 7 corpus include:

- `#` comments in mod metadata;
- structured metadata version objects;
- optional CSV columns;
- harmless blank/sentinel CSV rows;
- ordered composite CSV identities;
- deterministic GB18030 fallback for legacy source CSV text;
- uppercase loose-JSON literals where they represent known accepted syntax;
- Unicode-safe paths when Java receives them intact.

Probable source mistakes remain errors.

Example:

```json
"autofire": Ture
```

is not normalized to `true`.

---

# Metadata Boundary

`ssmt-scanner` parses real-world `mod_info.json` variants while normalizing them into typed metadata.

Supported compatibility behavior must be fixture-backed.

Version metadata may be represented as:

```json
"version": "1.2.3"
```

or a structured object.

Structured source information should be retained long enough to normalize safely rather than being blindly stringified at the parser boundary.

Hash-comment support is limited to metadata compatibility and must not weaken unrelated JSON parsing.

---

# CSV Extraction Boundary

CSV extraction uses explicit schemas.

Each schema defines:

- ordered identity columns;
- localizable columns;
- which localizable columns are optional;
- compatibility behavior for blank/sentinel rows;
- source encoding policy when needed.

## Translation Provider Routing

ADR-046 composes existing services rather than moving their responsibilities
into provider plugins. Project orchestration owns author-localization reuse,
exact/fuzzy translation memory, terminology, context, validation, routing,
review, and persistence. Providers receive an immutable request and return only
a provenance-bearing draft.

The deterministic target order is author localization, approved exact TM,
explicitly accepted fuzzy TM, glossary/terminology, one selected local provider,
validation/routing, explicitly configured AI when mode and score allow, human
review, and SQLite TM. Numeric scores recommend routing only; they never express
quality or acceptance. Source-mod writes remain impossible through this path.

## Stable identities

An identity is an ordered tuple:

```text
[id]
```

or:

```text
[id, type]
```

A complete identity tuple must be unique.

Stable keys encode every identity component and the target localizable column.

Conceptual examples:

```text
csv:id=foo:name
csv:id=foo&type=bar:name
```

Each identity value is percent-encoded so separators cannot create collisions.

### Invariants

- row order does not participate in identity;
- duplicate complete identities are rejected;
- missing required identity components are rejected;
- optional non-identity columns may be absent only when the schema allows them;
- sentinel rows may be skipped only when they contain no localizable data and match an observed safe convention.

The current standard registry includes the usual Starsector CSVs such as:

- `data/strings/descriptions.csv`
- `data/weapons/weapon_data.csv`
- `data/hulls/ship_data.csv`

Mod-specific identity layouts must be explicit.

---

# Source Encoding Boundary

SSMT is UTF-8 internally.

Source files may contain legacy encodings.

The decoding policy is:

1. attempt strict UTF-8;
2. if the file fails and an explicitly supported compatibility path applies, use the deterministic legacy fallback;
3. normalize decoded text to Java Unicode strings;
4. preserve source bytes untouched;
5. emit UTF-8 project/interchange data;
6. record or report use of the legacy fallback.

Current Phase 7 compatibility includes GB18030 source fallback for observed legacy CSV content.

Unrestricted encoding guessing is prohibited.

---

# JSON-Like Extraction Boundary

JSON extraction remains conservative.

Stable keys use RFC 6901-style pointers.

Default eligibility:

- all textual leaves in standard `data/strings/strings.json`;
- verified root display-name fields in `.faction`;
- `/displayName` in `.variant`;
- explicitly opted-in custom paths through versioned schema catalogs.

Identifiers, asset paths, weapon IDs, enum-like configuration values, and structural strings are not automatically translated.

Loose syntax support is compatibility-scoped.

Known syntax variants may be accepted when fixture-backed. Probable misspellings or unknown literals must remain errors.

---

# Bytecode Safety Boundary

Bytecode extraction reads raw `.class` bytes with ASM.

It must never:

- load classes;
- define classes;
- call `Class.forName`;
- invoke constructors;
- run static initializers;
- reflectively execute mod code.

Candidates are taken from string-valued constants and `LDC` instructions.

Stable bytecode keys include class, method/field identity, and instruction-local identity.

Reinjection verifies exact original text before replacement.

---

# Translation Memory Boundary

`ssmt-tm` owns local durable translation memory.

Identity is based on:

- source text;
- source language;
- target language;
- context.

Fuzzy lookup is deterministic and context-bounded.

AI output is never stored as trusted truth merely because an AI produced it.

Translations expose typed provenance. Deterministic preference order is:

```text
HUMAN_EDITED
AUTHOR_LOCALIZATION
MANUAL_IMPORT
AI_TRANSLATED
FUZZY_MATCH
```

SQLite schema v2 stores provenance. Version-1 catalogs migrate in place with
`MANUAL_IMPORT` as the deterministic legacy value. Automatic upserts accept
equal or higher confidence and reject lower-confidence replacement.

The project layer conservatively detects the observed FSF `aEP/...` and
`aEP_En/...` parallel namespaces. Pairing requires the same relative suffix
and existing stable extraction key. Matched translated-namespace entries
become author translations of their source counterparts; unmatched and
ambiguous entries remain findings. No general `_En` convention is inferred.

---

# Validation Boundary

Validation compares source and translated text without rewriting either.

Protected constructs include:

- Java Formatter conversions;
- numeric brace arguments;
- Starsector `$token` markers.

Duplicates are significant.

Validation returns structured findings to CLI and GUI callers.

---

# Clone Publication Boundary

The clone publisher:

- never writes inside the source mod;
- rejects source/output overlap;
- stages a byte-preserving source backup and a complete translated clone;
- preserves source metadata and applies translations only in translated staging;
- publishes only after both stages succeed;
- fingerprints every source path/byte and translated artifact deterministically;
- detects source changes during staging and restores both prior outputs when
  either replacement fails.

Source bytes are treated as immutable input. Links and special files are
rejected rather than followed outside the declared source root.

---

# Unicode Path Boundary

Path correctness belongs to the process boundary.

The Java application must receive the exact path.

The extractor must not attempt to reconstruct a path after a launcher has replaced non-ASCII characters with `?`.

Known Windows behavior:

- JavaFX directory selection and in-JVM paths preserve Unicode;
- some PowerShell/native launcher combinations can corrupt non-ASCII arguments.

The launcher is therefore part of compatibility testing.

---

# AI Boundary

AI assistance is optional.

AI providers return drafts only.

Core extraction, validation, project handling, and patch building must work fully offline.

AI may suggest corrections to malformed source, but must never apply source fixes automatically.

---

# Portable Project Boundary

The portable project is versioned UTF-8 JSON.

It stores:

- source-mod identity;
- patch identity;
- stable extraction identity;
- source text;
- translated text;
- deterministic ordering.

Filesystem roots are command inputs, not persisted redirect targets.

Builds reject:

- stale source text;
- unsupported schema versions;
- blank translations;
- validation findings;
- duplicate identities;
- mixed source identities.

Project refresh re-extracts the selected source into a new in-memory candidate and
compares entries by normalized source path and stable key. Unchanged exact matches
preserve translations. Changed and moved entries may carry advisory suggestions,
but suggestions are never copied into translated text automatically. Refresh
reports classify unchanged, changed, added, removed, and conflicted entries in
deterministic order. Persistence uses a staged sibling document and replacement,
so a dry run cannot alter the project and a failed write cannot publish a partial
document.

Project schema v1 retains compatibility by treating `provenance` as optional.
Readers assign `MANUAL_IMPORT` to legacy entries; writers emit the typed value.

AI exchange schema v1 likewise remains readable. New exports add optional
file/content/internal-id/mod/provenance context, optional author localization,
strict blank/line-break policy flags, entry count, and a deterministic
identity-set SHA-256. Imports validate all present integrity fields,
placeholders, `$tokens`, provenance, and configured line breaks before making
project or catalog changes. Rejections expose structured diagnostic codes.

Terminology consistency auditing is read-only. It separately reports exact
duplicate-source conflicts and normalized-term conflicts in equivalent
file/field contexts; it never changes translations.

---

# Phase 7 Real-Mod Compatibility Principle

A real-mod corpus is not allowed to redefine correctness.

The purpose of the corpus is to reveal legitimate ecosystem formats and diagnostics.

For every new compatibility case:

```text
observe
  Ã¢â€ â€œ
isolate
  Ã¢â€ â€œ
fixture
  Ã¢â€ â€œ
failing regression
  Ã¢â€ â€œ
narrow compatibility rule
  Ã¢â€ â€œ
module tests
  Ã¢â€ â€œ
full corpus
  Ã¢â€ â€œ
source immutability verification
```

Real-mod acceptance should cover source safety, output validity, and in-game loading.
