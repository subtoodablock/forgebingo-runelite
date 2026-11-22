package com.forgebingo.core;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/** Stamps the event keyword and the local date/time in the top-left corner of a proof screenshot copy. */
public final class ScreenshotStamp
{
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter OFFSET = DateTimeFormatter.ofPattern("xxx");
    /** the stamp box's darkness, 0-255: about 40%, so the game shows through */
    static final int BOX_ALPHA = 100;

    private ScreenshotStamp()
    {
    }

    /** "yyyy-MM-dd HH:mm +02:00" */
    public static String timeText(ZonedDateTime t)
    {
        return TIME.format(t) + " UTC" + OFFSET.format(t);
    }

    /** Returns a new image (same size) with the stamp drawn; the source is not modified. */
    public static BufferedImage stamp(BufferedImage src, String keyword, ZonedDateTime when)
    {
        return stamp(src, keyword, when, null, null);
    }

    /**
     * Proof stamp (owner, 2026-11-12): the tile it is for, the player, "Code: keyword" and the date/time, top-left.
     * Any of tile / player / keyword may be null or blank and is then left out.
     */
    public static BufferedImage stamp(BufferedImage src, String keyword, ZonedDateTime when, String player, String tile)
    {
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (tile != null && !tile.trim().isEmpty()) lines.add(clip(tile.trim(), 64));
        if (player != null && !player.trim().isEmpty()) lines.add(clip(player.trim(), 24));
        if (keyword != null && !keyword.trim().isEmpty()) lines.add("Code: " + clip(keyword.trim(), 40));
        lines.add(timeText(when));
        return draw(src, lines.toArray(new String[0]));
    }

    private static String clip(String s, int max)
    {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static BufferedImage draw(BufferedImage src, String[] lines)
    {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try
        {
            g.drawImage(src, 0, 0, null);
            int size = Math.max(14, Math.min(24, src.getHeight() / 40));
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size));
            FontMetrics fm = g.getFontMetrics();
            int pad = Math.max(4, size / 3);
            int w = 0;
            for (String l : lines) w = Math.max(w, fm.stringWidth(l));
            int lineH = fm.getHeight();
            int boxW = Math.min(src.getWidth(), w + pad * 2);
            int boxH = Math.min(src.getHeight(), lineH * lines.length + pad * 2);
            // light enough to see the game behind it (owner, 2026-11-22: the old 75% black box hid what was under it);
            // the text keeps a dark outline so it still reads on bright scenery
            g.setColor(new Color(0, 0, 0, BOX_ALPHA));
            g.fillRect(0, 0, boxW, boxH);
            int y = pad + fm.getAscent();
            Color outline = new Color(0, 0, 0, 220);
            for (String l : lines)
            {
                g.setColor(outline);
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) if (dx != 0 || dy != 0) g.drawString(l, pad + dx, y + dy);
                g.setColor(Color.WHITE);
                g.drawString(l, pad, y);
                y += lineH;
            }
        }
        finally
        {
            g.dispose();
        }
        return out;
    }
}
