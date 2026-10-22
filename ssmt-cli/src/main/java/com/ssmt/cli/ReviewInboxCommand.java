package com.ssmt.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.ssmt.project.GuiChecklistReview;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/** Lists the GUI checklist rows that need the owner's review, and records an explicit owner decision. */
@Command(name = "review-inbox", mixinStandardHelpOptions = true,
        description = "List GUI checklist rows needing owner review (list) or record the owner's decision (record).")
public final class ReviewInboxCommand implements Callable<Integer> {
    @Parameters(index = "0", description = "Operation: list or record.")
    private String action;
    @Option(names = "--dir", defaultValue = "ssmt-gui/build/gui-checklist",
            description = "Folder holding checklist.json, screenshots and owner-review.json.")
    private Path directory;
    @Option(names = "--all", description = "list: include rows whose decision is current.")
    private boolean all;
    @Option(names = "--json", description = "list: machine-readable output.")
    private boolean json;
    @Option(names = "--row", description = "record: 1-based row number as shown by list.")
    private int row;
    @Option(names = "--status", description = "record: PASS, FAIL or NOT_TESTED, chosen by the owner.")
    private String status;
    @Option(names = "--note", description = "record: observation; required for FAIL.")
    private String note;
    @Spec private CommandSpec spec;

    @Override public Integer call() {
        try {
            var review = new GuiChecklistReview(directory);
            if ("list".equals(action)) {
                list(all ? review.rows() : review.inbox());
                return 0;
            }
            if ("record".equals(action)) {
                if (row < 1 || status == null) {
                    throw new IllegalArgumentException("record needs --row and --status (PASS, FAIL, NOT_TESTED)");
                }
                var recorded = review.record(row, GuiChecklistReview.Status.valueOf(status), note);
                spec.commandLine().getOut().println("Recorded row " + recorded.number() + " ("
                        + recorded.scenario() + "): " + status);
                return 0;
            }
            throw new IllegalArgumentException("Operation must be list or record");
        } catch (IOException | IllegalArgumentException exception) {
            spec.commandLine().getErr().println("Review inbox failed: " + exception.getMessage());
            return 1;
        }
    }

    private void list(List<GuiChecklistReview.Row> rows) {
        var out = spec.commandLine().getOut();
        if (json) {
            var mapper = new ObjectMapper();
            ArrayNode array = mapper.createArrayNode();
            rows.forEach(item -> array.addObject().put("number", item.number()).put("scenario", item.scenario())
                    .put("state", item.state().name()).put("automated", item.automated())
                    .put("decision", item.decision().map(decision -> decision.status().name()).orElse(null))
                    .put("screenshots", item.screenshots().size()));
            out.println(array.toPrettyString());
            return;
        }
        rows.forEach(item -> out.println(item.number() + ". [" + item.state() + "] " + item.scenario()
                + " (" + item.automated() + ", " + item.screenshots().size() + " screenshots)"
                + item.decision().map(decision -> "; owner " + decision.status()).orElse("")));
        out.println(rows.size() + (all ? " rows" : " rows need review"));
    }
}
