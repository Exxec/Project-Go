# Maintenance and Compatibility Policy

## Supported Versions

Until the first stable release, only the newest `0.x` release receives fixes.
Project documents reject unknown future schema versions rather than guessing.
A schema change requires an ADR, fixtures, backward-read tests, and an explicit
migration path before release.

Deprecations remain documented for at least one minor release. Removal requires
replacement guidance and a release-note entry. Stable extraction identities are
not deprecated without an update-reconciliation migration.

## Compatibility Matrix

| Surface | Supported baseline | Verification |
| --- | --- | --- |
| Java | Temurin/OpenJDK 25 | Windows and Linux CI |
| Gradle | Wrapper 9.1 | Wrapper validation |
| Windows | Current GitHub-hosted image | Build and `jpackage` app image |
| Linux | Current GitHub-hosted image | Build/test CI; native package is best effort |
| macOS | Not a release target | No CI or packaging commitment |
| Starsector source | Data-only offline parsing | Owned fixtures and user-supplied smoke tests |
| Project schema | Version 1 | Strict read/write tests |
| OpenAI adapter | Responses endpoint `/v1/responses` | Mock-transport contract tests |
| Gemini adapter | Interactions endpoint `/v1beta/interactions` | Mock-transport contract tests |
| Ollama adapter | Chat endpoint `/api/chat` | Mock-transport contract tests |

## Translation-Memory Recovery

Before database maintenance or a schema migration:

1. Run SQLite integrity verification.
2. Create a staged backup with `SqliteTranslationMemory.backup`.
3. Open the backup and verify integrity.
4. Keep the original until the migrated database passes application tests.

Imports remain transactional. A failed import or backup must not replace the
last valid database.

## Resource Budgets

`RuntimeBudgets.DEFAULTS` defines finite defaults: 250,000 source files,
256 MiB per parsed input, a 30-minute operation timeout, and 10,000 diagnostic
entries. Owners must thread these limits and cooperative cancellation through
new long-running operations. Raising a limit requires a measured fixture and a
review of allocation, timeout, and partial-output behavior.

## Benchmark Procedure

Large-modpack benchmarks use permission-compatible local inputs and record:

- source file and byte counts;
- extracted-string count;
- wall-clock duration;
- peak process memory;
- output fingerprint;
- second-run duration;
- source immutability evidence.

Proprietary source text is never committed with benchmark results.

## Repository Hygiene

Fetch `origin` and inspect `git status --short --branch` before making changes.
Preserve unrelated edits and compare `HEAD` with `origin/main` before publishing.
Run `git diff --check` and check local Markdown link targets after editing docs.

`./gradlew hygieneDryRun` (`.\gradlew.bat hygieneDryRun` on Windows) lists existing
repository-owned build and Gradle cache directories with their byte counts. It
does not delete anything. Keep local acceptance evidence and packaged outputs
until their claims have durable tracked or hosted receipts. Application storage
cleanup is a separate hash-bound workflow described in [USER_GUIDE.md](USER_GUIDE.md).

Keep current entry-point guides aligned with implemented behavior. Label older
implementation receipts by checkpoint date, link follow-up evidence, and retain
unverified visual, live-game, rights and release gates explicitly.
