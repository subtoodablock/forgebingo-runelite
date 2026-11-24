package com.forgebingo.core;

import com.forgebingo.core.PixelArt.Grid;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntUnaryOperator;

/**
 * Raid-themed pixel-art creatures for the side panel (Chambers of Xeric crab, Theatre of Blood nylocas, Tombs of
 * Amascut chibi bosses) plus the little things that fall. All of it is our own original art, drawn on the shared
 * PixelArt grid facing LEFT with its feet on the bottom rows. Two walk frames each.
 */
public final class RaidSprites
{
    private static final Map<String, BufferedImage> CACHE = new ConcurrentHashMap<>();

    private RaidSprites()
    {
    }

    /** One walk frame (0 or 1) of a raid creature, or null for critters this file does not draw. */
    public static BufferedImage image(Critter c, int variant, int frame)
    {
        if (c == null) return null;
        switch (c)
        {
            case CRAB:
            case NYLO:
            case XARPUS:
            case VERZIK:
            case AKKHA:
            case ZEBAK:
            case KEPHRI:
            case BABA:
            case WARDEN:
                break;
            default:
                return null;
        }
        final int v = Math.max(0, Math.min(c.variants - 1, variant));
        final int f = frame == 0 ? 0 : 1;
        return CACHE.computeIfAbsent(c + "|" + v + "|" + f, k -> make(c, v, f));
    }

    /** A small falling thing (14 to 18 px), or null for ones this file does not draw. */
    public static BufferedImage fall(Critter.Fall f, int variant)
    {
        if (f == null) return null;
        final int v = variant <= 0 ? 0 : 1;
        switch (f)
        {
            case BLOOD_DROP:
            case CRYSTAL:
                return CACHE.computeIfAbsent("fall|" + f + "|" + v, k -> makeFall(f, v));
            case KEPHRI_BOMB:
            case WATER_DROP:
                return CACHE.computeIfAbsent("fall|" + f + "|0", k -> makeFall(f, 0));
            default:
                return null;
        }
    }

    private static BufferedImage make(Critter c, int v, int f)
    {
        switch (c)
        {
            case CRAB: return PixelArt.render(crab(v, f), pal(CRAB_PAL + CRAB_GEM[v]));
            case NYLO: return PixelArt.render(nylo(f), pal(NYLO_PAL[v] + (f == 1 ? NYLO_PULSE[v] : "")));
            case XARPUS: return PixelArt.render(xarpus(f), pal(XARPUS_PAL));
            case VERZIK: return PixelArt.render(verzik(f), pal(VERZIK_PAL));
            case AKKHA: return PixelArt.render(akkha(f), pal(AKKHA_PAL));
            case ZEBAK: return PixelArt.render(zebak(f), pal(ZEBAK_PAL));
            case KEPHRI: return PixelArt.render(kephri(f), pal(KEPHRI_PAL));
            case BABA: return PixelArt.render(baba(f), pal(BABA_PAL));
            default: return PixelArt.render(warden(f), pal(WARDEN_PAL + (f == 1 ? " Wffffff Tb0fff0" : " Wd0fff8 T50f0e0")));
        }
    }

    // ---------------------------------------------------------------- palette and drawing helpers

    /** "kRRGGBB gRRGGBB ..." : a palette letter followed by its six hex digits, separated by spaces. */
    private static IntUnaryOperator pal(String spec)
    {
        Map<Character, Integer> m = new HashMap<>();
        for (String t : spec.trim().split("\\s+"))
        {
            if (t.length() == 7) m.put(t.charAt(0), Integer.parseInt(t.substring(1), 16));
        }
        return ch -> m.getOrDefault((char) ch, 0xff00ff);
    }

    private static void part(Grid g, Grid fill)
    {
        PixelArt.part(g, fill, 'k');
    }

    /** Fills an ellipse into {@code m}, lit from the top left: highlight letter, base letter, shade letter. */
    private static Grid ell(Grid m, double cx, double cy, double rx, double ry, char base, char hi, char sh)
    {
        for (int y = 0; y < m.h; y++)
        {
            for (int x = 0; x < m.w; x++)
            {
                double nx = (x - cx) / rx;
                double ny = (y - cy) / ry;
                if (nx * nx + ny * ny > 1.0) continue;
                double t = nx * 0.6 + ny * 0.8;
                m.set(x, y, t < -0.5 ? hi : t > 0.5 ? sh : base);
            }
        }
        return m;
    }

    private static Grid ell(double cx, double cy, double rx, double ry, char base, char hi, char sh)
    {
        return ell(new Grid(), cx, cy, rx, ry, base, hi, sh);
    }

    /** A line of {@code thick} x {@code thick} pixel brush. */
    private static void line(Grid m, int x0, int y0, int x1, int y1, char c, int thick)
    {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0;
        int y = y0;
        while (true)
        {
            for (int a = 0; a < thick; a++)
            {
                for (int b = 0; b < thick; b++) m.set(x + a, y + b, c);
            }
            if (x == x1 && y == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy)
            {
                err -= dy;
                x += sx;
            }
            if (e2 < dx)
            {
                err += dx;
                y += sy;
            }
        }
    }

    /** Paints {@code o} onto {@code m} only where {@code m} already has pixels. */
    private static void overlay(Grid m, Grid o)
    {
        for (int y = 0; y < o.h; y++)
        {
            for (int x = 0; x < o.w; x++)
            {
                if (o.px[y][x] != 0 && m.has(x, y)) m.px[y][x] = o.px[y][x];
            }
        }
    }

    /** Pixels with nothing directly below them become {@code sh} (a cheap under-shade). */
    private static void underShade(Grid m, char sh)
    {
        for (int y = 0; y < m.h; y++)
        {
            for (int x = 0; x < m.w; x++)
            {
                if (m.px[y][x] != 0 && !m.has(x, y + 1)) m.px[y][x] = sh;
            }
        }
    }

    private static Grid rect(int x, int y, int w, int h, char c)
    {
        Grid m = new Grid();
        for (int j = 0; j < h; j++)
        {
            for (int i = 0; i < w; i++) m.set(x + i, y + j, c);
        }
        return m;
    }

    // ---------------------------------------------------------------- Chambers of Xeric: the jewelled crab

    private static final String CRAB_PAL = "k1c1a22 g8e8e98 Hbcbcc6 G5e5e6c wf6f6f6 p1a2468";
    private static final String[] CRAB_GEM = {
        " Wffffff Ye6f4ff Xaec4de x7890b0 o56688a", // white
        " Wffe0e0 Yff8a8a Xe03030 x901818 o5a0c10",  // red
        " Wf0fff0 Y9affa0 X30c050 x187a30 o0c4a1c",  // green
        " Wf0f8ff Y8ac8ff X3070e8 x1a3c9a o0e2260",  // blue
    };

    /** One pointed, faceted crystal: tip leans by {@code lean}, light face on the left, darker on the right. */
    private static Grid shard(double cx, int baseY, int w, int h, int lean)
    {
        Grid m = new Grid();
        double tipx = cx + lean;
        int top = baseY - h;
        for (int y = top; y <= baseY; y++)
        {
            double fr = (double) (y - top) / h;
            double c = tipx + (cx - tipx) * fr;
            double hw = Math.max(0.5, w / 2.0 * Math.min(1.0, fr * 1.3));
            for (int x = (int) Math.floor(c - hw); x <= (int) Math.ceil(c + hw); x++)
            {
                double dx = x - c;
                if (Math.abs(dx) > hw) continue;
                char ch = dx <= 0 ? 'Y' : 'X';
                if (dx >= hw - 0.6 && fr > 0.3) ch = 'x';
                if (x == (int) Math.round(c) - 1 && fr > 0.2 && fr < 0.65 && w >= 6) ch = 'W';
                m.set(x, y, ch);
            }
        }
        return m;
    }

    private static Grid crab(int v, int f)
    {
        Grid g = new Grid();
        int[] lx = {14, 19, 25, 30};
        for (int i = 0; i < 4; i++)
        {
            boolean lift = i % 2 == f;
            Grid leg = new Grid();
            for (int y = 27; y <= (lift ? 31 : 32); y++)
            {
                leg.set(lx[i], y, 'g');
                leg.set(lx[i] + 1, y, 'G');
            }
            leg.set(lx[i] - 1, lift ? 31 : 32, 'g');
            part(g, leg);
        }
        // far claw (darker) and its arm
        Grid arm = new Grid();
        line(arm, 9, 18, 13, 22, 'G', 2);
        part(g, arm);
        String[] claw = {"Hg..gg.", "Hgg.gGG", "HgggGGG", ".gggGG.", "..gGG.."};
        String[] dark = new String[claw.length];
        for (int i = 0; i < claw.length; i++) dark[i] = claw[i].replace('g', 'G').replace('H', 'g');
        part(g, PixelArt.shape(4, 12 + (1 - f), dark));
        // eye stalks
        int[] ex = {13, 16};
        for (int i = 0; i < 2; i++)
        {
            Grid st = new Grid();
            for (int y = 17 + i; y <= 23; y++) st.set(ex[i], y, 'G');
            part(g, st);
            part(g, PixelArt.shape(ex[i] - 1, 14 + i, "www", "pww", "www"));
        }
        // the crystal cluster: back shards first, tall one in the middle, short ones in front
        double[][] sh = {
            {20, 13, 6, -2}, {27, 15, 7, 2}, {23.5, 18, 8, 0},
            {19, 10, 6, -3}, {25.5, 11, 6, 3}, {29.5, 8, 5, 2}};
        for (double[] s : sh)
        {
            PixelArt.part(g, shard(s[0], 23, (int) s[2], (int) s[1], (int) s[3]), 'o');
        }
        // the stone shell peeking out underneath, with a glow rim in the crystal colour
        Grid shell = ell(21.5, 25.5, 9.5, 3.6, 'g', 'H', 'G');
        for (int y = 0; y < shell.h; y++)
        {
            for (int x = 0; x < shell.w; x++)
            {
                if (shell.px[y][x] != 0 && !shell.has(x, y - 1)) shell.px[y][x] = 'x';
            }
        }
        shell.pxs('G', 16, 27, 25, 27, 29, 26);
        part(g, shell);
        // near claw bobs
        part(g, PixelArt.shape(4, 21 + f, claw));
        g.pxs('G', 9, 26 + f, 10, 26 + f);
        return g;
    }

    // ---------------------------------------------------------------- Theatre of Blood

    private static final String NYLO_BASE = "k14141c d2c2c38 D464656 l5c5c72 L9a9ab0 ";
    private static final String[] NYLO_PAL = {
        NYLO_BASE + "aB8b0b8 AA0969e cE8e0e6 rF4eef2 b6a626c h7a7490 ef0f0ff",
        NYLO_BASE + "a58c860 A3fa84c cC8ffc0 rB8f8a8 b2a7a3a h2f8a3a eF0ffe0",
        NYLO_BASE + "a5cc8f0 A3c9cc8 cD8f8ff rB0ecff b2a78a8 h2f86b0 eF0ffff",
    };
    private static final String[] NYLO_PULSE = {" cF8f2f6 h9a94aa", " cE8ffe0 h4cb85a", " cFFFFFF h4cb0d8"};

    private static void tleg(Grid g, int rx, int ry, int kx, int ky, int fx, int fy)
    {
        Grid leg = new Grid();
        line(leg, rx, ry, kx, ky, 'l', 1);
        line(leg, kx, ky, fx, fy, 'l', 1);
        leg.set(kx, ky, 'L');
        leg.set(fx, fy, 'L');
        leg.set(fx + (fx > kx ? -1 : fx < kx ? 1 : 0), fy - 1, 'L');
        for (int y = 0; y < leg.h; y++)
        {
            for (int x = 0; x < leg.w; x++)
            {
                if (leg.px[y][x] != 0) g.set(x, y, leg.px[y][x]);
            }
        }
    }

    private static Grid nylo(int f)
    {
        Grid g = new Grid();
        // spindly legs with high knees and pointed tips; left set from the head, right set from under the abdomen
        int[][] legs = {
            {14, 25, 9, 19, 7, 30}, {16, 26, 12, 23, 11, 31}, {18, 27, 16, 26, 15, 32},
            {24, 26, 27, 25, 27, 32}, {26, 25, 30, 22, 31, 31}, {27, 23, 32, 18, 34, 28}};
        for (int i = 0; i < 6; i++)
        {
            int[] l = legs[i];
            int sw = ((i + f) % 2 == 0) ? 1 : -1;
            boolean lift = i % 2 == f;
            int fx = l[4] + (i < 3 ? sw : -sw);
            int fy = lift ? l[5] - 1 : l[5];
            tleg(g, l[0], l[1], l[2], l[3], fx, fy);
        }
        // the glowing egg: bright core, lighter rim on the lit side, dark bands wrapped around it
        Grid egg = new Grid();
        double cy = 15;
        double ry = 11;
        double rx = 7.5;
        for (int y = 3; y <= 27; y++)
        {
            double ny = (y - cy) / ry;
            if (Math.abs(ny) > 1) continue;
            double cxr = 23 - (y - cy) * 0.25;
            double hw = rx * Math.sqrt(1 - ny * ny);
            for (int x = (int) Math.floor(cxr - hw); x <= (int) Math.ceil(cxr + hw); x++)
            {
                if (Math.abs(x - cxr) > hw) continue;
                double nx = (x - cxr) / rx;
                double d = Math.sqrt(nx * nx + ny * ny);
                double t = nx * 0.6 + ny * 0.8;
                char ch = d < 0.38 ? 'c' : d < 0.78 ? 'a' : (t < 0.1 ? 'r' : 'A');
                egg.set(x, y, ch);
            }
        }
        int[] bandBase = {9, 14, 19, 24};
        for (int bb : bandBase)
        {
            double cxc = 23 - (bb - cy) * 0.25;
            for (int x = 14; x <= 32; x++)
            {
                int y = bb + (int) Math.round(0.07 * (x - cxc) * (x - cxc));
                double d = Math.sqrt(Math.pow((x - cxc) / rx, 2) + Math.pow((y - cy) / ry, 2));
                if (egg.has(x, y) && d > 0.3) egg.px[y][x] = 'b';
            }
        }
        // spikes along the rim
        Grid spikes = new Grid();
        for (int y = 5; y <= 21; y += 4)
        {
            double cxr = 23 - (y - cy) * 0.25;
            double hw = rx * Math.sqrt(1 - Math.pow((y - cy) / ry, 2));
            int xe = (int) Math.floor(cxr + hw);
            spikes.set(xe, y, 'd');
            spikes.set(xe + 1, y, 'd');
            spikes.set(xe + 2, y - 1, 'd');
        }
        part(g, spikes);
        part(g, egg);
        // soft halo in the variant colour
        for (int y = 0; y < g.h; y++)
        {
            for (int x = 0; x < g.w; x++)
            {
                if (g.px[y][x] != 0) continue;
                boolean near2 = false;
                boolean near3 = false;
                for (int dy = -3; dy <= 3 && !near2; dy++)
                {
                    for (int dx = -3; dx <= 3; dx++)
                    {
                        if (!egg.has(x + dx, y + dy)) continue;
                        double dd = Math.sqrt(dx * dx + dy * dy);
                        if (dd <= 2.3) near2 = true;
                        else if (dd <= 3.2) near3 = true;
                    }
                }
                if (near2 || (near3 && (x + y) % 2 == 0)) g.px[y][x] = 'h';
            }
        }
        // tiny dark head with glowing eyes and little fangs
        part(g, ell(15.5, 24.5, 3.6, 3.2, 'd', 'D', 'd'));
        g.pxs('e', 13, 24, 13, 25, 16, 23, 17, 23);
        g.pxs('L', 12, 27, 14, 28);
        return g;
    }

    /** Even-odd polygon fill. */
    private static Grid poly(int[][] pts, char c)
    {
        Grid m = new Grid();
        for (int y = 0; y < m.h; y++)
        {
            for (int x = 0; x < m.w; x++)
            {
                boolean in = false;
                for (int i = 0, j = pts.length - 1; i < pts.length; j = i++)
                {
                    if ((pts[i][1] > y) != (pts[j][1] > y)
                        && x < (double) (pts[j][0] - pts[i][0]) * (y - pts[i][1]) / (pts[j][1] - pts[i][1]) + pts[i][0])
                    {
                        in = !in;
                    }
                }
                if (in) m.set(x, y, c);
            }
        }
        return m;
    }

    private static final String XARPUS_PAL =
        "k120c0e d3a2a2e f5a443c F7a5e52 m8c786c Ma89482 n4a3a38 we8eaf4 Wb8bccc eff5a4a tc8a090 cd8d0c0";

    /** A bat wing: polygon of shoulder, wrist, tip and scalloped trailing edge; bones run from the wrist. */
    private static void wing(Grid g, int[][] p, int[] fingers, boolean right)
    {
        int[][] q = new int[p.length][2];
        for (int i = 0; i < p.length; i++)
        {
            q[i][0] = right ? 41 - p[i][0] : p[i][0];
            q[i][1] = p[i][1];
        }
        Grid w = poly(q, 'm');
        for (int y = 0; y < w.h; y++)
        {
            for (int x = 0; x < w.w; x++)
            {
                if (w.px[y][x] != 0 && (x + 2 * y) % 6 == 0) w.px[y][x] = 'M';
            }
        }
        line(w, q[0][0], q[0][1], q[1][0], q[1][1], 'n', 2);
        for (int i : fingers) line(w, q[1][0], q[1][1], q[i][0], q[i][1], 'n', 1);
        part(g, w);
    }

    private static Grid xarpus(int f)
    {
        Grid g = new Grid();
        // always airborne: wings raised high (frame 0) or swept down (frame 1)
        int[][] lw = f == 0
            ? new int[][] {{18, 15}, {10, 7}, {3, 1}, {4, 6}, {2, 10}, {5, 12}, {6, 15}, {9, 15}, {12, 18}}
            : new int[][] {{15, 15}, {8, 15}, {2, 19}, {4, 22}, {2, 25}, {5, 24}, {7, 26}, {9, 23}, {11, 22}};
        int[] fingers = {2, 4, 6, 8};
        wing(g, lw, fingers, false);
        wing(g, lw, fingers, true);
        // tail trailing behind
        Grid tail = new Grid();
        line(tail, 23, 23, 27, 27, 't', 2);
        line(tail, 27, 27, 31, 26 - f, 't', 2);
        part(g, tail);
        // small tucked legs with claws
        for (int i = 0; i < 2; i++)
        {
            int x = i == 0 ? 18 : 22;
            int len = 0;
            Grid leg = new Grid();
            for (int y = 24; y <= 24 + len; y++)
            {
                leg.set(x, y, 'd');
                leg.set(x + 1, y, 'd');
            }
            leg.set(x, 25 + len, 'c');
            leg.set(x + 1, 25 + len, 'c');
            part(g, leg);
        }
        // compact furry body
        Grid torso = ell(20.5, 19.5, 4.6, 5.2, 'd', 'F', 'd');
        torso.pxs('f', 18, 17, 20, 18, 19, 21, 22, 20, 21, 23, 23, 17);
        torso.pxs('F', 17, 19, 22, 22);
        part(g, torso);
        // horn blade, bat ears, head
        Grid horn = new Grid();
        line(horn, 20, 9, 18, 2, 'w', 2);
        horn.set(18, 2, 'W');
        horn.set(19, 3, 'W');
        part(g, horn);
        part(g, PixelArt.shape(16, 7, "d.", "dd", "dd"));
        part(g, PixelArt.shape(24, 7, ".d", "dd", "dd"));
        part(g, ell(20.5, 12.5, 4.6, 4.0, 'd', 'f', 'd'));
        g.pxs('e', 18, 12, 19, 12, 22, 12, 23, 12);
        g.pxs('k', 18, 11, 23, 11);
        g.pxs('F', 20, 14, 21, 14);
        g.pxs('w', 19, 15, 22, 15);
        return g;
    }

    private static final String VERZIK_PAL =
        "k14101c P6e3e90 Q9a66c0 S40205a q2c2a34 Yf0d030 yc0a010 g6a6030 D4a4650 E6e6a7a "
            + "Lc8c4e4 la09cc0 H2a2230 N4a3a5a r8a1830 wf0f0f0 z8472a8 hf2eeff eff5050 T6cb8b4 U3c8a8e V98d8d4";

    private static void vleg(Grid g, int rx, int ry, int kx, int ky, int fx, int fy, boolean right)
    {
        int[] p = {rx, ry, kx, ky, fx, fy};
        if (right)
        {
            for (int i = 0; i < 6; i += 2) p[i] = 40 - p[i];
        }
        Grid leg = new Grid();
        line(leg, p[0], p[1], p[2], p[3], 'D', 3);
        line(leg, p[2], p[3], p[4], p[5], 'D', 3);
        line(leg, p[0], p[1], p[2], p[3], 'E', 1);
        line(leg, p[2], p[3], p[4], p[5], 'E', 1);
        part(g, leg);
    }

    private static Grid verzik(int f)
    {
        Grid g = new Grid();
        int[][] legs = {{17, 20, 8, 11, 4, 24}, {17, 22, 5, 18, 2, 30}, {18, 25, 9, 25, 8, 31}, {19, 27, 14, 28, 14, 32}};
        for (int i = 0; i < 4; i++)
        {
            int[] l = legs[i];
            int sw = ((i + f) % 2 == 0) ? 1 : -1;
            boolean lift = i % 2 == f;
            vleg(g, l[0], l[1], l[2], l[3], l[4] + sw, lift && l[5] > 28 ? l[5] - 1 : l[5], false);
            vleg(g, l[0], l[1], l[2], l[3], l[4] - sw, lift && l[5] > 28 ? l[5] - 1 : l[5], true);
        }
        // the big spiked purple abdomen with a yellow crest
        Grid spikes = new Grid();
        Grid egg = new Grid();
        double cy = 12;
        double ry = 11.5;
        double rx = 8.5;
        for (int y = 1; y <= 24; y++)
        {
            double ny = (y - cy) / ry;
            if (Math.abs(ny) > 1) continue;
            double cxr = 25 - (y - cy) * 0.2;
            double hw = rx * Math.sqrt(1 - ny * ny);
            for (int x = (int) Math.floor(cxr - hw); x <= (int) Math.ceil(cxr + hw); x++)
            {
                if (Math.abs(x - cxr) > hw) continue;
                double nx = (x - cxr) / rx;
                double d = Math.sqrt(nx * nx + ny * ny);
                double t = nx * 0.6 + ny * 0.8;
                egg.set(x, y, d > 0.84 ? 'q' : t < -0.45 ? 'Q' : t > 0.4 ? 'S' : 'P');
            }
            if (y % 3 == 1 && y > 3 && y < 21)
            {
                int xe = (int) Math.floor(cxr + hw);
                spikes.set(xe, y, 'g');
                spikes.set(xe + 1, y, 'g');
                spikes.set(xe + 1, y - 1, 'g');
            }
        }
        for (int i = 0; i <= 7; i++)
        {
            for (int t = 0; t < 3; t++)
            {
                egg.set(24 - i, 4 + i + t, t == 2 ? 'y' : 'Y');
                egg.set(24 + i, 4 + i + t, t == 2 ? 'y' : 'Y');
            }
        }
        part(g, spikes);
        part(g, egg);
        // Verzik herself, bobbing a pixel
        Grid body = new Grid();
        Grid horns = new Grid();
        horns.pxs('N', 17, 6, 17, 5, 16, 4, 25, 6, 25, 5, 26, 4);
        part(body, horns);
        Grid hair = ell(21, 11, 5.2, 5.6, 'H', 'N', 'H');
        for (int y = 14; y <= 18; y++)
        {
            hair.set(16, y, 'H');
            hair.set(26, y, 'H');
        }
        part(body, hair);
        part(body, PixelArt.shape(20, 15, "lll", "lll"));
        part(body, ell(21, 17.5, 5, 2.2, 'L', 'L', 'l'));
        part(body, ell(21, 10.5, 3.9, 4.4, 'L', 'L', 'l'));
        // swept-back fringe, stern brows, narrow red eyes, nose, dark-red mouth with fangs
        body.pxs('H', 19, 6, 20, 6, 21, 6, 22, 6, 23, 6, 18, 7, 19, 7, 23, 7, 24, 7, 18, 8, 24, 8);
        body.pxs('N', 20, 7, 22, 7);
        body.pxs('k', 18, 9, 19, 9, 20, 10, 24, 9, 23, 9, 22, 10);
        body.pxs('e', 18, 10, 19, 10, 23, 10, 24, 10);
        body.pxs('l', 21, 11, 21, 12, 17, 12, 25, 12, 17, 13, 25, 13);
        body.pxs('r', 20, 13, 21, 13, 22, 13);
        body.pxs('w', 20, 14, 22, 14);
        body.pxs('z', 17, 10, 25, 10, 18, 11, 19, 11, 23, 11, 24, 11);
        body.pxs('h', 22, 8, 19, 12, 23, 12);
        Grid dress = ell(21, 22.5, 5, 6, 'T', 'V', 'U');
        dress.pxs('U', 19, 21, 21, 23, 23, 25, 20, 26);
        part(body, dress);
        part(body, PixelArt.shape(17, 19, "LL", "LL"));
        part(body, PixelArt.shape(24, 19, "LL", "LL"));
        blit(g, body, f == 1 ? -1 : 0);
        return g;
    }

    // ---------------------------------------------------------------- Tombs of Amascut

    private static final String AKKHA_PAL =
        "k2a1a14 Sd8b890 sb08c68 Rb4552a rd8784a Yf0e0c0 Wf2f2f2 Hf2f0e8 Ib4bcc4 Gc8a860 Z7a4a9a Be8dcc0 pa09080 q6a4028 gc8b890 e202020";

    private static Grid akkha(int f)
    {
        Grid g = new Grid();
        int bob = -f;
        // spear behind everything
        Grid sp = new Grid();
        for (int y = 8; y <= 30; y++) sp.set(5, y + bob, 'p');
        part(g, sp);
        part(g, PixelArt.shape(3, 2 + bob, "..H..", ".HHI.", "HHHII", "HHHII", ".HII.", "..p.."));
        // back arm
        part(g, PixelArt.shape(27, 14, "RR", "RS", "SS", "SS", "SS", "gg", "SS"));
        // legs
        int backX = f == 0 ? 22 : 21;
        int frontX = f == 0 ? 17 : 19;
        for (int i = 0; i < 2; i++)
        {
            int x = i == 0 ? backX : frontX;
            int lift = (i == 0) == (f == 1) ? 1 : 0;
            Grid l = new Grid();
            for (int y = 25; y <= 30 - lift; y++)
            {
                l.set(x, y, 'S');
                l.set(x + 1, y, 'S');
                l.set(x + 2, y, 's');
                if (y >= 28 - lift) for (int k = 0; k < 3; k++) l.set(x + k, y, 'g');
            }
            for (int k = -1; k <= 2; k++)
            {
                l.set(x + k, 31 - lift, 'q');
                l.set(x + k, 32 - lift, 'q');
            }
            part(g, l);
        }
        // armoured torso and kilt
        part(g, PixelArt.shape(16, 13,
            "RRRRRRRRRRR",
            "RYRRRrRRRYR",
            "RRRYRRRYRRR",
            "RYRRRYRRRYR",
            "RRRYRRRYRRR",
            "GGGGGGGGGGG"));
        part(g, PixelArt.shape(16, 19, "RYRYRYRYRYR", "RRYRRYRRYRR", ".RRYRRYRRR.", "..RRYRYRR.."));
        // head: white helmet, red crest, small horns, tan face
        part(g, PixelArt.shape(16, 2,
            "...rrRRR...",
            "..rRRRRRR..",
            ".RRRRRRRRR."));
        part(g, PixelArt.shape(16, 5,
            "WWWWWWWWWWW",
            "WWWWWWWWWWW",
            "WWWSSSSSWWW",
            ".WSeSSeSSW.",
            ".WSSSSSSSW.",
            "..WSSSSSW.."));
        part(g, PixelArt.shape(14, 3, "B.", "BB"));
        part(g, PixelArt.shape(27, 3, ".B", "BB"));
        // the big round shield
        Grid sh = new Grid();
        for (int y = 0; y < sh.h; y++)
        {
            for (int x = 0; x < sh.w; x++)
            {
                double nx = (x - 12.5) / 5.5;
                double ny = (y - (20 + bob)) / 8.0;
                double d = nx * nx + ny * ny;
                if (d > 1.0) continue;
                sh.set(x, y, d > 0.72 ? 'G' : (nx + ny > 0.9 ? 'I' : 'H'));
            }
        }
        sh.pxs('Z', 12, 18 + bob, 12, 19 + bob, 12, 20 + bob, 11, 19 + bob, 13, 19 + bob);
        sh.pxs('R', 10, 15 + bob, 14, 15 + bob, 10, 23 + bob, 14, 23 + bob, 12, 16 + bob);
        sh.pxs('Y', 12, 21 + bob, 11, 22 + bob, 13, 22 + bob);
        part(g, sh);
        // hand gripping the spear below the shield
        part(g, ell(5.5, 29.5 + bob, 2.2, 2, 'S', 'S', 's'));
        return g;
    }

    private static final String ZEBAK_PAL =
        "k10201a g4aa84e G86d25c d2a6a38 ydcec98 Yf2c93a oa97a1c wffffff r9a2434 effe44a";

    private static void croc(Grid g, int x, int lift, char c)
    {
        Grid l = new Grid();
        for (int y = 27; y <= 30 - lift; y++)
        {
            l.set(x, y, c);
            l.set(x + 1, y, c);
            l.set(x + 2, y, 'd');
        }
        int fy = 31 - lift;
        for (int i = -1; i <= 2; i++) l.set(x + i, fy, 'd');
        l.set(x - 1, fy + 1, 'w');
        l.set(x + 1, fy + 1, 'w');
        part(g, l);
    }

    private static Grid zebak(int f)
    {
        Grid g = new Grid();
        // far legs (shaded), tail, back spikes
        croc(g, 17, f == 0 ? 1 : 0, 'd');
        croc(g, 28, f == 0 ? 0 : 1, 'd');
        Grid tail = new Grid();
        line(tail, 26, 23, 29, 25, 'g', 4);
        line(tail, 29, 25, 31, 23 - f, 'g', 2);
        underShade(tail, 'd');
        part(g, tail);
        for (int x = 14; x <= 26; x += 3) part(g, PixelArt.shape(x, 18, "d", "d"));
        // long body
        Grid body = ell(19.5, 24.5, 9, 4.5, 'g', 'G', 'd');
        for (int y = 0; y < body.h; y++)
        {
            for (int x = 0; x < body.w; x++)
            {
                if (body.px[y][x] != 0 && y >= 27) body.px[y][x] = 'y';
            }
        }
        body.pxs('d', 16, 22, 20, 21, 24, 22, 27, 23);
        part(g, body);
        // skull, eye bump
        part(g, ell(10.5, 23.5, 4, 3.3, 'g', 'G', 'd'));
        part(g, PixelArt.shape(10, 19, "gg", "ee"));
        // long jaws: upper with hanging teeth, lower with teeth pointing up, red mouth between
        part(g, PixelArt.shape(4, 21,
            "GGGGGGGG",
            "gggggggg",
            "dddddddd",
            ".w.w.w.w"));
        part(g, PixelArt.shape(5, 26,
            "w.w.w.w",
            "gggggg.",
            "dddddd."));
        for (int x = 6; x <= 11; x++) g.set(x, 25, 'r');
        g.pxs('k', 4, 21);
        // gold collar
        part(g, PixelArt.shape(12, 20, "YY", "oY", "YY", "Yo", "YY", "oY", "YY", "Yo"));
        // near legs
        croc(g, 13, f == 0 ? 0 : 1, 'g');
        croc(g, 24, f == 0 ? 1 : 0, 'g');
        return g;
    }

    private static final String KEPHRI_PAL =
        "k080a18 n1c2a58 H3a54a0 N0e1330 Yf2c93a yb88a1c hfff0a0 L34468c l1c2650 wf6f6f6 e202040 m6a4a2a M8a6638 u3e2812 sc8a860";

    private static int ballY(double x)
    {
        double dx = x - 24;
        return (int) Math.round(22.5 - Math.sqrt(Math.max(0, 9.5 * 9.5 - dx * dx)));
    }

    private static Grid kephri(int f)
    {
        Grid g = new Grid();
        // the dung ball with a rolling texture
        Grid ball = ell(24, 22.5, 9.5, 9.5, 'm', 'M', 'u');
        int[][] spots = {{20, 17}, {27, 16}, {31, 21}, {17, 23}, {24, 25}, {29, 28}, {20, 29}, {26, 20}, {22, 22}};
        for (int i = 0; i < spots.length; i++)
        {
            int sx = 14 + (spots[i][0] - 14 + f * 2) % 20;
            int sy = spots[i][1];
            if (ball.has(sx, sy)) ball.px[sy][sx] = i % 3 == 0 ? 'u' : i % 3 == 1 ? 'M' : 's';
            if (ball.has(sx + 1, sy)) ball.px[sy][sx + 1] = i % 2 == 0 ? 'u' : 'm';
        }
        part(g, ball);
        // legs resting on the ball
        int[] lxs = {17, 19, 27, 29};
        for (int i = 0; i < 4; i++)
        {
            boolean lift = i % 2 == f;
            int tx = lxs[i];
            Grid leg = new Grid();
            line(leg, tx + (i < 2 ? 1 : 0), 12, tx + (i < 2 ? -1 : 1), ballY(tx) + (lift ? 0 : 1), 'L', 2);
            part(g, leg);
        }
        // beetle perched on top
        Grid shell = ell(22.5, 9, 6.5, 5, 'n', 'H', 'N');
        for (int y = 0; y < shell.h; y++)
        {
            for (int x = 0; x < shell.w; x++)
            {
                if (shell.px[y][x] != 0 && y >= 12) shell.px[y][x] = x > 24 ? 'y' : 'Y';
            }
        }
        for (int y = 5; y <= 12; y++)
        {
            if (shell.has(21, y)) shell.px[y][21] = 'Y';
        }
        shell.pxs('H', 18, 6, 19, 6);
        part(g, shell);
        Grid horn = new Grid();
        line(horn, 14, 8, 12, 4, 'Y', 2);
        horn.set(12, 4, 'h');
        part(g, horn);
        part(g, ell(15.5, 10.5, 3.6, 3.2, 'L', 'H', 'n'));
        part(g, PixelArt.shape(10, 12, "Y.", "YY"));
        part(g, PixelArt.shape(13, 9, "ww", "ee"));
        return g;
    }

    private static final String BABA_PAL =
        "k24160e f7d7468 FA0968a d4a443c bE4DCC4 BB8AE92 ra86a6a Pc88a8a wf6f6f6 eff4040 m3a2a22";

    private static Grid baba(int f)
    {
        Grid g = new Grid();
        int nearX = f == 0 ? 11 : 16;
        int farX = f == 0 ? 17 : 12;
        int hindNear = f == 0 ? 23 : 28;
        int hindFar = f == 0 ? 28 : 23;
        // far arm and far hind leg (darker, bony forearm)
        Grid farArm = new Grid();
        line(farArm, 19, 17, farX, 28, 'd', 3);
        for (int y = 24; y < farArm.h; y++)
        {
            for (int x = 0; x < farArm.w; x++)
            {
                if (farArm.px[y][x] != 0) farArm.px[y][x] = 'B';
            }
        }
        part(g, farArm);
        part(g, ell(farX + 1.5, 30.5, 2.8, 2, 'B', 'B', 'B'));
        Grid farLeg = new Grid();
        line(farLeg, 27, 22, hindFar + 1, 29, 'd', 3);
        part(g, farLeg);
        part(g, ell(hindFar + 2, 31, 3, 1.5, 'd', 'd', 'd'));
        // short ragged tail
        Grid tail = new Grid();
        line(tail, 31, 21, 33, 17, 'd', 2);
        part(g, tail);
        // hunched body: rump, then shoulders
        Grid rump = ell(25.5, 22.5, 6.5, 5.5, 'f', 'F', 'd');
        rump.pxs('r', 27, 21, 28, 21, 28, 22, 29, 22, 28, 23, 29, 23, 30, 23, 29, 24);
        rump.pxs('b', 24, 18, 26, 17, 28, 17, 30, 18);
        part(g, rump);
        Grid sho = ell(19.5, 17.5, 6.5, 6, 'F', 'F', 'f');
        part(g, sho);
        // exposed rib cage on the shoulder
        for (int y = 14; y <= 21; y++)
        {
            for (int x = 17; x <= 23; x++)
            {
                if (!sho.has(x, y)) continue;
                double dx = (x - 20) / 3.6;
                double dy = (y - 17.5) / 4.2;
                if (dx * dx + dy * dy > 1.0) continue;
                g.set(x, y, (y % 2 == 0) ? 'b' : 'm');
            }
        }
        g.pxs('B', 20, 15, 20, 17, 20, 19, 20, 21);
        g.pxs('d', 17, 15, 17, 20, 23, 16, 23, 20);
        // near hind leg
        Grid leg = new Grid();
        line(leg, 26, 23, hindNear + 1, 29, 'f', 3);
        part(g, leg);
        part(g, ell(hindNear + 2, 31, 3.5, 1.5, 'd', 'd', 'd'));
        // near arm: fur on top, bone from the elbow down, big knuckle fist
        Grid arm = new Grid();
        line(arm, 18, 18, nearX + 1, 28, 'f', 4);
        for (int y = 23; y < arm.h; y++)
        {
            for (int x = 0; x < arm.w; x++)
            {
                if (arm.px[y][x] != 0) arm.px[y][x] = (y == 23) ? 'B' : (x % 2 == 0 ? 'b' : 'B');
            }
        }
        part(g, arm);
        part(g, ell(nearX + 2.5, 30.5, 3.4, 2.4, 'B', 'b', 'B'));
        // head: skull showing on the crown, rotten muzzle with bare teeth and jaw
        part(g, PixelArt.shape(16, 8, ".f.", "fbf", ".f."));
        Grid head = ell(12.5, 14.5, 5.5, 5, 'f', 'F', 'd');
        head.pxs('b', 13, 10, 14, 10, 15, 11, 14, 11, 15, 12, 16, 12);
        part(g, head);
        part(g, ell(8.5, 17.5, 3.8, 3, 'r', 'P', 'r'));
        part(g, PixelArt.shape(5, 20, "w.w.w.w", "bbbbbbb"));
        g.pxs('m', 6, 16, 7, 16);
        // sunken eye socket with a red glint
        part(g, PixelArt.shape(10, 12, "mmm", "mem", "mmm"));
        g.pxs('d', 9, 11, 10, 11, 11, 11, 12, 11);
        return g;
    }

    private static final String WARDEN_PAL =
        "k1a1620 Saaa498 hd0c8b0 s766e62 Yf2c93a Ge0a82c B2a5aa8 e40f0e0 d5a5448 u0a5a5c U1f6f74 V3ad0c8";

    private static void blit(Grid dst, Grid src, int dy)
    {
        for (int y = 0; y < src.h; y++)
        {
            for (int x = 0; x < src.w; x++)
            {
                if (src.px[y][x] != 0) dst.set(x, y + dy, src.px[y][x]);
            }
        }
    }

    private static Grid warden(int f)
    {
        Grid body = new Grid();
        int ab = f;
        // tapering lower body: no legs, it floats
        part(body, PixelArt.shape(15, 22,
            "YGYGYGYGYGYGY",
            ".GYGYGYGYGYG.",
            "..YGYGYGYGY..",
            "...GYGYGYG...",
            "....GYGYG....",
            ".....GYG....."));
        warm(body, 12, 14 + ab);
        warm(body, 28, 14 + (1 - ab));
        Grid torso = rect(16, 14, 11, 8, 'S');
        for (int y = 14; y <= 21; y++)
        {
            torso.set(16, y, 'h');
            torso.set(26, y, 's');
        }
        for (int x = 16; x <= 26; x++) torso.set(x, 21, 'Y');
        torso.pxs('d', 17, 19, 18, 19, 24, 19, 25, 19, 17, 15, 25, 15);
        part(body, torso);
        part(body, PixelArt.shape(14, 12, "YGYGYGYGYGYGYGY", "GYGYGYGYGYGYGYG"));
        PixelArt.part(body, PixelArt.shape(19, 15, "..T..", ".TWT.", "TWWWT", ".TWT.", "..T.."), 'u');
        part(body, PixelArt.shape(14, 3,
            "...GBGBGBGBG...",
            "..BGBGBGBGBGB..",
            "..GBSSSSSSSBG..",
            "..BGSeSSeSSGB..",
            "..GBSSSdSSSBG..",
            ".BGBSSSSSSSBGB.",
            ".GBGSSddSSSGBG.",
            "BGBGSSSSSSSGBGB",
            "GBGBGGGGGGGBGBG"));
        body.pxs('Y', 21, 5);
        Grid g = new Grid();
        // faint glow on the floor and loose stones circling underneath
        ell(g, 21.5, 32, 7.5, 1.5, 'U', 'U', 'U');
        g.pxs('V', 19, 32, 20, 32, 21, 32, 22, 32, 23, 32);
        int sw = f == 0 ? 0 : 1;
        part(g, PixelArt.shape(14 + sw, 29, "Sh", "sS"));
        part(g, PixelArt.shape(28 - sw, 28 + sw, "Sh", "sS"));
        part(g, PixelArt.shape(21 + sw, 30, "hS"));
        blit(g, body, f == 1 ? -1 : 0);
        return g;
    }

    private static void warm(Grid g, int x, int y)
    {
        Grid l = new Grid();
        for (int j = 0; j < 9; j++)
        {
            l.set(x, y + j, 'h');
            l.set(x + 1, y + j, 'S');
            l.set(x + 2, y + j, 's');
        }
        for (int i = 0; i < 3; i++) l.set(x + i, y + 5, 'Y');
        part(g, l);
    }

    // ---------------------------------------------------------------- falling things

    private static BufferedImage makeFall(Critter.Fall f, int v)
    {
        switch (f)
        {
            case BLOOD_DROP:
            {
                Grid g = v == 0 ? new Grid(14, 14) : new Grid(18, 18);
                Grid d = v == 0 ? drop(g, 3, 2, 7, 9, 'R', 'H', 'S') : drop(g, 3, 2, 11, 14, 'R', 'H', 'S');
                PixelArt.part(g, d, 'k');
                return PixelArt.render(g, pal("k3a0810 Rc81e2a Hff7080 S8a1020"));
            }
            case WATER_DROP:
            {
                Grid g = new Grid(16, 16);
                PixelArt.part(g, drop(g, 3, 2, 9, 12, 'R', 'H', 'S'), 'k');
                return PixelArt.render(g, pal("k0a2050 R3a8ef0 Hd0f0ff S1e56b8"));
            }
            case CRYSTAL:
            {
                Grid g = new Grid(16, 16);
                if (v == 0)
                {
                    Grid s = new Grid(16, 16).stamp(4, 1,
                        "...HH...",
                        "..HWgG..",
                        ".HWHggG.",
                        ".HHggggG",
                        ".HHgggGG",
                        ".HHgggGG",
                        ".HHgggGG",
                        ".HHgggGG",
                        ".HHgggGG",
                        "..HgggG.",
                        "..HgggG.",
                        "...ggG..",
                        "...gG...",
                        "....G...");
                    PixelArt.part(g, s, 'k');
                }
                else
                {
                    Grid s = new Grid(16, 16).stamp(5, 3,
                        "..HH..",
                        ".HWgG.",
                        ".HHggG",
                        ".HggGG",
                        ".HggGG",
                        ".HggGG",
                        "..HgG.",
                        "..ggG.",
                        "...gG.",
                        "...G..");
                    PixelArt.part(g, s, 'k');
                }
                return PixelArt.render(g, pal("k063a38 H90ffe0 Wffffff g28c8a0 G108070"));
            }
            default:
            {
                Grid g = new Grid(16, 16);
                Grid ball = ell(new Grid(16, 16), 7.5, 9.5, 5, 5, 'm', 'M', 'n');
                ball.pxs('n', 9, 12, 10, 12, 5, 11, 8, 8, 10, 10);
                ball.pxs('M', 6, 7, 5, 8, 7, 9);
                Grid stem = new Grid(16, 16);
                stem.pxs('u', 9, 4, 10, 3);
                PixelArt.part(g, stem, 'k');
                PixelArt.part(g, ball, 'k');
                g.pxs('u', 9, 4, 10, 3);
                g.pxs('F', 11, 3, 10, 2, 12, 2, 11, 1);
                g.pxs('E', 11, 2);
                g.pxs('F', 13, 1, 13, 3);
                return PixelArt.render(g, pal("k2a1808 m6a4a2a M9a7444 n3e2812 u5a3a18 FFF9A1F EFFE066"));
            }
        }
    }

    /** A teardrop: a point at the top, a round belly, shaded from the top left. */
    private static Grid drop(Grid g, int x0, int y0, int w, int h, char base, char hi, char sh)
    {
        Grid m = new Grid(g.w, g.h);
        double r = w / 2.0;
        double cx = x0 + (w - 1) / 2.0;
        double cy = y0 + h - 1 - (r - 0.5);
        for (int y = y0; y < y0 + h; y++)
        {
            for (int x = x0; x < x0 + w; x++)
            {
                double dx = x - cx;
                double dy = y - cy;
                boolean inCircle = Math.sqrt(dx * dx + dy * dy) <= r;
                boolean inTri = y <= cy && Math.abs(dx) <= (y - y0) / Math.max(1.0, cy - y0) * r;
                if (!inCircle && !inTri) continue;
                m.set(x, y, dx * 0.7 + dy * 0.7 > r * 0.55 ? sh : base);
            }
        }
        int hx = (int) Math.round(cx - r * 0.45);
        int hy = (int) Math.round(cy - r * 0.2);
        m.set(hx, hy, hi);
        m.set(hx, hy + 1, hi);
        if (w > 8) m.set(hx + 1, hy - 1, hi);
        return m;
    }
}
