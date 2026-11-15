package com.forgebingo.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.Test;

/**
 * Owner, 2026-11-14: every clue tier counts the moment the casket is opened, and every raid / Barrows / Moons chest
 * counts with its kill-count message. The game's own chat lines go through the parsers and the result must be a name
 * the server knows (its npc_sources aliases, copied here from the live table on 2026-11-14; the server compares
 * lower-cased names against aliases, label and key).
 */
public class ClueAndChestCoverageTest
{
    private static final Map<String, List<String>> SERVER = new HashMap<>();
    static
    {
        for (String t : new String[] {"beginner", "easy", "medium", "hard", "elite", "master"})
        {
            SERVER.put("clue_" + t, Arrays.asList(t, "clue scroll (" + t + ")", "reward casket (" + t + ")"));
        }
        SERVER.put("barrows", Arrays.asList("barrows", "barrows chests"));
        SERVER.put("lunar_chest", Arrays.asList("lunar chest", "moons of peril"));
        SERVER.put("chambers_of_xeric", Arrays.asList("chambers of xeric", "chambers of xeric challenge mode", "cox", "cox cm"));
        SERVER.put("theatre_of_blood", Arrays.asList("theatre of blood", "theatre of blood: entry mode", "theatre of blood: hard mode", "tob"));
        SERVER.put("tombs_of_amascut", Arrays.asList("toa", "tombs of amascut", "tombs of amascut: entry mode", "tombs of amascut: expert mode"));
    }

    private static String resolve(String name)
    {
        String n = name.trim().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, List<String>> e : SERVER.entrySet())
        {
            if (e.getValue().contains(n) || e.getKey().equals(n)) return e.getKey();
        }
        return null;
    }

    @Test
    public void everyClueTierCountsWhenTheCasketIsOpened()
    {
        for (String t : new String[] {"beginner", "easy", "medium", "hard", "elite", "master"})
        {
            // the line the game posts as the reward casket opens (first trail is singular), with and without colour tags
            for (String msg : new String[] {
                "You have completed 1 " + t + " Treasure Trail.",
                "You have completed <col=ef1020>1,234</col> " + t + " Treasure Trails."})
            {
                ChatParsers.Clue c = ChatParsers.clueCompletion(msg).orElseThrow(() -> new AssertionError("not read: " + msg));
                assertEquals(msg, "clue_" + t, resolve(c.sourceName()));
            }
            // the Loot Tracker's casket loot (the items) goes to the same clue tier
            String loot = "Clue Scroll (" + Character.toUpperCase(t.charAt(0)) + t.substring(1) + ")";
            assertEquals(GameEvent.CLUE, SourceClassifier.forEventLoot(loot));
            assertEquals(loot, "clue_" + t, resolve(SourceClassifier.clueTier(loot)));
        }
    }

    @Test
    public void raidBarrowsAndMoonsChestsCount()
    {
        String[][] cases = {
            {"Your Barrows chest count is: <col=ff0000>12</col>.", "barrows"},
            {"Your Lunar Chest count is: <col=ff0000>7</col>.", "lunar_chest"},
            {"Your completed Chambers of Xeric count is: <col=ff0000>5</col>.", "chambers_of_xeric"},
            {"Your completed Chambers of Xeric Challenge Mode count is: 2.", "chambers_of_xeric"},
            {"Your completed Theatre of Blood count is: 9.", "theatre_of_blood"},
            {"Your completed Theatre of Blood: Entry Mode count is: 1.", "theatre_of_blood"},
            {"Your completed Theatre of Blood: Hard Mode count is: 3.", "theatre_of_blood"},
            {"Your completed Tombs of Amascut count is: 4.", "tombs_of_amascut"},
            {"Your completed Tombs of Amascut: Entry Mode count is: 4.", "tombs_of_amascut"},
            {"Your completed Tombs of Amascut: Expert Mode count is: 6.", "tombs_of_amascut"},
        };
        for (String[] c : cases)
        {
            ChatParsers.Kc k = ChatParsers.killCount(c[0]).orElseThrow(() -> new AssertionError("not read: " + c[0]));
            assertEquals(c[0] + " -> " + k.boss, c[1], resolve(k.boss));
        }
        // the chests' loot itself is reported as chest loot (with the two loot-window pictures)
        for (String chest : new String[] {"Barrows", "Lunar Chest", "Chambers of Xeric", "Theatre of Blood", "Tombs of Amascut"})
        {
            assertTrue(chest, !GameEvent.CLUE.equals(SourceClassifier.forEventLoot(chest)));
        }
    }
}
