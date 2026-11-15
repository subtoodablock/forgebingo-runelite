package com.forgebingo.core;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
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
 * small sway, so they never bunch up; where that angle falls on each comet's ring is solved exactly.
 *
 * The power-up (owner, 2026-11-14): on every visit Chexy powers up, anime style. A few seconds as itself, then it
 * charges (it trembles, its rings spin up, sparks crackle and the glow pulses), flashes, and comes back in its GOLD
 * FORM, with gold rings and a tall flickering flame aura, lightning and rising specks. Later it charges again and
 * flashes into its BLUE FORM: cyan and electric blue, with a calmer, steadier aura and a blue shimmer. It stays blue
 * until the visit ends and it pops out. Everything is a pure function of the clocks: no state, and nothing big is
 * allocated per frame.
 */
public final class Chexy
{
    private Chexy()
    {
    }

    /** how wide it is at normal size, in pixels (the rings; the glow, and in a form the aura, reach further) */
    public static final float SIZE = 38f;
    /** how fast the comets go round (rad/s): one lap every LAP seconds; the twinkles keep time with it */
    public static final float LAP = 2.52f;
    private static final float W = (float) (Math.PI * 2 / LAP);

    // ------------------------------------------------------------------ the power-up, by seconds into the visit
    /** the forms */
    public static final int BASE = 0, GOLD = 1, BLUE = 2;
    /** seconds as itself before the first charge */
    public static final float CHARGE_AT = 6f;
    /** how long a charge-up lasts */
    public static final float CHARGE_TIME = 1.8f;
    /** the flash into the gold form */
    public static final float GOLD_AT = CHARGE_AT + CHARGE_TIME;
    /** the second charge (the gold form holds 26 s) */
    public static final float CHARGE2_AT = GOLD_AT + 26f;
    /** the flash into the blue form, which then holds until the visit ends (a visit lasts 60-90 s) */
    public static final float BLUE_AT = CHARGE2_AT + CHARGE_TIME;

    /** which form it is in, this many seconds into its visit (below 0: no visit, always the base form) */
    public static int form(float age)
    {
        return age < GOLD_AT ? BASE : age < BLUE_AT ? GOLD : BLUE;
    }

    /** how far into a charge-up it is, 0 (none) to 1 (about to flash) */
    public static float charge(float age)
    {
        if (age >= CHARGE_AT && age < GOLD_AT) return (age - CHARGE_AT) / CHARGE_TIME;
        if (age >= CHARGE2_AT && age < BLUE_AT) return (age - CHARGE2_AT) / CHARGE_TIME;
        return 0f;
    }

    /** the white flash at a change of form, 0 to 1 */
    static float flash(float age)
    {
        float d = Math.abs(age - GOLD_AT) < Math.abs(age - BLUE_AT) ? age - GOLD_AT : age - BLUE_AT;
        if (d < -0.12f || d > 0.55f) return 0f;
        if (d < 0) return 1f + d / 0.12f;
        float k = 1f - d / 0.55f;
        return k * k;
    }

    /** seconds since the last change of form (for the shock ring and the aura flaring up) */
    private static float sinceChange(float age)
    {
        return age >= BLUE_AT ? age - BLUE_AT : age >= GOLD_AT ? age - GOLD_AT : 99f;
    }

    /**
     * How fast its own clock should run, this far into the visit: the rings spin up while it charges and stay a
     * little quicker in its forms. The caller advances Chexy's clock by dt times this.
     */
    public static float spin(float age)
    {
        float c = charge(age);
        if (c > 0) return (form(age) == GOLD ? 1.45f : 1f) + 1.7f * c * c;
        int f = form(age);
        return f == GOLD ? 1.45f : f == BLUE ? 1.25f : 1f;
    }

    private static final int RINGS = 4;
    private static final int SEGS = 64;
    private static final int HUES = 64;
    private static final int ALPHAS = 32;
    private static final double TAU = Math.PI * 2;

    /** per form: saturated glow colours and pale core colours, by hue bin and alpha step (made once) */
    private static final Color[][][] GLOW = new Color[3][HUES][ALPHAS];
    private static final Color[][][] CORE = new Color[3][HUES][ALPHAS];
    private static final Color[] WHITE = new Color[ALPHAS];
    /** the aura's three layers per form (outer, middle, inner), then the lightning glow, by alpha step */
    private static final Color[][][] AURA = new Color[3][4][ALPHAS];
    private static final Composite[] FADE = new Composite[ALPHAS];

    static
    {
        for (int h = 0; h < HUES; h++)
        {
            double wave = Math.sin(h / (double) HUES * TAU * 2);
            int[] glow = {
                Color.HSBtoRGB(h / (float) HUES, 0.9f, 1f),
                // gold: amber to yellow, rippling round the wheel so the rings still shimmer
                Color.HSBtoRGB((float) (0.118 + 0.028 * wave), 0.86f, 1f),
                // blue: cyan to electric blue
                Color.HSBtoRGB((float) (0.545 + 0.045 * wave), 0.82f, 1f),
            };
            int[] core = {
                Color.HSBtoRGB(h / (float) HUES, 0.45f, 1f),
                Color.HSBtoRGB(0.14f, 0.3f, 1f),
                Color.HSBtoRGB(0.52f, 0.22f, 1f),
            };
            for (int f = 0; f < 3; f++)
            {
                for (int a = 0; a < ALPHAS; a++)
                {
                    int alpha = Math.round(255f * (a + 1) / ALPHAS);
                    GLOW[f][h][a] = new Color((glow[f] & 0xffffff) | alpha << 24, true);
                    CORE[f][h][a] = new Color((core[f] & 0xffffff) | alpha << 24, true);
                }
            }
        }
        int[][] aura = {
            {0xffffff, 0xffffff, 0xffffff, 0xffffff},
            {0xff8a1a, 0xffc93a, 0xfff3b8, 0xffe27a},
            {0x2f6dff, 0x39cfff, 0xd9f7ff, 0x8fe8ff},
        };
        for (int a = 0; a < ALPHAS; a++)
        {
            int alpha = Math.round(255f * (a + 1) / ALPHAS);
            WHITE[a] = new Color(255, 255, 255, alpha);
            FADE[a] = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (a + 1) / (float) ALPHAS);
            for (int f = 0; f < 3; f++)
            {
                for (int l = 0; l < 4; l++) AURA[f][l][a] = new Color(aura[f][l] | alpha << 24, true);
            }
        }
    }

    /** the glow behind it, per form: a faint lumpy blue-violet nebula, a warm gold blaze, a cold blue one */
    private static final BufferedImage[] HAZE = {haze(0), haze(1), haze(2)};
    /** the flash at a change of form: white light, bright in the middle and fading softly out */
    private static final BufferedImage BLOOM = bloom();

    private static BufferedImage bloom()
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
                int a = (int) Math.round(255 * Math.pow(1 - d, 1.8));
                img.setRGB(x, y, a << 24 | 0xfffdf4);
            }
        }
        return img;
    }

    private static BufferedImage haze(int form)
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
                int rr, gg, bb, a;
                if (form == BASE)
                {
                    rr = (int) (28 + 70 * mix);
                    gg = (int) (30 + 8 * mix);
                    bb = (int) (150 + 40 * (1 - mix));
                    a = (int) Math.round(72 * fall * (0.4 + 0.6 * lump));
                }
                else if (form == GOLD)
                {
                    rr = 255;
                    gg = (int) (195 + 50 * (1 - d));
                    bb = (int) (80 + 110 * (1 - d) * (1 - d));
                    a = (int) Math.round(72 * fall * (0.75 + 0.25 * lump));
                }
                else
                {
                    rr = (int) (60 + 120 * (1 - d) * (1 - d));
                    gg = (int) (170 + 60 * (1 - d));
                    bb = 255;
                    a = (int) Math.round(70 * fall * (0.75 + 0.25 * lump));
                }
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
        Stroke wide, mid, core, hot, thin, bolt;

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
            bolt = new BasicStroke(Math.max(1f, 2.2f * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
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

    /** a steady pseudo-random number in 0..1 for an integer (sparks and lightning pick their spots with it) */
    private static float hash(int n)
    {
        n = (n << 13) ^ n;
        n = n * (n * n * 15731 + 789221) + 1376312589;
        return (n & 0x7fffffff) / (float) 0x7fffffff;
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
     * Draws it centred on (cx, cy), in its base form.
     *
     * @param t    its own clock in seconds (keep it running; the caller slows it for Calm)
     * @param size how wide the rings are, in pixels (SIZE is normal)
     * @param calm fewer sparkles
     */
    public static void paint(Graphics2D g, float cx, float cy, float t, float size, boolean calm)
    {
        paint(g, cx, cy, t, t, size, calm, -1f);
    }

    /**
     * As above, with the slow motions (the rings turning and nodding, the colour wheel creeping round) on a clock of
     * their own, and {@code age} the seconds since its visit began, which picks the power-up's stage (below 0: base
     * form). The panel passes the same clock for t and drift; a looping preview sways drift so it repeats.
     */
    public static void paint(Graphics2D g, float cx, float cy, float t, float drift, float size, boolean calm, float age)
    {
        Scratch k = SCRATCH.get();
        float s = size / SIZE;
        k.strokes(s);
        Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        Object sc = g.getRenderingHint(RenderingHints.KEY_STROKE_CONTROL);
        Object interp = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        Stroke oldStroke = g.getStroke();
        Color oldColor = g.getColor();
        Composite oldComp = g.getComposite();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        int form = age < 0 ? BASE : form(age);
        float charge = age < 0 ? 0f : charge(age);
        float flash = age < 0 ? 0f : flash(age);
        float since = age < 0 ? 99f : sinceChange(age);
        Color[][] glow = GLOW[form];
        Color[][] coreCol = CORE[form];

        // it trembles while it charges (harder towards the flash), and hums a little in its gold form
        if (!calm)
        {
            float shake = charge > 0 ? 1.6f * charge * charge + 0.25f : form == GOLD ? 0.3f : 0f;
            if (shake > 0)
            {
                cx += shake * s * (float) (Math.sin(t * 61.3) * 0.6 + Math.sin(t * 97.1 + 1.3) * 0.4);
                cy += shake * s * (float) (Math.sin(t * 73.7 + 0.4) * 0.6 + Math.sin(t * 89.9 + 2.2) * 0.4);
            }
        }

        // it breathes a little (faster as it charges), and the whole colour wheel turns very slowly
        float pulse = charge > 0 ? charge * (0.55f + 0.45f * (float) Math.sin(t * (14f + 16f * charge))) : 0f;
        float r = size * 0.5f * (1f + 0.03f * (float) Math.sin(t * W) + 0.05f * pulse);
        float shift = drift * 0.015f;
        float bright = form == BASE ? 1f : 1.12f;
        bright *= 1f + 0.5f * pulse;
        float[] p = k.p;

        // the glow behind it, brighter while it charges
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        int nr = Math.round(r * (form == BASE ? 1.3f : 1.6f));
        g.drawImage(HAZE[form], Math.round(cx) - nr, Math.round(cy) - nr, nr * 2, nr * 2, null);
        if (pulse > 0.05f)
        {
            // the charge glow takes on the colour it is about to become
            g.setComposite(FADE[step(Math.min(1f, pulse))]);
            int pr = Math.round(r * (1.3f + 0.4f * pulse));
            g.drawImage(HAZE[form == BASE ? GOLD : BLUE], Math.round(cx) - pr, Math.round(cy) - pr, pr * 2, pr * 2, null);
            g.setComposite(oldComp);
        }
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            interp == null ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : interp);

        // the aura: behind the rings. It flares up after a flash, swells in the second charge, and a faint gold
        // flicker already starts to show through in the first charge
        if (form != BASE || charge > 0.45f)
        {
            float amt;
            int af = form;
            if (form == BASE)
            {
                af = GOLD;
                float c = (charge - 0.45f) / 0.55f;
                amt = 0.5f * c * c;
            }
            else
            {
                amt = Math.min(1f, since / 0.35f) * (1f + 0.25f * Math.max(0f, 1f - since / 1.2f));
                if (charge > 0) amt *= 1f + 0.35f * charge;
            }
            aura(g, k, cx, cy, r, t, s, af, amt, calm);
        }

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
                    Color[][] table = glow;
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
                        if (tr > 0.72f) table = coreCol;
                    }
                    else
                    {
                        if (tr < 0.6f) continue;
                        alpha = Math.min(1f, (tr - 0.6f) * 2.2f) * dd;
                        table = null;
                    }
                    alpha *= bright;
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
            star(g, k, cx + p[0], cy + p[1], tw, s, coreCol[hue(p[0], p[1], shift)]);
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
            g.setColor(glow[hh][step(0.18f * depth * bright)]);
            fillDot(g, k, cx + hx, cy + hy, 3.0f * s);
            g.setColor(glow[hh][step(0.5f * depth * bright)]);
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
                g.setColor(coreCol[hue(p[0], p[1], shift)][step(sa)]);
                fillDot(g, k, cx + p[0], cy + p[1], 0.6f * s);
            }
        }

        // tiny stars twinkling around it (in a form, the aura's rising specks take their place)
        if (form == BASE)
        {
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
                star(g, k, cx + px, cy + py, tw * 0.7f, s * 0.8f, coreCol[hue(px, py, shift)]);
            }
        }

        if (charge > 0) sparks(g, k, cx, cy, r, t, s, charge, form == BASE ? GOLD : BLUE, calm);
        if (flash > 0 || since < 0.6f) burst(g, k, cx, cy, r, s, flash, since, form);

        g.setComposite(oldComp);
        g.setStroke(oldStroke);
        g.setColor(oldColor);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa == null ? RenderingHints.VALUE_ANTIALIAS_DEFAULT : aa);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, sc == null ? RenderingHints.VALUE_STROKE_DEFAULT : sc);
    }

    // ------------------------------------------------------------------ the aura

    /** the aura's layers: how big (outer, middle, inner) and how strong, per form */
    private static final float[] LAYER_SCALE = {1f, 0.8f, 0.58f};
    private static final float[] GOLD_LAYER_ALPHA = {0.2f, 0.2f, 0.17f};
    private static final float[] BLUE_LAYER_ALPHA = {0.18f, 0.19f, 0.16f};
    /** the aura's gradient fill: stops, and per form, layer and strength level the colours (made once) */
    private static final float[] GRAD_STOPS = {0f, 0.5f, 1f};
    private static final int GRAD_LEVELS = 9;
    private static final Color[][][][] GRAD = new Color[3][3][GRAD_LEVELS][];

    static
    {
        // per form and layer: body, middle, tip colours (rgb) and their alphas at full strength
        int[][][] rgb = {
            {{0xffffff, 0xffffff, 0xffffff}, {0xffffff, 0xffffff, 0xffffff}, {0xffffff, 0xffffff, 0xffffff}},
            {{0xffbe46, 0xffaa32, 0xff8c1e}, {0xffe16e, 0xffcd50, 0xffb93c}, {0xfff8c8, 0xffeb96, 0xffdc6e}},
            {{0x78c8ff, 0x3278ff, 0x285aff}, {0xa0e6ff, 0x46beff, 0x3296ff}, {0xe6faff, 0x8ce1ff, 0x5ac8ff}},
        };
        float[][] alpha = {{0.1f, 0.42f, 0.16f}, {0.08f, 0.36f, 0.1f}, {0.14f, 0.3f, 0.06f}};
        for (int f = 0; f < 3; f++)
        {
            for (int l = 0; l < 3; l++)
            {
                for (int v = 0; v < GRAD_LEVELS; v++)
                {
                    float k = v / (float) (GRAD_LEVELS - 1);
                    Color[] c = new Color[3];
                    for (int i = 0; i < 3; i++)
                    {
                        int a = Math.round(255 * Math.min(1f, alpha[l][i] * k));
                        c[i] = new Color(rgb[f][l][i] | a << 24, true);
                    }
                    GRAD[f][l][v] = c;
                }
            }
        }
    }

    /**
     * The flame aura: three layers (outer, middle, inner), each one closed silhouette of flame tongues round the
     * body, so a whole layer is a single fill. The tongues are tallest on top and lean upward on the sides, so the
     * fire rises; each one licks up and drops back on its own beat and sways, with a wave running round. The gold
     * form burns wild; the blue form burns steadier, with more, shorter tongues and an electric rim.
     */
    private static void aura(Graphics2D g, Scratch k, float cx, float cy, float r, float t, float s, int form,
        float amt, boolean calm)
    {
        boolean gold = form == GOLD;
        float soft = calm ? 0.65f : 1f;
        float speed = calm ? 0.6f : 1f;
        int tongues = gold ? (calm ? 9 : 13) : (calm ? 10 : 15);
        float wild = gold ? 0.5f : 0.22f;
        float tall = (gold ? 1.05f : 0.82f) * r;
        float[] layerAlpha = gold ? GOLD_LAYER_ALPHA : BLUE_LAYER_ALPHA;
        for (int layer = 0; layer < 3; layer++)
        {
            float ls = LAYER_SCALE[layer];
            Path2D.Float path = k.path;
            path.reset();
            float base = r * (0.86f + 0.06f * layer) * ls + r * 0.1f * (1 - ls);
            float rot = layer * 0.37f + (float) Math.sin(t * 0.7f * speed + layer) * 0.08f;
            float pvx = cx + (float) Math.cos(rot) * base;
            float pvy = cy + (float) Math.sin(rot) * base * 0.95f;
            path.moveTo(pvx, pvy);
            for (int i = 0; i < tongues; i++)
            {
                // the valley this tongue ends at, and its own centre line
                double a1 = rot + TAU * (i + 1) / tongues;
                double ac = rot + TAU * (i + 0.5) / tongues;
                float vx = cx + (float) Math.cos(a1) * base;
                float vy = cy + (float) Math.sin(a1) * base * 0.95f;
                // this tongue: how tall (tallest on top), how it flickers, which way it leans
                float up = (float) -Math.sin(ac);
                float shape = 0.22f + 0.78f * (float) Math.pow(0.5f + 0.5f * up, 1.6);
                int seed = i * 7 + layer * 31;
                float ph = hash(seed) * 6.28f;
                float rate = (gold ? 1.3f : 0.75f) * (0.8f + 0.5f * hash(seed + 1)) * speed;
                float lick = (float) ((t * rate + hash(seed + 2)) % 1.0);
                lick = 1f - lick;
                float flick = (0.5f + 0.3f * (float) Math.sin(t * 9.1f * speed + ph) + 0.2f * (float) Math.sin(t * 15.3f * speed + ph * 1.7f));
                float h = tall * ls * shape * (1f - wild + wild * (0.55f * flick + 0.45f * lick * lick)) * amt;
                float sway = (float) (0.16 * Math.sin(t * 3.3f * speed + ph) + 0.1 * Math.sin(t * 4.6f * speed - ac * 2));
                // the tip leans upward: radial direction bent towards the top
                double dirX = Math.cos(ac + sway);
                double dirY = Math.sin(ac + sway) - 0.9;
                double len = Math.sqrt(dirX * dirX + dirY * dirY);
                float bx = cx + (float) Math.cos(ac) * base;
                float by = cy + (float) Math.sin(ac) * base * 0.95f;
                float tx = bx + (float) (dirX / len) * h;
                float ty = by + (float) (dirY / len) * h;
                // the sides hug the body, then sweep up and pinch to a sharp point: a spiky flame tongue
                path.quadTo(pvx * 0.45f + bx * 0.25f + tx * 0.3f, pvy * 0.45f + by * 0.25f + ty * 0.3f, tx, ty);
                path.quadTo(vx * 0.45f + bx * 0.25f + tx * 0.3f, vy * 0.45f + by * 0.25f + ty * 0.3f, vx, vy);
                pvx = vx;
                pvy = vy;
            }
            path.closePath();
            // a few slender flame wisps break off the top of the outer fire and rise away, shrinking
            int wisps = layer > 0 ? 0 : calm ? 1 : 3;
            for (int j = 0; j < wisps; j++)
            {
                float ph = (float) ((t * (gold ? 1.1f : 0.7f) * speed + j / (float) wisps) % 1.0);
                float wx = cx + r * 0.42f * (float) Math.sin(j * 2.4f + t * 0.8f) + (float) Math.sin(t * 7f + j) * 0.8f * s;
                float wy = cy - r * 0.9f - tall * (0.75f + 0.7f * ph);
                float ws = (1f - ph) * (gold ? 1.5f : 1.2f) * s * amt;
                if (ws < 0.3f) continue;
                path.moveTo(wx, wy - ws * 3.2f);
                path.quadTo(wx + ws * 0.9f, wy - ws * 0.4f, wx, wy + ws);
                path.quadTo(wx - ws * 0.9f, wy - ws * 0.4f, wx, wy - ws * 3.2f);
                path.closePath();
            }
            // filled with light that is hot and bright by the body and fades out towards the tips
            float flare = layer == 2 ? 1f : 0.9f + 0.1f * (float) Math.sin(t * 11f * speed + layer);
            float strength = layerAlpha[layer] * 5f * soft * Math.min(1f, amt) * flare;
            int level = Math.max(0, Math.min(GRAD_LEVELS - 1, Math.round(strength * (GRAD_LEVELS - 1))));
            if (level > 0)
            {
                java.awt.Paint oldPaint = g.getPaint();
                float gr = base + tall * ls * 0.95f;
                g.setPaint(new java.awt.RadialGradientPaint(cx, cy - r * 0.1f, gr, GRAD_STOPS, GRAD[form][layer][level]));
                g.fill(path);
                g.setPaint(oldPaint);
            }
            if (layer < 2)
            {
                // the brighter rims: gold's edges burn and flicker; blue's shimmer electric
                float rim = gold ? (layer == 0 ? 0.75f : 0.4f) * (0.8f + 0.2f * (float) Math.sin(t * 17f * speed + layer * 2))
                    : (layer == 0 ? 0.55f : 0.36f) + 0.22f * (float) Math.sin(t * 13f * speed + layer * 2.5f);
                if (layer == 0)
                {
                    // a soft halo round the outer edge, so the fire glows against the dark panel
                    g.setStroke(k.mid);
                    g.setColor(AURA[form][1][step(0.22f * rim * soft * Math.min(1f, amt))]);
                    g.draw(path);
                }
                g.setStroke(k.thin);
                g.setColor(AURA[form][3][step(rim * soft * Math.min(1f, amt))]);
                g.draw(path);
            }
        }

        // thin streaks of light rushing up through the fire, so it reads as rising
        g.setStroke(k.thin);
        int streaks = calm ? 2 : gold ? 6 : 5;
        for (int j = 0; j < streaks; j++)
        {
            float ph = (float) ((t * (gold ? 1.4f : 0.9f) * speed + hash(j + 60)) % 1.0);
            float sx = cx + r * (hash(j + 70) * 1.5f - 0.75f);
            float sy = cy + r * (0.55f - 1.9f * ph) - tall * 0.5f * ph;
            float len = (2.5f + 3f * hash(j + 80)) * s * (gold ? 1f : 0.8f);
            float a = (float) Math.sin(Math.PI * ph) * 0.55f * soft * Math.min(1f, amt);
            g.setColor(AURA[form][2][step(a)]);
            k.line.setLine(sx, sy, sx, sy + len);
            g.draw(k.line);
        }

        // energy specks rising through it
        int specks = calm ? 3 : 7;
        for (int j = 0; j < specks; j++)
        {
            float ph = (float) ((t * (gold ? 0.7f : 0.5f) * speed + hash(j + 90)) % 1.0);
            float sx = cx + r * (hash(j + 40) * 2f - 1f) * 1.05f + (float) Math.sin(t * 2f + j) * s;
            float sy = cy + r * (0.9f - 2.3f * ph);
            float a = (float) Math.sin(Math.PI * ph) * 0.9f * Math.min(1f, amt);
            g.setColor(AURA[form][2][step(a)]);
            fillDot(g, k, sx, sy, (0.5f + 0.4f * (1f - ph)) * s);
        }

        // lightning: now and then a short jagged crackle flicks out from the body (often in gold, rarely in blue)
        float chance = gold ? (calm ? 0.12f : 0.4f) : (calm ? 0.05f : 0.16f);
        int slot = (int) Math.floor(t * 6f);
        float into = t * 6f - slot;
        if (amt > 0.6f && into < 0.55f && hash(slot * 3 + 7) < chance)
        {
            double ang = hash(slot * 3 + 8) * TAU;
            float r0 = r * 0.7f;
            float r1 = r * (1.35f + 0.3f * hash(slot * 3 + 9));
            Path2D.Float bolt = k.path;
            bolt.reset();
            float nx = (float) -Math.sin(ang);
            float ny = (float) Math.cos(ang);
            for (int i = 0; i <= 5; i++)
            {
                float f = i / 5f;
                float rr = r0 + (r1 - r0) * f;
                float jit = (i == 0 || i == 5) ? 0f : (hash(slot * 17 + i) * 2f - 1f) * 2.6f * s;
                float x = cx + (float) Math.cos(ang) * rr + nx * jit;
                float y = cy + (float) Math.sin(ang) * rr + ny * jit;
                if (i == 0) bolt.moveTo(x, y);
                else bolt.lineTo(x, y);
            }
            float fade = 1f - into / 0.55f;
            g.setStroke(k.bolt);
            g.setColor(AURA[form][1][step(0.4f * fade)]);
            g.draw(bolt);
            g.setStroke(k.thin);
            g.setColor(WHITE[step(0.95f * fade)]);
            g.draw(bolt);
        }
    }

    /** charge-up sparks: short crackles bursting out round it, more of them as the charge builds */
    private static void sparks(Graphics2D g, Scratch k, float cx, float cy, float r, float t, float s, float charge,
        int toward, boolean calm)
    {
        int slot = (int) Math.floor(t * 14f);
        int n = calm ? 1 : 1 + Math.round(4 * charge);
        g.setStroke(k.thin);
        for (int i = 0; i < n; i++)
        {
            int seed = slot * 13 + i * 5;
            double ang = hash(seed) * TAU;
            float r0 = r * (0.8f + 0.45f * hash(seed + 1));
            float len = (2f + 3.5f * hash(seed + 2) * charge) * s;
            float x0 = cx + (float) Math.cos(ang) * r0;
            float y0 = cy + (float) Math.sin(ang) * r0;
            // a little zigzag outward
            float kx = (float) -Math.sin(ang) * 1.2f * s;
            float ky = (float) Math.cos(ang) * 1.2f * s;
            float mx = x0 + (float) Math.cos(ang) * len * 0.5f + kx;
            float my = y0 + (float) Math.sin(ang) * len * 0.5f + ky;
            float x1 = x0 + (float) Math.cos(ang) * len;
            float y1 = y0 + (float) Math.sin(ang) * len;
            g.setColor(AURA[toward][3][step(0.7f)]);
            k.line.setLine(x0, y0, mx, my);
            g.draw(k.line);
            k.line.setLine(mx, my, x1, y1);
            g.draw(k.line);
            g.setColor(WHITE[step(0.9f)]);
            fillDot(g, k, x0, y0, 0.55f * s);
        }
    }

    /** the change of form: a white flash that swallows it, then a ring of light that rushes outward */
    private static void burst(Graphics2D g, Scratch k, float cx, float cy, float r, float s, float flash, float since,
        int form)
    {
        if (flash > 0)
        {
            // a soft bloom of white light, with a hot core
            Composite old = g.getComposite();
            Object interp = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setComposite(FADE[step(flash)]);
            int fr = Math.round(r * (1.1f + 0.6f * flash));
            g.drawImage(BLOOM, Math.round(cx) - fr, Math.round(cy) - fr, fr * 2, fr * 2, null);
            g.setComposite(old);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                interp == null ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : interp);
            g.setColor(WHITE[step(0.85f * flash)]);
            fillDot(g, k, cx, cy, r * 0.45f * (0.6f + 0.4f * flash));
        }
        if (since < 0.6f)
        {
            float q = since / 0.6f;
            float rr = r * (1f + 0.65f * q);
            g.setStroke(k.mid);
            g.setColor(AURA[form][3][step(0.8f * (1f - q))]);
            k.dot.setFrame(cx - rr, cy - rr * 0.95f, rr * 2, rr * 1.9f);
            g.draw(k.dot);
        }
    }

    // ------------------------------------------------------------------ its float, and small helpers

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

    /** a small four-point glint: faint rays and a bright tinted centre (colours by alpha step) */
    private static void star(Graphics2D g, Scratch k, float x, float y, float tw, float s, Color[] tint)
    {
        float len = (0.8f + 2.2f * tw) * s;
        g.setColor(tint[step(0.55f * tw)]);
        k.line.setLine(x - len, y, x + len, y);
        g.draw(k.line);
        k.line.setLine(x, y - len, x, y + len);
        g.draw(k.line);
        g.setColor(tint[step(0.95f * tw)]);
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
