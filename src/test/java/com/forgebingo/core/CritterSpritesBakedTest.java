package com.forgebingo.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.Test;

/**
 * Proof that baking the creatures changed no pixel: what the plugin loads from sprites/critters.png (CritterSprite)
 * is exactly what the art code (RaidSprites and CryptSprites via CritterArt) draws, for every creature, variant,
 * frame and falling thing, including out-of-range variants and frames. Fails when the art is edited without
 * re-baking. With the FORGE_BAKE environment variable set it re-bakes instead (see CritterSheetBaker).
 */
public class CritterSpritesBakedTest
{
    private static final int[] VARIANTS = {Integer.MIN_VALUE, -7, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5, 6, 7, 100, Integer.MAX_VALUE};
    private static final int[] FRAMES = {Integer.MIN_VALUE, -1, 0, 1, 2, 7, Integer.MAX_VALUE};

    @Test
    public void everyBakedPictureIsPixelForPixelTheDrawnOne() throws Exception
    {
        if (SheetBaker.bakeRequested())
        {
            CritterSheetBaker.bake();
            return;
        }
        SpriteSheet sheet = new SpriteSheet("critters");
        int pictures = 0;
        for (Map.Entry<String, BufferedImage> e : CritterSheetBaker.drawn().entrySet())
        {
            SheetBaker.assertSame(e.getKey(), e.getValue(), sheet.get(e.getKey()), CritterSheetBaker.REBAKE);
            if (e.getValue() != null) pictures++;
        }
        // and the sheet holds nothing else
        int lines = 0;
        try (InputStream in = SpriteSheet.class.getResourceAsStream("/com/forgebingo/sprites/critters.txt"))
        {
            assertNotNull("critters.txt is missing - " + CritterSheetBaker.REBAKE, in);
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n"))
            {
                if (line.trim().split(" ").length == 5) lines++;
            }
        }
        assertEquals("pictures in critters.txt - " + CritterSheetBaker.REBAKE, pictures, lines);
        System.out.println("CritterSpritesBakedTest: compared " + pictures + " baked pictures key by key");
    }

    @Test
    public void everyInputInAndOutOfRangeGetsThePictureItAlwaysGot()
    {
        if (SheetBaker.bakeRequested()) return;
        int compared = 0;
        Critter[] critters = new Critter[Critter.values().length + 1]; // plus null, which shows the imp
        System.arraycopy(Critter.values(), 0, critters, 0, Critter.values().length);
        for (Critter c : critters)
        {
            for (int v : VARIANTS)
            {
                for (int f : FRAMES)
                {
                    BufferedImage old = CritterArt.image(c, v, f);
                    assertNotNull(c + " always has a picture", old);
                    SheetBaker.assertSame(c + " variant " + v + " frame " + f, old, CritterSprite.image(c, v, f), CritterSheetBaker.REBAKE);
                    compared++;
                }
            }
        }
        assertNull(CritterSprite.fall(null, 0));
        for (Critter.Fall fall : Critter.Fall.values())
        {
            for (int v : VARIANTS)
            {
                BufferedImage old = CritterArt.fall(fall, v);
                assertNotNull(fall + " is drawn", old);
                SheetBaker.assertSame("fall " + fall + " variant " + v, old, CritterSprite.fall(fall, v), CritterSheetBaker.REBAKE);
                compared++;
            }
        }
        System.out.println("CritterSpritesBakedTest: compared " + compared + " critter/fall inputs (in and out of range)");
    }

    @Test
    public void theSameInstanceComesBackWheneverTheArtCodeGaveTheSameInstance()
    {
        if (SheetBaker.bakeRequested()) return;
        // callers may hold on to pictures: equal inputs (after wrapping) must give one shared, cached image
        for (Critter c : Critter.values())
        {
            for (int v : VARIANTS)
            {
                for (int f : FRAMES)
                {
                    boolean oldSame = CritterArt.image(c, v, f) == CritterArt.image(c, 0, 0);
                    boolean newSame = CritterSprite.image(c, v, f) == CritterSprite.image(c, 0, 0);
                    assertEquals(c + " variant " + v + " frame " + f + " shares its picture with variant 0 frame 0", oldSame, newSame);
                }
            }
        }
        for (Critter.Fall fall : Critter.Fall.values())
        {
            for (int v : VARIANTS)
            {
                boolean oldSame = CritterArt.fall(fall, v) == CritterArt.fall(fall, 0);
                boolean newSame = CritterSprite.fall(fall, v) == CritterSprite.fall(fall, 0);
                assertEquals("fall " + fall + " variant " + v + " shares its picture with variant 0", oldSame, newSame);
            }
        }
        assertSame(CritterSprite.image(Critter.CRAB, 5, 3), CritterSprite.image(Critter.CRAB, 1, 1));
    }
}
