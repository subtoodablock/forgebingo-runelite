package com.forgebingo.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.Test;

public class OpeningAnimTest
{
    @Test
    public void thereAre100UniqueAnimationsSplitByRarity()
    {
        assertEquals(100, OpeningAnim.ALL.size());
        assertEquals(OpeningAnim.COUNT, OpeningAnim.ALL.size());
        Set<String> names = new HashSet<>();
        Set<String> moves = new HashSet<>();
        Map<OpeningAnim.Rarity, Integer> per = new EnumMap<>(OpeningAnim.Rarity.class);
        for (OpeningAnim.Spec s : OpeningAnim.ALL)
        {
            assertTrue("blank name", s.name != null && !s.name.trim().isEmpty() && s.name.length() <= 24);
            assertTrue("duplicate name " + s.name, names.add(s.name.toLowerCase()));
            // no two share the same order and entrance, so each looks different
            assertTrue("same order and entrance as another: " + s.name, moves.add(s.order + "/" + s.entrance));
            per.merge(s.rarity, 1, Integer::sum);
        }
        assertEquals(45, (int) per.get(OpeningAnim.Rarity.COMMON));
        assertEquals(30, (int) per.get(OpeningAnim.Rarity.UNCOMMON));
        assertEquals(17, (int) per.get(OpeningAnim.Rarity.EPIC));
        assertEquals(8, (int) per.get(OpeningAnim.Rarity.LEGENDARY));
    }

    @Test
    public void theTiersAreNamedAndColoured()
    {
        assertEquals("Common", OpeningAnim.Rarity.COMMON.label);
        assertEquals("Uncommon", OpeningAnim.Rarity.UNCOMMON.label);
        assertEquals("Epic", OpeningAnim.Rarity.EPIC.label);
        assertEquals("Legendary", OpeningAnim.Rarity.LEGENDARY.label);
        assertEquals(0x3ec46d, OpeningAnim.Rarity.UNCOMMON.colour.getRGB() & 0xffffff);
        double total = 0;
        for (OpeningAnim.Rarity r : OpeningAnim.Rarity.values()) total += r.chance;
        assertEquals(1, total, 1e-9);
    }

    @Test
    public void everyAnimationHasItsTiersEffectAndEachLegendaryHasItsOwnScene()
    {
        Set<OpeningAnim.Fx> scenes = new HashSet<>();
        for (OpeningAnim.Spec s : OpeningAnim.ALL)
        {
            assertEquals(s.name, s.rarity, s.fx.tier);
            if (s.rarity == OpeningAnim.Rarity.LEGENDARY) assertTrue("shared scene " + s.fx, scenes.add(s.fx));
            assertNotNull(s.accent(3));
            assertTrue(OpeningAnim.byName(s.name) == s);
        }
    }

    @Test
    public void everyAnimationFinishesInsideTheTimingLimits()
    {
        assertTrue(OpeningAnim.LAND_MS + OpeningAnim.LANDING_FX_MS <= OpeningAnim.DEAL_MS);
        for (OpeningAnim.Spec s : OpeningAnim.ALL)
        {
            assertTrue(s.name, s.pace.flightMs > 0 && s.pace.flightMs < OpeningAnim.LAND_MS);
            for (int size = 1; size <= 10; size++)
            {
                int[] ranks = OpeningAnim.ranks(s.order, size, 99);
                int max = 0;
                for (int r : ranks) max = Math.max(max, r);
                double lastLand = 0;
                for (int r : ranks)
                {
                    double start = OpeningAnim.startMs(s.pace, r, max);
                    assertTrue(s.name + " starts early", start >= 0);
                    double land = start + s.pace.flightMs;
                    assertTrue(s.name + " size " + size + " lands at " + land, land <= OpeningAnim.LAND_MS + 1e-6);
                    lastLand = Math.max(lastLand, land);
                }
                // the whole board is used: the last tile lands right at LAND_MS (one tile simply flies in)
                if (max > 0) assertEquals(s.name, OpeningAnim.LAND_MS, lastLand, 1e-6);
            }
        }
    }

    @Test
    public void laterPlacesNeverLeaveEarlier()
    {
        for (OpeningAnim.Pace p : OpeningAnim.Pace.values())
        {
            double last = -1;
            for (int r = 0; r <= 48; r++)
            {
                double at = OpeningAnim.startMs(p, r, 48);
                assertTrue(p + " " + r, at >= last);
                last = at;
            }
        }
    }

    @Test
    public void rollsFollowTheRarityChancesOverManySeeds()
    {
        Map<OpeningAnim.Rarity, Integer> got = new EnumMap<>(OpeningAnim.Rarity.class);
        int n = 40000;
        for (int seed = 0; seed < n; seed++) got.merge(OpeningAnim.roll(new Random(1_760_000_000_000L + seed * 7919L)).rarity, 1, Integer::sum);
        for (OpeningAnim.Rarity r : OpeningAnim.Rarity.values())
        {
            double share = got.getOrDefault(r, 0) / (double) n;
            assertEquals(r.label, r.chance, share, 0.012);
        }
    }

    @Test
    public void everyAnimationCanBeRolled()
    {
        Random rng = new Random(5);
        Map<String, Integer> seen = new HashMap<>();
        for (int i = 0; i < 200_000; i++) seen.merge(OpeningAnim.roll(rng).name, 1, Integer::sum);
        assertEquals(100, seen.size());
        // the bingo-start reveal is always Legendary
        for (int i = 0; i < 200; i++) assertEquals(OpeningAnim.Rarity.LEGENDARY, OpeningAnim.rollOf(OpeningAnim.Rarity.LEGENDARY, rng).rarity);
    }

    @Test
    public void everyOrderPlacesEveryTileWithNoGaps()
    {
        for (int size = 1; size <= 10; size++)
        {
            for (OpeningAnim.Order o : OpeningAnim.Order.values())
            {
                int[] ranks = OpeningAnim.ranks(o, size, 7);
                assertEquals(size * size, ranks.length);
                int max = 0;
                for (int r : ranks)
                {
                    assertTrue(o + " size " + size, r >= 0 && r < size * size);
                    max = Math.max(max, r);
                }
                Set<Integer> seen = new HashSet<>();
                for (int r : ranks) seen.add(r);
                assertEquals(o + " size " + size, max + 1, seen.size());
                // a real order: on a board of 3 or more, at least three separate moments
                if (size >= 3) assertTrue(o + " size " + size, max >= 2);
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
            assertEquals(e + " scale x", 1, p[2], 0.01);
            assertEquals(e + " scale y", 1, p[3], 0.01);
            assertEquals(e + " rotation", 0, p[4], 0.01);
            assertEquals(e + " alpha", 1, p[5], 0.01);
        }
    }

    @Test
    public void everyEntranceStaysFiniteAllTheWay()
    {
        double[] out = new double[6];
        for (OpeningAnim.Entrance e : OpeningAnim.Entrance.values())
        {
            for (int idx = 0; idx < 49; idx += 6)
            {
                for (double fp = -0.1; fp <= 1.1; fp += 0.01)
                {
                    OpeningAnim.pose(out, e, fp, idx, 100, 80, 30, 0, 0, 300, 300, 150, 280);
                    for (double v : out) assertTrue(e + " at " + fp, Double.isFinite(v));
                    assertTrue(e + " alpha", out[5] >= 0 && out[5] <= 1);
                }
            }
        }
    }
}
