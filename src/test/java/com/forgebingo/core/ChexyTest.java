package com.forgebingo.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.Test;

public class ChexyTest
{
    @Test
    public void drawsAtSeveralSizesAndTimes()
    {
        float[] sizes = {8f, Chexy.SIZE, 60f};
        float[] times = {0f, 0.37f, 5f, 123.4f, 9999f};
        for (float size : sizes)
        {
            for (float t : times)
            {
                for (boolean calm : new boolean[] {false, true})
                {
                    BufferedImage img = new BufferedImage(96, 96, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = img.createGraphics();
                    Chexy.paint(g, 48, 48, t, size, calm);
                    g.dispose();
                    assertTrue("nothing drawn at size " + size + " t " + t, lit(img) > 20);
                }
            }
        }
    }

    @Test
    public void stillFitsTheCreatureGrid()
    {
        BufferedImage img = Chexy.still();
        assertEquals(PixelArt.WIDTH, img.getWidth());
        assertEquals(PixelArt.HEIGHT, img.getHeight());
        assertTrue(lit(img) > 50);
    }

    private static int lit(BufferedImage img)
    {
        int n = 0;
        for (int y = 0; y < img.getHeight(); y++)
        {
            for (int x = 0; x < img.getWidth(); x++) if ((img.getRGB(x, y) >>> 24) > 40) n++;
        }
        return n;
    }
}
