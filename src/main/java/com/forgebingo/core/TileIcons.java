package com.forgebingo.core;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Picks an in-game item to show for a tile that has no item icon of its own: website emoji icons (iconEmoji, from
 * servers that send it), then words in the title ("Hard clue", "ToB run"), then the first item a task asks for.
 * Every id here is checked against the items catalogue. No RuneLite dependencies (unit-tested).
 */
public final class TileIcons
{
    /** Title words, most specific first (first match wins). */
    private static final Map<String, Integer> TITLE = new LinkedHashMap<>();
    /** Website emoji icons. */
    private static final Map<String, Integer> EMOJI = new LinkedHashMap<>();

    static
    {
        TITLE.put("free space", 6199);          // Mystery box
        TITLE.put("easy clue", 2677);
        TITLE.put("medium clue", 2801);
        TITLE.put("hard clue", 2722);
        TITLE.put("elite clue", 12073);
        TITLE.put("master clue", 19835);
        TITLE.put("clue", 2722);
        TITLE.put("cox", 20851);                // Olmlet
        TITLE.put("chambers", 20851);
        TITLE.put("tob", 22473);                // Lil' Zik
        TITLE.put("theatre", 22473);
        TITLE.put("toa", 27352);                // Tumeken's guardian
        TITLE.put("tombs", 27352);
        TITLE.put("raid", 21018);               // Ancestral hat
        TITLE.put("raider", 21018);
        TITLE.put("kbd", 12653);                // Prince Black Dragon
        TITLE.put("king black", 12653);
        TITLE.put("barrows", 4716);             // Dharok's helm
        TITLE.put("wildy", 964);                // Skull
        TITLE.put("wilderness", 964);
        TITLE.put("pet", 3695);                 // Pet rock
        TITLE.put("agility", 11850);            // Graceful hood
        TITLE.put("gatherer", 1275);            // Rune pickaxe
        TITLE.put("combat", 1333);              // Rune scimitar
        TITLE.put("level", 13280);              // Max cape
        TITLE.put("solo", 11640);               // Book of Knowledge
        TITLE.put("xp", 2528);                  // Lamp
        TITLE.put("goblin", 288);               // Goblin mail
        TITLE.put("fire cape", 6570);

        EMOJI.put("🎁", 6199);        // gift
        EMOJI.put("💀", 964);         // skull
        EMOJI.put("🐉", 20851);       // dragon
        EMOJI.put("🩸", 22473);       // drop of blood
        EMOJI.put("🏺", 27352);       // amphora
        EMOJI.put("🏆", 9813);        // trophy
        EMOJI.put("🐲", 12653);       // dragon face
        EMOJI.put("📜", 2722);        // scroll
        EMOJI.put("🗺", 19835);       // map
        EMOJI.put("⬆", 13280);             // up arrow
        EMOJI.put("⛏", 1275);              // pick
        EMOJI.put("⚔", 1333);              // crossed swords
        EMOJI.put("📈", 2528);        // chart
        EMOJI.put("🧍", 11640);       // standing person
        EMOJI.put("🏃", 11850);       // runner
        EMOJI.put("🐾", 3695);        // paw prints
        EMOJI.put("🙇", 288);         // bowing
        EMOJI.put("⚰", 4716);              // coffin
        EMOJI.put("🎲", 6199);        // die
        EMOJI.put("👑", 21018);       // crown
        EMOJI.put("🔥", 6570);        // fire
        EMOJI.put("💰", 995);         // money bag
        EMOJI.put("🪙", 995);         // coin
    }

    private TileIcons()
    {
    }

    /** An item to draw for this tile when it has no item icon of its own, or null (show its initials). */
    public static Integer fallbackItem(ApiModels.Tile t)
    {
        if (t == null) return null;
        String title = t.title == null ? "" : " " + t.title.toLowerCase(Locale.ROOT) + " ";
        for (Map.Entry<String, Integer> e : TITLE.entrySet())
        {
            if (containsWord(title, e.getKey())) return e.getValue();
        }
        if (t.iconEmoji != null)
        {
            for (Map.Entry<String, Integer> e : EMOJI.entrySet())
            {
                if (t.iconEmoji.startsWith(e.getKey())) return e.getValue();
            }
        }
        if (t.tasks != null)
        {
            for (ApiModels.Task k : t.tasks)
            {
                if (k != null && k.itemIds != null && !k.itemIds.isEmpty() && k.itemIds.get(0) != null && k.itemIds.get(0) > 0)
                {
                    return k.itemIds.get(0);
                }
            }
        }
        return null;
    }

    /** Skill name (as the plugin reports it) -> that skill's cape, for level-up tasks. */
    private static final Map<String, Integer> SKILL = new LinkedHashMap<>();

    static
    {
        String[] names = {"attack", "strength", "defence", "ranged", "prayer", "magic", "runecraft", "hitpoints", "agility", "herblore",
            "thieving", "crafting", "fletching", "slayer", "construction", "mining", "smithing", "fishing", "cooking", "firemaking",
            "woodcutting", "farming"};
        int[] capes = {9747, 9750, 9753, 9756, 9759, 9762, 9765, 9768, 9771, 9774, 9777, 9780, 9783, 9786, 9789, 9792, 9795, 9798,
            9801, 9804, 9807, 9810};
        for (int i = 0; i < names.length; i++) SKILL.put(names[i], capes[i]);
        SKILL.put("hunter", 9948);
        SKILL.put("sailing", 13280);
        SKILL.put("overall", 13280);            // Max cape
    }

    /**
     * The picture for one task: its own item, else its skill's cape (level-ups), else a boss/activity named in its
     * text, else the tile's picture. Null only when nothing at all fits (the panel then draws a tick box).
     */
    public static Integer taskItem(ApiModels.Task k, ApiModels.Tile t)
    {
        if (k != null)
        {
            if (k.itemIds != null) for (Integer id : k.itemIds) if (id != null && id > 0) return id;
            if (k.skill != null)
            {
                Integer cape = SKILL.get(k.skill.toLowerCase(Locale.ROOT));
                if (cape != null) return cape;
            }
            String text = " " + ((k.description == null ? "" : k.description) + " " + (k.sourceKey == null ? "" : k.sourceKey.replace('_', ' ')))
                .toLowerCase(Locale.ROOT) + " ";
            for (Map.Entry<String, Integer> e : SKILL.entrySet()) if (containsWord(text, e.getKey())) return e.getValue();
            for (Map.Entry<String, Integer> e : TITLE.entrySet()) if (containsWord(text, e.getKey())) return e.getValue();
        }
        if (t == null) return null;
        // XP tasks say "Team gains 100,000 XP"; the skill is only in the tile's name ("100K Fishing XP")
        String title = " " + (t.title == null ? "" : t.title.toLowerCase(Locale.ROOT)) + " ";
        for (Map.Entry<String, Integer> e : SKILL.entrySet()) if (containsWord(title, e.getKey())) return e.getValue();
        return t.iconItemId != null ? t.iconItemId : null;
    }

    /** Last resort for a task with nothing of its own: the tile's own item, else an item picked from its title/emoji. */
    public static Integer taskFallback(ApiModels.Tile t)
    {
        return t == null ? null : t.iconItemId != null ? t.iconItemId : fallbackItem(t);
    }

    /** "cox" matches "CoX run" but not "coxswain"; multi-word keys match as a phrase. */
    private static boolean containsWord(String padded, String key)
    {
        int i = padded.indexOf(key);
        while (i >= 0)
        {
            boolean before = i == 0 || !Character.isLetterOrDigit(padded.charAt(i - 1));
            int end = i + key.length();
            boolean after = end >= padded.length() || !Character.isLetterOrDigit(padded.charAt(end));
            if (before && after) return true;
            i = padded.indexOf(key, i + 1);
        }
        return false;
    }
}
