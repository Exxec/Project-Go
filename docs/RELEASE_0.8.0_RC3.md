# Project Go 0.8.0-rc.3

Prepared 2026-10-09 for GitHub distribution. Includes the features and
owner-reported live/save-reload acceptance recorded in
[rc.2 release notes](RELEASE_0.8.0_RC2.md).

The rc.2 tagged Windows release job failed before publication because the
PowerShell 7 CI parent did not expose Windows PowerShell's module directory to
the launcher regression script. Both Windows launcher verification scripts now
add the system Windows PowerShell module directory before invoking hashing
cmdlets. This preserves the hash and source-immutability checks. Application
behavior is unchanged. The rc.2 tag is retained as historical evidence.

Download `Project-Go-0.8.0-rc.3-windows-x64.zip` and its `.sha256` from GitHub.
Extract the complete bundle and launch `Project Go/Project Go.exe`; its Java
runtime is included. The ZIP also contains Project Go Auto, license and linked
documentation. Separate ssmt ZIPs require JDK 25. Native EXE metadata remains
numeric `0.8.0`; archive and JAR versions are `0.8.0-rc.3`.
