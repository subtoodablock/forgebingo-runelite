package com.forgebingo.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.Rectangle;
import org.junit.Test;

public class BoardLayoutTest
{
    private static void assertInside(Rectangle outer, Rectangle inner)
    {
        assertTrue(inner + " inside " + outer, outer.contains(inner));
    }

    @Test
    public void tenByTenFitsFixedModeWithMinimumCells()
    {
        BoardLayout l = BoardLayout.compute(765, 503, 10);
        assertTrue(l.cell >= BoardLayout.MIN_CELL);
        Rectangle view = new Rectangle(0, 0, 765, 503);
        assertInside(view, l.board());
        assertInside(l.board(), l.grid());
        assertInside(view, l.card());
        assertTrue(l.board().height <= 503 * BoardLayout.MAX_FRACTION + 1);
    }

    @Test
    public void boardIsCentredAndCapped()
    {
        for (int n = 3; n <= 10; n++)
        {
            BoardLayout l = BoardLayout.compute(1920, 1080, n);
            Rectangle b = l.board();
            assertTrue(b.height <= 1080 * BoardLayout.MAX_FRACTION + 1);
            assertTrue(Math.abs((b.x + b.width / 2) - 960) <= 1);
            assertTrue(Math.abs((b.y + b.height / 2) - 540) <= 1);
            assertEquals(l.grid().width, l.grid().height);
            // last cell sits flush with the grid's bottom-right corner
            Rectangle last = l.cell(n - 1, n - 1);
            Rectangle g = l.grid();
            assertEquals(g.x + g.width, last.x + last.width);
            assertEquals(g.y + g.height, last.y + last.height);
            assertEquals(BoardLayout.GAP, l.gap);
        }
    }

    @Test
    public void tinyViewportStillFits()
    {
        BoardLayout l = BoardLayout.compute(200, 160, 10);
        Rectangle view = new Rectangle(0, 0, 200, 160);
        assertInside(view, l.board());
        assertInside(view, l.card());
        assertTrue(l.cell >= 2);
        BoardLayout z = BoardLayout.compute(0, 0, 10);
        assertTrue(z.cell >= 2);
    }

    @Test
    public void smallViewportGrowsCellsToMinimumWhenThereIsRoom()
    {
        // 80% of 420 leaves cells under 28, but the whole viewport fits 28px cells
        BoardLayout l = BoardLayout.compute(640, 420, 10);
        assertEquals(BoardLayout.MIN_CELL, l.cell);
        assertInside(new Rectangle(0, 0, 640, 420), l.board());
    }

    @Test
    public void cellAtHitsCellsAndMissesGaps()
    {
        BoardLayout l = BoardLayout.compute(1280, 900, 5);
        for (int r = 0; r < 5; r++)
        {
            for (int c = 0; c < 5; c++)
            {
                Rectangle cell = l.cell(r, c);
                assertEquals(r * 5 + c, l.cellAt(cell.x, cell.y));
                assertEquals(r * 5 + c, l.cellAt(cell.x + cell.width - 1, cell.y + cell.height - 1));
                assertEquals(r * 5 + c, l.cellAt((int) cell.getCenterX(), (int) cell.getCenterY()));
            }
        }
        Rectangle first = l.cell(0, 0);
        assertEquals(-1, l.cellAt(first.x + first.width, first.y)); // the gap after the first cell
        assertEquals(-1, l.cellAt(first.x - 1, first.y));
        Rectangle g = l.grid();
        assertEquals(-1, l.cellAt(g.x + g.width, g.y));
        assertEquals(-1, l.cellAt(g.x, g.y + g.height));
    }

    @Test
    public void insideBoardCloseButtonAndCard()
    {
        BoardLayout l = BoardLayout.compute(1280, 900, 4);
        Rectangle b = l.board();
        assertTrue(l.insideBoard(b.x, b.y));
        assertTrue(l.insideBoard(b.x + b.width - 1, b.y + b.height - 1));
        assertFalse(l.insideBoard(b.x - 1, b.y));
        assertFalse(l.insideBoard(b.x + b.width, b.y));
        assertFalse(l.insideBoard(5, 5));
        Rectangle close = l.closeButton();
        assertInside(b, close);
        assertTrue(l.onCloseButton((int) close.getCenterX(), (int) close.getCenterY()));
        assertEquals(-1, l.cellAt((int) close.getCenterX(), (int) close.getCenterY()));
        Rectangle card = l.card();
        assertTrue(l.insideCard((int) card.getCenterX(), (int) card.getCenterY()));
        assertFalse(l.insideCard(card.x - 1, card.y));
        // card is about 60% of the board's width and centred on it
        assertTrue(Math.abs(card.width - b.width * 0.6) <= 1);
        assertTrue(Math.abs(card.getCenterX() - b.getCenterX()) <= 1);
    }

    @Test
    public void cardHasAReadableMinimumOnSmallBoards()
    {
        BoardLayout l = BoardLayout.compute(765, 503, 10);
        assertTrue(l.card().width >= 260);
        assertTrue(l.card().height >= 220);
    }

    @Test
    public void messageBoxIsCentred()
    {
        Rectangle m = BoardLayout.message(765, 503);
        assertInside(new Rectangle(0, 0, 765, 503), m);
        assertTrue(Math.abs(m.getCenterX() - 382.5) <= 1);
    }

    @Test
    public void easeOutIsClampedAndMonotonic()
    {
        assertEquals(0.0, BoardLayout.easeOut(-1), 1e-9);
        assertEquals(0.0, BoardLayout.easeOut(0), 1e-9);
        assertEquals(1.0, BoardLayout.easeOut(1), 1e-9);
        assertEquals(1.0, BoardLayout.easeOut(3), 1e-9);
        assertTrue(BoardLayout.easeOut(0.5) > 0.5); // ease-out is ahead of linear
        double prev = 0;
        for (int i = 1; i <= 20; i++)
        {
            double v = BoardLayout.easeOut(i / 20.0);
            assertTrue(v >= prev);
            prev = v;
        }
    }
}
