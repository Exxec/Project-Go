# Extractor comparison

Compare Project Go discovery with a schema-v1 export made by BridgeForge from
the same preserved mod tree:

```text
ssmt-cli translate compare SOURCE_MOD --reference bridgeforge-export.json --out comparison.json
```

The command reads the mod and reference and writes one new report outside the
mod. It refuses an existing report. It does not run Python, change translations,
or apply edits. Use BridgeForge's own export command to prepare the reference;
this keeps its checkout and dependencies outside Project Go's installation.

The report sorts differences by unit ID and includes the file, reason, and
changed protected fields. Reasons are `MISSING_IN_PROJECT_GO`,
`EXTRA_IN_PROJECT_GO`, and `PROTECTED_UNIT_CHANGED`. Protected fields are file,
kind, context, and source text. Translations, glossary, ordering, and optional
metadata do not affect discovery parity.

| Status | Meaning |
| --- | --- |
| `MATCH` | Discovered units agree, reference file hashes match, no unreadable diagnostics. |
| `DIFFERENCES` | Unit differences exist with no detected source mismatch or unreadable diagnostics. |
| `SOURCE_MISMATCH` | Mod identity differs or a reference-hashed file is absent or changed. Differences cannot be attributed to an extractor alone. |
| `INCOMPLETE` | Either export reported unreadable input. Matching observed units cannot establish parity. |

Exit codes: 0 for `MATCH`, 2 for other report statuses, 1 for invalid input or
report-write failure. Hash mismatch takes precedence over incomplete input;
both diagnostics remain in the report. File hashes from both exports are retained.

Schema-v1 exports hash files with discovered CJK units, not every mod file.
`MATCH` establishes observed discovery parity at that boundary. It does not
prove identical full mod trees, complete localization coverage, output behavior,
runtime compatibility, rights, or release readiness. Excluded files and
unobserved strings stay outside this comparison.

The repository conformance export covers CSV, JSON values/keys, Java literals,
and JAR constants. Regression tests cover parity, entry order/translation
independence, missing/extra/changed units, stale or absent reference files,
identity mismatch, unreadable input, report containment, overwrite refusal,
source preservation, and CLI status propagation.

Validation on 2026-10-08: full offline Windows build passed all 600 tests with
zero failures/errors/skips, Checkstyle, SpotBugs, the Unicode CLI launcher gate,
CLI/GUI/Auto distribution builds, and rebuilt native GUI/Auto smoke tests.
The packaged CLI compared the shared export as `MATCH` with 18 units and zero
differences. Tests used an isolated initially absent application-data profile.
The execution environment needed the standard Windows PowerShell module path
restored for `Get-FileHash`. Known native-access, Gradle deprecation, and
static-analysis missing-class warnings remain visible. Logs are local evidence
(`build-roadmap-compare-final.log`), not release receipts or exact-commit CI.
