# UX Review Checklist

`Last updated: 2026-08-02 by Codex (expanded scope for FEATURE_BACKLOG F13 diagnostic-language audit)`

## Purpose

A repeatable checklist for reviewing SSMT's GUI usability — button count,
grouping, and naming — without needing a screenshot/UI-automation tool.
Run this by asking Claude Code (or any AI session) to "run the UX_REVIEW.md
checklist against the GUI," instead of re-explaining the ask each time.

## Critical limitation — read this first, every time

**No environment used for this project has a way to actually see the
rendered JavaFX window.** Every finding produced by this checklist is
**structural inference** — reading layout code, counting controls, checking
container types, comparing label text against the method it calls — not a
real visual "does this look cluttered" judgment. This has worked well so far
(BUG-006's toolbar-overflow finding was confirmed correct through label-width
arithmetic alone), but every finding must be written up with this caveat
explicit, the same way BUG-006/BUG-007 were: **"confirmed by source read; not
independently verified via an actual click-through, since no UI-automation
tool is available in this environment."** Never let a structural finding be
written up as if it were a confirmed visual observation.

---

## Diagnostic-language audit

When reviewing FEATURE_BACKLOG F13, inventory every user-visible error,
warning, validation finding, and recovery message—not only button labels. Each
message should say, in plain language: what happened; which file, key, or
operation was affected; whether source/output changed; and what the user can do
next. Preserve stable diagnostic codes and technical detail where they help bug
reports, but do not require a translator to understand Java exceptions or SSMT
module names. Flag raw exception messages presented without actionable context.

---

## 1. Button/Control Count and Grouping

For each toolbar, tab, or dialog:

- **Count total interactive controls** (buttons, toggle buttons, menu items)
  in a single flat container (`HBox`/`VBox`/similar with no wrapping).
- **Threshold:** more than ~6-8 controls in one flat row is a signal worth
  flagging, not an automatic bug — some rows legitimately need more. Use
  judgment, but say the number explicitly in the finding.
- **Estimate preferred width** from label text lengths and compare against
  the documented minimum window size (check `setMinWidth`/`setMinHeight` in
  the application entry point). If preferred width plausibly exceeds the
  minimum, that's a real, checkable finding (this is exactly how BUG-006 was
  confirmed) — do the arithmetic, don't just eyeball it.
- **Group by workflow stage, not by feature area added-when.** The question
  isn't "were these added in the same session" but "does a user reach for
  these at the same point in their workflow." Suggested stages to check
  against for SSMT specifically:
  1. **Project lifecycle** — create, open, save, refresh/rebuild.
  2. **Editing/review** — search, filter, bulk actions, keyboard nav.
  3. **Translation memory / catalog** — open, compare, merge.
  4. **AI exchange** — export, import, provider settings.
  5. **Diagnostics** — font coverage, terminology audit, plugin discovery.
- **Propose specific reorganization**, not just "this is cluttered": which
  controls move together, whether they become a second row, a menu bar
  section, or a collapsed/expandable group. Name the actual buttons by their
  current label.
- **Do not propose hiding a control behind a menu with no keyboard/
  accessible-text path** — every control's existing `setAccessibleText` (or
  equivalent) must remain reachable after reorganization. This mirrors the
  invariant already written into BUG-006.

## 2. Naming Consistency

For every button, menu item, tab, and dialog title:

- **Read the label next to the action it actually performs** (the method/
  handler it calls) and judge: does the label say what happens, or does it
  require already knowing the feature to parse it?
- **Check verb-first vs. noun-first consistency** across the same
  container — e.g. "Create with JSON Schema" (verb-first) next to "Open
  Translation Memory" (verb-first) is consistent; a label that breaks the
  pattern (a bare noun, or object-first phrasing) stands out and is worth
  flagging even if individually fine.
- **Flag jargon that assumes SSMT-internal vocabulary** a first-time user
  wouldn't have (e.g. internal class/concept names leaking into UI text)
  versus plain-language equivalents.
- **Flag near-duplicate labels** in the same view that could be confused for
  each other (e.g. two buttons both starting "Export..." with only a subtle
  difference later in the label).
- **Propose specific renames**, not just "this is unclear" — give the exact
  current label and the exact proposed replacement.

## 3. Writing Up a Finding

Every finding from this checklist should be logged in `BUGS.md` if it's a
usability defect (following that file's existing format — found/symptom/
confirmed/proposed-direction/invariant-not-to-weaken), or in
`FEATURE_BACKLOG.md` if it's a nice-to-have improvement rather than something
actively wrong. Use the same discipline already established: confirm by
direct source read, state plainly what could and couldn't be verified without
a visual/UI-automation tool, and don't overclaim certainty about rendered
appearance.

---

## Review Log

Record each time this checklist is run, even if no new findings result —
an empty run still confirms the GUI was checked as of that date.

### 2026-08-02
First full run, against every tab (`editorTab`, `schemaEditorTab`,
`imageEditorTab`, `providerSettingsTab`, `pluginTab`, `fontCoverageTab`,
`logTab`) in `SsmtApplication.java`, requested directly by the user.

**Section 1 (button count/grouping):**
- `schemaEditorTab`, `providerSettingsTab`, `pluginTab`, `fontCoverageTab`,
  `logTab`: 0-4 controls per row, well under the 6-8 threshold, consistent
  workflow-stage grouping. No findings.
- `editorTab` (Translation Editor) toolbar: already the subject of
  BUG-006 (13 controls in one flat row); the fix landed this session as
  four grouped rows. Re-examining the *specific* grouping per this
  checklist's "group by workflow stage" rule (prompted by direct user
  pushback) found the first-pass grouping split a natural pair (Refresh
  Project / Refresh with TM) across rows and put "Mark Reviewed" in an
  AI-actions row instead of next to the search/filter controls it
  actually acts on. A refined grouping — including merging the three
  "Create…" variants into one `SplitMenuButton` — was proposed and is
  pending user confirmation before implementing (would revise the
  already-closed BUG-006 fix again).
- `imageEditorTab`: the "text" row (source-text field, Add Region, OCR
  language field, Auto-Detect Text, Render PNG — 6 controls) blends three
  sub-workflows. At the threshold, not over it — logged as `FEATURE_
  BACKLOG.md` F9 (nice-to-have), not a bug.

**Section 2 (naming consistency):** found three related issues, logged as
`BUGS.md` BUG-008: "Render PNG" vs. "Render Localized Image (AI)"
vocabulary mismatch for the same conceptual action; "Export for Online AI"
vs. "Export for Image AI" near-duplicate phrasing; "Refresh with TM"
abbreviating a term ("Translation Memory") spelled out fully on the
adjacent "Open Translation Memory" button in the same toolbar.

**Outcome:** BUG-008 (naming) and F9 (grouping polish) logged. The refined
Translation Editor toolbar grouping was approved and implemented the same
day (see `BUGS.md` BUG-006's 2026-08-02 update, `DECISIONS.md` ADR-038's
2026-08-02 update) — refresh/refresh-with-TM now share a row, "Mark
Reviewed" moved next to search/filter, and the three "Create…" buttons
were merged into one `SplitMenuButton`. BUG-008 was also fixed the same
day, on explicit user confirmation (see `DECISIONS.md` ADR-039); F9
remains open as a low-priority nice-to-have. All findings reconfirmed the
checklist's opening caveat: structural inference
from source only, no actual rendered-window verification was possible.
