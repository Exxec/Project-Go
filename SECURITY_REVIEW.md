# Security Review

Date: 2026-07-26

## Reviewed Boundaries

### Plugin archives and workers

- Catalog discovery reads metadata without loading plugin classes.
- Activation occurs in a bounded worker process.
- Worker timeouts terminate the process and have an automated regression.
- Sandbox capability is explicit; process isolation is not called an OS sandbox.
- `REQUIRED` mode fails when the selected OS boundary is unavailable.

Residual risk: Windows has no verified mandatory OS sandbox. Untrusted plugins
should not be enabled there when a strong containment boundary is required.

### Process construction and credentials

- Process commands use argument lists rather than shell command strings.
- AI provider configuration stores environment-variable names, not credential
  values.
- Credentials are resolved only when constructing an optional provider.
- Logs and diagnostic exports do not intentionally include credential values.

Residual risk: provider SDK/HTTP errors must continue to avoid echoing request
headers.

### Project schemas and source paths

- Project, extraction-schema, and plugin schema versions are bounded.
- Source-relative paths reject absolute paths and `..` escape.
- Unknown project versions fail instead of being guessed or migrated silently.
- Custom JSON schemas use exact paths and explicit RFC 6901 pointers.

### Patch publication

- Source/output overlap is rejected.
- Source files remain read-only.
- Complete output is staged before publication.
- Exact source text is verified before reinjection.
- Cancellation before publication leaves no partial output.
- Repeated unchanged builds are byte-identical.

### Release evidence

- CI covers Windows, Linux, and macOS on JDK 25.
- Dependency changes receive repository-host dependency review.
- Release archives are scanned for traversal, duplicate entries, entry-count
  limits, and expanded-size limits.
- A deterministic SBOM and SHA-256 manifest are generated.

## Release-Blocking Findings

No open release-blocking implementation defect was found in the reviewed
boundaries. Manual target-host installer/signing checks and Starsector loading
smoke tests remain required release evidence.
