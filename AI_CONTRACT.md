# AI Developer Contract

`Last updated: 2026-08-02 by Codex (ADR-043 confidence-gated escalation boundary)`

## Overview

This document defines the strict rules of engagement for any AI assistant or coding agent operating within the Starsector Mod Toolkit (SSMT) repository.

By working in this repository, the AI must follow these rules.

---

## 0. Quick Reference — Never Do These

Full detail and rationale for each is in the numbered section noted. If you
find yourself reasoning "it's fine to bend this once to make a test pass,"
that reasoning is the signal to stop and log a `BUGS.md` entry instead.

- Never weaken duplicate/composite identity validation to fix a bug (§9).
- Never silently repair a probable source typo (e.g. `Ture` → `true`) — report it, never fix it (§7).
- Never write inside, rename, or delete a file in a source mod (§6).
- Never invent APIs/dependencies without approval (§2).
- Never silently change a schema, interface, or project-file format (§3).
- Never weaken a parser or validation rule just to make a corpus/fixture pass (§4, §5).
- Never end a task without the documentation handoff in §13.

---

## 1. Code Preservation and Scope

- Never rewrite working modules unless the requested task requires it.
- Never silently optimize unrelated code.
- Never remove architectural comments, rationale comments, or public documentation without an explicit reason.
- Keep changes narrowly scoped to the current task.

---

## 2. API and Dependency Boundaries

- Do not invent APIs, methods, classes, or dependencies.
- Use Java 25 and only dependencies already declared by the repository unless a new dependency is explicitly approved.
- If a new third-party dependency is required, explain:
  - why it is needed;
  - what problem it solves;
  - the maintenance/security cost;
  - the standard-library or existing-dependency alternatives.

Do not implement against the new dependency until approval is given.

---

## 3. Schema and Interface Stability

- Never silently change JSON schemas, CSV contracts, public interfaces, project-file formats, or plugin contracts.
- Public compatibility changes must be documented before or alongside implementation.
- Breaking changes require explicit authorization and a migration plan.
- Stable keys must remain stable across harmless source reordering.

---

## 4. Test-First Rule

For every compatibility fix or feature:

1. Reproduce the behavior with a focused test or anonymized fixture.
2. Add the failing regression test first.
3. Implement the minimum safe change.
4. Run the affected module tests.
5. Run the full build gate before considering the task complete.

Do not weaken a parser merely to make a corpus pass.

---

## 5. Real-World Compatibility Rule

Every relaxation of validation must be justified by an observed real-world Starsector mod pattern.

For each compatibility exception:

1. Add an anonymized regression fixture.
2. Add an automated regression test.
3. Document the compatibility behavior.
4. Keep the exception scoped to the narrowest applicable parser, format, or boundary.
5. Preserve strict rejection where accepting malformed input could lose, merge, or corrupt translation data.

Compatibility behavior must never become “accept anything that looks close enough.”

---

## 6. Source Immutability Invariant

Source mods are read-only inputs.

Every integration or corpus test that touches a real mod must verify:

- source file count unchanged;
- SHA-256 hashes unchanged;
- modification timestamps unchanged;
- no files created inside the source mod;
- no files deleted;
- no files renamed.

A source-mod mutation is a release-blocking defect.

---

## 7. Malformed Source Policy

SSMT may tolerate documented ecosystem conventions, but it must not silently repair probable author mistakes in source files.

Example:

```json
"autofire": Ture
```

SSMT must not reinterpret `Ture` as `true`.

Preferred behavior:

- fail or skip at the narrowest safe boundary;
- report the exact file and offending token;
- optionally emit a human-readable diagnostic such as “possible typo: did you mean `true`?”;
- never edit the source mod automatically.

A future repair-assistant feature may generate a suggested fix or a user-owned repaired copy, but it must remain opt-in and non-destructive.

---

## 8. Encoding Rules

- SSMT internal text representation is Unicode.
- Project files, translation memory interchange, logs, generated metadata, and normal outputs are UTF-8.
- Source decoding is an input-compatibility concern.
- Strict UTF-8 is attempted first.
- A legacy encoding fallback may be used only when:
  - it is explicitly supported;
  - UTF-8 decoding fails or the file is positively identified as legacy input;
  - the fallback is deterministic;
  - the chosen encoding is reported.
- Do not use unrestricted charset guessing.
- Do not “repair” mojibake after the process boundary has already corrupted input.

---

## 9. CSV Identity Safety

CSV row identity may be single-column or composite.

- Composite identity must use an ordered list of explicit identity columns.
- All identity components participate in the stable key.
- Duplicate complete identities are errors.
- Reordering rows must not change stable keys.
- Missing identity components must not be silently merged.
- Optional display/localizable columns may be absent only when the schema explicitly allows them.

Never weaken duplicate protection to support a mod.

---

## 10. Unicode Path Rule

Unicode path corruption must be fixed at the launcher or process boundary.

- Java code must receive the original path.
- The extractor must not guess or reconstruct paths containing replacement characters.
- GUI/in-JVM file selection is considered authoritative when it preserves Unicode.
- Windows launcher tests belong in compatibility verification.

Temporary diagnostic exclusion of non-ASCII paths is permitted only during investigation and must never become acceptance behavior.

Offline machine-translation adapters follow the same trust boundary as remote
AI providers. A local Argos Translate or TranslateLocally result is a draft,
not an approval. Provider fallback may happen automatically, but persistence
to the approved glossary/translation memory requires an explicit user-accept
action and must record `HUMAN_EDITED` provenance.

Under ADR-043, confidence-gated escalation must remain explainable. Never label
an SSMT heuristic as a provider-supplied probability. Each provider translates
the original source; earlier candidates may be compared or supplied to an AI
adjudicator but must not silently become replacement source. The adjudicator
must receive escalation reasons and available project-authored tone/lore/style
context. `UNSAFE` output cannot be automatically approved, and no confidence
state bypasses explicit human acceptance.

Final-AI adjudication prompts must use the ADR-043 structured envelope with the
untouched source, identified local-machine draft, context, approved terms,
style brief when supplied, and escalation reasons. Put the authoritative
preservation/non-invention instruction after all mod-controlled data, explicitly
label that data untrusted, and request only translated text. Provider adapters
must pass a prepared envelope through unchanged rather than nesting it inside a
second generic translation prompt.

ADR-044 separates trust provenance from generation lineage. Never erase the
provider/model trail merely because a human approved or edited a result.
`HUMAN_EDITED` describes current trust; companion metadata may still identify
Argos, TranslateLocally, or AI generation, timestamp, refinement, and review
state. Unreviewed machine output must not acquire approved provenance.

ADR-045 pipeline prohibitions are absolute: no automatic human-edit overwrite;
no automatic draft acceptance; no source-mod writes; no protected-syntax or
line-break bypass; no AI call without explicit provider configuration; no GPU
requirement; no unconsented model download; no repeated exact request within
the bounded session cache; and no accepted-exact rerun unless requested. An
`UNSAFE` assessment must block approval until the candidate is corrected.

ADR-046 numeric values are routing heuristics only. Never call them confidence,
quality, correctness, or acceptance scores. Use stable observable reasons and
deterministic thresholds. A `5+` recommendation means “invoke AI if the user
mode permits and a provider was explicitly configured,” never “accept AI.”
Author/exact/accepted-fuzzy/glossary reuse precedes providers, and every
provider result remains a validated, provenance-bearing draft.

---

## 11. Architectural Alignment

Before generating code in a new session, read:

1. `PROJECT_MANIFEST.md`
2. `STANDARDS.md`
3. `WORKFLOW.md`
4. `AI_CONTRACT.md`
5. `BUGS.md` — confirmed open bugs and the invariants that must not be bent to fix them
6. `SESSION.md`
7. `REAL_MOD_COMPATIBILITY.md` when working on Phase 7 or parsers

For architectural changes, also review:

- `ARCHITECTURE.md`
- `DECISIONS.md`
- `TEST_PLAN.md`
- `ROADMAP.md`

---

## 11a. Source of Truth Per Topic

Docs drift when the same fact is restated in multiple places and only one
copy gets updated. Each topic below has exactly one authoritative file;
every other file should link to it rather than restate it.

| Topic | Source of truth | Other files should... |
|---|---|---|
| What's implemented / phase status | `ROADMAP.md` | Link, don't re-list features |
| Confirmed bugs and their constraints | `BUGS.md` | Link from `SESSION.md`, not restate |
| Prior architectural tradeoffs | `DECISIONS.md` (ADRs) | Never re-decide without a new entry |
| Current session state | `SESSION.md` | Overwritten each session, not appended |
| Architecture/module boundaries | `ARCHITECTURE.md` | — |
| Unscheduled feature/architecture ideas | `FEATURE_BACKLOG.md` | Items graduate into `ROADMAP.md`/`DECISIONS.md` when picked up, then get removed here |

If two files disagree, fix the non-authoritative one to link instead of
restate, and note the conflict in `SESSION.md` rather than guessing which
version is stale.

---

## 12. Review Requirements

Before marking work complete, review:

- determinism;
- source immutability;
- stable-key behavior;
- data-loss risk;
- path containment;
- character encoding;
- failure diagnostics;
- unnecessary allocation or I/O;
- backward compatibility.

---

## 13. Documentation Handoff

No session or major task ends without completing this checklist. An
incomplete handoff leaves the next AI or person without a trail to follow.

1. **Update `SESSION.md`** — overwrite, don't append — with:
   - current branch and its actual state (built? tested? committed?);
   - exactly what changed this session;
   - what's still broken, by cross-reference to `BUGS.md` entries (don't
     restate bug detail here — see §11a);
   - the exact next command or test to run to continue.
2. **Update `BUGS.md`** if any bug was found, partially fixed, or fully
   resolved — include the regression test that now guards against
   recurrence.
3. **Update `ROADMAP.md`** when milestone state changed.
4. **Add or amend an ADR in `DECISIONS.md`** when an architectural decision
   changed — never re-litigate a past decision without a new entry.
5. **Update `REAL_MOD_COMPATIBILITY.md`** for newly observed ecosystem
   behavior.
6. **Update `TEST_PLAN.md`** when regression coverage changed.
7. **Stamp every file you touched** with a `Last updated: <date> by
   <AI/session>` line near the top.
8. **Use a commit message that names the fixture or bug ID**, e.g.
   `fix: preserve CSV sentinel rows on reinjection (BUGS.md#BUG-001,
   regression: AzureFederation_structural_rows_test)` — so git history
   carries the trail even if `SESSION.md` is overwritten later.

Documentation must describe the implementation that actually exists, not the implementation that was originally planned.
# Manual browser review boundary

The no-API browser bridge is a local export/open/import workflow, not an AI
provider integration. It may launch only a user-configured HTTP(S) URL in the
default browser. It performs no login, page automation, upload, text entry,
download, credential storage, or scraping. Exported mod content leaves the
machine only through an explicit user action after a privacy notice. Imported
responses use the same fail-closed identity, completeness, syntax, line-break,
and provenance validation as other AI exchange files and remain drafts.

# Final-AI adjudication boundary

Final adjudication receives all retained local candidates and the deterministic
routing reasons. Local-only mode and absent provider configuration perform no
provider call. Remote providers additionally require disclosure consent before
receiving text. The returned value is structurally validated and is always an
unapproved draft; invalid output leaves the local uncertainty unresolved.
