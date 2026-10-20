# Translation schema alignment validation, 2026-10-19

Owner continuation: "If able continue same process in next phase."
Scope: the first Project Go near-term recommendation and BridgeForge 77.3's translation-schema slice, after G2 safety
hardening. The remaining recommendations are separate. Schema v1 is retained; no schema-2 migration or runtime change.

## Implementation

- BridgeForge discovers, applies and checks `text-line` and loose `class` units with Project Go's stable ids, contexts,
  referenced constants, size cap, text path/encoding/BOM/whitespace/line-ending rules. Malformed loose classes and
  undecodable supported text are reported unreadable.
- Both consumers validate role/group shape. BridgeForge checks supplied entry identities against source rediscovery,
  protects declared identifiers including glossary fallback, and stages linked copies before publication. Invalid
  placeholders, collisions or omitted references refuse the linked publication in full. Linked in-place application is
  deliberately unsupported in BridgeForge; Project Go retains its existing tested path.
- Two byte-identical JSON fixtures and an extension-profile document are shared. They store only synthetic inputs as
  base64 and verify decoded source/output bytes. The original version-1 18-unit corpus remains unchanged.
- BridgeForge consumes declared annotations rather than claiming Project Go's classifier. Its conservative complete
  same-source check can refuse a group that omits incidental same-text units; this is an explicit review boundary.
  Metadata does not create approval. See `fixtures/translation-conformance/SCHEMA_V1_EXTENSIONS.md`.

Delegation: GPT-6 Luna implemented the bounded BridgeForge text/class support; the parent reviewed it, added strict
identity/role handling and shared fixtures, and independently ran validation.

## Evidence

| Level | Result |
|---|---|
| Source review | Completed for the changed source and delegated implementation |
| BridgeForge targeted tests | PASS: 57 translation, conformance, role, batch and CLI regressions |
| BridgeForge lint | PASS: Ruff on changed Python source/tests |
| BridgeForge full suite | NOT_RUN: no broad-suite or coverage claim |
| Project Go full isolated build | PASS: 712 tests reported, zero failures, one skipped; 116 actionable tasks, 1m 50s |
| Project Go static checks | PASS through full build: Checkstyle, SpotBugs, warning-as-error compilation |
| Project Go package checks / launcher | PASS through full build: distribution/checksum tasks and `verifyWindowsLauncher` |
| Real mods / native owner GUI / game / saves | NOT_TESTED: LIVE TEST NOT PERFORMED |
| Publication | NOT_PERFORMED |

Reproduce BridgeForge's targeted check from its repository root:

```powershell
python -m unittest tests.test_translation tests.test_translation_conformance tests.test_translation_extended_kinds tests.test_translation_extension_conformance tests.test_translation_roles tests.test_translate_batch tests.test_cli_golden tests.test_cli_output_encoding -q
```

Project Go: `gradlew.bat build --offline --console=plain`, on an isolated archive of baseline
`bee22828f9980002ba3a52d254c1fedeb8e60c5e` plus the preceding G2 safety slice and these changes.
Local logs: `Project BridgeForge/scratch/schema-python-validation.log` and
`Project BridgeForge/scratch/go-schema-alignment/schema-full-build.log`.

## Integration and remaining work

Only the schema validator, new conformance test, shared fixtures/profile/README, roadmap update and this receipt are
copied into Project Go after guarding against concurrent edits. Existing unfinished runtime work is preserved and remains
outside the green-build claim. The combined dirty checkout is not certified green by this isolated result.

The next separate recommendation is observed runtime evidence from BridgeForge: record actual Java/exit context and
exercise a bound run. This work does not synthesize runtime success or close that gate. P8.10, real-mod acceptance, GUI
owner review and release decisions remain open.
