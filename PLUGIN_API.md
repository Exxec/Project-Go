# Plugin API Specification

`Last updated: 2026-08-02 by Codex (ADR-043 confidence-gated provider escalation)`

## Overview
To ensure the Starsector Mod Toolkit (SSMT) remains future-proof and tightly scoped, all non-standard file formats, third-party AI integrations, and custom validation rules must be implemented as isolated plugins. 

The core contracts live in `ssmt-core`; the workflow delegates format-specific
work through these interfaces. `ssmt-plugin-manager` catalogs compatible JAR
metadata without loading classes and initializes approved plugins in a bounded
worker JVM. Worker isolation protects the main process from crashes and hangs,
but is not an operating-system filesystem or network sandbox.

Activation additionally accepts `PluginSandboxProfile`:

* `PROCESS_ONLY` preserves the backward-compatible worker behavior;
* `AUTO` uses Bubblewrap on Linux or `sandbox-exec` on macOS when available;
* `REQUIRED` fails before plugin launch if a supported OS wrapper is unavailable.

The current OS policies deny network access and writes outside the dedicated
worker directory. They retain read-only host filesystem visibility so the JVM
and its native libraries can start. Windows required mode is unavailable until
an external non-interactive sandbox integration is defined.

---

## Bundled Offline Translation Provider Plugins

Argos Translate and TranslateLocally are bundled executable-provider adapters,
not arbitrary third-party JARs. They execute user-selected, locally installed
binaries directly (without a command shell), pass source text over UTF-8 stdin,
capture at most 1 MiB per output stream, and terminate after two minutes.

The implemented local chain is: one unambiguous approved exact translation-
memory match, then Argos Translate, then TranslateLocally when Argos fails or
its output is difficult, uncertain, or structurally unsafe. ADR-043 still
schedules escalation of unresolved local results to configured AI. The
engines always translate the original source; earlier candidates are comparison
evidence, not text to retranslate. The
TranslateLocally adapter requires an installed model identifier such as
`Helsinki-NLP/opus-mt-zh-en` (the current Chinese-to-English default); neither
adapter downloads models. Machine output remains an
untrusted draft. Only an explicit approval call feeds it back to translation
memory with `HUMAN_EDITED` provenance.

These adapters isolate Python/native translation engines in child processes.
They do not yet expose the third-party JAR discovery surface planned by A1;
that API split should use these concrete provider needs as input.

Argos additionally supports `CPU` (default), `AUTO`, and `CUDA` device modes
through its documented `ARGOS_DEVICE_TYPE` child-process environment setting.
An accelerated failure retries once on CPU. That adapter instance caches the
failed acceleration state for the session, reports requested/used devices and
the fallback reason, and serializes its translation calls to avoid concurrent
VRAM contention. TranslateLocally remains on its documented CPU-optimized path.

`TranslationResourceLimits` carries maximum worker threads (default 1), maximum
batch size (default 32), and an optional GPU-memory budget in MiB. Argos maps
the first two to `ARGOS_INTER_THREADS` and `ARGOS_BATCH_SIZE`. Its current
CTranslate2-backed CLI exposes no supported hard GPU-memory budget, so SSMT
retains that request as capability metadata and warns that it is not enforced;
it does not pass a fictional setting to the child process.

Local adapters implement `ResourceAwareTranslationProvider` and declare
whether GPU acceleration, a hard GPU-memory budget, and persistent models are
actually supported. SSMT applies settings only to an advertised capability.
Argos advertises CUDA but no hard memory cap or persistent model; TranslateLocally
advertises none of those capabilities. `AUTO` lazily tries CUDA, reports CUDA
only after success, and otherwise retries and reports CPU with the failure
reason. An interrupted wait forcibly terminates the child. Because adapters are
sequential one-shot processes, exiting each process unloads its model before
the next provider starts.

`AiAdjudicationPromptBuilder` creates the final-AI provider envelope with
`Source`, identified `Local machine draft`, `Context`, and `Instruction`
sections. Prepared prompts are carried by `AiTranslationRequest` and bypass the
generic prompt wrapper. The final instruction follows all untrusted mod data
and requires preservation of mechanics, protected syntax, line breaks,
terminology, and creator intent without invented lore or mechanics.

Attributed providers return `ProviderGenerationMetadata`. Argos records
`argos-translate` plus the language package direction; TranslateLocally records
`translate-locally` plus its configured model ID. Version is retained when
known and otherwise left empty rather than guessed. SQLite schema v3 stores
this lineage separately from trust provenance, allowing a reviewed
`HUMAN_EDITED` entry to retain its machine origin and review history.

## Pipeline Safety Invariants

Plugins/providers cannot write source mods, accept drafts, bypass syntax or
line-break validation, invoke an unconfigured AI provider, require GPU, or
download models implicitly. Automatic storage respects provenance preference
and cannot replace human edits. A bounded exact-request LRU avoids duplicate
session inference; approved exact TM/glossary lookup avoids accepted reruns.
Only explicit user actions may approve, deliberately rerun, replace a human
entry, or initiate a separately disclosed model download.

Provider selection follows ADR-046. Argos is the default local adapter; a
configured TranslateLocally model may be selected instead. The router invokes
one local provider for normal work and requests an additional provider only for
observable escalation reasons. `AiRoutingHeuristic` returns a routing decision,
not a provider-confidence or quality score. Plugins do not own TM lookup,
terminology, validation, acceptance, persistence, or output publication.

## 1. Core Plugin Lifecycle

All plugins must implement the base `SsmtPlugin` interface. The contract
currently lives in `ssmt-core`.

```java
public interface SsmtPlugin {
    /**
     * Called once when the plugin is first loaded by the toolkit.
     * Use this to allocate resources, read configs, or initialize connections.
     */
    void initialize(PluginContext context) throws PluginLoadException;
    
    /**
     * @return The unique string identifier for this plugin (e.g., "com.ssmt.plugins.json").
     */
    String getPluginId();
    
    /**
     * @return The semantic version of the plugin.
     */
    String getVersion();
}
```

## File Extractor Contract

Format plugins implement `FileExtractor`, which accepts a normalized
`ExtractionRequest` and returns immutable `ExtractedString` values in
deterministic order. Extractors must:

* reject source files outside the declared mod root;
* open source files for reading only;
* preserve source text exactly;
* use stable keys that do not depend on row position or filesystem order;
* throw `SsmtParseException` for localized read/parse failures.

The standard CSV implementation requires an explicit identity column and
localizable column list. See `DECISIONS.md` ADR-023 (ordered composite CSV
identities).
