package com.forgebingo.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Test;

/** The finished tile card's "Finished by": what each teammate's share is called, and how long ago it was. */
public class FinishedCardTest
{
    private static ApiModels.Tile tile(String... sourceTypes)
    {
        ApiModels.Tile t = new ApiModels.Tile();
        t.tasks = new ArrayList<>();
        for (String st : sourceTypes)
        {
            ApiModels.Task k = new ApiModels.Task();
            k.sourceType = st;
            t.tasks.add(k);
        }
        return t;
    }

    @Test
    public void killTilesCountKillsAndDropTilesCountDrops()
    {
        assertEquals("14 kills", TileInfo.contributed(tile("kc"), 14));
        assertEquals("1 kill", TileInfo.contributed(tile("kc", "kc"), 1));
        assertEquals("3 drops", TileInfo.contributed(tile("npc", "raid_chest"), 3));
        assertEquals("1 drop", TileInfo.contributed(tile("clue"), 1));
    }

    @Test
    public void mixedAndScreenshotTilesShowJustTheNumber()
    {
        assertEquals("x2", TileInfo.contributed(tile("kc", "npc"), 2));
        assertEquals("x1", TileInfo.contributed(tile("manual"), 1));
        assertEquals("x4", TileInfo.contributed(tile(), 4));
        assertEquals("x4", TileInfo.contributed(null, 4));
    }

    @Test
    public void finishedAgoReadsTheServersTime()
    {
        long now = java.time.OffsetDateTime.parse("2026-11-23T12:00:00+00:00").toInstant().toEpochMilli();
        assertEquals("Finished just now", TileInfo.finishedAgo("2026-11-23T11:59:40.5+00:00", now));
        assertEquals("Finished 1 minute ago", TileInfo.finishedAgo("2026-11-23T11:58:30+00:00", now));
        assertEquals("Finished 45 minutes ago", TileInfo.finishedAgo("2026-11-23T11:15:00+00:00", now));
        assertEquals("Finished 1 hour ago", TileInfo.finishedAgo("2026-11-23T10:30:00+00:00", now));
        assertEquals("Finished 2 days ago", TileInfo.finishedAgo("2026-11-21T05:42:20.494322+00:00", now));
        // a clock that is ahead of the server never shows a time in the future
        assertEquals("Finished just now", TileInfo.finishedAgo("2026-11-23T12:05:00+00:00", now));
    }

    @Test
    public void noTimeOrABrokenOneShowsNothing()
    {
        assertNull(TileInfo.finishedAgo(null, 0));
        assertNull(TileInfo.finishedAgo("", 0));
        assertNull(TileInfo.finishedAgo("yesterday", 0));
    }

    @Test
    public void freeSpaceOnlyWhenTheServerSaysSo()
    {
        ApiModels.Tile t = tile("manual");
        assertFalse(TileInfo.isFree(t)); // older servers send nothing
        t.free = true;
        assertTrue(TileInfo.isFree(t));
        assertFalse(TileInfo.isFree(null));
    }

    @Test
    public void namesFromTheServerAreCapped()
    {
        ApiModels.BoardResponse b = new ApiModels.BoardResponse();
        ApiModels.Tile t = tile("kc");
        ApiModels.Contributor ok = new ApiModels.Contributor();
        ok.name = "goonferno";
        ok.qty = 3;
        ApiModels.Contributor none = new ApiModels.Contributor();
        none.name = "nobody";
        none.qty = 0;
        t.contributors = new ArrayList<>(Arrays.asList(ok, none, null));
        b.tiles = new ArrayList<>(Arrays.asList(t));
        Sanitizer.board(b);
        assertEquals(1, b.tiles.get(0).contributors.size());
        assertEquals("goonferno", b.tiles.get(0).contributors.get(0).name);
    }
}
