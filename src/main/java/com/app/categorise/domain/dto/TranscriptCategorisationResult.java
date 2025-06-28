package com.app.categorise.domain.dto;

/**
 * A DTO representing the result of an AI classification call.
 * This object holds the canonical categoryId (if found), a generic topic, and a suggested alias from the AI.
 *
 * @param category       The special, predefined categoryId if the content matches one (e.g., "Recipe"). Can be null.
 * @param genericTopic   A stable, one-word keyword for the general topic (e.g., "tech"). Used for grouping non-special content.
 * @param suggestedAlias A creative, trendy alias for the video suggested by the AI.
 */
public record TranscriptCategorisationResult(String category, String genericTopic, String suggestedAlias) {

}