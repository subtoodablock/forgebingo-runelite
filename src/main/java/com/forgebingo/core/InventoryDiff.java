package com.forgebingo.core;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Item-count snapshots of a container and what was added between two of them (pets put into the backpack). */
public final class InventoryDiff
{
    private InventoryDiff()
    {
    }

    /** item id -> total quantity; empty slots (id < 0 or quantity < 1) are ignored. */
    public static Map<Integer, Integer> counts(int[] ids, int[] quantities)
    {
        Map<Integer, Integer> m = new HashMap<>();
        if (ids == null)
        {
            return m;
        }
        for (int i = 0; i < ids.length; i++)
        {
            int q = quantities == null || i >= quantities.length ? 1 : quantities[i];
            if (ids[i] < 0 || q < 1)
            {
                continue;
            }
            m.merge(ids[i], q, Integer::sum);
        }
        return m;
    }

    /** Items whose count went up from {@code before} to {@code after}, with the increase (insertion order of {@code after}). */
    public static Map<Integer, Integer> added(Map<Integer, Integer> before, Map<Integer, Integer> after)
    {
        Map<Integer, Integer> out = new LinkedHashMap<>();
        if (after == null)
        {
            return out;
        }
        for (Map.Entry<Integer, Integer> e : after.entrySet())
        {
            int was = before == null ? 0 : before.getOrDefault(e.getKey(), 0);
            if (e.getValue() > was)
            {
                out.put(e.getKey(), e.getValue() - was);
            }
        }
        return out;
    }
}
