# Project Go 0.8.0-rc.1 publication receipt

Published 2026-09-13 UTC as a non-draft **pre-release**:
https://github.com/Exxec/Project-Go/releases/tag/v0.8.0-rc.1

Immutable tagged source: `8e9e55f6f61e9861ba69cd22325142d2b1bbb36b`.
This receipt is a documentation-only follow-up, not a change to the release source.
See [new-work review](NEW_WORK_RELEASE_REVIEW.md) for scope and reproduced bugs.

Reviewed/fixed: source-text-aware lineage conflicts and independent fork identity;
safe advisory registry recovery and locking; explicit CLI conflict choices;
existing output-folder collision protection; Windows short-path-safe recovery.
Preserved source mod identity, original inputs, and failed import/save contracts.

Validation recorded separately:

- Source review: PASS for the changed workflow and output-safety paths.
- Compile: PASS, Java 25, all application modules.
- Automated/static validation: PASS, clean uncached fresh-profile check, 455 tests,
  zero failures/errors/skips, Checkstyle and SpotBugs.
- Windows short-name regression: PASS, 17 affected GUI/preferences/output tests
  with an explicit short-name `java.io.tmpdir`; logs retained with the review.
- Package validation: PASS, archive/metadata checks, native GUI/Auto smoke tests,
  launcher versions, and rebuilt JAR manifest version/commit inspection.
- Dependency evidence: SBOM published with the release.
- Exact-source Windows/Linux CI: [34735938582](https://github.com/Exxec/Project-Go/actions/runs/34735938582), PASS.
- Exact-tag verify/Windows portable/publish CI: [34736179385](https://github.com/Exxec/Project-Go/actions/runs/34736179385), PASS.
- Public asset validation: PASS, freshly downloaded all four ZIPs, checked
  SHA256SUMS/portable checksum, release-source.txt and all 45 embedded application
  JAR manifests against the full candidate version and immutable tagged source.
- Interactive native picker/drag-drop acceptance: NOT PERFORMED.
- Live Starsector/save acceptance: LIVE TEST NOT PERFORMED; no game saves changed.

Public archive SHA-256:

| Archive | SHA-256 |
| --- | --- |
| Project-Go-0.8.0-rc.1-windows-x64.zip | c837c3b5b9434fb84474be3ec570883370785ca023f7e00ed20c3f8b0159d49a |
| ssmt-auto-0.8.0-rc.1.zip | 9110c4552d61de7a7b6a59a161c782fcf504cbbaec723c631d8d7edc9f46c866 |
| ssmt-cli-0.8.0-rc.1.zip | fc2a78aff9c3117267784b828321cd56a887c37c630be11fb42375c62de2c17e |
| ssmt-gui-0.8.0-rc.1.zip | a244865c6a1d4d05035758316893a76eff4332d232874d748e1986a8fff7466b |

Native EXE metadata is numeric `0.8.0`; launcher/JAR/ZIP identity remains
`0.8.0-rc.1`. Windows short-name CI failures on superseded commits were resolved,
not accepted as successful release evidence. Transfer snapshots, phase handoff,
failed/passing regression logs and downloaded assets remain under the separate
BridgeForge `In operation/_attic` review workspace for interruption recovery.

Keep the candidate a pre-release until interactive GUI and representative in-game
translation acceptance are completed and recorded. BridgeForge's independent
roadmap/live gates are not closed by this Project Go release.
