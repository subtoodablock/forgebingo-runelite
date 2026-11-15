package com.forgebingo.core;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * Chexy's spells (owner, 2026-11-15), all our own designs, cast at set moments of a spell visit (half its visits; the rest power up instead, never both), all in its base form:
 * <ul>
 * <li>the FLOWER SPELL, in its base form: it glows softly, a gentle wave spreads out from under it and small pixel
 * flowers pop up and bloom one after another on the cards around it and near the bottom of the panel, sway, shed a
 * few petals, and fade;</li>
 * <li>the BARRIER, a pale rainbow dome of hexagon cells grows round it, a shimmer sweeps across it, stray
 * sparks strike it and it ripples and flashes where they hit, then it breaks into fading fragments;</li>
 * <li>the BEAM VOLLEY, once: it stops, a layered magic circle opens in front of it (an outer
 * ring of small rune strokes and an inner geometric ring turning the other way) and two small satellite circles
 * open beside it; particles are drawn in, and four beams fire one after another at different angles, the last one
 * sweeping, each with a hot core, soft glow, wavy energy edges, a brief afterimage and sparks where it ends, and
 * Chexy kicks back a little with every shot.</li>
 * </ul>
 * Everything is a pure function of the clocks. The flowers' and the beams' placing is the caller's (PanelImps).
 */
public final class ChexySpells
{
    private ChexySpells()
    {
    }

    // ------------------------------------------------------------------ when, by seconds into the visit

    /**
     * The spell clock for a visit: the visit's age on a spell visit, -1 (no spells) on a power-up visit. Each visit
     * is one or the other, never both (owner), rolled 50/50 when Chexy arrives. A spell visit stays in the base form.
     */
    public static float spellAge(boolean spellVisit, float visitAge)
    {
        return spellVisit ? visitAge : -1f;
    }

    /** the flower spell starts here (just after it has popped in) */
    public static final float FLOWER_AT = 1.4f;
    /** and a second, gentler bloom late in the visit (fewer flowers), done before the shortest visit ends */
    public static final float FLOWER_AGAIN = 44f;
    /** both blooms */
    public static final float[] FLOWER_TIMES = {FLOWER_AT, FLOWER_AGAIN};
    /** how long Chexy glows for it */
    public static final float FLOWER_CAST = 1.6f;
    /** how far into the cast the wave sets off and the flowers are placed */
    public static final float FLOWER_SPAWN = 0.35f;
    /** how long the flowers last once placed (they fade over the last FLOWER_FADE) */
    public static final float FLOWER_LIFE = 7.6f;
    public static final float FLOWER_FADE = 1.8f;
    /** the barrier, after the first flowers have faded */
    public static final float BARRIER_AT = 14f;
    public static final float BARRIER_TIME = 4.2f;
    /** the beam volley, half way through; then it floats calmly until the second bloom */
    public static final float[] VOLLEYS = {28f};
    public static final float VOLLEY_TIME = 3.8f;

    /** seconds into a flower spell's cast, or -1 */
    public static float flowerPhase(float age)
    {
        for (float at : FLOWER_TIMES)
        {
            float p = age - at;
            if (p >= 0 && p < FLOWER_CAST) return p;
        }
        return -1f;
    }

    /** seconds into the barrier, or -1 */
    public static float barrierPhase(float age)
    {
        float p = age - BARRIER_AT;
        return p >= 0 && p < BARRIER_TIME ? p : -1f;
    }

    /** seconds into a beam volley, or -1 */
    public static float volleyPhase(float age)
    {
        for (float v : VOLLEYS)
        {
            float p = age - v;
            if (p >= 0 && p < VOLLEY_TIME) return p;
        }
        return -1f;
    }

    /** how fast Chexy may fly while it casts: it stands still to fire, slows to cast and to shield */
    public static float flySpeed(float age)
    {
        if (volleyPhase(age) >= 0) return 0f;
        if (flowerPhase(age) >= 0) return 0.2f;
        if (barrierPhase(age) >= 0) return 0.35f;
        return 1f;
    }

    // ------------------------------------------------------------------ colours, strokes, scratch

    private static final int ALPHAS = 32;
    private static final double TAU = Math.PI * 2;
    /** per form (base, gold, blue): the spell colour (the base form: white-cyan beams; its dome and runes are rainbow), a paler one, by alpha step */
    private static final Color[][] TINT = new Color[3][ALPHAS];
    private static final Color[][] PALE = new Color[3][ALPHAS];
    private static final Color[] WHITE = new Color[ALPHAS];
    private static final Composite[] FADE = new Composite[ALPHAS];
    /** round-ended strokes by width in quarter pixels, up to 16 px (made once) */
    private static final Stroke[] STROKES = new Stroke[65];

    static
    {
        int[] tint = {0x8fe6ff, 0xffcf5a, 0x6fe3ff};
        int[] pale = {0xe8fbff, 0xfff4cf, 0xd8f8ff};
        for (int a = 0; a < ALPHAS; a++)
        {
            int alpha = Math.round(255f * (a + 1) / ALPHAS);
            WHITE[a] = new Color(255, 255, 255, alpha);
            FADE[a] = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (a + 1) / (float) ALPHAS);
            for (int f = 0; f < 3; f++)
            {
                TINT[f][a] = new Color(tint[f] | alpha << 24, true);
                PALE[f][a] = new Color(pale[f] | alpha << 24, true);
            }
        }
        for (int i = 0; i < STROKES.length; i++)
        {
            STROKES[i] = new BasicStroke(Math.max(0.25f, i / 4f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        }
    }

    private static int step(float a)
    {
        return Math.max(0, Math.min(ALPHAS - 1, Math.round(a * ALPHAS) - 1));
    }

    private static Stroke stroke(float w)
    {
        return STROKES[Math.max(1, Math.min(STROKES.length - 1, Math.round(w * 4)))];
    }

    private static float hash(int n)
    {
        n = (n << 13) ^ n;
        n = n * (n * n * 15731 + 789221) + 1376312589;
        return (n & 0x7fffffff) / (float) 0x7fffffff;
    }

    private static float smooth(float a, float b, float x)
    {
        float k = Math.max(0f, Math.min(1f, (x - a) / (b - a)));
        return k * k * (3 - 2 * k);
    }

    /** eases in with a small overshoot, for things that spring open */
    private static float spring(float k)
    {
        k = Math.max(0f, Math.min(1f, k));
        float c = 1.7f;
        float m = k - 1;
        return 1 + (c + 1) * m * m * m + c * m * m;
    }

    private static int formOf(int form)
    {
        return Math.max(0, Math.min(2, form));
    }

    /** a soft white light, bright in the middle (made once, drawn scaled) */
    private static final BufferedImage GLOW = glow(0xffffff);
    /** a soft pink-white light for the flower spell */
    private static final BufferedImage GLOW_PINK = glow(0xffd8ec);

    private static BufferedImage glow(int rgb)
    {
        int n = 40;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < n; y++)
        {
            for (int x = 0; x < n; x++)
            {
                double dx = (x + 0.5) / n * 2 - 1;
                double dy = (y + 0.5) / n * 2 - 1;
                double d = Math.min(1, Math.sqrt(dx * dx + dy * dy));
                int a = (int) Math.round(255 * Math.pow(1 - d, 1.9));
                img.setRGB(x, y, a << 24 | rgb);
            }
        }
        return img;
    }

    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private static final class Scratch
    {
        final Path2D.Float a = new Path2D.Float();
        final Path2D.Float b = new Path2D.Float();
        final Line2D.Float line = new Line2D.Float();
        final Ellipse2D.Float dot = new Ellipse2D.Float();
        final Rectangle2D.Float rect = new Rectangle2D.Float();
        final float[] p = new float[2];
        final float[] q = new float[2];
        /** the dome's strokes are drawn in its own unit space, so their widths depend on its size (kept per size) */
        float hexR = -1;
        Stroke hexThin, hexBold, hexRim;
        final Path2D.Float c = new Path2D.Float();

        void hexStrokes(float r)
        {
            float key = Math.round(r * 4) / 4f;
            if (key == hexR) return;
            hexR = key;
            hexThin = new BasicStroke(0.9f / Math.max(1f, key));
            hexBold = new BasicStroke(1.3f / Math.max(1f, key));
            hexRim = new BasicStroke(1.8f / Math.max(1f, key));
        }
    }

    private static void fillDot(Graphics2D g, Scratch k, float x, float y, float r)
    {
        k.dot.setFrame(x - r, y - r, r * 2, r * 2);
        g.fill(k.dot);
    }

    private static void light(Graphics2D g, BufferedImage img, float x, float y, float r, float alpha)
    {
        if (alpha <= 0.02f || r < 0.5f) return;
        Composite old = g.getComposite();
        Object interp = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setComposite(FADE[step(Math.min(1f, alpha))]);
        int ir = Math.round(r);
        g.drawImage(img, Math.round(x) - ir, Math.round(y) - ir, ir * 2, ir * 2, null);
        g.setComposite(old);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            interp == null ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : interp);
    }

    // ------------------------------------------------------------------ the flower spell

    /** Chexy's soft glow while it casts the flower spell (drawn behind its rings). */
    public static void paintCastGlow(Graphics2D g, float cx, float cy, float r, float phase)
    {
        float k = (float) Math.sin(Math.PI * Math.max(0f, Math.min(1f, phase / FLOWER_CAST)));
        light(g, GLOW_PINK, cx, cy, r * (1.5f + 0.25f * k), 0.55f * k);
    }

    /** how far the wave has spread (px) this long after it set off; below 0 once it is gone */
    public static float waveRadius(float since)
    {
        if (since < 0 || since > 1.5f) return -1f;
        float e = since / 1.5f;
        return 6f + 74f * (1f - (1f - e) * (1f - e));
    }

    /** the gentle wave that spreads out from under Chexy as it casts: two soft rings, green and pink, and a few glints */
    public static void paintWave(Graphics2D g, float x, float y, float since, boolean calm)
    {
        float rad = waveRadius(since);
        if (rad < 0) return;
        Scratch k = SCRATCH.get();
        float e = since / 1.5f;
        Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (int ring = 0; ring < 2; ring++)
        {
            float rr = rad * (ring == 0 ? 1f : 0.72f);
            float a = (1f - e) * (ring == 0 ? 0.55f : 0.4f) * (calm ? 0.7f : 1f);
            g.setStroke(stroke(ring == 0 ? 1.3f : 1f));
            g.setColor(ring == 0 ? PETAL[4][step(a)] : PETAL[1][step(a)]);
            k.dot.setFrame(x - rr, y - rr * 0.3f, rr * 2, rr * 0.6f);
            g.draw(k.dot);
        }
        int glints = calm ? 3 : 7;
        for (int i = 0; i < glints; i++)
        {
            double ang = i * TAU / glints + 0.4;
            float gx = x + (float) Math.cos(ang) * rad;
            float gy = y + (float) Math.sin(ang) * rad * 0.3f;
            g.setColor(WHITE[step((1f - e) * 0.8f)]);
            g.fillRect(Math.round(gx), Math.round(gy), 1, 1);
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa == null ? RenderingHints.VALUE_ANTIALIAS_DEFAULT : aa);
    }

    /** the flowers: 0 white daisy, 1 pink tulip, 2 yellow star, 3 light-blue bell */
    public static final int FLOWER_KINDS = 4;
    private static final String[][] HEADS = {
        {"..P..", ".PPP.", "PPCPP", ".PPP.", "..P.."},
        {"P.P.P", "PPPPP", "PPCPP", ".PDP."},
        {".P.P.", "PPCPP", ".PDP.", "P...P"},
        {".PPP.", "PPPPP", "PDCDP", "P.P.P"},
    };
    /** per kind: petal, centre, shade; then stem, leaf (rgb) */
    private static final int[][] FLOWER_RGB = {
        {0xf4f4ee, 0xffd250, 0xc8cad6},
        {0xff8cc0, 0xffdc78, 0xd8609c},
        {0xffe146, 0xff9628, 0xe0b028},
        {0x8cc8ff, 0xe6f5ff, 0x5a96e0},
    };
    private static final int STEM = 0x5ab45a, LEAF = 0x82d764;
    private static final int FLOWER_ALPHAS = 8;
    /** [kind][0 petal, 1 centre, 2 shade, 3 stem, 4 leaf][alpha level] */
    private static final Color[][][] FLOWER = new Color[FLOWER_KINDS][5][FLOWER_ALPHAS];
    /** petal colours for drifting petals and the wave, by alpha step: the four kinds, then soft green */
    private static final Color[][] PETAL = new Color[5][ALPHAS];

    static
    {
        for (int f = 0; f < FLOWER_KINDS; f++)
        {
            int[] rgb = {FLOWER_RGB[f][0], FLOWER_RGB[f][1], FLOWER_RGB[f][2], STEM, LEAF};
            for (int c = 0; c < 5; c++)
            {
                for (int a = 0; a < FLOWER_ALPHAS; a++)
                {
                    int alpha = Math.round(255f * (a + 1) / FLOWER_ALPHAS);
                    FLOWER[f][c][a] = new Color(rgb[c] | alpha << 24, true);
                }
            }
            for (int a = 0; a < ALPHAS; a++) PETAL[f][a] = new Color(FLOWER_RGB[f][0] | Math.round(255f * (a + 1) / ALPHAS) << 24, true);
        }
        for (int a = 0; a < ALPHAS; a++) PETAL[4][a] = new Color(0x9cf0a0 | Math.round(255f * (a + 1) / ALPHAS) << 24, true);
    }

    private static final float GROW = 0.32f, BUD = 0.5f, OPEN = 0.6f, FULL = 0.7f, SPARKLE = 0.95f;

    private static int stemHeight(int kind, int x)
    {
        return 5 + ((kind + x) & 1) + (kind == 1 ? 1 : 0);
    }

    private static int sway(float clock, int x)
    {
        return (int) Math.round(Math.sin(clock * 2.1 + x * 0.37) * 0.85);
    }

    private static int fadeLevel(float since, float life)
    {
        float a = since > life - FLOWER_FADE ? Math.max(0f, (life - since) / FLOWER_FADE) : 1f;
        return Math.max(-1, Math.round(a * FLOWER_ALPHAS) - 1);
    }

    /**
     * A number that changes whenever this flower would be drawn differently, so the caller repaints it only then
     * (it sways a pixel at a time, so most frames it does not change at all).
     */
    public static int flowerKey(int kind, int x, float since, float life, float clock)
    {
        if (since < 0) return -1;
        int stage = since < GROW ? Math.round(since / GROW * 8) : since < BUD ? 9 : since < OPEN ? 10 : since < SPARKLE
            ? 11 + Math.round((since - OPEN) / (SPARKLE - OPEN) * 4) : 16;
        int hop = since >= OPEN && since < OPEN + 0.12f ? 50000 : 0;
        return hop + stage * 1000 + (sway(clock, x) + 1) * 100 + fadeLevel(since, life) + 1;
    }

    /**
     * One pixel flower standing on groundY, centred on x: it grows its stem, shows a bud, pops open with a glint,
     * then sways a pixel to and fro, and fades at the end of its life.
     *
     * @param since seconds since this flower started (below 0: not yet)
     * @param life  how long it lasts in all
     * @param clock a running clock for the sway
     */
    public static void paintFlower(Graphics2D g, int x, int groundY, int kind, float since, float life, float clock)
    {
        if (since < 0 || since >= life) return;
        int lv = fadeLevel(since, life);
        if (lv < 0) return;
        Color[][] c = FLOWER[Math.floorMod(kind, FLOWER_KINDS)];
        int h = stemHeight(kind, x);
        int grown = since < GROW ? Math.max(1, Math.round(h * since / GROW)) : h;
        int sw = since < OPEN ? 0 : sway(clock, x);
        // the stem, growing up from the ground; its top bends with the sway
        g.setColor(c[3][lv]);
        for (int i = 0; i < grown; i++) g.fillRect(x + (i >= h - 2 ? sw : 0), groundY - 1 - i, 1, 1);
        if (grown >= 3)
        {
            g.setColor(c[4][lv]);
            int side = ((x + kind) & 1) == 0 ? -1 : 1;
            g.fillRect(x + side, groundY - 3, 1, 1);
            g.fillRect(x + side * 2, groundY - 4, 1, 1);
        }
        if (since < GROW) return;
        int top = groundY - 1 - h; // the row just above the stem
        if (since < BUD)
        {
            // a bud, swelling
            g.setColor(c[4][lv]);
            g.fillRect(x - 1, top, 1, 1);
            g.fillRect(x + 1, top, 1, 1);
            g.setColor(c[0][lv]);
            g.fillRect(x, top - (since > (GROW + BUD) / 2 ? 1 : 0), 1, 2);
            return;
        }
        String[] head = HEADS[Math.floorMod(kind, FLOWER_KINDS)];
        int rows = head.length;
        // popping open: first the middle rows, then the whole flower
        int from = since < OPEN ? rows / 2 - 1 : 0;
        int to = since < OPEN ? rows / 2 + 1 : rows;
        // and it hops up a pixel as it opens, then settles
        int hop = since >= OPEN && since < OPEN + 0.12f ? 1 : 0;
        for (int j = Math.max(0, from); j < Math.min(rows, to); j++)
        {
            String row = head[j];
            int py = top - (rows - 1 - j) - hop;
            for (int i = 0; i < 5; i++)
            {
                char ch = row.charAt(i);
                if (ch == '.') continue;
                g.setColor(c[ch == 'P' ? 0 : ch == 'C' ? 1 : 2][lv]);
                g.fillRect(x - 2 + i + sw, py, 1, 1);
            }
        }
        if (since >= OPEN && since < SPARKLE)
        {
            // a little burst as it opens: white glints on the diagonals, petal-coloured bits straight out
            float k = (since - OPEN) / (SPARKLE - OPEN);
            int d = 3 + Math.round(k * 2);
            int cyy = top - rows / 2;
            float fade = (1f - k) * (lv + 1) / FLOWER_ALPHAS;
            g.setColor(WHITE[step(0.9f * fade)]);
            g.fillRect(x + sw - d, cyy - d, 1, 1);
            g.fillRect(x + sw + d, cyy - d, 1, 1);
            g.fillRect(x + sw - d, cyy + d, 1, 1);
            g.fillRect(x + sw + d, cyy + d, 1, 1);
            g.setColor(PETAL[Math.floorMod(kind, FLOWER_KINDS)][step(0.9f * fade)]);
            g.fillRect(x + sw - d - 1, cyy, 1, 1);
            g.fillRect(x + sw + d + 1, cyy, 1, 1);
            g.fillRect(x + sw, cyy - d - 1, 1, 1);
        }
    }

    /** when this flower's drifting petals are in the air (so the caller keeps repainting around it) */
    public static boolean petalsFlying(float since, int index, boolean calm)
    {
        if (calm && index % 3 != 0) return false;
        for (int j = 0; j < (calm ? 1 : 2); j++)
        {
            float start = FULL + 0.6f + j * 1.7f + hash(index * 5 + j) * 0.8f;
            if (since >= start && since < start + PETAL_LIFE) return true;
        }
        return false;
    }

    private static final float PETAL_LIFE = 2.4f;

    /** A few petals that come loose and drift sideways and down, turning, fading. */
    public static void paintPetals(Graphics2D g, int x, int groundY, int kind, float since, int index, boolean calm)
    {
        if (calm && index % 3 != 0) return;
        int top = groundY - 1 - stemHeight(kind, x) - 2;
        for (int j = 0; j < (calm ? 1 : 2); j++)
        {
            float start = FULL + 0.6f + j * 1.7f + hash(index * 5 + j) * 0.8f;
            float tau = since - start;
            if (tau < 0 || tau >= PETAL_LIFE) continue;
            float side = hash(index * 5 + j + 2) < 0.5f ? -1f : 1f;
            float px = x + side * (4f + 5f * hash(index * 7 + j)) * tau + (float) Math.sin(tau * 3.4f + index) * 1.6f;
            float py = top - 2f * tau + 3.2f * tau * tau;
            float a = (1f - tau / PETAL_LIFE) * 0.95f;
            g.setColor(PETAL[Math.floorMod(kind, FLOWER_KINDS)][step(a)]);
            // it turns as it falls: flat, then edge on
            boolean flat = Math.sin(tau * 6f + j) > 0;
            g.fillRect(Math.round(px), Math.round(py), flat ? 2 : 1, 1);
        }
    }

    // ------------------------------------------------------------------ the barrier

    /** the dome's hexagon cells, on a unit sphere seen from the front (made once) */
    private static final Path2D.Float HEX = hexDome();
    /** a soft-filled bubble per form, brighter towards its rim (made once, drawn scaled) */
    private static final BufferedImage[] BUBBLE = {bubble(0), bubble(1), bubble(2)};
    private static final float[] HITS = {1.05f, 1.75f, 2.35f, 2.95f};
    private static final float[] HITS_CALM = {1.4f, 2.6f};

    private static Path2D.Float hexDome()
    {
        Path2D.Float path = new Path2D.Float();
        double h = 0.21;
        double cell = h * 0.86;
        for (int qq = -8; qq <= 8; qq++)
        {
            for (int rr = -8; rr <= 8; rr++)
            {
                double x = h * Math.sqrt(3) * (qq + rr / 2.0);
                double y = h * 1.5 * rr;
                if (Math.sqrt(x * x + y * y) > 1.18) continue;
                for (int v = 0; v <= 6; v++)
                {
                    double a = Math.PI / 6 + v * Math.PI / 3;
                    double px = x + Math.cos(a) * cell;
                    double py = y + Math.sin(a) * cell;
                    // bend the flat grid onto the dome: cells crowd together towards the rim
                    double d = Math.sqrt(px * px + py * py);
                    double m = d < 1e-6 ? 1 : Math.sin(Math.min(d, 1.25) / 1.25 * Math.PI / 2) / d;
                    if (v == 0) path.moveTo(px * m, py * m);
                    else path.lineTo(px * m, py * m);
                }
                path.closePath();
            }
        }
        return path;
    }

    /** the base form's pale rainbow, as paints across the unit dome at a few strengths (made once) */
    private static final int RAINBOW_LEVELS = 10;
    private static final java.awt.LinearGradientPaint[] RAINBOW = new java.awt.LinearGradientPaint[RAINBOW_LEVELS];
    /** pale rainbow colours by hue step and alpha step, for the base form's runes and fragments */
    private static final int RUNE_HUES = 10;
    private static final Color[][] RUNE = new Color[RUNE_HUES][ALPHAS];

    static
    {
        float[] stops = {0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f};
        float[] hues = {0.85f, 0.98f, 0.15f, 0.33f, 0.5f, 0.68f};
        for (int lv = 0; lv < RAINBOW_LEVELS; lv++)
        {
            int a = Math.round(255f * (lv + 1) / RAINBOW_LEVELS);
            Color[] c = new Color[hues.length];
            for (int i = 0; i < hues.length; i++) c[i] = new Color(Color.HSBtoRGB(hues[i], 0.42f, 1f) & 0xffffff | a << 24, true);
            RAINBOW[lv] = new java.awt.LinearGradientPaint(-1f, -1f, 1f, 1f, stops, c);
        }
        for (int h = 0; h < RUNE_HUES; h++)
        {
            int rgb = Color.HSBtoRGB(h / (float) RUNE_HUES, 0.45f, 1f) & 0xffffff;
            for (int a = 0; a < ALPHAS; a++) RUNE[h][a] = new Color(rgb | Math.round(255f * (a + 1) / ALPHAS) << 24, true);
        }
    }

    private static int rainbowLevel(float a)
    {
        return Math.max(0, Math.min(RAINBOW_LEVELS - 1, Math.round(a * RAINBOW_LEVELS) - 1));
    }

    private static BufferedImage bubble(int form)
    {
        int n = 48;
        int[] rgb = {0xe8fbff, 0xffd264, 0x7ee0ff};
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < n; y++)
        {
            for (int x = 0; x < n; x++)
            {
                double dx = (x + 0.5) / n * 2 - 1;
                double dy = (y + 0.5) / n * 2 - 1;
                // the base form's bubble is tinted round the wheel like its rings (yellow right, cyan below, ...)
                if (form == 0) rgb[0] = Color.HSBtoRGB((float) (0.16 + Math.atan2(dy, dx) / TAU), 0.32f, 1f) & 0xffffff;
                double d = Math.sqrt(dx * dx + dy * dy);
                double a = d >= 1 ? 0 : d < 0.93 ? 16 + 104 * Math.pow(d / 0.93, 4) : 120 * (1 - d) / 0.07;
                // a soft highlight up and to the left, as on a bubble
                double hx = dx + 0.38, hy = dy + 0.42;
                double hl = Math.max(0, 1 - Math.sqrt(hx * hx + hy * hy) / 0.3);
                a = Math.min(255, a + 70 * hl * hl);
                img.setRGB(x, y, (int) Math.round(a) << 24 | rgb[form]);
            }
        }
        return img;
    }

    /**
     * The barrier round Chexy (radius R, centred on cx, cy): a dome of hexagon cells that grows out from the middle,
     * a shimmer sweeping across, stray sparks striking it (each makes a ripple, lights the cells round the hit and
     * flashes), then it breaks up into hexagon fragments that drift away and fade.
     */
    public static void paintBarrier(Graphics2D g, float cx, float cy, float R, float p, float t, int form, boolean calm)
    {
        if (p < 0 || p >= BARRIER_TIME) return;
        int f = formOf(form);
        Scratch k = SCRATCH.get();
        AffineTransform old = g.getTransform();
        Shape oldClip = g.getClip();
        Stroke oldStroke = g.getStroke();
        Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        float dissolveAt = BARRIER_TIME - 0.8f;
        float grow = p / 0.6f;
        float alpha = grow < 1 ? smooth(0f, 1f, grow) : 1f;
        float d = p > dissolveAt ? (p - dissolveAt) / 0.8f : 0f;
        if (d > 0) alpha *= (1f - d) * (1f - d);
        float scale = grow < 1 ? spring(grow) : 1f + 0.015f * (float) Math.sin(t * 3f);
        float rr = R * scale;

        if (alpha > 0.02f)
        {
            // the soft body of the dome
            light(g, BUBBLE[f], cx, cy, rr, alpha);
            // the cells, revealed from the middle outward while it forms, turning slowly
            g.translate(cx, cy);
            if (grow < 1)
            {
                float rev = grow * 1.4f * rr;
                k.dot.setFrame(-rev, -rev, rev * 2, rev * 2);
                g.clip(k.dot);
            }
            g.rotate(t * 0.12);
            g.scale(rr, rr);
            k.hexStrokes(rr);
            g.setStroke(k.hexThin);
            if (f == 0)
            {
                // in its base form the cells and rim are a pale rainbow, like its rings
                java.awt.Paint oldPaint = g.getPaint();
                g.setPaint(RAINBOW[rainbowLevel(0.5f * alpha)]);
                g.draw(HEX);
                g.setStroke(k.hexRim);
                k.dot.setFrame(-1f, -1f, 2f, 2f);
                g.draw(k.dot);
                g.setPaint(oldPaint);
            }
            else
            {
                g.setColor(TINT[f][step(0.5f * alpha)]);
                g.draw(HEX);
            }
            g.setTransform(old);
            g.setClip(oldClip);
            // the shimmer: a bright band that sweeps across the cells
            float sweep = (float) (((p - 0.3f) / 1.5f) % 1.0);
            if (p > 0.3f)
            {
                g.translate(cx, cy);
                k.dot.setFrame(-rr, -rr, rr * 2, rr * 2);
                g.clip(k.dot);
                g.rotate(-0.7);
                float pos = (-1.3f + 2.6f * sweep) * rr;
                k.rect.setRect(pos - rr * 0.2f, -rr * 1.3f, rr * 0.4f, rr * 2.6f);
                g.clip(k.rect);
                g.rotate(0.7 + t * 0.12);
                g.scale(rr, rr);
                g.setStroke(k.hexBold);
                g.setColor(WHITE[step(0.7f * alpha)]);
                g.draw(HEX);
                g.setTransform(old);
                g.setClip(oldClip);
            }
            // the rim, which flares up for a moment each time something strikes the dome
            float struck = 0f;
            for (float h : calm ? HITS_CALM : HITS)
            {
                float q = p - h;
                if (q >= 0 && q < 0.3f) struck = Math.max(struck, 1f - q / 0.3f);
            }
            g.setStroke(stroke(1.6f + 1.4f * struck));
            g.setColor(TINT[f][step((0.35f + 0.45f * struck) * alpha)]);
            k.dot.setFrame(cx - rr, cy - rr, rr * 2, rr * 2);
            g.draw(k.dot);
            g.setStroke(stroke(0.7f));
            g.setColor(PALE[f][step(0.85f * alpha)]);
            g.draw(k.dot);
        }

        // stray sparks that strike it
        float[] hits = calm ? HITS_CALM : HITS;
        for (int i = 0; i < hits.length; i++)
        {
            float q = p - hits[i];
            if (q < -0.22f || q > 0.55f || p > dissolveAt) continue;
            double ang = hash(i * 7 + 3) * TAU;
            float ux = (float) Math.cos(ang);
            float uy = (float) Math.sin(ang);
            if (q < 0)
            {
                // incoming: a short streak flying in
                float head = rr * (1f + 1.2f * (-q / 0.22f));
                g.setStroke(stroke(1.1f));
                g.setColor(TINT[f][step(0.95f)]);
                k.line.setLine(cx + ux * head, cy + uy * head, cx + ux * (head + 6f), cy + uy * (head + 6f));
                g.draw(k.line);
                g.setColor(WHITE[step(0.9f)]);
                fillDot(g, k, cx + ux * head, cy + uy * head, 0.9f);
                continue;
            }
            float e = q / 0.55f;
            float hx = cx + ux * rr * 0.97f;
            float hy = cy + uy * rr * 0.97f;
            // the cells round the hit light up, and ripples run out from it across the dome
            g.translate(cx, cy);
            k.dot.setFrame(-rr, -rr, rr * 2, rr * 2);
            g.clip(k.dot);
            g.setTransform(old);
            float lit = rr * (0.35f + 0.4f * e);
            k.dot.setFrame(hx - lit, hy - lit, lit * 2, lit * 2);
            g.clip(k.dot);
            g.translate(cx, cy);
            g.rotate(t * 0.12);
            g.scale(rr, rr);
            k.hexStrokes(rr);
            g.setStroke(k.hexBold);
            g.setColor(PALE[f][step((1f - e) * 0.8f)]);
            g.draw(HEX);
            g.setTransform(old);
            g.setClip(oldClip);
            g.translate(cx, cy);
            k.dot.setFrame(-rr, -rr, rr * 2, rr * 2);
            g.clip(k.dot);
            g.setTransform(old);
            for (int ring = 0; ring < 2; ring++)
            {
                float rad = rr * (ring == 0 ? 0.12f + 0.75f * e : 0.06f + 0.45f * e);
                g.setStroke(stroke(ring == 0 ? 1.2f : 0.8f));
                g.setColor(WHITE[step((1f - e) * (ring == 0 ? 0.8f : 0.55f))]);
                k.dot.setFrame(hx - rad, hy - rad, rad * 2, rad * 2);
                g.draw(k.dot);
            }
            g.setClip(oldClip);
            if (!calm && q < 0.16f) light(g, GLOW, hx, hy, 7f, 1f - q / 0.16f);
            // sparks bouncing off
            g.setStroke(stroke(0.8f));
            for (int j = 0; j < (calm ? 2 : 4); j++)
            {
                double sa = ang + (hash(i * 11 + j) - 0.5) * 2.2;
                float len = (2.5f + 4f * hash(i * 13 + j)) * (1f - e);
                float s0 = 2f + 9f * e;
                float x0 = hx + (float) Math.cos(sa) * s0;
                float y0 = hy + (float) Math.sin(sa) * s0;
                g.setColor(TINT[f][step(1f - e)]);
                k.line.setLine(x0, y0, x0 + (float) Math.cos(sa) * len, y0 + (float) Math.sin(sa) * len);
                g.draw(k.line);
            }
        }

        // breaking up: hexagon fragments drift off, turning, and fade
        if (d > 0)
        {
            int n = calm ? 7 : 14;
            Path2D.Float frag = k.a;
            frag.reset();
            float time = d * 0.8f;
            for (int j = 0; j < n; j++)
            {
                double a0 = hash(j + 50) * TAU;
                float r0 = rr * (0.55f + 0.45f * hash(j + 60));
                float v = 20f + 18f * hash(j + 70);
                float fx = cx + (float) Math.cos(a0) * (r0 + v * time);
                float fy = cy + (float) Math.sin(a0) * (r0 + v * time) - 9f * time;
                float size = 2.6f * (1f - d * 0.55f);
                double rot = t * 3 + j;
                for (int vtx = 0; vtx <= 6; vtx++)
                {
                    double a = rot + vtx * Math.PI / 3;
                    float px = fx + (float) Math.cos(a) * size;
                    float py = fy + (float) Math.sin(a) * size;
                    if (vtx == 0) frag.moveTo(px, py);
                    else frag.lineTo(px, py);
                }
                frag.closePath();
                if (f == 0)
                {
                    // in its base form each fragment keeps the colour of where it broke off, round the wheel
                    int hue = Math.floorMod((int) Math.floor((0.16 + a0 / TAU) * RUNE_HUES), RUNE_HUES);
                    g.setColor(RUNE[hue][step(0.55f * (1f - d))]);
                    g.fill(frag);
                    g.setStroke(stroke(0.6f));
                    g.setColor(WHITE[step(0.8f * (1f - d))]);
                    g.draw(frag);
                    frag.reset();
                }
            }
            if (f != 0)
            {
                g.setColor(TINT[f][step(0.4f * (1f - d))]);
                g.fill(frag);
                g.setStroke(stroke(0.6f));
                g.setColor(PALE[f][step(0.85f * (1f - d))]);
                g.draw(frag);
            }
        }
        g.setStroke(oldStroke);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa == null ? RenderingHints.VALUE_ANTIALIAS_DEFAULT : aa);
    }

    // ------------------------------------------------------------------ the beam volley

    /** each shot: when it fires (s into the volley), how long it holds, its angle, which circle fires it */
    private static final float[] SHOT_FIRE = {1.0f, 1.42f, 1.84f, 2.3f};
    private static final float[] SHOT_HOLD = {0.42f, 0.42f, 0.42f, 0.85f};
    private static final float[] SHOT_ANGLE = {0f, -0.5f, 0.38f, 0f};
    private static final int[] SHOT_FROM = {0, 1, 2, 0};
    private static final float SHOT_FADE = 0.22f, AFTER = 0.3f;
    private static final float SWEEP = 0.34f;

    /** the small original rune strokes on the circles: each a list of segments (x1, y1, x2, y2) in a unit box */
    private static final float[][] GLYPHS = {
        {-1, -1, 0, 1, 0, 1, 1, -1, -0.6f, 0, 0.6f, 0},
        {0, -1, 0, 1, 0, 1, 0.8f, 0.4f},
        {-0.8f, -1, -0.8f, 0.6f, 0, -0.4f, 0, 1, 0.8f, -1, 0.8f, 0.6f},
        {-0.8f, -1, -0.8f, 1, -0.8f, 1, 0.8f, 1, 0.8f, 1, 0.8f, 0},
        {-1, -0.8f, 0, 0.8f, 0, 0.8f, 0.2f, -0.8f, 0.2f, -0.8f, 1, 0.8f},
        {-1, 0, 1, 0, 0, -1, 0, -0.3f, 0, 0.3f, 0, 1},
    };

    private static boolean shotOn(int shot, boolean calm)
    {
        return !calm || shot == 0 || shot == 3;
    }

    /** a shot's angle (screen radians) at this moment, for Chexy facing dir (1 right, -1 left) */
    private static double shotAngle(int shot, float p, int dir, boolean calm)
    {
        double o = SHOT_ANGLE[shot];
        if (shot == 3 && !calm)
        {
            float q = (p - SHOT_FIRE[3]) / SHOT_HOLD[3];
            o = -SWEEP + 2 * SWEEP * smooth(0f, 1f, q);
        }
        return dir > 0 ? o : Math.PI - o;
    }

    /** where the big circle points: at the first shot, then swinging round to start the sweep */
    private static double aim(float p, int dir, boolean calm)
    {
        double a0 = shotAngle(0, p, dir, calm);
        if (p < SHOT_FIRE[3] - 0.3f) return a0;
        double a3 = shotAngle(3, Math.max(p, SHOT_FIRE[3]), dir, calm);
        if (p >= SHOT_FIRE[3]) return a3;
        float k = smooth(SHOT_FIRE[3] - 0.3f, SHOT_FIRE[3], p);
        return a0 + (a3 - a0) * k;
    }

    /** how open each circle is (0 big, 1 upper satellite, 2 lower satellite) */
    private static float open(int which, float p, boolean calm)
    {
        if (which == 0) return smooth(0f, 0.4f, p) * (1f - smooth(VOLLEY_TIME - 0.5f, VOLLEY_TIME, p));
        if (calm) return 0f;
        float in = which == 1 ? 0.3f : 0.4f;
        float out = which == 1 ? 2.45f : 2.55f;
        return smooth(in, in + 0.3f, p) * (1f - smooth(out, out + 0.35f, p));
    }

    private static void emitter(int which, float cx, float cy, float p, int dir, boolean calm, float[] out)
    {
        if (which == 0)
        {
            double a = aim(p, dir, calm);
            out[0] = cx + (float) Math.cos(a) * 19f;
            out[1] = cy + (float) Math.sin(a) * 19f;
        }
        else
        {
            out[0] = cx + dir * 7f;
            out[1] = cy + (which == 1 ? -23f : 18f);
        }
    }

    /**
     * Chexy's kick back from the shots (and a little wobble), to add to where it is drawn: x into out[0], y into
     * out[1]. dir is the way it faces (1 right, -1 left).
     */
    public static void recoil(float p, int dir, boolean calm, float[] out)
    {
        out[0] = 0;
        out[1] = 0;
        if (p < 0) return;
        for (int s = 0; s < 4; s++)
        {
            if (!shotOn(s, calm)) continue;
            float q = p - SHOT_FIRE[s];
            if (q < 0 || q > 0.38f) continue;
            float k = 1f - q / 0.38f;
            float amp = (calm ? 1.2f : 3.2f) * k * k;
            double a = shotAngle(s, SHOT_FIRE[s], dir, calm);
            out[0] -= (float) Math.cos(a) * amp;
            out[1] -= (float) Math.sin(a) * amp;
            if (!calm) out[1] += (float) Math.sin(q * 45f) * 0.9f * k;
        }
    }

    /** how far a ray from (x, y) at angle a runs before it leaves the box (inset by 6 px), at most max */
    private static float reach(float x, float y, double a, Rectangle box, float max)
    {
        double dx = Math.cos(a), dy = Math.sin(a);
        double best = max;
        double l = box.x + 6, r = box.x + box.width - 6, t = box.y + 6, b = box.y + box.height - 6;
        if (dx > 1e-6) best = Math.min(best, (r - x) / dx);
        if (dx < -1e-6) best = Math.min(best, (l - x) / dx);
        if (dy > 1e-6) best = Math.min(best, (b - y) / dy);
        if (dy < -1e-6) best = Math.min(best, (t - y) / dy);
        return (float) Math.max(10, best);
    }

    /**
     * The beam volley, drawn in the panel's coordinates over everything: Chexy is centred on (cx, cy) facing dir,
     * p seconds into the volley, t a running clock, and the beams stop at the edges of box (what you can see).
     */
    public static void paintVolley(Graphics2D g, float cx, float cy, int dir, float p, float t, int form, boolean calm,
        Rectangle box)
    {
        if (p < 0 || p >= VOLLEY_TIME) return;
        int f = formOf(form);
        Scratch k = SCRATCH.get();
        Stroke oldStroke = g.getStroke();
        Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        dir = dir < 0 ? -1 : 1;

        // the circles, with their charge
        for (int which = 0; which < 3; which++)
        {
            float o = open(which, p, calm);
            if (o <= 0.01f) continue;
            emitter(which, cx, cy, p, dir, calm, k.p);
            double facing = which == 0 ? aim(p, dir, calm) : shotAngle(which, p, dir, calm);
            // the charge: brightest just before this circle's next shot
            float charge = 0f;
            for (int s = 0; s < 4; s++)
            {
                if (SHOT_FROM[s] != which || !shotOn(s, calm)) continue;
                charge = Math.max(charge, smooth(SHOT_FIRE[s] - (s == 0 ? 0.6f : 0.4f), SHOT_FIRE[s], p)
                    * (p < SHOT_FIRE[s] + 0.1f ? 1f : 0f));
                if (p >= SHOT_FIRE[s] && p < SHOT_FIRE[s] + SHOT_HOLD[s]) charge = Math.max(charge, 0.7f);
            }
            circle(g, k, k.p[0], k.p[1], which == 0 ? 14f : 7.5f, facing, t * (which == 0 ? 1.6f : -2.2f), o, f,
                which == 0, charge);
            if (charge > 0.05f && charge < 0.99f && p < SHOT_FIRE[3] + 0.05f)
            {
                // particles drawn in to the circle
                int n = calm ? 3 : 7;
                for (int j = 0; j < n; j++)
                {
                    float ph = (float) ((t * 1.9f + j / (float) n) % 1.0);
                    double ang = j * 2.39 + t * 0.6;
                    float rad = (which == 0 ? 30f : 16f) * (1f - ph);
                    g.setColor(PALE[f][step(ph * charge)]);
                    fillDot(g, k, k.p[0] + (float) Math.cos(ang) * rad, k.p[1] + (float) Math.sin(ang) * rad, 0.5f + 0.6f * ph);
                }
            }
        }

        // the beams
        for (int s = 0; s < 4; s++)
        {
            if (!shotOn(s, calm)) continue;
            float q = p - SHOT_FIRE[s];
            float hold = SHOT_HOLD[s];
            if (q < 0 || q > hold + SHOT_FADE + AFTER) continue;
            emitter(SHOT_FROM[s], cx, cy, p, dir, calm, k.p);
            double a = shotAngle(s, p, dir, calm);
            float ex = k.p[0], ey = k.p[1];
            float full = reach(ex, ey, a, box, 180f);
            float len = full * Math.min(1f, q / 0.07f);
            float x1 = ex + (float) Math.cos(a) * len;
            float y1 = ey + (float) Math.sin(a) * len;
            float on = q < hold ? 1f : Math.max(0f, 1f - (q - hold) / SHOT_FADE);
            // a brief afterimage left where it was
            if (q > hold)
            {
                float after = Math.max(0f, 1f - (q - hold) / (SHOT_FADE + AFTER));
                g.setStroke(stroke(7f));
                g.setColor(TINT[f][step(0.13f * after)]);
                k.line.setLine(ex, ey, x1, y1);
                g.draw(k.line);
            }
            if (on <= 0) continue;
            float punch = calm ? 1f : 1f + 0.6f * Math.max(0f, 1f - q / 0.16f);
            float w = (2.6f + 0.35f * (float) Math.sin(t * 45f + s)) * punch * on * (calm ? 0.8f : 1f);
            beam(g, k, ex, ey, x1, y1, len, w, on, f, t, s);
            if (!calm && q < 0.1f)
            {
                // the punch: the whole beam flashes white as it fires, and a bright spear tip leads it out
                float e = q / 0.1f;
                g.setStroke(stroke(w * 2.4f));
                g.setColor(WHITE[step(0.45f * (1f - e))]);
                k.line.setLine(ex, ey, x1, y1);
                g.draw(k.line);
                light(g, GLOW, x1, y1, 9f, 1f - e * 0.6f);
            }
            if (q > 0.06f)
            {
                impact(g, k, x1, y1, a, w, on, f, t, s, calm);
                // the moment it strikes: a ring of light bursts out where it lands
                float e = (q - 0.06f) / 0.3f;
                if (e < 1)
                {
                    float rad = 2f + 12f * (1f - (1f - e) * (1f - e));
                    g.setStroke(stroke(1.2f * (1f - e) + 0.4f));
                    g.setColor(PALE[f][step(0.9f * (1f - e))]);
                    k.dot.setFrame(x1 - rad, y1 - rad, rad * 2, rad * 2);
                    g.draw(k.dot);
                }
            }
            // the muzzle: a hot point where it leaves the circle, a flash and a shock ring as it fires
            g.setColor(WHITE[step(on)]);
            fillDot(g, k, ex, ey, 1.4f + w * 0.45f);
            if (q < 0.25f)
            {
                float e = q / 0.25f;
                float cf = (float) Math.cos(a), sf = (float) Math.sin(a);
                Path2D.Float ring = k.a;
                ring.reset();
                ellipse(ring, ex + cf * 4f * e, ey + sf * 4f * e, (SHOT_FROM[s] == 0 ? 14f : 7.5f) * (1.1f + 0.9f * e), cf, sf, 0.42f);
                g.setStroke(stroke(1.4f * (1f - e) + 0.4f));
                g.setColor(WHITE[step((calm ? 0.4f : 0.85f) * (1f - e))]);
                g.draw(ring);
            }
            if (!calm && q < 0.18f) light(g, GLOW, ex, ey, 16f, 1f - q / 0.18f);
        }
        g.setStroke(oldStroke);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa == null ? RenderingHints.VALUE_ANTIALIAS_DEFAULT : aa);
    }

    /** a magic circle seen at a slant, facing along its shot: outer rune ring and inner geometry turning opposite */
    private static void circle(Graphics2D g, Scratch k, float x, float y, float R, double facing, float spin,
        float open, int f, boolean rich, float charge)
    {
        float rr = R * (open < 1 ? Math.max(0.05f, spring(open)) : 1f);
        float a = Math.min(1f, open * 1.3f);
        float cf = (float) Math.cos(facing), sf = (float) Math.sin(facing);
        float sq = rich ? 0.42f : 0.5f;
        // its faint face, so it reads as a disc of light
        light(g, GLOW, x, y, rr * 1.15f, 0.22f * a + 0.3f * charge * a);
        // rings: outer pair and inner one
        Path2D.Float rings = k.a;
        rings.reset();
        float[] radii = rich ? RICH_RINGS : SMALL_RINGS;
        for (float rad : radii) ellipse(rings, x, y, rr * rad, cf, sf, sq);
        g.setStroke(stroke(2.2f));
        g.setColor(TINT[f][step(0.28f * a)]);
        g.draw(rings);
        g.setStroke(stroke(0.75f));
        g.setColor(PALE[f][step(0.9f * a)]);
        g.draw(rings);
        // the rune ring (or, on a small circle, ticks), turning one way
        Path2D.Float marks = k.b;
        marks.reset();
        int n = rich ? 10 : 6;
        for (int i = 0; i < n; i++)
        {
            double ang = spin + i * TAU / n;
            if (rich)
            {
                float[] gl = GLYPHS[i % GLYPHS.length];
                float tx = (float) -Math.sin(ang), ty = (float) Math.cos(ang);
                float rx = (float) Math.cos(ang), ry = (float) Math.sin(ang);
                float size = 0.085f;
                // in its base form each rune glows its own colour of the wheel; otherwise they are all white
                Path2D.Float into = f == 0 ? k.c : marks;
                if (f == 0) into.reset();
                for (int s = 0; s + 3 < gl.length; s += 4)
                {
                    float u1 = rx * 0.77f + (tx * gl[s] + rx * gl[s + 1]) * size;
                    float v1 = ry * 0.77f + (ty * gl[s] + ry * gl[s + 1]) * size;
                    float u2 = rx * 0.77f + (tx * gl[s + 2] + rx * gl[s + 3]) * size;
                    float v2 = ry * 0.77f + (ty * gl[s + 2] + ry * gl[s + 3]) * size;
                    map(k.p, x, y, rr, cf, sf, sq, u1, v1);
                    into.moveTo(k.p[0], k.p[1]);
                    map(k.p, x, y, rr, cf, sf, sq, u2, v2);
                    into.lineTo(k.p[0], k.p[1]);
                }
                if (f == 0)
                {
                    g.setStroke(stroke(0.7f));
                    g.setColor(RUNE[i % RUNE_HUES][step(0.95f * a)]);
                    g.draw(into);
                }
            }
            else
            {
                float u = (float) Math.cos(ang), v = (float) Math.sin(ang);
                Path2D.Float into = f == 0 ? k.c : marks;
                if (f == 0) into.reset();
                map(k.p, x, y, rr, cf, sf, sq, u * 0.66f, v * 0.66f);
                into.moveTo(k.p[0], k.p[1]);
                map(k.p, x, y, rr, cf, sf, sq, u * 0.92f, v * 0.92f);
                into.lineTo(k.p[0], k.p[1]);
                if (f == 0)
                {
                    g.setStroke(stroke(0.8f));
                    g.setColor(RUNE[(i * 5 / 3) % RUNE_HUES][step(0.95f * a)]);
                    g.draw(into);
                }
            }
        }
        // the inner geometry, turning the other way: two squares crossed into a star of eight points
        if (rich)
        {
            for (int sqr = 0; sqr < 2; sqr++)
            {
                for (int v = 0; v <= 4; v++)
                {
                    double ang = -spin * 1.5 + sqr * Math.PI / 4 + v * Math.PI / 2;
                    map(k.p, x, y, rr, cf, sf, sq, (float) Math.cos(ang) * 0.54f, (float) Math.sin(ang) * 0.54f);
                    if (v == 0) marks.moveTo(k.p[0], k.p[1]);
                    else marks.lineTo(k.p[0], k.p[1]);
                }
            }
            // and fine ticks round the outer pair, turning slowly
            for (int i = 0; i < 24; i++)
            {
                double ang = -spin * 0.4 + i * TAU / 24;
                float u = (float) Math.cos(ang), v = (float) Math.sin(ang);
                map(k.p, x, y, rr, cf, sf, sq, u * 0.92f, v * 0.92f);
                marks.moveTo(k.p[0], k.p[1]);
                map(k.p, x, y, rr, cf, sf, sq, u, v);
                marks.lineTo(k.p[0], k.p[1]);
            }
        }
        g.setStroke(stroke(0.6f));
        g.setColor(WHITE[step(0.85f * a)]);
        g.draw(marks);
        // the core, brightening as it charges
        g.setColor(TINT[f][step((0.35f + 0.5f * charge) * a)]);
        fillDot(g, k, x, y, (1.4f + 2.4f * charge) * (rr / R));
        g.setColor(WHITE[step((0.6f + 0.4f * charge) * a)]);
        fillDot(g, k, x, y, (0.7f + 1.3f * charge) * (rr / R));
    }

    private static final float[] RICH_RINGS = {1f, 0.92f, 0.62f, 0.3f};
    private static final float[] SMALL_RINGS = {1f, 0.6f};

    /** a point of a slanted circle: (u, v) on the unit disc, the disc squashed along its facing direction */
    private static void map(float[] out, float x, float y, float R, float cf, float sf, float sq, float u, float v)
    {
        out[0] = x + (u * sq * cf - v * sf) * R;
        out[1] = y + (u * sq * sf + v * cf) * R;
    }

    private static void ellipse(Path2D.Float path, float x, float y, float R, float cf, float sf, float sq)
    {
        for (int i = 0; i <= 28; i++)
        {
            double a = i * TAU / 28;
            float u = (float) Math.cos(a), v = (float) Math.sin(a);
            float px = x + (u * sq * cf - v * sf) * R;
            float py = y + (u * sq * sf + v * cf) * R;
            if (i == 0) path.moveTo(px, py);
            else path.lineTo(px, py);
        }
    }

    /** one beam: soft glow, coloured body, hot white core, wavy energy edges and bright ripples running out along it */
    private static void beam(Graphics2D g, Scratch k, float x0, float y0, float x1, float y1, float len, float w,
        float on, int f, float t, int seed)
    {
        k.line.setLine(x0, y0, x1, y1);
        g.setStroke(stroke(w * 3.6f));
        g.setColor(TINT[f][step(0.12f * on)]);
        g.draw(k.line);
        g.setStroke(stroke(w * 2.2f));
        g.setColor(TINT[f][step(0.25f * on)]);
        g.draw(k.line);
        g.setStroke(stroke(w * 1.25f));
        g.setColor(PALE[f][step(0.7f * on)]);
        g.draw(k.line);
        g.setStroke(stroke(Math.max(0.6f, w * 0.55f)));
        g.setColor(WHITE[step(on)]);
        g.draw(k.line);
        if (len < 8) return;
        float ux = (x1 - x0) / len, uy = (y1 - y0) / len;
        float nx = -uy, ny = ux;
        // two strands of energy weaving along its edges
        int pts = Math.max(4, Math.min(40, (int) (len / 5)));
        g.setStroke(stroke(0.6f));
        for (int strand = 0; strand < 2; strand++)
        {
            Path2D.Float path = k.a;
            path.reset();
            for (int i = 0; i <= pts; i++)
            {
                float s = len * i / pts;
                float wob = (float) Math.sin(s * 0.33f - t * 30f + strand * Math.PI + seed) * (w * 0.9f + 1f);
                float px = x0 + ux * s + nx * wob;
                float py = y0 + uy * s + ny * wob;
                if (i == 0) path.moveTo(px, py);
                else path.lineTo(px, py);
            }
            g.setColor(TINT[f][step(0.8f * on)]);
            g.draw(path);
        }
        // pulses of energy racing out along it: soft swellings of light
        for (int j = 0; j < 3; j++)
        {
            float s = (float) ((t * 260f + j * len / 3f + seed * 17) % len);
            float px = x0 + ux * s, py = y0 + uy * s;
            g.setColor(TINT[f][step(0.35f * on)]);
            fillDot(g, k, px, py, w * 1.3f + 0.6f);
            g.setColor(WHITE[step(0.75f * on)]);
            fillDot(g, k, px, py, w * 0.6f + 0.3f);
        }
    }

    /** where a beam ends: a flare of light, a glinting star and sparks spraying back off it */
    private static void impact(Graphics2D g, Scratch k, float x, float y, double a, float w, float on, int f, float t,
        int seed, boolean calm)
    {
        float pulse = 0.8f + 0.2f * (float) Math.sin(t * 38f + seed);
        light(g, GLOW, x, y, (5f + w * 1.6f) * pulse, 0.8f * on);
        g.setColor(TINT[f][step(0.5f * on)]);
        fillDot(g, k, x, y, 1.6f + w * 0.5f);
        g.setStroke(stroke(0.7f));
        g.setColor(WHITE[step(0.8f * on)]);
        float fl = (3f + 2f * pulse) * on;
        k.line.setLine(x - fl, y, x + fl, y);
        g.draw(k.line);
        k.line.setLine(x, y - fl, x, y + fl);
        g.draw(k.line);
        int slot = (int) Math.floor(t * 18f);
        int n = calm ? 2 : 5;
        for (int j = 0; j < n; j++)
        {
            int h = slot * 31 + j * 7 + seed * 101;
            double sa = a + Math.PI + (hash(h) - 0.5) * 2.6;
            float s0 = 1.5f + 3f * hash(h + 1);
            float len = 2.5f + 4.5f * hash(h + 2);
            float x0 = x + (float) Math.cos(sa) * s0, y0 = y + (float) Math.sin(sa) * s0;
            g.setColor(j % 2 == 0 ? WHITE[step(0.9f * on)] : TINT[f][step(0.9f * on)]);
            k.line.setLine(x0, y0, x0 + (float) Math.cos(sa) * len, y0 + (float) Math.sin(sa) * len);
            g.draw(k.line);
        }
    }

    /** the part of the panel a volley can touch, around Chexy at (cx, cy), inside box */
    public static Rectangle volleyArea(float cx, float cy, Rectangle box)
    {
        int top = (int) Math.max(box.y, cy - 120);
        int bottom = (int) Math.min(box.y + box.height, cy + 120);
        return new Rectangle(box.x, top, box.width, Math.max(1, bottom - top));
    }
}
