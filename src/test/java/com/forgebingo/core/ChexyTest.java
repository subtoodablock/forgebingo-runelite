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
    public void drawsEveryStageOfThePowerUp()
    {
        float[] ages = {-1f, 0f, 3f, Chexy.CHARGE_AT + 0.9f, Chexy.GOLD_AT - 0.05f, Chexy.GOLD_AT + 0.1f, 20f,
            Chexy.CHARGE2_AT + 1f, Chexy.BLUE_AT + 0.2f, 60f, 95f};
        for (float size : new float[] {8f, Chexy.SIZE, 60f})
        {
            for (float age : ages)
            {
                for (boolean calm : new boolean[] {false, true})
                {
                    BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = img.createGraphics();
                    Chexy.paint(g, 64, 70, 7.3f + age, 7.3f + age, size, calm, age);
                    g.dispose();
                    assertTrue("nothing drawn at age " + age, lit(img) > 20);
                }
            }
        }
    }

    @Test
    public void powersUpBaseThenGoldThenBlue()
    {
        assertEquals(Chexy.BASE, Chexy.form(-1f));
        assertEquals(Chexy.BASE, Chexy.form(3f));
        assertEquals(Chexy.GOLD, Chexy.form(Chexy.GOLD_AT + 1f));
        assertEquals(Chexy.BLUE, Chexy.form(Chexy.BLUE_AT + 1f));
        // the blue form arrives well inside the shortest visit (60 s)
        assertTrue(Chexy.BLUE_AT < 45f);
        // it charges before each change, and its rings spin up while it does
        assertTrue(Chexy.charge(Chexy.GOLD_AT - 0.5f) > 0.5f);
        assertTrue(Chexy.charge(Chexy.BLUE_AT - 0.5f) > 0.5f);
        assertEquals(0f, Chexy.charge(20f), 0f);
        assertTrue(Chexy.spin(Chexy.GOLD_AT - 0.1f) > Chexy.spin(3f) * 2);
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
