package com.forgebingo.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.awt.image.BufferedImage;
import org.junit.Test;

public class WindowShotTest
{
    private static BufferedImage filled(int w, int h, int rgb)
    {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
        {
            for (int x = 0; x < w; x++)
            {
                img.setRGB(x, y, rgb);
            }
        }
        return img;
    }

    @Test
    public void gameIsLaidOverTheWindowAtTheCanvas()
    {
        BufferedImage window = filled(300, 200, 0x112233); // sidebar colour
        BufferedImage game = filled(100, 100, 0xff0000);
        BufferedImage out = WindowShot.compose(window, game, 10, 20, 100, 100);
        assertEquals(300, out.getWidth());
        assertEquals(200, out.getHeight());
        assertEquals(0xff0000, out.getRGB(50, 50) & 0xffffff);   // game area
        assertEquals(0x112233, out.getRGB(250, 100) & 0xffffff); // sidebar kept
    }

    @Test
    public void fallsBackToTheGameScreen()
    {
        BufferedImage game = filled(100, 100, 0xff0000);
        assertSame(game, WindowShot.compose(null, game, 0, 0, 100, 100));
        assertSame(game, WindowShot.compose(filled(50, 50, 0), game, 0, 0, 100, 100)); // canvas does not fit
        assertSame(game, WindowShot.compose(filled(WindowShot.MAX_SIDE + 1, 10, 0), game, 0, 0, 5, 5)); // too big
    }
}
