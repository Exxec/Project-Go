package com.ssmt.cli;

import com.ssmt.project.BridgeForgeTranslationApply;
import com.ssmt.project.BridgeForgeTranslationComparison;
import com.ssmt.project.BridgeForgeTranslationDocument;
import com.ssmt.project.BridgeForgeTranslationPrefill;
import com.ssmt.project.BridgeForgeTranslationService;
import com.ssmt.project.ProjectException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** Direct schema-v1 workflow for exchange with BridgeForge. */
@Command(name = "translate", mixinStandardHelpOptions = true,
        description = "BridgeForge-compatible export, import, apply, prefill, or leftover check.")
public final class BridgeForgeTranslationCommand implements Callable<Integer> {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(BridgeForgeTranslationCommand.class);
    @Parameters(index = "0", description = "Operation: export, import, apply, check, prefill, compare.")
    private String action;
    @Parameters(index = "1", description = "Source mod working copy (or JSON document for import).")
    private Path source;
    @Option(names = "--document", description = "Returned schema-v1 translation JSON.")
    private Path document;
    @Option(names = "--out", description = "New JSON document or translated mod directory.")
    private Path out;
    @Option(names = "--in-place", description = "Apply only to the explicitly selected mod working copy.")
    private boolean inPlace;
    @Option(names = "--record", description = "Author zh/en record file or directory.")
    private List<Path> records = new java.util.ArrayList<>();
    @Option(names = "--reference", description = "English mod for prefill, or BridgeForge export JSON for compare.")
    private Path reference;

    @Override public Integer call() {
        try {
            var exchange = new BridgeForgeTranslationDocument();
            var service = new BridgeForgeTranslationService();
            if (!action.equals("apply") && inPlace) {
                throw new IllegalArgumentException("--in-place is only valid for apply");
            }
            switch (action) {
                case "compare" -> {
                    requireOut();
                    if (reference == null) {
                        throw new IllegalArgumentException("Comparison needs --reference export.json");
                    }
                    var comparison = new BridgeForgeTranslationComparison();
                    var result = comparison.compare(source, exchange.read(reference));
                    comparison.write(source, out, result);
                    LOG.info("Comparison {}: {} unit differences", result.path("status"),
                            result.path("differences").size());
                    return result.path("status").asText().equals("MATCH") ? 0 : 2;
                }
                case "export" -> {
                    requireOut();
                    var exported = service.export(source);
                    exchange.write(out, exported);
                    LOG.info("Exported {} units; {} unreadable diagnostics", exported.path("entry_count"),
                            exported.path("unreadable").size());
                }
                case "import" -> {
                    var imported = exchange.read(source);
                    if (out != null) {
                        exchange.write(out, imported);
                    }
                    LOG.info("Valid schema-v1 document: {} entries", imported.path("entries").size());
                }
                case "apply" -> {
                    requireDocument();
                    var result = new BridgeForgeTranslationApply().apply(source, exchange.read(document), out, inPlace);
                    LOG.info("{}", result);
                    return result.path("status").asText().equals("OK") ? 0 : 2;
                }
                case "check" -> {
                    var result = service.export(source);
                    LOG.info("Leftover CJK units: {}; unreadable diagnostics: {}", result.path("entry_count"),
                            result.path("unreadable").size());
                    return result.path("entry_count").asInt() == 0 && result.path("unreadable").isEmpty() ? 0 : 2;
                }
                case "prefill" -> {
                    requireDocument();
                    requireOut();
                    var value = exchange.read(document);
                    var prefill = new BridgeForgeTranslationPrefill();
                    if (!records.isEmpty()) {
                        LOG.info("Records: {}", prefill.records(value, records));
                    }
                    if (reference != null) {
                        LOG.info("Reference: {}", prefill.reference(value, source, reference));
                    }
                    exchange.write(out, value);
                }
                default -> throw new IllegalArgumentException("Operation must be export, import, apply, check, prefill, or compare");
            }
            return 0;
        } catch (ProjectException | IllegalArgumentException exception) {
            LOG.error("{}", exception.getMessage());
            return 1;
        }
    }

    private void requireOut() {
        if (out == null) {
            throw new IllegalArgumentException("This operation needs --out");
        }
    }
    private void requireDocument() {
        if (document == null) {
            throw new IllegalArgumentException("This operation needs --document");
        }
    }
}
