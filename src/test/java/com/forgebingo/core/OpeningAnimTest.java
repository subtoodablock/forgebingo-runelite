package com.forgebingo.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.Test;

public class OpeningAnimTest
{
    @Test
    public void thereAre109UniqueAnimationsSplitByRarity()
    {
        assertEquals(109, OpeningAnim.ALL.size());
        Set<String> names = new HashSet<>();
        Map<OpeningAnim.Rarity, Integer> per = new EnumMap<>(OpeningAnim.Rarity.class);
        for (OpeningAnim.Spec s : OpeningAnim.ALL)
        {
            assertTrue("duplicate " + s.name, names.add(s.name));
            per.merge(s.rarity, 1, Integer::sum);
        }
        assertEquals(60, (int) per.get(OpeningAnim.Rarity.COMMON));
        assertEquals(28, (int) per.get(OpeningAnim.Rarity.RARE));
        assertEquals(15, (int) per.get(OpeningAnim.Rarity.EPIC));
        assertEquals(6, (int) per.get(OpeningAnim.Rarity.LEGENDARY));
    }

    @Test
    public void rarerAnimationsAreTheShowierOnes()
    {
        int worstLegendary = Integer.MAX_VALUE, bestCommon = 0;
        for (OpeningAnim.Spec s : OpeningAnim.ALL)
        {
            int show = s.entrance.show * 10 + s.order.show;
            if (s.rarity == OpeningAnim.Rarity.LEGENDARY) worstLegendary = Math.min(worstLegendary, show);
            if (s.rarity == OpeningAnim.Rarity.COMMON) bestCommon = Math.max(bestCommon, show);
        }
        assertTrue(worstLegendary > bestCommon);
    }

    @Test
    public void rollsFollowTheRarityChances()
    {
        Random rng = new Random(42);
        Map<OpeningAnim.Rarity, Integer> got = new EnumMap<>(OpeningAnim.Rarity.class);
        int n = 20000;
        for (int i = 0; i < n; i++) got.merge(OpeningAnim.roll(rng).rarity, 1, Integer::sum);
        for (OpeningAnim.Rarity r : OpeningAnim.Rarity.values())
        {
            double share = got.getOrDefault(r, 0) / (double) n;
            assertEquals(r.label, r.chance, share, 0.015);
        }
    }

    @Test
    public void everyOrderVisitsEveryTileOnce()
    {
        for (int size = 1; size <= 10; size++)
        {
            for (OpeningAnim.Order o : OpeningAnim.Order.values())
            {
                int[] ranks = OpeningAnim.ranks(o, size, 7);
                Set<Integer> seen = new HashSet<>();
                for (int r : ranks) seen.add(r);
                assertEquals(o + " size " + size, size * size, seen.size());
            }
        }
    }

    @Test
    public void everyEntranceLandsExactlyOnItsTile()
    {
        for (OpeningAnim.Entrance e : OpeningAnim.Entrance.values())
        {
            double[] p = OpeningAnim.pose(e, 1, 3, 100, 80, 30, 0, 0, 300, 300, 150, 280);
            assertEquals(e + " x", 100, p[0], 0.01);
            assertEquals(e + " y", 80, p[1], 0.01);
            assertEquals(e + " scale", 1, p[2], 0.01);
            assertEquals(e + " rotation", 0, p[4], 0.01);
        }
    }
}
