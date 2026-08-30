# Plain-Language Diagnostic Audit

Audit date: 2026-08-02. This is a source-level inventory; rendered dialogs and
assistive-technology output still require manual acceptance.

## Standard

Every user-facing failure should say what operation failed, identify the
relevant file/entry/provider when safe, state whether source or output changed,
and give a useful next action. Stable diagnostic codes should be preserved when
available. Raw exception text may supplement this explanation, but must not be
the whole explanation.

## Inventory and result

| Surface | Current pattern | Result |
| --- | --- | --- |
| Project creation, refresh, build, recovery | Operation heading plus domain exception; transactional services protect the source | Partial: many dialogs omit an explicit unchanged-state statement and recovery action |
| Translation and provider execution | Provider/backend and confidence details are reported; failures retain drafts | Partial: generic background-action failures still surface raw exception messages |
| Browser-AI export/import | Privacy confirmation and transactional import validation | Pass structurally; manual wording/layout check remains |
| Schema, OCR, image, font, and plugin tools | Operation-specific headings with paths in many domain errors | Partial: several validation messages lack a next action |
| CLI | Commands generally provide operation/provider details and nonzero exit behavior | Partial: low-level validation exceptions can reach the standard handler without file/entry context |

The audit found one cross-cutting remediation item, recorded as BUG-010 in
`BUGS.md`.
It does not justify weakening transactional validation, protected-syntax
checks, source immutability, or exception chaining. New diagnostics should use
the standard above as an acceptance criterion.
