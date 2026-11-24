package com.forgebingo.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Activity uniques that may reach the backpack without a loot event: reward-shop purchases (Barbarian Assault, Mage
 * Training Arena, ...), opened containers (Forgotten lockbox, Pyramid Plunder chests, Vale offerings) and raid or
 * minigame rewards the loot tracker may not name. Item id -> activity name the server knows (npc_sources alias).
 *
 * The rows live in the bundled data file src/main/resources/com/forgebingo/data/activity-items.txt (format in its
 * header); they come from apps/web/src/features/boardgen/data/activities.ts. The table as it was in Java is kept as
 * ActivityItemsReference in src/test/java, and ActivityItemsDataTest proves the two are equal. A missing or broken
 * file leaves this table empty and logs an error (see DataFile for why it does not throw).
 */
public final class ActivityItems
{
    private static final Map<Integer, String> BY_ITEM;

    static
    {
        Map<Integer, String> m;
        try
        {
            m = parse(DataFile.lines("activity-items"));
        }
        catch (RuntimeException e)
        {
            DataFile.failed("activity-items", e);
            m = new HashMap<>();
        }
        BY_ITEM = Collections.unmodifiableMap(m);
    }

    private ActivityItems()
    {
    }

    /**
     * "[Activity name]" starts an activity; every other line is its item ids, separated by spaces.
     *
     * @throws RuntimeException on anything else: ids before the first activity, not a number, or an id listed twice
     */
    static Map<Integer, String> parse(List<String> lines)
    {
        Map<Integer, String> m = new HashMap<>();
        String activity = null;
        for (String line : lines)
        {
            if (line.startsWith("[") && line.endsWith("]"))
            {
                activity = line.substring(1, line.length() - 1).trim();
                if (activity.isEmpty()) throw new IllegalStateException(line);
                continue;
            }
            for (String id : line.split(" +"))
            {
                if (activity == null || m.put(Integer.valueOf(id), activity) != null) throw new IllegalStateException(line);
            }
        }
        return m;
    }

    /** Activity name for an item id, or null when the item is not an activity unique. */
    public static String activityFor(int itemId)
    {
        return BY_ITEM.get(itemId);
    }

    public static int size()
    {
        return BY_ITEM.size();
    }
}
