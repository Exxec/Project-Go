# Project Go Auto: Drag-and-Drop Workflow

`Last updated: 2026-09-11 (internal workspace and single-copy output)`

`Project Go Auto.exe` is the simple drag-and-drop Project Go helper. Drop one
Starsector mod ZIP on it and it either makes your personal translated copy or
creates one clear file for an AI translation pass. It never edits the archive
or an unpacked original mod.

## First run

1. Open the development bundle's `Project Go Auto` folder.
2. Drag a mod ZIP onto `Project Go Auto.exe`. (An unpacked mod folder or its
   `mod_info.json` also works for development.)
3. Project Go keeps its project, extraction, state, and recovery files in its
   application-data folder. Beside the ZIP it creates only this handoff file:

```text
Original Mod Name - AI translation request.json
```

4. Give it to an online AI and ask it to follow the embedded instructions.
5. Save the returned complete JSON beside the ZIP. Any JSON filename works; this
   documented name remains the clearest choice:

```text
Original Mod Name - AI translation library.json
```

6. Drag the same ZIP onto `Project Go Auto.exe` again.

Project Go validates the response, imports it into the persistent translation
library, updates its internal project, and publishes one `<mod-id>.english`
translated copy beside the ZIP when every nonblank source string has a
translation. The original ZIP remains the pristine source.

## Files you see

```text
Original Mod.zip
Original Mod Name - AI translation request.json
Original Mod Name - AI translation library.json
<mod-id>.english\
```

The request and response are temporary handoff files. Project Go does not create
a visible project file, state file, extracted-archive folder, changes report, or
pristine-backup tree in the normal Auto flow.

Project Go-owned state lives under the operating system's application-data
location. On Windows this is:

```text
%LOCALAPPDATA%\Project Go\
  project-go-catalog.db
  projects\<project-name-and-source-hash>\
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

Auto can adopt a `project-go-catalog.db` found in its current internal workspace
when the master library does not yet exist. It does not search former sibling
`Project Go - ...` workspaces; legacy discovery and adoption remain roadmap
work, and old folders are left untouched.

No generated file is written inside the source mod. Move or copy only the
translated copy into Starsector's `mods` directory and disable the original mod
while using it. When the selected source is already a folder in `mods`, Auto
creates the translated copy beside it.

## What happens on every drop

1. Safely unpack a ZIP (when dropped) and find its one `mod_info.json`.
2. Create or open the saved project.
3. If the declared mod version changed, reconcile against the updated mod.
4. Check the master translation library, applying an exact entry only when the same source/language pair
   has one unambiguous translated value.
5. Find a new sibling JSON response by its embedded project identity and entry-set
   integrity (the documented name is checked first), then validate it and add it to both
   the project and the master SQLite library.
6. If the master library is missing or incomplete, export only the remaining nonblank
   strings in `AI translation request.json`; no patch is made yet.
7. Build one translated copy only when nothing remains.
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

- Keep `%LOCALAPPDATA%\Project Go`; it contains the master library and internal
  automated projects.
- Back up the catalog using the normal `ssmt-cli tm backup` command.
- Auto and the normal desktop Import action both accept a response under any JSON
  filename. Auto ignores request files and unrelated JSON by checking embedded
  project identity, entry-set integrity, protected source text, and completed
  translations. If multiple new matching responses are present, keep only the one
  you intend to import or give it the documented response filename.
- If the library is absent or incomplete, Project Go writes a new request with
  only the remaining strings.
- Changed IDs, source strings, schema, or source-mod identity reject the whole
  response.
- Malformed source such as `Ture` remains rejected and is never repaired.
- ZIP archives must contain exactly one `mod_info.json`. Entries that escape
  the workspace, archives with more than 10,000 entries, and archives that
  expand past 1 GB are rejected.
