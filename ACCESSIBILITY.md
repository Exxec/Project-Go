# Desktop Accessibility Review

Date: 2026-07-26 (amended 2026-09-01: removed the deleted Image Localization tab's accessible-text bullet)

## Implemented

- Primary project, refresh, review, build, schema, and provider actions
  expose accessible text.
- Translation rows support keyboard selection and multi-selection.
- `Ctrl+S` saves and `Ctrl+F` focuses search.
- Search and review-status filters do not change deterministic project order.
- Text previews wrap and remain selectable without being editable.
- JavaFX layout uses resizable containers rather than fixed pixel window bounds.
- Long patch builds expose an indeterminate progress control and a Cancel action.
- Suggestions are named and require explicit application.
- Validation findings remain textual and are not communicated by color alone.

## Manual Release Checks

Each target-platform application image must still be checked with:

- keyboard-only traversal and visible focus;
- Windows Narrator, VoiceOver, or Orca label announcement;
- 100%, 150%, and 200% display scaling;
- high-contrast system themes;
- long translated strings and bidirectional text;
- cancel and error-dialog focus restoration.

Failures in these checks block a desktop release. They do not permit changing
source-mod content or bypassing validation.
