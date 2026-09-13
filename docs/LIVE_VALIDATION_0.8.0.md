# 0.8.0 live validation and follow-up fixes

Published v0.8.0-rc.1 source: 8e9e55f6f61e9861ba69cd22325142d2b1bbb36b.
Main documentation base: e32f1f6949ebf4a262082b110e8f0b57643a7d0a.
Follow-up code in this checkout is not the published RC1 binary. It requires a
new build and release verification before any further publication or promotion.

Private evidence/checkpoints now reside in this project's
.local/live-tests/ProjectGo-live-rc1 (ACCEPTANCE.md, PLANET_TYPE_FIX.md,
INTAKE_FIX.md, RELOCATION.md and logs). Historical logs retain former BridgeForge
paths as provenance, not current launch targets.
Original inputs and main game installations are never edited. Private test rigs
use Java 25 compatibility flags; they are not bytecode-verifier validation.

## Bug classes and covering checks

| Finding | Evidence and root-cause limit | Coverage / status |
| --- | --- | --- |
| LIVE-GO-001 apparent intake inactivity | Failed operation was only reported in inline status; invalid ancestor selection could misleadingly report cache overlap. Exact original native Desktop picker behavior was not reproduced. | ModInputPreparationServiceTest invalid-root guidance; TranslationWorkflowControllerTest active-state preservation; UserDiagnosticTest nested causes; WorkflowFailureReportTest fresh storage/source protection. Real JavaFX error alert, report and re-enabled controls smoke PASS. Native user intake retest pending. |
| LIVE-GO-002 Windows extracted-tree publication denied | Exact Nightcross ZIP failed directory rename under deep cache; temporary busy/content-related handles are supported by isolated rename probes, but lock owner and precise OS cause are unknown. | ArchivePublicationRetryTest temporary/permanent denial, interruption, atomic fallback and concurrent-cache semantics. Two independent fresh deep-cache exact-ZIP loads PASS without short-path workaround. Same rename retry bounded at five seconds, no in-place copy/delete fallback. |
| LIVE-GO-004 unreadable generated names in existing save | Original campaign retained unreadable names; a new translated campaign showed English stations. Exact serialized generation provenance was not traced. | Static response/JAR and ship-name audits; user fresh-campaign report and station screenshot. This is an observed save limitation, not a save migration implementation. No automatic existing-save relocalization promised. |
| LIVE-GO-005 Chinese custom planet-type labels | Proper planet name was English, but planets.json type names were omitted from the export. | StandardJsonFileExtractorTest exact path and /*/name-only extraction; StandardFileInjectorTest parsed structural/source preservation. Nine missing labels added, 12,736 existing translations reused. User localization confirmation PASS after private rig restart. |
| Translated clone publication denied | Windows rename denied on staged translated content; ordinary rename also denied in a controlled copy, later rename succeeded after process exit. Exact lock owner unknown. | PublicationRetryTest bounded same-operation retries, no fallback on denial, interruption and other errors. Existing PatchBuilder rollback tests retained. Real translated build PASS. |

JSON injection preserves parsed values other than selected labels but normalizes
formatting/comments, as before. No unrelated architecture redesign is included.
Local diagnostic reports can contain selected filesystem paths and exception
messages; they are stored locally, not automatically uploaded.

## Live observations and remaining gates

Nightcross is present in Intel/Factions; its name and faction description display
in English. Bottom Intel faction filters are not a complete faction roster, so
absence of a filter button alone does not demonstrate failed faction generation.
Fresh campaign station names and user-reported remaining localization PASS.
User confirms save/reload working. Combat encountered, but names were not
observed: combat-name localization NOT OBSERVED, not a failure or a PASS.
No exhaustive combat or dialogue assurance inferred from these reports.

Original baseline reproduces missing flare/module_hightech_decor hull messages
and MagicLib subsystemInfoKey warning. They are not translation-only regressions;
their causes remain unresolved and are outside this product-tooling fix scope.

## Build evidence

Combined initial check after fixes: 473 tests, zero failures/errors/skips;
Checkstyle/SpotBugs and CLI/GUI/Auto installDist PASS (intake-check2.log).
Exact ZIP SHA256 remains
a6baabc3c935c99cf881312f738fa044bc7b8b6fed6fbf9ed569e77e3fe2b65b.
Intake deep-a and deep-b each load 12,745 entries in independent new caches.
FailureVisibilitySmoke invokes the real asynchronous normal-flow load failure
and checks expanded owned error dialog, guidance, local report and controls.
An uncached check with absent LOCALAPPDATA and development native image build
completed successfully (intake-fresh-check2.log): 115 tasks executed, BUILD
SUCCESSFUL in 1m 22s. Native image labeled 0.8.0-dev-livefix to distinguish it
from RC1; numeric EXE metadata is 0.8.0. It is a development build from a dirty
worktree, not an immutable-source published release artifact.

Next: finish fresh-profile/native verification, transfer only these reviewed
tool changes, user native ZIP/drop/picker retest, then commit/build/CI/hash/version
and publication evidence for a new release. Published RC1 remains pre-release.

## Proper-home relocation checkpoint

All reviewed source fixes transferred to main; existing source changes preserved.
User requested removing Go artifacts from BridgeForge. Thirty-five Go-only items
relocated into ignored .local/, with before/after file counts and byte totals
matching for each item. RELOCATION_MANIFEST.json records exact verified mappings.
Both linked worktrees moved with git worktree move; registrations now point to
.local/worktrees/go-review and .local/worktrees/go-intake-review. No Go worktree,
Go-prefixed history artifact or _go-live-short rig remains in BridgeForge.

BridgeForge phase builds/shared rigs/originals left untouched. Original Nightcross
ZIP and shared Java runtime remain there; independent copies in .local/inputs/
nightcross.zip and .local/runtime/starsector-java25 were hash-verified. Campaigns,
baseline, old mod backup, downloaded assets and historical profiles moved intact.
Active game/development launchers and key probes now use Go's proper home.
Historical preparation/transfer scripts are archival, not launch instructions;
cached project JSON with former paths is evidence, not silently auto-adopted.

Relocated development native smoke/version modes both exit 0: full version
0.8.0-dev-livefix, numeric EXE version 0.8.0. EXE SHA256:
406da0ecce35a30ddd4d77d47539457ab92b367c469ea85f02869533176acd59.
This is a dirty-worktree development build, not an immutable-source release.
An earlier native-check approval was rejected due to approval-service usage;
after static hash/manifest/entry-point checks, normal approval retry succeeded.
Logs relocated-native-* record actual executions.

Main checkout uncached clean check and CLI/GUI/Auto distribution builds PASS:
main-intake-fresh-check.log, BUILD SUCCESSFUL in 1m 28s, 114 tasks executed without
build cache. Suite: 473 tests, zero failures/errors/skips. Checkstyle/SpotBugs PASS.
Native user ZIP/drop/picker retest and exact new release commit/CI/publication
remain separate gates. No commit/push/new release/promotion in this tranche.
