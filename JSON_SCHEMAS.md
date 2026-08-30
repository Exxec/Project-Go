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

Paths must be relative `.json`, `.faction`, or `.variant` files. Catalogs
cannot contain globs, duplicate paths or pointers, standard-handler files, more
than 256 files, more than 256 pointers per file, or more than 1 MiB of JSON.
Missing pointers are skipped; selected non-text values are reported as errors.

Use the GUI's **Create with JSON Schema** action or the CLI:

```powershell
ssmt project create SOURCE PROJECT `
  --patch-id example.translation `
  --patch-name "Example Translation" `
  --json-schema custom-schema.json
```

Stable keys remain `json:<pointer>`, so the normal stale-source verification
and non-destructive patch workflow apply.
