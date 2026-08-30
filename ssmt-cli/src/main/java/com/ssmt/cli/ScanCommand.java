package com.ssmt.cli;

import com.ssmt.core.exception.SsmtParseException;
import com.ssmt.core.model.ModInfo;
import com.ssmt.scanner.CyclicDependencyException;
import com.ssmt.scanner.ModScanner;
import com.ssmt.scanner.ScanReport;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * Implements {@code ssmt scan MODS_DIRECTORY}.
 */
@Command(
        name = "scan",
        description = "Discover mods and report dependency order."
)
public final class ScanCommand implements Callable<Integer> {

    private static final Logger LOG = LoggerFactory.getLogger(ScanCommand.class);

    @Parameters(index = "0", description = "Directory containing Starsector mod directories.")
    private Path modsDirectory;

    private final ModScanner scanner;

    public ScanCommand() {
        this(new ModScanner());
    }

    ScanCommand(ModScanner scanner) {
        this.scanner = scanner;
    }

    @Override
    public Integer call() {
        try {
            ScanReport report = scanner.scan(modsDirectory);
            for (ModInfo mod : report.mods()) {
                LOG.info("Found {} ({}) version {}; {} dependenc{}",
                        mod.name(),
                        mod.id(),
                        mod.version(),
                        mod.dependencies().size(),
                        mod.dependencies().size() == 1 ? "y" : "ies");
            }
            report.warnings().forEach(LOG::warn);
            LOG.info("Scan complete: {} mod(s), {} warning(s)",
                    report.mods().size(), report.warnings().size());
            return 0;
        } catch (SsmtParseException | CyclicDependencyException exception) {
            LOG.error("Scan failed: {}", exception.getMessage());
            return 1;
        }
    }
}
