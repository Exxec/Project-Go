# Weapon tooltip coverage and honest export scope — 2026-09-13

## Accepted scope

Installed vanilla weapon_data.csv and public WeaponSpecAPI getter signatures
confirm speedStr, trackingStr, turnRateStr, accuracyStr, customPrimary,
customPrimaryHL and customAncillary as tooltip overrides. They are now optional
standard CSV text fields; prior name/manufacturer/primaryRoleStr/customAncillaryHL
coverage stays. Technical groupTag/tags/IDs/stats/control flags are not selected.
No source mod or game files were changed.

WeaponTooltipCoverageTest was run failing first, then verifies all tooltip fields,
exact sources/placeholders, technical exclusions, deterministic extraction and
minimal old headers/sentinel skipping. WeaponTooltipWorkflowTest covers shared
export/import/build, %s/highlight preservation, GB18030 and unchanged technical,
extra-cell and original source bytes.

The unchanged Edmund fixture has six newly selected tooltip strings. Project and
GUI corpus tests retain the historical 187 translations and archived 207-entry
export while explicitly asserting six new ID/source pairs and 213 current entries.
GUI reconciliation adds 26 rather than 20. No fixture archive was rewritten.

## Honest export scope

AiTranslationExchangeService now exports coverageSummary: scope
SELECTED_PROJECT_ENTRIES_ONLY, project/exported entry counts, fullModCoverage
NOT_ESTABLISHED. Instructions distinguish completing exported entries from
complete mod coverage or runtime compatibility. The summary is not an invented
supported/skipped-file inventory or authenticated source authority.

Present summaries must be internally consistent and cannot claim complete coverage.
Counts describe the historical export snapshot: exact-ID/source subset responses
remain valid after current project coverage grows. Legacy exports without a summary
still import. AiCoverageSummaryTest and existing growth/browser-integrity tests
cover these contracts. Initial strict current-count binding and early error ordering
failed existing tests; corrected before the successful final combined run.

## Real Nightcross refresh

The existing workspace now has 12,912 entries: all 12,745 earlier translations
match their previous ID/source/value exactly, and 167 new tooltip entries are blank.
New counts: customPrimary 55, customPrimaryHL 47, customAncillary 16, speedStr 24,
trackingStr 24, accuracyStr 1. No new turnRateStr value was present.
Earlier English output remains intact; no missing translation was invented.
Three existing pending review flags were reported and not silently cleared.

Initial refresh artifact: added run's Nightcross-Tooltip-Gaps-to-English.json,
SHA-256 c9162d84fc98dbad36df10636c52a5dea168de624bc7c18bb9af57dede68c287.
Subsequent export with coverageSummary is recorded in the completion checkpoint.
Current handoff: Nightcross-Tooltip-Gaps-with-Coverage-to-English.json under the
added run root, SHA-256
`6cf5b7b144551742cb9933ebd0a88384a8c16acc46243adfd9ac83e5b20da277`.

## Automated gates and resume

Tooltip-only uncached fresh-profile full check/rebuilt distributions passed 520
tests. Final combined tooltip/summary check and GUI/CLI/Auto installDist passed
in 38s, 102/102 tasks executed and 522 tests, zero failures/errors/skips.
Checkstyle and SpotBugs main/test passed. Existing native-access, Gradle-deprecation
and validation analysis missing-class warnings remain; no warning-free claim.
Source baseline e5122a115eee6a630a51ecf0de57c5ac5ce77c87 plus scoped changes;
configured distribution labels remain RC1, not published immutable RC1 artifacts.

Product bytecode residual auditing, supported-file unselected-field diagnostics,
unknown custom dialogue/options and complete file/JAR coverage remain open.
Auto/shared state convergence, package/build assurance, runtime capture and final
evidence integration remain part of the active full roadmap goal. Native Unicode
argument handling is still unresolved; UTF-8 picocli args are a workaround.
Push remains pending explicit approval; no new release or full promotion.

## Deferred validation today

On the final exact build inspect weapon descriptions, speed/tracking/turn/accuracy
overrides and highlights. Placeholder values must render in the original order;
highlights must match translated phrases. GB18030 mods must load and tags/IDs/stats
remain unchanged. Inspect the selected-only coverage warning in the AI handoff and
invalid-response native feedback without active-state loss. Record source/output/
response hashes, final commit/version, scenario and screenshots/logs under
`.local/checks/weapon-tooltip-validation/`. LIVE TEST NOT PERFORMED.
