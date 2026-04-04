package com.app.categorise.data.dto;

/**
 * A DTO representing the result of an AI classification call.
 * This object holds the canonical categoryId (if found), a generic topic, a suggested alias,
 * and an AI-generated title from the AI.
 *
 * @param category        The special, predefined categoryId if the content matches one (e.g., "Recipe"). Can be null.
 * @param genericTopic    A stable, one-word keyword for the general topic (e.g., "tech"). Used for grouping non-special content.
 * @param suggestedAlias  A creative, trendy alias for the video suggested by the AI.
 * @param generatedTitle  A short, engaging AI-generated title capturing the video's tone and main point. Can be null.
 */
public record TranscriptCategorisationResult(String category, String genericTopic, String suggestedAlias, String generatedTitle) { }