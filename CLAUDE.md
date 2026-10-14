# CLAUDE.md

`AGENTS.md` is the agent contract for this repository: read it first and follow it, together with the boundaries in
`ARCHITECTURE.md`. This file exists only for tools that look for `CLAUDE.md`.

In short: Project Go translates Starsector mods offline into a copy; bytecode is read, never run; identifiers are protected; AI
returns drafts; every gate records PASS, FAIL or NOT_TESTED with evidence. Plan: `docs/ROADMAP.md`, `docs/AUTOMATION_PLAN.md`.

```powershell
.\gradlew.bat build
```
