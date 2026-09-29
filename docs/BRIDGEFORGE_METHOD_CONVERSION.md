# Converting Project Go to BridgeForge's translation method (proposed 2026-09-29)

Status: proposed, owner-requested. Project Go keeps its Java implementation, GUI, Auto mode and translation
memory. What changes is the method: how units are found, identified, applied and checked. That method follows
BridgeForge's `bridgeforge/translation.py`, which is the reference implementation. This refines the 2026-09-27
sister design ("Project Go ... owns its Java extractors"): Project Go still owns the Java code, but its
extraction and reinjection rules follow the shared method, proved by shared fixtures.

## Why

The goal is one translated mod: a working copy whose language is replaced in place. It is not a second overlay
mod next to the original. BridgeForge already does this for its revivals. Measured on the same input, BridgeForge
also finds text that Project Go skips.

Evidence, FlowerGod 1.1.9 working copy (`In operation/FlowerGod/working`, 2026-09-29):

| | BridgeForge `translate-export` | Project Go `ssmt extract` |
|---|---|---|
| Files with Chinese text found | 101 | 96 of those 101 |
| Entries | 1,296 (967 unique): jar 663, CSV 501, JSON 132 | 108 of BridgeForge's entries are in files it skipped |
| Skipped files that hold Chinese | none | `trapNames.csv` (72), `reports.csv` (21), `FG_shipblackList.csv` (7), `channels.json` (6), `mod_info.json` (2) |

Earlier observations (2026-09-13, BridgeForge memory):

- On Nightcross, Project Go missed 861 strings.
- Strict CSV/JSON parsing rejects files Starsector loads fine: rows wider or narrower than the header, and
  unquoted JSON tokens.
- JSON keys are skipped (`designTypeColors` in `settings.json`).
- `ssmt-cli.bat` turns non-ASCII paths into `?`.

New on 2026-09-29: the launcher also fails when `JAVA_HOME` contains a space ("'C:\Users\exxec\Documents\Starsector'
is not recognized"). The Windows short path works around it.

## The method to adopt

These are the elements of `bridgeforge/translation.py`. Each needs a Java equivalent with the same observable
behaviour.

1. **Lenient unit discovery, with no normalisation and no schema opt-in for text.**
   - CSV is read as raw cell spans, including padded or short rows, embedded newlines and quoted or bare cells.
   - JSON-like files (`.json .faction .ship .variant .wpn .proj .skin .system`) go through a path-tracking
     tokenizer that understands `#` and `//` comments, single quotes and barewords. Object keys are their own
     `json-key` units.
   - Loose Janino `.java` scripts outside `src/` contribute their string literals.
   - Loaded jars contribute the `CONSTANT_Utf8` entries that a `CONSTANT_String` references: literals only, never
     member or descriptor names.
   - Every unit that contains CJK text is exported. Project Go's schema catalogs stay as advisory metadata (column
     meaning, player visibility), not as a gate on what is exported.
2. **Exclusions:** `src/`, `src-decompiled*/`, `out/`, `build/`, `target/`, `reports/`, `scratch/`,
   `disabled_files/`, and VCS/IDE folders. BridgeForge added `src-decompiled*` on 2026-09-29 because a decompile
   duplicated 960 of the jar's strings.
3. **Stable ids and source hashes.**
   - Ids: `csv:<file>#<row key>~<occurrence>:<column>`, `json:<file>#<path>`, `json-key:...`,
     `java:<file>#<n>`, `jar:<jar>!<class>#<constant-pool index>`.
   - Each file's SHA-256 is recorded, and apply refuses a changed source.
4. **Placeholder rules.** Carried unchanged:
   - Java format specifiers: real conversions only, with no space flag, so "50% faster" is prose.
   - ASCII-only `$variables`, dotted only when a name follows (`$player.name`, not a sentence period).
   - The `\u0001` highlight marker.

   `%%` is not a placeholder. A full-width `％` becomes `%%` where the game runs `String.format`
   (`hull_mods.csv`; Nexerelin and SWP write it that way) and `%` elsewhere.
5. **Prefill before translation:**
   - zh/en translator records, such as Nightcross's `ai/en`.
   - An English copy of the same mod: CSV by row and column, JSON by path.
   - Jar constants by index when the class layouts align (at least 90% match).
6. **Span-exact apply.**
   - Only each unit's exact span is edited: CSV re-quoted by the original quoting, mUTF-8 constant-pool rewrite
     for classes.
   - Afterwards the result is re-verified: CSV row shape, JSON-like parse, class-file parse.
   - Two modes, choose one:
     - `--out DIR` writes a new copy.
     - `--in-place` writes into a working copy the user designates, giving one mod with its language replaced.

     The input mod itself is never edited, which is an existing Project Go rule that stays.
7. **Leftover check:** scan the result, including jar string constants, for untranslated CJK (`translate-check`).
8. **Translation memory:** Project Go's TM stays the store. BridgeForge's `translate-tm` already writes Project
   Go's TM schema.

## Interchange contract

Project Go reads and writes BridgeForge's translation document, schema version 1:

- `schema_version`, `mode`, `mod_id`, `source_language`, `target_language`, `instructions`, `file_hashes`,
  `unreadable`;
- `entries[]` with `id`, `file`, `kind`, `context`, `source`, `translation`;
- `glossary{source: translation}`.

A document exported by either tool imports into the other. Applying it produces the same bytes.

Conformance fixtures live in a versioned folder that both repositories copy: `fixtures/translation-conformance/`,
with a `VERSION` file. They start from BridgeForge's `tests/test_translation.py` cases, plus the FlowerGod and
Nightcross counts above. A change to the method lands in both tools with a fixture that fails first.

## Non-goals

- No Python in Project Go's distribution; the packaged GUI, Auto and CLI gates stay as they are.
- BridgeForge does not edit Project Go, and Project Go does not edit BridgeForge.
- No machine translation policy change: drafts stay unapproved until a person or agent approves them.

## Phases and exit gates

Detailed in `ROADMAP.md`, "P7 — adopt BridgeForge's translation method".

1. **Interchange first:** import and export schema v1, so work moves between the tools at once.
2. **Discovery parity:** zero units that BridgeForge finds and Project Go misses, on the fixture corpus.
3. **Span-exact apply and the in-place working-copy mode.**
4. **Jar constant-pool parity** and aligned-reference prefill.
5. **Placeholder and leftover-check parity.**
6. **Launcher path fixes:** a quoted `JAVA_HOME` and `APP_HOME`, and non-ASCII paths.
7. **Retire the duplicated paths:** once 2 to 5 pass, the schema-opt-in extraction becomes advisory.
