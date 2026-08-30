# Project Go: A Conversational Quick Start

This guide follows the normal, safe path: start a project, translate and check
it, then make a personal copy for your own game. Project Go never edits the source
mod, and generated copies are for personal use only.

## First time

**You:** “I want to try Project Go without touching a real mod.”

**Project Go:** Choose **Try a Practice Project**, select any writable folder, and work
with the synthetic sample. It contains no Starsector or community-mod content.
Use it to practise editing a row, saving, and building before selecting a real
mod.

**You:** “I am ready to localize a mod I have permission to use.”

**Project Go:** Choose **Start New Translation**, select the mod folder containing
`mod_info.json`, confirm the translated-clone name, and let extraction finish.
Your original mod remains read-only. Project files, recovery snapshots, the
pristine backup, and the translated clone are created outside it.

## Translate and review

**You:** “Where do I type a translation?”

**Project Go:** In **Translation Editor**, double-click a cell in the Translation
column, type the new text, then press Enter. Use the search field and status
filter to focus on untranslated or invalid entries. Select a row to compare its
source and translation in the previews below the table.

**You:** “A suggestion looks useful.”

**Project Go:** Select the row, choose an entry from the suggestion list, then select
**Apply Suggestion**. Suggestions are never applied automatically. Save with
**Save** or `Ctrl+S` when ready.

**You:** “How do I finish a group of translations?”

**Project Go:** Select the rows, open **Selected Rows ▾**, then choose **Mark
Reviewed**. If you imported AI drafts, use the same menu to approve or reject
them only after checking the wording and preserved placeholders.

## Build safely

**You:** “My translations look ready. What now?”

**Project Go:** Select **Make My Personal Copy**. Project Go validates the project and
creates two outputs: a pristine source backup and a translated clone. Copy or
move only the translated copy into Starsector’s `mods` directory. Disable the
original mod before enabling the copy; never enable both at once. Keep the
generated copy for your own use and do not share it.

**You:** “The build says a translation is invalid.”

**Project Go:** Select the affected row and read the Validation column. Keep
placeholders, formatting tokens, and protected syntax compatible with the
source. Correct the translation, save, and build again. The failed build does
not modify the source mod or replace the last successful output.

## Optional tools without clutter

**You:** “Where did the advanced options go?”

**Project Go:** Core actions remain in the first toolbar. Open **More Actions ▾**
for four groups:

- **Project Tools**: refresh with translation-memory suggestions, translate
  blank entries, or resume a paused translation.
- **Catalog and Report Tools**: open or merge a translation-memory catalog,
  check a glossary, or export a report.
- **AI Exchange**: export/import a structured AI package.
- **Browser AI Review**: create and manage a manual browser-based AI review.

**You:** “Can Project Go send my mod text to an AI automatically?”

**Project Go:** No browser workflow uploads anything automatically. The optional API
workflow always asks for per-run consent before it sends routed text to a
configured provider. AI output is a draft, not an approval.

## When to use the other tabs

**You:** “Do I need every tab?”

**Project Go:** No. Most projects use only Translation Editor and Project Info.
Use the other tabs only for a specific task:

| Need | Tab |
| --- | --- |
| Check output folders, recovery, or progress | Project Info |
| Extract a mod-specific JSON field | JSON Schema Editor |
| Replace text embedded in an image | Image Localization |
| Configure an optional AI provider | AI Provider Settings |
| Inspect plugin metadata | Plugin Manager |
| Check characters against a Starsector font | Font Coverage |
| Read warnings and operation details | Diagnostics |

## Usability review and next recommendations

The Translation Editor now uses categorized arrow menus so routine work is
visible without crowding the minimum 900 px window. Before adding more top-level
controls, retain this progressive-disclosure pattern.

The next improvements worth validating with real users are:

1. Add a **Tools ▾** overflow for secondary tabs if another tab is introduced;
   keep Translation Editor and Project Info visible.
2. Add contextual empty-state links, such as “Create Project” and “Open
   Project,” when no project is loaded.
3. Offer a compact “Guided workflow” view for first-time users that presents
   only Create/Open, Translate, Review, and Build.
4. Test the menu grouping at 150% and 200% display scaling, with keyboard-only
   navigation and a screen reader, before treating it as a final accessibility
   solution.

For complete details, see [USER_GUIDE.md](USER_GUIDE.md) and
[ACCESSIBILITY.md](ACCESSIBILITY.md).
