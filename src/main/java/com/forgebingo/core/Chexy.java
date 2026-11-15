package com.forgebingo.core;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

/**
 * Chexy (owner's reference, 2026-11-14): a glowing rainbow "atom" that drifts over the side panel as a rare
 * visitor. Four thin tilted rings turn and precess; bright comets ride them with long neon light trails that fade
 * back into the ring, and tiny glints twinkle along the rings and around it. Drawn with Java2D rather than a pixel
 * grid so the glow and the trails stay smooth. A point's colour comes from where it sits around the centre (yellow
 * on the right, green and cyan below, blue and violet on the left, magenta and red on top), so a comet sweeps
 * through the whole wheel on every lap.
 *
 * The comets are steered by the angle they sit at around the centre, spaced evenly round the wheel with only a
 * small sway, so they never bunch up; where that angle falls on each comet's ring is solved exactly. Everything is
 * a pure function of the clock: no state, and nothing allocated per frame.
 */
public final class Chexy
{
    private Chexy()
    {
    }

    /** how wide it is at normal size, in pixels (the rings; the glow reaches a little further) */
    public static final float SIZE = 38f;
    /** how fast the comets go round (rad/s): one lap every LAP seconds; the twinkles keep time with it */
    public static final float LAP = 2.52f;
    private static final float W = (float) (Math.PI * 2 / LAP);

    private static final int RINGS = 4;
    private static final int SEGS = 64;
    private static final int HUES = 64;
    private static final int ALPHAS = 32;
    private static final double TAU = Math.PI * 2;

    /** saturated glow colours and pale core colours, by hue bin and alpha step (made once) */
    private static final Color[][] GLOW = new Color[HUES][ALPHAS];
    private static final Color[][] CORE = new Color[HUES][ALPHAS];
    private static final Color[] WHITE = new Color[ALPHAS];

    static
    {
        for (int h = 0; h < HUES; h++)
        {
            int glow = Color.HSBtoRGB(h / (float) HUES, 0.9f, 1f);
            int core = Color.HSBtoRGB(h / (float) HUES, 0.45f, 1f);
            for (int a = 0; a < ALPHAS; a++)
            {
                int alpha = Math.round(255f * (a + 1) / ALPHAS);
                GLOW[h][a] = new Color((glow & 0xffffff) | alpha << 24, true);
                CORE[h][a] = new Color((core & 0xffffff) | alpha << 24, true);
            }
        }
        for (int a = 0; a < ALPHAS; a++) WHITE[a] = new Color(255, 255, 255, Math.round(255f * (a + 1) / ALPHAS));
    }

    /** the faint nebula behind it: deep blue and violet, lumpy, fading out to nothing (made once, drawn scaled) */
    private static final BufferedImage NEBULA = nebula();

    private static BufferedImage nebula()
    {
        int n = 48;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < n; y++)
        {
            for (int x = 0; x < n; x++)
            {
                double dx = (x + 0.5) / n * 2 - 1;
                double dy = (y + 0.5) / n * 2 - 1;
                double d = Math.min(1, Math.sqrt(dx * dx + dy * dy));
                double fall = Math.pow(1 - d, 2.2);
                double lump = 0.5 + 0.25 * Math.sin(dx * 5.1 + dy * 2.3) + 0.25 * Math.sin(dy * 6.3 - dx * 3.7 + 1.3);
                double mix = 0.5 + 0.5 * Math.sin(dx * 3.2 - dy * 2.1 + 0.6);
                int rr = (int) (28 + 70 * mix);
                int gg = (int) (30 + 8 * mix);
                int bb = (int) (150 + 40 * (1 - mix));
                int a = (int) Math.round(72 * fall * (0.4 + 0.6 * lump));
                img.setRGB(x, y, a << 24 | rr << 16 | gg << 8 | bb);
            }
        }
        return img;
    }

    // per ring: starting tilt, how fast the tilt turns (precession), size
    private static final float[] TILT = {0.25f, 1.05f, 1.85f, 2.65f};
    private static final float[] PRECESS = {0.15f, 0.12f, 0.17f, 0.13f};
    private static final float[] REACH = {1f, 0.93f, 0.97f, 0.88f};
    /** which ring each comet rides; the comets sit evenly round the wheel */
    private static final int[] COMET_RING = {0, 1, 2, 3, 1};
    private static final int COMETS = COMET_RING.length;
    private static final float SWAY = 0.3f; // < half the spacing, so neighbours can never meet
    private static final float TRAIL = 0.8f; // how much of a lap the light trail covers

    /** glints along the rings: ring, place on it, twinkle (times the lap speed), phase */
    private static final float[][] GLINTS = {
        {0, 0.9f, 2, 0.3f}, {1, 3.6f, 3, 2.1f}, {2, 5.2f, 2, 4.0f}, {3, 2.2f, 3, 5.2f}, {0, 4.4f, 1, 1.4f}, {2, 1.6f, 1, 3.3f},
    };
    /** ambient star specks: angle, distance (of the radius), twinkle (times the lap speed), phase */
    private static final float[][] SPECKS = {
        {0.3f, 1.2f, 1, 0.0f}, {1.4f, 1.3f, 2, 1.7f}, {2.2f, 1.25f, 1, 3.1f}, {3.0f, 1.35f, 2, 4.4f},
        {3.9f, 1.22f, 1, 2.2f}, {4.7f, 1.32f, 2, 5.3f}, {5.4f, 1.18f, 1, 0.9f}, {6.0f, 1.28f, 2, 3.8f},
    };

    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    /** reused per thread: ring points and brightness, and the strokes for the last size drawn */
    private static final class Scratch
    {
        final float[][] rx = new float[RINGS][SEGS + 1];
        final float[][] ry = new float[RINGS][SEGS + 1];
        final float[][] ra = new float[RINGS][SEGS];
        final float[][] rd = new float[RINGS][SEGS];
        final float[] ra2 = new float[RINGS];
        final float[] rb2 = new float[RINGS];
        final float[] rc = new float[RINGS];
        final float[] rs = new float[RINGS];
        final double[] headU = new double[COMETS];
        final float[] p = new float[2];
        final Path2D.Float path = new Path2D.Float();
        final Line2D.Float line = new Line2D.Float();
        final Ellipse2D.Float dot = new Ellipse2D.Float();
        float strokeScale = -1;
        Stroke wide, mid, core, hot, thin;

        void strokes(float s)
        {
            if (s == strokeScale) return;
            strokeScale = s;
            // the glow passes have butt ends so neighbouring translucent runs meet instead of overlapping
            wide = new BasicStroke(3.6f * s, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);
            mid = new BasicStroke(1.7f * s, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);
            core = new BasicStroke(Math.max(0.6f, 0.75f * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
            hot = new BasicStroke(Math.max(0.5f, 0.55f * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
            thin = new BasicStroke(Math.max(0.45f, 0.55f * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        }
    }

    /** hue bin for a point at (x, y) from the centre: yellow on the right, round through green, cyan, blue, violet, red */
    private static int hue(float x, float y, float shift)
    {
        double h = 0.16 + Math.atan2(y, x) / TAU + shift;
        h -= Math.floor(h);
        return Math.min(HUES - 1, (int) (h * HUES));
    }

    /** how round a ring looks right now: it nods as if tipping towards and away from you, never closing to a line */
    private static float squash(float t, int ring)
    {
        return 0.42f + 0.12f * (float) Math.sin(t * 0.47f + ring * 1.7f);
    }

    private static int step(float a)
    {
        return Math.max(0, Math.min(ALPHAS - 1, Math.round(a * ALPHAS) - 1));
    }

    /** the near half of a ring is brighter than the far half */
    private static float depth(double u)
    {
        return 0.6f + 0.4f * (0.5f + 0.5f * (float) Math.sin(u));
    }

    /** a point on a ring, by its place u along it: x into px[0], y into px[1] */
    private static void at(Scratch k, int ring, double u, float off, float[] px)
    {
        float ex = k.ra2[ring] * off * (float) Math.cos(u);
        float ey = k.rb2[ring] * off * (float) Math.sin(u);
        px[0] = ex * k.rc[ring] - ey * k.rs[ring];
        px[1] = ex * k.rs[ring] + ey * k.rc[ring];
    }

    /**
     * Draws the wisp centred on (cx, cy).
     *
     * @param t    its own clock in seconds (keep it running; the caller slows it for Calm)
     * @param size how wide the rings are, in pixels (SIZE is normal)
     * @param calm fewer sparkles
     */
    public static void paint(Graphics2D g, float cx, float cy, float t, float size, boolean calm)
    {
        paint(g, cx, cy, t, t, size, calm);
    }

    /**
     * As above, with the slow motions (the rings turning and nodding, the colour wheel creeping round) on a clock of
     * their own. The panel passes the same clock for both; a looping preview sways drift gently so it repeats.
     */
    public static void paint(Graphics2D g, float cx, float cy, float t, float drift, float size, boolean calm)
    {
        Scratch k = SCRATCH.get();
        float s = size / SIZE;
        k.strokes(s);
        Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        Object sc = g.getRenderingHint(RenderingHints.KEY_STROKE_CONTROL);
        Object interp = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        Stroke oldStroke = g.getStroke();
        Color oldColor = g.getColor();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // it breathes a little, and the whole colour wheel turns very slowly
        float r = size * 0.5f * (1f + 0.03f * (float) Math.sin(t * W));
        float shift = drift * 0.015f;
        float[] p = k.p;

        // the nebula behind it
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        int nr = Math.round(r * 1.3f);
        g.drawImage(NEBULA, Math.round(cx) - nr, Math.round(cy) - nr, nr * 2, nr * 2, null);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            interp == null ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : interp);

        // the rings: shape, then where each comet is
        for (int ring = 0; ring < RINGS; ring++)
        {
            double tilt = TILT[ring] + drift * PRECESS[ring];
            k.ra2[ring] = r * REACH[ring];
            k.rb2[ring] = k.ra2[ring] * squash(drift, ring);
            k.rc[ring] = (float) Math.cos(tilt);
            k.rs[ring] = (float) Math.sin(tilt);
            for (int i = 0; i <= SEGS; i++)
            {
                at(k, ring, TAU * i / SEGS, 1f, p);
                k.rx[ring][i] = p[0];
                k.ry[ring][i] = p[1];
            }
        }
        for (int c = 0; c < COMETS; c++)
        {
            int ring = COMET_RING[c];
            // the angle round the centre this comet sits at, then the place on its ring at that angle
            double theta = t * W + c * TAU / COMETS + SWAY * Math.sin(t * W + c * 2.1);
            double phi = theta - (TILT[ring] + drift * PRECESS[ring]);
            k.headU[c] = Math.atan2(k.ra2[ring] * Math.sin(phi), k.rb2[ring] * Math.cos(phi));
        }
        for (int ring = 0; ring < RINGS; ring++)
        {
            float[] al = k.ra[ring];
            float[] dp = k.rd[ring];
            for (int i = 0; i < SEGS; i++)
            {
                double u = TAU * (i + 0.5) / SEGS;
                float best = 0f;
                for (int c = 0; c < COMETS; c++)
                {
                    if (COMET_RING[c] != ring) continue;
                    double d = (k.headU[c] - u) / TAU;
                    d -= Math.floor(d);
                    if (d < TRAIL)
                    {
                        float f = 1f - (float) (d / TRAIL);
                        best = Math.max(best, (float) Math.pow(f, 1.7));
                    }
                }
                al[i] = best;
                dp[i] = depth(u);
            }
        }

        // four passes, wide and faint first, so the glow builds up like light does, then a hot white core
        for (int pass = 0; pass < 4; pass++)
        {
            g.setStroke(pass == 0 ? k.wide : pass == 1 ? k.mid : pass == 2 ? k.core : k.hot);
            int run = pass < 2 ? 4 : 2;
            for (int ring = 0; ring < RINGS; ring++)
            {
                float[] xs = k.rx[ring];
                float[] ys = k.ry[ring];
                float[] al = k.ra[ring];
                float[] dp = k.rd[ring];
                for (int i0 = 0; i0 < SEGS; i0 += run)
                {
                    float tr = 0f, dd = 0f;
                    for (int i = i0; i < i0 + run; i++)
                    {
                        tr += al[i];
                        dd += dp[i];
                    }
                    tr /= run;
                    dd /= run;
                    float alpha;
                    Color[][] table = GLOW;
                    if (pass == 0)
                    {
                        if (tr < 0.06f) continue;
                        alpha = 0.17f * tr * dd;
                    }
                    else if (pass == 1)
                    {
                        if (tr < 0.04f) continue;
                        alpha = 0.32f * tr * dd;
                    }
                    else if (pass == 2)
                    {
                        // the faint full ring is always there; the trail lights it up towards the comet
                        alpha = (0.22f + 0.78f * tr) * dd;
                        if (tr > 0.72f) table = CORE;
                    }
                    else
                    {
                        if (tr < 0.6f) continue;
                        alpha = Math.min(1f, (tr - 0.6f) * 2.2f) * dd;
                        table = null;
                    }
                    if (alpha < 0.03f) continue;
                    int mid = i0 + run / 2;
                    g.setColor(table == null ? WHITE[step(alpha)] : table[hue(xs[mid], ys[mid], shift)][step(alpha)]);
                    k.path.reset();
                    k.path.moveTo(cx + xs[i0], cy + ys[i0]);
                    for (int i = i0 + 1; i <= i0 + run; i++) k.path.lineTo(cx + xs[i], cy + ys[i]);
                    g.draw(k.path);
                }
            }
        }

        // glints that flash along the rings now and then
        g.setStroke(k.thin);
        int glints = calm ? 2 : GLINTS.length;
        for (int i = 0; i < glints; i++)
        {
            float[] gl = GLINTS[i];
            float tw = (float) Math.sin(t * W * gl[2] + gl[3]);
            if (tw <= 0.3f) continue;
            tw = (float) Math.pow(tw, 6);
            at(k, (int) gl[0], gl[1], 1f, p);
            star(g, k, cx + p[0], cy + p[1], tw, s, hue(p[0], p[1], shift));
        }

        // the comet heads, with a few specks shed behind them
        g.setStroke(k.thin);
        for (int c = 0; c < COMETS; c++)
        {
            int ring = COMET_RING[c];
            double u = k.headU[c];
            float depth = depth(u);
            at(k, ring, u, 1f, p);
            float hx = p[0];
            float hy = p[1];
            int hh = hue(hx, hy, shift);
            g.setColor(GLOW[hh][step(0.18f * depth)]);
            fillDot(g, k, cx + hx, cy + hy, 3.0f * s);
            g.setColor(GLOW[hh][step(0.5f * depth)]);
            fillDot(g, k, cx + hx, cy + hy, 1.7f * s);
            g.setColor(WHITE[step(depth)]);
            fillDot(g, k, cx + hx, cy + hy, 0.95f * s);
            // a four-point flare that glints on the near side
            if (depth > 0.72f)
            {
                float tw = 0.5f + 0.5f * (float) Math.sin(t * W * 3 + c * 2.4f);
                float fl = (1.4f + 2.8f * tw * tw) * s * depth;
                g.setColor(WHITE[step(0.5f * depth * (0.4f + 0.6f * tw))]);
                k.line.setLine(cx + hx - fl, cy + hy, cx + hx + fl, cy + hy);
                g.draw(k.line);
                k.line.setLine(cx + hx, cy + hy - fl * 0.8f, cx + hx, cy + hy + fl * 0.8f);
                g.draw(k.line);
            }
            int shed = calm ? 1 : 3;
            for (int j = 1; j <= shed; j++)
            {
                // each speck drifts outward a little as it falls behind
                float off = 1f + (0.04f + 0.03f * j) * (float) Math.sin(j * 2.3 + c * 1.9 + t * W);
                at(k, ring, u - 0.3 * j, off, p);
                float sa = (0.7f - 0.17f * j) * (0.55f + 0.45f * (float) Math.sin(t * W * 4 + j * 1.7f + c));
                g.setColor(CORE[hue(p[0], p[1], shift)][step(sa)]);
                fillDot(g, k, cx + p[0], cy + p[1], 0.6f * s);
            }
        }

        // tiny stars twinkling around it
        int specks = calm ? 3 : SPECKS.length;
        for (int i = 0; i < specks; i++)
        {
            float[] sp = SPECKS[calm ? i * 2 + 1 : i];
            float tw = (float) Math.sin(t * W * sp[2] + sp[3]);
            if (tw <= 0.2f) continue;
            tw = tw * tw * tw;
            double ang = sp[0] + drift * 0.08f;
            float px = (float) Math.cos(ang) * r * sp[1];
            float py = (float) Math.sin(ang) * r * sp[1] * 0.85f;
            star(g, k, cx + px, cy + py, tw * 0.7f, s * 0.8f, hue(px, py, shift));
        }

        g.setStroke(oldStroke);
        g.setColor(oldColor);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa == null ? RenderingHints.VALUE_ANTIALIAS_DEFAULT : aa);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, sc == null ? RenderingHints.VALUE_STROKE_DEFAULT : sc);
    }

    /** its float, a slow figure of eight in time with the comets: x offset in pixels at time t */
    public static float bobX(float t)
    {
        return 2.2f * (float) Math.sin(t * W);
    }

    /** its float: y offset in pixels at time t */
    public static float bobY(float t)
    {
        return 1.8f * (float) Math.sin(t * W * 2 + 0.6f);
    }

    /** a small four-point glint: faint white rays and a bright tinted centre */
    private static void star(Graphics2D g, Scratch k, float x, float y, float tw, float s, int hue)
    {
        float len = (0.8f + 2.2f * tw) * s;
        g.setColor(CORE[hue][step(0.55f * tw)]);
        k.line.setLine(x - len, y, x + len, y);
        g.draw(k.line);
        k.line.setLine(x, y - len, x, y + len);
        g.draw(k.line);
        g.setColor(CORE[hue][step(0.95f * tw)]);
        fillDot(g, k, x, y, 0.6f * s);
    }

    private static void fillDot(Graphics2D g, Scratch k, float x, float y, float rad)
    {
        k.dot.setFrame(x - rad, y - rad, rad * 2, rad * 2);
        g.fill(k.dot);
    }

    private static BufferedImage still;

    /** One still frame on the shared creature grid (PixelArt.WIDTH x HEIGHT), for anything that wants a picture. */
    public static synchronized BufferedImage still()
    {
        if (still == null)
        {
            BufferedImage img = new BufferedImage(PixelArt.WIDTH, PixelArt.HEIGHT, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            paint(g, PixelArt.WIDTH / 2f, PixelArt.HEIGHT / 2f, 1.3f, Math.min(PixelArt.HEIGHT - 4, SIZE), false);
            g.dispose();
            still = img;
        }
        return still;
    }
}
