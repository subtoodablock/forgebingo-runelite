package com.forgebingo.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import org.junit.Test;

public class PetTest
{
    @Test
    public void thereAreSeventyTwoPets()
    {
        assertEquals(72, Pet.values().length);
    }

    @Test
    public void rarePetsTurnUpFarLessOftenThanCommonOnes()
    {
        Map<Pet.Rarity, Integer> seen = new EnumMap<>(Pet.Rarity.class);
        Random r = new Random(7);
        int draws = 200_000;
        for (int i = 0; i < draws; i++)
        {
            Pet p = Pet.pick(r.nextDouble(), x -> true);
            assertNotNull(p);
            seen.merge(p.rarity, 1, Integer::sum);
        }
        int common = seen.getOrDefault(Pet.Rarity.COMMON, 0);
        int rare = seen.getOrDefault(Pet.Rarity.RARE, 0);
        int legendary = seen.getOrDefault(Pet.Rarity.LEGENDARY, 0);
        assertTrue("common pets are most of what you see", common > draws / 2);
        assertTrue(rare < common / 4);
        // the two legendary pets together: about 1 in 2,800
        assertTrue("legendary pets are almost never seen: " + legendary, legendary > 0 && legendary < draws / 1000);
    }

    @Test
    public void onlyDrawnPetsArePicked()
    {
        assertNull(Pet.pick(0.5, x -> false));
        for (double roll = 0; roll < 1; roll += 0.01)
        {
            assertEquals(Pet.HERON, Pet.pick(roll, x -> x == Pet.HERON));
        }
    }

    @Test
    public void everyDrawnPetHasBothFramesInTheSharedBox()
    {
        for (Pet p : Pet.values())
        {
            if (!PetSprites.drawn(p)) continue;
            for (int f = 0; f < 2; f++)
            {
                BufferedImage img = PetSprites.image(p, f);
                assertNotNull(p + " frame " + f, img);
                assertEquals(PixelArt.WIDTH, img.getWidth());
                assertEquals(PixelArt.HEIGHT, img.getHeight());
            }
        }
    }
}
