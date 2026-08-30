# Risk Register

## Overview
This document tracks known architectural, community, and technical risks associated with the Starsector Mod Toolkit (SSMT). These risks must be actively monitored, and their mitigations strictly enforced during development.

---

## 1. Technical Risks

| Risk ID | Description | Impact | Probability | Mitigation Strategy |
| :--- | :--- | :--- | :--- | :--- |
| **TR-01** | **Java Bytecode Version Drift**<br>Target mods compile using various JDKs (7, 8, 14, 21). Standard decompilers fail on mismatched versions. | High | Medium | Use the latest version of OW2 ASM, which maintains deep backward compatibility. Only parse the constant pool for strings; do not attempt to execute or reconstruct bytecode. |
| **TR-02** | **Memory Exhaustion (OOM)**<br>Scanning massive mod lists (e.g., 100+ mods) with deeply nested JSON files could exhaust the JVM heap. | High | Low | Use Jackson's Streaming API instead of loading entire JSON trees into memory for files over a specific threshold. |
| **TR-03** | **String Interpolation Breakage**<br>Modders heavily use variables like `%s`, `%d`, and `$color`. If the toolkit drops or misplaces these, the game will crash. | Critical | High | Implement a strict Validation Engine that aborts patch generation if the variable specifiers in the translation do not perfectly match the source string. |
| **TR-04** | **Undocumented File Formats**<br>Some mods use custom `.hjson` or bespoke scripting logic that standard CSV/JSON parsers cannot read. | Medium | High | Maintain a strict Plugin API (`PLUGIN_API.md`). If a file format is unrecognized, the tool must skip it gracefully and log a warning rather than crashing the loop. |

---

## 2. Community & Ecosystem Risks

| Risk ID | Description | Impact | Probability | Mitigation Strategy |
| :--- | :--- | :--- | :--- | :--- |
| **CR-01** | **Malicious Code / Crashcode Propagation**<br>Historically, a small minority of Starsector modders have embedded "crashcode" or malware. If SSMT blindly copies these scripts, it could propagate malicious logic. | Critical | Medium | **Zero-Touch Source Policy:** The toolkit strictly extracts and overwrites *only* localization strings and never copies or executes `.jar` files. |
| **CR-02** | **Mod Updates Break Translations**<br>A mod author updates their mod, changing keys, column layouts, or adding new lines, instantly invalidating the generated patch. | High | High | Use the Translation Memory (TM) database coupled with fuzzy matching (e.g., Levenshtein distance) to automatically remap existing translations to the new layout without losing progress. |
| **CR-03** | **Copyright & Author Permissions**<br>Mod authors may object to unauthorized third-party translations or derivative patches being distributed without consent. | Medium | Medium | Include explicit disclaimers in the GUI. Design the toolkit for *personal use* by default, and require users to acknowledge modders' permission guidelines before exporting a mod for public distribution. |