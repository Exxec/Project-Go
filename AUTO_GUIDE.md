# Project Go Auto: Drag-and-Drop Workflow

`Last updated: 2026-08-02 by Codex (ADR-041 pristine/translated clone output)`

`Project Go Auto.exe` is the drag-and-drop Project Go helper. It runs the normal source-safe
project, translation-memory, refresh, validation, and clone pipeline without
the GUI.

## First run

1. Open the development bundle's `Project Go Auto` folder.
2. Drag a mod's `mod_info.json` onto `Project Go Auto.exe`.
3. Project Go creates a sibling workspace beside the source mod:

```text
Project Go - Original Mod Name
```

4. Open this generated file:

```text
Original Mod Name - words-to-translate.json
```

5. Give it to an online AI and ask it to follow the embedded instructions.
6. Save the returned complete JSON in the same workspace using exactly:

```text
Original Mod Name - words-translated.json
```

7. Drag the same `mod_info.json` onto `Project Go Auto.exe` again.

Project Go validates the response, updates the persistent catalog, updates the
project, and publishes the `<mod-id>.english` translated clone plus its
`<mod-id>.english-source-backup` pristine sibling when every nonblank source
string has a translation.

## Files it creates

```text
Project Go - Original Mod Name\
  Translation - Original Mod Name.ssmt.json
  Translated Name - Original Mod Name.ssmt.json
  Original Mod Name - words-to-translate.json
  Original Mod Name - words-translated.json
  project-go-state.json
```

All automated projects use the same persistent SQLite catalog:

```text
%LOCALAPPDATA%\Project Go\project-go-catalog.db
```

This is a normal Project Go translation-memory database and can be selected from
the GUI or supplied to the CLI. To use an already-established database, set
`SSMT_TRANSLATION_MEMORY` to its full path before starting `Project Go Auto.exe`.
The Java system property `-Dssmt.catalog=<path>` is also supported.

An older per-workspace `project-go-catalog.db` is copied into the shared
location if the shared database does not exist yet. The old file is retained
as a backup.

The translated-name project appears after a response supplies
`translatedModName`. Older project snapshots are retained rather than
destructively deleted.

The copies are inside the Project Go workspace:

```text
<mod-id>.english\
<mod-id>.english-source-backup\
```

No generated file is written inside the source mod. Keep the pristine backup
in the workspace; copy only the translated clone into Starsector's `mods`
directory and disable the original mod while using it.

## What happens on every drop

1. Read `mod_info.json`.
2. Create or open the saved project.
3. If the declared mod version changed, reconcile against the updated mod.
4. Apply an exact catalog translation only when the same source/language pair
   has one unambiguous translated value.
5. If the specifically named translated response changed, validate and import
   it into both the project and SQLite catalog.
6. Export only the remaining untranslated nonblank strings.
7. Build the pristine backup and translated clone when nothing remains.
8. Report `PATCH_UNCHANGED` when identical clone outputs already exist.

Fuzzy matches are not auto-applied. Conflicting exact catalog translations are
also left untranslated for the AI/reviewer to resolve.

## Headless command-line use

The same executable accepts a mod directory or `mod_info.json`:

```powershell
& ".\Project Go Auto\Project Go Auto.exe" `
  "C:\Games\Starsector\mods\ExampleMod\mod_info.json"
```

For a development JVM launch:

```powershell
.\gradlew.bat :ssmt-auto:run --args='"C:\Games\Starsector\mods\ExampleMod\mod_info.json"'
```

## Safety and recovery

- Keep `%LOCALAPPDATA%\Project Go\project-go-catalog.db`; it is the growing
  catalog shared by every automated mod project.
- Back up the catalog using the normal `ssmt-cli tm backup` command.
- Do not rename the two AI exchange files unless you also restore their
  expected names before the next run.
- If an AI response is incomplete, Project Go writes a new missing-strings export.
- Changed IDs, source strings, schema, or source-mod identity reject the whole
  response.
- Malformed source such as `Ture` remains rejected and is never repaired.
