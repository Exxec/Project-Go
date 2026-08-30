# Technical Coding Standards

## Overview

These standards apply to all human and AI contributions to SSMT.

---

## 1. Core Stack

- **Language:** Java 25
- **Build:** Gradle Kotlin DSL
- **UI:** JavaFX 25 using MVVM
- **JSON:** Jackson
- **CSV:** Apache Commons CSV or repository-approved equivalent
- **Bytecode:** OW2 ASM
- **Database:** SQLite/JDBC behind repository APIs
- **Logging:** SLF4J + Logback
- **Testing:** JUnit 5 + AssertJ + Mockito where appropriate

Use the repository Gradle wrapper.

---

## 2. Java Design

Prefer:

- records for immutable data carriers;
- sealed types where they clarify closed domain boundaries;
- pattern matching when it improves clarity;
- explicit typed domain exceptions;
- immutable collections at public boundaries.

Do not use newer language features merely because they exist.

Virtual threads may be used for I/O concurrency only when measurements and cancellation semantics justify them.

---

## 3. Module Boundaries

- `ssmt-core` must not depend on GUI or concrete extractor modules.
- UI views contain no extraction, persistence, patching, or plugin activation logic.
- Format compatibility belongs in the owning parser/extractor layer, not scattered through orchestration code.
- Launcher/process compatibility belongs at the process boundary.

---

## 4. Source Immutability

Source mods are read-only.

No code may:

- edit;
- rename;
- delete;
- rewrite;
- normalize in place;
- create helper files inside

a source mod directory.

Generated work must be written to SSMT-owned locations or explicit output destinations.

---

## 5. Encoding

### Internal and output encoding

- Internal text is Unicode.
- Source code and documentation are UTF-8.
- Project files are UTF-8.
- Translation memory interchange is UTF-8.
- Generated metadata and normal text outputs are UTF-8.
- Always specify charsets explicitly.

### Source input compatibility

Source mods may contain legacy text encodings.

Policy:

1. attempt strict UTF-8 first;
2. use only explicit, deterministic fallback encodings justified by real fixtures;
3. current supported compatibility includes GB18030 fallback for observed legacy CSVs;
4. do not use unrestricted charset detection;
5. report fallback usage where practical;
6. never rewrite the source file to “upgrade” its encoding.

---

## 6. Unicode Paths

Windows launchers must preserve Unicode command-line arguments.

The application must not attempt to repair mojibake or replacement-character paths caused by a broken launcher.

When a launcher corrupts a path:

- fail clearly;
- identify the process boundary;
- prefer Unicode-safe invocation.

---

## 7. CSV Standards

Do not manually split CSV with regex or delimiter strings.

Every CSV extraction schema explicitly defines:

- ordered identity columns;
- localizable columns;
- optional localizable columns;
- any safe sentinel-row behavior.

### Identity

Single identity:

```text
[id]
```

Composite identity:

```text
[id, type]
```

Rules:

- identity column order is significant and deterministic;
- every component participates in the stable key;
- blank required identity components are errors;
- duplicate complete identities are errors;
- row position must never be part of identity;
- optional columns cannot weaken identity.

---

## 8. JSON-Like Standards

Use repository-configured Jackson parsing.

Compatibility features must be explicit and fixture-backed.

Do not globally accept arbitrary identifiers as booleans, nulls, or numbers.

Probable typos such as:

```json
"autofire": Ture
```

remain errors.

Diagnostics may suggest a likely correction but must not rewrite source.

---

## 9. Error Handling

- Do not return `null` from public APIs.
- Use `Optional` only for genuine optionality, not as a substitute for clear models.
- Do not throw generic `Exception` for domain failures.
- Use typed exceptions.
- Fail at the narrowest safe boundary.
- Continue processing unrelated files/mods when the failure can be safely isolated.
- Never continue after an error if doing so could merge or misidentify translation data.

---

## 10. Logging

Do not use `System.out.println()` for application logging.

Use:

- `DEBUG` for detailed parser/process traces;
- `INFO` for normal operations;
- `WARN` for recoverable compatibility issues;
- `ERROR` for task failures.

Do not log secrets or full credential values.

---

## 11. Determinism

Given identical:

- source bytes;
- project data;
- translation memory state;
- configuration;

SSMT must produce identical normalized results and patch outputs.

Do not allow filesystem iteration order, map hash order, locale, timezone, or random values to affect output.

---

## 12. Testing

Every parser, builder, compatibility rule, or public behavior change needs tests.

Compatibility fixes require:

- fixture;
- focused regression test;
- module test pass;
- full build;
- corpus rerun when applicable.

Repeated extraction of identical input must produce identical normalized output and extraction counts.

---

## 13. Performance

Performance optimization follows measurement.

Do not weaken correctness for speculative speed.

Prefer:

- bounded I/O;
- streaming for proven large-file hotspots;
- incremental reprocessing;
- deterministic caching.

No SSMT feature may add Starsector runtime hooks or startup overhead.

---

## 14. Dependencies

Keep third-party dependencies minimal.

A new dependency requires an explicit justification and approval.

Do not add a dependency to solve a problem already safely handled by the JDK or an existing library.

---

## 15. Documentation

Documentation changes are part of the same development cycle as implementation.

At minimum:

- `SESSION.md` reflects current state;
- `BUGS.md` reflects current open/resolved bug status (status only — link to the files below for detail);
- `DECISIONS.md` records architecture changes;
- `TEST_PLAN.md` records regression coverage;
- `REAL_MOD_COMPATIBILITY.md` records observed ecosystem behavior;
- `ROADMAP.md` reflects milestone state.
