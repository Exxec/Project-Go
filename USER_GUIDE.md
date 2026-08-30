# Project Go Personal-Use Guide

`Last updated: 2026-08-30 (personal-copy preview, restore point, and change report added)`

## What this build is

Project Go is an offline-first Starsector localization tool. The Windows
development bundle is self-contained: it includes the application and its Java
runtime, so testers do not need to install Java. It is a development-testing
build, not a signed installer.

Project Go reads source mods and makes a pristine backup plus a translated copy for
your own game.
It must never edit the source mod. Malformed source, including `Ture` where a
boolean is required, is reported and is not silently repaired.

## Start the Windows program

1. Extract the development ZIP to a writable folder.
2. Open the `Project Go` folder.
3. Run `Project Go.exe`.
4. Read the personal-use reminder. Generated copies are for your own game and
   should not be shared.

Windows SmartScreen may warn because the development build is unsigned. Verify
the ZIP against its adjacent `.sha256` file before testing.

Keep working project files and the pristine backup outside both the original
mod and the Starsector `mods` directory. Only the translated clone belongs in
`mods`, and it replaces the original while testing or playing.
After selecting a source mod, the GUI uses a safe sibling workspace named
`Project Go - <Mod Name>`. Generated files never go inside the
source mod:

```text
Project Go - <Mod Name>\
  <Mod Name> project.ssmt.json
  <Mod Name> words.json
  <Mod Name> words translated.json
  <Mod Name> translated\
  <Mod Name> translated-source-backup\
```

The SQLite translation-memory pool remains separate. The GUI and Project Go Auto
share `%LOCALAPPDATA%\Project Go\project-go-catalog.db` by default. It is created
automatically, reused after restart, and never stored inside a source mod.
Opening another database makes that catalog the remembered GUI default.
Existing projects keep using their project directory unless it is inside the
source mod, in which case Project Go uses the safe sibling workspace.

## Recommended first test

If you do not want to begin with a real mod, select **Try a Practice Project**.
Choose a writable parent folder; Project Go creates or resets
`ssmt-sample-project` there and opens a synthetic localization project. The
copy contains no proprietary Starsector or community-mod content.

1. Select **Start New Translation** and choose a mod folder containing
   `mod_info.json`.
2. Project Go automatically loads `mod_info.json`, proposes
   `<mod-id>.translation`, and proposes `Translation (<original mod name>)`.
   You may edit both before extraction.
3. Project Go creates
   `Project Go - <Mod Name>\<Mod Name> project.ssmt.json`.
4. Search or filter entries, edit translations, and review validation results.
5. Save the project.
6. Select **Preview My Copy**. This safe dry run changes no files and tells you
   how many entries and source files are ready.
7. Select **Make My Personal Copy**. Project Go creates `<Mod Name> translated`
   plus `<Mod Name> translated-source-backup` in the workspace.
8. Copy or move only the translated clone into Starsector's `mods` directory.
9. Disable the original mod, enable the translated clone, then launch Starsector.
10. Rebuild without changing translations and confirm that output is unchanged.

## Project Info and recovery

The **Project Info** tab shows the current workflow state and every active
location Project Go can identify: source mod, project document, translated clone,
pristine source backup,
translation-memory database, JSON/CSV schema catalogs, and recovery snapshot
directory. **Open Folder** is enabled only after that location exists.

For an open project, autosave snapshots live in `.ssmt-recovery` next to the
project document—not inside the source mod. The first-run message also explains
that builds produce two clones: keep the pristine backup safe and enable the
translated clone instead of the original mod. Never enable both copies.

## Translation Editor

| Control | Effect |
|---|---|
| Start New Translation | Extracts supported text into a new editable project. Source files remain untouched. |
| Start with JSON Settings | Also loads an explicit exact-path JSON schema. Use this only for mod-specific visible JSON fields. |
| Start with CSV Settings | Also loads an explicit exact-path/column CSV schema. Use this only for mod-specific unrecognized CSV columns. |
| Continue Saved Translation | Loads an existing versioned `.ssmt.json` project. Unsupported major versions are rejected. |
| Save | Atomically saves current translations. Recovery snapshots are stored outside source mods. |
| Preview My Copy | Performs every validation needed for a build without changing any mod files. It shows what will be translated and what will stay untouched. |
| Save Restore Point | Saves an explicit point you can safely return to before trying a large edit or AI import. |
| Undo to Restore Point | Restores the last explicit restore point. This replaces the open project document, so use it only when you want to discard later edits. |
| Refresh Project | Compares the project with an updated source mod and shows unchanged, changed, added, removed, and conflicted entries before replacement. |
| Refresh with Translation Memory | Adds translation-memory suggestions to refresh results. Suggestions are never applied automatically. |
| Open Translation Memory | Opens and integrity-checks an existing SQLite `.db`/`.sqlite` catalog and remembers it for later GUI sessions. |
| Compare / Merge Catalog | Compares another database with the active catalog before writing. Missing entries and strictly higher-confidence provenance can be merged; equal/lower-confidence disagreements remain reported conflicts. |
| Export for Online AI | Writes every project entry to an ID-keyed JSON document with source text, context, existing provenance, and strict output instructions. |
| Import AI Response | Validates a complete AI response, then imports reviewable drafts or explicitly bulk-approves every validated result. It can also update the patch name and translation memory. |
| Make My Personal Copy | Validates entries and transactionally creates a pristine source backup plus a translated copy for your own use. A failure restores prior outputs and does not modify the source. The copy includes `Project Go Changes.csv`, a simple record of every source file, key, status, and translation. |
| Search | Filters by source text, translated text, identity, or related displayed content. |
| Status filter | Shows all, untranslated, translated, or invalid entries. |
| Mark Reviewed | Marks every selected row reviewed. Multi-selection is supported. |
| Suggestion list | Shows exact/fuzzy translation-memory candidates for the selected entry. |
| Apply Suggestion | Copies the selected suggestion into the editable translation; it remains subject to validation and review. |

The first toolbar is the normal path. Use **More Actions** only when you need
catalog, AI, browser-review, or advanced project tools. Use **Selected Rows**
when you need to review, approve, reject, or inspect the rows you selected.

Shortcuts:

- `Ctrl+S`: save the open project.
- `Ctrl+F`: focus the search field.
- Standard table navigation and multi-selection keys are supported.

Protected placeholders, format tokens, escapes, and other syntax must remain
compatible with the source. Invalid translations are blocked from publication.
Ordinary numeric percentages such as `85%` and `1.5% of capacity` are treated
as prose, not as `printf` placeholders. Error details are selectable and can
be copied with `Ctrl+A`, then `Ctrl+C`.

## Bounded project translation

Use **Translate Blank Entries** to process only untranslated rows. Select
`LOCAL_ONLY`, `SMART_DEFAULT`, or `AI_ASSISTED`, choose Argos or
TranslateLocally as the preferred local provider, and set a bounded batch size.
The progress dialog can pause/resume or cancel safely between batches. Existing local drafts,
human edits, and author translations are preserved. Results remain drafts and
the status/log reports the backends actually used and unresolved work.

The AI Provider Settings tab stores provider type, endpoint, model, and only
the *name* of a credential environment variable—never the credential value.
Before a remote provider can receive routed text, SSMT displays a per-run
disclosure and requires confirmation. With no configured AI, routing stays
offline. Argos remains the preferred default; TranslateLocally uses
`Helsinki-NLP/opus-mt-zh-en` by default and can be selected explicitly.

The CLI equivalent is:

```text
ssmt translate-project project.ssmt.json --memory translations.db --source-language zh --target-language en --mode SMART_DEFAULT --preferred-local ARGOS --maximum-batch-size 32
```

Optional retained lineage is written beside the project as schema-v2 JSON.
Use `--discard-lineage` when unreviewed provider metadata should not persist.
Remote CLI providers additionally require `--allow-remote-ai`; API provider
type, endpoint, model, and credential environment-variable name must be
configured together.

## Manual browser AI review (no API key)

Select **Export Browser Review**, acknowledge the privacy notice, confirm the
languages, choose the maximum entries per part, and optionally enter approved
terminology. SSMT writes `PROMPT.txt`, `TRANSLATION_REQUEST.json`, and
`README.txt`. When more than one deterministic part is required, each
`part-NNN` folder contains those files and the root contains `manifest.json`.

Use **Open AI Website** to configure a provider name and HTTP(S) URL and launch
it in your default browser. ChatGPT, Claude, and Gemini names provide URL
presets. **Copy Prompt**, **Open Export Folder**, and **Reopen Last Export**
operate only on the local export. SSMT does not log in, upload, paste, click,
download, or read a website. You must deliberately send the content and save
the returned complete JSON yourself.

Use **Import Browser Response** for either a single request or one batch part.
Every expected entry must be present, nonblank, identity-stable, and valid.
Import is fail-closed and transactional. Browser-AI results are unapproved
drafts and fill only blank translations; existing local drafts, human edits,
and author localizations remain untouched. This workflow is independent of the
optional API-provider workflow below.

## External online-AI round trip

1. Open or create a project.
2. Select **Export for Online AI**. SSMT examines the loaded strings and
   suggests `zh`, `ja`, `ko`, `ru`, or `en` when confident; `und` means the
   source language is uncertain. Correct this advisory value if needed.
3. English (`en`) is suggested as the target. Change it only when you want a
   different target language. Set the batch size (default 250) to the maximum
   entries one AI response should contain. SSMT writes `<Mod Name> words.json`
   when the project fits in one file, or numbered sibling files
   (`<Mod Name> words1.json`, `<Mod Name> words2.json`, ...) when it doesn't —
   this keeps a single AI response from being silently truncated on a large mod.
4. Upload that JSON to the online AI of your choice without editing its IDs or
   source fields. Upload/translate each numbered file separately if the
   project was split.
5. Ask the AI to follow the embedded `instructions` field and return the
   complete JSON object only.
6. Save the returned JSON beside the project as
   `<Mod Name> words translated.json`.
7. Select **Import AI Response**. SSMT loads that expected file automatically;
   if it is absent, you can locate a response manually.
8. Choose whether the response should also be added to a translation
   memory database.
9. Choose **No** at the approval prompt to import reviewable drafts. Choose
   **Yes** only when deliberately approving every valid result. Approval is
   all-or-nothing: any protected-syntax or required line-break failure rejects
   the entire import before project or translation-memory state changes.

The response must preserve `schemaVersion`, `sourceModId`, every `id`, and
every `source` exactly. Unknown or duplicate IDs and modified source text cause
the entire import to fail. Blank translations are skipped. If
`translatedModName` is present, the patch name becomes:

```text
Translated Mod Name (Original Mod Name)
```

Importing as reviewable drafts remains the default. SSMT never sends the
document itself and never approves a response merely because it arrived.

Bulk approval is a separate explicit user decision; receiving a response never
approves it by itself. Approved entries become trusted human-approved
translations while provider/model, generation time, AI-refined state, and
review status remain in translation-memory lineage metadata.

The embedded prompt requests natural, polished Starsector English, consistent
terminology, use of IDs and context for ambiguous names, exact preservation of
schema/source/tokens/formatting/line breaks, and no invented lore or mechanics.

## JSON Schema Editor

Custom schemas opt specific JSON files and pointers into extraction.

- **Add Pointer** adds an exact relative path and JSON pointer.
- **Save Schema** writes the versioned schema catalog.
- Broad wildcard extraction is intentionally unavailable.

Incorrect schemas can extract internal identifiers or omit visible text. Review
the mod structure and use the narrowest pointers possible.

## Image Localization

- **Open Image** loads an image for preview.
- **Auto-Detect Text** runs Tesseract OCR to find text regions automatically
  (requires a user-supplied Tesseract executable, remembered after first
  use). Alternatively, use the geometry fields plus **Add Region** to enter a
  region manually.
- The region table lists every current region; double-click the Translation
  column to edit a region's translated text inline.
- **Render Localized Image (Text)** creates a deterministic localized PNG by
  drawing a panel and retyped text over each region — a simple, reliable
  default.

For text baked into shaded or textured artwork, where a flat text overlay
would look wrong, use the AI-assisted region regeneration workflow instead:

- **Export Image Regions for AI** crops each region (with proportionate
  padding for style context) and writes a PNG plus a plain-text
  instructions file per region to a folder you choose. Hand each pair to
  whatever AI image tool you use (SSMT never calls one itself) and ask it
  to regenerate the crop with the translated text.
- **Import Regenerated Region** loads the AI's regenerated crop for the
  selected table row. It must be the exact same pixel dimensions as the
  exported crop, or it is rejected — a resized asset could break in-game
  texture/UI placement.
- **Render Localized Image (AI)** composites every imported regenerated crop
  into a copy of the source image and writes a new PNG.

A visible seam at a crop's edge is possible if the regenerated art doesn't
perfectly match the surrounding context; this workflow validates size and
readability only; a human still visually approves the result before it goes
into a patch.

OCR is optional and requires a user-supplied Tesseract executable. Image output
is generated separately; the source image is not overwritten.

## AI Provider Settings

Providers generate drafts only. Their output is never accepted automatically.

| Option | Effect |
|---|---|
| Ollama | Uses a configurable local HTTP endpoint and model. Normally requires no cloud credential. |
| Gemini | Uses the Gemini adapter and the named credential environment variable. |
| OpenAI | Uses the Responses API adapter and the named credential environment variable. |
| Endpoint | Provider base URL. Change only for the selected provider or a compatible gateway. |
| Model | Provider model identifier sent with requests. |
| Credential variable | Name of an environment variable containing the secret, not the secret itself. |
| Validate Settings | Checks configuration shape; it does not approve future AI output. |

## Fully Offline Translation Chain

The command-line build includes bundled adapters for
[Argos Translate](https://github.com/argosopentech/argos-translate) and
[TranslateLocally](https://github.com/XapaJIaMnu/translateLocally). Install
both tools and the desired language models separately; SSMT never downloads a
model. TranslateLocally also needs the installed model ID shown by
`translateLocally -l`.

Run one glossary-first draft like this (quote paths containing spaces):

```text
ssmt offline-translate "你好，舰长" --source-language zh --target-language en --memory path/to/translation-memory.db --argos path/to/argos-translate --translate-locally path/to/translateLocally
```

Argos uses CPU by default. Add `--argos-device AUTO` to let Argos/CTranslate2
try supported acceleration, or `--argos-device CUDA` to request CUDA. `AUTO`
lazily attempts CUDA so SSMT can report the backend actually used. If GPU
initialization, execution, or memory allocation fails, SSMT retries once on
CPU, reports the reason, and uses CPU for later requests through that provider
instance. GPU failure alone never fails the translation job. SSMT does not
install or configure GPU drivers. TranslateLocally remains CPU-only and never
receives GPU settings.

Resource controls are also available:

```text
--maximum-worker-threads 1 --maximum-batch-size 32 --maximum-gpu-memory-mib 4096
```

Worker and batch values must be positive and are passed to Argos using its
documented CTranslate2 settings. The GPU-memory value is capability-aware:
Argos currently has no supported hard-cap control, so SSMT prints a warning and
does not claim to enforce it. Omit that option unless another provider later
advertises real support.

The current providers run sequentially as one-shot processes. This means two
large local models are not loaded simultaneously and each inactive model is
unloaded when its process exits. Cancelling the translation interrupts and
terminates an active provider process and is honored between provider stages.
Cancellation between multiple future batches is scheduled with the future
multi-item batch coordinator; the current command translates one item at a
time.

Pipeline safety is strict: machine drafts are never automatically accepted;
human edits cannot be replaced by lower-trust automation; source mods are never
written; protected placeholders/tokens and exact line-break sequences must
validate before approval; AI requires explicit provider configuration; CPU
always remains supported; and SSMT never downloads Argos or TranslateLocally
models implicitly. Exact duplicate requests reuse a bounded session draft, and
accepted exact translations stop at the glossary/TM unless you deliberately
request a revision.

Planned routing modes are deliberately simple:

- **Local Only:** trusted reuse, one selected local provider, then review.
- **Smart Default:** the same path, with AI only for entries whose observable
  routing signals justify it and only after explicit provider configuration.
- **AI Assisted:** permits routed refinement of long-form or complex entries,
  while preserving the same validation and review gates.

Routing scores are not translation-quality scores: `0–2` uses the local draft,
`3–4` queues optional AI review, and `5+` permits AI only when enabled. The
project-level mode/router UI and final AI call are roadmap work, not active
behavior yet.

SSMT checks for one unambiguous approved exact match. On a miss it runs Argos.
High-confidence Argos output stops there; failed, difficult, uncertain, or
structurally unsafe output advances to an independent TranslateLocally
translation of the original source. Output identifies the selected provider,
confidence category, reasons, and says `review-required`. Repeat the command
with `--approve` only after reviewing the result; that explicit action saves it
to the glossary/translation memory as a human-approved entry. A future request
for the same source/language pair then stops at the glossary.

ADR-043's remaining extension will advance unresolved local candidates to a
configured AI adjudicator with both candidates and the project's terminology,
context, and explicit style brief. That final-AI stage is roadmap work, not
part of the current command yet.

When that final stage runs, its prompt is structured rather than conversational:

```text
Source:
[untouched source]

Local machine draft (Argos Translate or TranslateLocally):
[selected local draft]

Context:
[ship/system/file context, approved terms, style brief, escalation reasons]

Instruction:
Produce polished Starsector <target language>. Preserve mechanics, protected
syntax, line breaks, terminology, and creator intent. Correct the local draft
where needed. Do not invent lore or mechanics. Return only the translation.
```

Secrets must not be entered into project files or committed configuration.
Network providers send selected text to that provider; Ollama can remain local.

## Plugins and diagnostics

The Plugin Manager lists cataloged plugin metadata, compatibility status, and
failure details without loading plugin classes during discovery. Activation
uses a bounded worker process with timeouts. Windows process separation is not
a verified security sandbox, so test only plugins you trust.

The Diagnostics tab shows bounded application messages. Structured diagnostic
exports redact known credential and sensitive-path fields.

## Font coverage and translation progress

The Translation Editor toolbar shows a live "Translated X/Y (Z%)" summary as
you edit.

The Font Coverage tab lets you select a Starsector BMFont `.fnt` file (found
under `starsector-core/graphics/fonts/` in a Starsector installation, with the
currently active one named by `data/config/settings.json`'s `defaultFont`
key) and checks every translated entry's text against that font's actual
glyph coverage. It reports entries containing characters the font cannot
render — useful for catching incomplete or wrong-script translations before
they show up in-game as `???`. This is a warning only, never a build
failure, and it is not a substitute for extraction-schema coverage: a field
SSMT never extracted at all won't appear here, since it never became a
project entry.

## Translation execution and review

**Translate Blank Entries** works in bounded batches and writes a project-
adjacent checkpoint after each completed batch. **Resume Last Translation**
restores that checkpoint only when its source-mod identity and source-text
digest still match the open project; it never writes to the source mod.
Cancellation and pause/resume take effect between batches.

The review table can filter by status, text, and provenance. **Approve Draft**
promotes only valid, nonblank selected drafts to human-reviewed provenance;
**Reject Draft** clears selected drafts without touching accepted human or
author translations. **View Lineage** loads provider/model/version/generation
metadata on demand when the optional generation sidecar exists.

In Provider Settings, **Check Local Providers** performs a read-only setup
check. It neither starts a provider nor installs/downloads a model. **Inspect
Routing Evidence** reports conservative context clues for the selected entry;
these findings currently have zero routing weight and cannot enable AI.

**Check Glossary** opens the versioned data-only JSON format documented in
`GLOSSARY_FORMAT.md` and reports conflicts without applying changes. **Export
Translation Report** writes a deterministic CSV containing stable file/key
identity, status, provenance, source, and translation for author review.

## Translation memory

Translation memory stores reusable translations persistently in the selected
SQLite `.db` file. It survives application restarts and can grow across many
projects. Reimporting AI results for the same source/language/context updates
that catalog record; new identities are added transactionally. General CLI
JSON/CSV imports remain collision-strict. Exact matches are preferred and
fuzzy matches are advisory. Back up a database before migration or large
imports and run an integrity check after recovery.

The default shared catalog is:

```text
%LOCALAPPDATA%\Project Go\project-go-catalog.db
```

Use **Open Translation Memory** to switch to an existing catalog. Use
**Compare / Merge Catalog** to select an older or secondary database. SSMT
first reports additions, higher-confidence upgrades, identical entries, and
conflicts. It changes nothing until you approve. Conflicts are never
overwritten automatically.

For an ELI5 explanation, complete GUI walkthrough, detailed CLI arguments, and
copyable examples, see `BEGINNERS_GUIDE.md`.

## Command-line interface

The CLI distribution requires JDK 25 and is mainly intended for automation:

```text
ssmt-cli scan MODS_DIRECTORY
ssmt-cli extract MOD_DIRECTORY
ssmt-cli validate "SOURCE TEXT" "TRANSLATED TEXT"
ssmt-cli project create SOURCE PROJECT --patch-id ID --patch-name NAME
ssmt-cli project create SOURCE PROJECT --patch-id ID --patch-name NAME --json-schema SCHEMA
ssmt-cli project create SOURCE PROJECT --patch-id ID --patch-name NAME --csv-schema SCHEMA
ssmt-cli project refresh SOURCE PROJECT
ssmt-cli project refresh SOURCE PROJECT --apply
ssmt-cli project refresh SOURCE PROJECT --tm MEMORY --source-language en --target-language zh --minimum-score 0.8
ssmt-cli project build SOURCE PROJECT OUTPUT
ssmt-cli project check-fonts PROJECT FONT.fnt
ssmt-cli project coverage PROJECT
ssmt-cli project import-ai-response PROJECT RESPONSE.json
ssmt-cli project import-ai-response PROJECT RESPONSE.json --tm MEMORY --approve
ssmt-cli plugins PLUGIN_DIRECTORY
ssmt-cli tm export DATABASE json OUTPUT
ssmt-cli tm export DATABASE csv OUTPUT
ssmt-cli tm import DATABASE json INPUT
ssmt-cli tm import DATABASE csv INPUT
ssmt-cli tm backup DATABASE BACKUP
ssmt-cli tm integrity DATABASE
```

`project refresh` is a dry run unless `--apply` is supplied. `--minimum-score`
controls which fuzzy TM candidates are shown; it never enables automatic
application.

`project import-ai-response` applies the same reviewable-draft validation as
the GUI's Import AI Response: unknown/duplicate ids, changed source text, or
invalid protected syntax fail the whole import before anything is written.
Results are always left as `AI_TRANSLATED` review drafts unless `--approve`
is supplied.

On Windows, use the GUI file choosers for paths containing non-ASCII characters
until the native PowerShell/batch argument-boundary limitation is resolved.

## What testers should report

Include:

- Windows version and display scaling;
- source mod name/version;
- operation performed;
- expected and actual behavior;
- diagnostic export or exact error;
- whether any source hash or timestamp changed;
- whether the translated clone loads in Starsector with the original disabled;
- whether a second unchanged build differs.

Do not attach proprietary mod contents unless distribution permission allows it.
