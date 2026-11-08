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
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try
        {
            g.drawImage(src, 0, 0, null);
            int size = Math.max(14, Math.min(24, src.getHeight() / 40));
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size));
            FontMetrics fm = g.getFontMetrics();
            String kw = keyword == null ? "" : keyword.trim();
            String[] lines = kw.isEmpty() ? new String[] {timeText(when)} : new String[] {kw, timeText(when)};
            int pad = Math.max(4, size / 3);
            int w = 0;
            for (String l : lines) w = Math.max(w, fm.stringWidth(l));
            int lineH = fm.getHeight();
            int boxW = Math.min(src.getWidth(), w + pad * 2);
            int boxH = Math.min(src.getHeight(), lineH * lines.length + pad * 2);
            g.setColor(new Color(0, 0, 0, 190));
            g.fillRect(0, 0, boxW, boxH);
            g.setColor(Color.WHITE);
            int y = pad + fm.getAscent();
            for (String l : lines)
            {
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
