# P1 workflow convergence plan

## Boundary

Unify the state-transition contract used by GUI, simple CLI, and Auto without
removing Auto's catalog reuse or bounded sibling-response discovery.

## Required shared states

1. Input accepted and source bytes bound.
2. Project refreshed or created.
3. Pending AI response exported.
4. Response identity verified and imported.
5. Complete translated clone published.
6. Failure/cancellation leaves prior source, project, and output intact.

## Migration order

Introduce a project-module transition contract with deterministic guards and
tests. Adapt TranslationWorkflow first, then Auto around its catalog and
response-discovery adapters. Only then verify GUI and simple CLI use the same
contract. Do not delegate Auto directly to TranslationWorkflow before those
adapters exist.
