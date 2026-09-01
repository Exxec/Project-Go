# Project Go Beginner Guide

This tool makes a private translated copy for your own game. It never changes
the original mod, and generated copies should not be shared.

`Last updated: 2026-08-02 by Codex (ADR-041 clone workflow)`

This guide assumes no command-line or localization experience. The shorter
reference is `USER_GUIDE.md`.

## 1. The five important things

Think of Project Go as keeping five boxes:

1. **Source mod**: the original mod. Project Go reads it but never changes it.
2. **Project file**: your editable translation work, usually ending in
   `.ssmt.json`.
3. **Translation memory**: an optional persistent SQLite `.db` catalog that
   remembers source/translation pairs across projects and program restarts.
4. **Pristine backup**: a build-time copy of the unmodified source.
5. **Translated clone**: a complete source copy with translations applied.
   Enable this instead of the original mod, never alongside it.

Never choose the source-mod folder as a project or clone destination.

Example layout:

```text
C:\Games\Starsector\mods\ExampleMod\          original source mod
C:\SSMT Work\Example French.ssmt.json         editable project
C:\SSMT Work\catalogs\english-french.db       reusable translation memory
C:\SSMT Work\ExampleMod-source-backup\         pristine clone
C:\Games\Starsector\mods\ExampleMod-French\  translated clone
```

## 2. Translation memory explained simply

The SQLite database is a real file on disk. Closing SSMT does not erase it.
You can keep using the same database for many projects.

Each catalog record stores:

- exact source text;
- source language;
- target language;
- translated text;
- context identifying where the text was used;
- creation and update timestamps.

Context prevents a translation for a menu label from being blindly reused for
an unrelated story sentence. Exact and similar matches can be suggested, but
SSMT never automatically approves them.

When an AI response is imported with the translation-memory option:

- new source/context pairs are added;
- an existing identical source/language/context record is updated;
- unrelated existing records remain;
- the operation is transactional, so a failed import does not leave half an
  import behind.

Use one catalog per language direction, such as:

```text
english-to-french.db
english-to-spanish.db
chinese-to-english.db
```

You may use one database for several directions, but separate files are easier
to back up and understand.

### Back up a catalog

Do not copy an actively used database manually. Use the CLI backup command:

```powershell
ssmt-cli.bat tm backup `
  "C:\SSMT Work\catalogs\english-french.db" `
  "D:\Backups\english-french-2026-07-26.db"
```

Check the original or backup:

```powershell
ssmt-cli.bat tm integrity "C:\SSMT Work\catalogs\english-french.db"
```

Exit code `0` means healthy. Exit code `1` means the check failed.

### Export the catalog to a readable file

JSON preserves all fields:

```powershell
ssmt-cli.bat tm export `
  "C:\SSMT Work\catalogs\english-french.db" `
  json `
  "C:\SSMT Work\exports\english-french.json"
```

CSV is convenient for a spreadsheet:

```powershell
ssmt-cli.bat tm export `
  "C:\SSMT Work\catalogs\english-french.db" `
  csv `
  "C:\SSMT Work\exports\english-french.csv"
```

Import an interchange file:

```powershell
ssmt-cli.bat tm import `
  "C:\SSMT Work\catalogs\english-french.db" `
  json `
  "C:\SSMT Work\imports\reviewed-translations.json"
```

`json` and `csv` are the only accepted format values.

## 3. GUI: first project from start to finish

### Start Project Go

Extract the development ZIP, open `Project Go`, and double-click `Project Go.exe`. The
runtime is included; Java does not need to be installed.

### Create

1. Select **Create Project**.
2. Select the exact mod folder containing `mod_info.json`.
3. SSMT reads that JSON automatically.
4. Choose where to save the new `.ssmt.json` project.
5. Review the suggested patch ID:
   `<original-mod-id>.translation`.
6. Review the suggested patch name:
   `Translation (Original Mod Name)`.
7. Confirm creation and wait for extraction.

**Create Project** is a split button: clicking its main face creates a plain
project, and its dropdown arrow reveals two schema variants:

- **Create with JSON Schema** Ã¢â‚¬â€ use only when the mod has visible text in
  custom JSON locations that standard extraction does not know about. A
  schema is an explicit list of relative files and JSON pointers; it is not
  a repair file. See [JSON_SCHEMAS.md](JSON_SCHEMAS.md).
- **Create with CSV Schema** Ã¢â‚¬â€ the same idea for a mod's extra or
  unrecognized CSV columns (e.g. a custom tooltip column standard
  extraction doesn't know about). See [CSV_SCHEMAS.md](CSV_SCHEMAS.md).

Both variants are opt-in additions to standard extraction, not replacements
for it.

### Translate and review

- Click a row to see its source and translation preview.
- Type into the translation column.
- Use search to find text, IDs, or files.
- Use the status selector to isolate untranslated or invalid rows.
- Select several rows and use **Mark Reviewed** after checking them.
- `Ctrl+S` saves.
- `Ctrl+F` focuses search.
- A live "Translated X/Y (Z%)" label in the toolbar tracks overall progress
  as you translate. See "Check font and translation coverage" below for the
  full picture, including whether the game's font can even display what
  you typed.

Validation protects placeholders and game syntax. For example, translating:

```text
Welcome, %s
```

to:

```text
Bienvenue
```

is invalid because `%s` disappeared. A safe translation retains `%s`.

### Choose or share a translation-memory catalog

If you already have a catalog elsewhere, select **Open Translation Memory**
and choose its `.db`/`.sqlite` file; SSMT verifies it and reuses it for
**Refresh with Translation Memory** and AI imports, and remembers your choice.

If you do nothing, the GUI automatically creates and shares the same growing
catalog used by SSMT Auto:

```text
%LOCALAPPDATA%\Project Go\project-go-catalog.db
```

To combine an older database into the active one:

1. Open the database you want to keep as the active catalog.
2. Select **Compare / Merge Catalog**.
3. Choose the older or secondary database.
4. Read the comparison: "Add" is new, "higher-confidence update" means its
   provenance is safer than the active value, "conflict" means SSMT leaves
   the active value alone.
5. Choose **Yes** only if the proposed add/update counts look correct.

Comparing is always read-only. Merging never overwrites an equal- or
higher-confidence conflicting translation.

### Use an online AI without giving it the mod

1. Select **Export for Online AI**.
2. SSMT examines the source and suggests its main language. It recognizes
   Chinese (`zh`), Japanese (`ja`), Korean (`ko`), Russian (`ru`), and clear
   English (`en`). `und` means it could not decide Ã¢â‚¬â€ correct the suggestion
   for mixed-language material or a wrong guess.
3. The target defaults to English (`en`); keep it for English localization.
4. SSMT saves `<Mod Name> words.json` beside the project.
5. Upload only that JSON to the online service.
6. Tell it: "Follow the instructions inside this JSON and return the complete
   JSON only."
7. Save the returned JSON beside the project using the suggested
   `<Mod Name> words translated.json` name.
8. In SSMT, select **Import AI Response**. The expected response is found
   automatically.
9. Select **Yes** to add the imported pairs to your persistent catalog, then
   choose or create the `.db` file.
10. Review all loaded translations and validation statuses.

The export instructs the AI to translate into natural Starsector English,
return import-ready JSON, preserve schema, IDs, source strings, tokens,
formatting, and line breaks, use IDs/context to resolve terminology, remain
consistent across repeated strings, prefer polished localization over
literal wording, and never invent lore or mechanics absent from the source.

SSMT rejects the complete response if the AI changes a source string, ID,
schema version, or source-mod identity. Blank translations are simply skipped.
The AI-translated mod name becomes:

```text
Translated Name (Original Name)
```

### Refresh after a mod update

1. Open your existing project against the updated source mod.
2. Select **Refresh Project** for normal comparison.
3. Select **Refresh with Translation Memory** to also consult your catalog.
4. Review unchanged, changed, added, removed, and conflicted entries.
5. Apply the refresh only after reading the report.

Unchanged translations are preserved by exact stable identity. Fuzzy or moved
suggestions remain suggestions.

### Build and test

1. Select **Make My Personal Copy**.
2. Confirm that this copy is only for your own game.
3. SSMT creates a translated clone and a `-source-backup` sibling.
4. Keep the pristine backup outside the Starsector `mods` directory.
5. Never choose the original mod directory.
6. Disable the original and enable only the translated clone in Starsector.
7. Test menus, descriptions, missions, variants, and any translated images.
8. Build again without changes. Both clones should be unchanged.

### Check font and translation coverage

Two ways to see how complete a translation is before you build:

- The Translation Editor toolbar shows a live "Translated X/Y (Z%)" label
  as you work.
- The **Font Coverage** tab checks something different: whether the
  in-game font can actually *display* your translated text. Select
  **Check Font Coverage**, choose the Starsector `.fnt` file (e.g.
  `starsector-core\graphics\fonts\insignia15LTaa.fnt` in your Starsector
  install), and review any findings Ã¢â‚¬â€ each one names the file, the entry,
  and which characters the font has no glyph for.

A translation can be 100% complete and still show `?` in-game if the
active font can't render a character you typed; check both.

## 4. CLI: setup

The CLI ZIP is named after the current version in `gradle.properties`
(`ssmtVersion`, currently `0.6.0`):

```text
ssmt-cli\build\distributions\ssmt-cli-0.6.0.zip
```

Unlike the Windows GUI development bundle, the CLI distribution requires JDK
25. Extract it, then in PowerShell:

```powershell
cd "C:\Tools\ssmt-cli-0.6.0"
.\bin\ssmt-cli.bat --version
```

Every path containing spaces must be inside quotes. Commands return `0` for
success and a nonzero value for failure.

## 5. CLI: commands and examples

### Scan a mods directory

```powershell
.\bin\ssmt-cli.bat scan "C:\Games\Starsector\mods"
```

`scan` reads each child mod's metadata, reports invalid mods, checks dependency
relationships, and prints dependency order. It does not extract or modify.

### Extract and inspect coverage

```powershell
.\bin\ssmt-cli.bat extract "C:\Games\Starsector\mods\ExampleMod"
```

This reports the number of extracted strings and unsupported files. It does
not create an editable project and does not change the mod.

### Validate one translated string

```powershell
.\bin\ssmt-cli.bat validate "Welcome, %s" "Bienvenue, %s"
```

Use this for a quick protected-token check. Exit `0` means no validation issue.

### Create a project

```powershell
.\bin\ssmt-cli.bat project create `
  "C:\Games\Starsector\mods\ExampleMod" `
  "C:\SSMT Work\Example French.ssmt.json" `
  --patch-id "example.fr" `
  --patch-name "Exemple (Example Mod)"
```

Arguments:

- first path: source mod;
- second path: new project JSON;
- `--patch-id`: stable translation-project identifier retained for project and
  interchange compatibility;
- `--patch-name`: translated project/display name. Clone publication preserves
  the source mod's own runtime ID and metadata.

With a custom extraction schema:

```powershell
.\bin\ssmt-cli.bat project create `
  "C:\Games\Starsector\mods\ExampleMod" `
  "C:\SSMT Work\Example French.ssmt.json" `
  --patch-id "example.fr" `
  --patch-name "Exemple (Example Mod)" `
  --json-schema "C:\SSMT Work\example-schema.json"
```

The CLI project file can be edited by tooling, but preserve its schema,
identities, source text, and JSON structure.

### Preview a source update

```powershell
.\bin\ssmt-cli.bat project refresh `
  "C:\Games\Starsector\mods\ExampleMod" `
  "C:\SSMT Work\Example French.ssmt.json"
```

This is a dry run. It prints reconciliation counts and does not change the
project.

Preview with translation-memory suggestions:

```powershell
.\bin\ssmt-cli.bat project refresh `
  "C:\Games\Starsector\mods\ExampleMod" `
  "C:\SSMT Work\Example French.ssmt.json" `
  --tm "C:\SSMT Work\catalogs\english-french.db" `
  --source-language en `
  --target-language fr `
  --minimum-score 0.80
```

`--minimum-score` ranges conceptually from `0` to `1`. Higher values show fewer
but closer fuzzy matches. `1.0` effectively requires an exact normalized text
match. Suggestions are never auto-applied.

After reviewing the dry run, explicitly write the refreshed project:

```powershell
.\bin\ssmt-cli.bat project refresh `
  "C:\Games\Starsector\mods\ExampleMod" `
  "C:\SSMT Work\Example French.ssmt.json" `
  --apply
```

### Build the translated clone

```powershell
.\bin\ssmt-cli.bat project build `
  "C:\Games\Starsector\mods\ExampleMod" `
  "C:\SSMT Work\Example French.ssmt.json" `
  "C:\Games\Starsector\mods\example.fr"
```

The first argument is always the original source mod. The last argument is the
translated-clone destination; SSMT also creates a `-source-backup` sibling.
SSMT validates source hashes and translations, stages both outputs, and
publishes only complete clones. Disable the original before enabling the
translated clone.

## 6. Common problems

**Missing `mod_info.json`**

You selected the folder above or below the actual mod root. Select the folder
that directly contains `mod_info.json`.

**Malformed `Ture`**

The source contains invalid syntax. SSMT reports it and does not rewrite the
mod. Ask the mod author for a correction or work from an explicitly repaired
copy.

**Invalid translation**

Compare placeholders, `$variables`, escape sequences, markup, and line breaks
with the source.

**AI response rejected**

The service probably wrapped JSON in commentary/code fences, removed entries,
changed IDs/source text, or returned a different object. Ask it to return the
complete JSON object only.

**Duplicate AI catalog entry**

AI catalog integration updates an existing identical
source/language/context record and adds new records. General CLI JSON/CSV
imports remain strict: an identity collision rejects and rolls back that import
so an interchange file cannot silently overwrite catalog data.

**Non-ASCII path fails in CLI**

Some Windows batch/PowerShell boundaries corrupt Unicode arguments before Java
receives them. Use the GUI file chooser or temporarily use an ASCII-only path.

**Installer unavailable**

The development ZIP is portable. Building the Windows installer separately
requires WiX.

## 7. Safe routine

1. Back up the translation-memory database.
2. Run its integrity check.
3. Create/open the project.
4. Translate or perform the AI round trip.
5. Review and validate.
6. Save the project.
7. Build a pristine backup and translated clone; enable only the translated clone.
8. Test in Starsector.
9. Keep the original mod unchanged.
