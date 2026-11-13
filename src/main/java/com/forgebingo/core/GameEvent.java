package com.forgebingo.core;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

/**
 * One thing that happened in game, as sent to {@code POST /v1/game-events} (docs/API.md section 8).
 * The server decides which team boards / tasks it counts for; the client never matches.
 *
 * <p>{@code id} is serialized as {@code eventId}: a UUID generated once and reused on every retry
 * (the server's idempotency key). {@code accountHash} is a decimal string (a 64-bit long would lose
 * precision in JSON).
 */
public final class GameEvent
{
    public static final String NPC = "npc";
    public static final String RAID_CHEST = "raid_chest";
    public static final String CLUE = "clue";
    public static final String COLLECTION_LOG = "collection_log";
    public static final String PET = "pet";
    public static final String KC = "kc";
    public static final String LEVEL = "level";
    /** a finished Combat Achievement task (sourceName = task name); hidden until the server supports it */
    public static final String COMBAT_TASK = "combat_task";

    public static final class Item
    {
        public int itemId;
        public int quantity;

        public Item()
        {
        }

        public Item(int itemId, int quantity)
        {
            this.itemId = itemId;
            this.quantity = quantity;
        }
    }

    @SerializedName("eventId")
    public String id;
    public String sourceType;
    /** ISO-8601; the server accepts now-1d .. now+5m. */
    public String occurredAt;
    /** Required, ^[A-Za-z0-9 _-]{1,12}$ */
    public String rsn;
    /** Required, client.getAccountHash() as a decimal string. */
    public String accountHash;
    /** npc */
    public Integer npcId;
    /** which NPC in the scene dropped it (never sent or saved): keeps two same-tick kills with the same loot apart */
    public transient Integer npcIndex;
    public String npcName;
    /** raid_chest / clue (tier) / kc (boss or raid name). */
    public String sourceName;
    /** Canonical (unnoted) item ids. Max 40. */
    public List<Item> items;
    /** level: lower-case skill name, or "overall". */
    public String skill;
    public Integer level;
    public String pluginVersion;
    public Integer world;

    public GameEvent()
    {
    }

    public GameEvent addItem(int itemId, int quantity)
    {
        if (items == null)
        {
            items = new ArrayList<>();
        }
        items.add(new Item(itemId, quantity));
        return this;
    }

    /** Human label for notices. */
    public String label()
    {
        if (sourceName != null && !sourceName.isEmpty())
        {
            return sourceName;
        }
        if (npcName != null && !npcName.isEmpty())
        {
            return npcName;
        }
        return skill != null ? skill : sourceType;
    }
}
