# Project Go 0.8.0-rc.2

Prepared 2026-10-09. This version increments the earlier 0.8.0-rc.1 release;
historical release assets and receipts remain unchanged.

## Changes since the published rc.1

- BridgeForge schema-v1 translation interoperability, tolerant span-based
  discovery/application and shared conformance fixtures.
- Read-only extractor comparison with source-hash and observed-unit boundaries.
- Review Changes, remembered installation, operation stages and cooperative Cancel.
- Installed-copy quality evidence and subsequent file-byte verification.
- Diagnostic export and explicit game-test instructions.
- Native Auto Unicode command arguments and packaged regression coverage.
- Distribution checksum freshness verification and refreshed workflow guides.
- Portable Windows documentation includes the license and linked documentation.

## Live testing

The owner confirmed on 2026-10-09 that the latest Project Go version had already
been live-tested, including save/reload. This is owner-reported acceptance, not
an automated or agent-observed game test. The mod candidate, executable hash,
game version and observation logs were not supplied. This confirmation does not
establish every candidate's compatibility, native chooser/drop acceptance or
third-party redistribution permission. No further game test was performed for
this version-only packaging iteration.

## Distribution

`Project-Go-0.8.0-rc.2-windows-x64.zip` contains self-contained Project Go and
Project Go Auto application folders plus documentation. Extract before launching
`Project Go/Project Go.exe`. Java installation is unnecessary for this bundle.
The applications are unsigned. Verify the adjacent `.sha256` file before use.

Separate `ssmt-cli`, `ssmt-gui` and `ssmt-auto` ZIPs are Java distributions and
require JDK 25. `SHA256SUMS` covers these three archives. Native Windows metadata
is numeric `0.8.0`; application JARs and archive names retain `0.8.0-rc.2`.

Source commit identity is embedded in application JAR manifests. Local packages
are not a GitHub Release until separately published. Original mods remain
read-only; generated translated copies are for personal use.

## Local validation

The rc.2 build passed on 2026-10-09: 610 tests with zero failures/errors,
Checkstyle, SpotBugs, Windows CLI launcher verification, GUI/Auto native smoke
tests, native Auto Unicode exact-output/source-hash verification, SBOM generation,
distribution archive checks, release metadata and checksum verification.
The first packaging pass executed 88 of 121 tasks; 33 were up-to-date.
This is local validation; hosted CI and release publication are separate evidence.
