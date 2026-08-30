package com.ssmt.cli;

import com.ssmt.validation.TranslationValidator;
import com.ssmt.validation.ValidationIssue;
import java.util.List;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * Validates protected translation syntax.
 */
@Command(name = "validate", description = "Compare protected syntax in source and translation.")
public final class ValidateCommand implements Callable<Integer> {
    private static final Logger LOG = LoggerFactory.getLogger(ValidateCommand.class);

    @Parameters(index = "0", description = "Original source text.")
    private String sourceText;

    @Parameters(index = "1", description = "Translated text.")
    private String translatedText;

    @Override
    public Integer call() {
        List<ValidationIssue> issues =
                new TranslationValidator().validate(sourceText, translatedText);
        issues.forEach(issue -> LOG.error("{}: {}", issue.code(), issue.message()));
        return issues.isEmpty() ? 0 : 1;
    }
}
