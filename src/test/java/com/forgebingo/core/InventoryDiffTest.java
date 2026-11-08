package com.forgebingo.core;

import static org.junit.Assert.*;

import java.util.Map;
import org.junit.Test;

public class InventoryDiffTest
{
    @Test
    public void countsMergeStacksAndSkipEmptySlots()
    {
        Map<Integer, Integer> c = InventoryDiff.counts(new int[]{995, -1, 995, 4151, 4151}, new int[]{10, 0, 5, 1, 1});
        assertEquals(Integer.valueOf(15), c.get(995));
        assertEquals(Integer.valueOf(2), c.get(4151));
        assertFalse(c.containsKey(-1));
        assertTrue(InventoryDiff.counts(null, null).isEmpty());
    }

    @Test
    public void addedReportsNewAndIncreasedItemsOnly()
    {
        Map<Integer, Integer> before = InventoryDiff.counts(new int[]{995, 4151, 385}, new int[]{100, 1, 5});
        Map<Integer, Integer> after = InventoryDiff.counts(new int[]{995, 12646, 385}, new int[]{150, 1, 4});
        Map<Integer, Integer> added = InventoryDiff.added(before, after);
        assertEquals(2, added.size());
        assertEquals(Integer.valueOf(50), added.get(995));
        assertEquals(Integer.valueOf(1), added.get(12646));
        assertFalse(added.containsKey(4151)); // removed
        assertFalse(added.containsKey(385)); // decreased
        assertEquals(1, InventoryDiff.added(null, InventoryDiff.counts(new int[]{1}, new int[]{1})).size());
        assertTrue(InventoryDiff.added(before, null).isEmpty());
        assertTrue(InventoryDiff.added(before, before).isEmpty());
    }
}
