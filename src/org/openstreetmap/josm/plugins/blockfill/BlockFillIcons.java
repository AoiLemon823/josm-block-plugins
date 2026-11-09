package org.openstreetmap.josm.plugins.blockfill;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import javax.swing.ImageIcon;

/** Icon for the BlockFill (landuse) plugin: a stylized block polygon with land-use fill. */
final class BlockFillIcons {
    private static ImageIcon cached;
    static ImageIcon icon() {
        if (cached != null) return cached;
        int size = 24;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        try {
            // Fill: rounded land-use block in amber-green
            g.setColor(new Color(0xE8, 0xC5, 0x47));
            g.fillRoundRect(3, 3, 18, 18, 4, 4);
            // Border: darker outline
            g.setColor(new Color(0x88, 0x66, 0x00));
            g.drawRoundRect(3, 3, 18, 18, 4, 4);
            // Roads crossing the block (two lines)
            g.setColor(new Color(0x99, 0x99, 0x99));
            g.drawLine(3, 12, 21, 12); // horizontal road
            g.drawLine(12, 3, 12, 21); // vertical road
            // Sub-block fills (different land-use colors)
            g.setColor(new Color(0xC8, 0xE8, 0x87)); // light green - top-left
            g.fillRect(4, 4, 7, 7);
            g.setColor(new Color(0xE0, 0xA0, 0x40)); // amber - top-right
            g.fillRect(13, 4, 7, 7);
            g.setColor(new Color(0x88, 0xBB, 0xDD)); // light blue - bottom-left
            g.fillRect(4, 13, 7, 7);
            g.setColor(new Color(0xD0, 0x70, 0x60)); // terracotta - bottom-right
            g.fillRect(13, 13, 7, 7);
        } finally { g.dispose(); }
        cached = new ImageIcon(img);
        return cached;
    }
    private BlockFillIcons() { }
}
