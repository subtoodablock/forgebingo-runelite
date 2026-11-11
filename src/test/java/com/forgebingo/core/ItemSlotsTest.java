package com.forgebingo.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import org.junit.Test;

public class ItemSlotsTest
{
    private static ApiModels.SlotProgress p(int approved, int pending)
    {
        ApiModels.SlotProgress s = new ApiModels.SlotProgress();
        s.approved = approved;
        s.pending = pending;
        return s;
    }

    @Test
    public void bonesAndBigBonesAreTwoSubTasks()
    {
        ApiModels.Task k = new ApiModels.Task();
        k.eachItem = true;
        k.itemIds = Arrays.asList(526, 532);
        k.requiredQty = 2;
        k.itemProgress = new HashMap<>();
        k.itemProgress.put("i532", p(1, 0));
        List<ItemSlots.Slot> s = ItemSlots.of(k);
        assertEquals(2, s.size());
        assertEquals("i526", s.get(0).key);
        assertFalse(s.get(0).done());
        assertTrue(s.get(1).done());
        assertEquals(1, s.get(1).need);
    }

    @Test
    public void groupsCountAsOneSubTaskKeyedByIndex()
    {
        ApiModels.Task k = new ApiModels.Task();
        k.eachItem = true;
        k.itemIds = Arrays.asList(1, 2, 3, 4);
        k.itemGroups = Arrays.asList(Arrays.asList(2, 3));
        k.requiredQty = 3;
        k.itemProgress = new HashMap<>();
        k.itemProgress.put("g0", p(0, 5));
        List<ItemSlots.Slot> s = ItemSlots.of(k);
        assertEquals(3, s.size());
        assertEquals(Arrays.asList(2, 3), s.get(1).itemIds);
        assertEquals("g0", s.get(1).key);
        assertEquals(1, s.get(1).pending); // capped at what the sub-task needs
    }

    @Test
    public void ordinaryTasksHaveNoSubTasks()
    {
        ApiModels.Task k = new ApiModels.Task();
        k.itemIds = Arrays.asList(526, 532);
        k.requiredQty = 25;
        assertTrue(ItemSlots.of(k).isEmpty());
    }
}
