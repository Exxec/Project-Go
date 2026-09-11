package com.ssmt.cli;

import com.ssmt.project.ProjectException;
import com.ssmt.project.StorageHygieneService;
import com.ssmt.project.TranslationWorkflow;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** Read-first maintenance for application-owned cache and interrupted staging files. */
@Command(name = "storage", mixinStandardHelpOptions = true,
        description = "Preview or clean old application-owned cache and staging data.")
public final class StorageCommand implements Callable<Integer> {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(StorageCommand.class);

    @Parameters(index = "0", description = "Operation: preview or clean.")
    private String action;

    @Option(names = "--older-than-days", defaultValue = "30",
            description = "Only include candidates at least this old (default: ${DEFAULT-VALUE}).")
    private int olderThanDays;

    private final StorageHygieneService service;
    private final Path manifest;

    /** Uses the normal application-owned storage and approval manifest. */
    public StorageCommand() {
        this(new StorageHygieneService(),
                TranslationWorkflow.defaultApplicationRoot().resolve("cleanup-preview.json"));
    }

    StorageCommand(StorageHygieneService service, Path manifest) {
        this.service = service;
        this.manifest = manifest;
    }

    @Override public Integer call() {
        try {
            if (!java.util.Set.of("preview", "clean").contains(action)) {
                throw new IllegalArgumentException("Operation must be preview or clean");
            }
            if (olderThanDays < 0) {
                throw new IllegalArgumentException("Cleanup age must not be negative");
            }
            var preview = action.equals("preview")
                    ? service.preview(Duration.ofDays(olderThanDays))
                    : service.readPreview(manifest);
            for (var item : preview.items()) {
                LOG.info("{}  {}  {} files  {} bytes  sha256:{}",
                        item.kind(), item.relativePath(), item.files(), item.bytes(), item.treeHash());
            }
            LOG.info("{} candidate(s), {} file(s), {} byte(s)",
                    preview.items().size(), preview.files(), preview.bytes());
            if (action.equals("clean")) {
                service.cleanup(preview);
                LOG.info("Cleaned exactly the listed candidates");
            } else {
                service.writePreview(manifest, preview);
                LOG.info("Preview saved to {}; inspect it before running storage clean", manifest);
            }
            return 0;
        } catch (ProjectException | IllegalArgumentException exception) {
            LOG.error(exception.getMessage());
            return 1;
        }
    }
}
