package com.forgebingo.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Suppresses the same logical event seen twice in a short window (for example a
 * collection-log message emitted alongside a loot event, or a chat line replayed on login).
 */
public final class EventDeduplicator
{
    private final long windowMillis;
    private final int maxEntries;
    private final Map<String, Long> seen = new LinkedHashMap<>();

    public EventDeduplicator(long windowMillis, int maxEntries)
    {
        this.windowMillis = windowMillis;
        this.maxEntries = maxEntries;
    }

    public synchronized boolean firstSeen(String signature, long now)
    {
        seen.values().removeIf(t -> now - t > windowMillis);
        if (seen.containsKey(signature))
        {
            return false;
        }
        seen.put(signature, now);
        while (seen.size() > maxEntries)
        {
            seen.remove(seen.keySet().iterator().next());
        }
        return true;
    }

    public synchronized void clear()
    {
        seen.clear();
    }

    public static String signature(GameEvent e, int tick)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(e.sourceType).append('|').append(e.sourceName).append('|').append(e.npcId)
            .append('|').append(e.skill).append('|').append(e.level).append('|').append(tick);
        if (e.items != null)
        {
            for (GameEvent.Item i : e.items)
            {
                sb.append('|').append(i.itemId).append('x').append(i.quantity);
            }
        }
        return sb.toString();
    }
}
