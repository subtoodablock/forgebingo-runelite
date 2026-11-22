package com.forgebingo.core;

import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.time.ZonedDateTime;
import org.junit.Test;

/** The proof stamp is see-through (owner, 2026-11-22): the game behind the box must still show. */
public class ScreenshotStampFadeTest
{
    @Test
    public void gameShowsThroughTheStampBox()
    {
        BufferedImage src = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 800; x++) for (int y = 0; y < 600; y++) src.setRGB(x, y, new Color(200, 160, 40).getRGB());
        BufferedImage out = ScreenshotStamp.stamp(src, "dragon", ZonedDateTime.now(), "Zezima", "Any Zulrah unique");
        // a spot inside the box but away from the letters: the right end of the padding on the first line
        Color c = new Color(out.getRGB(2, 2));
        // at least half of the game's brightness is kept (the old box kept a quarter)
        assertTrue("box too dark: " + c, c.getRed() >= 100 && c.getGreen() >= 80);
        // and the box is still there (darker than the game), so the text has a backing
        assertTrue("no box: " + c, c.getRed() < 200);
        // outside the box nothing changes
        assertTrue(new Color(out.getRGB(790, 590)).equals(new Color(200, 160, 40)));
    }
}
