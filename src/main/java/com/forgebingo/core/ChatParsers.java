package com.forgebingo.core;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure parsing of game chat messages. No RuneLite dependencies so it is unit-testable. */
public final class ChatParsers
{
    private static final Pattern TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern COLLECTION_LOG = Pattern.compile("^New item added to your collection log: (.+?)\\.?$");
    // Counts above 999 are printed with thousands separators ("1,234").
    private static final Pattern KC = Pattern.compile(
        "^Your (?:completed |subdued )?(.+?) (?:(?:kill|harvest|lap|completion|chest) )?count is: ([0-9][0-9,]{0,12})\\.?$");
    private static final Pattern KC_FOR = Pattern.compile("^Your completion count for (.+?) is: ([0-9][0-9,]{0,12})\\.?$");
    /** "You have completed 12 hard Treasure Trails." (singular for the first one). */
    private static final Pattern CLUE = Pattern.compile(
        "^You have completed ([0-9][0-9,]{0,12}) (beginner|easy|medium|hard|elite|master) Treasure Trails?\\.?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PET_NORMAL = Pattern.compile("^You have a funny feeling like you.?re being followed\\.?$");
    private static final Pattern PET_BACKPACK = Pattern.compile("^You feel something weird sneaking into your backpack\\.?$");
    private static final Pattern PET_DUPE = Pattern.compile("^You have a funny feeling like you would have been followed\\.{0,3}$");
    /**
     * Clan broadcast, the only line that names a duplicate pet:
     * "Zezima has a funny feeling like he's being followed: Vorki at 1,234 killcount.",
     * "Zezima feels something weird sneaking into her backpack: Heron at 99 Fishing.",
     * "Zezima has a funny feeling like they would have been followed: Olmlet at 50 killcount."
     */
    private static final Pattern PET_BROADCAST = Pattern.compile(
        "^([A-Za-z0-9 _-]{1,12}) (?:has a funny feeling like \\S+(?: \\S+)?? (?:(would have been)|(?:is |are )?being) followed"
            + "|feels something weird sneaking into \\S+ backpack): (.+?)(?: at [0-9][0-9,]* .*?)?\\.?$");

    private ChatParsers()
    {
    }

    public static String stripTags(String message)
    {
        if (message == null)
        {
            return "";
        }
        return TAGS.matcher(message).replaceAll("").replace(' ', ' ').trim();
    }

    public static final class Kc
    {
        public final String boss;
        public final int count;

        Kc(String boss, int count)
        {
            this.boss = boss;
            this.count = count;
        }
    }

    public static Optional<String> collectionLogItem(String message)
    {
        Matcher m = COLLECTION_LOG.matcher(stripTags(message));
        return m.matches() ? Optional.of(m.group(1).trim()) : Optional.empty();
    }

    public static Optional<Kc> killCount(String message)
    {
        String text = stripTags(message);
        Matcher m = KC.matcher(text);
        if (!m.matches())
        {
            m = KC_FOR.matcher(text);
            if (!m.matches())
            {
                return Optional.empty();
            }
        }
        Integer n = count(m.group(2));
        return n == null ? Optional.empty() : Optional.of(new Kc(m.group(1).trim(), n));
    }

    private static Integer count(String digits)
    {
        try
        {
            return Integer.parseInt(digits.replace(",", ""));
        }
        catch (NumberFormatException ex)
        {
            return null;
        }
    }

    public static final class Clue
    {
        /** lower-case tier: beginner .. master */
        public final String tier;
        public final int count;

        Clue(String tier, int count)
        {
            this.tier = tier;
            this.count = count;
        }

        /** sourceName for the {@code kc} event; an alias of the {@code clue_<tier>} catalogue source. */
        public String sourceName()
        {
            return "Clue scroll (" + tier + ")";
        }
    }

    /** Treasure Trail completion: "You have completed N <tier> Treasure Trails." */
    public static Optional<Clue> clueCompletion(String message)
    {
        Matcher m = CLUE.matcher(stripTags(message));
        if (!m.matches())
        {
            return Optional.empty();
        }
        Integer n = count(m.group(1));
        return n == null ? Optional.empty() : Optional.of(new Clue(m.group(2).toLowerCase(java.util.Locale.ROOT), n));
    }

    public enum Pet
    {
        /** New pet, now following the player. */
        FOLLOWING,
        /** New pet, put into the backpack (the player already had a follower). */
        BACKPACK,
        /** Duplicate: the player already owns it; nothing appears. */
        DUPLICATE;

        public boolean isNew()
        {
            return this != DUPLICATE;
        }
    }

    public static Optional<Pet> pet(String message)
    {
        String text = stripTags(message);
        if (PET_NORMAL.matcher(text).matches())
        {
            return Optional.of(Pet.FOLLOWING);
        }
        if (PET_BACKPACK.matcher(text).matches())
        {
            return Optional.of(Pet.BACKPACK);
        }
        if (PET_DUPE.matcher(text).matches())
        {
            return Optional.of(Pet.DUPLICATE);
        }
        return Optional.empty();
    }

    public static final class PetBroadcast
    {
        public final String player;
        public final String petName;
        public final boolean duplicate;

        PetBroadcast(String player, String petName, boolean duplicate)
        {
            this.player = player;
            this.petName = petName;
            this.duplicate = duplicate;
        }
    }

    /** A clan "funny feeling" broadcast; the caller checks {@code player} against the local player. */
    public static Optional<PetBroadcast> petBroadcast(String message)
    {
        Matcher m = PET_BROADCAST.matcher(stripTags(message));
        if (!m.matches())
        {
            return Optional.empty();
        }
        return Optional.of(new PetBroadcast(m.group(1).trim(), m.group(3).trim(), m.group(2) != null));
    }
}
