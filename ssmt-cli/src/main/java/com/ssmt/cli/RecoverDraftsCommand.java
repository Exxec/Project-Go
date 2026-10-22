package com.ssmt.cli;

import com.ssmt.project.DraftRecoveryPlan;
import com.ssmt.project.DraftRecoveryService;
import com.ssmt.project.ProjectException;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Resumable recovery of missing review drafts: plan, export bounded batches, import filled batches by identity. Reads
 * the review packet only; generates and sends nothing, and approves nothing.
 */
@Command(
        name = "recover-drafts",
        description = "Plan, export and import missing review drafts in resumable batches.",
        subcommands = {
            RecoverDraftsCommand.Plan.class,
            RecoverDraftsCommand.Export.class,
            RecoverDraftsCommand.Import.class,
            RecoverDraftsCommand.Status.class
        })
public final class RecoverDraftsCommand implements Runnable {
    private static final int DEFAULT_BATCH_SIZE = 50;

    @Override
    public void run() {
        // Picocli displays this command's usage when no operation is selected.
    }

    /** Options shared by the operations. */
    abstract static class Base implements Callable<Integer> {
        private static final Logger LOG = LoggerFactory.getLogger(Base.class);

        @Option(names = "--workspace", required = true,
                description = "Recovery workspace (batches, manifest, drafts); must be outside the packet.")
        protected Path workspace;

        abstract void run() throws ProjectException;

        @Override
        public final Integer call() {
            try {
                run();
                return 0;
            } catch (ProjectException exception) {
                LOG.error(CliDiagnostics.explain("Draft recovery", exception));
                return 1;
            }
        }
    }

    /** Options for operations that read the packet. */
    abstract static class PacketBase extends Base {
        @Option(names = "--packet", required = true,
                description = "Review packet directory holding review.json (read only).")
        protected Path packet;

        @Option(names = "--batch-size", defaultValue = "" + DEFAULT_BATCH_SIZE,
                description = "Maximum units per batch; a LINKED group is never split (default: ${DEFAULT-VALUE}).")
        protected int batchSize;
    }

    @Command(name = "plan", description = "List units with missing targets and their batch assignment (writes plan.json).")
    static final class Plan extends PacketBase {
        private static final Logger LOG = LoggerFactory.getLogger(Plan.class);

        @Override
        void run() throws ProjectException {
            DraftRecoveryPlan plan = new DraftRecoveryService().plan(packet, workspace, batchSize);
            LOG.info("Missing targets: {}; LINKED groups: {}; batches: {}; plan: {}", plan.units().size(),
                    plan.groupCount(), plan.batches().size(), workspace.resolve("plan.json"));
        }
    }

    @Command(name = "export", description = "Write batch files not yet exported; re-running resumes.")
    static final class Export extends PacketBase {
        private static final Logger LOG = LoggerFactory.getLogger(Export.class);

        @Override
        void run() throws ProjectException {
            var result = new DraftRecoveryService().export(packet, workspace, batchSize);
            LOG.info("Batches written: {}; already recorded: {}; total: {}", result.written(), result.skipped(),
                    result.total());
        }
    }

    @Command(name = "import", description = "Import one filled-in batch file by identity as unapproved drafts.")
    static final class Import extends PacketBase {
        private static final Logger LOG = LoggerFactory.getLogger(Import.class);

        @Parameters(index = "0", description = "Filled-in batch file.")
        private Path batch;

        @Override
        void run() throws ProjectException {
            var result = new DraftRecoveryService().importBatch(packet, workspace, batch);
            LOG.info(result.alreadyImported() ? "{} was already imported; nothing changed"
                    : "{}: imported {} draft(s) as DRAFT_NEEDS_REVIEW", result.batch(), result.units());
        }
    }

    @Command(name = "status", description = "Show recovery progress from the workspace.")
    static final class Status extends Base {
        private static final Logger LOG = LoggerFactory.getLogger(Status.class);

        @Override
        void run() throws ProjectException {
            var status = new DraftRecoveryService().status(workspace);
            LOG.info("Units: {}; batches: {} ({} imported); drafted: {}; remaining: {}", status.units(),
                    status.batches(), status.importedBatches(), status.draftedUnits(), status.remainingUnits());
        }
    }
}
