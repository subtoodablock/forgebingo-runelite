package com.forgebingo.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class ChestOpensTest
{
    private static Map<Integer, Integer> of(int... pairs)
    {
        Map<Integer, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) m.put(pairs[i], pairs[i + 1]);
        return m;
    }

    @Test
    public void bothChestsReportWhenEachShowsItsOwnLoot()
    {
        ChestOpens c = new ChestOpens();
        assertEquals(of(560, 300, 1513, 80), c.opened(of(560, 300, 1513, 80))); // standard chest
        assertEquals(of(34719, 1), c.opened(of(34719, 1)));                     // unique chest
    }

    @Test
    public void secondChestReportsOnlyTheNewItemsWhenLootIsAddedTogether()
    {
        ChestOpens c = new ChestOpens();
        assertEquals(of(560, 300), c.opened(of(560, 300)));
        assertEquals(of(34719, 1), c.opened(of(560, 300, 34719, 1)));
    }

    @Test
    public void openingTheSameChestAgainReportsNothing()
    {
        ChestOpens c = new ChestOpens();
        c.opened(of(34719, 1));
        assertTrue(c.opened(of(34719, 1)).isEmpty());
    }

    @Test
    public void theNextRaidStartsFresh()
    {
        ChestOpens c = new ChestOpens();
        c.opened(of(34719, 1));
        c.reset();
        assertEquals(of(34719, 1), c.opened(of(34719, 1)));
    }
}
