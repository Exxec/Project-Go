# Opt-In JSON Extraction Schemas

Custom JSON schemas enable visible text in non-standard mod files without
weakening SSMT's conservative defaults. A catalog is UTF-8 JSON with an
explicit version, exact safe relative paths, and explicit RFC 6901 pointers.

## Version 1 example

```json
{
  "schemaVersion": 1,
  "files": [
    {
      "path": "data/config/custom_dialog.json",
      "pointers": [
        "/description",
        "/dialog/title"
      ]
    }
  ]
}
```

Paths must be relative `.json`, `.faction`, `.variant`, or `.ship` files.
Catalogs cannot contain globs, duplicate paths or pointers, standard-handler
files (see `StandardJsonFileExtractor` in `ssmt-extractor` for the full,
current list, which grows as new real-mod evidence is found), more than 256
files, more than 256 pointers per file, or more than 1 MiB of JSON. Missing
pointers are skipped; selected non-text values are reported as errors.

## Standard hull files

Hull files under `data/hulls/` are covered by a standard handler
(evidence-gated, per ADR-032): a `data/hulls/*.ship` file extracts only
`/hullName` and `/description`, and every other hull field (`hullId`,
`spriteName`, `bounds`, `weaponSlots`, `engineSlots`, ...) is structural
and is not extracted. Hull files are parsed with the same lenient JSON
dialect as `.variant` and `.faction` files. For example:

```json
{
  hullId: 'example_hull',
  hullName: 'Example Hull',
  description: 'A sturdy example hull.',
  spriteName: 'graphics/ships/example.png',
}
```

yields exactly two strings, with source locations `json:/hullName` and
`json:/description` keyed against the file's relative source path (for
example `data/hulls/example.ship`).

Because this standard handler exists, opt-in catalogs cannot target
`data/hulls/*.ship` files — such entries are rejected as standard-handler
overlap. Opt-in `.ship` paths outside `data/hulls/` remain allowed, so a
mod's hull-like files in other directories can still be covered with
explicit pointers.

Use the GUI's **Create with JSON Schema** action or the CLI:

```powershell
ssmt project create SOURCE PROJECT `
  --patch-id example.translation `
  --patch-name "Example Translation" `
  --json-schema custom-schema.json
```

Stable keys remain `json:<pointer>`, so the normal stale-source verification
and non-destructive patch workflow apply.
