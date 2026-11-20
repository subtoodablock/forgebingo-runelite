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
 *   <li>the worn equipment changed around that tick (taking gear off; a worn item may carry another id);</li>
 *   <li>a ground item of that id vanished next to the player around that tick (a pickup: PvP loot never counts);</li>
 *   <li>a Grand Exchange offer changed around that tick (a collect);</li>
 *   <li>a trade with another player was open, accepted or closed around that tick (traded items never count);</li>
 *   <li>the player died moments ago, or it is an item they had when they died coming back to them;</li>
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

    /** ticks in which the worn equipment changed: anything reaching the backpack then came off the player */
    private final java.util.Set<Integer> wornTicks = new java.util.HashSet<>();

    /** The worn equipment changed this tick (equip, unequip, swap). */
    public void onWornChanged(int tick)
    {
        wornTicks.add(tick);
    }

    private boolean nearWornChange(int tick)
    {
        for (int t = tick - MOVE_WINDOW; t <= tick + MOVE_WINDOW; t++) if (wornTicks.contains(t)) return true;
        return false;
    }

    /** how long after a player trade (window open, accepted or closed) a backpack gain is still that trade's items */
    public static final int TRADE_WINDOW = 4;
    private final java.util.Set<Integer> tradeTicks = new java.util.HashSet<>();

    /**
     * A trade with another player is open, was accepted or just closed this tick (owner, 2026-11-17: an item traded
     * over counted as a reward). Items from another player are never a drop.
     */
    public void onTrade(int tick)
    {
        tradeTicks.add(tick);
    }

    private boolean nearTrade(int tick)
    {
        for (int t = tick - TRADE_WINDOW; t <= tick + 1; t++) if (tradeTicks.contains(t)) return true;
        return false;
    }

    /** right after the player dies nothing that reaches the backpack is a reward (kept items being handed back) */
    public static final int DEATH_WINDOW = 25;
    /** how long the items the player had when they died are remembered (gravestone, Death's office, safe deaths) */
    public static final int DEATH_MEMORY = 6000;
    private int deathTick = Integer.MIN_VALUE / 2;
    private final Map<Integer, Integer> heldAtDeath = new HashMap<>();

    /**
     * The player died this tick holding {@code held} (backpack and worn, item id to amount). Getting those items back,
     * whether kept at a safe death or reclaimed later, never counts as a reward (owner, 2026-11-19: a player's own
     * Eclipse atlatl counted as a Moons of Peril drop after they died at the Colosseum).
     */
    public void onDeath(int tick, Map<Integer, Integer> held)
    {
        deathTick = tick;
        heldAtDeath.clear();
        if (held != null) held.forEach((id, qty) -> { if (qty != null && qty > 0) heldAtDeath.merge(id, qty, Integer::sum); });
    }

    /** true when this gain is the player getting back something they had when they died (uses up that amount) */
    private boolean afterDeath(Pending p)
    {
        int since = p.tick - deathTick;
        if (since < 0 || since > DEATH_MEMORY) return false;
        if (since <= DEATH_WINDOW) return true;
        Integer had = heldAtDeath.get(p.itemId);
        if (had == null || had <= 0) return false;
        int left = had - p.quantity;
        if (left > 0) heldAtDeath.put(p.itemId, left);
        else heldAtDeath.remove(p.itemId);
        return true;
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
                || transformTicks.contains(p.tick) || transformTicks.contains(p.tick - 1) || nearWornChange(p.tick) || nearTrade(p.tick) || afterDeath(p))
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
        wornTicks.removeIf(t -> t < tick - KEEP);
        tradeTicks.removeIf(t -> t < tick - KEEP);
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
