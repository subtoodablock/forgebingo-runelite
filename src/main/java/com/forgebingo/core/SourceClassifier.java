package com.forgebingo.core;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Maps a LootReceived source name onto one of the server's source types and normalises clue names. */
public final class SourceClassifier
{
    private static final Pattern TIER = Pattern.compile("\\b(beginner|easy|medium|hard|elite|master)\\b");

    private SourceClassifier()
    {
    }

    /**
     * Loot that never counts (owner, 2026-11-11): Wilderness loot keys. Their chest just hands back items taken from
     * other players, so it is neither a drop nor a task. RuneLite's loot tracker names it "Loot Chest".
     */
    public static boolean ignored(String name)
    {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT).trim();
        return n.equals("loot chest") || n.contains("loot key");
    }

    public static String forEventLoot(String name)
    {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (n.startsWith("reward casket") || n.contains("clue scroll") || n.contains("clue casket"))
        {
            return GameEvent.CLUE;
        }
        // Raids, Barrows and every other chest / minigame reward share one bucket;
        // the server matches sourceName against its catalogue (unknown names match nothing).
        return GameEvent.RAID_CHEST;
    }

    /** "Reward casket (elite)" -> "elite"; null when no tier is present. */
    public static String clueTier(String name)
    {
        if (name == null)
        {
            return null;
        }
        Matcher m = TIER.matcher(name.toLowerCase(Locale.ROOT));
        return m.find() ? m.group(1) : null;
    }
}
