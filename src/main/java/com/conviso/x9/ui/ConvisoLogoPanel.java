package com.conviso.x9.ui;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/** Vector fallback logo, used when the packaged PNG asset can't be loaded. */
public final class ConvisoLogoPanel extends JPanel {

    private static final Color NAVY = new Color(15, 42, 66);
    private static final Color GOLD = new Color(238, 182, 52);

    public ConvisoLogoPanel() {
        setPreferredSize(new Dimension(260, 120));
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();

            g2.setColor(Color.WHITE);
            g2.fill(new RoundRectangle2D.Double(10, 10, w - 20, h - 20, 28, 28));
            g2.setColor(new Color(225, 231, 237));
            g2.draw(new RoundRectangle2D.Double(10, 10, w - 20, h - 20, 28, 28));

            int cx = w / 2;
            int cy = h / 2;
            int r = Math.min(w, h) / 4;

            g2.setColor(NAVY);
            g2.fill(new Ellipse2D.Double(cx - r - 48, cy - r / 2.0, r + 34, r + 6));
            g2.fill(new Ellipse2D.Double(cx + 10, cy - r / 2.0, r + 34, r + 6));

            g2.setColor(GOLD);
            g2.fill(new Arc2D.Double(cx - r - 48, cy - r / 2.0, r + 34, r + 6, 20, 160, Arc2D.PIE));

            g2.setColor(Color.WHITE);
            g2.fill(new Ellipse2D.Double(cx - r - 18, cy - 10, 22, 22));
            g2.fill(new Ellipse2D.Double(cx + 26, cy - 10, 22, 22));
        } finally {
            g2.dispose();
        }
    }
}
