# AGENTS.md: Project Go agent contract

Version 1, 2026-10-14. Binds every agent working in this repository (Claude, Codex, local models). `CLAUDE.md` only points here.
The architecture boundaries in `ARCHITECTURE.md` are part of this contract; where this file is shorter, that file governs.

## 1. What Project Go is, and where it is going

Project Go (SSMT) translates Starsector mods, mainly Chinese to English, into a separate translated copy or in place on a copy
the user chose, offline first. It implements BridgeForge's translation method (P7, schema v1) and keeps conformance with it.

Direction (details: `docs/ROADMAP.md`, `docs/AUTOMATION_PLAN.md`):

1. Never lose data: atomic in-place apply with a snapshot and restore.
2. Nothing silently missed: every file kind the game shows text from is covered, and every leftover unit has a reason.
3. The tool, not the reviewer, finds identifiers and lookup keys; linked values are translated together or not at all.
4. Approval by rules: a glossary built from the mod and the translation memory, per-unit policy approval, ranked suggestions.
5. Gates that run themselves: candidate archive audit, runtime evidence from BridgeForge run records, GUI checklist screenshots.

## 2. Read first

1. `ARCHITECTURE.md` (module and safety boundaries), `docs/ROADMAP.md` (phases, the "Protocol for future revival attempts", P7, P8).
2. `docs/AUTOMATION_PLAN.md` for checks, coverage and automation work.
3. `USER_GUIDE.md` and `AUTO_GUIDE.md` for user-facing behaviour you might change.
4. `docs/BRIDGEFORGE_METHOD_CONVERSION.md` and `docs/P7_IMPLEMENTATION_STATUS.md` before touching the shared method.

## 3. Non-negotiables

- **Never write into the user's source mod** except through the in-place apply path on a copy the user selected, after the
  source-hash check. Original archives and trees are preserved; hashes are recorded before analysis.
- **Bytecode is read, never run**: no class loading or definition, no `Class.forName`, constructors, static initializers or
  reflection on mod code. Jars are read as zip archives and rewritten entry by entry with exact-original-text checks.
- **Identity units are protected.** A unit classified IDENTIFIER is never translated; a LINKED group is translated as one or not
  at all. When in doubt, protect it and report it.
- **Offline first.** Extraction, validation, project handling and builds work with no network. AI providers return drafts; AI
  never fixes source automatically. A draft becomes approved only by a person or by the policy rules (owner decision 2026-10-14:
  `TEXT` units only, every validator passing, marked `POLICY_APPROVED`, reversible). Sending strings to a provider happens only
  when the user configured one (opt-in, off by default).
- **Evidence over claims.** A successful extraction, compile or main-menu boot is not a compatibility or release claim. Gates
  record `PASS`, `FAIL`, `NOT_TESTED` or `NOT_APPLICABLE` with evidence; a BridgeForge live run record counts as runtime evidence
  only when its mod hash matches the candidate (owner decision 2026-10-14).
- **Real-mod evidence first.** Every newly observed format or failure becomes a minimal failing fixture and a detector before the
  fix (P3, Phase 7 principle). Fixtures are synthetic or minimal excerpts; never commit mod or game files.
- Never commit translated mods, user translation memories, or anything identifying a user's machine.

## 4. Engineering rules

- Java with Gradle; Checkstyle, SpotBugs and `-Xlint:all -Werror` are part of the build. Run `gradlew build` (or `gradlew.bat build`
  on Windows) before committing; add tests to the module that owns the behaviour (`ARCHITECTURE.md` module boundaries).
- Conformance with BridgeForge: when a behaviour must match BridgeForge's method, add the case to
  `fixtures/translation-conformance/` (which keeps its own line endings) so both sides are held to it. A change to the schema-v1
  exchange format needs the same change in BridgeForge, made under BridgeForge's contract.
- Windows launch: keep the UTF-8 argfile launcher and quoted `JAVA_HOME`; `verifyWindowsLauncher` and `verifyNativeUnicode` must
  keep passing. Non-ASCII paths and a `JAVA_HOME` with spaces are supported and tested.
- Repository hygiene (`MAINTENANCE.md`): fetch and inspect `git status --short --branch` before changes, preserve unrelated edits,
  run `git diff --check` and check Markdown links after editing docs. Another agent may be working here: use a branch and check the
  tree is clean before starting.
- One logical change per commit, conventional subject, body ends with the build/test result; mark the roadmap item with the tests
  that cover it. Label older receipts by checkpoint date; keep unverified visual, live-game, rights and release gates explicit.
- Publish only from a green exact-commit workflow, and verify the remote release and downloaded asset hashes before calling it done.

## 5. How to work

Continue through the planned order without asking after each step. Ask the owner only for a decision that changes what is
written to a user's mod, what is sent off the machine, what counts as approval or evidence, or anything irreversible or
outward-facing (tagging, publishing, pushing). Report in plain language: what was found, the evidence, what changed, what is
still `NOT_TESTED`.

## 6. Sister projects

| Project | Relationship |
|---|---|
| BridgeForge (`Documents/Project BridgeForge`) | reference implementation of the translation method (`translate-export`, `translate-apply`); its live run records (`In operation/_live/<TESTID>.json`) can supply runtime evidence; its `docs/RC8_BEHAVIOUR.md` holds game facts |
| Salvor, VoidSmith | unrelated to translation; no shared data |

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

When the user types `/graphify`, use the installed graphify skill or instructions before doing anything else.

Rules:
- When graphify-out/graph.json exists, run `graphify query "<question>"` before your first grep, search or source-file read for a codebase question, and put this rule in every subagent prompt that explores code. Read raw files only for lines the graph or an earlier read already located. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- Dirty graphify-out/ files are expected after hooks or incremental updates; dirty graph files are not a reason to skip graphify. Only skip graphify if the task is about stale or incorrect graph output, or the user explicitly says not to use it.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
- A committed graphify-out/graph.json is rewritten whole on every build, so a large diff to it in a pull request is expected. Do not flag it as churn or suggest skipping the graph rebuild or its hooks.
