# Project Go Beginner Guide

`Last updated: 2026-09-11 (three-step workflow)`

Project Go makes a private translated copy of a Starsector mod. It does not
change the original mod, and generated copies should not be shared without the
mod author's permission.

## The three steps

### 1. Choose

1. Start `Project Go.exe` from the extracted Windows development bundle.
2. Open **Translate a Mod**.
3. Select **Choose a Mod** and choose the folder that directly contains
   `mod_info.json`.

Project Go reads that folder and saves progress in its own internal workspace.
You do not need to create or name a project file, database, report, or backup.
The source folder remains unchanged.

### 2. Translate

If Project Go already has complete saved translations, it proceeds directly to
the final step. Otherwise:

1. Select **Save AI Translation Request**.
2. Give the resulting JSON file to your AI translator.
3. Ask it to follow the instructions inside and return the complete JSON only.
4. Select **Open AI Translation Response** and choose the returned JSON file.

The response filename does not matter. Project Go checks its embedded mod
identity, source text, entry IDs, formatting tokens, and syntax before saving
anything. A rejected response leaves previous progress untouched.

If entries are still blank, Project Go returns to the translation step and can
create another request containing the remaining work.

### 3. Install

1. Select **Create Translated Mod**.
2. Choose the destination parent, normally Starsector's `mods` directory.
3. Use the one generated `<mod-id>-translated` folder.
4. Disable the original mod and enable only the translated copy.
5. Launch Starsector and test the mod in the game.

A successful normal build creates one translated folder. It does not create a
permanent source-backup sibling or put `Project Go Changes.csv` in that folder.
Temporary rollback data is internal and is removed after successful publication.
After the first successful build, Project Go remembers that validated destination
and opens the chooser there next time. If the folder is moved or removed, it asks
again instead of using a stale path.

## Files you need to understand

Normal use involves at most four visible items:

| Item | Purpose |
|---|---|
| Original mod folder or ZIP | Read-only source owned by you |
| AI request JSON | Temporary handoff to an external translator |
| AI response JSON | Returned handoff; imported read-only |
| Translated mod folder | The one result to enable in Starsector |

Project state, history, reports, extraction data, recovery data, and the shared
translation library are application-owned details. Keep the Project Go
application-data folder if you want saved work and translation reuse to persist.

## Project Go Auto

`Project Go Auto.exe` accepts a dropped mod ZIP, directory, or `mod_info.json`.
For a ZIP it creates a clearly named AI request beside the ZIP. Save the completed
response beside the same input; its JSON filename does not matter. Dropping the
input again identifies the response by its embedded project identity and creates
one `<mod-id>.english` folder when the translation is complete.

Auto keeps its project and extracted ZIP internally. See [AUTO_GUIDE.md](AUTO_GUIDE.md)
for the exact handoff filename and safety limits.

## Common problems

### Project Go cannot find `mod_info.json`

Choose the actual mod folder, not the folder above or below it. The selected
folder must directly contain `mod_info.json`.

### The AI response is rejected

The translator may have added commentary, removed entries, changed source text
or IDs, or damaged tokens such as `%s`, `$variable`, markup, escapes, or line
breaks. Ask it to return the complete JSON object without code fences.

### The translated copy does not appear

Every nonblank source entry must have a valid translation. Import another
response for the remaining entries. Existing valid work is retained.

### Text displays as `?` in the game

The active Starsector font may lack a required glyph. The Advanced editor has a
font-coverage check for `.fnt` files.

### Windows warns about the application

The development build is unsigned, so SmartScreen may warn. Verify the bundle
using its adjacent SHA-256 file before opening it.

## Advanced tools

Advanced mode is for maintainers who intentionally need portable `.ssmt.json`
projects, explicit translation-memory databases, custom JSON/CSV schemas,
reports, restore points, provider settings, or scripted CLI operations. Advanced
project builds retain the explicit pristine backup and changes report.

The CLI distribution requires JDK 25. Build it and inspect its commands with:

```powershell
.\gradlew.bat :ssmt-cli:installDist
.\ssmt-cli\build\install\ssmt-cli\bin\ssmt-cli.bat --help
```

See [USER_GUIDE.md](USER_GUIDE.md) for the full Advanced interface and CLI
command reference. See [docs/ROADMAP.md](docs/ROADMAP.md) for known limitations,
including legacy adoption, shared internal roots, and live-game validation.

## Safe routine

1. Keep the original mod or archive unchanged.
2. Translate through the normal three-step workflow.
3. Create one translated copy.
4. Disable the original before enabling the copy.
5. Test menus, descriptions, campaign behavior, combat, and save/reload where
   relevant; a successful build is not proof of in-game compatibility.
6. Keep generated copies private unless redistribution permission is explicit.
