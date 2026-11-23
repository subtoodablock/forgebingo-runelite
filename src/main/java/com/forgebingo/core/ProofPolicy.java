package com.forgebingo.core;

import java.util.Locale;

/**
 * When an event gets an automatic proof screenshot and how many: only events that could count for the board, one
 * picture for drops, kills and clues, two for a reward chest's loot window. The server still decides what counted.
 */
public final class ProofPolicy
{
    /** one picture of a drop or kill: a tick on, so the loot is drawn on the ground before it can be picked up */
    public static final long[] SINGLE_SHOT_DELAYS_MS = {600};
    /** one picture of the clue reward window, once it has drawn */
    public static final long[] CLUE_SHOT_DELAYS_MS = {600};
    /** A reward chest's loot window: once as it opens and again a little later (sent as "1 of 2" and "2 of 2"). */
    public static final long[] CHEST_SHOT_DELAYS_MS = {300, 1800};

    private ProofPolicy()
    {
    }

    /**
     * Could this event count for an open task on the board on screen? Only those get a screenshot, so ordinary
     * drops do not fill the few screenshot slots before the drop that matters.
     *
     * @param board the board on screen, or null before the first fetch
     * @param liveBoards how many of the player's boards are live right now
     */
    public static boolean couldCount(GameEvent ev, ApiModels.BoardResponse board, int liveBoards)
    {
        ApiModels.BoardResponse b = board;
        if (b == null || b.tiles == null) return true;
        // On more than one board (several bingos on one key): only the selected board is known here, and the drop may
        // count for another one, so every event gets its picture; it is only sent when the server says it counted.
        // Finished bingos do not count: only boards that are live right now.
        if (liveBoards > 1) return true;
        // finished tiles count too: drops keep counting (with proof) until the bingo is over (owner, 2026-11-11)
        for (ApiModels.Tile t : b.tiles)
        {
            if (t == null || t.tasks == null) continue;
            for (ApiModels.Task k : t.tasks)
            {
                if (k == null) continue;
                if (k.itemIds != null && !k.itemIds.isEmpty())
                {
                    if (ev.items == null) continue;
                    for (GameEvent.Item it : ev.items) if (k.itemIds.contains(it.itemId)) return true;
                }
                else if (k.sourceType != null && k.sourceType.equals(ev.sourceType))
                {
                    return true;
                }
            }
        }
        // secret tiles hide their tasks: their items come as hiddenItemIds
        if (b.hiddenItemIds != null && ev.items != null)
        {
            for (GameEvent.Item it : ev.items) if (b.hiddenItemIds.contains(it.itemId)) return true;
        }
        // a secret tile may ask for a kill count, any pet or any clue (no item ids to go on): with one on the board,
        // those events always get their picture; it is only sent when the server says the event counted (2026-11-22)
        if (GameEvent.KC.equals(ev.sourceType) || GameEvent.PET.equals(ev.sourceType) || GameEvent.CLUE.equals(ev.sourceType))
        {
            for (ApiModels.Tile t : b.tiles) if (t != null && TileInfo.isSecret(t)) return true;
        }
        return false;
    }

    /**
     * Milliseconds between spotting a drop or kill and taking its screenshot(s). The loot notice and the "kill count"
     * chat line arrive in the same game tick as the loot (measured on live Zulrah, Giant Mole, Vorkath and clue
     * events: 5-34 ms apart), so a picture a moment later shows the loot on the ground and the chat line. A reward
     * chest's two pictures go up stacked as one image, see ScreenshotStack.
     */
    public static long[] shotDelays(String sourceType, String sourceName, boolean chestWindow)
    {
        // Two pictures only for reward chests (raids, Barrows, Moons); clues, boss drops and kill counts get one
        // (owner, 2026-11-14).
        if (isClue(sourceType, sourceName)) return CLUE_SHOT_DELAYS_MS;
        return chestWindow ? CHEST_SHOT_DELAYS_MS : SINGLE_SHOT_DELAYS_MS;
    }

    /** a clue casket's loot, or the "You have completed N ... Treasure Trails" count that comes with it */
    public static boolean isClue(String sourceType, String sourceName)
    {
        return GameEvent.CLUE.equals(sourceType)
            || (GameEvent.KC.equals(sourceType) && sourceName != null && sourceName.toLowerCase(Locale.ROOT).startsWith("clue scroll"));
    }

    /** Raids whose completion message comes before their reward chest is opened. */
    public static boolean raidWithChest(String name)
    {
        if (name == null) return false;
        String n = name.toLowerCase(Locale.ROOT);
        return n.contains("chambers of xeric") || n.contains("theatre of blood") || n.contains("tombs of amascut") || n.contains("fractured archive");
    }
}
