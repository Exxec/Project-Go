package com.ssmt.ai;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Read-only provider setup checks; never installs or downloads anything. */
public final class LocalProviderPreflight {
    public List<String> inspect(
            Path argosExecutable, Path translateLocallyExecutable, String translateLocallyModel) {
        List<String> findings = new ArrayList<>();
        inspectExecutable(argosExecutable, "Argos Translate", findings);
        inspectExecutable(translateLocallyExecutable, "TranslateLocally", findings);
        if (translateLocallyModel == null || translateLocallyModel.isBlank()) {
            findings.add("TranslateLocally model is not configured. Choose an installed model; "
                    + "SSMT will not download one automatically.");
        } else {
            findings.add("TranslateLocally model configured: " + translateLocallyModel
                    + " (installation not verified; no download attempted).");
        }
        return List.copyOf(findings);
    }

    private static void inspectExecutable(Path executable, String name, List<String> findings) {
        if (executable == null) {
            findings.add(name + " executable is not configured.");
        } else if (executable.getNameCount() > 1 && !Files.isRegularFile(executable)) {
            findings.add(name + " executable was not found at " + executable + ".");
        } else {
            findings.add(name + " executable configured as " + executable
                    + " (runtime initialization not attempted).");
        }
    }
}
