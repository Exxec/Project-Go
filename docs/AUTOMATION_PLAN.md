# Project Go automation plan: less hand work in translating a mod

Owner request 2026-10-14 (made in a BridgeForge session): a roadmap plan for further automation, fixes, checks and anything that
reduces hand work, written so any AI can implement it. Status 2026-10-18: every item was implemented on `main` (per-item status lines below; details, tests and deviations in the "Automation plan" list of `docs/ROADMAP.md`). The text below is the original spec and is kept as written, so command names and wording in it can differ from what was built. Each item names the classes to touch, the behaviour,
the tests and when it is done. An implementer needs this file, `docs/ROADMAP.md`, `ARCHITECTURE.md`, `USER_GUIDE.md` and the code.

## 1. Evidence (measured 2026-10-14 on `780606d`, working tree clean)

| Fact | Value |
|---|---|
| Version | `0.8.0-rc.3` (`gradle.properties`) |
| Modules | core, scanner, extractor, tm, validation, patcher, ai, gui, project, cli, auto |
| Tests | 601 `@Test` + 2 parameterized (last documented run 610); Checkstyle, SpotBugs, `-Xlint:all -Werror` |
| CI | `build.yml` Windows + Ubuntu (`gradlew build`, native smoke, `verifyNativeUnicode`, SBOM, checksums); `release.yml` |
| Roadmap | P0-P2, P4, P7 done; P3 (392), P5 (438, 441), P6 (458, 463), P8 items 5, 10, 11, 12 and sister interop 1-2 open |

Hand work and gaps, with locations (all under `ssmt-project/src/main/java/com/ssmt/project/` unless named):

- **Coverage holes on the CJK path.** `BridgeForgeTranslationService` reads only `.csv`, `.java`, the JSON-like suffixes
  (`JSON_SUFFIXES`, line 25) and jar classes. `mission_text.txt`, other `.txt` and loose `.class` files are not exported, and the
  leftover check uses the same types, so it cannot see them. Legacy injection is skipped when a method document exists
  (`LocalizationProjectService` around 808-812).
- **One unreadable file stops everything.** `LocalizationProjectService.createUsingBridgeForge` (65-72) throws when
  `unreadable` is non-empty; the user must fix the file by hand before any translation starts.
- **Identifier risk is left to the reviewer.** Without a per-mod bytecode allowlist every CJK literal is exported; Mirfak's
  `groupTag` (69 of 72 cells Chinese) may be a lookup key; JSON keys all have role UNKNOWN (P8 item 10).
- **Glossary is empty.** The schema-v1 export always writes `glossary{}` (`BridgeForgeTranslationService` line 43); `GLOSSARY.md`
  is a static 12-term table. `GlossaryConflictAuditor` and `TerminologyConsistencyAuditor` report but curate nothing.
- **Approval is by hand.** API and offline drafts stay unapproved; approval on import is all-or-nothing; fuzzy and conflicting TM
  matches are never applied (`AUTO_GUIDE.md` 105-107). Nightcross prefill left 167 ambiguous units.
- **In-place apply is not atomic.** `BridgeForgeTranslationApply` (183-200) writes each file in place with in-memory rollback; no
  pre-apply snapshot. The `--out` path stages and moves atomically.
- **Gates are manual.** `assurance --template` starts every gate NOT_TESTED; the P5/P6 gates (campaign, combat, save/reload, final
  ZIP audit) and the 14-row native GUI checklist (`docs/GUI_WORKFLOW_ENHANCEMENTS.md` 75-90) are all by hand.
- **Custom dialogue/option files** such as `zgrstuff.csv` need syntax-preserving support (`docs/RESIDUAL_CHINESE_DIAGNOSIS.md` 56-64).
- **Stale text.** `docs/ROADMAP.md` line 654 says the translator needs ASCII paths and no space in `JAVA_HOME`; both are fixed
  (`ssmt-cli/build.gradle.kts` 40-61, UTF-8 argfile launcher and quoted `JAVA_HOME`).

## 2. Rules for the implementer

1. Read `docs/ROADMAP.md` (protocol section, P7) and `ARCHITECTURE.md` first. Never write into the selected source mod except through
   the in-place apply path; source-hash refusal stays.
2. Every new rule gets a failing fixture first (P3's rule: narrowest rule per accepted ecosystem format). Fixtures are synthetic
   or minimal excerpts; never commit mod files or game files. `fixtures/translation-conformance/` keeps its own line endings.
3. A unit is never auto-translated into a lookup key: anything classified IDENTIFIER or LINKED (G2, G3) is either protected or
   rewritten together with all its references, never alone.
4. Build with `gradlew build` (checks include Checkstyle, SpotBugs, `-Werror`). One item per commit, conventional subject,
   body ends with the test result; mark the roadmap item done with the tests that cover it.
5. BridgeForge is the reference implementation of the translation method (P7). When behaviour must match it, add the case to
   the conformance fixtures so both sides are held to it.
6. Another agent may be working in this repo: work on a branch and check the tree is clean before starting.

## 3. Work items

### Phase G0: small fixes (about a day)

Status: Done 2026-10-14. Backups live in `<mod>/.ssmt-backup/<timestamp>/`; `translate restore <mod> [timestamp]` lists snapshots when no timestamp is given.

**G0.1 Atomic in-place apply.** In `BridgeForgeTranslationApply`, write each file to a sibling temp file and `Files.move(...,
ATOMIC_MOVE, REPLACE_EXISTING)`; before the first write copy every file to be changed into
`<mod>/.ssmt-backup/<timestamp>/` with a manifest of SHA-256 hashes; rollback restores from that copy. A `translate restore
<mod> <timestamp>` command restores and verifies hashes.
- Tests: injected failure on the third file leaves all files at their original hashes; restore after a successful apply returns
  the original bytes.

Status: Done 2026-10-14.

**G0.2 Stale roadmap line.** Replace `docs/ROADMAP.md` line 654 with the current state (fixed, with the gate names
`verifyWindowsLauncher` and `verifyNativeUnicode`).

Status: Done 2026-10-14 (`CliGoldenHelpTest`, `.gitattributes`, `config/known-failures.tsv`).

**G0.3 Contract guards.** A golden test of each CLI command's `--help` text hash (picocli `usage()`), with a Gradle task to
rewrite the golden file; `.gitattributes` (LF, batch CRLF, the conformance fixtures exempt); a known-failures file with expiry
dates read by the test runner. Same rules as BridgeForge item 70.

### Phase G1: coverage the user cannot see today (about three days)

The legacy extractors already handle mission text and loose class files (`ARCHITECTURE.md`, Plain-Text Extraction and Bytecode
Safety boundaries); G1.1 and G1.2 bring the same rules and stable keys to the shared method, reusing that code rather than
writing new rules.

Status: Done 2026-10-18. Deviation: the new kinds `text-line` and `class` are accepted by this repository's validator only until BridgeForge adds them; no conformance fixture was added.

**G1.1 Text files.** Add `mission_text.txt`, `descriptor.json`-style `.txt` under `data/missions/*/` and any `.txt` under
`data/strings/` to the shared method as a `text-line` unit kind (one unit per line, protected leading/trailing whitespace).
`translate check` and `leftover_cjk_units` count them. Conformance fixture: one mission folder.

Status: Done 2026-10-18.

**G1.2 Loose class files.** Treat `.class` files outside jars like jar entries (`BridgeForgeClassFile`, referenced
`CONSTANT_String` only, same size cap).

Status: Done 2026-10-18 (CLI, GUI checkbox, Auto). Deviation: no separate lenient retry, the shared readers already are the lenient ones. The GUI checkbox is untested in a live window (NOT_TESTED beyond the GUI checklist harness).

**G1.3 Unreadable files do not stop the project.** `createUsingBridgeForge` continues with the readable files and records each
unreadable file with its parser error in the project and in the GUI's Output quality view (status PARTIAL). Before giving up on a
file, retry with the lenient readers BridgeForge uses for the same formats (raw CSV cells, comment/bareword JSON already in
`BridgeForgeTextUnits`). Build refuses only if the user did not acknowledge the unreadable list (one checkbox / `--accept-unreadable`).
- Tests: a project with one broken JSON file builds the rest; the broken file is listed; `translate check` exits non-zero until
  acknowledged.

Status: Done 2026-10-18 for UNSUPPORTED_TYPE, UNREADABLE, UNTRANSLATED, REJECTED. PROTECTED_IDENTIFIER stays reserved and is not emitted yet.

**G1.4 Leftover classifier.** Every leftover CJK unit after apply gets a reason: `UNSUPPORTED_TYPE` (file kind not covered),
`UNREADABLE`, `PROTECTED_IDENTIFIER` (G2), `UNTRANSLATED` (no translation supplied), `REJECTED` (validator failure). Output in
`translate check --json` and the GUI. Replaces the manual triage in `RESIDUAL_CHINESE_DIAGNOSIS.md`.

Status: Done 2026-10-18 on a synthetic fixture; the real `zgrstuff.csv` syntax is unproven.

**G1.5 Custom dialogue and option CSVs** (`zgrstuff.csv` class). A CSV with no known schema whose cells contain CJK and option
syntax (`$var`, `|` separators, `%s`) is exported cell by cell with the syntax protected by `BridgeForgePlaceholders`; a
fixture from a synthetic file of the same shape.

### Phase G2: identifiers found by the tool, not the reviewer (about three days)

Status: Done 2026-10-18 (roles TEXT, IDENTIFIER, LINKED, plus UNKNOWN; data rules in `unit-roles.json`).

**G2.1 Key-or-text classifier.** New `UnitRoleClassifier` (ssmt-project) assigns each unit `TEXT`, `IDENTIFIER` or `LINKED`:
- `IDENTIFIER`: CSV columns named `id`, `*_id`, `tags`, `groupTag`, `designType` references, `hints`, or any column whose values
  are all found as ids elsewhere in the mod; JSON fields `id`, `*Id`, `tags`; bytecode literals passed to `getString`-style lookups,
  `Global.getSettings().getXXX(String)`, `getMemory().get/set`, `equals` against another CSV's id column.
- `LINKED`: a value that is also a key elsewhere (a `designTypeColors` key and the `design type` cells naming it; a `groupTag` used
  by code). Linked units form a group translated together (G2.2).
- `TEXT`: everything else.
The rules are data (`unit-roles.json`) so new ones need no code. Classifications appear in the export as `role` and in the GUI.
- Tests: one fixture per rule; Mirfak-shaped `groupTag` column used in code becomes `LINKED`.

Status: Done 2026-10-18.

**G2.2 Linked groups.** A `LINKED` group gets one translation applied to every member (key and references), or none. Export puts the
group's members under one entry; import refuses a translation that would make two groups collide.

Status: Done 2026-10-18 as `translate bytecode-audit MOD --out proposal.tsv [--json report.json]`; limits: the call after a literal is a heuristic.

**G2.3 Bytecode residual audit against referenced locations** (`RESIDUAL_CHINESE_DIAGNOSIS.md` 66-84). For each jar CJK literal,
record the methods that load it (from `BridgeForgeClassFile`'s constant references) and classify through G2.1's call rules. Generates
the per-mod `ssmt-bytecode-allowlist.tsv` as a proposal (REVIEW) instead of the user writing it.

Status: Done in part 2026-10-18; P8 item 10 is not closed (a bare key nothing uses stays UNKNOWN until BridgeForge agrees).

**G2.4 JSON key role (P8 item 10).** Close P8.10 with G2.1's rules for keys: a key referenced as a value elsewhere is `LINKED`, a key
under a map of display names (`designTypeColors`) is `LINKED` with its references, other keys `IDENTIFIER`.

### Phase G3: approval with rules instead of by hand (about three days; needs owner decision 1)

Status: Done 2026-10-18 as `translate glossary` / `glossary-approve` and a GUI dialog. Not done: repeat counting through `TerminologyConsistencyAuditor` (plain counting used).

**G3.1 Glossary built from the mod.** Fill the export's `glossary{}` from: approved TM terms that occur in this mod; proper names
the mod itself spells in English somewhere (faction display names, ship names in an English `descriptions.csv` or `mod_info.json`);
and terms `TerminologyConsistencyAuditor` finds repeated more than N times, proposed. The GUI shows proposed terms for one-click
approval. Approved terms are stored per mod and globally.
- Tests: a mod whose faction CSV has Chinese and English names yields that pair; a repeated term is proposed, not approved.

Status: Done 2026-10-18 (`PolicyApproval`, `POLICY_APPROVED`, `project import-ai-response --approve-by-rules`). A one-click revoke button in the GUI is not built.

**G3.2 Policy approval.** Draft translations (AI, API, offline) are approved automatically when all hold: every validator in
`ssmt-validation` passes (placeholders, markers, line breaks); glossary terms are used consistently; no CJK left in the
translation; length ratio within bounds per unit kind; role is `TEXT` (never `IDENTIFIER`/`LINKED`). Approved units carry
provenance `POLICY_APPROVED` with the rule version; others go to review. Per-unit approval replaces all-or-nothing import.
- Update `ARCHITECTURE.md` "AI Boundary" in the same change: AI still returns drafts and never fixes source; a draft may become
  approved only through these rules (owner decision 2026-10-14), recorded per unit.
- Tests: one passing draft approved; each rule, failing alone, sends it to review.

Status: Done 2026-10-18. Deviation: a fuzzy match is only proposed unless `auto_apply_fuzzy` is set in the ranking config.

**G3.3 Fuzzy TM and prefill ranking.** For fuzzy or conflicting TM matches and ambiguous prefill (Nightcross 167), rank candidates
by same file, same column or JSON path, same neighbouring ids, and edit distance; propose the top one with its score; auto-apply
only under G3.2's rules plus score above a threshold set in config.

Status: Done 2026-10-18 with `ai-provider.json` and `--confirm-ai-send`; not run against a live provider (NOT_TESTED).

**G3.4 API round trip in Auto.** `AutoWorkflow` can call a configured provider (`ssmt-ai`) for the remaining strings instead of
waiting for the user to upload JSON to a website, in chunks with progress lines and a checkpoint so a stopped run resumes; then G3.2.
Off unless a provider is configured.

### Phase G4: gates that run themselves (about four days)

Status: Done 2026-10-18 as the command `audit-candidate ZIP --manifest installed-copy.json --ledger LEDGER`, not as `assess --candidate`.

**G4.1 Final candidate ZIP audit** (P6, roadmap line 458). `assess --candidate ZIP`: extract to a temp folder with a size cap, compare
every file hash against the apply manifest, run `translate check` on it, and check the mod id and version. Writes the
PACKAGE_IDENTITY and STATIC_VALIDATION gates in the assurance ledger.

Status: Done 2026-10-18 as `assurance --candidate DIR --import-run RECORD --ledger-out LEDGER` (not an `import-run` subcommand).
Reads BridgeForge's real R1 record (`manifest_id`, `manifest`, `evidence.status` and `evidence.milestones`, `triage`, `jvm_crashes`,
`core_baseline_sha256`), checked against a record BridgeForge's own `write_run_record` wrote for a synthetic tree. Open: the record
has no Java version and no process exit code, which Project Go's runtime capture requires, so every gate stays `NOT_TESTED` with
that reason until BridgeForge writes `manifest.java_version` and `process_exit_code`; SAVE_RELOAD needs reload evidence the
milestones do not provide.

**G4.2 Boot, campaign and save/reload from BridgeForge.** `assurance import-run <BridgeForge run record>`: read a BridgeForge
live run record (`<queue>/_live/<TESTID>.json`, for example `In operation/_live/DIAG-3.json`, written by `bridgeforge accept` and `probe-group record`) and set
AUTOMATED_BOOT, CAMPAIGN, SAVE_RELOAD from its milestones when the record's mod hash matches the candidate. BridgeForge's
`probe-group smoke` and `live_run` do the playing; Project Go only reads the record. Closes sister-interop item 1 locally.
- Tests: a fixture record with matching and mismatching hashes.

Status: Done 2026-10-18 as a harness (`gradlew :ssmt-gui:guiChecklist`, plain JavaFX snapshots, not TestFX); 10 rows AUTOMATED_PASS, 5 NOT_AUTOMATED; the owner has not marked any row, so those stay NOT_TESTED.

**G4.3 GUI checklist screenshots** (P8 item 5). A TestFX run (headless Monocle on CI, real window locally) that walks the 14 rows
and saves a screenshot per row into `build/gui-checklist/`; the owner marks each row from the screenshots. Rows a test can assert
(button enabled, list non-empty) are asserted.

## 4. Order

| Step | Items | Why |
|---|---|---|
| 1 | G0.1-G0.3 | data safety of in-place apply; cheap |
| 2 | G1.3, G1.4, G1.1, G1.2 | hand triage and silent misses |
| 3 | G2.1-G2.4 | prerequisite for safe auto-approval |
| 4 | G3.1-G3.4 | the largest hand cost, once G2 protects identifiers |
| 5 | G1.5, G4.1-G4.3 | release gates |

## 5. Owner decisions (2026-10-14, owner: "follow your recommendations")

| # | Decision | Answer |
|---|---|---|
| 1 | G3.2: approve drafts that pass every rule without a person? | **Yes**, `TEXT` units only, marked `POLICY_APPROVED` with the rule version, reversible from the review view |
| 2 | G3.4: may Auto send strings to an AI provider by itself? | **Yes, opt-in**: only when the user has configured a provider; off by default; the GUI says which provider and how many strings before the first send |
| 3 | G4.2: may a BridgeForge run record count as runtime evidence? | **Yes**, only when the record's mod hash matches the candidate; otherwise the gate stays NOT_TESTED |
