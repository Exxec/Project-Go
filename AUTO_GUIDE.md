# Project Go Auto: Drag-and-Drop Workflow

`Last updated: 2026-08-02 by Codex (ADR-041 pristine/translated clone output)`

`Project Go Auto.exe` is the simple drag-and-drop Project Go helper. Drop one
Starsector mod ZIP on it and it either makes your personal translated copy or
creates one clear file for an AI translation pass. It never edits the archive
or an unpacked original mod.

## First run

1. Open the development bundle's `Project Go Auto` folder.
2. Drag a mod ZIP onto `Project Go Auto.exe`. (An unpacked mod folder or its
   `mod_info.json` also works for development.)
3. Project Go creates a sibling workspace beside the ZIP:

```text
Project Go - Original Mod Name
```

4. Open this generated file:

```text
Original Mod Name - AI translation request.json
```

5. Give it to an online AI and ask it to follow the embedded instructions.
6. Save the returned complete JSON in the same workspace using exactly:

```text
Original Mod Name - AI translation library.json
```

7. Drag the same ZIP onto `Project Go Auto.exe` again.

Project Go validates the response, imports it into the persistent translation
library, updates the project, and publishes the `<mod-id>.english` translated clone plus its
`<mod-id>.english-source-backup` pristine sibling when every nonblank source
string has a translation.

## Files it creates

```text
Project Go - Original Mod Name\
  Translation - Original Mod Name.ssmt.json
  Translated Name - Original Mod Name.ssmt.json
  Original Mod Name - AI translation request.json
  Original Mod Name - AI translation library.json
  project-go-state.json
  archive-source-<content-hash>\
```

All automated projects look for and grow the same persistent SQLite **master
translation library**:

```text
%LOCALAPPDATA%\Project Go\project-go-catalog.db
```

This is a normal Project Go translation-memory database and can be selected from
the GUI or supplied to the CLI. Auto checks it first for safe exact matches;
each validated AI response is added to it, so later mods can reuse the growing
index. To use an already-established database as the master library, set
`SSMT_TRANSLATION_MEMORY` to its full path before starting `Project Go Auto.exe`.
The Java system property `-Dssmt.catalog=<path>` is also supported.

An older per-workspace `project-go-catalog.db` is copied into the master
location if the master library does not exist yet. The old file is retained
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

1. Safely unpack a ZIP (when dropped) and find its one `mod_info.json`.
2. Create or open the saved project.
3. If the declared mod version changed, reconcile against the updated mod.
4. Check the master translation library, applying an exact entry only when the same source/language pair
   has one unambiguous translated value.
5. If `AI translation library.json` changed, validate it and add it to both
   the project and the master SQLite library.
6. If the master library is missing or incomplete, export only the remaining nonblank
   strings in `AI translation request.json`; no patch is made yet.
7. Build the pristine backup and translated clone only when nothing remains.
8. Report `PATCH_UNCHANGED` when identical clone outputs already exist.

Fuzzy matches are not auto-applied. Conflicting exact catalog translations are
also left untranslated for the AI/reviewer to resolve.

## Headless command-line use

The same executable accepts a ZIP, mod directory, or `mod_info.json`:

```powershell
& ".\Project Go Auto\Project Go Auto.exe" `
  "C:\Downloads\ExampleMod.zip"
```

For a development JVM launch:

```powershell
.\gradlew.bat :ssmt-auto:run --args='"C:\Games\Starsector\mods\ExampleMod\mod_info.json"'
```

## Safety and recovery

- Keep `%LOCALAPPDATA%\Project Go\project-go-catalog.db`; it is the master
  library shared by every automated mod project.
- Back up the catalog using the normal `ssmt-cli tm backup` command.
- Do not rename the AI request or AI translation-library file unless you also
  restore their expected names before the next run.
- If the library is absent or incomplete, Project Go writes a new request with
  only the remaining strings.
- Changed IDs, source strings, schema, or source-mod identity reject the whole
  response.
- Malformed source such as `Ture` remains rejected and is never repaired.
- ZIP archives must contain exactly one `mod_info.json`. Entries that escape
  the workspace, archives with more than 10,000 entries, and archives that
  expand past 1 GB are rejected.
