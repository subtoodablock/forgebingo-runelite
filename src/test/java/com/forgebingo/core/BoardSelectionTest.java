package com.forgebingo.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.Test;

public class BoardSelectionTest
{
    private static ApiModels.BoardSummary board(String id, boolean active)
    {
        ApiModels.BoardSummary b = new ApiModels.BoardSummary();
        b.teamBoardId = id;
        b.active = active;
        b.eventId = "event-" + id;
        return b;
    }

    @Test
    public void thePickedBoardIsShownEvenWhenItIsNotLive()
    {
        List<ApiModels.BoardSummary> boards = Arrays.asList(board("a", true), board("b", false));
        assertEquals("b", BoardSelection.select(boards, "b").teamBoardId);
    }

    @Test
    public void withNoPickTheFirstLiveBoardIsShown()
    {
        // the player never chose a board: the saved choice is empty, and the first live board shows anyway
        List<ApiModels.BoardSummary> boards = Arrays.asList(board("old", false), board("live1", true), board("live2", true));
        assertEquals("live1", BoardSelection.select(boards, "").teamBoardId);
        assertEquals("live1", BoardSelection.select(boards, null).teamBoardId);
        // a saved choice that is gone (team deleted, another key) falls back the same way
        assertEquals("live1", BoardSelection.select(boards, "gone").teamBoardId);
    }

    @Test
    public void withNothingLiveTheFirstBoardIsShown()
    {
        List<ApiModels.BoardSummary> boards = Arrays.asList(board("x", false), board("y", false));
        assertEquals("x", BoardSelection.select(boards, "").teamBoardId);
        assertNull(BoardSelection.select(Collections.emptyList(), "x"));
        assertNull(BoardSelection.select(null, "x"));
    }

    @Test
    public void countsLiveBoardsAndListsIds()
    {
        List<ApiModels.BoardSummary> boards = Arrays.asList(board("a", true), board("b", false), board("c", true));
        assertEquals(2, BoardSelection.liveCount(boards));
        assertEquals(new HashSet<>(Arrays.asList("a", "b", "c")), BoardSelection.ids(boards));
        assertEquals(0, BoardSelection.liveCount(Collections.emptyList()));
        assertTrue(BoardSelection.ids(null).isEmpty());
    }

    @Test
    public void aBoardIsNewlyOpenedOnlyWhenItWasNotKnownBefore()
    {
        ApiModels.BoardSummary a = board("a", true);
        ApiModels.BoardSummary b = board("b", true);
        List<ApiModels.BoardSummary> boards = Arrays.asList(a, b);
        // the first list of a session (or after another key was pasted): nothing is "new"
        assertNull(BoardSelection.newlyOpened(boards, null));
        // both known already
        assertNull(BoardSelection.newlyOpened(boards, new HashSet<>(Arrays.asList("a", "b"))));
        // b appeared while running: its bingo just started
        assertSame(b, BoardSelection.newlyOpened(boards, new HashSet<>(Collections.singletonList("a"))));
        // two appear at once: the first in list order
        assertSame(a, BoardSelection.newlyOpened(boards, new HashSet<>()));
    }

    @Test
    public void aNewBoardThatIsNotLiveIsNotAnOpening()
    {
        List<ApiModels.BoardSummary> boards = Arrays.asList(board("a", true), board("finished", false));
        assertNull(BoardSelection.newlyOpened(boards, new HashSet<>(Collections.singletonList("a"))));
    }
}
