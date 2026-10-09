# GUI and workflow enhancements

P8 was approved on 2026-10-08. The roadmap separates implemented automation
from native visual acceptance, game behavior, rights and release publication.

## Implemented workflow

- Install English Copy uses the existing validated remembered destination.
  Other actions > Install to a different destination opens the chooser explicitly.
  A missing, linked or unusable remembered directory falls back to the chooser.
  Successful publication updates the preference; failed publication remains a
  recovery attempt, not an installed result.
- Review Changes shows pending source changes, prior source/translation,
  removed-entry history, missing translations, protected-placeholder failures,
  and JSON-key role advice. Viewing the report does not approve entries or
  erase history. Corrected response import resolves current pending findings.
  Readiness and the normal build gate use builder-compatible placeholder rules.
- JSON object keys have `UNKNOWN` roles unless candidate-specific consumer
  evidence establishes displayed-label or lookup-identifier semantics. The
  review advises checking their use; no inferred role changes direct schema-v1
  discovery/application. The shared key fixture verifies unchanged interchange.
- Output quality shows remaining observed CJK units grouped by file, and
  unreadable inputs. Zero findings is observed-unit evidence, not complete text
  coverage. English/non-CJK leftovers, excluded trees and unsupported formats
  are outside this detector's scope. Published output can still need review.
- Publication records an internal regular-file inventory and quality report in
  `installed-copy.json`. Verify Installed Copy compares paths and SHA-256 values,
  detects added/missing/changed files, rejects links, and never changes output.
  Inventory evidence unavailable or failing to persist remains unknown; it does
  not undo an already-published copy or make quality PASS.
- Operation stages and an activity indicator replace an unexplained busy state.
  Cancel is cooperative during archive preparation, CJK discovery, preflight,
  and before workspace/output publication. An individual legacy extraction or
  inventory pass may finish before the next cancellation boundary. Once
  authoritative publication starts, Cancel is disabled and publication finishes.
  Saved state and published output remain intact after pre-publication cancellation;
  reusable input caches may have been prepared.
- Advanced > Tools > Extractor Comparison chooses a mod folder and BridgeForge
  schema-v1 export, then groups differences by file and reason using the existing
  comparison service. Hash mismatch and unreadable-input dispositions stay visible.
- Other actions > Export Diagnostics writes a new external JSON document with
  version/commit, last failure operation/detail, current status, and only explicitly
  selected logs. Cancelling optional log selection exports without logs. Logs
  are capped at eight files of 1 MiB each, included byte-exact as Base64 with hashes.
  Existing reports and source-contained destinations are refused. Development
  class directories have `UNKNOWN` packaged identity rather than a guessed commit.
- Test Instructions explains separate campaign, representative content/combat,
  and applicable save/reload checks. No successful byte check implies runtime PASS.

## Native Auto Unicode

The packaged Windows Auto launcher opts into original UTF-16 argument recovery.
Java 25 FFM calls the Windows `GetCommandLineW`, `CommandLineToArgvW`, and
`LocalFree` APIs. The native allocation is released, argument count and string
length are bounded, and this path is enabled only for the configured native Auto
executable. Ordinary Java/distribution invocations keep their supplied arguments.
The package enables native access explicitly; no guessed repair of `?` paths occurs.

Reference: [Microsoft's CommandLineToArgvW contract](https://learn.microsoft.com/en-us/windows/win32/api/shellapi/nf-shellapi-commandlinetoargvw).

`:ssmt-auto:verifyNativeUnicode` exercises the actual executable with a CJK,
spaced, ampersand-containing source folder, then its `mod_info.json` path. It
imports an arbitrarily named schema-v1 response, verifies all shared expected
output files byte-for-byte, and verifies original source hashes. This proves
native command-argument handling; an actual Windows Explorer drop remains visual
acceptance. Branch CI now runs this gate and uploads source/package chain reports.

## Visual acceptance checklist

Use the rebuilt native GUI and a repository fixture copied into a disposable
directory. Keep game installations and original mods read-only. Record executable
version/commit, environment, steps, observations and evidence location. Every
row starts `NOT_TESTED`; mark PASS only from actual observation.

| Scenario | Expected behavior | Observed status |
| --- | --- | --- |
| First chooser and ZIP/folder/metadata drop | Correct mod appears; one active project. | NOT_TESTED |
| Unicode chooser/drop | Exact selected source loads without an ASCII copy. | NOT_TESTED |
| Renamed response picker/drop | Valid response imports by identity. | NOT_TESTED |
| Wrong response or malformed input | Clear finding; active project and source retained. | NOT_TESTED |
| Changed source with prior translations | Review shows old/new text; stale translation is not installed. | NOT_TESTED |
| First install, then repeat install | First chooser; later install uses remembered destination. | NOT_TESTED |
| Explicit different destination | Chooser appears; new location remembered only after success. | NOT_TESTED |
| Missing/linked remembered destination | Chooser fallback; no silent wrong-location install. | NOT_TESTED |
| Cancel during preparation/validation | Task stops at a safe boundary; previous project/output retained. | NOT_TESTED |
| Publication begins | Cancel disabled; commit finishes and result is shown. | NOT_TESTED |
| Output quality and subsequent byte edit | Findings visible; verification names changed file. | NOT_TESTED |
| Advanced comparison | File/reason groups and incomplete/hash-mismatch states visible. | NOT_TESTED |
| Diagnostics without and with selected logs | Only selected logs exported; overwrite refused. | NOT_TESTED |
| Native Auto Explorer drop | Unicode folder and ZIP reach the same packaged workflow. | NOT_TESTED |

## Remaining gates

Local validation on 2026-10-08: all 610 tests passed with zero failures, errors
or skips. Fresh-profile uncached `build`, CLI install, GUI/Auto native smoke,
native Unicode acceptance, SBOM, checksums, archive scan and release-metadata
checks passed with all 118 tasks executed. Local log: `build-p8-final.log`.
The execution environment restored the standard Windows PowerShell module path
for the existing hash check. Native-access/Gradle deprecation/static-analysis
missing-class warnings remain visible. These logs are local development evidence.

Implementation commit `55bf2f8` also passed Windows and Ubuntu
[CI run 37876311841](https://github.com/Exxec/Project-Go/actions/runs/37876311841).
Both jobs uploaded branch evidence; Windows exercised the packaged native gates.
This is branch validation, not a tagged release or visual/game acceptance.

- Candidate-specific evidence must establish whether particular keys are labels
  or lookup identifiers; schema-v1 parity is not semantic approval to rename them.
- Coverage manifests retain unknown total supported strings; absence of entries
  never establishes complete localization. Sister-tool consumption remains a
  separate acceptance step.
- Branch CI and package hash chains do not establish tag/release publication,
  downloaded asset provenance, rights or live readiness.
  Final commit packaging exposed an incremental-build gap: `releaseChecksums`
  had no archive inputs and could keep stale hashes after ZIP bytes changed.
  It now declares those inputs, and `verifyReleaseChecksums` runs under `check`
  to compare the entire checksum listing against actual current ZIP bytes.
- Game scenarios, persistent-state compatibility, approved upgrade testing,
  final candidate-to-ZIP audit and redistribution rights require a selected
  candidate and actual observations. They remain P5/P6 gates.
