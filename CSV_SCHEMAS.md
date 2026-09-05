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
paths, standard-handler files (for example `data/strings/descriptions.csv`,
`data/weapons/weapon_data.csv`, `data/hulls/ship_data.csv` — see
`StandardCsvSchemas` in `ssmt-extractor` for the full, current list, which
grows as new real-mod evidence is found), more than 256 files, more than 256
text columns per file, or more than 1 MiB of JSON.
`textColumns` are used when present in the file's header — a declared column
missing from a particular row's header is skipped, not an error, matching how
opt-in JSON pointers behave when absent. `identityColumns` follow the same
composite-identity rules as standard schemas: order is significant, every
component participates in the stable key, and duplicate complete identities
are still rejected as errors.

## Why this exists

Standard CSV schemas (`StandardCsvSchemas`) are a closed, hardcoded map —
adding coverage for a column or file not shipped with SSMT normally requires
a new SSMT release backed by a reproducible compatibility case. An opt-in CSV
schema catalog lets you handle a specific mod's extra
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

## Gap coverage suggestions

`ssmt extract` reports unrecognized CSV files that appear to hold non-English
text as advisory coverage gaps. Instead of hand-authoring a catalog from those
warnings, ask for a reviewable draft:

```powershell
ssmt extract MOD_DIRECTORY --suggest-csv-schema draft.csv.json
ssmt extract MOD_DIRECTORY --suggest-csv-schema draft.csv.json --merge-into custom-csv-schema.json
```

For each gap finding, SSMT infers one identity column — a header named `id`
(case-insensitive) when present, otherwise the first column that is non-blank
and unique in every data row — plus every other named column holding non-ASCII
data cells, in file order. `#`-prefixed and blank rows are structural and never
contribute. Findings that cannot yield a schema are reported with a status
instead: `NO_ID_COLUMN`, `NO_TEXT_COLUMNS`, or `UNPARSEABLE`. Row order is never
used as identity, because the injector matches rows by identity values.

The draft is a normal version-1 catalog and nothing is extracted until you
accept it: review the file, edit or delete entries you disagree with, then pass
it via `--csv-schema`. Suggestions are advisory data, never applied behavior, so
the evidence-gated coverage policy is unchanged. `--merge-into` is only valid
together with `--suggest-csv-schema` and skips paths the existing catalog
already contains.

