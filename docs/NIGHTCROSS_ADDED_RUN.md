# Added Nightcross Project Go run — 2026-09-13

## Input and build identity

Source: `C:/Users/exxec/Documents/To be translated/夜十字军械/Nightcross`.
Nightcross 2.1.4 declares Starsector 0.98a and jars/nightcross.jar.
Added archive SHA-256:
`a6baabc3c935c99cf881312f738fa044bc7b8b6fed6fbf9ed569e77e3fe2b65b`.
Observed source inventory: 2,374 files, tree fingerprint
`4f5552b3a6b1aec13ec15046d99337975f5cade6a05447a34fe3a8598f19d655`.

Development distributions were rebuilt by the successful 517-test uncached check
from local source checkpoint 2ef73c832903c88fbecf8556574e132f37f2b754 plus the
CSV preservation changes. They retain configured 0.8.0-rc.1 labels, but are not
the immutable published RC1 artifacts. No new published-release claim is made.

## Normal workflow and observed results

App-owned run root: `.local/live-tests/nightcross-added-20260913/`.
Project Go's normal translation facade exported 12,745 entries to
Nightcross-Translate-to-English.json using a separate fresh workspace/profile.
Read-only assessment with coverage, CSV structure, JAR inventory and source manifest
reported UNCHANGED_OBSERVED_BYTES_AND_METADATA; no source modifications were made.

The existing planet-types-response.json matches every current ID/source, target
language and entry-ID digest (12,745 entries, no blank or mismatched translations).
Its SHA-256 is
`9370b70a20b6560db2cc70b7fb01c8f43930ced0af028fc8d82552bb1ae2ff93`.
Direct import rejected historical provenance metadata without changing active work.
RebaseResponse copies only verified translation values into the current export
structure; all other fresh structural fields remain. Project Go then accepted the
rebased Nightcross-English-response.json and built all 12,745 selected translations.
No new AI response was invented and no import validation was weakened.

New independent output:
`.local/live-tests/nightcross-added-20260913/outputs/Nightcross-English/`.
Do not replace the earlier known-good output or original with this development copy.

## Independent audit

AuditNightcross uses the actual Project Go extractors and scanner inventories.
Machine-readable local evidence: the run root's audit.json.

- Source bytes still match the captured tree fingerprint; 2,374 source files.
- Output contains 2,375 files; no missing source files.
- 736 changed files are declared translation targets or regenerated mod_info.json.
- No undeclared changed files. Only extra file is the intentional build fingerprint.
- All 12,745 selected output values exactly match the imported response.
- Zero Chinese-bearing selected/referenced output strings; this is NOT exhaustive
  file coverage and not runtime/game validation.

## Known coverage and launcher limits

Weapon CSV: 280 selected entries, while customPrimary/customAncillary and related
tooltip fields remain unselected. zgrstuff.csv is UNSUPPORTED with zero selected
entries; text/options remain unchanged and runtime registration remains REVIEW.
nightcross.jar has 3,797 referenced selected strings. Raw class constant-pool scans
can retain old Chinese alongside translated English and are not runtime evidence.
See RESIDUAL_CHINESE_DIAGNOSIS.md for distinctions and follow-up implementation.

Direct Windows native arguments, including batch and direct java.exe invocation,
lost Chinese pathname characters in this environment. UTF-8 picocli argument files
with portable forward-slash paths worked without renaming/moving source. This
workaround is recorded, not represented as a native Unicode-path defect fix.

CSV advisory assessment: 24 IDENTITY_NOT_ASSESSED and 23 BLANK_IDENTITY findings.
These remain advisory; comments/custom schemas do not become automatic repairs.

## Deferred validation today and resume

No live Starsector test was performed for this output. Once missing coverage is
implemented and the exact final prerelease rebuilt, inspect weapon descriptions
and highlight placeholders, registered custom dialogue/options, status/combat text,
fresh campaign names/types, then save/reload. Record exact build/commit/version,
source/output hashes and screenshots/logs under
`.local/checks/residual-chinese-validation/`. Test native Unicode input without the
argument-file workaround. Names not observed remain NOT_TESTED.

Do not claim full coverage or release readiness until the weapon/custom-schema and
other roadmap implementation gates are closed. Push remains awaiting explicit
approval; runtime/rights/authority gates stay separate.
