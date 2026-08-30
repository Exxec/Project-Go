package com.ssmt.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TranslationValidatorTest {
    private final TranslationValidator validator = new TranslationValidator();

    @Test
    void acceptsReorderedEquivalentPlaceholders() {
        assertThat(validator.validate(
                        "%1$s has {0} $color points and %% morale",
                        "{0} points : %1$s, $color et %% de moral"))
                .isEmpty();
    }

    @Test
    void reportsMissingAddedAndDuplicateCountChanges() {
        assertThat(validator.validate("%s %s {0} $red", "%s {1} $blue"))
                .extracting(ValidationIssue::code)
                .containsExactly(
                        ValidationCode.PRINTF_PLACEHOLDER_MISMATCH,
                        ValidationCode.MESSAGE_ARGUMENT_MISMATCH,
                        ValidationCode.DOLLAR_TOKEN_MISMATCH);
    }

    @Test
    void treatsEscapedPercentAsLiteral() {
        assertThat(validator.validate("Ready: 100%%", "Prêt : 100%%")).isEmpty();
    }

    @Test
    void reportsMalformedTargetSyntax() {
        assertThat(validator.validate("Value {0}: %s", "Valeur {oops}: %"))
                .extracting(ValidationIssue::code)
                .contains(
                        ValidationCode.MALFORMED_MESSAGE_ARGUMENT,
                        ValidationCode.MALFORMED_PRINTF,
                        ValidationCode.PRINTF_PLACEHOLDER_MISMATCH,
                        ValidationCode.MESSAGE_ARGUMENT_MISMATCH);
    }

    @Test
    void identicalTranslationNeverFailsValidationEvenWithStrayBraceOrPercent() {
        assertThat(validator.validate(
                        "Insert the {cartridge} now",
                        "Insert the {cartridge} now"))
                .isEmpty();
        assertThat(validator.validate(
                        "Value: %s done, ~% bonus",
                        "Value: %s done, ~% bonus"))
                .isEmpty();
    }

    @Test
    void detectsSwappedStrayBraceKindEvenWhenTotalStrayCountIsUnchanged() {
        assertThat(validator.validate(
                        "See {A and {B for details",
                        "Voir A} et B} pour détails"))
                .extracting(ValidationIssue::code)
                .contains(ValidationCode.MALFORMED_MESSAGE_ARGUMENT);
    }

    @Test
    void treatsNaturalLanguagePercentagesAsLiteral() {
        assertThat(validator.validate(
                        "护盾伤害降低 85% ，每秒产生 1.5% 幅能容量。",
                        "Reduces damage by 85%, but generates 1.5% of capacity."))
                .isEmpty();
        assertThat(validator.validate(
                        "Chance 50%: %s",
                        "Chance 50%: %s"))
                .isEmpty();
    }

    @Test
    void reportsAddedRemovedOrChangedLineBreaks() {
        assertThat(validator.validate("Line one\nLine two", "Ligne un Ligne deux"))
                .extracting(ValidationIssue::code)
                .containsExactly(ValidationCode.LINE_BREAK_MISMATCH);
        assertThat(validator.validate("Line one\r\nLine two", "Ligne un\nLigne deux"))
                .extracting(ValidationIssue::code)
                .containsExactly(ValidationCode.LINE_BREAK_MISMATCH);
        assertThat(validator.validate("Line one\nLine two", "Ligne un\nLigne deux"))
                .isEmpty();
    }
}
