package com.forgebingo.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Activity uniques that reach the backpack without a loot event (reward-shop purchases, opened lockboxes and chests,
 * claimed rewards). Each new activity item in the inventory is held for one tick, so every container event of that
 * tick is seen first, then reported unless it was only moved or already reported:
 * <ul>
 *   <li>the loot tracker / NPC loot already reported it within {@link #LOOT_WINDOW} ticks (reward chests post their loot
 *       when they open; claiming the items minutes later must not count them again);</li>
 *   <li>another item left the backpack in the same tick (an item changing form: a looting bag closing, a tablet being
 *       made, a weapon being uncharged, a cape taken apart) unless it was a currency being spent;</li>
 *   <li>the same item left another container (bank, equipment, storage, looting bag, trade) around that tick;</li>
 *   <li>a ground item of that id vanished next to the player around that tick (a pickup: PvP loot never counts);</li>
 *   <li>a Grand Exchange offer changed around that tick (a collect);</li>
 *   <li>the player is in the Wilderness or on a PvP world.</li>
 * </ul>
 * Every acquisition counts, duplicates included. Pure Java so it is unit-tested without RuneLite.
 */
public final class ActivityWatcher
{
    public static final int LOOT_WINDOW = 300;
    public static final int MOVE_WINDOW = 2;
    private static final int KEEP = 30;

    /** One activity unique to report. */
    public static final class Drop
    {
        public final int itemId;
        public final int quantity;
        public final String activity;

        Drop(int itemId, int quantity, String activity)
        {
            this.itemId = itemId;
            this.quantity = quantity;
            this.activity = activity;
        }
    }

    private static final class Pending
    {
        final int itemId;
        final int quantity;
        final int tick;
        final String activity;

        Pending(int itemId, int quantity, int tick, String activity)
        {
            this.itemId = itemId;
            this.quantity = quantity;
            this.tick = tick;
            this.activity = activity;
        }
    }

    private final Map<Integer, Integer> lootSeen = new HashMap<>();
    private final Map<Integer, Integer> leftContainer = new HashMap<>();
    private final Map<Integer, Integer> pickedUp = new HashMap<>();
    private int geTick = Integer.MIN_VALUE / 2;
    /** ticks in which a non-currency item left the backpack: additions in those ticks are transformations, not drops */
    private final java.util.Set<Integer> transformTicks = new java.util.HashSet<>();
    private final List<Pending> pending = new ArrayList<>();

    public void onLoot(int itemId, int tick)
    {
        lootSeen.put(itemId, tick);
    }

    public void onLeftContainer(int itemId, int tick)
    {
        leftContainer.put(itemId, tick);
    }

    public void onGroundItemGone(int itemId, int tick)
    {
        pickedUp.put(itemId, tick);
    }

    public void onGrandExchange(int tick)
    {
        geTick = tick;
    }

    /** A non-currency item left the backpack this tick (see the class comment). */
    public void onItemsLeftInventory(int tick)
    {
        transformTicks.add(tick);
    }

    public void onInventoryAdded(int itemId, int quantity, int tick)
    {
        String activity = ActivityItems.activityFor(itemId);
        if (activity != null && quantity > 0)
        {
            pending.add(new Pending(itemId, quantity, tick, activity));
        }
    }

    /** Call once per game tick. Returns what to report from earlier ticks. */
    public List<Drop> flush(int tick, boolean pvp)
    {
        List<Drop> out = new ArrayList<>();
        Iterator<Pending> it = pending.iterator();
        while (it.hasNext())
        {
            Pending p = it.next();
            if (p.tick >= tick)
            {
                continue;
            }
            it.remove();
            if (pvp || near(lootSeen, p.itemId, p.tick, LOOT_WINDOW, 1) || near(leftContainer, p.itemId, p.tick, MOVE_WINDOW, MOVE_WINDOW)
                || near(pickedUp, p.itemId, p.tick, MOVE_WINDOW, MOVE_WINDOW) || Math.abs(p.tick - geTick) <= MOVE_WINDOW
                || transformTicks.contains(p.tick) || transformTicks.contains(p.tick - 1))
            {
                continue;
            }
            out.add(new Drop(p.itemId, p.quantity, p.activity));
        }
        // kept for the whole loot window: a chest's loot may be claimed minutes after the chest reported it
        lootSeen.values().removeIf(t -> t < tick - LOOT_WINDOW);
        prune(leftContainer, tick);
        prune(pickedUp, tick);
        transformTicks.removeIf(t -> t < tick - KEEP);
        return out;
    }

    /** true when the item was recorded between {@code before} ticks before and {@code after} ticks after {@code tick}. */
    private static boolean near(Map<Integer, Integer> m, int itemId, int tick, int before, int after)
    {
        Integer t = m.get(itemId);
        return t != null && t >= tick - before && t <= tick + after;
    }

    private static void prune(Map<Integer, Integer> m, int tick)
    {
        m.values().removeIf(t -> t < tick - KEEP);
    }

    int pendingCount()
    {
        return pending.size();
    }
}
