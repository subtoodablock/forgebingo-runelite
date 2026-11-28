package com.forgebingo.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class NpcLootMergeTest
{
    /** every tick up to and including {@code last}: what the older reports send in total */
    private static int flushed(NpcLootMerge<String> m, int from, int last)
    {
        int n = 0;
        for (int t = from; t <= last; t++) n += m.flush(t).size();
        return n;
    }

    @Test
    public void anOrdinaryKillIsSentOnceWhenTheOlderReportComesFirst()
    {
        NpcLootMerge<String> m = new NpcLootMerge<>();
        m.onLegacy("Abyssal demon", 100, "old");
        m.onServer("Abyssal demon", 100); // the caller sends this one
        assertEquals(0, flushed(m, 100, 120));
    }

    @Test
    public void anOrdinaryKillIsSentOnceWhenTheGameReportsFirst()
    {
        NpcLootMerge<String> m = new NpcLootMerge<>();
        m.onServer("Abyssal demon", 100);
        m.onLegacy("abyssal demon", 100, "old");
        assertEquals(0, flushed(m, 100, 120));
    }

    @Test
    public void theGamesReportATickBehindStillPairsUp()
    {
        NpcLootMerge<String> m = new NpcLootMerge<>();
        m.onLegacy("Vorkath", 100, "old");
        assertTrue(m.flush(101).isEmpty()); // still held
        m.onServer("Vorkath", 101);
        assertEquals(0, flushed(m, 102, 120));
    }

    @Test
    public void lootThatLandsLateIsNotCountedAgain()
    {
        NpcLootMerge<String> m = new NpcLootMerge<>();
        m.onServer("The Nightmare", 100);
        m.onLegacy("The Nightmare", 100 + NpcLootMerge.LATE_WINDOW, "old");
        assertEquals(0, flushed(m, 100, 140));
    }

    @Test
    public void aMonsterOnlyTheGameReportsNeedsNothingFromTheOlderReport()
    {
        NpcLootMerge<String> m = new NpcLootMerge<>();
        m.onServer("The Whisperer", 100);
        assertEquals(0, flushed(m, 100, 200));
    }

    @Test
    public void aKillTheGameNeverReportsIsStillSentTwoTicksLater()
    {
        NpcLootMerge<String> m = new NpcLootMerge<>();
        m.onLegacy("Goblin", 100, "old");
        assertTrue(m.flush(100).isEmpty());
        assertTrue(m.flush(101).isEmpty());
        List<String> out = m.flush(102);
        assertEquals(1, out.size());
        assertEquals("old", out.get(0));
        assertEquals(0, m.heldCount());
    }

    @Test
    public void severalKillsOfOneKindInATickEachCountOnce()
    {
        NpcLootMerge<String> m = new NpcLootMerge<>();
        for (int i = 0; i < 5; i++) m.onLegacy("Maniacal monkey", 100, "old" + i);
        for (int i = 0; i < 5; i++) m.onServer("Maniacal monkey", 100);
        assertEquals(0, flushed(m, 100, 120));
        // and when the game reports only three of five, the other two are sent from the older report
        NpcLootMerge<String> n = new NpcLootMerge<>();
        for (int i = 0; i < 5; i++) n.onLegacy("Maniacal monkey", 100, "old" + i);
        for (int i = 0; i < 3; i++) n.onServer("Maniacal monkey", 100);
        assertEquals(2, flushed(n, 100, 120));
    }

    @Test
    public void differentMonstersDoNotCancelEachOther()
    {
        NpcLootMerge<String> m = new NpcLootMerge<>();
        m.onServer("Vardorvis", 100);
        m.onLegacy("Duke Sucellus", 100, "old");
        assertEquals(1, flushed(m, 100, 120));
    }

    @Test
    public void anOldGameReportDoesNotSwallowAMuchLaterKill()
    {
        NpcLootMerge<String> m = new NpcLootMerge<>();
        m.onServer("The Whisperer", 100);
        m.flush(101);
        m.onLegacy("The Whisperer", 100 + NpcLootMerge.LATE_WINDOW + 1, "old");
        assertEquals(1, flushed(m, 100, 200));
    }
}
