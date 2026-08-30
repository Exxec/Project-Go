# Security Policy

## Reporting

Do not open a public issue for a suspected vulnerability involving source-path
escape, arbitrary process execution, credential disclosure, malicious plugin
activation, or generated-patch publication.

Report it privately through the repository host's security-advisory feature.
Include the affected version, operating system, minimal reproduction, impact,
and whether source-mod files were changed. Do not include third-party mod
content or credentials.

## Supported Boundary

SSMT parses untrusted data but does not execute mod classes. Source mods are
read-only. Generated patches contain localization artifacts only. Plugin worker
process isolation is a reliability boundary and is not represented as a complete
Windows security sandbox.

Credentials are accepted through environment-variable references. Diagnostic
exports omit translation content and redact path- and secret-like values.

## Release Evidence

Release candidates must pass the full build, Checkstyle, SpotBugs, tests,
three-platform CI, application-image construction, dependency review, SBOM
generation, and SHA-256 checksum generation. Signing keys remain outside the
repository.
