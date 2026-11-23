package com.forgebingo.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class MatchNoticesTest
{
    private static final Set<String> NO_HIDDEN = Collections.emptySet();

    private static ApiModels.Task task(String id, String description, boolean completed)
    {
        ApiModels.Task k = new ApiModels.Task();
        k.id = id;
        k.description = description;
        k.completed = completed;
        return k;
    }

    private static ApiModels.Tile tile(String id, String title, boolean completed, ApiModels.Task... tasks)
    {
        ApiModels.Tile t = new ApiModels.Tile();
        t.id = id;
        t.title = title;
        t.completed = completed;
        t.tasks = new ArrayList<>(Arrays.asList(tasks));
        return t;
    }

    private static ApiModels.BoardResponse board(ApiModels.Tile... tiles)
    {
        ApiModels.BoardResponse b = new ApiModels.BoardResponse();
        b.tiles = new ArrayList<>(Arrays.asList(tiles));
        return b;
    }

    /** tile id -> shown title, as the plugin keeps it for the board on screen */
    private static Map<String, String> titles(ApiModels.BoardResponse b)
    {
        Map<String, String> m = new HashMap<>();
        for (ApiModels.Tile t : b.tiles) m.put(t.id, TileInfo.shownTitle(t));
        return m;
    }

    private static ApiModels.Match match(String team, String tile, String task, String status, int current, int required, boolean completed)
    {
        ApiModels.Match m = new ApiModels.Match();
        m.teamId = team;
        m.tileId = tile;
        m.taskId = task;
        m.submissionId = "sub-" + tile + "-" + task;
        m.status = status;
        m.currentQuantity = current;
        m.requiredQuantity = required;
        m.completed = completed;
        return m;
    }

    private static ApiModels.MatchedTile done(String team, String tile)
    {
        ApiModels.MatchedTile mt = new ApiModels.MatchedTile();
        mt.teamBoardId = team;
        mt.tileId = tile;
        mt.completed = true;
        return mt;
    }

    private static ApiModels.EventResult result(ApiModels.Match... matches)
    {
        ApiModels.EventResult r = new ApiModels.EventResult();
        r.matched = new ArrayList<>(Arrays.asList(matches));
        r.matchedTiles = new ArrayList<>();
        return r;
    }

    private static List<String> texts(MatchNotices.Plan p)
    {
        List<String> out = new ArrayList<>();
        for (MatchNotices.Notice n : p.notices) out.add(n.text);
        return out;
    }

    private static final ApiModels.BoardResponse BOARD = board(
        tile("t1", "Dragon bones", false, task("k1", "Bury 5 bones", false), task("k2", "Kill Vorkath", true)),
        tile("t2", "Done already", true, task("k3", "Old task", true)));

    @Test
    public void countedWithProgressAndTheSidePanelLine()
    {
        MatchNotices.Plan p = MatchNotices.plan(result(match("A", "t1", "k1", "approved", 2, 5, false)), BOARD, titles(BOARD), NO_HIDDEN);
        assertEquals(Collections.singletonList("Counted: Dragon bones (2/5)"), texts(p));
        assertNull(p.notices.get(0).taskEffect);
        assertEquals("Bury 5 bones", p.counted);
        assertTrue(p.celebrations.isEmpty());
    }

    @Test
    public void progressNeverShowsMoreThanIsNeeded()
    {
        MatchNotices.Plan p = MatchNotices.plan(result(match("A", "t1", "k2", "approved", 7, 5, true)), BOARD, titles(BOARD), NO_HIDDEN);
        // k2 was finished before this drop: counted, not "Task complete" again
        assertEquals(Collections.singletonList("Counted: Dragon bones (5/5)"), texts(p));
    }

    @Test
    public void waitingForReviewAndUnverifiedNames()
    {
        ApiModels.Match pending = match("A", "t1", "k1", "pending", 1, 5, false);
        ApiModels.Match unverified = match("A", "t1", "k1", "pending", 1, 5, false);
        unverified.verified = false;
        MatchNotices.Plan p = MatchNotices.plan(result(pending, unverified), BOARD, titles(BOARD), NO_HIDDEN);
        assertEquals(Arrays.asList(
            "Counted: Dragon bones (1/5) - waiting for review",
            "Counted: Dragon bones (1/5) - waiting for review (your name isn't verified yet)"), texts(p));
    }

    @Test
    public void taskCompleteNamesTheTaskAndItsTile()
    {
        MatchNotices.Plan p = MatchNotices.plan(result(match("A", "t1", "k1", "approved", 5, 5, true)), BOARD, titles(BOARD), NO_HIDDEN);
        assertEquals(Collections.singletonList("Task complete: Bury 5 bones (Dragon bones)"), texts(p));
        assertEquals("Bury 5 bones", p.notices.get(0).taskEffect);
        assertTrue(p.celebrations.isEmpty());
    }

    @Test
    public void aTaskTheBoardDoesNotNameGoesByItsTile()
    {
        MatchNotices.Plan p = MatchNotices.plan(result(match("A", "t1", "unknown", "pending", 1, 1, true)), BOARD, titles(BOARD), NO_HIDDEN);
        assertEquals(Collections.singletonList("Task complete: Dragon bones - waiting for review"), texts(p));
        assertEquals("Dragon bones", p.notices.get(0).taskEffect);
        // the side panel line falls back to the tile's title
        assertEquals("Dragon bones", p.counted);
    }

    @Test
    public void tileCompleteIsSaidOnceAndCelebrated()
    {
        ApiModels.EventResult r = result(match("A", "t1", "k1", "approved", 5, 5, true), match("A", "t1", "k2", "approved", 1, 1, true));
        r.matchedTiles.add(done("A", "t1"));
        MatchNotices.Plan p = MatchNotices.plan(r, BOARD, titles(BOARD), NO_HIDDEN);
        assertEquals(Collections.singletonList("Tile complete: Dragon bones"), texts(p));
        assertEquals(1, p.celebrations.size());
        assertEquals("A", p.celebrations.get(0).teamBoardId);
        assertEquals("t1", p.celebrations.get(0).tileId);
        assertEquals("Dragon bones", p.celebrations.get(0).title);
        assertEquals("Bury 5 bones, Kill Vorkath", p.counted);
    }

    @Test
    public void anAlreadyFinishedTileIsAnExtraDropWithNoSecondCelebration()
    {
        ApiModels.EventResult r = result(match("A", "t2", "k3", "pending", 3, 1, true));
        r.matchedTiles.add(done("A", "t2"));
        MatchNotices.Plan p = MatchNotices.plan(r, BOARD, titles(BOARD), NO_HIDDEN);
        assertEquals(Collections.singletonList("Extra drop logged for Done already (already done) - waiting for review"), texts(p));
        assertTrue(p.celebrations.isEmpty());
    }

    @Test
    public void aHiddenSecretTileNeverShowsItsAmount()
    {
        ApiModels.Tile secret = tile("s1", "?", false);
        secret.secret = true;
        ApiModels.BoardResponse b = board(secret);
        Set<String> hidden = new HashSet<>(Collections.singletonList("s1"));
        MatchNotices.Plan p = MatchNotices.plan(result(match("A", "s1", null, "approved", 3, 10, false)), b, titles(b), hidden);
        assertEquals(Collections.singletonList("Counted: Secret tile"), texts(p));
        // the server leaves out which secret tile it was: still no amounts, even if it were to send them
        MatchNotices.Plan q = MatchNotices.plan(result(match("A", null, null, "pending", 3, 10, false)), b, titles(b), hidden);
        assertEquals(Collections.singletonList("Counted: a secret tile - waiting for review"), texts(q));
        assertNull(q.counted);
    }

    @Test
    public void aSecretTileBeingFinishedShowsOnlyItsName()
    {
        ApiModels.Tile secret = tile("s1", "?", false);
        secret.secret = true;
        ApiModels.BoardResponse b = board(secret);
        ApiModels.EventResult r = result(match("A", "s1", "hiddenTask", "approved", 10, 10, true));
        r.matchedTiles.add(done("A", "s1"));
        MatchNotices.Plan p = MatchNotices.plan(r, b, titles(b), new HashSet<>(Collections.singletonList("s1")));
        assertEquals(Collections.singletonList("Tile complete: Secret tile"), texts(p));
        assertEquals("Secret tile", p.celebrations.get(0).title);
    }

    @Test
    public void twoBingosAtOnceOnlyTheBoardOnScreenHasNames()
    {
        // team A's board is on screen; the same drop also counted for team B in another bingo
        ApiModels.EventResult r = result(
            match("A", "t1", "k1", "approved", 1, 5, false),
            match("B", "other-tile", "other-task", "approved", 1, 1, true));
        r.matchedTiles.add(done("B", "other-tile"));
        MatchNotices.Plan p = MatchNotices.plan(r, BOARD, titles(BOARD), NO_HIDDEN);
        assertEquals(Arrays.asList("Counted: Dragon bones (1/5)", "Tile complete: a tile"), texts(p));
        // the other board's tile is celebrated on its own team board, not the one on screen
        assertEquals(1, p.celebrations.size());
        assertEquals("B", p.celebrations.get(0).teamBoardId);
        assertEquals("other-tile", p.celebrations.get(0).tileId);
        assertNull(p.celebrations.get(0).title);
        assertEquals("Bury 5 bones", p.counted);
    }

    @Test
    public void theCelebratedTeamComesFromTheMatchWhenTheTileEntryHasNone()
    {
        ApiModels.EventResult r = result(match("A", "t1", "k1", "approved", 5, 5, true));
        r.matchedTiles.add(done(null, "t1"));
        assertEquals("A", MatchNotices.plan(r, BOARD, titles(BOARD), NO_HIDDEN).celebrations.get(0).teamBoardId);
        // neither says: null, and the plugin uses the board on screen
        ApiModels.EventResult s = result(match(null, "t1", "k1", "approved", 5, 5, true));
        s.matchedTiles.add(done(null, "t1"));
        assertNull(MatchNotices.plan(s, BOARD, titles(BOARD), NO_HIDDEN).celebrations.get(0).teamBoardId);
    }

    @Test
    public void orOptionsTheFinishedOptionIsTheTaskThatCompletes()
    {
        ApiModels.Task a = task("o1", "Abyssal whip", false);
        ApiModels.Task b = task("o2", "Kraken tentacle", false);
        a.group = 1;
        b.group = 1;
        ApiModels.BoardResponse board = board(tile("t", "Slayer uniques", false, a, b, task("k", "Something else", false)));
        MatchNotices.Plan p = MatchNotices.plan(result(match("A", "t", "o2", "approved", 1, 1, true)), board, titles(board), NO_HIDDEN);
        assertEquals(Collections.singletonList("Task complete: Kraken tentacle (Slayer uniques)"), texts(p));
    }

    @Test
    public void theSameTaskTwiceInOneAnswerIsAnnouncedOnce()
    {
        ApiModels.EventResult r = result(match("A", "t1", "k1", "approved", 5, 5, true), match("A", "t1", "k1", "approved", 5, 5, true));
        MatchNotices.Plan p = MatchNotices.plan(r, BOARD, titles(BOARD), NO_HIDDEN);
        assertEquals(Collections.singletonList("Task complete: Bury 5 bones (Dragon bones)"), texts(p));
        assertEquals("Bury 5 bones", p.counted);
    }

    @Test
    public void withNoBoardLoadedNothingIsNamed()
    {
        MatchNotices.Plan p = MatchNotices.plan(result(match("A", "t1", "k1", "approved", 1, 5, false)), null, new HashMap<>(), NO_HIDDEN);
        assertEquals(Collections.singletonList("Counted: a tile (1/5)"), texts(p));
        assertNull(p.counted);
        assertTrue(MatchNotices.plan(null, BOARD, titles(BOARD), NO_HIDDEN).notices.isEmpty());
        assertTrue(MatchNotices.plan(new ApiModels.EventResult(), BOARD, titles(BOARD), NO_HIDDEN).notices.isEmpty());
    }

    @Test
    public void namesAreJoinedTwoAtMostThenACount()
    {
        assertNull(MatchNotices.joinNames(Collections.emptyList()));
        assertNull(MatchNotices.joinNames(null));
        assertEquals("a", MatchNotices.joinNames(Collections.singletonList("a")));
        assertEquals("a, b", MatchNotices.joinNames(Arrays.asList("a", "b")));
        assertEquals("a, b +1", MatchNotices.joinNames(Arrays.asList("a", "b", "c")));
        assertEquals("a, b +3", MatchNotices.joinNames(Arrays.asList("a", "b", "c", "d", "e")));
    }

    @Test
    public void taskNamesIndexTheWholeBoard()
    {
        Map<String, String> names = MatchNotices.taskNames(BOARD);
        assertEquals(3, names.size());
        assertEquals("Kill Vorkath", names.get("k2"));
        assertTrue(MatchNotices.taskNames(null).isEmpty());
        assertTrue(MatchNotices.taskNames(new ApiModels.BoardResponse()).isEmpty());
    }
}
