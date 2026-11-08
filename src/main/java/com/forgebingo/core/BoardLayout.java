package com.forgebingo.core;

import java.awt.Rectangle;

/**
 * Where the on-screen board overlay goes (owner request, 2026-11-07): a centred board panel at most ~80% of the
 * smaller viewport side, its square grid of cells, the close button, the card a tile opens into, and hit-testing for
 * clicks. Pure and immutable, so the overlay (client thread) and the mouse listener (AWT thread) can share one.
 * Coordinates are game canvas coordinates: RuneLite's stretched mode translates mouse events into this space before
 * plugin listeners see them, and overlays draw in it too.
 */
public final class BoardLayout
{
    public static final double MAX_FRACTION = 0.8;
    public static final int MIN_CELL = 28;
    public static final int GAP = 4;
    public static final int PAD = 10;
    public static final int HEADER = 42;
    public static final int CLOSE = 20;
    /** space kept free around the board when a small viewport forces it bigger than MAX_FRACTION */
    public static final int MARGIN = 4;

    public final int viewW;
    public final int viewH;
    public final int size;
    public final int cell;
    public final int gap;
    private final int bx;
    private final int by;
    private final int bw;
    private final int bh;
    private final int gx;
    private final int gy;
    private final int cardX;
    private final int cardY;
    private final int cardW;
    private final int cardH;

    private BoardLayout(int viewW, int viewH, int size, int cell, int gap)
    {
        this.viewW = viewW;
        this.viewH = viewH;
        this.size = size;
        this.cell = cell;
        this.gap = gap;
        int grid = cell * size + gap * (size - 1);
        bw = grid + 2 * PAD;
        bh = grid + HEADER + 2 * PAD;
        bx = (viewW - bw) / 2;
        by = (viewH - bh) / 2;
        gx = bx + PAD;
        gy = by + HEADER + PAD;

        int maxW = Math.max(1, viewW - 16);
        int maxH = Math.max(1, viewH - 16);
        cardW = Math.min(maxW, Math.max(Math.min(260, maxW), (int) Math.round(bw * 0.6)));
        cardH = Math.min(maxH, Math.max(Math.min(220, maxH), (int) Math.round(bh * 0.7)));
        cardX = clamp(bx + (bw - cardW) / 2, 8, viewW - 8 - cardW);
        cardY = clamp(by + (bh - cardH) / 2, 8, viewH - 8 - cardH);
    }

    /** Lays out an N x N board (N clamped to 1..20) in a viewport of the given size. */
    public static BoardLayout compute(int viewW, int viewH, int boardSize)
    {
        int vw = Math.max(1, viewW);
        int vh = Math.max(1, viewH);
        int n = Math.max(1, Math.min(20, boardSize));
        int gap = GAP;
        int avail = (int) (Math.min(vw, vh) * MAX_FRACTION);
        // the panel is taller than wide (header), so the height decides the grid side
        int gridAvail = Math.min(avail - 2 * PAD, avail - HEADER - 2 * PAD);
        int cell = (gridAvail - gap * (n - 1)) / n;
        if (cell < MIN_CELL)
        {
            // small viewport or big board: grow towards MIN_CELL while the board still fits on screen
            int fit = Math.min(vw - 2 * MARGIN - 2 * PAD, vh - 2 * MARGIN - HEADER - 2 * PAD);
            int fitCell = (fit - gap * (n - 1)) / n;
            if (fitCell < 8)
            {
                gap = 1;
                fitCell = (fit - (n - 1)) / n;
            }
            cell = Math.max(cell, Math.min(MIN_CELL, fitCell));
        }
        return new BoardLayout(vw, vh, n, Math.max(2, cell), gap);
    }

    /** The whole board panel (background, header and grid). */
    public Rectangle board()
    {
        return new Rectangle(bx, by, bw, bh);
    }

    /** The square grid of cells inside the panel. */
    public Rectangle grid()
    {
        int side = cell * size + gap * (size - 1);
        return new Rectangle(gx, gy, side, side);
    }

    public Rectangle cell(int row, int col)
    {
        return new Rectangle(gx + col * (cell + gap), gy + row * (cell + gap), cell, cell);
    }

    /** The close button in the header's top-right corner. */
    public Rectangle closeButton()
    {
        return new Rectangle(bx + bw - PAD - CLOSE, by + PAD, CLOSE, CLOSE);
    }

    /** The card a clicked tile opens into: centred on the board, ~60% of its width, kept inside the viewport. */
    public Rectangle card()
    {
        return new Rectangle(cardX, cardY, cardW, cardH);
    }

    /** The small "no board yet" message box, centred. */
    public static Rectangle message(int viewW, int viewH)
    {
        int w = Math.min(Math.max(1, viewW - 16), 340);
        int h = Math.min(Math.max(1, viewH - 16), 64);
        return new Rectangle((viewW - w) / 2, (viewH - h) / 2, w, h);
    }

    /** Index (row * size + col) of the cell under the point, or -1 for the gaps and anything off the grid. */
    public int cellAt(int x, int y)
    {
        int dx = x - gx;
        int dy = y - gy;
        if (dx < 0 || dy < 0) return -1;
        int step = cell + gap;
        int col = dx / step;
        int row = dy / step;
        if (col >= size || row >= size) return -1;
        if (dx - col * step >= cell || dy - row * step >= cell) return -1;
        return row * size + col;
    }

    public boolean insideBoard(int x, int y)
    {
        return x >= bx && y >= by && x < bx + bw && y < by + bh;
    }

    public boolean onCloseButton(int x, int y)
    {
        return closeButton().contains(x, y);
    }

    public boolean insideCard(int x, int y)
    {
        return x >= cardX && y >= cardY && x < cardX + cardW && y < cardY + cardH;
    }

    /** Cubic ease-out over 0..1 (clamped): fast start, soft landing. */
    public static double easeOut(double t)
    {
        double c = Math.max(0, Math.min(1, t));
        return 1 - Math.pow(1 - c, 3);
    }

    private static int clamp(int v, int lo, int hi)
    {
        if (hi < lo) return Math.max(0, (lo + hi) / 2); // no room for the margins: centre it
        return Math.max(lo, Math.min(hi, v));
    }
}
