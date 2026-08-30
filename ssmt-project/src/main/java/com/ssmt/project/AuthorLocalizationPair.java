package com.ssmt.project;

/**
 * One unambiguous source/author-localization relationship.
 *
 * @param source source-language entry
 * @param authorTranslation matching author-provided translation
 */
public record AuthorLocalizationPair(
        ProjectEntry source,
        ProjectEntry authorTranslation) {
}
