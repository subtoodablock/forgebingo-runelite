package com.forgebingo.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class ProofPlanTest
{
    private static ApiModels.Match match(String team, String tile, String task, String submission)
    {
        ApiModels.Match m = new ApiModels.Match();
        m.teamId = team;
        m.tileId = tile;
        m.taskId = task;
        m.submissionId = submission;
        return m;
    }

    private static Map<String, String> map(String... pairs)
    {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) m.put(pairs[i], pairs[i + 1]);
        return m;
    }

    private static ApiModels.Tile tile(String id, String title)
    {
        ApiModels.Tile t = new ApiModels.Tile();
        t.id = id;
        t.title = title;
        return t;
    }

    private static ApiModels.BoardResponse board(ApiModels.Tile... tiles)
    {
        ApiModels.BoardResponse b = new ApiModels.BoardResponse();
        b.tiles = new ArrayList<>(Arrays.asList(tiles));
        return b;
    }

    @Test
    public void oneUploadPerTeamWithItsOwnEntries()
    {
        List<ProofPlan.Team> teams = ProofPlan.teams(Arrays.asList(
            match("A", "t1", "k1", "s1"),
            match("A", "t2", "k2", "s2"),
            match("A", "t1", "k1", "s1")), // the same entry twice: attached once
            map("k1", "Bury bones", "k2", "Kill Vorkath"), map("t1", "Bones", "t2", "Dragons"));
        assertEquals(1, teams.size());
        ProofPlan.Team a = teams.get(0);
        assertEquals("A", a.teamId);
        assertEquals(Arrays.asList("s1", "s2"), a.submissionIds);
        // the file is named after the first task the drop counted for
        assertEquals("Bury bones", a.label);
        assertEquals(Arrays.asList("t1", "t2", "t1"), a.tileIds);
    }

    @Test
    public void twoBingosAtOnceEachTeamGetsItsOwnPicture()
    {
        List<ProofPlan.Team> teams = ProofPlan.teams(Arrays.asList(
            match("A", "t1", "k1", "s1"),
            match("B", "x9", "other", "s7"),
            match("A", "t2", "k2", "s2")),
            map("k1", "Bury bones"), map("t1", "Bones", "t2", "Dragons"));
        assertEquals(2, teams.size());
        assertEquals("A", teams.get(0).teamId);
        assertEquals(Arrays.asList("s1", "s2"), teams.get(0).submissionIds);
        assertEquals("Bury bones", teams.get(0).label);
        // team B's board is not the one on screen: nothing to name its file after
        assertEquals("B", teams.get(1).teamId);
        assertEquals(Collections.singletonList("s7"), teams.get(1).submissionIds);
        assertNull(teams.get(1).label);
        assertEquals(Collections.singletonList("x9"), teams.get(1).tileIds);
    }

    @Test
    public void theLabelFallsBackToTheTileTitle()
    {
        List<ProofPlan.Team> teams = ProofPlan.teams(Collections.singletonList(match("A", "t1", "unknown", "s1")),
            new HashMap<>(), map("t1", "Bones"));
        assertEquals("Bones", teams.get(0).label);
    }

    @Test
    public void matchesWithoutAnEntryOrTeamMakeNoUpload()
    {
        assertTrue(ProofPlan.teams(Arrays.asList(match("A", "t1", "k1", null), match(null, "t1", "k1", "s1"), null),
            new HashMap<>(), new HashMap<>()).isEmpty());
        assertTrue(ProofPlan.teams(null, new HashMap<>(), new HashMap<>()).isEmpty());
        // but with an entry on the team, such a match's tile is still named on the stamp
        List<ProofPlan.Team> teams = ProofPlan.teams(Arrays.asList(match("A", "t1", "k1", "s1"), match("A", "t2", "k2", null)),
            new HashMap<>(), new HashMap<>());
        assertEquals(Collections.singletonList("s1"), teams.get(0).submissionIds);
        assertEquals(Arrays.asList("t1", "t2"), teams.get(0).tileIds);
    }

    @Test
    public void atMostTwentyFiveEntriesPerPicture()
    {
        List<ApiModels.Match> many = new ArrayList<>();
        for (int i = 0; i < 40; i++) many.add(match("A", "t" + i, "k" + i, "s" + i));
        List<ProofPlan.Team> teams = ProofPlan.teams(many, new HashMap<>(), new HashMap<>());
        assertEquals(25, teams.get(0).submissionIds.size());
        assertEquals("s0", teams.get(0).submissionIds.get(0));
        assertEquals("s24", teams.get(0).submissionIds.get(24));
    }

    @Test
    public void aRaidChestAlsoProvesTheRaidItself()
    {
        ApiModels.EventResult chest = new ApiModels.EventResult();
        chest.eventId = "chest-event";
        chest.matched = new ArrayList<>(Collections.singletonList(match("A", "purple", "k-item", "s-chest")));
        ApiModels.EventResult kill = new ApiModels.EventResult();
        kill.matched = new ArrayList<>(Arrays.asList(match("A", "kc-tile", "k-kc", "s-kc"), match("B", "kc-b", "k-kc-b", "s-kc-b")));

        ApiModels.EventResult both = ProofPlan.withRaidKill(chest, kill);
        assertEquals("chest-event", both.eventId);
        assertEquals(3, both.matched.size());
        // the answers themselves are left as they were
        assertEquals(1, chest.matched.size());
        assertEquals(2, kill.matched.size());

        List<ProofPlan.Team> teams = ProofPlan.teams(both.matched, map("k-item", "Twisted bow", "k-kc", "50 raids"), new HashMap<>());
        assertEquals(2, teams.size());
        assertEquals(Arrays.asList("s-chest", "s-kc"), teams.get(0).submissionIds);
        assertEquals("Twisted bow", teams.get(0).label);
        assertEquals(Arrays.asList("purple", "kc-tile"), teams.get(0).tileIds);
        assertEquals(Collections.singletonList("s-kc-b"), teams.get(1).submissionIds);
    }

    @Test
    public void aChestThatCountedForNothingStillProvesTheRaid()
    {
        ApiModels.EventResult chest = new ApiModels.EventResult();
        ApiModels.EventResult kill = new ApiModels.EventResult();
        kill.matched = new ArrayList<>(Collections.singletonList(match("A", "kc-tile", "k-kc", "s-kc")));
        ApiModels.EventResult both = ProofPlan.withRaidKill(chest, kill);
        assertTrue(both.isMatched());
        assertEquals("s-kc", both.matched.get(0).submissionId);
    }

    @Test
    public void withNoRaidKillTheChestAnswerIsUsedAsItIs()
    {
        ApiModels.EventResult chest = new ApiModels.EventResult();
        assertSame(chest, ProofPlan.withRaidKill(chest, null));
        assertSame(chest, ProofPlan.withRaidKill(chest, new ApiModels.EventResult()));
        assertNull(ProofPlan.withRaidKill(null, new ApiModels.EventResult()));
    }

    @Test
    public void theStampNamesTheTilesFromTheTeamsOwnBoard()
    {
        ApiModels.BoardResponse teamBoard = board(tile("t1", "Team name for t1"), tile("t2", "Second"), tile("t3", "Third"));
        Map<String, String> onScreen = map("t1", "On-screen name", "t9", "Only on screen");
        assertEquals("Team name for t1", ProofPlan.tiles(Collections.singletonList("t1"), teamBoard, onScreen));
        assertEquals("Team name for t1, Second", ProofPlan.tiles(Arrays.asList("t1", "t2", "t1"), teamBoard, onScreen));
        assertEquals("Team name for t1, Second +2", ProofPlan.tiles(Arrays.asList("t1", "t2", "t3", "t9"), teamBoard, onScreen));
        // the team's board could not be fetched: the board on screen names what it can
        assertEquals("On-screen name", ProofPlan.tiles(Arrays.asList("t1", "t2"), null, onScreen));
        assertNull(ProofPlan.tiles(Collections.singletonList("t2"), null, onScreen));
        assertNull(ProofPlan.tiles(Collections.emptyList(), teamBoard, onScreen));
    }

    @Test
    public void aHiddenSecretTileIsStampedByItsShownName()
    {
        ApiModels.Tile secret = tile("s1", "?");
        secret.secret = true;
        assertEquals("Secret tile", ProofPlan.tiles(Collections.singletonList("s1"), board(secret), new HashMap<>()));
    }

    private static final long TTL = 10 * 60_000L;

    @Test
    public void aBusyOrUnreachableServerGetsTheSamePictureAgain()
    {
        // never completed (timeout, no network), rate limited, server error: worth another try
        assertEquals(5_000, ProofPlan.retryDelay(0, null, 0, 1, 1_000, TTL));
        assertEquals(10_000, ProofPlan.retryDelay(503, null, 0, 2, 1_000, TTL));
        // the server's own wait is honoured (it answers 429 with 60 s)...
        assertEquals(60_000, ProofPlan.retryDelay(429, "rate_limited", 60_000, 1, 1_000, TTL));
        // ...up to a limit
        assertEquals(ProofPlan.RETRY_MAX_MILLIS, ProofPlan.retryDelay(429, null, 15 * 60_000L, 1, 1_000, TTL));
    }

    @Test
    public void threeSendsAtMost()
    {
        assertTrue(ProofPlan.retryDelay(503, null, 0, 1, 0, TTL) >= 0);
        assertTrue(ProofPlan.retryDelay(503, null, 0, 2, 0, TTL) >= 0);
        assertEquals(-1, ProofPlan.retryDelay(503, null, 0, 3, 0, TTL));
    }

    @Test
    public void anAnswerThatWillNotChangeIsNotRetried()
    {
        assertEquals(-1, ProofPlan.retryDelay(404, null, 0, 1, 0, TTL));
        assertEquals(-1, ProofPlan.retryDelay(409, "event_not_open", 0, 1, 0, TTL));
        assertEquals(-1, ProofPlan.retryDelay(409, "submission_reviewed", 0, 1, 0, TTL));
        assertEquals(-1, ProofPlan.retryDelay(400, "invalid_targets", 0, 1, 0, TTL));
        assertEquals(-1, ProofPlan.retryDelay(413, "payload_too_large", 0, 1, 0, TTL));
        assertEquals(-1, ProofPlan.retryDelay(401, "invalid_api_key", 0, 1, 0, TTL));
    }

    @Test
    public void noRetryPastTheScreenshotTimeLimit()
    {
        assertEquals(60_000, ProofPlan.retryDelay(429, null, 60_000, 1, TTL - 61_000, TTL));
        assertEquals(-1, ProofPlan.retryDelay(429, null, 60_000, 1, TTL - 60_000, TTL));
        assertEquals(-1, ProofPlan.retryDelay(0, null, 0, 1, TTL + 1, TTL));
    }

    @Test
    public void severalFramesSayWhichTheyAre()
    {
        assertEquals("Bones", ProofPlan.stampText("Bones", 1, 1));
        assertNull(ProofPlan.stampText(null, 1, 1));
        assertEquals("Bones (1 of 2)", ProofPlan.stampText("Bones", 1, 2));
        assertEquals("Bones (2 of 2)", ProofPlan.stampText("Bones", 2, 2));
        assertEquals("3 of 3", ProofPlan.stampText(null, 3, 3));
    }
}
