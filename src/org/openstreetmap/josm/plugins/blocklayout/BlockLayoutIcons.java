package org.openstreetmap.josm.plugins.blocklayout;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import javax.swing.ImageIcon;

/** Icon for the BlockLayout (building placement) plugin: three building blocks of varying height. */
final class BlockLayoutIcons {
    private static ImageIcon cached;
    static ImageIcon icon() {
        if (cached != null) return cached;
        int size = 24;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        try {
            // Background: subtle ground
            g.setColor(new Color(0xF0, 0xF0, 0xF0));
            g.fillRect(0, 0, size, size);
            // Building 1: tall (left) 6F
            g.setColor(new Color(0x4A, 0x90, 0xD9));
            g.fillRect(4, 4, 5, 16);
            g.setColor(new Color(0x2A, 0x70, 0xB9));
            g.drawRect(4, 4, 5, 16);
            // Windows
            g.setColor(new Color(0xFF, 0xFF, 0xFF, 180));
            for (int y = 6; y < 19; y += 3) {
                g.fillRect(5, y, 3, 1);
            }
            // Building 2: medium (center) 3F
            g.setColor(new Color(0x5B, 0xA4, 0x6E));
            g.fillRect(10, 9, 5, 11);
            g.setColor(new Color(0x3B, 0x84, 0x4E));
            g.drawRect(10, 9, 5, 11);
            for (int y = 11; y < 19; y += 3) {
                g.fillRect(11, y, 3, 1);
            }
            // Building 3: short (right) 2F
            g.setColor(new Color(0xE0, 0x8A, 0x4A));
            g.fillRect(16, 13, 5, 7);
            g.setColor(new Color(0xC0, 0x6A, 0x2A));
            g.drawRect(16, 13, 5, 7);
            for (int y = 15; y < 19; y += 3) {
                g.fillRect(17, y, 3, 1);
            }
        } finally { g.dispose(); }
        cached = new ImageIcon(img);
        return cached;
    }
    private BlockLayoutIcons() { }
}
