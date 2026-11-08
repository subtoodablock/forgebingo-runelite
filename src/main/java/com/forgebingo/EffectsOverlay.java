package com.forgebingo;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * In-game celebrations drawn over the game canvas, mirroring the website: a banner when a tile is finished, a sweep
 * when a line is finished, and the BLACKOUT show (screen goes dark, the word slams in, one of 17 colour / motion
 * styles picked at random) when every tile is done. One effect plays at a time; the rest wait in a queue.
 * Fireworks on the player are triggered by the plugin (spot animation 199, the level-up fireworks).
 */
public class EffectsOverlay extends Overlay
{
    enum Kind { TILE, LINE, BLACKOUT }

    static final class Effect
    {
        final Kind kind;
        final String title;
        final int style;
        long startedAt;

        Effect(Kind kind, String title, int style)
        {
            this.kind = kind;
            this.title = title;
            this.style = style;
        }
    }

    /** The 17 BLACKOUT styles (name, main colour, glow colour, tagline), matching the website's list. */
    static final Object[][] STYLES = {
        {"Forge", new Color(0xffbf1f), new Color(0xff7a00), "EVERY TILE. EVERY ONE."},
        {"Storm", new Color(0xe8f4ff), new Color(0x5ca8ff), "THE BOARD IS YOURS."},
        {"Night sky", Color.WHITE, new Color(0xb57cff), "NOTHING LEFT TO DO."},
        {"Gold rain", new Color(0xffd83d), new Color(0xff981f), "LOOT PILE COMPLETE."},
        {"Rune script", new Color(0x5cff5c), new Color(0x00aa55), "WRITTEN IN THE LOG."},
        {"Arena", new Color(0xff4d4d), new Color(0xaa0000), "FLAWLESS."},
        {"Tide", new Color(0x00ffff), new Color(0x0077aa), "SWEPT THE BOARD."},
        {"Terminal", new Color(0x7dff7d), new Color(0x00ff00), "BLACKOUT CONFIRMED"},
        {"Theatre", new Color(0xffd83d), new Color(0xaa0000), "TAKE A BOW."},
        {"Spotlight", new Color(0xfff6c8), new Color(0xffbf1f), "ALL EYES ON YOU."},
        {"Pulse", new Color(0xff5a5a), new Color(0xff0000), "STILL STANDING."},
        {"Gilded", new Color(0xffbf1f), new Color(0xffd83d), "A FULL SET."},
        {"Comet", new Color(0xd07dff), new Color(0x5ca8ff), "OUT OF THIS WORLD."},
        {"Wildfire", new Color(0xff981f), new Color(0xff2200), "BURNED THROUGH IT."},
        {"Frost", new Color(0xcfeeff), new Color(0x5ca8ff), "COLD AS ICE."},
        {"Runeforge", new Color(0xffd83d), new Color(0xb57cff), "FORGED IN FULL."},
        {"Victory lap", new Color(0x5cff5c), new Color(0xffd83d), "GG."},
    };

    private static final long TILE_MS = 3_200;
    private static final long LINE_MS = 2_600;
    private static final long BLACKOUT_MS = 5_600;
    private static final Color[] SPARK = {new Color(0xffd83d), new Color(0xff981f), new Color(0xff4d4d), new Color(0x5cff5c), new Color(0x5ca8ff), new Color(0xd07dff), Color.WHITE};

    private final Client client;
    private final Deque<Effect> queue = new ArrayDeque<>();
    private final Random random = new Random();
    private Effect current;
    private int lastStyle = -1;

    @Inject
    EffectsOverlay(Client client)
    {
        this.client = client;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(Overlay.PRIORITY_HIGH);
    }

    /** Thread-safe: effects are queued from the executor and drawn on the client thread. */
    synchronized void push(Kind kind, String title)
    {
        if (queue.size() >= 6) return;
        int style = 0;
        if (kind == Kind.BLACKOUT)
        {
            do { style = random.nextInt(STYLES.length); } while (style == lastStyle && STYLES.length > 1);
            lastStyle = style;
        }
        queue.add(new Effect(kind, title, style));
    }

    synchronized void clear()
    {
        queue.clear();
        current = null;
    }

    @Override
    public Dimension render(Graphics2D g)
    {
        Effect e;
        long now = System.currentTimeMillis();
        synchronized (this)
        {
            if (current != null && now - current.startedAt > durationOf(current.kind)) current = null;
            if (current == null)
            {
                current = queue.poll();
                if (current != null) current.startedAt = now;
            }
            e = current;
        }
        if (e == null) return null;
        int w = client.getCanvasWidth();
        int h = client.getCanvasHeight();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double t = (now - e.startedAt) / (double) durationOf(e.kind);
        switch (e.kind)
        {
            case TILE: drawTile(g, w, h, t, e.title); break;
            case LINE: drawLine(g, w, h, t, e.title); break;
            case BLACKOUT: drawBlackout(g, w, h, t, e.style, now - e.startedAt); break;
            default: break;
        }
        return null;
    }

    private static long durationOf(Kind k)
    {
        return k == Kind.TILE ? TILE_MS : k == Kind.LINE ? LINE_MS : BLACKOUT_MS;
    }

    /** 0..1 fade in over the first 12% and out over the last 18%. */
    private static float alpha(double t)
    {
        if (t < 0.12) return (float) (t / 0.12);
        if (t > 0.82) return (float) Math.max(0, (1 - t) / 0.18);
        return 1f;
    }

    private void drawTile(Graphics2D g, int w, int h, double t, String title)
    {
        float a = alpha(t);
        // a drop-in from above that settles, like the website's stamp
        double settle = Math.min(1, t / 0.15);
        int y = (int) (h * 0.22 - (1 - settle) * 60);
        banner(g, w, y, "TILE COMPLETE", title, new Color(0x5cff5c), new Color(0x1e6b22), a, 30);
        sparks(g, w / 2, y, t, 26);
    }

    private void drawLine(Graphics2D g, int w, int h, double t, String title)
    {
        float a = alpha(t);
        // the website sweeps the banner across; here it slides in from the left and out to the right
        double x = t < 0.2 ? -1 + t / 0.2 : t > 0.8 ? (t - 0.8) / 0.2 : 0;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.translate(x * w, 0);
        banner(g2, w, (int) (h * 0.3), title.toUpperCase(), "", new Color(0xffd83d), new Color(0x3a2a00), a, 34);
        g2.dispose();
    }

    private void drawBlackout(Graphics2D g, int w, int h, double t, int style, long ms)
    {
        Color main = (Color) STYLES[style][1];
        Color glow = (Color) STYLES[style][2];
        String sub = (String) STYLES[style][3];
        // the screen goes dark first, then comes back for the last part
        float dark = t < 0.1 ? (float) (t / 0.1) : t > 0.85 ? (float) Math.max(0, (1 - t) / 0.15) : 1f;
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, dark * 0.92f));
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, w, h);

        // a different motion per style family: slam (0,5,15), rise (6,13), flicker (1,14), zoom (2,12), type (4,7), pulse (10), drop (3), spin/other (rest)
        double in = Math.min(1, Math.max(0, (t - 0.08) / 0.3));
        double ease = 1 - Math.pow(1 - in, 3);
        int family = style % 6;
        float wordAlpha = (float) ease;
        double scale = 1;
        int dy = 0;
        if (family == 0) scale = 1 + (1 - ease) * 2.2;                       // slam
        else if (family == 1) wordAlpha = ((ms / 90) % 3 == 0 && in < 0.9) ? 0.25f : (float) ease; // flicker
        else if (family == 2) scale = 0.05 + ease * 0.95;                    // zoom
        else if (family == 3) dy = (int) ((1 - ease) * h * 0.6);             // drop
        else if (family == 4) dy = (int) ((1 - ease) * 60);                  // rise
        else scale = 1 + Math.sin(ms / 160.0) * 0.04;                        // pulse
        if (t > 0.85) wordAlpha *= dark;

        String word = family == 5 && in < 1 ? "BLACKOUT".substring(0, Math.max(1, (int) (in * 8))) : "BLACKOUT";
        int size = (int) Math.max(28, Math.min(w / 8, 110) * scale);
        Font f = new Font("Serif", Font.BOLD, size);
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(word);
        int x = (w - tw) / 2;
        int y = h / 2 + fm.getAscent() / 2 - 20 - dy;
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0, Math.min(1, wordAlpha))));
        // glow: the word drawn big and soft underneath
        g.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 90));
        for (int i = 6; i >= 2; i -= 2) g.drawString(word, x - i, y + i);
        g.setColor(new Color(0, 0, 0, 200));
        g.drawString(word, x + 4, y + 4);
        g.setColor(main);
        g.drawString(word, x, y);

        if (t > 0.35)
        {
            float sa = (float) Math.min(1, (t - 0.35) / 0.2) * (t > 0.85 ? dark : 1f);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, sa));
            g.setFont(new Font("Serif", Font.PLAIN, Math.max(14, size / 4)));
            FontMetrics sm = g.getFontMetrics();
            g.setColor(new Color(255, 255, 255, 210));
            g.drawString(sub, (w - sm.stringWidth(sub)) / 2, y + size / 2 + 10);
        }
        // sparks in the style's colours burst from the centre, like the website's particle backdrops
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, dark));
        sparks(g, w / 2, h / 2, t, 60, main, glow);
        // shockwave ring once the word has landed
        if (in >= 1 && t < 0.6)
        {
            double r = (t - 0.38) / 0.22;
            if (r > 0)
            {
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, 1 - r)));
                g.setColor(glow);
                g.setStroke(new BasicStroke(3f));
                int rad = (int) (r * Math.max(w, h) * 0.6);
                g.drawOval(w / 2 - rad, h / 2 - rad, rad * 2, rad * 2);
            }
        }
        g.setComposite(AlphaComposite.SrcOver);
    }

    private void banner(Graphics2D g, int w, int y, String head, String title, Color text, Color fill, float a, int size)
    {
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
        Font f = new Font("Serif", Font.BOLD, size);
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        Font tf = new Font("SansSerif", Font.PLAIN, Math.max(13, size / 2));
        FontMetrics tm = g.getFontMetrics(tf);
        int tw = Math.max(fm.stringWidth(head), title.isEmpty() ? 0 : tm.stringWidth(title)) + 48;
        int th = fm.getHeight() + (title.isEmpty() ? 0 : tm.getHeight()) + 20;
        int x = (w - tw) / 2;
        g.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 225));
        g.fillRoundRect(x, y, tw, th, 14, 14);
        g.setColor(text);
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(x, y, tw, th, 14, 14);
        g.setColor(new Color(0, 0, 0, 180));
        g.drawString(head, (w - fm.stringWidth(head)) / 2 + 2, y + 10 + fm.getAscent() + 2);
        g.setColor(text);
        g.drawString(head, (w - fm.stringWidth(head)) / 2, y + 10 + fm.getAscent());
        if (!title.isEmpty())
        {
            g.setFont(tf);
            g.setColor(Color.WHITE);
            g.drawString(title, (w - tm.stringWidth(title)) / 2, y + 10 + fm.getHeight() + tm.getAscent());
        }
        g.setComposite(AlphaComposite.SrcOver);
    }

    private void sparks(Graphics2D g, int cx, int cy, double t, int n, Color... palette)
    {
        Color[] cols = palette.length == 0 ? SPARK : palette;
        // deterministic per index so the sparks fly on steady paths frame to frame
        for (int i = 0; i < n; i++)
        {
            double ang = i * 2.399963; // golden angle spread
            double speed = 120 + (i * 37 % 160);
            double life = (t * durationScale(n)) % 1.0;
            double d = speed * life;
            int x = (int) (cx + Math.cos(ang) * d);
            int y = (int) (cy + Math.sin(ang) * d + 90 * life * life);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, 1 - life)));
            g.setColor(cols[i % cols.length]);
            g.fillRect(x, y, 4, 4);
        }
        g.setComposite(AlphaComposite.SrcOver);
    }

    private static double durationScale(int n)
    {
        return n > 40 ? 1.6 : 2.2;
    }
}
