# Reviewed bytecode text

A mod may supply `ssmt-bytecode-allowlist.tsv` at its root to restrict JAR/class
translation to reviewed player-visible strings. The first line must be exactly:

```text
# ssmt-bytecode-allowlist-v1
```

Each following non-comment row has three tab-separated fields: the exact
mod-relative JAR/class path using forward slashes, the complete `class:` extraction
key, and lowercase SHA-256 of the exact original string encoded as UTF-8.
The catalog is limited to 4 MiB; malformed, duplicate, and stale entries fail closed.

With a catalog present, unlisted bytecode locations are excluded from extraction.
The patcher also rejects modifications to unlisted locations, including replacements
from projects created before the catalog was added. Unchanged technical constants
may pass through. Project builds update the catalog's digests in translated clones,
so the clones can be re-extracted without weakening the source checks.

Without a catalog, the previous raw bytecode extraction behavior remains available;
it is not a semantic determination that every constant is safe to translate.
Catalogs require review of code usage, not merely a check for spaces or non-ASCII
characters. Enum IDs, CSV/JSON lookup keys, paths, memory keys, regexes, and formatting
implementation details must remain protected even when they resemble ordinary words.

VoidTec r13 supplies a reviewed catalog with 750 UI/tooltip locations. Updated SSMT
also covers its augment, welcome-message, and console-help CSVs by default. Source
strings containing paired `==highlight==` spans require matching paired markers in
translations; dollar substitutions and other existing protected syntax still apply.
