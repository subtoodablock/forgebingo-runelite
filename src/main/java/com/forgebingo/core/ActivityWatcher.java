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
    /**
     * how long the items lost at a death are remembered: reclaiming them from a gravestone, Death's office or a boss's
     * item-retrieval service never counts. A day, so a player who restarts or reclaims much later is still covered.
     */
    public static final long DEATH_MEMORY_MILLIS = 24L * 60 * 60 * 1000;
    private int deathTick = Integer.MIN_VALUE / 2;
    private boolean settlePending;
    /** item id to {amount still to come back, forget-at millis} */
    private final Map<Integer, long[]> lostAtDeath = new HashMap<>();
    private boolean deathMemoryChanged;

    /**
     * The player died this tick holding {@code held} (backpack and worn, item id to amount). Getting those items back,
     * whether kept at a safe death or reclaimed later, never counts as a reward (owner, 2026-11-19: a player's own
     * Eclipse atlatl counted as a Moons of Peril drop after they died at the Colosseum). Several deaths add up.
     */
    public void onDeath(int tick, Map<Integer, Integer> held, long nowMillis)
    {
        // the same death seen twice (RuneLite's death event and hitpoints reaching 0): count its items once
        if (tick >= deathTick && tick - deathTick <= DEATH_WINDOW) return;
        deathTick = tick;
        settlePending = true;
        if (held == null) return;
        long forgetAt = nowMillis + DEATH_MEMORY_MILLIS;
        held.forEach((id, qty) -> {
            if (id == null || qty == null || qty <= 0 || ActivityItems.activityFor(id) == null) return;
            long[] e = lostAtDeath.computeIfAbsent(id, k -> new long[]{0, 0});
            e[0] += qty;
            e[1] = Math.max(e[1], forgetAt);
        });
        deathMemoryChanged = true;
    }

    /**
     * true once, when the death moment is over and the plugin should report what the player still holds. Inside a
     * raid or other instance (Chambers of Xeric, Theatre of Blood, Tombs of Amascut, the Colosseum) the player can
     * respawn with their gear much later, so the death moment lasts until they leave it (owner, 2026-11-19).
     */
    public boolean wantsHoldingsAfterDeath(int tick, boolean inInstance)
    {
        return settlePending && !inInstance && tick - deathTick > DEATH_WINDOW;
    }

    /**
     * how long gear the player still had once a death was over stays remembered: long enough that a raid taking it
     * away and handing it back later (The Fractured Archive, Theatre of Blood style deaths) does not count, short
     * enough that a real new drop of it soon after counts again
     */
    public static final long KEPT_MEMORY_MILLIS = 30L * 60 * 1000;

    /**
     * What the player holds (backpack and worn) once the death moment is over. Those were kept (a safe death); they
     * are remembered for {@link #KEPT_MEMORY_MILLIS} instead of a day, in case the game takes them away and hands
     * them back (owner, 2026-11-19: raids, The Fractured Archive). Items not held stay remembered for the full day.
     */
    public void onHoldingsAfterDeath(Map<Integer, Integer> held, long nowMillis)
    {
        settlePending = false;
        if (held == null) return;
        long keptUntil = nowMillis + KEPT_MEMORY_MILLIS;
        held.forEach((id, qty) -> {
            long[] e = id == null ? null : lostAtDeath.get(id);
            if (e == null || qty == null || qty <= 0) return;
            e[1] = Math.min(e[1], keptUntil);
            deathMemoryChanged = true;
        });
    }

    private boolean useUp(int itemId, int qty)
    {
        long[] e = lostAtDeath.get(itemId);
        if (e == null || qty <= 0) return false;
        e[0] -= qty;
        if (e[0] <= 0) lostAtDeath.remove(itemId);
        deathMemoryChanged = true;
        return true;
    }

    /** true when this gain is the player getting back something they had when they died (uses up that amount) */
    private boolean afterDeath(Pending p, long nowMillis)
    {
        int since = p.tick - deathTick;
        // the death moment: until the player has left the raid or instance they died in, nothing counts
        if (since >= 0 && (since <= DEATH_WINDOW || settlePending)) return true;
        long[] e = lostAtDeath.get(p.itemId);
        if (e == null || e[1] < nowMillis) return false;
        return useUp(p.itemId, p.quantity);
    }

    /** The death memory as text to keep between sessions ("id:amount:forgetAt;..."). */
    public String saveDeathMemory()
    {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, long[]> e : lostAtDeath.entrySet())
        {
            if (sb.length() > 0) sb.append(';');
            sb.append(e.getKey()).append(':').append(e.getValue()[0]).append(':').append(e.getValue()[1]);
        }
        deathMemoryChanged = false;
        return sb.toString();
    }

    /** Puts back a death memory saved earlier (other entries of this session are kept); bad text is ignored. */
    public void restoreDeathMemory(String saved, long nowMillis)
    {
        if (saved == null || saved.isEmpty()) return;
        for (String part : saved.split(";"))
        {
            String[] f = part.split(":");
            if (f.length != 3) continue;
            try
            {
                int id = Integer.parseInt(f[0].trim());
                long qty = Long.parseLong(f[1].trim());
                long forgetAt = Long.parseLong(f[2].trim());
                if (qty <= 0 || forgetAt < nowMillis || ActivityItems.activityFor(id) == null) continue;
                long[] e = lostAtDeath.computeIfAbsent(id, k -> new long[]{0, 0});
                e[0] = Math.max(e[0], Math.min(qty, 1000));
                e[1] = Math.max(e[1], Math.min(forgetAt, nowMillis + DEATH_MEMORY_MILLIS));
            }
            catch (NumberFormatException ignored)
            {
                // a hand-edited or damaged value: skip that entry
            }
        }
    }

    /** Forgets every death (another account logged in: its own memory is restored next). */
    public void clearDeathMemory()
    {
        lostAtDeath.clear();
        settlePending = false;
        deathMemoryChanged = false;
    }

    /** true when the death memory changed since it was last saved */
    public boolean deathMemoryChanged()
    {
        return deathMemoryChanged;
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
        return flush(tick, pvp, System.currentTimeMillis());
    }

    public List<Drop> flush(int tick, boolean pvp, long nowMillis)
    {
        lostAtDeath.values().removeIf(e -> e[1] < nowMillis);
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
            boolean cameBack = afterDeath(p, nowMillis);
            if (cameBack || pvp || near(lootSeen, p.itemId, p.tick, LOOT_WINDOW, 1) || near(leftContainer, p.itemId, p.tick, MOVE_WINDOW, MOVE_WINDOW)
                || near(pickedUp, p.itemId, p.tick, MOVE_WINDOW, MOVE_WINDOW) || Math.abs(p.tick - geTick) <= MOVE_WINDOW
                || transformTicks.contains(p.tick) || transformTicks.contains(p.tick - 1) || nearWornChange(p.tick) || nearTrade(p.tick))
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
