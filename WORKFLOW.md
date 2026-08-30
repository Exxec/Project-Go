# Development Workflow

## Execution Loop

Every development session follows this sequence:

1. **Design:** Define public interfaces, models, and input/output contracts.
   Record architectural changes in `ARCHITECTURE.md` and `DECISIONS.md`.
2. **Tests:** Add failing JUnit 5 tests for happy paths, edge cases, and failures.
3. **Implementation:** Write the minimum Java 25 code required to pass.
4. **Static Analysis:** Run Checkstyle and SpotBugs through Gradle and resolve all findings.
5. **Documentation:** Document public APIs and update `SESSION.md`.
6. **Review:** Check determinism, source safety, error handling, and unnecessary allocation.
7. **Merge:** Use conventional commit syntax when committing.

## AI Session Protocol

Before generating code, read `WORKFLOW.md`, `AI_CONTRACT.md`, `BUGS.md`,
`SESSION.md`, `PROJECT_MANIFEST.md`, and `STANDARDS.md`. Work one tested
vertical slice at a time and update `SESSION.md` and, if a bug's status
changed, `BUGS.md` before handoff.
