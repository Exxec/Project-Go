# Simple user flow and file model

## Intended outcome

The normal Project Go experience should feel like one task, not a collection of
project, catalog, report, backup, and interchange files:

```text
Drop or choose a mod
        |
        v
Translate missing text  <--- drag in an AI response if needed
        |
        v
Install one translated copy
```

Project Go still keeps durable state, history, recovery data, and evidence, but
normal users should not have to name, place, move, or understand those internal
files.

## What is confusing now

The current entry points expose different storage models:

| Path | User-visible handling |
|---|---|
| Normal GUI | Hidden hashed workspace, but manual request save/open and output-parent selection |
| Auto | Sibling `Project Go - ...` folder with project, state, request, response, extracted source, translated clone, and source backup |
| Advanced GUI/CLI | User-managed `.ssmt.json`, translation-memory database, schemas, reports, restore points, and output paths |

The Auto workspace alone can expose five bookkeeping/interchange files, a
content-addressed extraction directory, and two complete output directories.
Exact response filenames are part of the workflow. The older GUI documentation
uses still another set of names and locations. These are useful implementation
details, but they do not represent separate user goals.

The permanent pristine backup is also redundant in the normal case: Project Go
does not modify the selected source archive or directory, so that source remains
the pristine original. A temporary prior-output backup is still needed for safe
transactional replacement, but it does not need to become a second published mod
tree.

## One shared workflow

The GUI, Auto launcher, and simple CLI should call one workflow and show the same
three states.

### 1. Choose a mod

Accept a ZIP, a mod directory, or `mod_info.json` by drag-and-drop or one
**Choose Mod** button. Resolve an archive wrapper automatically when exactly one
mod root exists. Show the mod name, version, source type, and a clear warning if
selection is ambiguous or unsafe.

Project Go creates or resumes internal work by stable mod identity. It should not
create a sibling `Project Go - ...` directory beside the source.

### 2. Translate

Show one project card with progress and one context-sensitive primary action:

- **Continue Translating** when the built-in editor has unfinished entries.
- **Create AI Request** when the user wants an external translation pass.
- **Import AI Response** after a request has been made. The response may have any
  filename; embedded identity fields determine whether it belongs to this mod.
- **Review Changes** when refreshed source text, conflicts, or validation findings
  need a decision.
- **Install Translated Copy** when all required entries are valid.

Do not show Export, Import, and Build as equally available actions at the same
time. Secondary actions may remain in a small menu, and the full legacy controls
remain under Advanced.

An external AI exchange requires one temporary handoff file at a time. Project Go
suggests a human-readable request name in Downloads, remembers the request, and
accepts the returned JSON by drag-and-drop or file selection without requiring a
special name or location. Import failure leaves both project state and the user's
response file untouched.

### 3. Install

Ask for the Starsector `mods` directory once, validate it, and remember it. The
primary action then creates or updates exactly one visible result:

```text
<Starsector>\mods\<original-folder>-English\
```

Show **Open Installed Copy** and **Launch/Test Instructions** after success. The
application disables neither the original nor other mods automatically; it tells
the user to enable only one copy.

If the user does not want direct installation, **Export Copy...** is a secondary
action that chooses a destination once and creates the same single translated
folder there.

## User-visible file contract

Normal users interact with no more than these items:

| Item | When visible | Ownership |
|---|---|---|
| Original ZIP/folder | Always | User; read-only to Project Go |
| AI request JSON | Only when external AI is selected | Temporary user handoff |
| AI response JSON | Supplied by user; any filename | Imported read-only |
| Translated mod folder | Only after a complete build | User-visible result |

There is no user-managed project document, state file, catalog database, source
backup, changes CSV, extracted archive tree, or recovery directory in the normal
flow.

## Internal file contract

All implementation state lives below one application-data root:

```text
%LOCALAPPDATA%\Project Go\
  catalog.db
  projects\
    <stable-project-id>\
      project.json
      state.json
      history\
      recovery\
      reports\
  cache\
    archives\<content-hash>\
  logs\
```

These names are intentionally boring and implementation-owned. The GUI shows a
project name and status, not the hashed path. **Open Project Data** and report
exports remain available under Advanced for diagnostics.

Internal storage rules:

- Create application-data parents on first use and test from an initially absent
  root.
- Keep one authoritative project document per mod/language identity.
- Store change reports internally; show their summary in the UI and export them
  only when requested.
- For a ZIP, cache extraction only while it is needed for an active incomplete
  project. Remove or make it reclaimable after a successful build when the
  original ZIP is still available and hash-matched.
- For a directory, read the source in place and never make a permanent pristine
  duplicate.
- Stage an output replacement beside its destination. Keep the previous output
  only as a recovery journal until the replacement is verified, then remove it.
  If interrupted, recover automatically or present one clear recovery action on
  the next launch.
- Never place workspaces, locks, reports, or recovery data inside the source mod
  or installed translated copy.

## File naming and identity

Filenames are presentation, not identity. Project and exchange identity comes
from validated fields containing the source mod ID, source fingerprint, target
language, project revision, and entry-set digest.

Consequences:

- Renaming a source folder, request, or response does not break the workflow.
- A response for an older source revision is rejected with a plain explanation.
- Two mods with the same display name do not collide.
- The output folder uses a readable name, while `mod_info.json` receives a stable,
  collision-safe translated mod ID.

## Migration from existing files

On first open after this change, Project Go searches only the existing documented
legacy locations. If exactly one matching project exists, it offers **Import Old
Project** and copies it into internal storage transactionally. It does not delete,
rename, or rewrite the legacy workspace.

Existing catalog entries merge through the normal provenance rules. Existing
translated copies and source-backup folders remain untouched. After successful
adoption, the UI explains that they are old outputs and offers to open their
location; cleanup is always an explicit user decision.

Ambiguous legacy projects stop at a chooser that shows mod ID, source path,
project timestamp, and entry counts. Project Go never selects the newest file by
guessing.

## Error and recovery language

Errors should name the user task, not the implementation file:

- “Project Go could not save your progress; your previous progress is still
  available.”
- “This response belongs to an older version of the mod. Create a new AI
  request.”
- “The installed copy could not be replaced. The previous installed copy was
  restored.”
- “Project Go found two older projects for this mod. Choose which one to import.”

Detailed paths, exception chains, hashes, and reports stay behind **Technical
Details** and remain copyable for support.

## Advanced mode

Advanced mode retains explicit paths and portable artifacts for maintainers:

- import/export portable `.ssmt.json` projects;
- select, merge, back up, and inspect translation-memory databases;
- edit JSON/CSV extraction schemas;
- export change, coverage, terminology, diagnostic, and revival evidence reports;
- choose custom workspace and output paths;
- use scripted CLI operations.

Advanced capabilities must not change the normal project's active state until an
import or operation validates and commits successfully.

## Acceptance criteria

- A first-time user can go from ZIP to installed translated copy without opening
  or naming a workspace, project, database, report, backup, or extraction folder.
- The normal path creates one installed/output directory, not a translated tree
  plus a permanent pristine-backup tree.
- External AI requires one export action and one response drop; response filenames
  and locations are irrelevant.
- GUI, Auto, and simple CLI use the same workspace service, state transitions,
  destination rules, messages, and tests.
- Cancelling any chooser changes nothing and does not navigate forward.
- Failed import, refresh, save, build, or output replacement retains the previous
  active state and output.
- Source bytes and metadata remain unchanged across every success and failure
  path.
- Internal cache and recovery storage have visible size information and a safe
  cleanup command under Settings.
- Legacy work is adopted by copying after explicit confirmation; no old file is
  deleted automatically.
