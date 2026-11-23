package com.forgebingo.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What to celebrate when a freshly fetched board is compared with the tiles already seen finished: teammates' tiles,
 * a new line, a blackout. Decides only; the plugin shows it and remembers what was seen.
 */
public final class BoardCelebrations
{
    private BoardCelebrations()
    {
    }

    /** The outcome of comparing a board with what was seen before. */
    public static final class Change
    {
        /** nothing was known about this board yet: record {@link #done}, replay nothing (a blackout still shows) */
        public boolean firstLook;
        /** every tile on the board is finished */
        public boolean all;
        /** ids of the tiles finished on this board now: add them to what was seen */
        public final Set<String> done = new HashSet<>();
        /** shown titles of tiles finished since the last look (by a teammate: the player's own are marked seen first) */
        public final List<String> finishedTitles = new ArrayList<>();
        /** lines completed since the last look, 0 when the count of finished tiles did not grow */
        public int newLines;

        /** the banner for {@link #newLines}, or null when there is no new line */
        public String lineText()
        {
            return newLines <= 0 ? null : newLines > 1 ? "Double line complete!" : "Line complete!";
        }
    }

    /**
     * @param seen tile ids already seen finished on this board, or null on the first look of the session (not changed)
     */
    public static Change changes(Set<String> seen, ApiModels.BoardResponse board)
    {
        Change c = new Change();
        for (ApiModels.Tile t : board.tiles)
        {
            if (t.completed && t.id != null) c.done.add(t.id);
        }
        c.all = !board.tiles.isEmpty() && c.done.size() >= board.tiles.size();
        if (seen == null)
        {
            c.firstLook = true;
            return c;
        }
        int size = board.size();
        int linesBefore = countLines(seen, board, size);
        Set<String> after = new HashSet<>(seen);
        for (ApiModels.Tile t : board.tiles)
        {
            if (t.completed && t.id != null && after.add(t.id)) c.finishedTitles.add(TileInfo.shownTitle(t));
        }
        int linesAfter = countLines(after, board, size);
        if (linesAfter > linesBefore && c.done.size() > seen.size()) c.newLines = linesAfter - linesBefore;
        return c;
    }

    /** Finished rows, columns and the two diagonals of a size x size board, given the ids of its finished tiles. */
    public static int countLines(Set<String> doneIds, ApiModels.BoardResponse board, int size)
    {
        boolean[][] d = new boolean[size][size];
        for (ApiModels.Tile t : board.tiles)
        {
            if (t.id != null && doneIds.contains(t.id) && t.row < size && t.col < size) d[t.row][t.col] = true;
        }
        int n = 0;
        for (int i = 0; i < size; i++)
        {
            boolean row = true;
            boolean col = true;
            for (int j = 0; j < size; j++)
            {
                row &= d[i][j];
                col &= d[j][i];
            }
            if (row) n++;
            if (col) n++;
        }
        boolean diag = true;
        boolean anti = true;
        for (int i = 0; i < size; i++)
        {
            diag &= d[i][i];
            anti &= d[i][size - 1 - i];
        }
        if (diag) n++;
        if (anti) n++;
        return n;
    }
}
