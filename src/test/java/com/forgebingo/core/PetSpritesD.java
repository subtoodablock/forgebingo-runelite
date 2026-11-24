package com.forgebingo.core;

import com.forgebingo.core.PixelArt.Grid;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pixel art for some of the pets (see Pet): Kalphite Princess, Lil' Zik, Lil'viathan, Little Nightmare, Maggot marquess,
 * Moxi, Muphin, Nexling, Nid, Noon, Olmlet, Orion, Pet chaos elemental and Pet Dagannoth Prime. Original chunky chibi art
 * in the style of PetSpritesB: big head, 1px dark outline, 2-3 shades per colour, two walk frames. Facing LEFT, feet on
 * the bottom rows, body centred on column 21. Returns null for a pet this file does not draw.
 */
public final class PetSpritesD
{
    private static final Map<String, BufferedImage> CACHE = new ConcurrentHashMap<>();

    private PetSpritesD()
    {
    }

    /** One walk frame (0 or 1) on the PixelArt.WIDTH x PixelArt.HEIGHT grid, facing left, feet on the bottom rows. */
    public static BufferedImage image(Pet pet, int frame)
    {
        if (pet == null) return null;
        switch (pet)
        {
            case KALPHITE_PRINCESS: case LIL_ZIK: case LILVIATHAN: case LITTLE_NIGHTMARE: case MAGGOT_MARQUESS:
            case MOXI: case MUPHIN: case NEXLING: case NID: case NOON: case OLMLET: case ORION:
            case CHAOS_ELEMENTAL: case DAGANNOTH_PRIME:
                break;
            default:
                return null;
        }
        int f = frame == 0 ? 0 : 1;
        return CACHE.computeIfAbsent(pet + "|" + f, k -> PixelArt.render(build(pet, f), PetSpritesD::colour));
    }

    private static int colour(int c)
    {
        switch ((char) c)
        {
            case 'k': return 0x16140f; // outline
            case 'z': return 0x1d1a22; // darkest
            case 'x': return 0xfff6dc; // highlight white
            case 'c': return 0x55555e; // stone grey
            case 'C': return 0x82828e;
            case 'u': return 0x33333b;
            case 'l': return 0x3a8c94; // teal armour
            case 'L': return 0x1f5a68;
            case 'm': return 0x6cc0c0;
            case 'g': return 0x4f9a3c; // green
            case 'G': return 0x86d05e;
            case 'H': return 0x2c6030;
            case 't': return 0xdcbc84; // tan
            case 'T': return 0xf2e0b0;
            case 's': return 0xa88650;
            case 'r': return 0x8a5a36; // brown
            case 'R': return 0x4e301c;
            case 'j': return 0xb88252;
            case 'D': return 0x6a2a28; // maroon
            case 'a': return 0xd8342a; // red
            case 'A': return 0xff7c50;
            case 'o': return 0xf0a020; // orange
            case 'O': return 0xffd460;
            case 'Q': return 0xb86418;
            case 'y': return 0xf8d838; // yellow
            case 'Y': return 0xfff48a;
            case 'v': return 0xb8a0d8; // lilac
            case 'V': return 0xdcd0f0;
            case 'N': return 0x7a62a0;
            case 'P': return 0x4a2868; // dark purple
            case 'p': return 0x7a4aa0;
            case 'q': return 0xa87ccc;
            case 'h': return 0xff5fb0; // pink
            case 'K': return 0xb02070;
            case 'W': return 0xffa0d0;
            case 'f': return 0xa8c8e0; // ice
            case 'F': return 0xdff0fa;
            case 'S': return 0x6c90b0;
            case 'I': return 0x3c5878;
            case 'e': return 0xe6dcc4; // bone
            case 'E': return 0xb0a488;
            case 'b': return 0x6a7a96; // blue-grey hood
            case 'B': return 0x3a4660;
            case 'w': return 0xa8bad4;
            case 'U': return 0xf2f6fc; // white
            case 'X': return 0xc4ccdc;
            case 'Z': return 0x8c94ac;
            case 'n': return 0x6f9670; // grey-green
            case 'M': return 0xa6c890;
            case 'J': return 0x3e6048;
            case 'd': return 0x7a4a24; // prime brown
            case 'i': return 0x4a2a14;
            default: return 0xff00ff;
        }
    }

    // ---------------------------------------------------------------- helpers

    private static Grid rect(int x, int y, int w, int h, char c)
    {
        Grid m = new Grid();
        for (int j = 0; j < h; j++)
        {
            for (int i = 0; i < w; i++) m.set(x + i, y + j, c);
        }
        return m;
    }

    private static Grid oval(int x, int y, int w, int h, char c)
    {
        Grid m = new Grid();
        double cx = x + w / 2.0;
        double cy = y + h / 2.0;
        for (int j = 0; j < h; j++)
        {
            for (int i = 0; i < w; i++)
            {
                double dx = (x + i + 0.5 - cx) / (w / 2.0);
                double dy = (y + j + 0.5 - cy) / (h / 2.0);
                if (dx * dx + dy * dy <= 1.0) m.set(x + i, y + j, c);
            }
        }
        return m;
    }

    private static Grid trap(int cx, int y0, int y1, double hw0, double hw1, char c)
    {
        Grid m = new Grid();
        for (int y = y0; y <= y1; y++)
        {
            double t = y1 == y0 ? 0 : (double) (y - y0) / (y1 - y0);
            int hw = (int) Math.round(hw0 + (hw1 - hw0) * t);
            for (int x = cx - hw; x < cx + hw; x++) m.set(x, y, c);
        }
        return m;
    }

    private static Grid ellipseRot(double cx, double cy, double a, double b, double deg, char c)
    {
        Grid m = new Grid();
        double r = Math.toRadians(deg);
        double cs = Math.cos(r);
        double sn = Math.sin(r);
        for (int y = 0; y < m.h; y++)
        {
            for (int x = 0; x < m.w; x++)
            {
                double dx = x + 0.5 - cx;
                double dy = y + 0.5 - cy;
                double u = dx * cs + dy * sn;
                double v = -dx * sn + dy * cs;
                if (u * u / (a * a) + v * v / (b * b) <= 1) m.set(x, y, c);
            }
        }
        return m;
    }

    /** A filled polygon through the x, y corner points (y shifted by dy). */
    private static Grid poly(char c, int dy, int... p)
    {
        Grid m = new Grid();
        int n = p.length / 2;
        for (int y = 0; y < m.h; y++)
        {
            for (int x = 0; x < m.w; x++)
            {
                double px = x + 0.5;
                double py = y + 0.5;
                boolean in = false;
                for (int i = 0, j = n - 1; i < n; j = i++)
                {
                    double xi = p[2 * i];
                    double yi = p[2 * i + 1] + dy;
                    double xj = p[2 * j];
                    double yj = p[2 * j + 1] + dy;
                    if ((yi > py) != (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi) in = !in;
                }
                if (in) m.set(x, y, c);
            }
        }
        return m;
    }

    /** Light on the top-left edge, dark on the bottom-right edge (0 to skip one). */
    private static Grid bevel(Grid m, char light, char dark)
    {
        char[][] src = new char[m.h][];
        for (int j = 0; j < m.h; j++) src[j] = m.px[j].clone();
        for (int j = 0; j < m.h; j++)
        {
            for (int i = 0; i < m.w; i++)
            {
                if (src[j][i] == 0) continue;
                boolean top = j == 0 || src[j - 1][i] == 0;
                boolean left = i == 0 || src[j][i - 1] == 0;
                boolean bottom = j == m.h - 1 || src[j + 1][i] == 0;
                boolean right = i == m.w - 1 || src[j][i + 1] == 0;
                if (dark != 0 && (bottom || right)) m.px[j][i] = dark;
                else if (light != 0 && (top || left)) m.px[j][i] = light;
            }
        }
        return m;
    }

    private static Grid with(Grid a, Grid b)
    {
        for (int j = 0; j < b.h; j++)
        {
            for (int i = 0; i < b.w; i++)
            {
                if (b.px[j][i] != 0) a.set(i, j, b.px[j][i]);
            }
        }
        return a;
    }

    private static void part(Grid g, Grid fill)
    {
        PixelArt.part(g, fill, 'k');
    }

    private static void line(Grid m, int x0, int y0, int x1, int y1, char c)
    {
        int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        if (n == 0)
        {
            m.set(x0, y0, c);
            return;
        }
        for (int i = 0; i <= n; i++)
        {
            m.set((int) Math.round(x0 + (x1 - x0) * (double) i / n), (int) Math.round(y0 + (y1 - y0) * (double) i / n), c);
        }
    }

    /** A 2px thick polyline through the given x, y points. */
    private static void thick(Grid m, char c, int... p)
    {
        for (int i = 0; i + 3 < p.length; i += 2)
        {
            line(m, p[i], p[i + 1], p[i + 2], p[i + 3], c);
            line(m, p[i] + 1, p[i + 1], p[i + 2] + 1, p[i + 3], c);
            line(m, p[i], p[i + 1] + 1, p[i + 2], p[i + 3] + 1, c);
            line(m, p[i] + 1, p[i + 1] + 1, p[i + 2] + 1, p[i + 3] + 1, c);
        }
    }

    /** Replaces every filled pixel in column x of the part with c. */
    private static void vstripe(Grid m, int x, char c)
    {
        for (int y = 0; y < m.h; y++)
        {
            if (m.has(x, y)) m.px[y][x] = c;
        }
    }

    private static int lift(int i, int f)
    {
        return ((i % 2 == 0) == (f == 1)) ? 1 : 0;
    }

    /** Four legs, diagonal pairs alternate: a lifted leg is one pixel shorter. Legs end on row 32. */
    private static void legs4(Grid g, int[] xs, int top, int w, char c, char foot, char shade, int f)
    {
        for (int i = 0; i < xs.length; i++)
        {
            int l = lift(i, f);
            Grid leg = bevel(rect(xs[i], top, w, 33 - top - l, c), (char) 0, shade);
            if (foot != 0) with(leg, rect(xs[i], 32 - l, w, 1, foot));
            part(g, leg);
        }
    }

    private static Grid build(Pet pet, int f)
    {
        switch (pet)
        {
            case KALPHITE_PRINCESS: return princess(f);
            case LIL_ZIK: return zik(f);
            case LILVIATHAN: return leviathan(f);
            case LITTLE_NIGHTMARE: return nightmare(f);
            case MAGGOT_MARQUESS: return maggot(f);
            case MOXI: return moxi(f);
            case MUPHIN: return muphin(f);
            case NEXLING: return nexling(f);
            case NID: return nid(f);
            case NOON: return noon(f);
            case OLMLET: return olmlet(f);
            case ORION: return orion(f);
            case CHAOS_ELEMENTAL: return chaos(f);
            default: return prime(f);
        }
    }

    // ---------------------------------------------------------------- pets

    /** Kalphite Princess: green and tan beetle with pale wings, antennae, little mandibles and a sting. */
    private static Grid princess(int f)
    {
        Grid g = new Grid();
        int dy = 3 - f;
        Grid w = new Grid();
        with(w, ellipseRot(24, 12 + dy, 8, 3.0, f == 0 ? -18 : -34, 'm'));
        with(w, ellipseRot(22, 14 + dy, 6, 2.2, f == 0 ? -8 : -22, 'x'));
        bevel(w, 'x', 'm');
        part(g, w);
        int[] xs = {11, 17, 24};
        for (int i = 0; i < 3; i++)
        {
            part(g, bevel(rect(xs[i], 28, 2, 5 - lift(i, f), 'r'), 'j', 'R'));
        }
        part(g, new Grid().stamp(30, 19 + dy, "oo", ".o"));
        Grid ab = bevel(oval(16, 14 + dy, 15, 12, 'g'), 'G', 'H');
        vstripe(ab, 20, 'H');
        vstripe(ab, 24, 'H');
        vstripe(ab, 28, 'H');
        part(g, ab);
        part(g, bevel(oval(11, 15 + dy, 8, 9, 't'), 'T', 's'));
        part(g, bevel(oval(5, 12 + dy, 10, 10, 't'), 'T', 's'));
        g.pxs('g', 7, 12 + dy, 8, 12 + dy, 9, 12 + dy, 10, 12 + dy, 11, 12 + dy);
        part(g, new Grid().stamp(4, 20 + dy, "e.", "ee", ".e"));
        g.pxs('z', 6, 15 + dy, 7, 15 + dy, 6, 16 + dy, 7, 16 + dy).pxs('x', 6, 15 + dy);
        g.pxs('h', 6, 19 + dy);
        Grid an = new Grid();
        line(an, 8, 11 + dy, 6, 6 + dy, 'R');
        line(an, 11, 11 + dy, 13, 6 + dy, 'R');
        part(g, an);
        g.pxs('y', 6, 6 + dy, 13, 6 + dy);
        return g;
    }

    /** Lil' Zik: tiny pale lilac vampire lady, dark swept hair, horn points, red eyes, dark dress. */
    private static Grid zik(int f)
    {
        Grid g = new Grid();
        int dy = -f;
        for (int i = 0; i < 2; i++)
        {
            int x = i == 0 ? 16 : 23;
            part(g, bevel(rect(x, 28, 4, 5 - lift(i, f), 'N'), (char) 0, 'P'));
        }
        part(g, bevel(trap(21, 19 + dy, 28, 5, 7, 'P'), 'p', 'z'));
        g.pxs('p', 18, 24 + dy, 24, 25 + dy, 21, 26 + dy);
        part(g, bevel(oval(12, 20 + dy, 4, 6, 'v'), 'V', 'N'));
        part(g, bevel(oval(27, 20 + dy, 4, 6, 'v'), 'V', 'N'));
        Grid hair = poly('z', dy, 10, 13, 10, 9, 13, 7, 28, 7, 32, 11, 33, 18, 33, 24, 30, 22, 29, 14);
        bevel(hair, 'P', (char) 0);
        part(g, hair);
        part(g, poly('E', dy, 12, 10, 11, 5, 16, 9));
        part(g, poly('E', dy, 25, 9, 30, 5, 30, 11));
        part(g, bevel(oval(11, 10 + dy, 19, 11, 'v'), 'V', 'N'));
        Grid fr = poly('z', dy, 10, 13, 10, 10, 14, 8, 27, 8, 31, 12, 29, 14, 25, 11, 20, 12, 15, 13, 13, 15);
        bevel(fr, 'P', (char) 0);
        part(g, fr);
        g.pxs('a', 14, 14 + dy, 15, 14 + dy, 14, 15 + dy, 15, 15 + dy, 22, 14 + dy, 23, 14 + dy, 22, 15 + dy, 23, 15 + dy);
        g.pxs('A', 14, 14 + dy, 22, 14 + dy);
        g.pxs('h', 12, 17 + dy, 27, 17 + dy);
        g.pxs('z', 18, 18 + dy, 19, 18 + dy, 20, 18 + dy).pxs('x', 18, 19 + dy);
        return g;
    }

    /** Lil'viathan: orange sea-serpent dragon with a pale belly, teal fins and a big toothy head. */
    private static Grid leviathan(int f)
    {
        Grid g = new Grid();
        int dy = -f;
        Grid tail = new Grid();
        thick(tail, 'o', 27, 22 + dy, 29, 22 + dy, 30, 18 + f * 2);
        with(tail, poly('b', f * 2, 29, 13, 32, 16, 30, 20));
        part(g, tail);
        legs4(g, new int[] {11, 14, 22, 26}, 26, 3, 'o', 'Q', 'Q', f);
        Grid fins = new Grid();
        for (int x = 14; x <= 26; x += 4) with(fins, poly('b', dy, x, 17, x + 2, 12, x + 4, 17));
        part(g, bevel(fins, 'B', 'b'));
        part(g, bevel(oval(12, 16 + dy, 18, 12, 'o'), 'O', 'Q'));
        part(g, bevel(oval(14, 22 + dy, 14, 5, 'T'), 'x', 's'));
        g.pxs('Q', 18, 19 + dy, 22, 20 + dy, 26, 19 + dy, 20, 21 + dy, 25, 21 + dy);
        part(g, bevel(poly('b', dy, 12, 12, 16, 10, 17, 16, 13, 18), 'B', 'b'));
        part(g, bevel(oval(6, 10 + dy, 11, 12, 'o'), 'O', 'Q'));
        part(g, bevel(oval(4, 15 + dy, 8, 6, 'o'), 'O', 'Q'));
        part(g, bevel(oval(5, 19 + dy, 9, 4, 'T'), 'x', 's'));
        g.pxs('z', 5, 16 + dy, 6, 16 + dy);
        g.pxs('z', 5, 19 + dy, 6, 19 + dy, 7, 19 + dy, 8, 19 + dy, 9, 19 + dy, 10, 19 + dy);
        g.pxs('x', 6, 20 + dy, 9, 20 + dy);
        g.pxs('y', 9, 12 + dy, 10, 12 + dy, 9, 13 + dy, 10, 13 + dy).pxs('z', 10, 13 + dy).pxs('Y', 9, 12 + dy);
        return g;
    }

    /** Little Nightmare: hooded ghost lady, pale face with glowing pink eyes, long pale hair, floating. */
    private static Grid nightmare(int f)
    {
        Grid g = new Grid();
        int dy = -f;
        part(g, bevel(poly('e', dy, 14, 11, 9, 15, 8, 28, 14, 28), 'x', 'E'));
        part(g, bevel(poly('e', dy, 28, 11, 33, 15, 34, 28, 28, 28), 'x', 'E'));
        g.pxs('E', 10 + f, 20 + dy, 10, 24 + dy, 32 - f, 21 + dy, 32, 25 + dy);
        Grid robe = bevel(trap(21, 17 + dy, 28 + dy, 7, 8, 'b'), 'w', 'B');
        for (int x = 12; x < 30; x++)
        {
            if ((x + f) % 3 == 0) robe.set(x, 28 + dy, (char) 0);
            if ((x + f) % 6 == 0) robe.set(x, 27 + dy, (char) 0);
        }
        part(g, robe);
        part(g, bevel(oval(11, 2 + dy, 20, 18, 'b'), 'w', 'B'));
        part(g, bevel(oval(14, 8 + dy, 14, 11, 'F'), (char) 0, 'w'));
        for (int x = 14; x <= 27; x++) g.set(x, 8 + dy, 'e');
        g.pxs('e', 14, 9 + dy, 15, 9 + dy, 16, 9 + dy, 25, 9 + dy, 26, 9 + dy, 27, 9 + dy, 14, 10 + dy, 14, 11 + dy, 27, 10 + dy,
            27, 11 + dy);
        g.pxs('E', 20, 8 + dy, 21, 9 + dy);
        g.pxs('h', 16, 12 + dy, 17, 12 + dy, 16, 13 + dy, 17, 13 + dy, 24, 12 + dy, 25, 12 + dy, 24, 13 + dy, 25, 13 + dy);
        g.pxs('W', 16, 12 + dy, 24, 12 + dy);
        g.pxs('K', 16, 14 + dy, 17, 14 + dy, 24, 14 + dy, 25, 14 + dy, 20, 16 + dy, 21, 16 + dy);
        return g;
    }

    /** Maggot marquess: fat segmented tan grub in a dark red-brown hood with a tiny gold crown. */
    private static Grid maggot(int f)
    {
        Grid g = new Grid();
        for (int i = 0; i < 4; i++)
        {
            part(g, bevel(rect(12 + i * 5, 27, 3, 6 - lift(i, f), 'r'), (char) 0, 'R'));
        }
        Grid body = bevel(oval(9, 15 - f, 21, 13 + f, 't'), 'T', 's');
        for (int x = 13 + f; x < 29; x += 4) vstripe(body, x, 's');
        part(g, body);
        g.pxs('T', 25, 18 - f, 26, 18 - f);
        part(g, bevel(poly('D', 0, 7, 13, 11, 7, 15, 13), 'r', 'R'));
        part(g, bevel(oval(5, 11, 13, 16, 'D'), 'r', 'R'));
        part(g, new Grid().stamp(10, 5, "y.y", "yYy"));
        part(g, bevel(oval(4, 15, 10, 10, 't'), 'T', 's'));
        g.pxs('z', 6, 18, 7, 18, 6, 19, 7, 19, 10, 18, 11, 18, 10, 19, 11, 19);
        g.pxs('x', 6, 18, 10, 18);
        g.pxs('z', 6, 22, 7, 22, 8, 22, 9, 22).pxs('x', 6, 23, 9, 23);
        g.pxs('h', 5, 21, 12, 21);
        return g;
    }

    /** Moxi: pale blue icy spirit wisp with a yellow glow in its tummy, floating. */
    private static Grid moxi(int f)
    {
        Grid g = new Grid();
        int dy = -f;
        part(g, bevel(poly('f', dy, 24 + f, 23, 29 + f, 22, 32 + f, 19, 31 + f, 26, 27 + f, 28), 'F', 'S'));
        part(g, bevel(oval(21 + f, 21 + dy, 8, 7, 'f'), 'F', 'S'));
        part(g, bevel(oval(16, 17 + dy, 11, 9, 'f'), 'F', 'S'));
        part(g, bevel(oval(11, 11 + dy, 12, 11, 'f'), 'F', 'S'));
        part(g, bevel(poly('F', dy, 8, 7, 7, 2, 12, 6), 'x', 'f'));
        part(g, bevel(poly('F', dy, 11, 6, 14, 2, 17, 6), 'x', 'f'));
        part(g, bevel(poly('F', dy, 16, 7, 20, 3, 19, 9), 'x', 'f'));
        part(g, bevel(oval(5, 5 + dy, 15, 13, 'f'), 'F', 'S'));
        part(g, bevel(oval(9, 16 + dy, 5, 6, 'S'), 'f', 'I'));
        g.pxs('z', 8, 10 + dy, 9, 10 + dy, 8, 11 + dy, 9, 11 + dy, 14, 10 + dy, 15, 10 + dy, 14, 11 + dy, 15, 11 + dy);
        g.pxs('x', 8, 10 + dy, 14, 10 + dy);
        g.pxs('z', 11, 14 + dy, 12, 14 + dy, 13, 14 + dy);
        part(g, bevel(oval(16, 18 + dy, 5, 5, 'y'), 'Y', 'o'));
        return g;
    }

    /** Muphin: round teal-and-grey armoured blob, green crest, toothy face. */
    private static Grid muphin(int f)
    {
        Grid g = new Grid();
        int dy = -f;
        for (int i = 0; i < 2; i++)
        {
            int x = i == 0 ? 11 : 23;
            part(g, bevel(rect(x, 27, 6, 6 - lift(i, f), 'c'), 'C', 'u'));
        }
        Grid crest = new Grid();
        with(crest, poly('g', dy, 14, 11, 16, 5, 19, 11));
        with(crest, poly('g', dy, 19, 10, 22, 4, 25, 10));
        with(crest, poly('g', dy, 25, 11, 28, 6, 30, 12));
        part(g, bevel(crest, 'G', 'H'));
        Grid body = bevel(oval(8, 9 + dy, 25, 20, 'l'), 'm', 'L');
        for (int x = 22; x <= 25; x++) vstripe(body, x, x == 22 ? 'C' : 'c');
        vstripe(body, 29, 'C');
        vstripe(body, 30, 'c');
        for (int y = 13; y <= 25; y += 4) body.set(24, y + dy, 'u');
        part(g, body);
        part(g, rect(9, 19 + dy, 9, 4, 'z'));
        g.pxs('x', 10, 19 + dy, 12, 19 + dy, 14, 19 + dy, 16, 19 + dy, 11, 22 + dy, 13, 22 + dy, 15, 22 + dy, 17, 22 + dy);
        g.pxs('y', 11, 14 + dy, 12, 14 + dy, 11, 15 + dy, 12, 15 + dy, 16, 14 + dy, 17, 14 + dy, 16, 15 + dy, 17, 15 + dy);
        g.pxs('z', 11, 15 + dy, 16, 15 + dy).pxs('Y', 12, 14 + dy);
        g.pxs('L', 10, 12 + dy, 11, 12 + dy, 12, 12 + dy, 16, 12 + dy, 17, 12 + dy, 18, 12 + dy);
        return g;
    }

    /** Nexling: pale lilac winged figure, dark horns, cream robe. */
    private static Grid nexling(int f)
    {
        Grid g = new Grid();
        int dy = -f;
        int wy = f * 3;
        part(g, poly('N', dy, 22, 19, 23, 9 + wy, 27, 5 + wy, 28, 12 + wy, 28, 21));
        Grid wing = poly('v', dy, 24, 19, 26, 7 + wy, 30, 3 + wy, 33, 8 + wy, 33, 16 + wy, 30, 20);
        bevel(wing, 'V', 'N');
        line(wing, 26, 18 + dy, 30, 4 + wy + dy, 'N');
        line(wing, 28, 19 + dy, 33, 10 + wy + dy, 'N');
        part(g, wing);
        for (int i = 0; i < 2; i++)
        {
            int x = i == 0 ? 16 : 23;
            part(g, bevel(rect(x, 29, 4, 4 - lift(i, f), 'v'), (char) 0, 'N'));
        }
        part(g, bevel(trap(21, 19 + dy, 29, 6, 8, 'T'), 'x', 's'));
        for (int x = 15; x <= 26; x++) g.set(x, 23 + dy, 's');
        g.pxs('y', 20, 23 + dy);
        part(g, bevel(oval(12, 19 + dy, 5, 8, 'v'), 'V', 'N'));
        part(g, bevel(oval(26, 19 + dy, 5, 8, 'v'), 'V', 'N'));
        Grid horns = new Grid();
        thick(horns, 'P', 15, 10 + dy, 12, 7 + dy, 11, 4 + dy);
        thick(horns, 'P', 26, 10 + dy, 29, 7 + dy, 30, 4 + dy);
        part(g, horns);
        part(g, bevel(oval(13, 8 + dy, 16, 13, 'v'), 'V', 'N'));
        g.pxs('z', 16, 13 + dy, 17, 13 + dy, 16, 14 + dy, 17, 14 + dy, 24, 13 + dy, 25, 13 + dy, 24, 14 + dy, 25, 14 + dy);
        g.pxs('a', 16, 14 + dy, 24, 14 + dy);
        g.pxs('z', 20, 17 + dy, 21, 17 + dy);
        return g;
    }

    /** Nid: dark spider with chunky green legs, a red eye cluster, green fangs and venom. */
    private static Grid nid(int f)
    {
        Grid g = new Grid();
        int dy = -f;
        // ox, oy, kx, ky, fx : out from the body, then down to the foot
        int[][] legs = {{12, 21, 9, 24, 9}, {15, 23, 13, 27, 13}, {25, 23, 28, 27, 28}, {28, 21, 31, 24, 31}};
        for (int i = 0; i < 4; i++)
        {
            int l = lift(i, f);
            int[] p = legs[i];
            Grid leg = new Grid();
            thick(leg, 'H', p[0], p[1] + dy, p[2], p[3] + dy);
            thick(leg, 'g', p[2], p[3] + dy, p[4], 31 - l);
            leg.pxs('G', p[4], 30 - l, p[4] + 1, 30 - l, p[4], 31 - l, p[4] + 1, 31 - l);
            part(g, leg);
        }
        part(g, bevel(oval(17, 8 + dy, 14, 14, 'u'), 'c', 'z'));
        g.pxs('a', 23, 11 + dy, 24, 11 + dy, 23, 15 + dy, 24, 15 + dy, 23, 12 + dy, 24, 12 + dy, 23, 16 + dy, 24, 16 + dy);
        g.pxs('a', 23, 13 + dy, 24, 13 + dy, 23, 14 + dy, 24, 14 + dy);
        g.pxs('A', 23, 11 + dy);
        g.pxs('G', 20, 13 + dy, 27, 13 + dy, 26, 18 + dy, 20, 18 + dy);
        part(g, bevel(oval(8, 13 + dy, 12, 10, 'u'), 'c', 'z'));
        g.pxs('a', 10, 15 + dy, 11, 15 + dy, 10, 16 + dy, 11, 16 + dy, 14, 14 + dy, 15, 14 + dy, 14, 15 + dy, 15, 15 + dy);
        g.pxs('A', 10, 15 + dy, 14, 14 + dy);
        g.pxs('a', 12, 18 + dy, 13, 18 + dy, 9, 18 + dy, 16, 17 + dy);
        part(g, new Grid().stamp(10, 23 + dy, "G..G", "G..G"));
        return g;
    }

    /** Noon: small dark grey stone gargoyle with clean bat wings and glowing yellow eyes. */
    private static Grid noon(int f)
    {
        Grid g = new Grid();
        int dy = -f;
        int wy = f * 3;
        Grid tail = new Grid();
        thick(tail, 'c', 24, 23 + dy, 28, 26, 30, 23 + f);
        tail.pxs('u', 30, 22 + f, 31, 22 + f, 30, 23 + f, 31, 23 + f);
        part(g, tail);
        Grid wing = poly('u', dy, 18, 18, 19, 10 + wy, 23, 4 + wy, 28, 8 + wy, 31, 13 + wy, 29, 19);
        line(wing, 19, 17 + dy, 23, 5 + wy + dy, 'C');
        line(wing, 21, 18 + dy, 28, 9 + wy + dy, 'C');
        line(wing, 24, 19 + dy, 30, 14 + wy + dy, 'C');
        part(g, wing);
        for (int i = 0; i < 2; i++)
        {
            int x = i == 0 ? 12 : 19;
            part(g, bevel(rect(x, 26, 4, 7 - lift(i, f), 'c'), 'C', 'u'));
            g.pxs('z', x + 1, 32 - lift(i, f), x + 2, 32 - lift(i, f));
        }
        part(g, bevel(oval(11, 15 + dy, 14, 12, 'c'), 'C', 'u'));
        g.pxs('C', 15, 19 + dy, 16, 20 + dy, 18, 23 + dy);
        part(g, bevel(poly('u', dy, 6, 12, 5, 6, 10, 11), 'c', 'z'));
        part(g, bevel(poly('u', dy, 11, 11, 13, 5, 15, 12), 'c', 'z'));
        part(g, bevel(oval(5, 10 + dy, 13, 11, 'c'), 'C', 'u'));
        g.pxs('y', 7, 14 + dy, 8, 14 + dy, 7, 15 + dy, 8, 15 + dy, 12, 14 + dy, 13, 14 + dy, 12, 15 + dy, 13, 15 + dy);
        g.pxs('Y', 7, 14 + dy, 12, 14 + dy);
        g.pxs('z', 7, 18 + dy, 8, 18 + dy, 9, 18 + dy, 10, 18 + dy);
        g.pxs('x', 7, 19 + dy, 10, 19 + dy);
        return g;
    }

    /** Olmlet: white baby dragon with a frill, horns and a wagging tail. */
    private static Grid olmlet(int f)
    {
        Grid g = new Grid();
        int dy = -f;
        part(g, bevel(oval(25, 21 + dy, 5, 4, 'U'), 'x', 'X'));
        part(g, bevel(oval(28, 17 + f * 2, 4, 6, 'U'), 'x', 'X'));
        legs4(g, new int[] {10, 13, 21, 24}, 26, 3, 'U', 'X', 'Z', f);
        Grid frill = poly('X', dy, 11, 15, 10, 8, 14, 10, 17, 5, 20, 10, 23, 7, 22, 16);
        bevel(frill, 'U', 'Z');
        part(g, frill);
        part(g, bevel(oval(11, 17 + dy, 16, 11, 'U'), 'x', 'X'));
        g.pxs('X', 16, 22 + dy, 17, 23 + dy, 22, 22 + dy, 23, 23 + dy);
        part(g, poly('T', dy, 8, 12, 7, 6, 11, 11));
        part(g, poly('T', dy, 12, 11, 14, 5, 15, 12));
        part(g, bevel(oval(6, 10 + dy, 11, 12, 'U'), 'x', 'X'));
        part(g, bevel(oval(4, 15 + dy, 8, 7, 'U'), 'x', 'X'));
        g.pxs('z', 8, 14 + dy, 9, 14 + dy, 8, 15 + dy, 9, 15 + dy).pxs('U', 8, 14 + dy);
        g.pxs('z', 5, 17 + dy, 6, 17 + dy);
        g.pxs('z', 5, 20 + dy, 6, 20 + dy, 7, 20 + dy, 8, 20 + dy);
        g.pxs('h', 10, 18 + dy, 11, 18 + dy);
        return g;
    }

    /** Orion: bone-white skull with glowing red eyes and separate wavy red tendrils, floating. */
    private static Grid orion(int f)
    {
        Grid g = new Grid();
        int dy = -f;
        for (int i = 0; i < 5; i++)
        {
            int x = 12 + i * 4;
            int len = i % 2 == 0 ? 10 : 8;
            for (int j = 0; j < len; j++)
            {
                int y = 19 + dy + j;
                int off = (((j + f) >> 1) + i) & 1;
                boolean tip = j >= len - 2;
                g.set(x + off, y, tip ? 'a' : 'A');
                g.set(x + off + 1, y, tip ? 'D' : 'a');
            }
        }
        part(g, bevel(rect(14, 15 + dy, 14, 5, 'e'), 'x', 'E'));
        part(g, bevel(oval(9, 2 + dy, 24, 18, 'e'), 'x', 'E'));
        g.pxs('E', 13, 4 + dy, 14, 5 + dy, 28, 5 + dy, 27, 4 + dy, 21, 3 + dy);
        part(g, oval(13, 8 + dy, 7, 7, 'z'));
        part(g, oval(22, 8 + dy, 7, 7, 'z'));
        g.pxs('a', 15, 10 + dy, 16, 10 + dy, 15, 11 + dy, 16, 11 + dy, 24, 10 + dy, 25, 10 + dy, 24, 11 + dy, 25, 11 + dy);
        g.pxs('A', 15, 10 + dy, 24, 10 + dy);
        g.pxs('z', 20, 14 + dy, 21, 14 + dy);
        g.pxs('z', 16, 17 + dy, 18, 17 + dy, 20, 17 + dy, 22, 17 + dy, 24, 17 + dy, 16, 18 + dy, 18, 18 + dy, 20, 18 + dy,
            22, 18 + dy, 24, 18 + dy);
        return g;
    }

    /** Pet chaos elemental: swirling purple-grey cloud with sparkles and pink and purple tentacles waving on top. */
    private static Grid chaos(int f)
    {
        Grid g = new Grid();
        Grid t1 = new Grid();
        thick(t1, 'h', 14, 19, 11, 13, 12 + f, 7);
        part(g, bevel(t1, 'W', 'K'));
        Grid t2 = new Grid();
        thick(t2, 'q', 19, 17, 17, 11, 18 - f, 6);
        part(g, bevel(t2, 'V', 'p'));
        Grid t3 = new Grid();
        thick(t3, 'h', 25, 17, 27, 11, 26 + f, 6);
        part(g, bevel(t3, 'W', 'K'));
        Grid t4 = new Grid();
        thick(t4, 'q', 30, 19, 32, 14, 30 - f, 9);
        part(g, bevel(t4, 'V', 'p'));
        Grid cl = new Grid();
        with(cl, oval(9, 18, 11, 11, 'N'));
        with(cl, oval(14, 15, 14, 13, 'N'));
        with(cl, oval(22, 19, 11, 10, 'N'));
        with(cl, oval(9, 24 + f, 9, 8 - f, 'N'));
        with(cl, oval(17, 25 - f, 9, 7 + f, 'N'));
        with(cl, oval(25, 24 + f, 8, 8 - f, 'N'));
        bevel(cl, 'v', 'P');
        part(g, cl);
        char sw = f == 0 ? 'v' : 'q';
        g.pxs(sw, 12, 20, 14, 18, 17, 17, 20, 17, 22, 18, 25, 22, 27, 21, 29, 22, 30, 24, 29, 26);
        g.pxs('x', 16, 20, 27, 25, 21, 21);
        g.pxs('Y', 12, 27 - f, 31, 23 + f);
        g.pxs('z', 11, 22, 12, 22, 17, 22, 18, 22);
        g.pxs('y', 12, 23, 13, 23, 12, 24, 13, 24, 17, 23, 18, 23, 17, 24, 18, 24);
        g.pxs('z', 12, 24, 17, 24).pxs('Y', 13, 23, 18, 23);
        g.pxs('z', 13, 28 - f, 14, 28 - f, 15, 28 - f, 16, 28 - f).pxs('x', 13, 27 - f, 16, 27 - f);
        return g;
    }

    /** Pet Dagannoth Prime: upright grey-green dagannoth with an orange-brown face plate and a big crest. */
    private static Grid prime(int f)
    {
        Grid g = new Grid();
        int dy = -f;
        part(g, bevel(oval(25, 23 + dy, 6, 4, 'n'), 'M', 'J'));
        for (int i = 0; i < 2; i++)
        {
            int x = i == 0 ? 15 : 21;
            int l = lift(i, f);
            part(g, bevel(rect(x, 27, 4, 6 - l, 'n'), 'M', 'J'));
            part(g, bevel(rect(x - 3, 31 - l, 7, 2, 'o'), 'O', 'Q'));
        }
        Grid crest = poly('o', dy, 17, 11, 17, 7, 21, 8, 24, 5, 27, 9, 30, 7, 28, 15, 21, 15);
        bevel(crest, 'O', 'Q');
        line(crest, 19, 10 + dy, 21, 8 + dy, 'Q');
        line(crest, 23, 10 + dy, 24, 6 + dy, 'Q');
        line(crest, 26, 11 + dy, 29, 8 + dy, 'Q');
        part(g, crest);
        part(g, bevel(oval(13, 16 + dy, 16, 12, 'n'), 'M', 'J'));
        part(g, bevel(oval(14, 19 + dy, 8, 7, 'd'), 'o', 'i'));
        g.pxs('i', 15, 21 + dy, 16, 21 + dy, 17, 21 + dy, 16, 24 + dy, 17, 24 + dy);
        part(g, bevel(oval(10, 18 + dy, 5, 9, 'n'), 'M', 'J'));
        g.pxs('x', 10, 27 + dy, 12, 27 + dy);
        part(g, bevel(oval(8, 9 + dy, 15, 10, 'n'), 'M', 'J'));
        part(g, bevel(oval(5, 10 + dy, 9, 8, 'o'), 'O', 'Q'));
        g.pxs('y', 7, 12 + dy, 8, 12 + dy, 7, 13 + dy, 8, 13 + dy, 15, 11 + dy, 16, 11 + dy, 15, 12 + dy, 16, 12 + dy);
        g.pxs('z', 8, 13 + dy, 15, 12 + dy).pxs('Y', 7, 12 + dy, 15, 11 + dy);
        g.pxs('z', 6, 16 + dy, 7, 16 + dy, 8, 16 + dy, 9, 16 + dy);
        g.pxs('x', 6, 17 + dy, 9, 17 + dy);
        return g;
    }
}
