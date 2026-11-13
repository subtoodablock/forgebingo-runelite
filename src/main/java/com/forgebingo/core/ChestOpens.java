package com.forgebingo.core;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A raid with more than one reward chest (The Fractured Archive: the standard chest, plus a second one for whoever
 * rolled the unique). RuneLite's loot tracker reports only the first chest opened in a raid, so the plugin reads the
 * chest window itself each time it opens. This remembers what that raid's chests have already shown, so opening the
 * same chest again reports nothing and a second chest reports only what is new, whether the game shows each chest's
 * loot on its own or adds it to the first chest's. Pure Java so it is unit-tested without RuneLite.
 */
public final class ChestOpens
{
    private final Map<Integer, Integer> reported = new HashMap<>();

    /**
     * @param contents item id to quantity, as the chest window shows it now
     * @return what has not been reported yet in this raid (empty when there is nothing new)
     */
    public Map<Integer, Integer> opened(Map<Integer, Integer> contents)
    {
        Map<Integer, Integer> fresh = new LinkedHashMap<>();
        if (contents == null) return fresh;
        for (Map.Entry<Integer, Integer> c : contents.entrySet())
        {
            int had = reported.getOrDefault(c.getKey(), 0);
            if (c.getValue() > had) fresh.put(c.getKey(), c.getValue() - had);
        }
        // a window showing one chest at a time: what it showed before still counts as reported
        for (Map.Entry<Integer, Integer> c : contents.entrySet()) reported.merge(c.getKey(), c.getValue(), Math::max);
        return fresh;
    }

    /** The raid is over (the player left the instance or logged out). */
    public void reset()
    {
        reported.clear();
    }
}
