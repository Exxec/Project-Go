# Replacement Notes

**Status: historical, superseded.** This documentation bundle is based on
the completed Phase 7 compatibility run reported on 2026-07-26. Phases 7
through 10 are now implementation-complete (see `ROADMAP.md`/`CLAUDE.md`);
the "Recommended Codex restart prompt" below is a snapshot of that specific
2026-07-26 handoff and should **not** be reused as a current restart prompt
— its "Continue Phase 7" instruction and "124-test build" baseline both
predate significant later work (ADR-021 through ADR-038 in `DECISIONS.md`).
For a current session's read order, use `CLAUDE.md`'s own "Read order"
section instead. Kept here for history, not deleted, per this project's
convention of preserving rather than erasing superseded records.

## Replace these files

Recommended replacements:

- `AI_CONTRACT.md`
- `ARCHITECTURE.md`
- `DECISIONS.md`
- `ENVIRONMENT.md`
- `STANDARDS.md`
- `TEST_PLAN.md`
- `ROADMAP.md`
- `SESSION.md`

Add:

- `REAL_MOD_COMPATIBILITY.md`

## Keep your existing versions unless Codex changed them later

These did not require a compatibility-driven rewrite in this pass:

- `README.md`
- `PROJECT_MANIFEST.md`
- `VISION.md` / `vision.md`
- `WORKFLOW.md`
- `PLUGIN_API.md`
- `JSON_SCHEMAS.md`
- `GLOSSARY.md`
- `RISK_REGISTER.md`
- `DISTRIBUTION.md`

## Important semantic change

SSMT now distinguishes:

1. compatibility with legitimate ecosystem conventions;
2. unsupported custom-but-valid content;
3. malformed or ambiguous source.

A probable typo such as:

```json
"autofire": Ture
```

belongs to category 3.

SSMT should report it and may suggest a likely correction, but must not silently fix or reinterpret it.

## Recommended Codex restart prompt

After replacing the documentation, start a fresh Codex session and use:

> Read PROJECT_MANIFEST.md, STANDARDS.md, WORKFLOW.md, AI_CONTRACT.md, SESSION.md, ARCHITECTURE.md, DECISIONS.md, TEST_PLAN.md, ROADMAP.md, and REAL_MOD_COMPATIBILITY.md before making changes. Treat the current 10/11 real-mod extraction corpus and 124-test build as the baseline. Do not redo completed compatibility work unless a regression demonstrates a defect. Continue Phase 7 with project refresh/update reconciliation, deterministic rebuild verification, and full workflow acceptance. Preserve the zero-touch source invariant and do not auto-correct malformed source such as `Ture`.
