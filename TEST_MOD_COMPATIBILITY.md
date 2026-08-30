# Local Test-Mod Compatibility

This report covers the local samples under `Test mods`. The samples are test
inputs only and are not copied into repository fixtures or release artifacts.
SSMT does not rename or modify source-mod directories.

## Results (2026-07-26)

| Supplied folder | Suggested English display name | Extraction |
|---|---|---|
| `ApproLight 0.8.6RC1` | ApproLight 0.8.6 RC1 | Pass |
| `ApproLightPlus 0.3.9RC1` | ApproLight Plus 0.3.9 RC1 | Pass |
| `AzureFederation` | Azure Federation | Pass |
| `BlueSeaFisher1.3.1` | Blue Sea Fisher 1.3.1 | Pass |
| `FSF_MilitaryCorporation5.1rc1-08` | FSF Military Corporation 5.1 RC1-08 | Pass |
| `Goat_Aviation_Bureau` | Goat Aviation Bureau | Pass |
| `TraverserDesignBureau.-.en.0.2` | Traverser Design Bureau - English 0.2 | Pass |
| `[0.98a][C]势力极客联盟 JKF_1.3.0` | `[0.98a][C] JKF 1.3.0` | Pass |
| `[0.98a][C]弧光设计局[ArcLightBureau]` | `[0.98a][C] ArcLight Bureau` | Pass |
| `[0.98]秘甘智库MIK_v0.7.0` | `[0.98] Mikan Institute of Knowledge 0.7.0` | Pass |
| `【2.2.8b】Moci的随意之作` | `Moci's Casual Creations 2.2.8b` | Fails on malformed source token |

The Moci sample contains `"autofire": Ture` in
`data/variants/team/Moci_AMS_119_GunnerH.variant`. Correcting the source token
to `true` is a mod-author decision; SSMT intentionally reports it.

The final extraction run covered all 11 roots and compared SHA-256 hashes and
modification timestamps for 17,237 files before and after. No source file
changed.

## Compatibility added

- `#` comments, single quotes, trailing commas, and structured versions in
  `mod_info.json`.
- Starsector JSON-like `#` comments and uppercase `TRUE`, `FALSE`, and `NULL`.
- Optional description columns, unnamed auxiliary headers, comment/sentinel
  rows, and stable composite `(id,type)` identities.
- Strict UTF-8 CSV decoding with a GB18030 fallback for legacy Chinese mods.
- Composite CSV reinjection so entries sharing an ID but differing by type are
  patched independently.

The English names above are conservative display-name suggestions. They do not
claim to be official translations, and the original folders were left intact.
