package com.forgebingo.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The sub-tasks of a "one of each" item task, the same way the website (lib/itemSlots.ts) and the server
 * (public.item_slot) split it: each item is its own sub-task, except items in a group, where any one of the group
 * counts. Keys match the server's itemProgress: "i&lt;itemId&gt;" or "g&lt;groupIndex&gt;". No RuneLite dependencies.
 */
public final class ItemSlots
{
    public static final class Slot
    {
        public final String key;
        public final List<Integer> itemIds;
        /** how many of this sub-task the task needs: its own amount when set, else the total shared out evenly */
        public final int need;
        public final int approved;
        public final int pending;

        Slot(String key, List<Integer> itemIds, int need, int approved, int pending)
        {
            this.key = key;
            this.itemIds = itemIds;
            this.need = need;
            this.approved = approved;
            this.pending = pending;
        }

        public boolean done()
        {
            return approved >= need;
        }
    }

    private ItemSlots()
    {
    }

    /** The sub-tasks of a "one of each" task with two or more of them; empty for every other task. */
    public static List<Slot> of(ApiModels.Task k)
    {
        if (k == null || !Boolean.TRUE.equals(k.eachItem) || k.itemIds == null || k.itemIds.size() < 2) return Collections.emptyList();
        List<List<Integer>> groups = k.itemGroups == null ? Collections.<List<Integer>>emptyList() : k.itemGroups;
        List<String> keys = new ArrayList<>();
        List<List<Integer>> members = new ArrayList<>();
        Set<Integer> placed = new HashSet<>();
        for (Integer id : k.itemIds)
        {
            if (id == null || placed.contains(id)) continue;
            int gi = -1;
            for (int g = 0; g < groups.size() && gi < 0; g++)
            {
                if (groups.get(g) != null && groups.get(g).contains(id)) gi = g;
            }
            List<Integer> slot = new ArrayList<>();
            if (gi >= 0)
            {
                for (Integer x : groups.get(gi)) if (x != null && k.itemIds.contains(x) && placed.add(x)) slot.add(x);
                keys.add("g" + gi);
            }
            else
            {
                placed.add(id);
                slot.add(id);
                keys.add("i" + id);
            }
            members.add(slot);
        }
        if (members.size() < 2) return Collections.emptyList();
        int shared = Math.max(1, k.requiredQty / members.size());
        List<Slot> out = new ArrayList<>();
        for (int i = 0; i < members.size(); i++)
        {
            int need = shared;
            if (k.itemQtys != null && !k.itemQtys.isEmpty())
            {
                need = 1;
                for (Integer x : members.get(i))
                {
                    Integer q = k.itemQtys.get(String.valueOf(x));
                    if (q != null && q > need) need = q;
                }
            }
            ApiModels.SlotProgress p = k.itemProgress == null ? null : k.itemProgress.get(keys.get(i));
            int approved = p == null ? 0 : Math.min(need, p.approved);
            int pending = p == null ? 0 : Math.max(0, Math.min(need - approved, p.pending));
            // a finished task ticks every sub-task even when older proof had no item list
            if (k.completed) approved = need;
            out.add(new Slot(keys.get(i), members.get(i), need, approved, pending));
        }
        return out;
    }
}
