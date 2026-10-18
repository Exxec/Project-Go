# Master Terminology & Domain Rules

`Last updated: 2026-08-02 by Codex (ADR-043 confidence-gated glossary semantics)`

## Overview
This document serves as the master glossary for Starsector-specific terminology. Because Starsector has highly specialized lore and mechanical concepts, standard translation engines (and AI models) frequently ruin the context by translating these terms literally.

**Rule for AI and Translators:** When extracting, translating, or validating text, the rules in this table override all standard grammatical or direct-translation logic. 

---

## The Glossary

| Domain Term | Translation Rule | Context / Meaning |
| :--- | :--- | :--- |
| **Vanilla** | Do NOT translate. Use original English. | Refers to the unmodded base game content. |
| **Hullmod** | Preserve capitalization; do NOT translate. | The ship modification system (e.g., Heavy Armor, Integrated Targeting Unit). |
| **D-mod** | Do NOT expand to "Defect". Use exact term. | Structural damage/flaw hullmods found on recovered broken ships. |
| **S-mod** | Do NOT expand to "Story". Use exact term. | Permanent, built-in hullmods installed via Story Points. |
| **Remnant** | Match vanilla game terminology perfectly. | The ancient, automated AI faction hostile to humanity. |
| **Flux** | Do NOT translate as generic "Flow" or "Energy". | The core combat resource governing ship heat, weapons, and shield capacity. |
| **Phase** | Domain specific; match vanilla localization. | The ship dimensional slipping/cloaking mechanic used by high-tech vessels. |
| **Story Point** | Match vanilla game terminology perfectly. | A rare meta-currency used for permanent upgrades or narrative choices. |
| **Sector** | Capitalize when referring to the Persean Sector. | The physical region of space where the game takes place. |
| **Tri-Tachyon** | Preserve exact spelling and hyphenation. | The high-tech megacorporation faction. |
| **Domain** | Capitalize; use exact term. | The ancient human empire that collapsed prior to the events of the game. |
| **CR** | Do NOT translate; abbreviates Combat Readiness. | Percentage stat determining if a ship can deploy or will suffer malfunctions. |

---

## AI Prompting Integration
When using an optional AI draft provider (Ollama, Gemini, or the OpenAI Responses API — the only adapters SSMT implements), the contents of this glossary must be automatically prepended to the system prompt to enforce domain accuracy on draft translations.

## Per-mod approved terms (automation plan G3.1)

Besides the fixed table above, each translation builds a glossary for the mod being translated (`GlossaryService`). Approved terms
come from: your approvals (the mod's `glossary.json` in the workspace and a global `global-glossary.json`; the mod's file wins),
names the mod itself spells in English in a parallel CSV row (same row id and column in another file), and library units with
exactly one human-approved translation (read only). Sources that disagree become a conflict proposal; units repeated three or
more times are only proposed. Approved terms that occur in the mod fill the glossary in the exchange document, so they reach the AI
request and the approval rules for `POLICY_APPROVED` (a translation that drops an approved term is not approved by rules).
Commands: `ssmt-cli translate glossary MOD --out PROPOSAL.json [--tm MEMORY] [--mod-glossary FILE] [--global-glossary FILE]` and
`translate glossary-approve FILE --term SOURCE=TARGET`; the normal workflow offers **Glossary terms...** for one-click approval.
The shareable files of `GLOSSARY_FORMAT.md` (used by Check Glossary, advisory) have a different format from these approval files.

## Offline Provider Lookup and Feedback

The executable offline chain treats unambiguous exact entries in the local
translation memory with `HUMAN_EDITED`, `AUTHOR_LOCALIZATION`, or
`MANUAL_IMPORT` provenance as approved glossary hits. It does not treat an
`AI_TRANSLATED` entry as approved. On a miss, Argos Translate is tried first;
ADR-043 schedules escalation of difficult or uncertain results through
TranslateLocally and then configured AI when uncertainty remains. A machine
result is written back as `HUMAN_EDITED` only after the caller explicitly
approves it.
