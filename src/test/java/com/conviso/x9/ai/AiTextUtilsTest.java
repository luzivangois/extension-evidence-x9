package com.conviso.x9.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiTextUtilsTest {

    @Test
    void keepsAtMostThreeNonBlankLines() {
        String raw = "Line one\n\nLine two\nLine three\nLine four";
        assertEquals("Line one\nLine two\nLine three", AiTextUtils.normalizeSummary(raw));
    }

    @Test
    void stripsNumberingAndBullets() {
        String raw = "1) First line\n2. Second line\n- Third line";
        assertEquals("First line\nSecond line\nThird line", AiTextUtils.normalizeSummary(raw));
    }

    @Test
    void returnsEmptyForNullOrBlankInput() {
        assertEquals("", AiTextUtils.normalizeSummary(null));
        assertEquals("", AiTextUtils.normalizeSummary("   \n  \n "));
    }

    @Test
    void extractsErrorMessageFromJsonBody() {
        String body = "{\"error\": {\"message\": \"invalid api key\"}}";
        assertEquals("invalid api key", AiTextUtils.extractApiErrorMessage(body));
    }

    @Test
    void fallsBackToRawSnippetWhenBodyIsNotJson() {
        assertEquals("not json at all", AiTextUtils.extractApiErrorMessage("not json at all"));
    }

    @Test
    void truncatesLongRawSnippet() {
        StringBuilder longBody = new StringBuilder();
        for (int i = 0; i < 400; i++) {
            longBody.append('x');
        }
        String result = AiTextUtils.extractApiErrorMessage(longBody.toString());
        assertEquals(263, result.length());
        assertEquals("...", result.substring(260));
    }

    @Test
    void returnsPlaceholderForBlankBody() {
        assertEquals("sem detalhes retornados pela API", AiTextUtils.extractApiErrorMessage(""));
        assertEquals("sem detalhes retornados pela API", AiTextUtils.extractApiErrorMessage(null));
    }
}
