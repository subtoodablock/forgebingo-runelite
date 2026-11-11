package com.forgebingo.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class TileIconsTest
{
    private static ApiModels.Tile tile(String title, String emoji)
    {
        ApiModels.Tile t = new ApiModels.Tile();
        t.title = title;
        t.iconEmoji = emoji;
        t.tasks = Collections.emptyList();
        return t;
    }

    @Test
    public void titlesPickTheRightItem()
    {
        assertEquals(Integer.valueOf(2722), TileIcons.fallbackItem(tile("Hard clue", null)));
        assertEquals(Integer.valueOf(19835), TileIcons.fallbackItem(tile("Master clue", null)));
        assertEquals(Integer.valueOf(20851), TileIcons.fallbackItem(tile("CoX run", null)));
        assertEquals(Integer.valueOf(22473), TileIcons.fallbackItem(tile("ToB run", null)));
        assertEquals(Integer.valueOf(1333), TileIcons.fallbackItem(tile("Combat level", null)));
        assertEquals(Integer.valueOf(11640), TileIcons.fallbackItem(tile("Solo 250K XP", null)));
        assertEquals(Integer.valueOf(21018), TileIcons.fallbackItem(tile("Grandmaster raider", null)));
        assertEquals(Integer.valueOf(6199), TileIcons.fallbackItem(tile("Free space", null)));
    }

    @Test
    public void wordsMustBeWholeWords()
    {
        // "toa" inside "toast" must not pick the ToA icon
        assertNull(TileIcons.fallbackItem(tile("Toast", null)));
    }

    @Test
    public void emojiThenTaskItems()
    {
        assertEquals(Integer.valueOf(4716), TileIcons.fallbackItem(tile("Brothers", "⚰️")));
        ApiModels.Tile t = tile("Something", null);
        ApiModels.Task k = new ApiModels.Task();
        k.itemIds = Arrays.asList(4151);
        t.tasks = Collections.singletonList(k);
        assertEquals(Integer.valueOf(4151), TileIcons.fallbackItem(t));
        assertNull(TileIcons.fallbackItem(tile("Team photo", "📸")));
    }
}
