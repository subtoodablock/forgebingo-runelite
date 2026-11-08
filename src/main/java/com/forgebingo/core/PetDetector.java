package com.forgebingo.core;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Works out WHICH pet a "funny feeling" chat line was about. The line itself names no pet, so it is paired, within
 * {@link #WINDOW_TICKS} game ticks either way, with the first of:
 * <ol>
 *   <li>the collection log line "New item added to your collection log: &lt;pet&gt;" (when that game setting is on);</li>
 *   <li>the clan broadcast "&lt;you&gt; has a funny feeling like ... followed: &lt;pet&gt;" (the only signal for duplicates);</li>
 *   <li>a new follower NPC (the "being followed" case), mapped NPC id -> pet;</li>
 *   <li>a pet item that appeared in the inventory (the "sneaking into your backpack" case);</li>
 *   <li>when the window closes unresolved: the boss / activity the player just killed or looted, if it has exactly one pet.</li>
 * </ol>
 * Pure state machine (no RuneLite types) driven by the plugin on the client thread. Each method returns the
 * pet drop to report, at most once per chat line.
 */
public final class PetDetector
{
    public static final int WINDOW_TICKS = 10;

    /** A pet to report. {@code pet} is null when the pet could not be identified. */
    public static final class Drop
    {
        public final PetTable.Pet pet;
        public final boolean duplicate;
        /** collection_log | broadcast | follower | inventory | source | unidentified */
        public final String via;

        Drop(PetTable.Pet pet, boolean duplicate, String via)
        {
            this.pet = pet;
            this.duplicate = duplicate;
            this.via = via;
        }

        public boolean identified()
        {
            return pet != null;
        }
    }

    private ChatParsers.Pet pending;
    private int pendingTick;
    /** A pet named (collection log / broadcast) before the chat line arrived. */
    private PetTable.Pet early;
    private boolean earlyDuplicate;
    private String earlyVia;
    private int earlyTick = Integer.MIN_VALUE / 2;
    /** Last boss / activity seen (loot, kill count, clue). */
    private String source;
    private int sourceTick = Integer.MIN_VALUE / 2;
    /** Tick of the last resolution, so a late second signal for the same pet is not paired with nothing. */
    private int resolvedTick = Integer.MIN_VALUE / 2;

    public boolean pending()
    {
        return pending != null;
    }

    /** The follower / inventory checks only matter while a line waits for its pet. */
    public boolean wantsFollower()
    {
        return pending == ChatParsers.Pet.FOLLOWING;
    }

    public boolean wantsInventory()
    {
        return pending == ChatParsers.Pet.BACKPACK;
    }

    public void reset()
    {
        pending = null;
        early = null;
        source = null;
        sourceTick = earlyTick = resolvedTick = Integer.MIN_VALUE / 2;
    }

    /** A "funny feeling" / "sneaking into your backpack" line. */
    public Optional<Drop> onPetMessage(ChatParsers.Pet kind, int tick)
    {
        pending = kind;
        pendingTick = tick;
        if (early != null && tick - earlyTick <= WINDOW_TICKS)
        {
            boolean dup = kind == ChatParsers.Pet.DUPLICATE || earlyDuplicate;
            return resolve(early, dup, earlyVia, tick);
        }
        return Optional.empty();
    }

    /** Any collection log line: only pet names matter here. */
    public Optional<Drop> onCollectionLog(String itemName, int tick)
    {
        return named(PetTable.byName(itemName), false, "collection_log", tick);
    }

    /** A clan pet broadcast already checked to be about the local player. */
    public Optional<Drop> onBroadcast(String petName, boolean duplicate, int tick)
    {
        return named(PetTable.byName(petName), duplicate, "broadcast", tick);
    }

    private Optional<Drop> named(PetTable.Pet pet, boolean duplicate, String via, int tick)
    {
        if (pet == null)
        {
            return Optional.empty();
        }
        if (pending != null)
        {
            return resolve(pet, pending == ChatParsers.Pet.DUPLICATE || duplicate, via, tick);
        }
        if (tick - resolvedTick <= WINDOW_TICKS)
        {
            return Optional.empty(); // second signal for a pet already reported
        }
        early = pet;
        earlyDuplicate = duplicate;
        earlyVia = via;
        earlyTick = tick;
        return Optional.empty();
    }

    /** The local player's follower NPC id (polled each tick while {@link #wantsFollower()}). */
    public Optional<Drop> onFollower(int npcId, int tick)
    {
        if (pending != ChatParsers.Pet.FOLLOWING)
        {
            return Optional.empty();
        }
        PetTable.Pet pet = PetTable.byFollowerNpcId(npcId);
        return pet == null ? Optional.empty() : resolve(pet, false, "follower", tick);
    }

    /** Item ids just added to the inventory (see {@link InventoryDiff#added}). */
    public Optional<Drop> onInventoryAdded(Collection<Integer> itemIds, int tick)
    {
        if (pending != ChatParsers.Pet.BACKPACK || itemIds == null)
        {
            return Optional.empty();
        }
        for (Integer id : itemIds)
        {
            PetTable.Pet pet = id == null ? null : PetTable.byItemId(id);
            if (pet != null)
            {
                return resolve(pet, false, "inventory", tick);
            }
        }
        return Optional.empty();
    }

    /** The boss / activity of a loot, kill count or clue event (for duplicates that nothing else names). */
    public void onSource(String name, int tick)
    {
        if (name != null && !name.isEmpty())
        {
            source = name;
            sourceTick = tick;
        }
    }

    /** Once per game tick: gives up on a line after the window, attributing it to the last source if unambiguous. */
    public Optional<Drop> onTick(int tick)
    {
        if (early != null && tick - earlyTick > WINDOW_TICKS)
        {
            early = null;
        }
        if (pending == null || tick - pendingTick <= WINDOW_TICKS)
        {
            return Optional.empty();
        }
        boolean dup = pending == ChatParsers.Pet.DUPLICATE;
        if (source != null && Math.abs(sourceTick - pendingTick) <= WINDOW_TICKS)
        {
            List<PetTable.Pet> pets = PetTable.bySource(source);
            if (pets.size() == 1)
            {
                return resolve(pets.get(0), dup, "source", tick);
            }
        }
        pending = null;
        resolvedTick = tick;
        return Optional.of(new Drop(null, dup, "unidentified"));
    }

    private Optional<Drop> resolve(PetTable.Pet pet, boolean duplicate, String via, int tick)
    {
        pending = null;
        early = null;
        resolvedTick = tick;
        return Optional.of(new Drop(pet, duplicate, via));
    }
}
