package com.conviso.x9.evidence;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceScreenshotRendererTest {

    @Test
    void rendersValidPngWithNonZeroDimensions() throws IOException {
        byte[] png = EvidenceScreenshotRenderer.renderRequestResponsePng(
            "GET", "https://example.test/pentest", "GET /pentest HTTP/1.1\nHost: example.test", "HTTP/1.1 200 OK\n\nbody"
        );

        assertTrue(png.length > 0);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertTrue(image.getWidth() > 0);
        assertTrue(image.getHeight() > 0);
    }

    @Test
    void tallerImageForMoreResponseLines() throws IOException {
        byte[] shortPng = EvidenceScreenshotRenderer.renderRequestResponsePng("GET", "https://example.test", "GET / HTTP/1.1", "HTTP/1.1 200 OK");
        StringBuilder longResponse = new StringBuilder("HTTP/1.1 200 OK\n");
        for (int i = 0; i < 50; i++) {
            longResponse.append("line-").append(i).append('\n');
        }
        byte[] longPng = EvidenceScreenshotRenderer.renderRequestResponsePng("GET", "https://example.test", "GET / HTTP/1.1", longResponse.toString());

        BufferedImage shortImage = ImageIO.read(new ByteArrayInputStream(shortPng));
        BufferedImage longImage = ImageIO.read(new ByteArrayInputStream(longPng));

        assertTrue(longImage.getHeight() > shortImage.getHeight());
    }

    @Test
    void handlesEmptyRequestAndResponse() throws IOException {
        byte[] png = EvidenceScreenshotRenderer.renderRequestResponsePng("", "", "", "");
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertTrue(image.getWidth() > 0);
        assertTrue(image.getHeight() > 0);
    }

    @Test
    void rendersValidPngForVulnerabilitySummary() throws IOException {
        byte[] png = EvidenceScreenshotRenderer.renderVulnerabilitySummaryPng(
            "SQL Injection", "The login form is vulnerable to SQL injection.", "AI-generated 3-line summary.", "HIGH"
        );

        assertTrue(png.length > 0);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertTrue(image.getWidth() > 0);
        assertTrue(image.getHeight() > 0);
    }

    @Test
    void stripHtmlConvertsRichTextTemplateDescriptionsToPlainText() {
        String html = "<p>Quebra no controle de acesso &eacute; um tipo de vulnerabilidade.</p>"
            + "<p>Resultados comuns da explora&ccedil;&atilde;o permitem acesso indevido.</p>";

        String plain = EvidenceScreenshotRenderer.stripHtml(html);

        assertTrue(plain.contains("Quebra no controle de acesso"));
        assertTrue(plain.contains("Resultados comuns da"));
        assertTrue(!plain.contains("<p>") && !plain.contains("</p>"));
    }

    @Test
    void stripHtmlHandlesNullAndBreaksAndEntities() {
        assertEquals("", EvidenceScreenshotRenderer.stripHtml(null));
        assertEquals("a\nb", EvidenceScreenshotRenderer.stripHtml("a<br>b"));
        assertEquals("A & B", EvidenceScreenshotRenderer.stripHtml("A &amp; B"));
    }
}
