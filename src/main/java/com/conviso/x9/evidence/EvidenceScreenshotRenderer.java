package com.conviso.x9.evidence;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders a request/response pair as a terminal-style PNG screenshot, used
 * as the evidence automatically attached to a new vulnerability. Pure
 * (no Burp dependency, no I/O beyond the in-memory PNG encode) so it can be
 * unit tested directly.
 */
public final class EvidenceScreenshotRenderer {

    private static final int FONT_SIZE = 13;
    private static final int LINE_HEIGHT = 18;
    private static final int PADDING = 18;
    private static final int MAX_LINE_CHARS = 130;
    private static final Color BACKGROUND = new Color(30, 32, 36);
    private static final Color SECTION_COLOR = new Color(97, 175, 239);
    private static final Color TEXT_COLOR = new Color(222, 224, 227);

    private EvidenceScreenshotRenderer() {
    }

    public static byte[] renderRequestResponsePng(String method, String url, String request, String response) throws IOException {
        List<String> lines = new ArrayList<>();
        List<Boolean> sectionHeader = new ArrayList<>();

        addSection(lines, sectionHeader, "REQUEST  " + safe(method) + " " + safe(url), safe(request));
        lines.add("");
        sectionHeader.add(false);
        addSection(lines, sectionHeader, "RESPONSE", safe(response));

        return renderPng(lines, sectionHeader);
    }

    /** Renders a vulnerability's Title/Description/Summary/Severity as the same terminal-style PNG, used as the evidence automatically attached when linking a vulnerability to a requirement. */
    public static byte[] renderVulnerabilitySummaryPng(String title, String description, String summary, String severity) throws IOException {
        List<String> lines = new ArrayList<>();
        List<Boolean> sectionHeader = new ArrayList<>();

        addSection(lines, sectionHeader, "TITLE", stripHtml(title));
        lines.add("");
        sectionHeader.add(false);
        addSection(lines, sectionHeader, "DESCRIPTION", stripHtml(description));
        lines.add("");
        sectionHeader.add(false);
        addSection(lines, sectionHeader, "SUMMARY", stripHtml(summary));
        lines.add("");
        sectionHeader.add(false);
        addSection(lines, sectionHeader, "SEVERITY", stripHtml(severity));

        return renderPng(lines, sectionHeader);
    }

    /**
     * Vulnerability template fields (e.g. description) come from the Conviso Platform as rich-text
     * HTML (e.g. {@code <p>...</p>}), which would otherwise render as literal tags in the PNG.
     */
    static String stripHtml(String html) {
        if (html == null) {
            return "";
        }
        return html
            .replaceAll("(?i)<\\s*br\\s*/?\\s*>", "\n")
            .replaceAll("(?i)<\\s*/(p|li|div|h[1-6])\\s*>", "\n\n")
            .replaceAll("(?i)<[^>]+>", "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replaceAll("\n{3,}", "\n\n")
            .trim();
    }

    private static byte[] renderPng(List<String> lines, List<Boolean> sectionHeader) throws IOException {
        Font font = new Font(Font.MONOSPACED, Font.PLAIN, FONT_SIZE);
        Font boldFont = font.deriveFont(Font.BOLD);

        BufferedImage measurer = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D measureGraphics = measurer.createGraphics();
        measureGraphics.setFont(font);
        int charWidth = measureGraphics.getFontMetrics().charWidth('M');
        measureGraphics.dispose();

        int longestLine = MAX_LINE_CHARS;
        for (String line : lines) {
            longestLine = Math.max(longestLine, Math.min(line.length(), MAX_LINE_CHARS));
        }

        int width = PADDING * 2 + longestLine * charWidth;
        int height = PADDING * 2 + Math.max(1, lines.size()) * LINE_HEIGHT;

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setColor(BACKGROUND);
        graphics.fillRect(0, 0, width, height);

        int y = PADDING + LINE_HEIGHT - 4;
        for (int i = 0; i < lines.size(); i++) {
            boolean header = sectionHeader.get(i);
            graphics.setFont(header ? boldFont : font);
            graphics.setColor(header ? SECTION_COLOR : TEXT_COLOR);
            graphics.drawString(lines.get(i), PADDING, y);
            y += LINE_HEIGHT;
        }
        graphics.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static void addSection(List<String> lines, List<Boolean> sectionHeader, String header, String body) {
        lines.add(header);
        sectionHeader.add(true);
        for (String line : wrap(body)) {
            lines.add(line);
            sectionHeader.add(false);
        }
    }

    private static List<String> wrap(String text) {
        List<String> wrapped = new ArrayList<>();
        for (String rawLine : text.split("\n", -1)) {
            String line = rawLine.replace("\r", "");
            if (line.isEmpty()) {
                wrapped.add("");
                continue;
            }
            int offset = 0;
            while (offset < line.length()) {
                int end = Math.min(offset + MAX_LINE_CHARS, line.length());
                wrapped.add(line.substring(offset, end));
                offset = end;
            }
        }
        return wrapped;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
