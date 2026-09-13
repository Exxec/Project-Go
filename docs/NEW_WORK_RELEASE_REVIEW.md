# New-work release review — 2026-09-12

Input: Project Go main f49e63ed95f8b4e75017bd32a554d930969ae725 plus the owner's
23 changed/new source and test files. Review in an isolated worktree, retain the
original changes, and verify the original snapshot before applying reviewed fixes.

Baseline: full check passed after resolving an uncached logback test dependency.
Offline failure was dependency availability, not a source/test regression.

SAFE narrow fixes under review:

- Same-id lineage compares entry locations rather than protected source text;
  different sources with identical keys can be merged without a choice. Include
  source content in fork identity and require authoritative saved-project evidence.
  Preserve normal same-path updates and rename/resume behavior with regressions.
- A malformed advisory forks field can fail after the project publication. Ignore
  invalid bounded advisory records; losing advisory data must not lose saved work.
- Fork loads update a shared lineage record without holding the primary lock.
  Serialize per-id registry decisions/updates while retaining fork workspace locks.
- CLI exposes the conflict but no way to resolve it. Add explicit mutually
  exclusive lineage-choice flags, reusing the shared facade instead of guessing.

Retain original mod metadata, readable names, source immutability, failed-import
and failed-save contracts. Do not refactor unrelated code or add translation AI
providers. Interactive native-picker/live-game testing remains a separate gate.

Additional SAFE finding: readable output folders can collide for different mods
with the same display name (or an unmanaged existing copy). Reject normal builds
into an existing destination unless its app fingerprint and source metadata prove
same-id translated output. Preserve all existing target/source bytes on rejection.

Release procedure: regressions first, full tests/Checkstyle/SpotBugs, rebuilt
GUI/Auto/CLI launch/version/smoke/archive/metadata checks, then original-snapshot
verification, transfer reviewed changes, commit/push, and exact-SHA/tag CI before
observable publication. Document every phase and artifacts for interruption.

Validation: all four reproduced lineage failures and both reproduced output
overwrite failures are fixed; full check passed including Checkstyle/SpotBugs.
CLI lineage flags have a facade regression. A normal update at the same source
path still refreshes, discarding stale translations into review/history.

Candidate version: 0.8.0-rc.1. Native EXE metadata is numeric 0.8.0 while JARs,
archives and launcher output retain the full candidate version. Correct the
release workflow's native-version comparison accordingly; its prior full-string
comparison would reject a valid prerelease. Publish as a pre-release while native
interactive picker/drag-drop and in-game acceptance remain unperformed.

Final clean, uncached check: 455 tests, zero failures/errors/skips, Checkstyle and
SpotBugs PASS. Original-input and delivery-patch evidence is retained separately
under BridgeForge/In operation/_attic/go-transfer; it is not a release asset.
Native images and final embedded commit/version must be rebuilt after committing
the reviewed source; an artifact built against the earlier review base is not
accepted as the published candidate.
