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

## Design-type color key preservation — 2026-09-14

`data/config/settings.json` has a narrow standard schema for
`/designTypeColors/*`. These are JSON **field names**, not values: Starsector uses
each name as the lookup counterpart of a hull, weapon, or hullmod
`tech/manufacturer` value. Project Go exports them as `json-key:` entries and
renames the selected field in place during reinjection, retaining the original
RGBA array and all non-target source bytes.

Before output is created, reinjection rejects a destination key that already
exists in the same object and rejects two requested renames targeting the same
destination. It therefore cannot create a Chinese/English pair. It deliberately
does not infer which side of a pre-existing historical pair to delete; that is a
review decision, not an automatic translation repair.

Repository fixtures cover an extractor-only selection, direct rename,
duplicate-destination rejection with source preservation, and a shared workflow
that translates `夜十字军械` and its matching `tech/manufacturer` to
`Nightcross Armory`. The latter proves output contains one English color key and
no Chinese counterpart. Focused extractor/patcher/project tests and the full
offline `gradlew check` passed. This adds no live-game compatibility claim.

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

## CSV token preservation — subsequent phase

CSV injection now retains the original header and record ranges, replacing only
changed fields with escaped CSV values. Untouched fields, extra cells, short
untargeted rows, comments/sentinels, ignored blank lines, quoted technical cells,
multiline values, mixed line endings and missing final newline remain unchanged.
It uses the same strict encoding/BOM and reread/round-trip guard as JSON output.
Missing target cells, duplicate nonempty headers or duplicate selected row identities
fail explicitly rather than padding, dropping data or selecting the first match.

CsvTokenPatchTest covers these boundary cases and source-byte preservation. A
blank line immediately before a translated row exposed Commons CSV's record-offset
contract: ignored empty lines are included in the character range. The span scanner
now preserves that prefix; the failing case remains in the fixture. Existing tests
were corrected to expect original LF and GB18030 rather than normalization.

The final full uncached fresh-profile check and GUI/CLI/Auto rebuild succeeded in
1m8s with 102/102 executed tasks and 517 tests, zero failures/errors/skips.
Patcher main/test Checkstyle and SpotBugs passed. Build started from local source
checkpoint 2ef73c832903c88fbecf8556574e132f37f2b754 plus the scoped CSV changes.
This is still local-source evidence, not CI or a published release.

## Remaining work and trust limits

- The CSV limitations stated in the earlier JSON phase are superseded by the
  subsequent CSV phase above; advisory findings still do not authorize repairs.
- Whole-file mission text now uses the same strict UTF-8/GB18030 detection,
  round-trip guard, UTF-8 BOM retention, and source reread as token-preserving
  formats. A translation that cannot be represented in the source encoding fails
  before publication. Extracted text excludes the BOM from the translatable unit.
- This does not authorize arbitrary technical JSON pointers or unknown schemas.
- Round-trip verification is not an adversarial atomic filesystem snapshot.
- Existing duplicate-field sources now fail explicitly; no guessed repair is allowed.
- Existing `designTypeColors` Chinese/English pairs are preserved and require
  review; importing a translation that would create another pair fails safely.
- No GUI/live-game validation or full-release promotion was performed for this phase.

`ClassFileInjectorTest` independently proves a translated JAR changes the targeted
class string while a non-target resource entry remains byte-identical. Together with
the JSON, CSV, and whole-file fixtures, the current supported reinjection formats now
meet the source-format preservation gate. New ecosystem formats still require their
own failing fixture and narrow acceptance review.

## Deferred validation today

Use the final rebuilt prerelease to translate a representative permissive JSON mod;
verify preserved comments/technical values and that the game accepts the retained
dialect. Include a GB18030 fixture and confirm stale input or ambiguous duplicate keys
produce a useful native error without replacing active work or source files.
Repeat with CSV containing extra cells, comments, short sentinel rows, multiline
quoted text and GB18030; expected output retains technical cells/row shape/encoding
and loads in game. Duplicate identities/headers must fail without state loss. Store
screenshots/logs and hashes beneath `.local/checks/reinjection-validation/`, recording
the final exact commit/version and fixture identity rather than an earlier build.
For `designTypeColors`, inspect the final translated mod's manufacturer labels and
color map together: every translated manufacturer must have exactly one matching
key, color arrays must be unchanged, and a pre-existing duplicate must show a
safe import failure without modifying source or active work.
