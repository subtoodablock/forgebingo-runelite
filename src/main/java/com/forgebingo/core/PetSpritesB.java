package com.forgebingo.core;

import com.forgebingo.core.PixelArt.Grid;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pixel art for some of the pets (see Pet): the Abyssal protector, Bloodhound, Chompy chick, Herbi, Lil' Creator,
 * Mr McGroot, Pet Penance Queen and Quetzin. Original chunky chibi art in the style of CryptSprites: big head, 1px dark
 * outline, 2-3 shades per colour, two walk frames. Facing LEFT, feet on the bottom rows, body centred on column 21.
 * Returns null for a pet this file does not draw.
 */
public final class PetSpritesB
{
    private static final Map<String, BufferedImage> CACHE = new ConcurrentHashMap<>();

    private PetSpritesB()
    {
    }

    /** One walk frame (0 or 1) on the PixelArt.WIDTH x PixelArt.HEIGHT grid, facing left, feet on the bottom rows. */
    public static BufferedImage image(Pet pet, int frame)
    {
        if (pet == null) return null;
        switch (pet)
        {
            case ABYSSAL_PROTECTOR: case BLOODHOUND: case CHOMPY_CHICK: case HERBI:
            case LIL_CREATOR: case MR_MCGROOT: case PENANCE_QUEEN: case QUETZIN:
                break;
            default:
                return null;
        }
        int f = frame == 0 ? 0 : 1;
        return CACHE.computeIfAbsent(pet + "|" + f, k -> PixelArt.render(build(pet, f), PetSpritesB::colour));
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
            case 'i': return 0x2fd0c0; // teal glow
            case 'I': return 0xa6f6ea;
            case 'J': return 0x1a8088;
            case 'p': return 0x7a4aa0; // purple
            case 'P': return 0x4a2868;
            case 'q': return 0xa87ccc;
            case 'r': return 0x8a5a36; // brown
            case 'R': return 0x4e301c;
            case 'j': return 0xb88252;
            case 't': return 0xdcbc84; // tan
            case 'T': return 0xf2e0b0;
            case 's': return 0xa88650;
            case 'g': return 0x4f9a3c; // green
            case 'G': return 0x86d05e;
            case 'H': return 0x2c6030;
            case 'm': return 0xa6c890; // pale green
            case 'M': return 0xd4ecba;
            case 'n': return 0x6f9670;
            case 'a': return 0xd8342a; // red
            case 'A': return 0xff7c50;
            case 'o': return 0xf0a020; // orange
            case 'O': return 0xffd460;
            case 'Q': return 0xb86418;
            case 'b': return 0x2aa890; // blue-green
            case 'B': return 0x70e4cc;
            case 'd': return 0x186a74;
            case 'y': return 0xf8d838; // yellow
            case 'Y': return 0xfff48a;
            case 'e': return 0xe6dcc4; // bone/cream
            case 'E': return 0xb0a488;
            case 'K': return 0x1f4a24; // far wing: dark green
            case 'L': return 0x14525c; // far wing: dark teal
            case 'N': return 0x2e7a56; // far wing tips
            case 'w': return 0x6a4a28; // wood/hoof
            case 'W': return 0x3e2a14;
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
        for (int i = 0; i <= n; i++)
        {
            m.set((int) Math.round(x0 + (x1 - x0) * (double) i / n), (int) Math.round(y0 + (y1 - y0) * (double) i / n), c);
        }
    }

    /**
     * Four legs; diagonal pairs alternate. A lifted leg swings forward (left) and is 2px shorter, a planted leg
     * is pushed back (right). Legs end on row 32.
     */
    private static void legs4(Grid g, int[] xs, int top, int w, char c, char foot, int f)
    {
        for (int i = 0; i < xs.length; i++)
        {
            boolean fwd = (i % 2 == 0) == (f == 1);
            int lift = fwd ? 2 : 0;
            int x = xs[i] + (fwd ? -1 : 1);
            Grid l = bevel(rect(x, top, w, 33 - top - lift, c), (char) 0, 'R');
            if (foot != 0) with(l, rect(x, 32 - lift, w, 1, foot));
            part(g, l);
        }
    }

    /** A tapering round-brush stroke along the polyline through the given x, y points. */
    private static Grid stroke(char c, double w0, double w1, int... p)
    {
        Grid m = new Grid();
        int segs = p.length / 2 - 1;
        int n = segs * 24;
        for (int s = 0; s <= n; s++)
        {
            double t = s / (double) n;
            double ft = t * segs;
            int k = Math.min(segs - 1, (int) ft);
            double u = ft - k;
            double x = p[2 * k] + (p[2 * k + 2] - p[2 * k]) * u + 0.5;
            double y = p[2 * k + 1] + (p[2 * k + 3] - p[2 * k + 1]) * u + 0.5;
            double r = Math.max(0.7, (w0 + (w1 - w0) * t) / 2);
            for (int j = (int) (y - r - 1); j <= y + r + 1; j++)
            {
                for (int i = (int) (x - r - 1); i <= x + r + 1; i++)
                {
                    if (Math.hypot(i + 0.5 - x, j + 0.5 - y) <= r) m.set(i, j, c);
                }
            }
        }
        return m;
    }

    private static Grid build(Pet pet, int f)
    {
        switch (pet)
        {
            case ABYSSAL_PROTECTOR: return protector(f);
            case BLOODHOUND: return bloodhound(f);
            case CHOMPY_CHICK: return chompy(f);
            case HERBI: return herbi(f);
            case LIL_CREATOR: return creator(f);
            case MR_MCGROOT: return goat(f);
            case PENANCE_QUEEN: return queen(f);
            default: return quetzin(f);
        }
    }

    // ---------------------------------------------------------------- pets

    /** Abyssal protector: faceted dark stone cone with a glowing teal band and stubby arms, hovering on a purple base. */
    private static Grid protector(int f)
    {
        Grid g = new Grid();
        int dy = -f;
        // purple base: a wobbling disc with a glossy rim
        part(g, bevel(oval(9 - f, 27, 24 + 2 * f, 7, 'p'), 'q', 'P'));
        part(g, bevel(oval(13, 25, 16, 5, 'P'), 'p', 'z'));
        g.pxs('q', 12, 29, 13, 29, 28, 30, 29, 30);
        // stubby arms that swing up and down
        part(g, bevel(oval(7, 18 + (f == 0 ? 1 : -1) + dy, 7, 5, 'c'), 'C', 'u'));
        part(g, bevel(oval(28, 18 + (f == 0 ? -1 : 1) + dy, 7, 5, 'c'), 'C', 'u'));
        // stone cone with a lit left face and a shaded right face
        Grid cone = trap(21, 2 + dy, 24 + dy, 1, 8, 'c');
        for (int y = 2 + dy; y <= 24 + dy; y++)
        {
            for (int x = 0; x < 21; x++)
            {
                if (cone.has(x, y)) cone.set(x, y, 'C');
            }
            if (cone.has(21, y)) cone.set(21, y, 'u');
        }
        part(g, cone);
        g.pxs('x', 20, 4 + dy, 20, 5 + dy, 19, 8 + dy, 18, 11 + dy);
        // glowing teal band with a bright core
        Grid band = bevel(trap(21, 20 + dy, 25 + dy, 7, 9, 'i'), 'I', 'J');
        part(g, band);
        g.pxs('I', 16, 22 + dy, 17, 22 + dy, 21, 22 + dy, 22, 22 + dy, 26, 23 + dy);
        // face: two glowing eyes and a small mouth
        g.pxs('z', 16, 13 + dy, 17, 13 + dy, 18, 13 + dy, 24, 13 + dy, 25, 13 + dy, 26, 13 + dy);
        g.pxs('i', 17, 14 + dy, 18, 14 + dy, 25, 14 + dy, 26, 14 + dy);
        g.pxs('I', 17, 14 + dy, 25, 14 + dy);
        g.pxs('u', 20, 17 + dy, 21, 17 + dy, 22, 17 + dy);
        return g;
    }

    /** Bloodhound: brown and tan dog, long droopy ear, little green hat; head bobs, legs alternate, tail wags. */
    private static Grid bloodhound(int f)
    {
        Grid g = new Grid();
        int hd = f;
        // tail wags up and down
        Grid tail = stroke('r', 3, 2, 32, 18, 35, 15 - 2 * f, 36, 12 - 3 * f);
        part(g, bevel(tail, 'j', 'R'));
        legs4(g, new int[] {8, 12, 26, 30}, 24, 3, 'r', 't', f);
        part(g, bevel(oval(11, 14, 24, 13, 'r'), 'j', 'R'));
        part(g, bevel(oval(14, 21, 16, 6, 't'), 'T', 's'));
        g.pxs('R', 18, 17, 24, 16, 29, 18, 21, 18);
        // head, tan muzzle, black nose
        part(g, bevel(oval(3, 9 + hd, 15, 14, 'r'), 'j', 'R'));
        part(g, bevel(oval(0, 16 + hd, 10, 8, 't'), 'T', 's'));
        part(g, rect(0, 16 + hd, 3, 2, 'z'));
        g.pxs('x', 1, 16 + hd);
        g.pxs('R', 3, 22 + hd, 4, 22 + hd, 5, 22 + hd, 6, 21 + hd);
        // eye with a bright spot and a worried brow
        g.pxs('z', 8, 14 + hd, 9, 14 + hd, 8, 15 + hd, 9, 15 + hd).pxs('x', 8, 14 + hd);
        g.pxs('R', 7, 12 + hd, 8, 12 + hd, 9, 13 + hd);
        // long droopy ear that swings
        part(g, bevel(oval(12, 11 + hd, 6, 15 + f, 'R'), 'r', 'W'));
        g.pxs('r', 13, 13 + hd, 13, 14 + hd);
        // little green hat with a band and feather
        Grid hat = bevel(trap(10, 3 + hd, 9 + hd, 3, 5, 'g'), 'G', 'H');
        with(hat, rect(5, 9 + hd, 11, 2, 'H'));
        part(g, hat);
        g.pxs('y', 8, 8 + hd, 9, 8 + hd, 10, 8 + hd, 11, 8 + hd);
        g.pxs('a', 13, 2 + hd, 13, 3 + hd);
        return g;
    }

    /** Chompy chick: plump tan baby bird, long neck, folded feathered wing, blue-green tail, big orange feet. */
    private static Grid chompy(int f)
    {
        Grid g = new Grid();
        int dy = -f;
        // tail feathers that flick
        Grid tail = new Grid();
        tail.stamp(29, 12 + dy - f, "..bB", "..bB", ".bBb", ".bBd", "bBbd", "bBd.");
        tail.stamp(30, 19 + dy, "bBbb", "bBb.");
        part(g, tail);
        // big webbed feet that clearly swap
        for (int i = 0; i < 2; i++)
        {
            boolean fwd = (i == 0) == (f == 1);
            int x = (i == 0 ? 11 : 20) + (fwd ? -1 : 1);
            int lift = fwd ? 2 : 0;
            Grid foot = bevel(rect(x + 2, 27, 2, 5 - lift, 'o'), (char) 0, 'Q');
            with(foot, rect(x - 1, 32 - lift, 8, 1, 'o'));
            foot.set(x - 1, 32 - lift, 'Q');
            foot.set(x + 2, 32 - lift, 'O');
            foot.set(x + 6, 32 - lift, 'Q');
            part(g, foot);
        }
        part(g, bevel(oval(10, 15 + dy, 21, 15, 't'), 'T', 's'));
        g.pxs('T', 14, 21 + dy, 15, 20 + dy);
        // folded wing: pointed back, rows of feather tips
        Grid wing = new Grid();
        wing.stamp(16, 19 + dy,
            ".TTTTTtt...",
            "TtttttttT..",
            "ttsttsttstt",
            "ssssssssss.",
            ".ssTssTss..");
        part(g, wing);
        g.pxs('R', 18, 21 + dy, 21, 21 + dy, 24, 21 + dy, 20, 22 + dy, 23, 22 + dy);
        // neck and head bob
        part(g, bevel(rect(9, 7 + dy, 5, 14, 't'), 'T', 's'));
        part(g, bevel(oval(5, 2 + dy, 12, 9, 't'), 'T', 's'));
        part(g, bevel(trap(3, 5 + dy, 8 + dy, 1, 3, 'o'), 'O', 'Q'));
        g.pxs('Q', 2, 7 + dy, 3, 7 + dy);
        g.pxs('z', 9, 5 + dy, 10, 5 + dy, 9, 6 + dy, 10, 6 + dy).pxs('x', 9, 5 + dy);
        g.pxs('A', 7, 8 + dy, 8, 8 + dy);
        g.pxs('s', 11, 2 + dy, 12, 1 + dy, 13, 2 + dy);
        return g;
    }

    /** Herbi: little boar with a back full of herbs and leaves, small tusks; head bobs, legs alternate. */
    private static Grid herbi(int f)
    {
        Grid g = new Grid();
        int hd = f;
        Grid tail = stroke('r', 2, 2, 33, 18, 35, 16 - f, 36, 14 - f);
        part(g, tail);
        legs4(g, new int[] {8, 12, 27, 31}, 25, 4, 'w', 'W', f);
        part(g, bevel(oval(11, 15, 23, 12, 'r'), 'j', 'R'));
        // herbs and leaves sprouting up the back and swaying a little
        Grid leaves = new Grid();
        with(leaves, ellipseRot(15, 12, 4.5, 2.2, -50 + 8 * f, 'g'));
        with(leaves, ellipseRot(21, 9, 4.8, 2.2, -100, 'G'));
        with(leaves, ellipseRot(27, 12, 4.5, 2.2, -135 - 8 * f, 'g'));
        with(leaves, ellipseRot(21, 14, 7, 2.6, 0, 'H'));
        part(g, leaves);
        g.pxs('G', 15, 11, 21, 7, 26, 11, 21, 8).pxs('a', 18, 13).pxs('y', 24, 13).pxs('A', 19, 12);
        g.pxs('j', 14, 17, 15, 16, 20, 16);
        part(g, bevel(oval(3, 14 + hd, 13, 12, 'r'), 'j', 'R'));
        part(g, bevel(oval(0, 19 + hd, 8, 6, 't'), 'T', 's'));
        g.pxs('z', 0, 20 + hd, 1, 20 + hd, 1, 21 + hd);
        g.pxs('x', 3, 25 + hd, 3, 24 + hd, 3, 23 + hd, 7, 25 + hd, 7, 24 + hd, 7, 23 + hd);
        g.pxs('z', 8, 18 + hd, 9, 18 + hd, 8, 19 + hd, 9, 19 + hd).pxs('x', 8, 18 + hd);
        g.pxs('R', 7, 16 + hd, 8, 16 + hd, 9, 17 + hd);
        part(g, bevel(oval(10, 11 + hd, 5, 5, 'r'), 'j', 'R'));
        g.pxs('a', 12, 13 + hd);
        return g;
    }

    /** Lil' Creator: pale green horned creature with a fierce-cute face, on little legs. */
    private static Grid creator(int f)
    {
        Grid g = new Grid();
        int hd = f;
        for (int i = 0; i < 2; i++)
        {
            boolean fwd = (i == 0) == (f == 1);
            int x = (i == 0 ? 15 : 23) + (fwd ? -1 : 1);
            int lift = fwd ? 2 : 0;
            part(g, bevel(rect(x, 28, 4, 5 - lift, 'n'), (char) 0, 'H'));
        }
        // body with a belly highlight; arms swing
        part(g, bevel(oval(14, 21, 14, 10, 'm'), 'M', 'n'));
        g.pxs('M', 18, 25, 19, 25, 20, 26);
        part(g, bevel(oval(10, 22 + f, 5, 5, 'm'), 'M', 'n'));
        part(g, bevel(oval(27, 22 + (1 - f), 5, 5, 'm'), 'M', 'n'));
        // long swept-back horns
        Grid horns = stroke('e', 4, 1, 12, 10 + hd, 8, 7 + hd, 5, 2 + hd);
        with(horns, stroke('e', 4, 1, 30, 10 + hd, 34, 7 + hd, 37, 2 + hd));
        bevel(horns, 'x', 'E');
        part(g, horns);
        // wide head
        part(g, bevel(oval(9, 6 + hd, 24, 16, 'm'), 'M', 'n'));
        g.pxs('x', 14, 8 + hd, 15, 8 + hd, 16, 7 + hd);
        // fierce eyes: bright, slanted brows
        part(g, bevel(oval(13, 12 + hd, 6, 5, 'x'), (char) 0, 'Y'));
        part(g, bevel(oval(23, 12 + hd, 6, 5, 'x'), (char) 0, 'Y'));
        g.pxs('z', 16, 13 + hd, 17, 13 + hd, 16, 14 + hd, 17, 14 + hd, 24, 13 + hd, 25, 13 + hd, 24, 14 + hd, 25, 14 + hd);
        g.pxs('H', 12, 10 + hd, 13, 10 + hd, 14, 11 + hd, 15, 11 + hd, 16, 12 + hd, 17, 12 + hd);
        g.pxs('H', 30, 10 + hd, 29, 10 + hd, 28, 11 + hd, 27, 11 + hd, 26, 12 + hd, 25, 12 + hd);
        g.pxs('n', 20, 15 + hd, 22, 15 + hd);
        // wide fanged grin and a glowing chin gem
        g.pxs('z', 16, 18 + hd, 17, 19 + hd, 18, 19 + hd, 19, 19 + hd, 20, 19 + hd, 21, 19 + hd, 22, 19 + hd, 23, 19 + hd,
            24, 19 + hd, 25, 18 + hd);
        g.pxs('x', 18, 18 + hd, 24, 18 + hd);
        g.pxs('i', 20, 21 + hd, 21, 21 + hd).pxs('I', 20, 21 + hd);
        return g;
    }

    /** Mr McGroot: brown mountain goat, big curved shaded horns, a goatee; head bobs, legs alternate. */
    private static Grid goat(int f)
    {
        Grid g = new Grid();
        int hd = f;
        Grid tail = stroke('r', 3, 2, 33, 16, 35, 14 - f, 36, 12 - f);
        part(g, tail);
        legs4(g, new int[] {11, 15, 27, 31}, 25, 2, 'r', 'W', f);
        part(g, bevel(oval(11, 14, 25, 13, 'r'), 'j', 'R'));
        part(g, bevel(oval(15, 21, 15, 5, 't'), 'T', 's'));
        g.pxs('R', 18, 17, 25, 16, 30, 18, 22, 18);
        // neck
        part(g, bevel(oval(8, 11, 10, 13, 'r'), 'j', 'R'));
        // two ram horns, thick at the base, sweeping back and curling down in a tight C; ridged, with a highlight
        Grid horn = stroke('e', 4, 2, 11, 11 + hd, 12, 6 + hd, 17, 3 + hd, 22, 6 + hd, 22, 10 + hd, 19, 12 + hd);
        bevel(horn, 'x', 'E');
        for (int[] r : new int[][] {{12, 7}, {15, 4}, {20, 4}, {22, 8}}) horn.pxs('E', r[0], r[1] + hd, r[0] + 1, r[1] + 1 + hd);
        part(g, horn);
        // goat head: round skull, long narrow muzzle sloping down to a small pale nose
        part(g, bevel(stroke('r', 9, 4, 11, 12 + hd, 8, 16 + hd, 4, 21 + hd), 'j', 'R'));
        part(g, bevel(stroke('t', 3, 3, 6, 19 + hd, 3, 21 + hd), 'T', 's'));
        part(g, bevel(oval(1, 20 + hd, 4, 3, 'T'), 'x', 's'));
        g.pxs('z', 1, 21 + hd, 2, 21 + hd);
        g.pxs('R', 5, 22 + hd, 6, 22 + hd);
        // eye set high with a horizontal slit pupil, and a sideways leaf-shaped ear
        g.pxs('R', 8, 11 + hd, 9, 11 + hd, 10, 11 + hd);
        g.pxs('y', 8, 12 + hd, 11, 12 + hd).pxs('z', 9, 12 + hd, 10, 12 + hd);
        Grid ear = ellipseRot(16.5, 13.5 + hd, 4.2, 1.7, -12, 'r');
        bevel(ear, 'j', 'R');
        part(g, ear);
        g.pxs('R', 14, 13 + hd, 15, 13 + hd, 16, 13 + hd);
        // pointed goatee
        Grid beard = new Grid();
        beard.stamp(4, 22 + hd, "ee", "xe", ".e", ".E");
        part(g, beard);
        return g;
    }

    /** Pet Penance Queen: pale slug queen: ringed tall neck, red crest, segmented bulbous base that wobbles. */
    private static Grid queen(int f)
    {
        Grid g = new Grid();
        int w = f;
        // three stacked body segments, each a little wider on alternate frames
        part(g, bevel(oval(8 - w, 26, 26 + 2 * w, 8 - w, 'm'), 'M', 'n'));
        part(g, bevel(oval(11 + w, 22, 20 - 2 * w, 7, 'm'), 'M', 'n'));
        part(g, bevel(oval(14 - w, 18, 14 + 2 * w, 7, 'm'), 'M', 'n'));
        g.pxs('M', 11, 28, 12, 28, 14, 24, 15, 24, 17, 20, 18, 20);
        g.pxs('n', 27, 30, 29, 29, 25, 26, 22, 24, 24, 21);
        // neck: stacked rows that lean with the wobble, with ring lines
        Grid neck = new Grid();
        for (int y = 10; y <= 19; y++)
        {
            int ox = (int) Math.round(f * (19 - y) / 8.0);
            for (int x = 19; x < 23; x++) neck.set(x + ox, y, y % 3 == 0 ? 'n' : (x == 19 ? 'M' : (x >= 22 ? 'n' : 'm')));
        }
        part(g, neck);
        int nx = 19 + f;
        // head with snout, eye, red three-spike crest
        part(g, bevel(oval(nx - 4, 3, 10, 8, 'm'), 'M', 'n'));
        Grid crest = new Grid();
        crest.stamp(nx - 1, 0, "a.a.a", "aAaAa", "yyyyy");
        part(g, crest);
        g.pxs('z', nx - 2, 6, nx + 1, 6);
        g.pxs('A', nx - 3, 8, nx + 2, 8);
        g.pxs('M', nx - 2, 4, nx - 1, 4);
        return g;
    }

    /** Quetzin: bright green bird, two feathered wings with teal bands and yellow tips, hopping with flapping wings. */
    private static Grid quetzin(int f)
    {
        Grid g = new Grid();
        int dy = -f;
        // far wing (behind): same fan, darker, rotated slightly
        wing(g, 19, 17 + dy, f == 0 ? -92 : -62, f == 0 ? -48 : -22, 4, 10, 13, true);
        // legs alternate while hopping
        for (int i = 0; i < 2; i++)
        {
            boolean fwd = (i == 0) == (f == 1);
            int x = (i == 0 ? 15 : 22) + (fwd ? -1 : 1);
            int lift = fwd ? 2 : 0;
            part(g, bevel(rect(x, 27 + dy, 2, 6 - dy - lift, 'o'), (char) 0, 'Q'));
            part(g, rect(x - 2, 32 - lift, 4, 1, 'o'));
        }
        // long tail feathers with teal bands
        Grid tail = new Grid();
        tail.stamp(29, 22 + dy, "..bd", ".bBd", "bBBd", "bBd.", "bd..");
        tail.stamp(28, 19 + dy, "ggg", "gG.");
        tail.stamp(31, 28 + dy, "yy", ".y");
        part(g, tail);
        part(g, bevel(oval(12, 15 + dy, 19, 14, 'g'), 'G', 'H'));
        part(g, bevel(oval(13, 22 + dy, 11, 6, 'y'), 'Y', 'o'));
        part(g, bevel(oval(6, 8 + dy, 13, 12, 'g'), 'G', 'H'));
        g.pxs('b', 10, 8 + dy, 11, 8 + dy, 12, 8 + dy).pxs('B', 11, 7 + dy, 12, 7 + dy);
        part(g, bevel(trap(4, 13 + dy, 16 + dy, 1, 3, 'o'), 'O', 'Q'));
        g.pxs('z', 9, 12 + dy, 10, 12 + dy, 9, 13 + dy, 10, 13 + dy).pxs('x', 9, 12 + dy);
        // near wing: fan of feathers, half open, flapping
        wing(g, 22, 18 + dy, f == 0 ? -70 : -38, f == 0 ? -20 : 14, 4, 12, 16, false);
        return g;
    }

    /** A fan of n feathers from a root at angles a0..a1 (degrees, 0 = straight back, negative = up). */
    private static void wing(Grid g, double bx, double by, double a0, double a1, int n, double len0, double len1, boolean far)
    {
        Grid all = new Grid();
        for (int i = n - 1; i >= 0; i--)
        {
            double t = n == 1 ? 0 : i / (double) (n - 1);
            double deg = a1 + (a0 - a1) * t;
            double len = len0 + (len1 - len0) * t;
            with(all, feather(bx, by, len, deg, far));
        }
        part(g, all);
    }

    /** One feather: green quill end, teal band, yellow tip (dark versions for the far wing). */
    private static Grid feather(double bx, double by, double len, double deg, boolean far)
    {
        Grid m = new Grid();
        double r = Math.toRadians(deg);
        double dx = Math.cos(r);
        double dy = Math.sin(r);
        for (int y = 0; y < m.h; y++)
        {
            for (int x = 0; x < m.w; x++)
            {
                double ux = x + 0.5 - bx;
                double uy = y + 0.5 - by;
                double u = ux * dx + uy * dy;
                double v = -ux * dy + uy * dx;
                if (u < 0 || u > len) continue;
                double hw = u > len * 0.7 ? 2.4 * (1 - 0.8 * (u - len * 0.7) / (len * 0.3)) : 2.4;
                if (Math.abs(v) > hw) continue;
                double t = u / len;
                boolean low = v > hw - 1.0;
                char c;
                if (far) c = t < 0.4 ? 'K' : (t < 0.7 ? 'L' : (low ? 'K' : 'N'));
                else if (t < 0.4) c = low ? 'H' : (v < -0.8 ? 'G' : 'g');
                else if (t < 0.7) c = low ? 'd' : (v < -0.8 ? 'B' : 'b');
                else c = low ? 'o' : (v < 0 ? 'Y' : 'y');
                m.set(x, y, c);
            }
        }
        return m;
    }
}
