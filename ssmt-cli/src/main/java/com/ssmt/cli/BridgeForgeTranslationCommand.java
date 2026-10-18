package com.ssmt.cli;

import com.ssmt.project.BridgeForgeTranslationApply;
import com.ssmt.project.BridgeForgeTranslationComparison;
import com.ssmt.project.BridgeForgeTranslationDocument;
import com.ssmt.project.BridgeForgeTranslationPrefill;
import com.ssmt.project.BridgeForgeTranslationService;
import com.ssmt.project.ProjectException;
import com.ssmt.project.TranslationSnapshot;
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
    @Parameters(index = "0", description = "Operation: export, import, apply, check, prefill, compare, restore, bytecode-audit.")
    private String action;
    @Parameters(index = "1", description = "Source mod working copy (or JSON document for import).")
    private Path source;
    @Parameters(index = "2", arity = "0..1", description = "Snapshot timestamp for restore (omit to list snapshots).")
    private String snapshot;
    @Option(names = "--document", description = "Returned schema-v1 translation JSON.")
    private Path document;
    @Option(names = "--out", description = "New JSON document or translated mod directory.")
    private Path out;
    @Option(names = "--in-place", description = "Apply only to the explicitly selected mod working copy.")
    private boolean inPlace;
    @Option(names = "--accept-unreadable",
            description = "check: do not fail only because some inputs were unreadable (they are still listed).")
    private boolean acceptUnreadable;
    @Option(names = "--json", description = "check or bytecode-audit: write the JSON report to this file.")
    private Path jsonReport;
    @Option(names = "--record", description = "Author zh/en record file or directory.")
    private List<Path> records = new java.util.ArrayList<>();
    @Option(names = "--reference", description = "English mod for prefill, or BridgeForge export JSON for compare.")
    private Path reference;

    @Override public Integer call() {
        try {
            var exchange = new BridgeForgeTranslationDocument();
            var service = new BridgeForgeTranslationService();
            if (acceptUnreadable && !action.equals("check")
                    || jsonReport != null && !action.equals("check") && !action.equals("bytecode-audit")) {
                throw new IllegalArgumentException("--accept-unreadable is only valid for check, --json for check and bytecode-audit");
            }
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
                case "bytecode-audit" -> {
                    requireOut();
                    var audit = com.ssmt.project.BytecodeCallSites.withDefaultRules();
                    var unreadable = new java.util.ArrayList<String>();
                    var sites = audit.scan(source.toRealPath(), unreadable);
                    java.nio.file.Files.writeString(out, com.ssmt.project.BytecodeCallSites.proposal(sites),
                            java.nio.charset.StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
                    long text = sites.stream().filter(site -> site.verdict()
                            == com.ssmt.project.BytecodeCallSites.Verdict.TEXT).count();
                    LOG.info("Bytecode audit: {} CJK literal(s), {} proposed as shown text (REVIEW before use), "
                            + "{} unreadable class(es): {}", sites.size(), text, unreadable.size(), out);
                    if (jsonReport != null) {
                        new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter()
                                .writeValue(jsonReport.toFile(), java.util.Map.of("schema_version", 1,
                                        "mode", "BYTECODE_AUDIT", "sites", sites, "unreadable", unreadable));
                    }
                }
                case "restore" -> {
                    if (snapshot == null) {
                        LOG.info("Snapshots: {}", TranslationSnapshot.list(source));
                    } else {
                        LOG.info("Restored {} files from snapshot {}",
                                TranslationSnapshot.restore(source, snapshot).size(), snapshot);
                    }
                }
                case "check" -> {
                    var result = service.export(source);
                    LOG.info("Leftover CJK units: {}; unreadable diagnostics: {}", result.path("entry_count"),
                            result.path("unreadable").size());
                    for (var diagnostic : result.path("unreadable")) {
                        LOG.warn("Unreadable: {}", diagnostic.asText());
                    }
                    if (jsonReport != null) {
                        var report = com.ssmt.project.LeftoverClassifier.classify(
                                source, result, null, java.util.List.of());
                        report.put("schema_version", 1).put("mode", "LEFTOVER_CHECK");
                        new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter()
                                .writeValue(jsonReport.toFile(), report);
                    }
                    boolean unreadableOk = result.path("unreadable").isEmpty() || acceptUnreadable;
                    return result.path("entry_count").asInt() == 0 && unreadableOk ? 0 : 2;
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
                default -> throw new IllegalArgumentException("Operation must be export, import, apply, check, prefill, compare, restore, or bytecode-audit");
            }
            return 0;
        } catch (ProjectException | IllegalArgumentException | java.io.IOException exception) {
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
