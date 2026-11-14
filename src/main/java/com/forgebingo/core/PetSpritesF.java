package com.forgebingo.core;

import com.forgebingo.core.PixelArt.Grid;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pixel art for some of the pets (see Pet): Skotos, Smol Heredit, Smolcano, Sraracha, Tiny tempor, Tumeken's guardian,
 * TzRek-Jad, Venenatis spiderling, Vet'ion Jr., Vorki, Wisp, Yami and Youngllef. Original chunky chibi art: big head,
 * 1px dark outline, 2-3 shades per colour, two walk frames. Facing LEFT, feet on the bottom rows, body centred on
 * column 21. Returns null for a pet this file does not draw.
 */
public final class PetSpritesF
{
    private static final Map<String, BufferedImage> CACHE = new ConcurrentHashMap<>();

    private PetSpritesF()
    {
    }

    /** One walk frame (0 or 1) on the PixelArt.WIDTH x PixelArt.HEIGHT grid, facing left, feet on the bottom rows. */
    public static BufferedImage image(Pet pet, int frame)
    {
        if (pet == null) return null;
        switch (pet)
        {
            case SKOTOS: case SMOL_HEREDIT: case SMOLCANO: case SRARACHA: case TINY_TEMPOR: case TUMEKENS_GUARDIAN:
            case TZREK_JAD: case VENENATIS_SPIDERLING: case VETION_JR: case VORKI: case WISP: case YAMI: case YOUNGLLEF:
                break;
            default:
                return null;
        }
        int f = frame == 0 ? 0 : 1;
        return CACHE.computeIfAbsent(pet + "|" + f, k -> PixelArt.render(build(pet, f), PetSpritesF::colour));
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
            case 'v': return 0x6a5a80; // purple-grey
            case 'V': return 0x9a88b4;
            case 'U': return 0x3c3050;
            case 'p': return 0x7a4aa0; // purple
            case 'P': return 0x4a2868;
            case 'q': return 0xa87ccc;
            case 'a': return 0xd8342a; // red
            case 'A': return 0xff7c50;
            case 'h': return 0x5a1418;
            case 'D': return 0x8a2a2a; // dark red
            case 'F': return 0xb84444;
            case 'o': return 0xf0a020; // orange
            case 'O': return 0xffd460;
            case 'Q': return 0xb86418;
            case 'y': return 0xd8a828; // gold
            case 'Y': return 0xf8d860;
            case 's': return 0x9a6a18;
            case 'e': return 0xe6dcc4; // bone
            case 'E': return 0xb0a488;
            case 't': return 0xdcbc84; // tan
            case 'T': return 0xf2e0b0;
            case 'r': return 0xa88650;
            case 'l': return 0x9ec8d8; // pale blue
            case 'L': return 0xd2eef6;
            case 'n': return 0x6a98ae;
            case 'i': return 0x2fd0c0; // cyan glow
            case 'I': return 0xa6f6ea;
            case 'J': return 0x1a8088;
            case 'b': return 0x2aa890; // teal
            case 'B': return 0x70e4cc;
            case 'd': return 0x186a74;
            case 'w': return 0x6a4a28; // brown
            case 'W': return 0x3e2a14;
            case 'R': return 0x8a5a36;
            case 'm': return 0x3a4458; // blue-grey
            case 'M': return 0x5a6a82;
            case 'N': return 0x9aaac4;
            case 'g': return 0x7a4030; // red-brown
            case 'G': return 0xa86448;
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

    private static Grid tri(double x0, double y0, double x1, double y1, double x2, double y2, char c)
    {
        Grid m = new Grid();
        double den = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2);
        for (int y = 0; y < m.h; y++)
        {
            for (int x = 0; x < m.w; x++)
            {
                double px = x + 0.5;
                double py = y + 0.5;
                double a = ((y1 - y2) * (px - x2) + (x2 - x1) * (py - y2)) / den;
                double b = ((y2 - y0) * (px - x2) + (x0 - x2) * (py - y2)) / den;
                if (a >= 0 && b >= 0 && a + b <= 1) m.set(x, y, c);
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

    /** Paints c over the pixels of base that lie in the given rectangle. */
    private static void tint(Grid base, int x, int y, int w, int h, char c)
    {
        for (int j = y; j < y + h; j++)
        {
            for (int i = x; i < x + w; i++)
            {
                if (base.has(i, j)) base.set(i, j, c);
            }
        }
    }

    private static void part(Grid g, Grid fill)
    {
        PixelArt.part(g, fill, 'k');
    }

    private static void line(Grid m, int x0, int y0, int x1, int y1, char c)
    {
        int n = Math.max(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)), 1);
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

    /** Four legs, diagonal pairs alternate: a lifted leg is one pixel shorter. Legs end on row 32. */
    private static void legs4(Grid g, int[] xs, int top, int w, char c, char dark, char foot, int f)
    {
        for (int i = 0; i < xs.length; i++)
        {
            int lift = ((i % 2 == 0) == (f == 1)) ? 1 : 0;
            Grid l = bevel(rect(xs[i], top, w, 33 - top - lift, c), (char) 0, dark);
            if (foot != 0) with(l, rect(xs[i], 32 - lift, w, 1, foot));
            part(g, l);
        }
    }

    /** Two legs that swap lift each frame. */
    private static void legs2(Grid g, int x0, int x1, int top, int w, char c, char dark, char foot, int f)
    {
        int[] xs = {x0, x1};
        for (int i = 0; i < 2; i++)
        {
            int lift = ((i == 0) == (f == 1)) ? 1 : 0;
            Grid l = bevel(rect(xs[i], top, w, 33 - top - lift, c), (char) 0, dark);
            if (foot != 0) with(l, rect(xs[i], 32 - lift, w, 1, foot));
            part(g, l);
        }
    }

    private static Grid build(Pet pet, int f)
    {
        switch (pet)
        {
            case SKOTOS: return skotos(f);
            case SMOL_HEREDIT: return heredit(f);
            case SMOLCANO: return smolcano(f);
            case SRARACHA: return sraracha(f);
            case TINY_TEMPOR: return tempor(f);
            case TUMEKENS_GUARDIAN: return guardian(f);
            case TZREK_JAD: return jad(f);
            case VENENATIS_SPIDERLING: return venenatis(f);
            case VETION_JR: return vetion(f);
            case VORKI: return vorki(f);
            case WISP: return wisp(f);
            case YAMI: return yami(f);
            default: return youngllef(f);
        }
    }


    private static java.util.List<int[]> path(int... p)
    {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (int i = 0; i + 3 < p.length; i += 2)
        {
            int n = Math.max(Math.max(Math.abs(p[i + 2] - p[i]), Math.abs(p[i + 3] - p[i + 1])), 1);
            for (int k = 0; k < n; k++)
            {
                out.add(new int[] {(int) Math.round(p[i] + (p[i + 2] - p[i]) * (double) k / n),
                    (int) Math.round(p[i + 1] + (p[i + 3] - p[i + 1]) * (double) k / n)});
            }
        }
        out.add(new int[] {p[p.length - 2], p[p.length - 1]});
        return out;
    }

    /** A brush stroke along the path that gets thinner towards the end. */
    private static Grid taper(java.util.List<int[]> pts, int sMax, char c)
    {
        Grid m = new Grid();
        int n = pts.size();
        for (int k = 0; k < n; k++)
        {
            double t = (double) k / (n - 1);
            int s = t < 0.4 ? sMax : t < 0.75 ? Math.max(sMax - 1, 1) : Math.max(sMax - 2, 1);
            for (int i = 0; i < s; i++)
            {
                for (int j = 0; j < s; j++) m.set(pts.get(k)[0] + i, pts.get(k)[1] + j, c);
            }
        }
        return m;
    }

    private static void tendril(Grid t, int x0, int y0, int len, double phase, double drift, char c)
    {
        for (int k = 0; k <= len; k++)
        {
            t.set(x0 + (int) Math.round(drift * k + Math.sin(k * 0.9 + phase) * 1.6), y0 + k, c);
        }
    }

    // ---------------------------------------------------------------- pets

    /** Skotos: dark purple-grey demon, big dark horns and spikes, glowing red eyes. */
    private static Grid skotos(int f)
    {
        Grid g = new Grid();
        int dy = -f;
        Grid tail = new Grid();
        thick(tail, 'U', 29, 21 + dy, 32, 19 + dy, 33, 15 - f);
        part(g, tail);
        g.pxs('A', 33, 15 - f);
        legs4(g, new int[] {12, 16, 25, 28}, 26, 4, 'v', 'U', 'z', f);
        Grid spikes = new Grid();
        for (int cx : new int[] {22, 26, 30})
        {
            with(spikes, bevel(trap(cx, 10 + dy + (cx - 22) / 4, 18 + dy, 1, 3, 'P'), 'q', 'z'));
        }
        part(g, spikes);
        part(g, bevel(oval(13, 16 + dy, 19, 13, 'v'), 'V', 'U'));
        g.pxs('U', 22, 21 + dy, 23, 22 + dy, 27, 20 + dy, 28, 24 + dy, 19, 25 + dy);
        Grid horns = new Grid();
        thick(horns, 'P', 11, 10 + dy, 9, 6 + dy, 9, 1 + dy);
        thick(horns, 'P', 17, 9 + dy, 20, 5 + dy, 19, 1 + dy);
        horns.pxs('q', 9, 2 + dy, 19, 2 + dy, 9, 5 + dy, 20, 6 + dy);
        part(g, horns);
        part(g, bevel(oval(8, 9 + dy, 14, 13, 'v'), 'V', 'U'));
        part(g, bevel(oval(7, 15 + dy, 8, 7, 'v'), 'V', 'U'));
        g.pxs('z', 10, 14 + dy, 11, 14 + dy, 12, 14 + dy, 16, 14 + dy, 17, 14 + dy, 18, 14 + dy);
        g.pxs('A', 10, 14 + dy, 11, 14 + dy, 16, 14 + dy, 17, 14 + dy);
        g.pxs('a', 12, 14 + dy, 18, 14 + dy);
        g.pxs('z', 8, 17 + dy, 9, 17 + dy);
        g.pxs('z', 8, 20 + dy, 9, 20 + dy, 10, 20 + dy, 11, 20 + dy, 12, 20 + dy, 13, 20 + dy);
        g.pxs('x', 9, 21 + dy, 12, 21 + dy);
        return g;
    }

    /** Smol Heredit: tiny gladiator with a big pale-and-gold helmet with a red sun, a round shield and a spear. */
    private static Grid heredit(int f)
    {
        Grid g = new Grid();
        int dy = -f;
        Grid spear = new Grid();
        with(spear, rect(30, 7 + dy, 1, 24, 'w'));
        part(g, spear);
        part(g, bevel(trap(31, 1 + dy, 7 + dy, 0.5, 2, 'x'), 'x', 'E'));
        legs2(g, 15, 22, 27, 4, 't', 'r', 'W', f);
        part(g, bevel(oval(12, 18 + dy, 17, 11, 'a'), 'A', 'h'));
        part(g, bevel(rect(14, 25 + dy, 13, 2, 'y'), 'Y', 's'));
        part(g, bevel(oval(26, 20 + dy, 5, 5, 't'), 'T', 'r'));
        Grid dome = bevel(oval(8, 2 + dy, 18, 17, 'T'), 'x', 't');
        tint(dome, 8, 14 + dy, 18, 4, 'y');
        tint(dome, 8, 14 + dy, 18, 1, 'Y');
        tint(dome, 8, 17 + dy, 18, 1, 's');
        part(g, dome);
        g.pxs('z', 9, 15 + dy, 10, 15 + dy, 11, 15 + dy, 12, 15 + dy, 13, 15 + dy, 14, 15 + dy, 15, 15 + dy, 16, 15 + dy);
        g.pxs('Y', 10, 15 + dy, 14, 15 + dy);
        part(g, bevel(oval(13, 5 + dy, 7, 7, 'a'), 'A', 'h'));
        g.pxs('o', 16, 4 + dy, 11, 8 + dy, 21, 8 + dy, 16, 13 + dy);
        g.pxs('O', 16, 8 + dy);
        part(g, bevel(trap(17, -1 + dy, 2 + dy, 1, 3, 'a'), 'A', 'h'));
        Grid shield = bevel(oval(6, 19 + dy, 9, 10, 'y'), 'Y', 's');
        shield.stamp(9, 22 + dy, "aa", "aa");
        part(g, shield);
        g.pxs('A', 9, 22 + dy);
        return g;
    }

    /** Smolcano: round dark grey rock demon, big curved horns, cracks and a glowing lava face. */
    private static Grid smolcano(int f)
    {
        Grid g = new Grid();
        int dy = -f;
        legs2(g, 12, 24, 27, 5, 'u', 'z', 'z', f);
        Grid horns = new Grid();
        thick(horns, 'c', 13, 13 + dy, 10, 10 + dy, 9, 5 + dy, 11, 2 + dy);
        thick(horns, 'c', 28, 13 + dy, 31, 10 + dy, 32, 5 + dy, 30, 2 + dy);
        horns.pxs('C', 9, 6 + dy, 10, 9 + dy, 32, 6 + dy, 11, 3 + dy);
        horns.pxs('u', 12, 12 + dy, 29, 12 + dy);
        part(g, horns);
        part(g, bevel(oval(9, 10 + dy, 24, 21, 'c'), 'C', 'u'));
        g.pxs('C', 22, 13 + dy, 23, 13 + dy, 27, 15 + dy, 14, 12 + dy);
        g.pxs('u', 29, 23 + dy, 30, 24 + dy, 26, 27 + dy, 24, 26 + dy);
        // glowing cracks
        Grid cr = new Grid();
        line(cr, 21, 13 + dy, 24, 17 + dy, 'o');
        line(cr, 24, 17 + dy, 23, 21 + dy, 'o');
        line(cr, 24, 17 + dy, 29, 19 + dy, 'o');
        line(cr, 29, 19 + dy, 30, 24 + dy, 'o');
        line(cr, 23, 21 + dy, 26, 25 + dy, 'o');
        with(g, cr);
        g.pxs('O', 24, 17 + dy, 29, 19 + dy);
        // lava face
        part(g, bevel(oval(7, 16 + dy, 16, 13, 'a'), 'A', 'h'));
        part(g, oval(9, 18 + dy, 12, 9, 'o'));
        g.pxs('O', 10, 19 + dy, 11, 19 + dy, 18, 19 + dy);
        g.pxs('z', 10, 20 + dy, 11, 20 + dy, 12, 20 + dy, 16, 20 + dy, 17, 20 + dy, 18, 20 + dy);
        g.pxs('Y', 11, 20 + dy, 17, 20 + dy);
        g.pxs('z', 10, 25 + dy, 11, 24 + dy, 12, 25 + dy, 13, 24 + dy, 14, 25 + dy, 15, 24 + dy, 16, 25 + dy, 17, 24 + dy);
        g.pxs('a', 13, 22 + dy, 14, 22 + dy);
        return g;
    }

    /** Legs for a side-on spider: each is root x, root y, knee x, knee y, foot x. */
    private static void spiderLegs(Grid g, int[][] legs, char c, char hi, int f)
    {
        Grid l = new Grid();
        for (int i = 0; i < legs.length; i++)
        {
            int[] p = legs[i];
            boolean up = (i % 2 == 0) == (f == 1);
            int fx = p[4] + (up ? (p[4] < p[0] ? 1 : -1) : 0);
            int fy = up ? 31 : 32;
            line(l, p[0], p[1], p[2], p[3], c);
            line(l, p[2], p[3], fx, fy, c);
            l.set(p[2], p[3], hi);
        }
        part(g, l);
    }

    /** Sraracha: dark red spider with a bulbous abdomen. */
    private static Grid sraracha(int f)
    {
        Grid g = new Grid();
        int dy = -f;
        spiderLegs(g, new int[][] {{14, 23 + dy, 9, 20 + dy, 8}, {17, 24 + dy, 13, 19 + dy, 11}, {24, 24 + dy, 28, 19 + dy, 30},
            {27, 23 + dy, 31, 21 + dy, 33}}, 'D', 'F', f);
        part(g, bevel(oval(15, 8 + dy, 17, 19, 'D'), 'F', 'h'));
        g.pxs('h', 21, 12 + dy, 22, 13 + dy, 23, 14 + dy, 24, 15 + dy, 25, 16 + dy, 26, 17 + dy);
        g.pxs('e', 20, 13 + dy, 21, 14 + dy, 22, 15 + dy, 23, 16 + dy, 24, 17 + dy, 25, 18 + dy);
        g.pxs('F', 19, 11 + dy, 20, 11 + dy, 28, 13 + dy, 29, 15 + dy);
        part(g, bevel(oval(8, 16 + dy, 13, 11, 'D'), 'F', 'h'));
        g.pxs('z', 9, 19 + dy, 10, 19 + dy, 13, 19 + dy, 14, 19 + dy, 9, 20 + dy, 10, 20 + dy);
        g.pxs('A', 9, 19 + dy, 13, 19 + dy);
        g.pxs('a', 10, 19 + dy, 14, 19 + dy);
        g.pxs('A', 11, 21 + dy);
        g.pxs('e', 9, 25 + dy, 9, 26 + dy, 13, 25 + dy, 13, 26 + dy);
        g.pxs('z', 10, 24 + dy, 11, 24 + dy, 12, 24 + dy);
        return g;
    }

    /** Tiny tempor: pale blue water spirit with wave crests, droplets, a swirl tail and glowing cyan eyes. */
    private static Grid tempor(int f)
    {
        Grid g = new Grid();
        int dy = -f;
        for (int i = 0; i < 2; i++)
        {
            int lift = ((i == 0) == (f == 1)) ? 1 : 0;
            int x = i == 0 ? 14 : 22;
            part(g, bevel(rect(x, 28, 5, 5 - lift, 'n'), (char) 0, 'm'));
        }
        // water-swirl tail
        Grid tail = new Grid();
        thick(tail, 'l', 29, 25 + dy, 33, 25 + dy, 34, 21 + dy, 31, 19 + dy, 30, 22 + dy);
        part(g, tail);
        g.pxs('L', 33, 25 + dy, 34, 22 + dy, 32, 19 + dy);
        g.pxs('n', 31, 24 + dy, 32, 24 + dy);
        // body with wave crests
        Grid body = oval(11, 11 + dy, 20, 19, 'l');
        with(body, oval(12, 7 + dy, 6, 6, 'l'));
        with(body, oval(17, 5 + dy, 7, 7, 'l'));
        with(body, oval(23, 8 + dy, 6, 5, 'l'));
        bevel(body, 'L', 'n');
        part(g, body);
        g.pxs('n', 19, 9 + dy, 20, 9 + dy, 21, 10 + dy, 14, 11 + dy, 15, 11 + dy, 25, 11 + dy);
        g.pxs('x', 18, 6 + dy, 13, 8 + dy);
        // droplets
        part(g, new Grid().stamp(8, 12 + f, ".L.", "LLL", ".L."));
        part(g, new Grid().stamp(30, 8 + (1 - f), ".L.", "LLL", ".L."));
        // glowing eyes
        g.pxs('z', 13, 17 + dy, 14, 17 + dy, 15, 17 + dy, 13, 18 + dy, 14, 18 + dy, 15, 18 + dy, 13, 19 + dy, 14, 19 + dy, 15, 19 + dy);
        g.pxs('i', 14, 18 + dy, 14, 19 + dy);
        g.pxs('I', 14, 17 + dy);
        g.pxs('z', 19, 17 + dy, 20, 17 + dy, 21, 17 + dy, 19, 18 + dy, 20, 18 + dy, 21, 18 + dy, 19, 19 + dy, 20, 19 + dy, 21, 19 + dy);
        g.pxs('i', 20, 18 + dy, 20, 19 + dy);
        g.pxs('I', 20, 17 + dy);
        // clover markings
        g.pxs('i', 25, 16 + dy, 26, 16 + dy, 25, 17 + dy, 26, 17 + dy, 24, 20 + dy, 25, 20 + dy, 24, 21 + dy, 25, 21 + dy);
        g.pxs('i', 27, 19 + dy, 28, 19 + dy, 27, 20 + dy, 28, 20 + dy);
        g.pxs('I', 25, 16 + dy, 24, 20 + dy, 27, 19 + dy);
        g.pxs('n', 15, 24 + dy, 16, 25 + dy, 17, 25 + dy, 18, 24 + dy);
        return g;
    }

    /** Tumeken's guardian: floating gold-and-stone statue with a pharaoh headdress, a torso and a glowing core. */
    private static Grid guardian(int f)
    {
        Grid g = new Grid();
        int dy = 1 - f;
        // glow under the hover point
        g.pxs('O', 21, 29 + dy);
        g.pxs('A', 20, 30 + dy, 22, 30 + dy);
        // tapering stone torso
        part(g, bevel(trap(21, 15 + dy, 28 + dy, 8, 1.4, 'c'), 'C', 'u'));
        Grid plate = bevel(rect(16, 17 + dy, 10, 6, 'y'), 'Y', 's');
        part(g, plate);
        part(g, bevel(oval(19, 18 + dy, 4, 4, 'o'), 'O', 'Q'));
        g.pxs('x', 20, 19 + dy);
        // floating hands
        part(g, bevel(oval(8, 17 + dy - f, 5, 5, 'y'), 'Y', 's'));
        part(g, bevel(oval(29, 17 + dy + f, 5, 5, 'y'), 'Y', 's'));
        // headdress with striped lappets
        part(g, bevel(rect(11, 6 + dy, 4, 10, 'y'), 'Y', 's'));
        part(g, bevel(rect(27, 6 + dy, 4, 10, 'y'), 'Y', 's'));
        for (int y = 8; y < 15; y += 2) g.pxs('m', 12, y + dy, 13, y + dy, 28, y + dy, 29, y + dy);
        part(g, bevel(oval(11, 0 + dy, 20, 14, 'y'), 'Y', 's'));
        for (int x = 16; x < 29; x += 3) g.pxs('m', x, 2 + dy, x, 3 + dy);
        part(g, bevel(oval(11, 4 + dy, 13, 11, 't'), 'T', 'r'));
        g.pxs('z', 13, 8 + dy, 14, 8 + dy, 19, 8 + dy, 20, 8 + dy);
        g.pxs('O', 13, 8 + dy, 19, 8 + dy);
        g.pxs('r', 16, 10 + dy, 16, 11 + dy);
        g.pxs('z', 14, 12 + dy, 15, 12 + dy, 16, 12 + dy, 17, 12 + dy);
        g.pxs('a', 16, 3 + dy, 16, 4 + dy);
        return g;
    }

    /** TzRek-Jad: dark red beast with a big spiked hump, thick pillar legs with flared feet, low head, wide toothy jaw. */
    private static Grid jad(int f)
    {
        Grid g = new Grid();
        int dy = -f;
        // pillar legs with flared feet
        int[] xs = {11, 15, 25, 29};
        for (int i = 0; i < 4; i++)
        {
            int lift = ((i % 2 == 0) == (f == 1)) ? 1 : 0;
            Grid l = bevel(rect(xs[i], 23, 4, 9 - lift, 'D'), 'F', 'h');
            with(l, rect(xs[i] - 1, 31 - lift, 6, 2, 'h'));
            with(l, rect(xs[i] - 1, 31 - lift, 6, 1, 'D'));
            part(g, l);
        }
        // hump with spikes
        Grid spikes = new Grid();
        for (int cx : new int[] {18, 22, 26, 30})
        {
            with(spikes, bevel(trap(cx, 4 + dy + Math.abs(cx - 24) / 3, 12 + dy, 1, 3, 'F'), 'A', 'h'));
        }
        part(g, spikes);
        part(g, bevel(oval(12, 8 + dy, 20, 20, 'D'), 'F', 'h'));
        g.pxs('h', 21, 14 + dy, 22, 15 + dy, 27, 19 + dy, 28, 20 + dy, 18, 22 + dy, 25, 12 + dy);
        // low head, brow, wide jaw
        part(g, bevel(oval(7, 16 + dy, 12, 9, 'D'), 'F', 'h'));
        part(g, bevel(oval(7, 22 + dy, 14, 6, 'h'), 'D', 'z'));
        g.pxs('z', 8, 22 + dy, 9, 22 + dy, 10, 22 + dy, 11, 22 + dy, 12, 22 + dy, 13, 22 + dy, 14, 22 + dy, 15, 22 + dy, 16, 22 + dy);
        g.pxs('x', 9, 23 + dy, 11, 23 + dy, 13, 23 + dy, 15, 23 + dy, 10, 21 + dy, 14, 21 + dy);
        g.pxs('h', 10, 18 + dy, 11, 18 + dy, 12, 18 + dy, 13, 18 + dy, 14, 18 + dy);
        g.pxs('z', 11, 19 + dy, 12, 19 + dy, 13, 19 + dy);
        g.pxs('Y', 12, 19 + dy);
        g.pxs('z', 8, 19 + dy);
        return g;
    }

    /** Venenatis spiderling: dark red-brown spider with long legs and a striped abdomen. */
    private static Grid venenatis(int f)
    {
        Grid g = new Grid();
        int dy = -f;
        spiderLegs(g, new int[][] {{14, 21 + dy, 9, 11 + dy, 8}, {17, 22 + dy, 14, 12 + dy, 13}, {25, 21 + dy, 29, 11 + dy, 30},
            {28, 22 + dy, 32, 13 + dy, 34}}, 'R', 'G', f);
        Grid abd = bevel(oval(17, 10 + dy, 15, 15, 'g'), 'G', 'W');
        for (int x = 20; x < 30; x += 4) tint(abd, x, 10 + dy, 2, 15, 'W');
        for (int x = 22; x < 30; x += 4) tint(abd, x, 10 + dy, 1, 15, 'G');
        part(g, abd);
        part(g, bevel(oval(10, 16 + dy, 12, 11, 'g'), 'G', 'W'));
        g.pxs('z', 11, 19 + dy, 12, 19 + dy, 13, 19 + dy, 14, 19 + dy, 15, 19 + dy);
        g.pxs('x', 11, 19 + dy, 13, 19 + dy);
        g.pxs('Y', 12, 20 + dy, 15, 20 + dy);
        g.pxs('e', 11, 24 + dy, 11, 25 + dy, 14, 24 + dy, 14, 25 + dy);
        g.pxs('z', 12, 23 + dy, 13, 23 + dy);
        return g;
    }

    /** Vet'ion Jr.: small purple skeleton warrior with a dark hammer. */
    private static Grid vetion(int f)
    {
        Grid g = new Grid();
        int dy = -f;
        Grid handle = new Grid();
        thick(handle, 'W', 6, 11 + dy, 6, 30 + dy);
        part(g, handle);
        part(g, bevel(rect(2, 4 + dy, 10, 8, 'u'), 'C', 'z'));
        g.pxs('c', 4, 6 + dy, 5, 6 + dy, 6, 6 + dy);
        legs2(g, 15, 23, 27, 3, 'e', 'E', 'P', f);
        part(g, bevel(oval(13, 17 + dy, 16, 12, 'p'), 'q', 'P'));
        part(g, bevel(rect(14, 24 + dy, 14, 2, 'P'), 'p', 'z'));
        g.pxs('y', 21, 24 + dy, 21, 25 + dy);
        part(g, bevel(oval(27, 20 + dy, 5, 6, 'p'), 'q', 'P'));
        part(g, bevel(oval(8, 21 + dy, 8, 6, 'p'), 'q', 'P'));
        g.pxs('e', 7, 24 + dy, 8, 24 + dy);
        part(g, bevel(oval(10, 5 + dy, 19, 15, 'p'), 'q', 'P'));
        part(g, bevel(oval(9, 9 + dy, 13, 10, 'e'), 'x', 'E'));
        g.pxs('z', 11, 12 + dy, 12, 12 + dy, 13, 12 + dy, 16, 12 + dy, 17, 12 + dy, 18, 12 + dy, 11, 13 + dy, 12, 13 + dy, 13, 13 + dy);
        g.pxs('z', 16, 13 + dy, 17, 13 + dy, 18, 13 + dy);
        g.pxs('A', 12, 13 + dy, 17, 13 + dy);
        g.pxs('z', 11, 17 + dy, 13, 17 + dy, 15, 17 + dy, 17, 17 + dy);
        g.pxs('E', 10, 16 + dy, 19, 16 + dy);
        part(g, bevel(trap(21, 1 + dy, 5 + dy, 0.6, 2.5, 'p'), 'q', 'P'));
        return g;
    }

    /** Vorki: pale blue undead dragon with horns, a bony torn-membrane wing and a skull-like face. */
    private static Grid vorki(int f)
    {
        Grid g = new Grid();
        int dy = -f;
        int w = f == 0 ? 0 : 2;
        // wing: arm bone up to the wrist, three finger bones, torn membrane between them
        int sx = 23;
        int sy = 17 + dy;
        int wx = 27;
        int wy = 5 + w;
        int[][] tips = {{34, 8 + w}, {34, 16 + w}, {29, 22 + w}};
        Grid mem = new Grid();
        with(mem, tri(sx, sy, wx, wy, tips[0][0], tips[0][1], 'n'));
        with(mem, tri(sx, sy, tips[0][0], tips[0][1], tips[1][0], tips[1][1], 'n'));
        with(mem, tri(sx, sy, tips[1][0], tips[1][1], tips[2][0], tips[2][1], 'n'));
        // tears between the finger tips
        with(mem, tri(34, 12 + w, 29, 14 + w, 34, 15 + w, 'z'));
        with(mem, tri(34, 19 + w, 30, 19 + w, 32, 23 + w, 'z'));
        for (int j = 0; j < mem.h; j++)
        {
            for (int i = 0; i < mem.w; i++)
            {
                if (mem.px[j][i] == 'z') mem.px[j][i] = 0;
            }
        }
        part(g, mem);
        Grid bones = new Grid();
        line(bones, sx, sy, wx, wy, 'e');
        for (int[] t : tips) line(bones, wx, wy, t[0], t[1], 'e');
        g.pxs('x', wx, wy);
        part(g, bones);
        Grid tail = new Grid();
        thick(tail, 'l', 29, 23 + dy, 32, 24 + dy, 33, 20 + dy - f);
        part(g, tail);
        legs4(g, new int[] {12, 16, 25, 28}, 26, 3, 'l', 'n', 'e', f);
        part(g, bevel(oval(11, 16 + dy, 20, 13, 'l'), 'L', 'n'));
        g.pxs('E', 19, 21 + dy, 21, 21 + dy, 23, 21 + dy, 20, 24 + dy, 22, 24 + dy);
        g.pxs('e', 18, 17 + dy, 21, 17 + dy, 24, 17 + dy, 27, 17 + dy);
        Grid horns = new Grid();
        thick(horns, 'e', 11, 10 + dy, 8, 6 + dy, 8, 1 + dy);
        thick(horns, 'e', 16, 9 + dy, 19, 5 + dy, 18, 1 + dy);
        horns.pxs('E', 8, 2 + dy, 18, 2 + dy, 9, 8 + dy);
        part(g, horns);
        part(g, bevel(oval(8, 8 + dy, 14, 14, 'l'), 'L', 'n'));
        part(g, bevel(oval(7, 15 + dy, 9, 7, 'e'), 'x', 'E'));
        g.pxs('z', 8, 17 + dy, 9, 17 + dy);
        // skull eye sockets with a glowing cyan spark, brow ridge
        g.pxs('n', 10, 12 + dy, 11, 12 + dy, 12, 12 + dy, 16, 12 + dy, 17, 12 + dy, 18, 12 + dy);
        g.pxs('z', 10, 13 + dy, 11, 13 + dy, 12, 13 + dy, 10, 14 + dy, 11, 14 + dy, 12, 14 + dy, 16, 13 + dy, 17, 13 + dy, 18, 13 + dy);
        g.pxs('z', 16, 14 + dy, 17, 14 + dy, 18, 14 + dy);
        g.pxs('I', 11, 14 + dy, 17, 14 + dy);
        g.pxs('i', 12, 14 + dy, 18, 14 + dy);
        g.pxs('z', 8, 20 + dy, 9, 20 + dy, 10, 20 + dy, 11, 20 + dy, 12, 20 + dy, 13, 20 + dy);
        g.pxs('x', 9, 21 + dy, 11, 21 + dy, 13, 21 + dy);
        return g;
    }

    /** Wisp: tall thin dark blue-grey shadow with pale glowing eyes, swaying pale tendrils and a faint glow. */
    private static Grid wisp(int f)
    {
        Grid g = new Grid();
        int dy = 1 - f;
        double ph = f * Math.PI;
        // faint glow (a dithered halo behind the figure)
        Grid halo = new Grid();
        for (int y = 0; y < 22; y++)
        {
            for (int x = 10; x < 32; x++)
            {
                double dx = (x - 21) / 11.0;
                double dyy = (y - 9 - dy) / 11.0;
                if (dx * dx + dyy * dyy <= 1 && (x + y) % 2 == 0) halo.set(x, y + 1, 'm');
            }
        }
        with(g, halo);
        // trailing pale tendrils
        Grid t = new Grid();
        tendril(t, 17, 22 + dy, 8 - f, ph, -0.25, 'N');
        tendril(t, 21, 24 + dy, 7, ph + 1.5, 0.0, 'N');
        tendril(t, 25, 22 + dy, 8 - f, ph + 3, 0.25, 'N');
        line(t, 17, 14 + dy, 13 - f, 19 + dy, 'N');
        line(t, 13 - f, 19 + dy, 12 + f, 23 + dy, 'N');
        line(t, 25, 14 + dy, 29 + f, 19 + dy, 'N');
        line(t, 29 + f, 19 + dy, 30 - f, 23 + dy, 'N');
        part(g, t);
        g.pxs('L', 12 + f, 23 + dy, 30 - f, 23 + dy);
        // tall cloak
        part(g, bevel(trap(21, 8 + dy, 24 + dy, 2, 6, 'm'), 'M', 'z'));
        g.pxs('M', 20, 13 + dy, 20, 15 + dy, 19, 18 + dy, 18, 21 + dy);
        g.pxs('z', 23, 14 + dy, 23, 17 + dy, 24, 20 + dy);
        part(g, bevel(oval(16, 1 + dy, 10, 11, 'm'), 'M', 'z'));
        part(g, bevel(trap(23, -2 + dy, 2 + dy, 1, 3, 'm'), 'M', 'z'));
        // pale glowing eyes
        g.pxs('z', 16, 5 + dy, 17, 5 + dy, 18, 5 + dy, 19, 5 + dy, 16, 6 + dy, 17, 6 + dy, 18, 6 + dy, 19, 6 + dy, 17, 7 + dy, 18, 7 + dy);
        g.pxs('x', 17, 6 + dy, 18, 6 + dy);
        g.pxs('N', 17, 7 + dy);
        g.pxs('z', 21, 5 + dy, 22, 5 + dy, 21, 6 + dy, 22, 6 + dy);
        g.pxs('x', 21, 6 + dy);
        return g;
    }

    /** Yami: small red demon hugged by two huge curled, ridged, tan ram horns. */
    private static Grid yami(int f)
    {
        Grid g = new Grid();
        int dy = -f;
        legs2(g, 15, 22, 27, 4, 'D', 'h', 'z', f);
        Grid tail = new Grid();
        thick(tail, 'D', 27, 25 + dy, 30, 25 + dy, 31, 22 - f);
        part(g, tail);
        part(g, bevel(oval(12, 19 + dy, 17, 11, 'a'), 'A', 'h'));
        // far horn, curling over the top of the head
        Grid far = bevel(taper(path(12, 10 + dy, 9, 5 + dy, 12, 1 + dy, 18, 1 + dy, 21, 5 + dy, 19, 8 + dy), 4, 'r'), 't', 'w');
        part(g, far);
        // head
        part(g, bevel(oval(7, 9 + dy, 17, 15, 'a'), 'A', 'h'));
        // near horn: out from the brow, round the side of the head and curling forward underneath
        java.util.List<int[]> hp = path(15, 10 + dy, 20, 7 + dy, 25, 9 + dy, 26, 15 + dy, 24, 20 + dy, 20, 22 + dy, 17, 19 + dy, 18, 16 + dy);
        Grid horn = bevel(taper(hp, 4, 't'), 'T', 'r');
        for (int k = 2; k < hp.size() - 2; k += 3)
        {
            int[] p = hp.get(k);
            horn.set(p[0] + 1, p[1] + 1, 'r');
            horn.set(p[0] + 2, p[1], 'r');
        }
        part(g, horn);
        // face
        g.pxs('h', 9, 14 + dy, 10, 15 + dy, 11, 15 + dy, 12, 14 + dy);
        g.pxs('z', 9, 16 + dy, 10, 16 + dy, 11, 16 + dy, 9, 17 + dy, 10, 17 + dy, 11, 17 + dy);
        g.pxs('Y', 10, 17 + dy);
        g.pxs('z', 16, 16 + dy, 17, 16 + dy, 16, 17 + dy, 17, 17 + dy);
        g.pxs('Y', 16, 17 + dy);
        g.pxs('z', 8, 20 + dy, 9, 20 + dy, 10, 20 + dy, 11, 20 + dy, 12, 20 + dy, 13, 20 + dy);
        g.pxs('x', 9, 21 + dy, 12, 21 + dy);
        return g;
    }

    /** Youngllef: teal crystal wolf with big, thick curved crystal horns. */
    private static Grid youngllef(int f)
    {
        Grid g = new Grid();
        int dy = -f;
        Grid tail = new Grid();
        thick(tail, 'b', 30, 20 + dy, 33, 18 + dy, 34, 14 - f);
        part(g, tail);
        g.pxs('I', 34, 14 - f, 34, 15 - f);
        legs4(g, new int[] {11, 15, 26, 30}, 25, 3, 'b', 'd', 'd', f);
        Grid spikes = new Grid();
        for (int cx : new int[] {21, 25, 29})
        {
            with(spikes, bevel(trap(cx, 9 + dy + (cx - 21) / 4, 16 + dy, 1, 2.5, 'B'), 'I', 'b'));
        }
        part(g, spikes);
        part(g, bevel(oval(12, 14 + dy, 21, 14, 'b'), 'B', 'd'));
        g.pxs('B', 20, 18 + dy, 21, 19 + dy, 27, 18 + dy, 28, 19 + dy);
        part(g, bevel(oval(17, 21 + dy, 9, 6, 'B'), 'I', 'b'));
        // thick curved crystal horns
        Grid h1 = bevel(taper(path(13, 11 + dy, 9, 8 + dy, 7, 4 + dy, 8, 0 + dy), 4, 'B'), 'I', 'd');
        part(g, h1);
        Grid h2 = bevel(taper(path(17, 10 + dy, 21, 8 + dy, 22, 4 + dy, 20, 0 + dy), 4, 'B'), 'I', 'd');
        part(g, h2);
        part(g, bevel(oval(9, 8 + dy, 13, 13, 'b'), 'B', 'd'));
        part(g, bevel(oval(6, 14 + dy, 9, 7, 'B'), 'I', 'b'));
        g.pxs('z', 6, 15 + dy, 7, 15 + dy, 6, 16 + dy);
        g.pxs('d', 8, 19 + dy, 9, 19 + dy, 10, 19 + dy, 11, 19 + dy);
        g.pxs('x', 8, 20 + dy, 11, 20 + dy);
        g.pxs('z', 12, 12 + dy, 13, 12 + dy, 14, 12 + dy, 12, 13 + dy, 13, 13 + dy, 14, 13 + dy);
        g.pxs('Y', 13, 13 + dy);
        return g;
    }
}
