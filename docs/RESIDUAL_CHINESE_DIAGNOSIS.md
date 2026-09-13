# Residual Chinese diagnosis — 2026-09-13

## Scope and preservation

Read-only inspection requested for three new candidates in
`C:/Users/exxec/Documents/To be translated`. No candidate, archive, translated
output or game installation was modified. The directory contains Blackrock Drive
Yards 0.9.5-cn0.4, Mirfak Parcel Service 0.65 and Mirfak 0.7.2; it does not contain
nightcross.jar or zgrstuff.csv. Those names were found in the older Project Go
Nightcross live-test output. The user's exact 18-file report/new translated-output
paths have not yet been supplied, so neither that count nor those new outputs are
verified here.

Mirfak 0.65 also contains a second mod_info.json under out/production. Do not select
an archive wrapper or bundled production duplicate as the intended mod root by guess.

## Evidence from current product source and private Nightcross output

Current source baseline: local commit 2ef73c832903c88fbecf8556574e132f37f2b754
plus tested but uncommitted CSV preservation changes. The final CSV full build passed
517 tests, all static gates and rebuilt GUI/CLI/Auto distributions. Final documentation
transfer/commit was rejected by the approval service because of its usage limit;
temporary copies remain in BridgeForge .goal-transfer pending an authorized transfer.
No retry, alternate write route or push was attempted after that rejection.

Nightcross inspected output:
`.local/live-tests/_go-live-short/planet-retry-fixed/`.
Selected-entry evidence:
`.local/live-tests/_go-live-short/planet-workspaces/5f05c106501edf288eaf4af686534f069b659d4eae2b535ce5167fc64f5e0938/project.ssmt.json`.

### Weapon CSV: genuine extraction gaps

StandardCsvSchemas selects weapon name plus optional tech/manufacturer,
primaryRoleStr and customAncillaryHL. It does not select customPrimary,
customPrimaryHL, customAncillary, speedStr or trackingStr. The private project has
280 selected weapon entries, 276 with Chinese source, none with Chinese translated
text. Output still contains unselected Chinese customPrimary/customAncillary text,
including armour-removal descriptions with %s placeholders. Those strings were
not available to the AI in this standard export, so a completed response cannot
translate them. CSV formatting preservation is separate and does not add coverage.

New source candidate counts, per column with CJK-bearing cells (includes structural
rows; these counts are not counts of confirmed player-visible entries):

| Candidate | customPrimary | customPrimaryHL | customAncillary | speedStr | trackingStr |
| --- | ---: | ---: | ---: | ---: | ---: |
| Blackrock 0.9.5-cn0.4 | 29 | 6 | 2 | 0 | 0 |
| Mirfak 0.65 | 41 | 3 | 4 | 1 | 1 |
| Mirfak 0.7.2 | 44 | 4 | 4 | 1 | 1 |

Mirfak groupTag also contains Chinese (69/72 cells); do not blindly translate a
potential grouping/lookup key. Confirm consumers and reciprocal references first.
Names/roles/manufacturers containing Chinese in original source folders alone do
not prove output failure: inspect the actual built outputs and selected responses.

### zgrstuff.csv: unsupported custom format, runtime use unresolved

The private project contains zero zgrstuff.csv entries. That nonstandard filename
does not match data/campaign/rules.csv, so it is copied unchanged. Its text and
options columns contain Chinese dialogue and structured option-id/label content.
The first comment marks it WIP. No runtime registration/consumer was established
by the source search in this diagnosis. Classify runtime use/visibility REVIEW,
not dead code and not a compatibility failure. The structured options require
separate syntax-preserving support; never translate IDs or scripts wholesale.

### nightcross.jar: raw scanning is not referenced-string scanning

ClassFileInjector constructs ClassWriter(reader, 0), preserving the old constant
pool while adding replacements. The private project selects 3,797 Nightcross JAR
entries, 574 with Chinese source and none with Chinese translated text. In output
AfterburnerStats.class, a parsed constant-pool inspection found both the original
Chinese manoeuvrability label and its English replacement, plus both Chinese and
English concatenation recipes for max speed. This confirms retained source
constants coexist with translations; it does not independently prove every runtime
reference uses the translation. ClassStringExtractor scans fields, method LDCs and
invokedynamic bootstrap strings, not all UTF8 constant-pool payloads.

Do not remove retained constants merely to make a raw scan green. Compare extracted
referenced locations in output against the translation manifest, distinguishing
unused retained constants, technical identifiers, active untranslated literals,
resources and uncertain visibility. A raw UTF8 decode of arbitrary class bytes can
also yield false CJK matches and was not used as a translation-completeness claim.
Sandboxed javap inspection failed with AccessDeniedException; no bypass or runtime
compatibility claim is made.

## Required follow-up implementation and validation

1. Obtain the actual translated-output paths and the exact 18-file report.
2. Add accepted weapon tooltip fields only after confirming schema/consumer binding;
   preserve placeholders and add synthetic extraction/reinjection regressions.
3. Add explicit unsupported-field/custom-file diagnostics to the normal workflow;
   a completed export must not imply complete mod coverage.
4. Inspect zgrstuff registration and option syntax, then use explicit reviewed
   custom schemas or narrow ecosystem support; no speculative runtime repair.
5. Add a bytecode residual audit against referenced locations and a fixture that
   distinguishes retained pool constants from active untranslated strings.
6. Refresh project extraction and request translations for newly selected entries;
   preserve existing exact source/ID translations and original input hashes.

Deferred today: inspect representative weapon tooltip text/highlights, custom
dialogue/options only if registered, and combat/status text in the final exact
build. Record candidate/output hashes, source/commit/version, scenario, observed
results and screenshots/logs under `.local/checks/residual-chinese-validation/`.
No live/game testing was performed in this diagnosis.

Pending destination for this staged document: Project Go docs/RESIDUAL_CHINESE_DIAGNOSIS.md.

## Subsequent added-input run

The user subsequently added Nightcross under To be translated. The source-safe
Project Go export/import/build and independent referenced-string/output audit are
recorded in NIGHTCROSS_ADDED_RUN.md. That audit confirms zero Chinese-bearing
selected output strings, but the weapon/custom-CSV extraction gaps remain open.
The exact historical 18-file report still has not been provided or validated.

## Implemented diagnostic boundary â€” 2026-09-13

`StandardCsvGapAuditor` now examines recognized standard CSV files after normal
extraction and emits deterministic review findings for a non-selected column that
contains non-ASCII text. It also reports unreadable, oversized, malformed, unsafe,
or ambiguous-header CSVs as unavailable for this review rather than silently
claiming that they have no gap. The normal CLI reports these findings with the
column name and explicitly says the field was not exported.

The audit is read-only. A finding does **not** establish player visibility, a stable
identity, an AI-export entry, or permission to extend a standard schema. In
particular, `groupTag`, `tags`, lookup keys and structured fields remain REVIEW.
The accompanying regression keeps Chinese weapon grouping/tag cells out of both
extraction and opt-in schema suggestions while asserting the source is unchanged.

This closes neither the historical 18-file investigation nor the custom-format,
JAR residual, whole-file-text, or game-visibility work. It makes the current
selection boundary observable so those decisions can be made with evidence.
