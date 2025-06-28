package com.app.categorise.domain.model;

/**
 * A DTO representing the result of an AI classification call.
 * This object holds the canonical category (if found), a generic topic, and a suggested alias from the AI.
 */
public class ClassificationResult {

    /** The special, predefined category if the content matches one (e.g., "Recipe"). Can be null. */
    private final String canonicalCategory;
    /** A stable, one-word keyword for the general topic (e.g., "tech"). Used for grouping non-special content. */
    private final String genericTopic;
    /** A creative, trendy alias for the video suggested by the AI. */
    private final String suggestedAlias;

    public ClassificationResult(String canonicalCategory, String genericTopic, String suggestedAlias) {
        this.canonicalCategory = canonicalCategory;
        this.genericTopic = genericTopic;
        this.suggestedAlias = suggestedAlias;
    }

    public String getCanonicalCategory() {
        return canonicalCategory;
    }

    public String getGenericTopic() {
        return genericTopic;
    }

    public String getSuggestedAlias() {
        return suggestedAlias;
    }
} 