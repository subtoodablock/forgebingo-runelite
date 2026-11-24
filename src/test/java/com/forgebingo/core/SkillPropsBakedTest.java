package com.forgebingo.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.Test;

/**
 * Proof that baking the skill props changed no pixel: what the plugin loads from sprites/skillprops.png (SkillProps)
 * is exactly what the art code (SkillPropArt) draws, for every prop, stage and frame, including out-of-range stages
 * and frames and the null prop. Null-ness matters too: ThemeFx uses "image(prop, 2, 0) == null" as "has no art".
 * Fails when the art is edited without re-baking. With the FORGE_BAKE environment variable set it re-bakes instead
 * (see SkillPropSheetBaker).
 */
public class SkillPropsBakedTest
{
    private static final int[] STAGES = {Integer.MIN_VALUE, -5, -1, 0, 1, 2, 3, 4, 100, Integer.MAX_VALUE};
    private static final int[] FRAMES = {Integer.MIN_VALUE, -1, 0, 1, 2, 7, Integer.MAX_VALUE};

    @Test
    public void everyBakedPictureIsPixelForPixelTheDrawnOne() throws Exception
    {
        if (SheetBaker.bakeRequested())
        {
            SkillPropSheetBaker.bake();
            return;
        }
        SpriteSheet sheet = new SpriteSheet("skillprops");
        int keys = 0;
        int pictures = 0;
        for (Map.Entry<String, BufferedImage> e : SkillPropSheetBaker.drawn().entrySet())
        {
            SheetBaker.assertSame(e.getKey(), e.getValue(), sheet.get(e.getKey()), SkillPropSheetBaker.REBAKE);
            keys++;
            if (e.getValue() != null) pictures++;
        }
        assertEquals(SkillProp.values().length * SkillProp.STAGES * 2, keys);
        // and the sheet holds nothing else
        int lines = 0;
        try (InputStream in = SpriteSheet.class.getResourceAsStream("/com/forgebingo/sprites/skillprops.txt"))
        {
            assertNotNull("skillprops.txt is missing - " + SkillPropSheetBaker.REBAKE, in);
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n"))
            {
                if (line.trim().split(" ").length == 5) lines++;
            }
        }
        assertEquals("pictures in skillprops.txt - " + SkillPropSheetBaker.REBAKE, pictures, lines);
        System.out.println("SkillPropsBakedTest: compared " + keys + " prop x stage x frame keys, " + pictures + " with a picture");
    }

    @Test
    public void everyInputInAndOutOfRangeGetsThePictureItAlwaysGot()
    {
        if (SheetBaker.bakeRequested()) return;
        int compared = 0;
        for (int st : STAGES)
        {
            for (int f : FRAMES)
            {
                assertNull(SkillPropArt.image(null, st, f));
                assertNull(SkillProps.image(null, st, f));
                for (SkillProp p : SkillProp.values())
                {
                    BufferedImage old = SkillPropArt.image(p, st, f);
                    BufferedImage now = SkillProps.image(p, st, f);
                    SheetBaker.assertSame(p + " stage " + st + " frame " + f, old, now, SkillPropSheetBaker.REBAKE);
                    // the same cached instance whenever the art code gave the same instance
                    assertEquals(p + " stage " + st + " frame " + f + " shares its picture with stage 2 frame 0",
                        old == SkillPropArt.image(p, 2, 0), now == SkillProps.image(p, 2, 0));
                    compared++;
                }
            }
        }
        // ThemeFx's "has art" test
        for (SkillProp p : SkillProp.values())
        {
            assertEquals(p + " has art", SkillPropArt.image(p, 2, 0) == null, SkillProps.image(p, 2, 0) == null);
        }
        System.out.println("SkillPropsBakedTest: compared " + compared + " prop inputs (in and out of range)");
    }
}
