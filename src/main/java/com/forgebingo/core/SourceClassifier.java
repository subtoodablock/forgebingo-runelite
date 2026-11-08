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
