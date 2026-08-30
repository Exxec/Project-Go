package com.ssmt.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compares protected syntax in source and translated text.
 */
public final class TranslationValidator {
    private static final Pattern PRINTF = Pattern.compile(
            "%(?:\\d+\\$)?[-#+0,(<]*\\d*(?:\\.\\d+)?[tT]?[a-zA-Z%](?![a-zA-Z])");
    private static final Pattern LITERAL_PERCENT =
            Pattern.compile("(?<=\\d)%(?![%a-zA-Z])");
    private static final Pattern MESSAGE = Pattern.compile("\\{(\\d+)(?:,[^{}]*)?}");
    private static final Pattern DOLLAR = Pattern.compile("\\$[A-Za-z][A-Za-z0-9_]*");
    private static final Pattern LINE_BREAK = Pattern.compile("\\r\\n|\\r|\\n");

    /**
     * Validates translated protected tokens against their source.
     *
     * @param sourceText original text
     * @param translatedText translated text
     * @return immutable findings in stable rule order
     */
    public List<ValidationIssue> validate(String sourceText, String translatedText) {
        if (sourceText == null || translatedText == null) {
            throw new IllegalArgumentException("texts must not be null");
        }
        List<ValidationIssue> issues = new ArrayList<>();
        if (hasMalformedPrintf(sourceText, translatedText)) {
            issues.add(new ValidationIssue(
                    ValidationCode.MALFORMED_PRINTF,
                    "Translation contains an incomplete printf conversion"));
        }
        if (hasMalformedMessageArgument(sourceText, translatedText)) {
            issues.add(new ValidationIssue(
                    ValidationCode.MALFORMED_MESSAGE_ARGUMENT,
                    "Translation contains a malformed brace argument"));
        }
        compare(
                issues,
                tokens(sourceText, PRINTF, matcher -> matcher.group(), true),
                tokens(translatedText, PRINTF, matcher -> matcher.group(), true),
                ValidationCode.PRINTF_PLACEHOLDER_MISMATCH,
                "printf conversions differ");
        compare(
                issues,
                tokens(sourceText, MESSAGE, matcher -> matcher.group(1), false),
                tokens(translatedText, MESSAGE, matcher -> matcher.group(1), false),
                ValidationCode.MESSAGE_ARGUMENT_MISMATCH,
                "numeric brace arguments differ");
        compare(
                issues,
                tokens(sourceText, DOLLAR, matcher -> matcher.group(), false),
                tokens(translatedText, DOLLAR, matcher -> matcher.group(), false),
                ValidationCode.DOLLAR_TOKEN_MISMATCH,
                "dollar-prefixed tokens differ");
        List<String> sourceBreaks = lineBreaks(sourceText);
        List<String> targetBreaks = lineBreaks(translatedText);
        if (!sourceBreaks.equals(targetBreaks)) {
            issues.add(new ValidationIssue(
                    ValidationCode.LINE_BREAK_MISMATCH,
                    "line breaks differ: source=" + sourceBreaks + ", target=" + targetBreaks));
        }
        return List.copyOf(issues);
    }

    private static List<String> lineBreaks(String text) {
        return LINE_BREAK.matcher(text).results().map(result -> result.group()).toList();
    }

    private static Map<String, Integer> tokens(
            String text,
            Pattern pattern,
            Function<Matcher, String> value,
            boolean excludeEscapedPercent) {
        Map<String, Integer> counts = new TreeMap<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String token = value.apply(matcher);
            if (!excludeEscapedPercent || !"%%".equals(token)) {
                counts.merge(token, 1, Integer::sum);
            }
        }
        return counts;
    }

    private static void compare(
            List<ValidationIssue> issues,
            Map<String, Integer> source,
            Map<String, Integer> target,
            ValidationCode code,
            String message) {
        if (!source.equals(target)) {
            issues.add(new ValidationIssue(code, message + ": source=" + source + ", target=" + target));
        }
    }

    private static boolean hasMalformedPrintf(String sourceText, String translatedText) {
        if (!PRINTF.matcher(sourceText).find()) {
            return false;
        }
        return strayPercentCount(translatedText) > strayPercentCount(sourceText);
    }

    private static long strayPercentCount(String text) {
        String withoutConversions = PRINTF.matcher(text).replaceAll("");
        withoutConversions = LITERAL_PERCENT.matcher(withoutConversions).replaceAll("");
        return withoutConversions.chars().filter(character -> character == '%').count();
    }

    private static boolean hasMalformedMessageArgument(String sourceText, String translatedText) {
        return strayCharacterCount(translatedText, '{') > strayCharacterCount(sourceText, '{')
                || strayCharacterCount(translatedText, '}') > strayCharacterCount(sourceText, '}');
    }

    private static long strayCharacterCount(String text, char strayCharacter) {
        String withoutArguments = MESSAGE.matcher(text).replaceAll("");
        return withoutArguments.chars().filter(character -> character == strayCharacter).count();
    }
}
