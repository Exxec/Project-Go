# Scale Benchmark

## 2026-07-26 Windows corpus run

Environment:

- Windows, Temurin 25.0.3, Gradle 9.1.0;
- SSMT 0.2.0;
- 11 local real-mod samples;
- 17,237 files, 1,218,894,637 bytes.

Results:

- SHA-256 and modification-time snapshots before and after: 0 changed files;
- seven ASCII-path mods extracted successfully in 4,346 ms total;
- largest successful input: 3,008 files;
- prior Unicode-safe in-JVM compatibility run: 10 of 11 mods;
- the remaining in-JVM rejection is the intentional malformed `Ture` case.

Four non-ASCII paths could not be benchmarked through the PowerShell/native
launcher because their arguments were corrupted before Java received them.
This is the known launcher-boundary limitation, not an extraction result.

## Streaming assessment

The measured corpus is 1.2 GB in aggregate, while extraction processes bounded
files one at a time. The current 256 MiB per-input budget prevents an
unbounded single-document allocation. No measurement in this corpus justifies
the complexity and semantic risk of replacing the current JSON-like parser
with a streaming implementation. Streaming remains an optimization trigger if
a permission-compatible input approaches the per-file budget or profiling
shows parser memory dominating the process.

Future runs follow the procedure in `MAINTENANCE.md` and must retain source
immutability evidence.
