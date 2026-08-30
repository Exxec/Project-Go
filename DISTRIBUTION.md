# Distribution and Native Packaging

`Last updated: 2026-08-02 by Codex (0.6.0 clone-output distribution warning)`

SSMT release versioning is controlled by `ssmtVersion` in `gradle.properties`
(currently 0.6.0). The release build includes PNG and Windows ICO
application assets and uses the JDK 25 `jpackage` tool without an
additional packaging plugin.

## Application image

```powershell
.\gradlew.bat :ssmt-gui:jpackageImage
```

The host-native self-contained image is written under
`ssmt-gui/build/jpackage/SSMT`. It includes a trimmed Java runtime and does not
require a separately installed JDK.

## Development-testing bundle

```powershell
.\gradlew.bat :ssmt-gui:developmentBundleChecksum
```

This builds and headlessly smoke-tests the application image, then writes a
self-contained Windows x64 ZIP and adjacent SHA-256 file under
`build/development`. The ZIP includes `USER_GUIDE.md`, security guidance, and
the compatibility matrix. It is unsigned and intended for real-world
development testing before installer/signing acceptance.

Generated pristine and translated clone directories contain the original
mod's assets and code. They are local/personal-use output unless the mod author
explicitly permits redistribution; do not include them in SSMT release bundles.

## Installer

```powershell
.\gradlew.bat :ssmt-gui:jpackageInstaller
```

The release installer is EXE on Windows and requires the packaging tools
expected by `jpackage`, including WiX. Linux DEB output is best effort.
macOS is not a release target. Native packages must be built and tested on
their target operating system.

## Optional signing

Signing identities are supplied at invocation time and must not be committed:

```powershell
.\gradlew.bat :ssmt-gui:signNativePackage `
  -PpackageFile="C:\release\SSMT-0.2.0.exe" `
  -PsigningIdentity="CERTIFICATE_SHA1_THUMBPRINT" `
  -PtimestampUrl="http://timestamp.digicert.com"
```

Windows signing uses `signtool`. Linux packages should use the signing process
of their distribution channel. Secrets and private keys are never read from
repository files.

## SBOM and checksums

```powershell
.\gradlew.bat generateSbom releaseChecksums
```

The tasks emit `build/reports/ssmt-sbom.cdx.json` and
`build/distributions/SHA256SUMS`. Archive names include the project version and
checksums are ordered by archive filename.
