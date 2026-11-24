package com.forgebingo.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.util.Map;
import org.junit.Test;

/**
 * Proof that baking the pets changed no pixel: what the plugin loads from sprites/pets.png (PetSprites) is exactly
 * what the art code (PetSpritesA..F via PetArt) draws, for every pet and both frames. Fails when the art is edited
 * without re-baking. With the FORGE_BAKE environment variable set it re-bakes instead (see PetSheetBaker).
 */
public class PetSpritesBakedTest
{
    @Test
    public void everyPetFrameLoadedFromTheSheetIsPixelForPixelTheDrawnOne() throws Exception
    {
        if (SheetBaker.bakeRequested())
        {
            PetSheetBaker.bake();
            return;
        }
        int compared = 0;
        int pictures = 0;
        for (Pet p : Pet.values())
        {
            for (int f = 0; f < 2; f++)
            {
                BufferedImage drawn = PetArt.image(p, f);
                SheetBaker.assertSame(p + " frame " + f, drawn, PetSprites.image(p, f), PetSheetBaker.REBAKE);
                compared++;
                if (drawn != null) pictures++;
            }
            assertEquals(p + " drawn()", PetArt.image(p, 0) != null, PetSprites.drawn(p));
        }
        assertEquals(Pet.values().length * 2, compared);
        assertTrue("some pets are drawn", pictures > 0);
        System.out.println("PetSpritesBakedTest: compared " + compared + " pet x frame images, " + pictures + " with a picture");
    }

    @Test
    public void theLoaderKeepsTheOldContract()
    {
        Pet drawn = null;
        for (Pet p : Pet.values()) if (PetArt.image(p, 0) != null) { drawn = p; break; }
        if (drawn == null || SheetBaker.bakeRequested()) return;
        // null pet -> null; any frame other than 0 is the second frame; the same cached instance every call
        assertEquals(null, PetSprites.image(null, 0));
        assertSame(PetSprites.image(drawn, 0), PetSprites.image(drawn, 0));
        assertSame(PetSprites.image(drawn, 1), PetSprites.image(drawn, 7));
        assertSame(PetSprites.image(drawn, 1), PetSprites.image(drawn, -1));
        for (Map.Entry<String, BufferedImage> e : PetSheetBaker.drawn().entrySet())
        {
            if (e.getValue() != null) assertEquals(e.getKey(), BufferedImage.TYPE_INT_ARGB, e.getValue().getType());
        }
    }

    @Test
    public void aMissingSheetMeansNoArtNotAnException()
    {
        assertEquals(null, new SpriteSheet("no-such-sheet").get("ANY|0"));
    }
}
