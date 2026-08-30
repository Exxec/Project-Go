# Real-Mod Compatibility

> Historical evidence: this report records results from a proprietary local
> test corpus that is intentionally excluded from this repository. It does not
> grant redistribution rights or provide bundled reproduction inputs.

`Last updated: 2026-08-01 by Claude (Azure Federation findings statuses updated to Fixed; see BUGS.md)`

## Purpose

This file records real Starsector ecosystem behaviors discovered during Phase 7.

It is not a list of hacks.

Each compatibility behavior listed here must have:

- an observed real-world source;
- an anonymized regression fixture;
- an automated test;
- a narrow implementation boundary.

---

# Compatibility Policy

SSMT distinguishes three categories:

## 1. Supported Ecosystem Convention

A format differs from the clean/default expectation but is consistently interpretable and safe.

Examples:

- metadata `#` comments;
- structured version objects;
- harmless CSV sentinel rows;
- composite CSV identity;
- legacy source encoding.

These may be normalized.

## 2. Unsupported but Valid Custom Content

The file may be valid for the mod but SSMT does not yet know which fields are player-visible.

Example:

- custom JSON fields without an opt-in schema.

These should be reported, skipped safely, or supported through schema/plugin mechanisms.

## 3. Malformed / Ambiguous Source

The source contains a probable mistake or unknown syntax.

Example:

```json
"autofire": Ture
```

SSMT must not guess.

It may report a likely correction but must not silently reinterpret or edit source.

---

# Current Corpus Snapshot

Date: 2026-07-26

Supplied samples: 11

Extraction result:

```text
10 passed
1 rejected as malformed source
```

Final source immutability verification:

```text
17,237 source files
0 hash changes
0 timestamp changes
0 source modifications
```

---

# Observed Compatibility Cases

## Metadata hash comments

Observed behavior:

```text
# comment
```

inside mod metadata.

Policy:

- accept only in the metadata compatibility parser;
- do not globally weaken JSON handling.

Status: Supported.

---

## Structured version objects

Observed metadata version values are not always strings.

Policy:

- accept supported structured form;
- normalize to typed internal version representation;
- do not discard structure prematurely.

Status: Supported.

---

## Optional CSV columns

Some real CSVs omit auxiliary/localizable columns expected by a strict standard schema.

Policy:

- optionality must be explicit in the schema;
- identity columns remain required;
- missing optional display columns do not invalidate unrelated rows.

Status: Supported where schema-declared.

---

## Blank / sentinel CSV rows

Some files contain blank or sentinel rows that carry no translatable data.

Policy:

- skip only when they match a safe fixture-backed rule;
- never skip a row containing localizable data;
- never use sentinel tolerance to hide identity collisions.

Status: Supported.

---

## Composite CSV identity

Observed case:

```text
(id, type)
```

is unique while `id` alone is not.

Policy:

- identity is an ordered tuple;
- all components participate in the stable key;
- duplicate complete tuples remain errors;
- row order does not participate in identity.

Status: Supported.

---

## Legacy CSV encoding

Some source CSV content is not valid UTF-8.

Current observed fallback:

```text
GB18030
```

Policy:

- strict UTF-8 first;
- deterministic explicit fallback only;
- internal/project/output text remains UTF-8;
- source bytes remain untouched.

Status: Supported for observed case.

---

## Uppercase loose-JSON literals

Some real mods use JSON-like syntax beyond strict JSON.

Policy:

- support only known fixture-backed literals that are semantically unambiguous;
- do not generalize arbitrary identifier tokens.

Status: Supported narrowly.

---

## Probable typo literal: `Ture`

Observed source:

```json
"autofire": Ture
```

Policy:

- reject;
- do not reinterpret as `true`;
- diagnostics may suggest “possible typo: true”;
- never edit source.

Status: Intentionally unsupported as malformed input.

---

## Unicode Windows paths

Observed behavior:

- JavaFX/in-JVM paths preserve Unicode.
- Some PowerShell/native/batch launcher paths can corrupt non-ASCII characters before Java receives them.

Policy:

- fix at launcher/process boundary;
- never repair inside extractor;
- final acceptance must include non-ASCII paths.

Status: Core-safe; launcher compatibility remains an environment concern.

---

## FSF parallel author-localization namespaces

Observed behavior:

```text
aEP/...
aEP_En/...
```

The FSF localization test found 229 source strings with corresponding
author-provided English content. Proprietary text is not committed; regression
fixtures reproduce only the anonymized directory/key structure.

FSF `data/hulls/ship_data.csv` also contains an intentionally blank auxiliary
header. Extraction and reinjection both preserve that header position and its
cell data; patch building must not reject or shift the column.

Policy:

- recognize only the explicit `aEP` and `aEP_En` roots for this first case;
- require an identical relative suffix and stable extraction key;
- never infer that arbitrary `_En` directories are translations;
- prefer author localization over manual/AI/fuzzy imports, but never over an
  existing human edit;
- report unmatched or ambiguous entries;
- do not modify either namespace or change extraction keys.

Status: Supported conservatively.

---

# Corpus Acceptance Rules

For every corpus run collect:

- mod/sample identifier;
- exit code;
- extracted string count;
- unsupported-file count;
- warnings/errors;
- source file count;
- source hash changes;
- source timestamp changes;
- encoding fallbacks;
- compatibility rules exercised.

Do not store proprietary source content in committed reports unless permission allows it.

---

# Future Diagnostics

Recommended future feature:

## Source Health Diagnostic

Read-only diagnostics may identify likely source mistakes such as:

- typo-like booleans;
- malformed commas;
- duplicate IDs;
- broken asset paths;
- encoding anomalies.

The diagnostic must distinguish:

```text
compatibility normalization
```

from:

```text
suggested source repair
```

Repair suggestions are advisory only.

Any future “apply fix” action must:

- require explicit user action;
- write to a user-owned copy or generated patch;
- never mutate the original mod.
---

# Live Overlay Finding: Azure Federation

## CSV output representation is semantically significant

Observed during the first manual Starsector load of a generated Azure Federation
translation overlay.

Source structural rows included:

```csv
,,,,,,
#ships,,,,,,
```

SSMT output changed those representations to:

```csv
"",,,,,,
"#ships",,,,,,
```

Starsector treated the quoted-empty rows as real records. With multiple such
rows, loading aborted on a duplicate composite identity equivalent to:

```text
["" | ""]
```

Restoring the blank sentinel and `#` row representation manually allowed the
game to load and a campaign to start.

Policy:

- structural/sentinel/comment rows with no localizable data must not acquire
  synthetic identities during reinjection;
- preserve their original raw representation where feasible;
- at minimum, fixture-backed blank sentinel and `#` structural rows must be
  emitted in a Starsector-compatible form;
- do not solve this by weakening duplicate complete-tuple protection.

Status: **Fixed 2026-08-01** — see `BUGS.md` BUG-001 (Resolved) and
`DECISIONS.md` ADR-031. The manual Starsector re-load to confirm this in-game
is still outstanding (`BUGS.md` BUG-005).

---

## Player-visible fields outside current explicit schemas

The repaired Azure overlay loaded, but some source-language text remained and
rendered as `???` when the active Starsector font lacked those glyphs.

Observed examples include player-visible content associated with:

- `ship_data.csv` manufacturer/tech text;
- `weapon_data.csv` manufacturer, weapon role/category, and supplemental
  tooltip/description text;
- faction rank/role display names.

Already-supported fields in the same overlay contained valid English, including
ship names and normal `descriptions.csv` prose. The issue is therefore
incomplete explicit extraction/localization coverage rather than global output
encoding corruption.

Policy:

- add only confirmed player-visible fields;
- require anonymized fixtures and automated tests;
- preserve identity and non-localizable mechanics columns;
- do not infer that every textual field is safe to translate;
- unsupported custom fields remain reportable/skippable until explicitly
  supported.

Status: **Fixed 2026-08-01 for the three directly-confirmed fields
(`tech/manufacturer`, `primaryRoleStr`, `customAncillaryHL`) plus faction
rank/post/fleet-type names** — see `BUGS.md` BUG-002 (Resolved) and
`DECISIONS.md` ADR-032. Remaining vanilla weapon tooltip columns are
deliberately deferred until directly observed. The manual Starsector re-load
to confirm this in-game is still outstanding (`BUGS.md` BUG-005).

**2026-08-02 update — new field confirmed by the actual BUG-005 live smoke
test:** running the manual translated-clone workflow against Azure Federation
found one more player-visible field rendering as `????`: a ship system's
display name (the short label shown in the ship loadout UI), sourced from
`data/shipsystems/ship_systems.csv`'s `name` column — a real, separate
Starsector CSV file that had no entry in `StandardCsvSchemas` at all (not
merely a missing optional column on an already-covered file, like the three
above). Confirmed against the fixture: the file has 6 rows, each with a
Chinese display name in `name` keyed by a unique `id` (no `type` column,
matching `ship_data.csv`'s single-column identity shape). Every other
observed field in the same live test was correctly translated. Fixed the
same evidence-gated way as BUG-002: added `data/shipsystems/ship_systems.csv`
→ identity `id`, required column `name`, to `StandardCsvSchemas` — no other
column in that file was ever observed player-visible, so none else was
added. See `BUGS.md` BUG-011 (Resolved) and `DECISIONS.md` ADR-032 (same
evidence-bar policy, no new ADR needed for a routine application of it).

**2026-08-02 follow-up — user requested a systematic check for more of the
same class of gap** ("check if anything includes extra sections just like
this," then "add a check... so even if nothing was discovered here then
it'll still get pulled for translation"). Two things came out of this:

1. A direct evidence-based sweep across every mod in `Test mods/` found
   three more entirely-uncovered standard files with confirmed real content:
   `data/hullmods/hull_mods.csv` (`name`/`tech/manufacturer`/`desc`/`short`),
   `data/characters/skills/skill_data.csv` (`name`/`description`/`author`),
   `data/hulls/wing_data.csv` (`role desc`). All three fixed the same way as
   `ship_systems.csv`.
2. A new read-only `CoverageGapAuditor` (`ssmt-extractor`, wired into `ssmt-cli
   extract`) that flags any `.csv` file with no `StandardCsvSchemas` entry
   containing likely non-English text. Running it found five more:
   `data/campaign/commodities.csv`, `industries.csv`, `market_conditions.csv`,
   `special_items.csv`, `submarkets.csv` — also fixed. It deliberately does
   **not** auto-include flagged content into translation; per ADR-032 that
   still requires a human (or a future evidence-gated session) to confirm
   each field is genuinely player-visible before it becomes a schema entry.
   Re-running the check after all nine fixes lands is silent on all nine
   files across every mod. Remaining flagged files (`rules.csv` and its
   dialogue-tree siblings, several `_ENG`/`_EN`-suffixed files that
   mysteriously still contain Chinese text, and a handful of smaller
   mod-specific config files) are intentionally left for a future pass — see
   `BUGS.md` BUG-011 for the full list and reasoning.

---

## SQLite catalog reopen/resume safety

Manual use identified ambiguity between creating/selecting a catalog location
and resuming an existing SQLite catalog. An explicit **Open Existing Database**
action was added as a safe user workflow.

Policy:

- keep explicit open-existing behavior;
- opening a valid existing catalog must never destructively reinitialize it;
- database creation must be distinct from database opening;
- invalid/incompatible existing databases must fail visibly;
- catalog/project/TM data must survive restart and multi-mod use.

Status: **Fixed 2026-08-01** — see `BUGS.md` BUG-003 (Resolved) and
`DECISIONS.md` ADR-033. Lifecycle regression coverage now exists
(`CatalogRestartResumeRegressionTest` and related `ssmt-tm` tests); a
previously undocumented first-launch catalog-creation bug was also found and
fixed in the same pass.
