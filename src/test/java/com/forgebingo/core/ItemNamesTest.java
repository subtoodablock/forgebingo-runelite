package com.forgebingo.core;

import static org.junit.Assert.*;

import java.util.Arrays;
import org.junit.Test;

public class ItemNamesTest
{
    @Test
    public void resolvesPetsWithoutAnyCatalogue()
    {
        ItemNames n = new ItemNames();
        assertEquals(Integer.valueOf(12646), n.resolve("Baby mole").get());
        assertFalse(n.resolve("Abyssal whip").isPresent());
        assertFalse(n.resolve("").isPresent());
        assertFalse(n.resolve(null).isPresent());
    }

    @Test
    public void catalogueAndBoardNames()
    {
        ItemNames n = new ItemNames();
        n.setCatalogue(Arrays.asList(new ApiModels.CatalogueItem(4151, "Abyssal whip"), new ApiModels.CatalogueItem(7981, "Kq head"),
            new ApiModels.CatalogueItem(20000, "Dup"), new ApiModels.CatalogueItem(19999, "dup"), null,
            new ApiModels.CatalogueItem(1, null)));
        assertEquals(Integer.valueOf(4151), n.resolve("abyssal  WHIP").get());
        assertEquals(Integer.valueOf(7981), n.resolve("KQ head").get());
        assertEquals(Integer.valueOf(19999), n.resolve("Dup").get()); // lowest id wins
        n.setBoard(Arrays.asList(new ApiModels.CatalogueItem(22111, "Dragonbone necklace")));
        assertEquals(Integer.valueOf(22111), n.resolve("Dragonbone necklace").get());
        n.setBoard(null);
        assertFalse(n.resolve("Dragonbone necklace").isPresent());
        assertEquals(Integer.valueOf(4151), n.resolve("Abyssal whip").get());
    }

    @Test
    public void normalize()
    {
        assertEquals("lil' zik", ItemNames.normalize("  Lil’ Zik "));
        assertEquals("", ItemNames.normalize(null));
    }
}
