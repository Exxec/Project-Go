# Development Environment

## Required Software

- Eclipse Temurin or another conforming JDK 25 distribution
- Git
- Repository Gradle wrapper

Optional:

- Tesseract for OCR
- Ollama for local AI drafts
- cloud-provider credentials for optional AI adapters
- WiX for Windows installer packaging
- Bubblewrap on Linux for optional plugin sandboxing
- platform signing tools for releases

---

# Java Selection

SSMT requires Java 25.

Do not rely on a system Java installation if it points to an older runtime.

PowerShell example:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\<jdk-25>"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat --version
```

Current verified JDK:

```text
Temurin 25.0.4
```

Current verified Gradle:

```text
Gradle 9.1.0
```

---

# Normal Build Gate

```powershell
.\gradlew.bat build --offline --no-daemon --max-workers=1
```

This is the authoritative normal Windows gate.

It includes:

- compilation;
- JUnit;
- lint/warnings gate;
- Checkstyle;
- SpotBugs;
- packaging tasks.

A transient Windows file lock during `clean build` is not by itself evidence of source/build failure. Investigate recurring locks, but prefer the normal `build` gate for routine verification.

---

# CLI Invocation and Unicode Paths

Some generated Windows `.bat` or PowerShell/native invocation paths can corrupt non-ASCII command-line arguments before Java receives them.

Do not compensate for this inside SSMT.

For compatibility diagnostics, direct Java invocation is acceptable:

```powershell
$java = Join-Path $env:JAVA_HOME "bin\java.exe"
$cp = Join-Path (Get-Location) "ssmt-cli\build\install\ssmt-cli\lib\*"

& $java -cp $cp com.ssmt.cli.Main extract "C:\path\to\mod"
```

Where possible, prefer launch methods that preserve Unicode path arguments exactly.

The JavaFX file/directory chooser path remains an important Unicode-safe workflow.

---

# Encoding

Repository source and documentation are UTF-8.

SSMT project/interchange/output text is UTF-8.

Real source mods may contain legacy encodings. Compatibility decoding is handled by the extractor boundary.

Current observed fallback:

- GB18030 for specific legacy CSV source content.

Fallback decoding does not change the repository's internal UTF-8 policy.

---

# Verification Commands

```powershell
.\gradlew.bat build --offline --no-daemon --max-workers=1
.\gradlew.bat :ssmt-cli:run --args="--help"
.\gradlew.bat :ssmt-gui:run
```

For distribution:

```powershell
.\gradlew.bat :ssmt-cli:installDist
.\gradlew.bat :ssmt-gui:installDist
```

For native application image:

```powershell
.\gradlew.bat :ssmt-gui:jpackageImage
```

---

# Line Endings

Use repository `.editorconfig` and `.gitattributes`.

Do not rely on platform-default encodings or line-ending behavior in deterministic output logic.
