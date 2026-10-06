# BridgeForge translation interchange

New CJK projects in GUI and Auto use the shared method automatically. Schema
catalogs are advisory. Existing translation-memory storage and draft approval
continue to apply. Original mods remain source inputs; normal build publishes a
separate translated copy.

The direct CLI exchanges BridgeForge schema-v1 documents:

```text
ssmt translate export WORKING_MOD --out request.json
ssmt translate import response.json
ssmt translate prefill WORKING_MOD --document request.json --record author-records --out prefilled.json
ssmt translate prefill WORKING_MOD --document request.json --reference ENGLISH_MOD --out prefilled.json
ssmt translate apply WORKING_MOD --document response.json --out TRANSLATED_MOD
ssmt translate check TRANSLATED_MOD
```

Output destinations must not exist. Edit only translations/glossary; changing
source hashes, entry identities, contexts or source text is refused. Check/apply
returns 2 for partial translation or unreadable diagnostics and 1 for errors.
`import` checks document structure; source identity/hash validation happens at
apply or normal project import/build. Ambiguous prefill remains unresolved.

`ssmt translate apply DISPOSABLE_WORKING_COPY --document response.json --in-place`
explicitly changes that selected copy. Never select upstream/original material.
On Windows keep the installed batch and PowerShell helper together. Native Auto
CJK input argv and interactive GUI acceptance limitations are recorded in
[P7_IMPLEMENTATION_STATUS.md](P7_IMPLEMENTATION_STATUS.md).
