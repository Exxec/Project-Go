# Project Go

Project Go is an offline-first Java tool for analyzing and localizing Starsector
mods without modifying their source directories. The long-term pipeline scans
mods, extracts localizable content, validates translations, and makes a
separate personal-use copy for the user’s own game.

## Repository scope

This repository contains the Project Go source code and reference documentation. It
intentionally excludes generated builds, local caches, and third-party mod
samples. Historical documents may refer to the former private test corpus;
those references record prior verification evidence and are not bundled inputs
or redistribution rights.

## Current status

The simplified source-safe workflow is implemented on `main`; exact implementation
commit `10f274b` passed Windows and Linux CI run `34605294776`. The existing
`v0.7.0` tag has no published GitHub Release;
post-tag source on `main` now identifies itself as `0.8.0-dev`, not as the old
tagged version. It remains development work, not a release candidate. Manual
GUI/file-picker and in-game acceptance remain separate gates.
Project Go scans Starsector mods, extracts standard CSV/JSON-like and bytecode
strings without executing mod classes, stores reusable translations in
SQLite, validates protected syntax, and generates deterministic
non-destructive translated clones. Opt-in JSON and CSV extraction-schema catalogs
cover mod-specific fields beyond the standard set.

The runnable JavaFX desktop application can start or continue localization
projects, edit and validate translations, save work, and make a separate
translated copy for personal use.
It also checks translated text against a Starsector font's glyph coverage
and shows a live translation-progress summary. Optional draft adapters support
Ollama, Gemini, and the OpenAI Responses API.

The versioned workflow connects extraction, editing, checking, and making a
personal-use copy.

## Requirements

- JDK 25
- No system Gradle installation is required

The Gradle wrapper uses Gradle 9.1 because it is the first Gradle release with
full Java 25 support.

## Build and test

On Windows:

```powershell
.\gradlew.bat build
```

On Linux or macOS:

```bash
./gradlew build
```

The `build` task compiles all modules, runs tests, Checkstyle, and SpotBugs,
and creates CLI distributions.

## Run the scanner

```powershell
.\gradlew.bat :ssmt-cli:installDist
.\ssmt-cli\build\install\ssmt-cli\bin\ssmt-cli.bat scan "C:\path\to\starsector\mods"
```

The scanner treats every child directory as a possible mod. Invalid or missing
metadata is reported as a warning; dependency cycles fail the scan. Source
files are opened for reading only.

## Simple command-line workflow

The simple CLI follows the same load, exchange, and single-output contract as
the normal desktop flow:

```powershell
.\ssmt-cli\build\install\ssmt-cli\bin\ssmt-cli.bat translation load MOD_FOLDER
.\ssmt-cli\build\install\ssmt-cli\bin\ssmt-cli.bat translation export MOD_FOLDER REQUEST.json
.\ssmt-cli\build\install\ssmt-cli\bin\ssmt-cli.bat translation import MOD_FOLDER RESPONSE.json
.\ssmt-cli\build\install\ssmt-cli\bin\ssmt-cli.bat translation build MOD_FOLDER OUTPUT_FOLDER
```

Project state remains internal. The final command creates one translated copy.

Application-owned storage has a read-only, hash-backed hygiene preview. The
preview command saves the exact approval manifest; cleanup does not discover new
candidates and stops if a listed candidate changes before deletion:

```powershell
ssmt-cli storage preview --older-than-days 30
ssmt-cli storage clean --older-than-days 30
```

## Advanced: create and build a portable localization project

```powershell
.\gradlew.bat :ssmt-cli:installDist
.\ssmt-cli\build\install\ssmt-cli\bin\ssmt-cli.bat project create `
  "C:\path\to\one\mod" "C:\work\translation.ssmt.json" `
  --patch-id example.translation --patch-name "Example Translation"

# Edit translatedText values, then validate and make your personal copy:
.\ssmt-cli\build\install\ssmt-cli\bin\ssmt-cli.bat project build `
  "C:\path\to\one\mod" "C:\work\translation.ssmt.json" `
  "C:\path\to\starsector\mods\example-translation"
```

The build rejects blank or invalid translations and stale source files before
making a pristine source backup and translated clone for your own use. It never
writes beneath the source mod; enable the translated clone instead of the
original, and keep the generated copy private.

## Run the desktop application

```powershell
.\gradlew.bat :ssmt-gui:installDist
.\ssmt-gui\build\install\ssmt-gui\bin\ssmt-gui.bat
```

The desktop app opens on a **Choose → Translate → Install** workflow with one
primary action at a time. Project state is saved internally, completed saved work
skips directly to installation, and a normal build creates one translated copy.
The former project editor, practice project, custom paths, catalogs, schemas, AI
providers, and diagnostics remain under Advanced. Custom schemas are
exact-path/pointer (JSON) or exact-path/column (CSV) catalogs; see
[JSON_SCHEMAS.md](JSON_SCHEMAS.md) and [CSV_SCHEMAS.md](CSV_SCHEMAS.md).

## Run Project Go Auto (drag-and-drop)

```powershell
.\gradlew.bat :ssmt-auto:run --args='"C:\path\to\ExampleMod.zip"'
```

Runs the same source-safe project/translation-memory/refresh/validation/copy
pipeline headlessly against a dropped mod ZIP (or unpacked mod), sharing the
same default SQLite master translation library as the GUI. Project, state, and
archive-extraction files stay in Project Go application data. Beside the selected
input, Auto exposes only the AI handoff files when needed and one translated copy
when complete. Returned response filenames are identified from embedded identity
and integrity fields rather than their path. See [AUTO_GUIDE.md](AUTO_GUIDE.md).

## Native packaging

```powershell
# Self-contained application image:
.\gradlew.bat :ssmt-gui:jpackageImage

# Host-native installer; requires WiX on Windows or native packaging tools:
.\gradlew.bat :ssmt-gui:jpackageInstaller
```

Release metadata, optional signing properties, and platform requirements are
documented in [DISTRIBUTION.md](DISTRIBUTION.md).
For a smoke-tested, self-contained Windows development bundle with user
documentation and checksum, run:

```powershell
.\gradlew.bat :ssmt-gui:developmentBundleChecksum
```

See [USER_GUIDE.md](USER_GUIDE.md) for features, controls, options, and
real-world testing instructions.
New users can choose **Open Sample Project** to copy a resettable synthetic
fixture into a writable workspace. The **Project Info** tab shows workflow
progress and the active source, project, output, translation-memory, schema,
and recovery locations.
See [BEGINNERS_GUIDE.md](BEGINNERS_GUIDE.md) for a short first-run walkthrough.
See [AUTO_GUIDE.md](AUTO_GUIDE.md) for the headless drag-and-drop workflow.

## Modules

- `ssmt-core`: dependency-free domain records, exceptions, and extraction contracts
- `ssmt-scanner`: metadata parsing, discovery, and dependency ordering
- `ssmt-extractor`: CSV, JSON-like, plain-text, and non-executing class-file/jar handlers
- `ssmt-tm`: versioned SQLite translation-memory persistence
- `ssmt-validation`: structured placeholder and syntax integrity checks
- `ssmt-patcher`: standard-file reinjection and transactional clone publication
- `ssmt-ai`: optional context-aware Ollama/Gemini/OpenAI draft adapters
- `ssmt-gui`: JavaFX desktop shell and tested plain-Java view models
- `ssmt-project`: versioned project documents and workflow orchestration
- `ssmt-cli`: command-line application
- `ssmt-auto`: drag-and-drop/headless automation state machine (`Project Go Auto`)

See [ENVIRONMENT.md](ENVIRONMENT.md) for setup details and
[ARCHITECTURE.md](ARCHITECTURE.md) for module boundaries.
The evidence-gated implementation priorities and protocol for assessing future
mod revival attempts are in [docs/ROADMAP.md](docs/ROADMAP.md).
The target normal workflow, implemented slices, and remaining acceptance
criteria for the reduced user-visible file model are described in
[docs/designs/simple-file-workflow.md](docs/designs/simple-file-workflow.md).

## License

Project Go is licensed under the [GNU General Public License v3.0](LICENSE).
