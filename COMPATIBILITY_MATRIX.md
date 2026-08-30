# Compatibility Matrix

## Release target

| Component | Required | Supported baseline | Notes |
|---|---:|---|---|
| Windows x64 | Yes | Windows 10/11 | Primary desktop and installer target |
| Linux x64 | Optional | Current Ubuntu CI image | Build/test coverage; native package is best effort |
| macOS | No | Not a release target | No packaging, signing, or smoke-test commitment |
| Java | Yes | Bundled Temurin-compatible JDK 25 runtime | Development requires JDK 25 |
| Starsector | Yes | 0.98a-era supplied corpus | Generated overlay still requires an installed-game smoke test |
| Tesseract | Optional | User-supplied executable with TSV output | Only needed for OCR |
| Ollama | Optional | Configurable HTTP endpoint | Drafts remain untrusted |
| Gemini | Optional | Current configured adapter contract | Credential via environment variable |
| OpenAI | Optional | Responses API adapter contract | Credential via environment variable |

Provider APIs and OCR engines are optional integrations. Their failure must
not affect offline extraction, editing, validation, or patch publication.
Plugin manifests and project documents are version-checked; unsupported major
versions are rejected rather than guessed or silently migrated.
