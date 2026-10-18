package com.ssmt.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssmt.patcher.PatchArtifact;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Keeps the established transactional publisher while using the shared method for all CJK artifacts. */
final class BridgeForgeProjectAdapter {
    List<PatchArtifact> artifacts(Path source, LocalizationProject project) throws ProjectException, IOException {
        ObjectNode document = project.methodDocument();
        if (document == null) {
            throw new ProjectException("Missing persisted translation method snapshot");
        }
        boolean acknowledged = document.path(LocalizationProjectService.UNREADABLE_ACKNOWLEDGED).asBoolean();
        if (!document.path("unreadable").isEmpty() && !acknowledged) {
            throw new ProjectException("Build refused: " + document.path("unreadable").size()
                    + " input file(s) could not be read and the list is not acknowledged: "
                    + document.path("unreadable"));
        }
        Map<String, ProjectEntry> entries = new HashMap<>();
        for (ProjectEntry entry : project.entries()) {
            if (!entry.key().startsWith("bf:")) {
                throw new ProjectException("Mixed legacy and BridgeForge method identities");
            }
            entries.put(entry.key().substring(3), entry);
        }
        for (JsonNode node : document.path("entries")) {
            ObjectNode unit = (ObjectNode) node;
            ProjectEntry entry = entries.remove(unit.path("id").asText());
            if (entry == null && unit.path("role").asText().equals("IDENTIFIER")) {
                continue;
            }
            if (entry == null || !entry.originalText().equals(unit.path("source").asText())
                    || !entry.sourceFile().toString().replace('\\', '/').equals(unit.path("file").asText())) {
                throw new ProjectException("Project differs from protected method snapshot");
            }
            unit.put("translation", entry.translatedText());
        }
        if (!entries.isEmpty()) {
            throw new ProjectException("Project has undeclared method entries");
        }
        Path scratch = Files.createTempDirectory("projectgo-method-");
        try {
            Path clone = scratch.resolve("clone");
            var report = new BridgeForgeTranslationApply().apply(source, document, clone, false);
            // The acknowledged unreadable inputs also fail the clone's own leftover check; they are not new problems.
            var problems = new java.util.ArrayList<String>();
            report.path("problems").forEach(problem -> {
                if (!problem.asText().startsWith(BridgeForgeTranslationApply.UNREADABLE_PROBLEM)) {
                    problems.add(problem.asText());
                }
            });
            if (!problems.isEmpty()) {
                throw new ProjectException("Translation method refused artifact: " + problems);
            }
            List<PatchArtifact> result = new ArrayList<>();
            var files = document.path("file_hashes").fieldNames();
            while (files.hasNext()) {
                String file = files.next();
                Path relative = BridgeForgeTranslationDocument.safePath(file);
                byte[] bytes = Files.readAllBytes(clone.resolve(relative));
                if (!java.util.Arrays.equals(bytes, Files.readAllBytes(source.resolve(relative)))) {
                    result.add(file.equals("mod_info.json")
                            ? PatchArtifact.translatedModInfo(source, bytes) : new PatchArtifact(relative, bytes));
                }
            }
            return List.copyOf(result);
        } finally {
            try (var walk = Files.walk(scratch)) {
                for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    path.toFile().setWritable(true);
                    Files.delete(path);
                }
            }
        }
    }
}
