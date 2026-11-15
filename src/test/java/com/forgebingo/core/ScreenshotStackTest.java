package com.forgebingo.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class ScreenshotStackTest
{
    private static BufferedImage solid(int w, int h, Color c)
    {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(c);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    @Test
    public void twoPicturesAreStackedFirstOnTop()
    {
        BufferedImage out = ScreenshotStack.vertical(Arrays.asList(solid(100, 50, Color.RED), solid(100, 50, Color.BLUE)));
        assertEquals(100, out.getWidth());
        assertEquals(100 + ScreenshotStack.GAP, out.getHeight());
        assertEquals(Color.RED.getRGB(), out.getRGB(50, 25));
        assertEquals(Color.BLACK.getRGB(), out.getRGB(50, 50 + ScreenshotStack.GAP / 2));
        assertEquals(Color.BLUE.getRGB(), out.getRGB(50, 50 + ScreenshotStack.GAP + 25));
    }

    @Test
    public void aNarrowerPictureIsCentred()
    {
        BufferedImage out = ScreenshotStack.vertical(Arrays.asList(solid(100, 20, Color.RED), solid(60, 20, Color.BLUE)));
        assertEquals(100, out.getWidth());
        int y = 20 + ScreenshotStack.GAP + 10;
        assertEquals(Color.BLACK.getRGB(), out.getRGB(10, y));
        assertEquals(Color.BLUE.getRGB(), out.getRGB(50, y));
    }

    @Test
    public void oneOrNone()
    {
        BufferedImage one = solid(10, 10, Color.RED);
        assertSame(one, ScreenshotStack.vertical(Collections.singletonList(one)));
        assertNull(ScreenshotStack.vertical(Collections.emptyList()));
    }
}
