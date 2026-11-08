package com.forgebingo.core;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Whole-window proof screenshots (opt-in "Include sidebar in screenshots", owner request 2026-11-07).
 * Swing can paint the RuneLite window (sidebar, plugin panel, title bar), but the game area is drawn by the GPU and
 * comes out blank, so the game frame from the draw manager is laid over it at the canvas position.
 */
public final class WindowShot
{
    /** Larger windows fall back to the game screen only (keeps uploads under the size cap). */
    public static final int MAX_SIDE = 4096;

    private WindowShot()
    {
    }

    /**
     * Lays {@code game} over {@code window} at the canvas rectangle. Returns {@code game} unchanged when the window
     * capture is missing, too big or the canvas does not fit inside it.
     */
    public static BufferedImage compose(BufferedImage window, BufferedImage game, int canvasX, int canvasY, int canvasW, int canvasH)
    {
        if (window == null || game == null)
        {
            return game;
        }
        int w = window.getWidth();
        int h = window.getHeight();
        if (w <= 0 || h <= 0 || w > MAX_SIDE || h > MAX_SIDE || canvasW <= 0 || canvasH <= 0
            || canvasX < 0 || canvasY < 0 || canvasX + canvasW > w || canvasY + canvasH > h)
        {
            return game;
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try
        {
            g.drawImage(window, 0, 0, null);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(game, canvasX, canvasY, canvasW, canvasH, null);
        }
        finally
        {
            g.dispose();
        }
        return out;
    }
}
