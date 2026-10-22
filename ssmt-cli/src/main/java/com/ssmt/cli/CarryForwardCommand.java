package com.ssmt.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssmt.project.CarryForwardService;
import com.ssmt.project.LocalizationProjectService;
import com.ssmt.project.ProjectException;
import com.ssmt.project.WorkflowReview;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** Reuses approvals from the previous mod version's project only where source identity, text and context match. */
@Command(name = "carry-forward",
        description = "Carry approvals from the old project into the new version's project (never modifies either).")
final class CarryForwardCommand implements Callable<Integer> {
    private static final Logger LOG = LoggerFactory.getLogger(CarryForwardCommand.class);

    @Option(names = "--from", required = true, description = "Project of the previous mod version (read only).")
    private Path from;

    @Option(names = "--to", required = true, description = "Freshly created project of the new mod version (read only).")
    private Path to;

    @Option(names = "--out", required = true, description = "New project file to write; must not exist.")
    private Path out;

    @Option(names = "--report", description = "JSON report path (default: <out>.carry-forward.json); a .txt summary is"
            + " written beside it; both must not exist.")
    private Path report;

    @Override
    public Integer call() {
        try {
            Path jsonReport = report != null ? report
                    : out.resolveSibling(out.getFileName() + ".carry-forward.json");
            Path textReport = jsonReport.resolveSibling(jsonReport.getFileName() + ".txt");
            for (Path target : new Path[] {out, jsonReport, textReport}) {
                if (Files.exists(target)) {
                    throw new IllegalArgumentException("Refusing to overwrite " + target);
                }
            }
            LocalizationProjectService service = new LocalizationProjectService();
            var result = new CarryForwardService().carry(service.read(from), service.read(to));
            String summary = CarryForwardService.summary(result) + "Units still needing review in the new project: "
                    + WorkflowReview.validate(result.project()).findings().size() + "\n";
            service.write(out, result.project());
            new ObjectMapper().writerWithDefaultPrettyPrinter()
                    .writeValue(jsonReport.toFile(), CarryForwardService.toJson(result));
            Files.writeString(textReport, summary, StandardCharsets.UTF_8);
            LOG.info(summary);
            return 0;
        } catch (ProjectException | IllegalArgumentException | IOException exception) {
            LOG.error(CliDiagnostics.explain("Carry-forward", exception));
            return 1;
        }
    }
}
