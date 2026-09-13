# Reinjection implementation checkpoint — 2026-09-13

## JSON token preservation

StandardFileInjector now validates original pointer/text matches as before, then
JsonTokenPatch replaces only matching JSON string token spans. Comments, whitespace,
CR/LF spelling, untouched quoted strings, numeric literals, unquoted field names,
trailing commas and UTF-8 BOM remain unchanged. UTF-8 or GB18030 is detected strictly;
the complete original must round-trip in that encoding before output is created.
Translations are escaped double-quoted JSON strings in the same encoding. Original
files are never written. Duplicate JSON fields/replacement keys fail rather than
selecting an ambiguous occurrence. The source is reread and compared before patching.
The parser retains the previous first-root contract: source suffixes after that root
are preserved, not interpreted as another JSON document. This is not a strict
whole-file validity certification; the Edmund regression exposed this compatibility
case during the initial combined run and it is now covered by the token fixture.

Regression fixtures cover array indices and escaped pointer names, multiple targets,
UTF-8 BOM/comments/CRLF/numeric spelling, GB18030 non-target bytes and stale/duplicate
field rejection. Three existing assertions now use a permissive reader because output
deliberately retains the original Starsector dialect instead of normalizing it.

Automated module validation: all 46 patcher tests, Checkstyle main/test and SpotBugs
main passed. The final whole-project uncached check and GUI/CLI/Auto distribution
rebuild passed 514 tests, zero failures/errors/skips, all Checkstyle/SpotBugs gates
and 102 executed tasks in 1m13s using an initially absent profile. Command/profile
and remaining warnings are recorded in NON_VALIDATION_COMPLETION.md. This is not
immutable release evidence.
The first combined run failed the Edmund first-root compatibility fixture and the
new CSV test-helper SpotBugs null-parent warning. The next run passed regressions
but retained that warning. Both causes were addressed before the final rerun;
failed attempts must not be cited as green full-suite evidence.

## Remaining work and trust limits

- CSV injection still reconstructs normal rows, can discard extra cells and normalizes
  encoding/header/line separators. Advisory CSV structure checks do not fix that.
- Whole-file text injection still emits UTF-8. Its intended whole-file replacement is
  distinct from token-only preservation; reconcile encoding contract separately.
- This does not authorize arbitrary technical JSON pointers or unknown schemas.
- Round-trip verification is not an adversarial atomic filesystem snapshot.
- Existing duplicate-field sources now fail explicitly; no guessed repair is allowed.
- No GUI/live-game validation or full-release promotion was performed for this phase.

## Deferred validation today

Use the final rebuilt prerelease to translate a representative permissive JSON mod;
verify preserved comments/technical values and that the game accepts the retained
dialect. Include a GB18030 fixture and confirm stale input or ambiguous duplicate keys
produce a useful native error without replacing active work or source files.
