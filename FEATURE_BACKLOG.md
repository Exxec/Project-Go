# Feature Backlog

`Last updated: 2026-08-02 by Codex (ADR-043 confidence-gated chain scheduled; broader provider/UX ideas remain)`

## Purpose

This file holds recommended architecture changes and features that are **not
yet scheduled** — distinct from `ROADMAP.md`, which tracks active phase
commitments. Nothing here is authorized work; it becomes a roadmap item (and
an ADR, if architectural) only when explicitly picked up.

Current program stance: **Windows-first, intentionally.** Ironing out the
core program on one platform before opening to public Linux/macOS
testing/feedback is a deliberate staging decision, not a gap — see
`ROADMAP.md` Phase 9. Items below that mention cross-platform work assume
that staging has already progressed to the public-testing phase.

---

# Architecture

## A1. Split `ssmt-plugin-api` out of `ssmt-core` now, while the surface is small
`PROJECT_MANIFEST.md` already flags `ssmt-plugin-api` as a possible future
module (ADR-004 base contracts currently live in `ssmt-core`). Splitting it
out is cheaper now, before an external plugin ecosystem exists and before
compatibility guarantees are expected, than after.
- **Trigger to act:** before publicizing any plugin-authoring documentation.

## A2. Cross-platform overlay verification (Linux/macOS)
CI already builds on Windows/Linux, but the only real-game smoke test to date
was Windows-only. Encoding and path handling are exactly the class of bug
(BUG-001) that surfaced outside the JVM's own guarantees — worth deliberately
re-running the same class of smoke test on Linux/macOS once public testing
opens, rather than assuming Windows-verified behavior transfers.
- **Trigger to act:** when the project moves from Windows-only staging to
  public Linux/macOS testing (per current explicit staging plan).

## A3. Windows sandbox evaluation (formalize, don't just defer)
Currently "environment blocked" per ADR/ROADMAP. Worth a scoped
investigation (Job Objects / AppContainer) at some point, since plugin
execution already runs in a bounded worker — this closes an open question
rather than leaving it permanently deferred by default.
- **Trigger to act:** low urgency; consider once BUG-001/BUG-003 are closed
  and there's slack for exploratory work.

## A4. Multi-target-language support
Current architecture implies one source → one target per project. Supporting
simultaneous translation into multiple languages is a genuine architectural
decision, not a small feature — flagging now so it's considered deliberately
if it ever comes up, rather than retrofitted.
- **Trigger to act:** only if multi-language demand actually appears; write
  an ADR before implementing, don't organically grow into it.

---

# Features — directly address problems already hit in testing

## F3. Context screenshots on translation entries
Extends the existing image-localization editor concept: let a translator
attach a reference screenshot to an ambiguous string for context. Natural,
low-effort extension of infrastructure already built.
- **Priority:** low/nice-to-have.

---

# Features — localization workflow

## F4. XLIFF or gettext PO export/import
Opens translation work to non-technical community contributors who may
already know tools like Poedit or Crowdin, without them touching SSMT
directly. One-time bridge, not a core-engine change.
- **Priority:** medium — valuable if/when community translation contribution
  becomes a goal.

# Small QoL features

## F7. Safe-mode toggle (fully hide AI provider settings)
Mostly cosmetic, but reinforces "AI-assisted, not AI-dependent" at the UX
level, not just the architecture level.
- **Priority:** low.

## F8. Bug report template for external testers
Placeholder for when public Linux/macOS testing opens (see A2 above):
mod name, OS, Starsector version, SSMT version, steps to reproduce, relevant
log excerpt. Goal: external feedback should slot directly into `BUGS.md`
format without translation effort on the maintainer's part.
- **Trigger to act:** before public testing opens — not needed yet.

# Features — first-time usability

## F13. Plain-language pass on all user-facing diagnostic/error text
The "diagnose clearly, never silently repair" philosophy only pays off if
the diagnostic text itself is written for a translator/modder, not someone
who already knows the codebase. Review every user-facing validation/error
message (the `Ture` typo diagnostic, malformed CSV messages, etc.) for
plain-language clarity. Natural extension of `UX_REVIEW.md`'s naming
section, applied to error/diagnostic text instead of button labels — update
`UX_REVIEW.md`'s scope to cover this explicitly when this item is picked up.
- **Priority:** medium.

---

# Features — Plugin Ecosystem

Split into two categories with very different security implications. Category
A needs actual code execution and goes through the bounded worker JVM /
plugin-manager sandbox already built for this. Category B is pure data
(schema/glossary files) and requires no code execution or sandbox at all —
it's a direct extension of the opt-in JSON/CSV schema catalog mechanism from
ADR-035.

**Tie-in note:** A1 (splitting `ssmt-plugin-api` out of `ssmt-core`) should be
informed by these concrete ideas rather than designed in the abstract first —
worth revisiting this list when A1 is actually picked up, so the API shape
reflects what real plugins in Category A actually need to do.

## Category A: Executable plugins (require sandbox/worker-JVM machinery)

### F20. Additional format extractors as plugins
Support for Starsector data types not in the standard schema set (hullmods,
hull styles, wings, exotic campaign-layer files) without waiting for
evidence-gated `StandardCsvSchemas`/`StandardJsonSchemas` additions per
ADR-032's policy. Lets a power user or community member add format support
without an SSMT release.
- **Priority:** medium.

### F21. Additional offline/self-hosted translation provider plugins
Beyond the current Ollama/Gemini/OpenAI providers — e.g. DeepL, Azure
Translator, or a self-hosted LibreTranslate instance for a fully offline,
no-API-key path consistent with the offline-first principle.
- **Priority:** low/medium.
- **Partial graduation:** bundled Argos Translate and TranslateLocally CLI
  adapters are implemented by ADR-042; confidence-gated local-to-AI escalation
  and bounded optional Argos acceleration are scheduled by ADR-043.
  This item remains for other provider plugins and user-facing provider
  discovery/configuration.

### F26. Project-authored mod voice/style profile
Provide a small, explicit project document describing tone, formality, faction
voice, humor/profanity policy, capitalization, protected proper nouns, and a
few approved examples. ADR-043 may consume such a brief when supplied, but
automatically inferring one, editing it without user review, or building a
large style-management subsystem is not scheduled.
- **Priority:** medium — directly supports faithful AI adjudication without
  pretending that generic fluency captures a mod's spirit.

### F27. Additional routing-signal detectors
Potential ADR-046 signals include lore/dialogue/flavor classification,
mechanics complexity, glossary conflict, terminology inconsistency, remaining
source-language spans, and ambiguous proper nouns. Promote a detector only with
fixtures demonstrating useful precision; do not grow a speculative NLP system
or assign weights based on intuition alone.
- **Priority:** low/medium — useful only after the unified router is wired.

### F22. Local target-language spell/grammar checker plugin
Something like a LanguageTool integration checking the *translated* text for
awkward phrasing — offline, no network call. Complements the existing font
coverage checker (ADR-036, catches "can't render") with "can render, but
reads badly."
- **Priority:** low/nice-to-have.

### F23. Visual-context previewer plugin
Render a translated string inside a mockup of its actual in-game UI element
(tooltip box, health bar label, etc.) so a translator can sanity-check
length/wrapping before building. Genuinely hard to get right in general;
best suited to a plugin author with deep Starsector UI-layout knowledge
rather than a core-team guess.
- **Priority:** low — high uncertainty on implementation difficulty.

## Category B: Content-pack plugins (data only, no code execution, no sandbox needed)

### F24. Pre-built opt-in schema packs for popular real mods
Instead of every translator hand-authoring their own opt-in CSV/JSON schema
(ADR-035) per mod, publish a small catalog of "known mod → known schema"
files for popular real mods (e.g. Nexerelin). Turns a one-off technical task
into something anyone can download and point SSMT at directly.
- **Priority:** medium — directly leverages existing ADR-035 mechanism with
  no new engine code required.

# How items graduate out of this file

1. Pick an item.
2. If architectural (section "Architecture" above): write an ADR in
   `DECISIONS.md` first, then add to `ROADMAP.md` as a scheduled phase item.
3. If a feature (not architectural): add directly to `ROADMAP.md` under the
   relevant phase, or a new "Phase 11+" section if it doesn't fit an existing
   phase.
4. Remove the item from this file once it's scheduled — this file is
   pre-commitment ideation, not a permanent duplicate of the roadmap.
