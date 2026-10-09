package com.ssmt.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssmt.scanner.CandidateInventory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

/** Byte evidence and observed CJK findings for one published copy; no runtime claim. */
public final class InstalledCopyEvidence {
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Output-quality findings grouped by portable file path. */
    public record Quality(int leftoverUnits, List<FileFinding> files, List<String> unreadable) {
        public Quality {
            files = List.copyOf(files);
            unreadable = List.copyOf(unreadable);
        }
        public boolean clear() { return leftoverUnits == 0 && unreadable.isEmpty(); }
    }
    public record FileFinding(String file, int units) { }

    /** Captures exact regular-file inventory around a read-only output scan. */
    public ObjectNode capture(Path output) throws ProjectException {
        try {
            var inventory = new CandidateInventory();
            var before = inventory.capture(output);
            Quality quality = inspect(output);
            if (!before.equals(inventory.capture(output))) {
                throw new ProjectException("Installed copy changed while quality evidence was captured");
            }
            ObjectNode record = JSON.createObjectNode().put("schema_version", 1)
                    .put("output", output.toRealPath().toString());
            record.set("inventory", JSON.valueToTree(before));
            record.set("quality", JSON.valueToTree(quality));
            return record;
        } catch (IOException exception) {
            throw new ProjectException("Could not capture installed-copy evidence", exception);
        }
    }

    /** Scans observed CJK units including JSON keys and JAR constants. */
    public Quality inspect(Path output) throws ProjectException {
        var document = new BridgeForgeTranslationService().export(output);
        var counts = new TreeMap<String, Integer>();
        for (var entry : document.path("entries")) {
            counts.merge(entry.path("file").asText(), 1, Integer::sum);
        }
        var unreadable = new java.util.ArrayList<String>();
        document.path("unreadable").forEach(node -> unreadable.add(node.asText()));
        return new Quality(document.path("entry_count").asInt(), counts.entrySet().stream()
                .map(entry -> new FileFinding(entry.getKey(), entry.getValue())).toList(), unreadable);
    }

    /** Compares added, missing and modified files against the saved publication inventory. */
    public List<String> verify(Path output, ObjectNode record) throws ProjectException {
        try {
            if (record.path("schema_version").asInt() != 1
                    || !record.path("output").asText().equals(output.toRealPath().toString())
                    || !record.path("inventory").isArray()) {
                throw new ProjectException("No matching publication inventory for this installed copy");
            }
            var expected = new TreeMap<String, String>();
            for (var node : record.path("inventory")) {
                expected.put(node.path("path").asText(), node.path("sha256").asText());
            }
            var actual = new TreeMap<String, String>();
            for (var node : new CandidateInventory().capture(output)) {
                actual.put(node.path(), node.sha256());
            }
            var paths = new TreeSet<>(expected.keySet());
            paths.addAll(actual.keySet());
            var findings = new java.util.ArrayList<String>();
            for (String path : paths) {
                if (!expected.containsKey(path)) { findings.add("Added: " + path); }
                else if (!actual.containsKey(path)) { findings.add("Missing: " + path); }
                else if (!actual.get(path).equals(expected.get(path))) { findings.add("Changed: " + path); }
            }
            return List.copyOf(findings);
        } catch (IOException exception) {
            throw new ProjectException("Could not verify the installed copy", exception);
        }
    }

    /** Converts saved quality data for GUI display. */
    public Quality quality(ObjectNode record) throws ProjectException {
        try {
            return JSON.treeToValue(record.path("quality"), Quality.class);
        } catch (IOException exception) {
            throw new ProjectException("Could not read installed-copy quality evidence", exception);
        }
    }
}
