package com.conviso.x9.requirement;

import com.conviso.x9.model.RequirementItem;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequirementMatcherTest {

    private static final List<RequirementItem> CATALOG = Arrays.asList(
        new RequirementItem("1", "OPEN", "SQL Injection", "Check for sql injection vulnerabilities in login forms"),
        new RequirementItem("2", "OPEN", "Cross-Site Scripting", "Reflected and stored xss payloads in search fields"),
        new RequirementItem("3", "OPEN", "Broken Access Control", "Verify authorization checks on admin endpoints")
    );

    @Test
    void matchesByTitleSubstring() {
        String evidence = "url=/login\nbody contains SQL Injection attempt";
        Optional<String> result = RequirementMatcher.matchByDescriptionTokens(evidence, CATALOG, 6);
        assertEquals(Optional.of("1"), result);
    }

    @Test
    void matchesByDescriptionKeywordHits() {
        // Description "Check for sql injection vulnerabilities in login forms" has exactly two
        // tokens >= 6 chars: "injection" and "vulnerabilities". Both must appear to reach the
        // required 2 hits, since "vulnerable" (the previous wording) doesn't match "vulnerabilities".
        String evidence = "the request appears to expose an injection vulnerabilities issue";
        Optional<String> result = RequirementMatcher.matchByDescriptionTokens(evidence, CATALOG, 6);
        assertEquals(Optional.of("1"), result);
    }

    @Test
    void returnsEmptyWhenNothingMatches() {
        String evidence = "completely unrelated evidence about performance timing";
        Optional<String> result = RequirementMatcher.matchByDescriptionTokens(evidence, CATALOG, 6);
        assertTrue(result.isEmpty());
    }

    @Test
    void matchesByQueryTokensBidirectionally() {
        Optional<String> result = RequirementMatcher.matchByQueryTokens("Injection / SQL Injection", CATALOG, 4);
        assertEquals(Optional.of("1"), result);
    }

    @Test
    void matchByQueryTokensReturnsEmptyForBlankQuery() {
        assertFalse(RequirementMatcher.matchByQueryTokens("", CATALOG, 4).isPresent());
        assertFalse(RequirementMatcher.matchByQueryTokens(null, CATALOG, 4).isPresent());
    }

    @Test
    void firstAvailableReturnsFirstCatalogEntry() {
        assertEquals("1", RequirementMatcher.firstAvailable(CATALOG));
    }

    @Test
    void firstAvailableReturnsEmptyForEmptyCatalog() {
        assertEquals("", RequirementMatcher.firstAvailable(Collections.emptyList()));
    }
}
