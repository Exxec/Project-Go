# Opt-In CSV Extraction Schemas

Custom CSV schemas enable visible text in non-standard mod CSV files without
weakening SSMT's conservative defaults. A catalog is UTF-8 JSON with an
explicit version, exact safe relative paths, ordered identity columns, and
the text columns to extract.

## Version 1 example

```json
{
  "schemaVersion": 1,
  "files": [
    {
      "path": "data/hulls/custom_hull_extra.csv",
      "identityColumns": ["id"],
      "textColumns": ["flavorText", "manufacturerName"]
    }
  ]
}
```

Paths must be relative `.csv` files. Catalogs cannot contain globs, duplicate
paths, standard-handler files (`data/strings/descriptions.csv`,
`data/weapons/weapon_data.csv`, `data/hulls/ship_data.csv`), more than 256
files, more than 256 text columns per file, or more than 1 MiB of JSON.
`textColumns` are used when present in the file's header — a declared column
missing from a particular row's header is skipped, not an error, matching how
opt-in JSON pointers behave when absent. `identityColumns` follow the same
composite-identity rules as standard schemas: order is significant, every
component participates in the stable key, and duplicate complete identities
are still rejected as errors.

## Why this exists

Standard CSV schemas (`StandardCsvSchemas`) are a closed, hardcoded map —
adding coverage for a column or file not shipped with SSMT normally requires
a new SSMT release, evidenced by a real mod per `REAL_MOD_COMPATIBILITY.md`'s
policy. An opt-in CSV schema catalog lets you handle a specific mod's extra
columns yourself, immediately, without waiting for that. It does not weaken
or replace the standard schemas — a path already covered by a standard
schema cannot also appear in an opt-in catalog.

Use the GUI's **Create with CSV Schema** action or the CLI:

```powershell
ssmt project create SOURCE PROJECT `
  --patch-id example.translation `
  --patch-name "Example Translation" `
  --csv-schema custom-csv-schema.json
```

A JSON schema and a CSV schema may be combined in the same `project create`
call (pass both `--json-schema` and `--csv-schema`); each extends its own
file-type coverage independently.

Stable keys remain `csv:<identity-columns>=<identity-values>:<column>`, so the
normal stale-source verification and non-destructive patch workflow apply
exactly as they do for standard CSV extraction.
