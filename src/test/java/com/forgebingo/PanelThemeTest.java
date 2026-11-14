package com.forgebingo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.forgebingo.core.Critter;
import com.forgebingo.core.CritterSprite;
import com.forgebingo.core.PixelArt;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Map;
import org.junit.After;
import org.junit.Test;

public class PanelThemeTest
{
    @After
    public void backToClassic()
    {
        ForgeBingoPanel.useTheme(PanelTheme.CLASSIC);
    }

    @Test
    public void everyThemeIsComplete()
    {
        for (PanelTheme t : PanelTheme.values())
        {
            assertEquals(t.name(), 12, t.palette.length);
            assertTrue(t.name(), t.cast.length > 0);
            assertTrue(t.name(), t.count >= 1 && t.count <= 6);
            assertNotNull(t.name(), t.falls);
            assertTrue(t.name(), t.toString().length() > 3);
        }
    }

    @Test
    public void classicIsTheLookThePluginAlwaysHad()
    {
        ForgeBingoPanel.useTheme(PanelTheme.BLOOD);
        Map<Color, Color> back = PanelUi.applyTheme(PanelTheme.CLASSIC);
        assertEquals(new Color(0xffbf1f), PanelUi.GOLD);
        assertEquals(new Color(0x363029), PanelUi.CARD_TOP);
        assertEquals(new Color(0xe6dfd3), PanelUi.TEXT);
        // the map takes every blood colour back to its classic one
        assertEquals(new Color(0xffbf1f), back.get(new Color(PanelTheme.BLOOD.palette[6])));
        assertEquals(PanelTheme.CLASSIC, PanelUi.theme);
    }

    @Test
    public void everyCreatureHasBothWalkFramesInTheSharedBox()
    {
        for (Critter c : Critter.values())
        {
            for (int v = 0; v < c.variants; v++)
            {
                for (int f = 0; f < 2; f++)
                {
                    BufferedImage img = CritterSprite.image(c, v, f);
                    assertNotNull(c + " " + v + " " + f, img);
                    assertEquals(c.name(), PixelArt.WIDTH, img.getWidth());
                    assertEquals(c.name(), PixelArt.HEIGHT, img.getHeight());
                }
            }
        }
    }

    @Test
    public void themedCreaturesAreNotTheImp()
    {
        BufferedImage imp = CritterSprite.image(Critter.IMP, 0, 0);
        for (Critter c : Critter.values())
        {
            if (c == Critter.IMP) continue;
            assertTrue(c + " is still drawn as the imp", differs(imp, CritterSprite.image(c, 0, 0)));
        }
        for (Critter.Fall f : Critter.Fall.values()) assertNotNull(f.name(), CritterSprite.fall(f, 0));
    }

    private static boolean differs(BufferedImage a, BufferedImage b)
    {
        for (int y = 0; y < a.getHeight(); y++)
        {
            for (int x = 0; x < a.getWidth(); x++) if (a.getRGB(x, y) != b.getRGB(x, y)) return true;
        }
        return false;
    }
}
