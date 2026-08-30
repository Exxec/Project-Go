package com.ssmt.scanner;

import com.ssmt.core.exception.SsmtParseException;
import com.ssmt.core.model.ModDependency;
import com.ssmt.core.model.ModInfo;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Discovers mod directories and resolves their dependency order.
 */
public final class ModScanner {

    private final ModInfoReader modInfoReader;

    public ModScanner() {
        this(new ModInfoReader());
    }

    ModScanner(ModInfoReader modInfoReader) {
        this.modInfoReader = modInfoReader;
    }

    /**
     * Scans one directory containing a child directory per mod.
     *
     * @param modsRoot scan root
     * @return deterministic scan report
     * @throws SsmtParseException when the root cannot be listed
     * @throws CyclicDependencyException when discovered mods contain a dependency cycle
     */
    public ScanReport scan(Path modsRoot)
            throws SsmtParseException, CyclicDependencyException {
        List<String> warnings = new ArrayList<>();
        Map<String, ModInfo> modsById = new LinkedHashMap<>();
        for (Path modDirectory : listModDirectories(modsRoot)) {
            try {
                ModInfo modInfo = modInfoReader.read(modDirectory);
                ModInfo existing = modsById.putIfAbsent(modInfo.id(), modInfo);
                if (existing != null) {
                    warnings.add("Duplicate mod id \"%s\" in %s; keeping %s"
                            .formatted(modInfo.id(), modDirectory, existing.sourceDirectory()));
                }
            } catch (SsmtParseException exception) {
                warnings.add("Skipped %s: %s".formatted(modDirectory, exception.getMessage()));
            }
        }

        addMissingDependencyWarnings(modsById, warnings);
        return new ScanReport(resolveOrder(modsById), warnings);
    }

    private static List<Path> listModDirectories(Path modsRoot) throws SsmtParseException {
        Path normalizedRoot = modsRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedRoot)) {
            throw new SsmtParseException("Not a directory", normalizedRoot);
        }

        List<Path> directories = new ArrayList<>();
        try (DirectoryStream<Path> stream =
                     Files.newDirectoryStream(normalizedRoot, Files::isDirectory)) {
            stream.forEach(directories::add);
        } catch (IOException exception) {
            throw new SsmtParseException("Could not list mod directories", normalizedRoot, exception);
        }
        directories.sort(Comparator.comparing(
                path -> Objects.requireNonNull(path.getFileName()).toString()));
        return directories;
    }

    private static void addMissingDependencyWarnings(
            Map<String, ModInfo> modsById,
            List<String> warnings
    ) {
        for (ModInfo mod : modsById.values()) {
            for (ModDependency dependency : mod.dependencies()) {
                if (!modsById.containsKey(dependency.id())) {
                    warnings.add("Mod \"%s\" depends on missing mod \"%s\""
                            .formatted(mod.id(), dependency.id()));
                }
            }
        }
    }

    private static List<ModInfo> resolveOrder(Map<String, ModInfo> modsById)
            throws CyclicDependencyException {
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();
        for (String id : modsById.keySet()) {
            inDegree.put(id, 0);
            dependents.put(id, new ArrayList<>());
        }

        for (ModInfo mod : modsById.values()) {
            for (ModDependency dependency : mod.dependencies()) {
                if (modsById.containsKey(dependency.id())) {
                    inDegree.merge(mod.id(), 1, Integer::sum);
                    dependents.get(dependency.id()).add(mod.id());
                }
            }
        }
        dependents.values().forEach(list -> list.sort(Comparator.naturalOrder()));

        PriorityQueue<String> ready = new PriorityQueue<>();
        inDegree.forEach((id, degree) -> {
            if (degree == 0) {
                ready.add(id);
            }
        });

        List<ModInfo> ordered = new ArrayList<>();
        while (!ready.isEmpty()) {
            String id = ready.remove();
            ordered.add(modsById.get(id));
            for (String dependent : dependents.get(id)) {
                int remaining = inDegree.merge(dependent, -1, Integer::sum);
                if (remaining == 0) {
                    ready.add(dependent);
                }
            }
        }

        if (ordered.size() != modsById.size()) {
            Set<String> resolved = new HashSet<>();
            ordered.forEach(mod -> resolved.add(mod.id()));
            List<String> unresolved = modsById.keySet().stream()
                    .filter(id -> !resolved.contains(id))
                    .sorted()
                    .toList();
            throw new CyclicDependencyException(unresolved);
        }
        return List.copyOf(ordered);
    }
}
