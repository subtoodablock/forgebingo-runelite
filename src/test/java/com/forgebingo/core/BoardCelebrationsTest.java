package com.forgebingo.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class BoardCelebrationsTest
{
    /** A size x size board; tile ids are "r,c"; the given ids are finished. */
    private static ApiModels.BoardResponse board(int size, String... done)
    {
        Set<String> finished = new HashSet<>(Arrays.asList(done));
        ApiModels.BoardResponse b = new ApiModels.BoardResponse();
        b.board = new ApiModels.BoardInfo();
        b.board.size = size;
        b.tiles = new ArrayList<>();
        for (int r = 0; r < size; r++)
        {
            for (int c = 0; c < size; c++)
            {
                ApiModels.Tile t = new ApiModels.Tile();
                t.id = r + "," + c;
                t.row = r;
                t.col = c;
                t.title = "Tile " + t.id;
                t.completed = finished.contains(t.id);
                b.tiles.add(t);
            }
        }
        return b;
    }

    private static Set<String> set(String... ids)
    {
        return new HashSet<>(Arrays.asList(ids));
    }

    @Test
    public void countsRowsColumnsAndBothDiagonals()
    {
        ApiModels.BoardResponse b = board(3);
        assertEquals(0, BoardCelebrations.countLines(set(), b, 3));
        assertEquals(1, BoardCelebrations.countLines(set("0,0", "0,1", "0,2"), b, 3));           // a row
        assertEquals(1, BoardCelebrations.countLines(set("0,1", "1,1", "2,1"), b, 3));           // a column
        assertEquals(1, BoardCelebrations.countLines(set("0,0", "1,1", "2,2"), b, 3));           // diagonal
        assertEquals(1, BoardCelebrations.countLines(set("0,2", "1,1", "2,0"), b, 3));           // the other diagonal
        assertEquals(2, BoardCelebrations.countLines(set("0,0", "0,1", "0,2", "1,0", "2,0"), b, 3)); // row + column
        Set<String> all = new HashSet<>();
        for (ApiModels.Tile t : b.tiles) all.add(t.id);
        assertEquals(8, BoardCelebrations.countLines(all, b, 3));
        // ids that are not on the board change nothing
        assertEquals(0, BoardCelebrations.countLines(set("9,9", "nope"), b, 3));
    }

    @Test
    public void theFirstLookOnlyRecords()
    {
        BoardCelebrations.Change c = BoardCelebrations.changes(null, board(3, "0,0", "0,1", "0,2"));
        assertTrue(c.firstLook);
        assertEquals(set("0,0", "0,1", "0,2"), c.done);
        assertTrue(c.finishedTitles.isEmpty());
        assertEquals(0, c.newLines);
        assertNull(c.lineText());
        assertFalse(c.all);
    }

    @Test
    public void aFinishedBoardIsABlackoutEvenOnTheFirstLook()
    {
        ApiModels.BoardResponse full = board(2, "0,0", "0,1", "1,0", "1,1");
        assertTrue(BoardCelebrations.changes(null, full).all);
        assertTrue(BoardCelebrations.changes(set("0,0"), full).all);
        // a board with no tiles is never a blackout
        ApiModels.BoardResponse empty = board(1);
        empty.tiles.clear();
        assertFalse(BoardCelebrations.changes(null, empty).all);
    }

    @Test
    public void aTeammatesNewTileIsAnnouncedOnce()
    {
        Set<String> seen = set("0,0");
        ApiModels.BoardResponse b = board(3, "0,0", "1,1");
        BoardCelebrations.Change c = BoardCelebrations.changes(seen, b);
        assertFalse(c.firstLook);
        assertEquals(Collections.singletonList("Tile 1,1"), c.finishedTitles);
        assertEquals(0, c.newLines);
        // the seen set is the caller's to update
        assertEquals(set("0,0"), seen);
        seen.addAll(c.done);
        assertTrue(BoardCelebrations.changes(seen, b).finishedTitles.isEmpty());
    }

    @Test
    public void thePlayersOwnTileMarkedSeenFirstIsNotAnnouncedAgain()
    {
        // the player's own drop finished 0,1 and it was marked seen on this board as the server answered: the
        // refresh that follows must not announce it as a teammate's (the auto-selected board bug)
        Set<String> seen = set("0,0", "0,1");
        BoardCelebrations.Change c = BoardCelebrations.changes(seen, board(3, "0,0", "0,1"));
        assertTrue(c.finishedTitles.isEmpty());
        assertEquals(0, c.newLines);
    }

    @Test
    public void aNewLineAndADoubleLine()
    {
        BoardCelebrations.Change one = BoardCelebrations.changes(set("0,0", "0,1"), board(3, "0,0", "0,1", "0,2"));
        assertEquals(1, one.newLines);
        assertEquals("Line complete!", one.lineText());
        // the corner finishes a row and a column at once
        BoardCelebrations.Change two = BoardCelebrations.changes(set("0,1", "0,2", "1,0", "2,0"), board(3, "0,0", "0,1", "0,2", "1,0", "2,0"));
        assertEquals(2, two.newLines);
        assertEquals("Double line complete!", two.lineText());
    }

    @Test
    public void aSecretTileIsAnnouncedByItsShownName()
    {
        ApiModels.BoardResponse b = board(2, "0,0");
        b.tiles.get(0).title = "?";
        b.tiles.get(0).secret = true;
        b.tiles.get(0).completed = true;
        // a finished secret tile shows the title the server sends for it
        assertEquals(Collections.singletonList("?"), BoardCelebrations.changes(set(), b).finishedTitles);
    }

    @Test
    public void tilesWithoutAnIdAreIgnored()
    {
        ApiModels.BoardResponse b = board(2, "0,0");
        b.tiles.get(1).id = null;
        b.tiles.get(1).completed = true;
        BoardCelebrations.Change c = BoardCelebrations.changes(set(), b);
        assertEquals(set("0,0"), c.done);
        assertEquals(1, c.finishedTitles.size());
    }
}
