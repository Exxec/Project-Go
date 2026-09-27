# Sister repository interoperability design (2026-09-27)

Status: proposed. Project Go remains the localization authority and owns its Java extractors. Other tools consume explicit, versioned evidence files; no source archive or mod tree is edited.

## Localization coverage export for BridgeForge

The extractor already has CSV/JSON gap auditors and file/JAR coverage records. Export a counts-and-identifiers `localization-coverage.json` for one selected mod root. Required fields: schema/extractor version, selected root and source archive/root SHA-256, per-file normalized relative path, content kind, supported/extracted/skipped counts, skip reason, and optional stable string locator. A partial or integrity-failed JAR must carry partial/unknown status; missing entries never become zero gaps. Do not include source text or translations by default. Detailed user-local output can stay outside source control.

BridgeForge joins this manifest to its translation review only on an exact source hash and path. Its own leftover-text detector remains separate. Neutral fixtures must cover matching and changed roots, malformed selected JSON, corrupt JAR entry, unsupported format, and legitimate zero-entry file. Exit when a matching manifest identifies uncovered supported strings and a mismatch blocks a complete-coverage claim without changing the mod.

## Release evidence contract (BridgeForge lesson)

Project Go already has source/version/release checks. Map them to a clear chain: exact commit/tag, clean build inputs, Gradle/JDK fingerprints, archive hash, embedded version, packaged GUI/Auto/CLI acceptance, and separately observed downloaded release asset/hash. Give offline, native, live, rights, and publication gates independent `PASS`, `FAIL`, `NOT_RUN`, or `UNKNOWN` states with evidence location/date. A prior RC release does not establish later `main` publication.

Inventory current `docs/ROADMAP.md` P0/P4/P5/P6 and existing release evidence before adding checks. Test missing or mismatched asset, stale version, and skipped native acceptance using synthetic fixtures. Exit when a candidate release can be traced from exact source through the published asset and no missing gate is reported as passed.
