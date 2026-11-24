package com.forgebingo.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Static table of every pet: in-game item name, item ids (base form first, then metamorph variants) and the follower
 * NPC ids that walk behind the player. Pets are untradeable, so {@code ItemManager.search} (tradeables only) can
 * never resolve them; this table is the plugin's only source for pet ids.
 *
 * <p>Ids come from the RuneLite gameval tables ({@code ItemID} / {@code NpcID}, follower NPCs only: the house
 * menagerie copies are left out). {@code sources} are lower-case boss / activity names as the plugin sees them in
 * loot, kill count and clue events; they are only used to attribute a duplicate pet, which names no pet at all.
 *
 * <p>The rows live in the bundled data file src/main/resources/com/forgebingo/data/pets.txt (format in its header).
 * The table as it was in Java is kept as PetTableReference in src/test/java, and PetTableDataTest proves the two are
 * equal. A missing or broken file (a name, id or source used by two pets included) leaves this table empty and logs
 * an error (see DataFile for why it does not throw).
 */
public final class PetTable
{
    public static final class Pet
    {
        public final String name;
        /** Base item first. Every id is sent, so a task listing any variant matches. */
        public final int[] itemIds;
        public final int[] followerNpcIds;
        public final List<String> sources;

        Pet(String name, int[] itemIds, int[] followerNpcIds, List<String> sources)
        {
            this.name = name;
            this.itemIds = itemIds;
            this.followerNpcIds = followerNpcIds;
            this.sources = sources;
        }

        public int primaryItemId()
        {
            return itemIds[0];
        }

        @Override
        public String toString()
        {
            return name;
        }
    }

    private static final List<Pet> PETS;
    private static final Map<String, Pet> BY_NAME = new HashMap<>();
    private static final Map<Integer, Pet> BY_ITEM = new HashMap<>();
    private static final Map<Integer, Pet> BY_NPC = new HashMap<>();
    private static final Map<String, List<Pet>> BY_SOURCE = new HashMap<>();

    static
    {
        List<Pet> pets;
        try
        {
            pets = parse(DataFile.lines("pets"));
            for (Pet p : pets)
            {
                put(BY_NAME, ItemNames.normalize(p.name), p);
                for (int id : p.itemIds) put(BY_ITEM, id, p);
                for (int id : p.followerNpcIds) put(BY_NPC, id, p);
                for (String s : p.sources) BY_SOURCE.computeIfAbsent(ItemNames.normalize(s), k -> new ArrayList<>()).add(p);
            }
        }
        catch (RuntimeException e)
        {
            DataFile.failed("pets", e);
            pets = new ArrayList<>();
            BY_NAME.clear();
            BY_ITEM.clear();
            BY_NPC.clear();
            BY_SOURCE.clear();
        }
        PETS = Collections.unmodifiableList(pets);
    }

    private PetTable()
    {
    }

    private static <K> void put(Map<K, Pet> map, K key, Pet p)
    {
        Pet old = map.put(key, p);
        if (old != null && old != p)
        {
            throw new IllegalStateException("pet table: " + key + " used by " + old + " and " + p);
        }
    }

    public static List<Pet> all()
    {
        return PETS;
    }

    /** By in-game item name (case, apostrophe and whitespace insensitive). */
    public static Pet byName(String name)
    {
        return name == null ? null : BY_NAME.get(ItemNames.normalize(name));
    }

    public static Pet byItemId(int itemId)
    {
        return BY_ITEM.get(itemId);
    }

    public static Pet byFollowerNpcId(int npcId)
    {
        return BY_NPC.get(npcId);
    }

    /** Pets dropped by a boss / activity name (loot, kill count or clue source); empty when unknown. */
    public static List<Pet> bySource(String source)
    {
        if (source == null)
        {
            return Collections.emptyList();
        }
        List<Pet> pets = BY_SOURCE.get(ItemNames.normalize(source));
        return pets == null ? Collections.emptyList() : pets;
    }

    /**
     * One pet per line: "Name | item ids | follower npc ids | sources" (ids separated by spaces, at least one of
     * each; sources separated by commas, possibly none).
     *
     * @throws RuntimeException on anything else: not four fields, no name, a missing or non-numeric id, an empty source
     */
    static List<Pet> parse(List<String> lines)
    {
        List<Pet> l = new ArrayList<>();
        for (String line : lines)
        {
            String[] f = line.split("\\|", -1);
            List<String> sources = new ArrayList<>();
            if (f.length != 4 || f[0].trim().isEmpty()) throw new IllegalStateException(line);
            if (!f[3].trim().isEmpty())
            {
                for (String s : f[3].split(",", -1))
                {
                    if (s.trim().isEmpty()) throw new IllegalStateException(line);
                    sources.add(s.trim());
                }
            }
            l.add(new Pet(f[0].trim(), ids(f[1]), ids(f[2]), Collections.unmodifiableList(sources)));
        }
        return l;
    }

    private static int[] ids(String field)
    {
        String[] parts = field.trim().split(" +");
        int[] ids = new int[parts.length];
        for (int i = 0; i < ids.length; i++) ids[i] = Integer.parseInt(parts[i]);
        return ids;
    }
}
