package com.forgebingo.core;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Several pictures of one drop or kill go up as a single image, one above the other in the order they were taken
 * (owner, 2026-11-14), so a reviewer sees them together instead of opening each.
 */
public final class ScreenshotStack
{
    /** dark line between two pictures, so it is clear where one ends */
    public static final int GAP = 6;

    private ScreenshotStack()
    {
    }

    /** The pictures one above the other, first on top. One picture comes back as it is; none gives null. */
    public static BufferedImage vertical(List<BufferedImage> frames)
    {
        if (frames == null || frames.isEmpty()) return null;
        if (frames.size() == 1) return frames.get(0);
        int w = 0;
        int h = GAP * (frames.size() - 1);
        for (BufferedImage f : frames)
        {
            w = Math.max(w, f.getWidth());
            h += f.getHeight();
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try
        {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, w, h);
            int y = 0;
            for (BufferedImage f : frames)
            {
                // a picture taken after the window was resized is centred
                g.drawImage(f, (w - f.getWidth()) / 2, y, null);
                y += f.getHeight() + GAP;
            }
        }
        finally
        {
            g.dispose();
        }
        return out;
    }
}
