package com.forgebingo;

import static org.junit.Assert.assertEquals;

import com.forgebingo.core.GameEvent;
import org.junit.Test;

/** Owner, 2026-11-14: only reward chests (raids, Barrows, Moons) get two pictures; clues, drops and kills get one. */
public class ShotCountTest
{
    @Test
    public void cluesDropsAndKillsTakeOnePicture()
    {
        assertEquals(1, ForgeBingoPlugin.shotDelays(GameEvent.CLUE, "hard", false).length);
        assertEquals(1, ForgeBingoPlugin.shotDelays(GameEvent.KC, "Clue scroll (elite)", false).length);
        assertEquals(1, ForgeBingoPlugin.shotDelays(GameEvent.NPC, null, false).length);
        assertEquals(1, ForgeBingoPlugin.shotDelays(GameEvent.KC, "Vorkath", false).length);
        assertEquals(1, ForgeBingoPlugin.shotDelays(GameEvent.KC, "Chambers of Xeric", false).length);
    }

    @Test
    public void rewardChestsTakeTwo()
    {
        assertEquals(2, ForgeBingoPlugin.shotDelays(GameEvent.RAID_CHEST, "Chambers of Xeric", true).length);
        assertEquals(2, ForgeBingoPlugin.shotDelays(GameEvent.RAID_CHEST, "Barrows", true).length);
        assertEquals(2, ForgeBingoPlugin.shotDelays(GameEvent.RAID_CHEST, "Lunar Chest", true).length);
    }
}
