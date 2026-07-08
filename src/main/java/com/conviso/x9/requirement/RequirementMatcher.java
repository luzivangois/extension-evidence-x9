package com.conviso.x9.requirement;

import com.conviso.x9.model.RequirementItem;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Keyword-based requirement matching, extracted from three near-identical
 * implementations that used to live inline in the Swing controller. Pure and
 * side-effect free so it can be unit tested without Burp or Swing.
 */
public final class RequirementMatcher {

    private static final int MIN_TOKEN_HITS = 2;

    private RequirementMatcher() {
    }

    /**
     * Matches free-form evidence text (e.g. request/response snippets) against
     * each requirement's title (substring) or a minimum number of keyword hits
     * drawn from the requirement's own description.
     */
    public static Optional<String> matchByDescriptionTokens(String evidenceText, List<RequirementItem> requirements, int minTokenLength) {
        String evidenceLower = safeLower(evidenceText);
        for (RequirementItem requirement : requirements) {
            String title = safeLower(requirement.getTitle());
            if (!title.isEmpty() && evidenceLower.contains(title)) {
                return Optional.of(requirement.getId());
            }
            String description = safeLower(requirement.getDescription());
            if (!description.isEmpty() && countTokenHits(description, evidenceLower, minTokenLength) >= MIN_TOKEN_HITS) {
                return Optional.of(requirement.getId());
            }
        }
        return Optional.empty();
    }

    /**
     * Matches a short query (e.g. a vulnerability's template/category/pattern)
     * against each requirement's title/description. Containment is checked in
     * both directions and tokens are drawn from the query, since the query is
     * typically much shorter than the requirement text.
     */
    public static Optional<String> matchByQueryTokens(String query, List<RequirementItem> requirements, int minTokenLength) {
        String queryLower = safeLower(query);
        if (queryLower.isEmpty()) {
            return Optional.empty();
        }
        for (RequirementItem requirement : requirements) {
            String title = safeLower(requirement.getTitle());
            if (!title.isEmpty() && (queryLower.contains(title) || title.contains(queryLower))) {
                return Optional.of(requirement.getId());
            }
            String description = safeLower(requirement.getDescription());
            if (!description.isEmpty() && countTokenHits(queryLower, description + " " + title, minTokenLength) >= MIN_TOKEN_HITS) {
                return Optional.of(requirement.getId());
            }
        }
        return Optional.empty();
    }

    public static String firstAvailable(List<RequirementItem> requirements) {
        return requirements.isEmpty() ? "" : requirements.get(0).getId();
    }

    private static int countTokenHits(String tokenSource, String haystack, int minTokenLength) {
        String[] tokens = tokenSource.split("\\s+");
        int hits = 0;
        for (String token : tokens) {
            String word = token.replaceAll("[^a-zA-Z0-9_-]", "");
            if (word.length() >= minTokenLength && haystack.contains(word)) {
                hits++;
            }
            if (hits >= MIN_TOKEN_HITS) {
                break;
            }
        }
        return hits;
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
