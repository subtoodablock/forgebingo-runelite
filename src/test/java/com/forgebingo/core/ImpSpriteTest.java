package com.forgebingo.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import org.junit.Test;

public class ImpSpriteTest
{
    private static int filled(BufferedImage img)
    {
        int n = 0;
        for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) if ((img.getRGB(x, y) >>> 24) != 0) n++;
        return n;
    }

    private static boolean same(BufferedImage a, BufferedImage b)
    {
        for (int y = 0; y < a.getHeight(); y++) for (int x = 0; x < a.getWidth(); x++) if (a.getRGB(x, y) != b.getRGB(x, y)) return false;
        return true;
    }

    @Test
    public void plainImpHasTheWebsiteSize()
    {
        BufferedImage img = ImpSprite.image(ImpSprite.Hat.NONE, ImpSprite.Item.NONE, 0);
        assertEquals(42, img.getWidth());
        assertEquals(34, img.getHeight());
        assertTrue("an imp is drawn", filled(img) > 250);
    }

    @Test
    public void feetStandOnTheBottomEdge()
    {
        BufferedImage img = ImpSprite.image(ImpSprite.Hat.NONE, ImpSprite.Item.NONE, 0);
        int bottom = 0;
        for (int x = 0; x < img.getWidth(); x++) if ((img.getRGB(x, img.getHeight() - 1) >>> 24) != 0) bottom++;
        assertTrue(bottom > 0);
    }

    @Test
    public void skinIsTheWebsiteRed()
    {
        BufferedImage img = ImpSprite.image(ImpSprite.Hat.NONE, ImpSprite.Item.NONE, 0);
        int red = 0;
        for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) if (img.getRGB(x, y) == 0xffe0402e) red++;
        assertTrue(red > 60);
    }

    @Test
    public void walkFramesDiffer()
    {
        assertTrue(!same(ImpSprite.image(ImpSprite.Hat.NONE, ImpSprite.Item.NONE, 0), ImpSprite.image(ImpSprite.Hat.NONE, ImpSprite.Item.NONE, 1)));
    }

    @Test
    public void hatsAndItemsAddPixels()
    {
        int plain = filled(ImpSprite.image(ImpSprite.Hat.NONE, ImpSprite.Item.NONE, 0));
        for (ImpSprite.Hat h : ImpSprite.Hat.values())
        {
            if (h != ImpSprite.Hat.NONE) assertNotEquals(h.name(), plain, filled(ImpSprite.image(h, ImpSprite.Item.NONE, 0)));
        }
        for (ImpSprite.Item i : ImpSprite.Item.values())
        {
            if (i != ImpSprite.Item.NONE) assertTrue(i.name(), filled(ImpSprite.image(ImpSprite.Hat.NONE, i, 0)) > plain);
        }
    }

    @Test
    public void framesAreCached()
    {
        assertSame(ImpSprite.image(ImpSprite.Hat.WIZARD, ImpSprite.Item.AYAK, 1), ImpSprite.image(ImpSprite.Hat.WIZARD, ImpSprite.Item.AYAK, 1));
    }
}
