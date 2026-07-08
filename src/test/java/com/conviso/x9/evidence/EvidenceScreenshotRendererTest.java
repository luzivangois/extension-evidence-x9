package com.conviso.x9.evidence;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

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
}
