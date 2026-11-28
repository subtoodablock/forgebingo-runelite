package com.forgebingo.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One monster kill can reach the plugin twice (found 2026-11-28, before 2.1.3 went out):
 * <ul>
 *   <li>the game itself tells RuneLite what the monster gave (RuneLite's ServerNpcLoot). This is exact, and it is the
 *       only report for monsters whose loot never lies under them: The Whisperer, the Maggot King, Araxxor, the Royal
 *       Titans and the like;</li>
 *   <li>RuneLite's older guess from the items that appear on the ground under the monster (NpcLootReceived). It is
 *       silent for those monsters, and for ordinary ones it describes the same kill a second time.</li>
 * </ul>
 * The game's own report is sent at once. The older one is held for a tick and sent only when the game said nothing
 * about that monster around then, so a kill is never counted twice and nothing is lost if the game's report is
 * missing. Several kills of the same kind in one tick (a cannon, a barrage) each have their own pair of reports.
 * Pure Java so it is unit-tested without RuneLite.
 */
public final class NpcLootMerge<T>
{
    /** how many ticks after the game's report the older one can still turn up for the same kill (some bosses' loot lands late) */
    public static final int LATE_WINDOW = 8;
    private static final int KEEP = 40;

    private static final class Held<T>
    {
        final String key;
        final int tick;
        final T event;

        Held(String key, int tick, T event)
        {
            this.key = key;
            this.tick = tick;
            this.event = event;
        }
    }

    /** monster name to the ticks the game reported loot for it, one entry per report not yet paired with an older one */
    private final Map<String, List<Integer>> server = new HashMap<>();
    private final List<Held<T>> held = new ArrayList<>();

    private static String key(String name)
    {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    /** The game reported this monster's loot at {@code tick}: the caller sends it now. */
    public void onServer(String name, int tick)
    {
        String k = key(name);
        // an older report of the same kill may already be waiting (it arrives earlier in the same tick): it is not needed
        Iterator<Held<T>> it = held.iterator();
        while (it.hasNext())
        {
            Held<T> h = it.next();
            if (h.key.equals(k) && tick - h.tick <= 2 && tick >= h.tick)
            {
                it.remove();
                return;
            }
        }
        server.computeIfAbsent(k, x -> new ArrayList<>()).add(tick);
    }

    /** RuneLite's older report for this monster at {@code tick}: kept until {@link #flush} decides. */
    public void onLegacy(String name, int tick, T event)
    {
        String k = key(name);
        // the game already reported this kill (this tick, or a few ticks ago for loot that lands late): drop this one
        List<Integer> seen = server.get(k);
        if (seen != null)
        {
            for (int i = 0; i < seen.size(); i++)
            {
                int s = seen.get(i);
                if (tick >= s && tick - s <= LATE_WINDOW)
                {
                    seen.remove(i);
                    return;
                }
            }
        }
        held.add(new Held<>(k, tick, event));
    }

    /** Call once per game tick. Returns the older reports from earlier ticks that the game never confirmed: send those. */
    public List<T> flush(int tick)
    {
        List<T> out = new ArrayList<>();
        Iterator<Held<T>> it = held.iterator();
        while (it.hasNext())
        {
            Held<T> h = it.next();
            if (h.tick >= tick - 1) continue; // held for two ticks: the game's report for the same kill may come a tick behind
            it.remove();
            out.add(h.event);
        }
        for (List<Integer> seen : server.values()) seen.removeIf(s -> s < tick - KEEP);
        server.values().removeIf(List::isEmpty);
        return out;
    }

    int heldCount()
    {
        return held.size();
    }
}
