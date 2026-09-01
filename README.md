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

The core workflow is implemented; the Windows release candidate is awaiting
manual GUI/game acceptance testing.
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

## Create and build a localization project

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

The desktop app opens on a **Start** screen: start a new project, continue a
saved project, or use a practice project. The primary path is then **Start**,
**Translation Editor**, and **Tools and Settings**, so optional configuration,
AI exchange, image localization, and diagnostics do not crowd the translation
workflow. Custom schemas are exact-path/pointer (JSON) or exact-path/column
(CSV) catalogs; see
[JSON_SCHEMAS.md](JSON_SCHEMAS.md) and [CSV_SCHEMAS.md](CSV_SCHEMAS.md).

## Run Project Go Auto (drag-and-drop)

```powershell
.\gradlew.bat :ssmt-auto:run --args='"C:\path\to\ExampleMod.zip"'
```

Runs the same source-safe project/translation-memory/refresh/validation/patch
pipeline headlessly against a dropped mod ZIP (or unpacked mod), sharing the
same default SQLite master translation library as the GUI. It makes a patch
when the library is complete; otherwise it writes an AI translation request and
imports the returned JSON into that library on the next drop. See [AUTO_GUIDE.md](AUTO_GUIDE.md).

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
See [BEGINNERS_GUIDE.md](BEGINNERS_GUIDE.md) for a slower, beginner-friendly
GUI and CLI walkthrough with examples.
See [AUTO_GUIDE.md](AUTO_GUIDE.md) for the headless drag-and-drop workflow.

## Modules

- `ssmt-core`: dependency-free domain records, exceptions, and extraction contracts
- `ssmt-scanner`: metadata parsing, discovery, and dependency ordering
- `ssmt-extractor`: CSV, JSON-like, and non-executing class-file handlers
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

## License

Project Go is licensed under the [GNU General Public License v3.0](LICENSE).
