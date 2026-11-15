package com.forgebingo;

import static org.junit.Assert.assertEquals;

import com.forgebingo.core.GameEvent;
import org.junit.Test;

/** Owner, 2026-11-14: clues get one picture; raid, Barrows and Moons chests (and drops / kills) keep two. */
public class ShotCountTest
{
    @Test
    public void cluesTakeOnePicture()
    {
        assertEquals(1, ForgeBingoPlugin.shotDelays(GameEvent.CLUE, "hard", false).length);
        assertEquals(1, ForgeBingoPlugin.shotDelays(GameEvent.KC, "Clue scroll (elite)", false).length);
    }

    @Test
    public void chestsDropsAndKillsTakeTwo()
    {
        assertEquals(2, ForgeBingoPlugin.shotDelays(GameEvent.RAID_CHEST, "Chambers of Xeric", true).length);
        assertEquals(2, ForgeBingoPlugin.shotDelays(GameEvent.RAID_CHEST, "Barrows", true).length);
        assertEquals(2, ForgeBingoPlugin.shotDelays(GameEvent.RAID_CHEST, "Lunar Chest", true).length);
        assertEquals(2, ForgeBingoPlugin.shotDelays(GameEvent.NPC, null, false).length);
        assertEquals(2, ForgeBingoPlugin.shotDelays(GameEvent.KC, "Vorkath", false).length);
    }
}
