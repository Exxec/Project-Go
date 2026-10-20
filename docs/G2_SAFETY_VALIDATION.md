# G2 safety validation, 2026-10-19

Scope: G2.1/G2.2/G2.3 safety follow-up to the independent review, based on committed Project Go
`bee22828f9980002ba3a52d254c1fedeb8e60c5e`. No schema change, runtime capture or publication was added.

The linked-group fix was delegated to GPT-6 Luna, then independently reviewed and expanded with edge-case tests by the
parent agent. Bytecode protection and integration validation were performed by the parent agent.

## Changes and regression evidence

- Bytecode constants with any detected PROTECT use remain IDENTIFIER even when another site displays them. All display
  sites of that file/class/text are excluded from the allowlist proposal. `BytecodeCallSitesTest` covers both jar and loose
  classes, scan-order independence, proposal parsing, protected leftovers and preservation during apply.
- Rejected LINKED members fail the operation before any snapshot, staging publication or in-place writes. This includes
  invalid placeholders/option syntax before group propagation, JSON key collisions and text-line newline rejection during
  rewriting. Existing inconsistent-group and group-to-group collision handling remains unchanged.
- `LinkedGroupCollisionSafetyTest` covers collisions with an existing English JSON key, untouched source and prior output,
  absent fresh output, in-place refusal without a snapshot, rejected placeholders and propagated newline refusal.

## Validation levels

| Level | Result | Evidence |
|---|---|---|
| Source review | PASS | Parent reviewed delegated fix and added three edge-case regressions |
| Java compile | PASS in isolated snapshot | Full Gradle build below |
| Static validation | PASS in isolated snapshot | Checkstyle and SpotBugs via full build; final test checks rerun |
| Regression suite | PASS in isolated snapshot | Full build; final focused run of both safety test classes |
| Package checks | PASS in isolated snapshot | Distribution and checksum tasks executed by full build |
| Windows launcher | PASS in isolated snapshot | `verifyWindowsLauncher`, Unicode paths and spaced JDK/install paths |
| Native GUI / real provider | NOT_TESTED | No interactive or external-provider run |
| Live game / save compatibility | NOT_TESTED | LIVE TEST NOT PERFORMED |
| Shared schema alignment / P8.10 | OPEN | No interchange changes in this slice |

Commands run on an isolated archive of the baseline plus these scoped changes:

```powershell
.\gradlew.bat build --offline --console=plain
# BUILD SUCCESSFUL, 1m 53s, 116 actionable tasks
.\gradlew.bat :ssmt-project:test --tests com.ssmt.project.LinkedGroupCollisionSafetyTest --tests com.ssmt.project.BytecodeCallSitesTest :ssmt-project:checkstyleTest :ssmt-project:spotbugsTest --offline --console=plain
# BUILD SUCCESSFUL, 14s, 21 actionable tasks; includes the three tests added during parent review
```

Local logs are retained under `Project BridgeForge/scratch/go-g2-hardening/` as `g2-full-build.log` and
`g2-final-focused.log`. These machine-local paths are not publication evidence.

## Integration boundary

Only the scoped G2 source/tests and this roadmap receipt are transferred to Project Go, after checking that their destination
files have no concurrent edits. Existing uncommitted changes to `AGENTS.md`, `gradle.properties`, `AutoWorkflow.java`, the
untracked `RuntimeEvidenceCapture.java`, handoff/patch files and extracted Gradle artifacts are preserved. The dirty checkout
is not certified green: earlier compilation failures in that unrelated runtime work remain a separate integration blocker.
This receipt validates the scoped safety changes on the committed baseline, not that unfinished runtime implementation.

Next separate roadmap work is schema-v1 alignment with BridgeForge (the first proposed near-term recommendation), subject
to its cross-repository contract and shared fixtures. The owner GUI pass, bound runtime evidence and release decision remain open.
