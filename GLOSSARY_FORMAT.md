# Shareable Glossary Format

Glossaries are UTF-8 JSON data files. Loading one executes no code, starts no
provider, and changes no translation. The current schema is version 1 and is
limited to 4 MiB and 10,000 terms.

```json
{
  "schemaVersion": 1,
  "sourceLanguage": "zh",
  "targetLanguage": "en",
  "terms": [
    {
      "source": "星舰",
      "target": "starship",
      "note": "Setting term"
    }
  ]
}
```

Source terms must be unique and source/target text must be nonblank. **Check
Glossary** reports translated entries that contain a source term but omit its
approved target. Findings are advisory: SSMT never rewrites or approves the
entry automatically.
