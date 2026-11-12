package com.forgebingo.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.junit.Test;

public class ScreenshotStampTest
{
    private static final ZonedDateTime T = ZonedDateTime.of(2026, 11, 6, 14, 5, 0, 0, ZoneOffset.ofHours(2));

    private static BufferedImage white(int w, int h)
    {
        BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) b.setRGB(x, y, Color.WHITE.getRGB());
        return b;
    }

    @Test
    public void stampsTopLeftAndKeepsSize()
    {
        BufferedImage src = white(765, 503);
        BufferedImage out = ScreenshotStamp.stamp(src, "Zulrah42", T);
        assertEquals(765, out.getWidth());
        assertEquals(503, out.getHeight());
        assertNotEquals(Color.WHITE.getRGB() & 0xffffff, out.getRGB(2, 2) & 0xffffff);
        assertEquals(Color.WHITE.getRGB() & 0xffffff, out.getRGB(700, 450) & 0xffffff);
        assertEquals(Color.WHITE.getRGB(), src.getRGB(2, 2));
    }

    /** Tile, player, code and time make a taller box than the time alone (four lines instead of one). */
    @Test
    public void proofStampHasTilePlayerCodeAndTime()
    {
        BufferedImage full = ScreenshotStamp.stamp(white(765, 503), "Zulrah42", T, "Goonferno", "Big bones x10");
        BufferedImage bare = ScreenshotStamp.stamp(white(765, 503), null, T, null, null);
        int fullBox = boxHeight(full), bareBox = boxHeight(bare);
        assertTrue("4-line box " + fullBox + " vs 1-line " + bareBox, fullBox > bareBox * 3);
    }

    private static int boxHeight(BufferedImage img)
    {
        int y = 0;
        while (y < img.getHeight() && (img.getRGB(1, y) & 0xffffff) != (Color.WHITE.getRGB() & 0xffffff)) y++;
        return y;
    }

    @Test
    public void stampsTimeWithoutKeyword()
    {
        BufferedImage out = ScreenshotStamp.stamp(white(400, 300), null, T);
        assertNotEquals(Color.WHITE.getRGB() & 0xffffff, out.getRGB(2, 2) & 0xffffff);
        assertTrue(ScreenshotStamp.timeText(T).startsWith("2026-11-06 14:05 UTC+02:00"));
    }
}
