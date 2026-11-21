package com.forgebingo.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class RejectedNoticesTest
{
    private static final long HOUR = 60L * 60 * 1000;
    private static final long T0 = 1_800_000_000_000L;

    private static ApiModels.Tile rejected(String id, String title, String reason)
    {
        ApiModels.Task k = new ApiModels.Task();
        k.id = "k-" + id;
        k.description = "the secret task text";
        k.requiredQty = 1;
        k.rejectedReason = reason;
        ApiModels.Tile t = new ApiModels.Tile();
        t.id = id;
        t.title = title;
        t.points = 1;
        t.tasks = new ArrayList<>(Arrays.asList(k));
        return t;
    }

    private static List<String> texts(List<RejectedNotices.Notice> list)
    {
        List<String> out = new ArrayList<>();
        for (RejectedNotices.Notice n : list) out.add(n.text());
        return out;
    }

    @Test
    public void oneLinePerRejectedTileNamingTileAndReason()
    {
        ApiModels.Tile fine = rejected("t2", "Dragon pickaxe", null);
        ApiModels.Tile waiting = rejected("t3", "Zenyte shard", "Too dark");
        waiting.tasks.get(0).pendingQty = 1;                      // new proof is with the reviewers: not "rejected" now
        ApiModels.Tile done = rejected("t4", "Abyssal whip", "Old reason");
        done.completed = true;
        List<RejectedNotices.Notice> found = RejectedNotices.find(Arrays.asList(
            rejected("t1", "Bandos hilt", "Drop message not visible"), fine, waiting, done, null));
        assertEquals(Arrays.asList("Rejected: Bandos hilt - Drop message not visible"), texts(found));
        assertEquals("t1", found.get(0).tileId);
        assertTrue(RejectedNotices.find(null).isEmpty());
    }

    @Test
    public void aHiddenSecretTileGivesItsShownNameAndNothingElse()
    {
        ApiModels.Tile named = rejected("s1", "Mystery Wildy tile", "That is not the Voidwaker hilt");
        named.secret = true;
        ApiModels.Tile unnamed = rejected("s2", "?", "Wrong boss, it is Vorkath");
        unnamed.secret = true;
        List<RejectedNotices.Notice> found = RejectedNotices.find(Arrays.asList(named, unnamed));
        assertEquals(Arrays.asList("Rejected: Mystery Wildy tile", "Rejected: Secret tile"), texts(found));
        for (RejectedNotices.Notice n : found)
        {
            assertNull(n.reason);
            assertFalse(n.text().contains("Voidwaker"));
            assertFalse(n.text().contains("Vorkath"));
            assertFalse(n.text().contains("secret task text"));
        }
    }

    @Test
    public void aVeryLongReasonIsCutShort()
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 80; i++) sb.append("word ");
        RejectedNotices.Notice n = RejectedNotices.find(Arrays.asList(rejected("t1", "Tile", sb.toString()))).get(0);
        assertTrue(n.reason.length() <= RejectedNotices.MAX_REASON);
        assertTrue(n.reason.endsWith("..."));
    }

    @Test
    public void newestFirstAndGoneAfterTheWindow()
    {
        RejectedNotices.Log log = new RejectedNotices.Log();
        ApiModels.Tile a = rejected("a", "Alpha", "r1");
        ApiModels.Tile b = rejected("b", "Beta", "r2");
        ApiModels.Tile c = rejected("c", "Gamma", "r3");
        assertTrue(log.update(RejectedNotices.find(Arrays.asList(a, b)), T0));
        // seen together: board order
        assertEquals(Arrays.asList("Rejected: Alpha - r1", "Rejected: Beta - r2"), texts(log.visible(T0)));
        // a later one goes on top; nothing new to remember on a repeat poll
        assertTrue(log.update(RejectedNotices.find(Arrays.asList(a, b, c)), T0 + HOUR));
        assertFalse(log.update(RejectedNotices.find(Arrays.asList(a, b, c)), T0 + 2 * HOUR));
        assertEquals(Arrays.asList("Rejected: Gamma - r3", "Rejected: Alpha - r1", "Rejected: Beta - r2"), texts(log.visible(T0 + 2 * HOUR)));
        // 48 hours after they were first seen the old ones drop off, the newer one an hour later
        assertEquals(Arrays.asList("Rejected: Gamma - r3"), texts(log.visible(T0 + RejectedNotices.WINDOW_MS)));
        assertTrue(log.visible(T0 + HOUR + RejectedNotices.WINDOW_MS).isEmpty());
    }

    @Test
    public void dismissedLinesStayAwayAcrossPollsAndRestarts()
    {
        RejectedNotices.Log log = new RejectedNotices.Log();
        List<ApiModels.Tile> board = Arrays.asList(rejected("a", "Alpha", "r1"), rejected("b", "Beta", "r2"));
        log.update(RejectedNotices.find(board), T0);
        String key = log.visible(T0).get(0).key;
        assertTrue(log.dismiss(key));
        assertFalse(log.dismiss(key));
        assertFalse(log.dismiss("nope"));
        assertEquals(Arrays.asList("Rejected: Beta - r2"), texts(log.visible(T0)));
        // next poll: still away
        log.update(RejectedNotices.find(board), T0 + HOUR);
        assertEquals(Arrays.asList("Rejected: Beta - r2"), texts(log.visible(T0 + HOUR)));
        // restart: the saved text brings back both the dismissal and when each line was first seen
        RejectedNotices.Log again = new RejectedNotices.Log();
        again.load(log.save());
        assertFalse(again.update(RejectedNotices.find(board), T0 + 2 * HOUR));
        assertEquals(Arrays.asList("Rejected: Beta - r2"), texts(again.visible(T0 + 2 * HOUR)));
        assertEquals(T0, again.visible(T0 + 2 * HOUR).get(0).seenAt());
    }

    @Test
    public void aNewRejectionOfTheSameTileShowsAgain()
    {
        RejectedNotices.Log log = new RejectedNotices.Log();
        ApiModels.Tile a = rejected("a", "Alpha", "Drop message not visible");
        log.update(RejectedNotices.find(Arrays.asList(a)), T0);
        log.dismiss(log.visible(T0).get(0).key);
        assertTrue(log.visible(T0).isEmpty());

        // a different reason is a new line straight away
        ApiModels.Tile other = rejected("a", "Alpha", "Wrong account");
        assertTrue(log.update(RejectedNotices.find(Arrays.asList(other)), T0 + HOUR));
        assertEquals(Arrays.asList("Rejected: Alpha - Wrong account"), texts(log.visible(T0 + HOUR)));
        log.dismiss(log.visible(T0 + HOUR).get(0).key);

        // new proof goes in (waiting), then is rejected for the very same reason: shown again
        ApiModels.Tile waiting = rejected("a", "Alpha", null);
        waiting.tasks.get(0).pendingQty = 1;
        assertTrue(log.update(RejectedNotices.find(Arrays.asList(waiting)), T0 + 2 * HOUR));
        assertTrue(log.visible(T0 + 2 * HOUR).isEmpty());
        assertTrue(log.update(RejectedNotices.find(Arrays.asList(other)), T0 + 3 * HOUR));
        assertEquals(Arrays.asList("Rejected: Alpha - Wrong account"), texts(log.visible(T0 + 3 * HOUR)));
        assertEquals(T0 + 3 * HOUR, log.visible(T0 + 3 * HOUR).get(0).seenAt());
    }

    @Test
    public void moreThanFiveAreSummedUp()
    {
        assertEquals("", RejectedNotices.moreText(0));
        assertEquals("", RejectedNotices.moreText(RejectedNotices.MAX_SHOWN));
        assertEquals("+1 more", RejectedNotices.moreText(RejectedNotices.MAX_SHOWN + 1));
        assertEquals("+3 more", RejectedNotices.moreText(8));
    }

    @Test
    public void badSavedTextAndClockChangesAreHarmless()
    {
        RejectedNotices.Log log = new RejectedNotices.Log();
        log.load("garbage;;a~b~c;<html>~1~0;ok-1:ff~" + T0 + "~1;x~-5~0");
        assertEquals("ok-1:ff~" + T0 + "~1", log.save());
        log.load(null);
        assertEquals("", log.save());

        // the clock went back a week after a line was first seen: it must not live a week longer
        ApiModels.Tile a = rejected("a", "Alpha", "r1");
        log.update(RejectedNotices.find(Arrays.asList(a)), T0);
        long earlier = T0 - 7 * 24 * HOUR;
        assertTrue(log.update(RejectedNotices.find(Arrays.asList(a)), earlier));
        assertEquals(1, log.visible(earlier).size());
        assertTrue(log.visible(earlier + RejectedNotices.WINDOW_MS).isEmpty());

        // a tile without an id, or an id that could not be a key, is left out rather than breaking the list
        assertTrue(RejectedNotices.find(Arrays.asList(rejected(null, "No id", "r"), rejected("bad id;~", "Odd id", "r"))).isEmpty());
    }
}
