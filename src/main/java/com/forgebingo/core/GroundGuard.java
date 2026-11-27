package com.forgebingo.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps ground items that did not come from the monster out of its loot (owner, 2026-11-17). RuneLite's loot tracker
 * takes whatever appears on the monster's tiles as it dies, whoever owns it, so an item put there at that moment
 * would look like a drop:
 * <ul>
 *   <li>an item the player dropped from the backpack themselves in the last few ticks;</li>
 *   <li>an item belonging to another player turning visible on (or right next to) the monster's tiles.</li>
 * </ul>
 * Another player's item elsewhere on screen is ignored, so a busy spot does not hide real drops.
 * Pure Java so it is unit-tested without RuneLite.
 */
public final class GroundGuard
{
    /** how long after the player chose "Drop" on an item it can still be the thing lying under the monster */
    public static final int DROP_WINDOW = 4;
    /** another player's item and the monster's loot must show up in (nearly) the same tick to be mixed up */
    public static final int FOREIGN_WINDOW = 1;
    private static final int KEEP = 30;

    private final Map<Integer, Integer> droppedByMe = new HashMap<>();
    /** item id to where and when another player's copy appeared: {tick, x, y, plane} */
    private final Map<Integer, List<int[]>> foreign = new HashMap<>();

    /** The player chose "Drop" on this backpack item. */
    public void onDropClicked(int itemId, int tick)
    {
        droppedByMe.put(itemId, tick);
    }

    /** A ground item owned by another player became visible at world tile (x, y, plane). */
    public void onForeignItem(int itemId, int tick, int x, int y, int plane)
    {
        foreign.computeIfAbsent(itemId, k -> new ArrayList<>()).add(new int[]{tick, x, y, plane});
    }

    /**
     * true when this item in the loot of a monster that died at {@code tick} may count as that monster's drop.
     * (npcX, npcY, plane) is the monster's south-west tile and {@code size} its width in tiles.
     */
    public boolean fromMonster(int itemId, int tick, int npcX, int npcY, int plane, int size)
    {
        Integer d = droppedByMe.get(itemId);
        if (d != null && tick >= d && tick - d <= DROP_WINDOW) return false;
        List<int[]> seen = foreign.get(itemId);
        if (seen == null) return true;
        int s = Math.max(1, size);
        for (int[] f : seen)
        {
            if (Math.abs(tick - f[0]) > FOREIGN_WINDOW || f[3] != plane) continue;
            // the monster's tiles plus one tile around them
            if (f[1] >= npcX - 1 && f[1] <= npcX + s && f[2] >= npcY - 1 && f[2] <= npcY + s) return false;
        }
        return true;
    }

    /** how long a drop of the player's own stays remembered while it lies there: 20 minutes of game ticks */
    public static final int OWN_LOOT_KEEP = 2000;
    /** the player's own fresh loot lying on the ground: "id:x:y:plane" to the tick it appeared */
    private final Map<String, Integer> ownLoot = new HashMap<>();

    private static String at(int itemId, int x, int y, int plane)
    {
        return itemId + ":" + x + ":" + y + ":" + plane;
    }

    /**
     * A ground item that belongs to the player appeared at world tile (x, y, plane). Unless they just dropped it from
     * the backpack themselves, it is loot of theirs waiting to be picked up (owner, 2026-11-27: a sea creature killed
     * from a distance leaves its loot where it died, and taking it with Telekinetic Grab did not count).
     */
    public void onOwnItem(int itemId, int tick, int x, int y, int plane)
    {
        Integer d = droppedByMe.get(itemId);
        if (d != null && tick >= d && tick - d <= DROP_WINDOW) return;
        ownLoot.put(at(itemId, x, y, plane), tick);
    }

    /**
     * A ground item vanished from world tile (x, y, plane). true when it was the player's own fresh loot (see
     * {@link #onOwnItem}): picking that up is collecting a reward, not picking up someone's item. Answers once.
     */
    public boolean takeOwnLoot(int itemId, int x, int y, int plane)
    {
        return ownLoot.remove(at(itemId, x, y, plane)) != null;
    }

    /** Call once per game tick. */
    public void prune(int tick)
    {
        ownLoot.values().removeIf(t -> t < tick - OWN_LOOT_KEEP);
        droppedByMe.values().removeIf(t -> t < tick - KEEP);
        foreign.values().forEach(l -> l.removeIf(f -> f[0] < tick - KEEP));
        foreign.values().removeIf(List::isEmpty);
    }
}
