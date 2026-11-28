package com.forgebingo.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Test;

public class ProofPolicyTest
{
    private static GameEvent event(String type, int... itemIds)
    {
        GameEvent e = new GameEvent();
        e.sourceType = type;
        for (int id : itemIds) e.addItem(id, 1);
        return e;
    }

    private static ApiModels.Task itemTask(Integer... itemIds)
    {
        ApiModels.Task k = new ApiModels.Task();
        k.sourceType = GameEvent.NPC;
        k.itemIds = new ArrayList<>(Arrays.asList(itemIds));
        return k;
    }

    private static ApiModels.Task sourceTask(String sourceType)
    {
        ApiModels.Task k = new ApiModels.Task();
        k.sourceType = sourceType;
        return k;
    }

    private static ApiModels.Tile tile(ApiModels.Task... tasks)
    {
        ApiModels.Tile t = new ApiModels.Tile();
        t.title = "Tile";
        t.tasks = new ArrayList<>(Arrays.asList(tasks));
        return t;
    }

    private static ApiModels.BoardResponse board(ApiModels.Tile... tiles)
    {
        ApiModels.BoardResponse b = new ApiModels.BoardResponse();
        b.tiles = new ArrayList<>(Arrays.asList(tiles));
        return b;
    }

    @Test
    public void withNoBoardLoadedEverythingGetsAPicture()
    {
        assertTrue(ProofPolicy.couldCount(event(GameEvent.NPC, 1), null, 1));
        assertTrue(ProofPolicy.couldCount(event(GameEvent.NPC, 1), new ApiModels.BoardResponse(), 0));
    }

    @Test
    public void onlyItemsTheBoardAsksFor()
    {
        ApiModels.BoardResponse b = board(tile(itemTask(11286, 22006)));
        assertTrue(ProofPolicy.couldCount(event(GameEvent.NPC, 526, 11286), b, 1));
        assertFalse(ProofPolicy.couldCount(event(GameEvent.NPC, 526), b, 1));
        // an item task never matches an event without items, whatever its source type
        assertFalse(ProofPolicy.couldCount(event(GameEvent.NPC), b, 1));
    }

    @Test
    public void tasksWithoutItemsGoBySourceType()
    {
        ApiModels.BoardResponse b = board(tile(sourceTask(GameEvent.KC)));
        assertTrue(ProofPolicy.couldCount(event(GameEvent.KC), b, 1));
        assertFalse(ProofPolicy.couldCount(event(GameEvent.LEVEL), b, 1));
    }

    @Test
    public void finishedTilesStillCount()
    {
        // drops keep counting (with proof) until the bingo is over
        ApiModels.Tile done = tile(itemTask(11286));
        done.completed = true;
        done.tasks.get(0).completed = true;
        assertTrue(ProofPolicy.couldCount(event(GameEvent.NPC, 11286), board(done), 1));
    }

    @Test
    public void orOptionsEachCount()
    {
        ApiModels.Task a = itemTask(100);
        ApiModels.Task b = itemTask(200);
        a.group = 1;
        b.group = 1;
        ApiModels.BoardResponse board = board(tile(a, b));
        assertTrue(ProofPolicy.couldCount(event(GameEvent.NPC, 100), board, 1));
        assertTrue(ProofPolicy.couldCount(event(GameEvent.NPC, 200), board, 1));
        assertFalse(ProofPolicy.couldCount(event(GameEvent.NPC, 300), board, 1));
    }

    @Test
    public void twoLiveBingosAtOnceEverythingGetsAPicture()
    {
        // only the selected board is known: the drop may count for the other one
        ApiModels.BoardResponse b = board(tile(itemTask(11286)));
        assertFalse(ProofPolicy.couldCount(event(GameEvent.NPC, 526), b, 1));
        assertTrue(ProofPolicy.couldCount(event(GameEvent.NPC, 526), b, 2));
        // a finished bingo next to a live one does not count as a second board
        assertFalse(ProofPolicy.couldCount(event(GameEvent.NPC, 526), b, 0));
    }

    @Test
    public void secretTilesGoByHiddenItemIds()
    {
        ApiModels.Tile secret = new ApiModels.Tile();
        secret.title = "?";
        secret.secret = true;
        ApiModels.BoardResponse b = board(secret);
        b.hiddenItemIds = new ArrayList<>(Arrays.asList(4151));
        assertTrue(ProofPolicy.couldCount(event(GameEvent.NPC, 4151), b, 1));
        assertFalse(ProofPolicy.couldCount(event(GameEvent.NPC, 526), b, 1));
    }

    @Test
    public void withASecretTileKillsPetsAndCluesAlwaysGetAPicture()
    {
        ApiModels.Tile secret = new ApiModels.Tile();
        secret.title = "?";
        secret.secret = true;
        ApiModels.BoardResponse b = board(secret, tile(itemTask(11286)));
        assertTrue(ProofPolicy.couldCount(event(GameEvent.KC), b, 1));
        assertTrue(ProofPolicy.couldCount(event(GameEvent.PET, 12921), b, 1));
        assertTrue(ProofPolicy.couldCount(event(GameEvent.CLUE, 995), b, 1));
        assertFalse(ProofPolicy.couldCount(event(GameEvent.LEVEL), b, 1));
        // without a secret tile on the board they go by the tasks like anything else
        ApiModels.BoardResponse open = board(tile(itemTask(11286)));
        assertFalse(ProofPolicy.couldCount(event(GameEvent.KC), open, 1));
    }

    @Test
    public void onePictureForDropsKillsAndCluesTwoForRewardChests()
    {
        assertArrayEquals(new long[] {600}, ProofPolicy.shotDelays(GameEvent.NPC, "Vorkath", false));
        assertArrayEquals(new long[] {600}, ProofPolicy.shotDelays(GameEvent.KC, "Chambers of Xeric", false));
        assertArrayEquals(new long[] {600}, ProofPolicy.shotDelays(GameEvent.CLUE, "hard", false));
        // a clue's reward window is never treated as a chest, even when asked that way
        assertEquals(1, ProofPolicy.shotDelays(GameEvent.CLUE, "hard", true).length);
        assertEquals(1, ProofPolicy.shotDelays(GameEvent.KC, "Clue scroll (elite)", true).length);
        assertArrayEquals(new long[] {600}, ProofPolicy.shotDelays(GameEvent.RAID_CHEST, "Barrows", true));
    }

    @Test
    public void cluesAreTheCasketAndItsCompletionCount()
    {
        assertTrue(ProofPolicy.isClue(GameEvent.CLUE, null));
        assertTrue(ProofPolicy.isClue(GameEvent.KC, "Clue Scroll (hard)"));
        assertFalse(ProofPolicy.isClue(GameEvent.KC, "Vorkath"));
        assertFalse(ProofPolicy.isClue(GameEvent.KC, null));
        assertFalse(ProofPolicy.isClue(GameEvent.NPC, "clue scroll (easy)"));
    }

    @Test
    public void raidsWhoseChestComesAfterTheKillLine()
    {
        assertTrue(ProofPolicy.raidWithChest("Chambers of Xeric: Challenge Mode"));
        assertTrue(ProofPolicy.raidWithChest("Theatre of Blood"));
        assertTrue(ProofPolicy.raidWithChest("Tombs of Amascut: Expert Mode"));
        assertTrue(ProofPolicy.raidWithChest("Fractured Archive"));
        assertFalse(ProofPolicy.raidWithChest("Barrows"));
        assertFalse(ProofPolicy.raidWithChest(null));
    }
}
