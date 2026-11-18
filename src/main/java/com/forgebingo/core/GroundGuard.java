package com.forgebingo.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Keeps ground items that did not come from the monster out of its loot (owner, 2026-11-17). A monster's loot is
 * whatever appears under it when it dies, so an item put on that spot at that moment would look like a drop:
 * <ul>
 *   <li>an item the player dropped from the backpack themselves in the last few ticks;</li>
 *   <li>an item that appeared on the ground belonging to another player (their dropped item turning visible).</li>
 * </ul>
 * Those item ids are left out of the loot that is sent. Pure Java so it is unit-tested without RuneLite.
 */
public final class GroundGuard
{
    /** how long after the player chose "Drop" on an item it can still be the thing lying under the monster */
    public static final int DROP_WINDOW = 4;
    /** another player's item and the monster's loot must show up in (nearly) the same tick to be mixed up */
    public static final int FOREIGN_WINDOW = 1;
    private static final int KEEP = 30;

    private final Map<Integer, Integer> droppedByMe = new HashMap<>();
    private final Map<Integer, Integer> foreign = new HashMap<>();

    /** The player chose "Drop" on this backpack item. */
    public void onDropClicked(int itemId, int tick)
    {
        droppedByMe.put(itemId, tick);
    }

    /** A ground item owned by another player became visible. */
    public void onForeignItem(int itemId, int tick)
    {
        foreign.put(itemId, tick);
    }

    /** true when this item in a monster's loot at {@code tick} may count as that monster's drop. */
    public boolean fromMonster(int itemId, int tick)
    {
        Integer d = droppedByMe.get(itemId);
        if (d != null && tick >= d && tick - d <= DROP_WINDOW) return false;
        Integer f = foreign.get(itemId);
        return f == null || Math.abs(tick - f) > FOREIGN_WINDOW;
    }

    /** Call once per game tick. */
    public void prune(int tick)
    {
        droppedByMe.values().removeIf(t -> t < tick - KEEP);
        foreign.values().removeIf(t -> t < tick - KEEP);
    }
}
