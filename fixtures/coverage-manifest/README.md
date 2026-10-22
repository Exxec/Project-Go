# Coverage manifest contract fixture, version 1

A tiny synthetic mod (`mod/`) and the `ssmt-localization-coverage-1` manifest Project Go exports for it
(`localization-coverage.json`, LF line endings). Not a real mod and not live evidence.

- Project Go's `CoverageManifestContractTest` (part of `gradlew build`) re-exports the manifest from `mod/` and requires it
  to equal the committed file, so a producer change cannot land without regenerating it.
- BridgeForge's `shared-fixtures` CI job checks out this repository and imports the committed manifest against `mod/` with
  its real consumer (`bridgeforge.localization_coverage.observe`), so a change the consumer cannot accept fails there.
- Acceptance means candidate identity MATCH only. MATCH never establishes complete coverage, approval, rights, archive
  identity or runtime proof.

Regenerate (from the repository root, after `gradlew :ssmt-cli:installDist`):

    ssmt-cli/build/install/ssmt-cli/bin/ssmt-cli assess fixtures/coverage-manifest/mod --coverage \
      --coverage-manifest fixtures/coverage-manifest/localization-coverage.json

Then convert CRLF to LF if produced on Windows. The files here keep their exact bytes (`-text`), since the manifest binds
to the mod's SHA-256.
