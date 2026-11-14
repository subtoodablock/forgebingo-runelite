package com.forgebingo.core;

import com.forgebingo.core.PixelArt.Grid;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pixel art for the skilling pets (baby chinchompa, beaver, giant squirrel, heron, rift guardian, rock golem, Rocky,
 * Soup and Tangleroot). Original chunky chibi art in the style of CryptSprites: big head, 1px dark outline, 2-3 shades
 * per colour, two walk frames, drawn facing LEFT on the shared grid with the feet on the bottom rows.
 */
public final class PetSpritesA
{
    private static final Map<String, BufferedImage> CACHE = new ConcurrentHashMap<>();

    private PetSpritesA()
    {
    }

    /** One walk frame (0 or 1) on the PixelArt.WIDTH x PixelArt.HEIGHT grid, facing left, feet on the bottom rows. */
    public static BufferedImage image(Pet pet, int frame)
    {
        if (pet == null) return null;
        switch (pet)
        {
            case BABY_CHINCHOMPA: case BEAVER: case GIANT_SQUIRREL: case HERON: case RIFT_GUARDIAN:
            case ROCK_GOLEM: case ROCKY: case SOUP: case TANGLEROOT:
                break;
            default:
                return null;
        }
        int f = frame == 0 ? 0 : 1;
        return CACHE.computeIfAbsent(pet + "|" + f, k -> PixelArt.render(build(pet, f), PetSpritesA::colour));
    }

    private static int colour(int c)
    {
        switch ((char) c)
        {
            case 'k': return 0x16140f; // outline
            case 'z': return 0x1d1a22; // darkest (eyes, noses)
            case 'x': return 0xfff7e0; // white
            case 'c': return 0xb9ab8c; // chinchompa fur
            case 'C': return 0xddd2b4;
            case 'd': return 0x85785f;
            case 'p': return 0xe08c84; // pink
            case 'n': return 0x7a5232; // beaver
            case 'N': return 0xa47450;
            case 'm': return 0x49301c;
            case 'l': return 0x3b2a20;
            case 'L': return 0x5e4636;
            case 'q': return 0x45404a; // squirrel
            case 'Q': return 0x6e6874;
            case 'u': return 0x26222c;
            case 'r': return 0xb0603a;
            case 'R': return 0xdc8c58;
            case 'v': return 0x6e3420;
            case 'h': return 0xc5cbd2; // heron
            case 'H': return 0xf0f2f4;
            case 'i': return 0x8b939e;
            case 'y': return 0xe8a828;
            case 'Y': return 0xf8d060;
            case 'o': return 0xb08828;
            case 'g': return 0x8d9098; // rift guardian stone
            case 'G': return 0xb9bcc4;
            case 'D': return 0x555962;
            case 'a': return 0xff8a1e; // rune glow
            case 'A': return 0xffd04a;
            case 'f': return 0xe03a12;
            case 'b': return 0xa98a58; // rock golem
            case 'B': return 0xd2b27c;
            case 'e': return 0x6c5634;
            case 'E': return 0x8a7040;
            case 'j': return 0x8c8c96; // raccoon
            case 'J': return 0xc2c2cc;
            case 'K': return 0x585860;
            case 'S': return 0x7e7038; // turtle
            case 'T': return 0xa89a52;
            case 'U': return 0x534a26;
            case 'P': return 0x8fa044;
            case 'M': return 0xb7c860;
            case 'O': return 0x5d6e2c;
            case 'W': return 0x7c5c3a; // tangleroot
            case 'V': return 0xa67e50;
            case 'X': return 0x4d3925;
            case '1': return 0x58a840;
            case '2': return 0x94d868;
            case '3': return 0x2e6a2e;
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

    /** b painted over a. */
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

    /** Stubby legs ending on row 32; in the walk frame every other one lifts a pixel. */
    private static void feet(Grid g, int[] xs, int w, int top, char c, char light, char dark, int f)
    {
        for (int i = 0; i < xs.length; i++)
        {
            boolean lifted = (i % 2 == 0) == (f == 1);
            int lift = lifted ? 1 : 0;
            part(g, bevel(rect(xs[i] + (lifted ? 1 : 0), top, w, 33 - top - lift, c), light, dark));
        }
    }

    private static Grid build(Pet pet, int f)
    {
        switch (pet)
        {
            case BABY_CHINCHOMPA: return chinchompa(f);
            case BEAVER: return beaver(f);
            case GIANT_SQUIRREL: return squirrel(f);
            case HERON: return heron(f);
            case RIFT_GUARDIAN: return riftGuardian(f);
            case ROCK_GOLEM: return rockGolem(f);
            case ROCKY: return rocky(f);
            case SOUP: return soup(f);
            default: return tangleroot(f);
        }
    }

    // ---------------------------------------------------------------- pets

    /** A thick curving shape: {x, y, radius} control points joined by round blobs (bushy tails). */
    private static Grid blob(double[][] pts, char c, boolean scallop)
    {
        Grid m = new Grid();
        int n = 0;
        for (int s = 0; s + 1 < pts.length; s++)
        {
            for (int i = 0; i < 24; i++)
            {
                double t = i / 24.0;
                double cx = pts[s][0] + (pts[s + 1][0] - pts[s][0]) * t;
                double cy = pts[s][1] + (pts[s + 1][1] - pts[s][1]) * t;
                double r = pts[s][2] + (pts[s + 1][2] - pts[s][2]) * t;
                if (scallop && (n++ / 6) % 2 == 1) r += 1.4;
                for (int y = 0; y < m.h; y++)
                {
                    for (int x = 0; x < m.w; x++)
                    {
                        if (Math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= r) m.set(x, y, c);
                    }
                }
            }
        }
        return m;
    }

    /** Baby chinchompa: a round, fluffy tan rodent with huge round ears, a big shiny eye, whiskers and a curly tufted tail. */
    private static Grid chinchompa(int f)
    {
        Grid g = new Grid();
        double w = f * 1.5;
        Grid tail = bevel(blob(new double[][] {{27, 25, 2.2}, {31, 21, 2.8}, {34 + w * 0.3, 15, 3.2}, {34 + w, 9, 3.4}, {31 + w, 5, 2.6}}, 'c', true), 'C', 'd');
        tail.pxs('d', 33, 9, 34, 10, 31, 6, 32, 6, 33, 15);
        part(g, tail);
        feet(g, new int[] {13, 24}, 4, 29, 'd', (char) 0, 'z', f);
        Grid body = bevel(oval(10, 16, 20, 15, 'c'), 'C', 'd');
        with(body, oval(12, 24, 11, 7, 'C'));
        body.pxs('d', 20, 17, 22, 17, 24, 18, 26, 19, 27, 21, 18, 17);
        part(g, body);
        part(g, bevel(oval(7, 6, 7, 9, 'c'), 'C', 'd'));
        part(g, bevel(oval(14, 6, 7, 9, 'c'), 'C', 'd'));
        g.pxs('p', 9, 9, 9, 10, 9, 11, 16, 9, 16, 10, 16, 11);
        g.pxs('d', 8, 6, 9, 6, 15, 6, 16, 6);
        part(g, bevel(oval(4, 11, 17, 15, 'c'), 'C', 'd'));
        // big shiny eye, nose, whiskers, rosy cheek
        g.pxs('z', 8, 16, 9, 16, 8, 17, 9, 17, 8, 18, 9, 18).pxs('x', 8, 16);
        g.pxs('z', 4, 20, 4, 21).pxs('p', 5, 20);
        g.pxs('p', 10, 21, 11, 21);
        g.pxs('C', 12, 12, 13, 12, 14, 12);
        return g;
    }

    /** Beaver: round brown rodent, big shiny eye, whiskers, two big buck teeth and a wide cross-hatched paddle tail that slaps. */
    private static Grid beaver(int f)
    {
        Grid g = new Grid();
        Grid tail = bevel(oval(24, 21 + f, 14, 8, 'l'), 'L', 'z');
        for (int x = 26; x <= 36; x += 2)
        {
            for (int y = 22 + f; y <= 27 + f; y += 2) tail.set(x + (y % 4 == 0 ? 0 : 1), y, 'L');
        }
        part(g, tail);
        feet(g, new int[] {11, 14, 23}, 4, 28, 'm', (char) 0, 'z', f);
        Grid body = bevel(oval(10, 14, 20, 16, 'n'), 'N', 'm');
        with(body, oval(12, 23, 10, 6, 'N'));
        body.pxs('m', 24, 16, 26, 18, 27, 21);
        part(g, body);
        part(g, bevel(oval(14, 8, 5, 5, 'm'), 'n', 'z'));
        g.pxs('p', 15, 10);
        part(g, bevel(oval(4, 11, 16, 14, 'n'), 'N', 'm'));
        part(g, bevel(oval(2, 17, 9, 8, 'N'), 'x', 'n'));
        g.pxs('z', 2, 19, 2, 20, 3, 19);
        g.pxs('x', 5, 23, 6, 23, 5, 24, 6, 24).pxs('B', 5, 25);
        g.pxs('m', 7, 24, 8, 24);
        g.pxs('z', 9, 14, 10, 14, 9, 15, 10, 15, 9, 16, 10, 16).pxs('x', 9, 14);
        g.pxs('m', 7, 12, 8, 12, 11, 12, 12, 12);
        return g;
    }

    /** Giant squirrel: a dark little body with a huge bushy S-curved red-brown tail full of fur tufts. */
    private static Grid squirrel(int f)
    {
        Grid g = new Grid();
        double w = f * 1.2;
        Grid tail = blob(new double[][] {{26, 26, 2.6}, {31, 22, 3.6}, {34, 16, 4.4}, {33 + w * 0.5, 10, 4.4},
            {29 + w, 5, 4.0}, {24 + w, 4, 3.4}, {20 + w, 6, 2.4}}, 'r', true);
        bevel(tail, 'R', 'v');
        tail.pxs('v', 31, 19, 32, 20, 33, 14, 34, 15, 33, 9, 34, 10, 29, 6, 30, 7, 25, 6);
        tail.pxs('R', 31, 12, 32, 12, 30, 8, 29, 7, 26, 4, 27, 4, 35, 18);
        tail.pxs('x', 21 + (int) w, 5);
        part(g, tail);
        feet(g, new int[] {12, 22}, 4, 28, 'u', (char) 0, 'z', f);
        Grid body = bevel(oval(10, 19, 18, 12, 'q'), 'Q', 'u');
        with(body, oval(11, 25, 10, 5, 'Q'));
        part(g, body);
        part(g, bevel(oval(6, 14, 13, 12, 'q'), 'Q', 'u'));
        part(g, bevel(oval(10, 10, 4, 6, 'q'), 'Q', 'u'));
        part(g, bevel(oval(15, 11, 4, 5, 'q'), 'Q', 'u'));
        g.pxs('r', 11, 11, 16, 12);
        g.pxs('z', 8, 17, 9, 17, 8, 18, 9, 18, 8, 19, 9, 19).pxs('x', 8, 17);
        g.pxs('z', 6, 21, 6, 22);
        g.pxs('Q', 11, 16, 12, 16, 13, 16);
        return g;
    }

    /** Heron: a tall slim grey-white wader on long thin legs, an S-curved neck, a dagger beak and a small crest. */
    private static Grid heron(int f)
    {
        Grid g = new Grid();
        int dy = f;
        // long thin legs: one planted, one lifted and bent forward
        int ax = 18 + f;
        int bx = 22 - f;
        for (int y = 22; y <= 31; y++) g.set(ax, y, 'o');
        g.pxs('o', ax - 2, 32, ax - 1, 32, ax, 32);
        for (int y = 22; y <= 28; y++) g.set(bx, y, 'o');
        g.pxs('o', bx - 1, 29, bx - 2, 30, bx - 3, 30, bx - 4, 30);
        g.set(ax, 22, 'i');
        g.set(bx, 22, 'i');
        // tail feathers
        Grid tail = new Grid();
        tail.stamp(28, 16 + dy, "iiii", ".iiiii", "..iiii");
        part(g, tail);
        // slim body with a folded wing
        Grid body = bevel(oval(14, 13 + dy, 16, 9, 'h'), 'H', 'i');
        body.stamp(17, 15 + dy, "hiiiiiiii", "iiiiiiiiii", ".iiiiiiii");
        body.pxs('h', 20, 17 + dy, 23, 17 + dy, 26, 16 + dy);
        part(g, body);
        // S-curved neck
        Grid neck = new Grid();
        int[] nx = {14, 14, 13, 13, 12, 12, 12, 13, 13, 12, 12};
        for (int i = 0; i < nx.length; i++) neck.stamp(nx[i], 15 + dy - i, "HH");
        part(g, bevel(neck, 'H', 'h'));
        // small head, dagger beak, crest
        part(g, bevel(oval(8, 2 + dy, 7, 6, 'H'), (char) 0, 'h'));
        Grid beak = new Grid();
        beak.stamp(0, 4 + dy, "yyyyyyyy", ".yyyyyyy", "...yyyyy");
        part(g, beak);
        g.pxs('Y', 1, 4 + dy, 2, 4 + dy, 3, 4 + dy);
        g.pxs('z', 9, 4 + dy, 10, 4 + dy, 9, 5 + dy).pxs('x', 10, 3 + dy);
        g.pxs('z', 13, 2 + dy, 14, 2 + dy, 15, 3 + dy, 16, 4 + dy, 14, 3 + dy);
        g.pxs('z', 12, 8 + dy, 12, 9 + dy);
        return g;
    }

    /** Rift guardian: a small rough grey stone golem of loose lumpy rocks, a glowing rune core floating in a hollow chest. */
    private static Grid riftGuardian(int f)
    {
        Grid g = new Grid();
        int dy = f;
        // lumpy legs
        for (int i = 0; i < 2; i++)
        {
            boolean lifted = (i == 0) == (f == 1);
            int x = i == 0 ? 13 : 23;
            part(g, bevel(oval(x + (lifted ? 1 : 0), 26 - (lifted ? 1 : 0), 8, 7, 'g'), 'G', 'D'));
        }
        // floating rock arms
        part(g, bevel(oval(5, 14 + dy, 8, 8, 'g'), 'G', 'D'));
        part(g, bevel(oval(5, 23 + dy, 7, 7, 'g'), 'G', 'D'));
        part(g, bevel(oval(30, 15 + 1 - dy, 8, 8, 'g'), 'G', 'D'));
        part(g, bevel(oval(31, 24 + 1 - dy, 7, 7, 'g'), 'G', 'D'));
        // rocky body
        Grid t = oval(11, 13, 20, 15, 'g');
        with(t, oval(10, 17, 7, 7, 'g'));
        with(t, oval(25, 14, 7, 8, 'g'));
        with(t, oval(14, 22, 14, 7, 'g'));
        bevel(t, 'G', 'D');
        t.pxs('D', 13, 20, 14, 21, 28, 18, 29, 19, 15, 26, 16, 27, 27, 25, 26, 26);
        t.pxs('G', 14, 15, 15, 14, 26, 15);
        part(g, t);
        // hollow chest with the floating rune core
        part(g, oval(16, 17, 11, 9, 'z'));
        part(g, oval(18, 19, 7, 6, 'f'));
        with(g, oval(19, 20, 5, 4, 'a'));
        g.pxs('A', 21, 21, 22, 21, 21, 22).pxs('x', 21, 21);
        g.pxs('a', 17, 19, 25, 24, 18, 24);
        // lumpy head with glowing eyes
        Grid head = oval(14, 4, 14, 10, 'g');
        with(head, oval(13, 6, 5, 5, 'g'));
        with(head, oval(25, 3, 5, 5, 'g'));
        bevel(head, 'G', 'D');
        part(g, head);
        g.pxs('D', 17, 8, 18, 8, 23, 8, 24, 8);
        g.pxs('a', 17, 9, 18, 9, 23, 9, 24, 9).pxs('A', 17, 9, 23, 9);
        g.pxs('D', 20, 11, 21, 11, 22, 11, 21, 12);
        g.pxs('G', 18, 5, 19, 5, 27, 4);
        return g;
    }

    /** Rock golem: a small tan-brown boulder golem, hunched, with lumpy shoulders, big fists and a stubborn little face. */
    private static Grid rockGolem(int f)
    {
        Grid g = new Grid();
        int dy = f;
        feet(g, new int[] {13, 24}, 6, 28, 'e', 'b', 'z', f);
        part(g, bevel(oval(6, 15 + dy, 9, 9, 'b'), 'B', 'e'));
        part(g, bevel(oval(6, 22 + dy, 9, 8, 'b'), 'B', 'e'));
        part(g, bevel(oval(28, 16 + 1 - dy, 9, 9, 'b'), 'B', 'e'));
        part(g, bevel(oval(28, 23 + 1 - dy, 9, 7, 'b'), 'B', 'e'));
        Grid body = bevel(oval(11, 12, 21, 18, 'b'), 'B', 'e');
        body.pxs('B', 15, 15, 16, 14, 24, 15, 26, 17, 17, 22, 18, 22, 14, 19);
        body.pxs('e', 17, 24, 18, 25, 19, 26, 26, 21, 27, 22, 22, 28, 23, 27, 20, 18, 21, 19, 22, 20);
        part(g, body);
        Grid head = bevel(oval(13, 3, 15, 12, 'E'), 'B', 'e');
        part(g, head);
        g.pxs('e', 14, 8, 15, 8, 16, 8, 20, 8, 21, 8, 22, 8);
        g.pxs('z', 15, 9, 16, 9, 15, 10, 16, 10, 20, 9, 21, 9, 20, 10, 21, 10).pxs('x', 15, 9, 20, 9);
        g.pxs('B', 18, 5, 19, 5, 24, 6, 17, 4).pxs('e', 23, 13, 22, 13, 21, 13, 18, 12);
        g.pxs('b', 14, 4, 26, 5);
        return g;
    }

    /** Rocky: a raccoon, grey fur, black bandit mask, pointed muzzle, ringed bushy tail. */
    private static Grid rocky(int f)
    {
        Grid g = new Grid();
        // ringed tail
        Grid tail = new Grid();
        for (int i = 0; i <= 60; i++)
        {
            double t = i / 60.0;
            double cx = 28.5 + 6.5 * t + f * t * t * 1.5;
            double cy = 23 - 12 * t;
            double r = 3.4 - 0.9 * t;
            char c = Math.min(5, (int) (t * 6)) % 2 == 0 ? 'j' : 'u';
            for (int y = 0; y < 34; y++)
            {
                for (int x = 0; x < 42; x++)
                {
                    if (Math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= r) tail.set(x, y, c);
                }
            }
        }
        part(g, tail);
        feet(g, new int[] {12, 24}, 4, 29, 'K', (char) 0, 'z', f);
        Grid body = bevel(oval(10, 17, 20, 14, 'j'), 'J', 'K');
        with(body, oval(12, 25, 10, 6, 'J'));
        part(g, body);
        part(g, bevel(oval(10, 8, 5, 6, 'j'), 'J', 'K'));
        part(g, bevel(oval(16, 8, 5, 6, 'j'), 'J', 'K'));
        g.pxs('z', 12, 10, 12, 11, 18, 10, 18, 11);
        part(g, bevel(oval(5, 11, 17, 13, 'j'), 'J', 'K'));
        // muzzle, bandit mask, eye
        part(g, bevel(oval(2, 17, 9, 7, 'J'), 'x', 'j'));
        Grid mask = rect(7, 15, 12, 4, 'z');
        mask.pxs('z', 6, 16, 6, 17, 7, 19, 8, 19);
        with(g, mask);
        g.pxs('x', 9, 16, 10, 16, 9, 17).pxs('z', 10, 17);
        g.pxs('z', 2, 18, 2, 19, 3, 18);
        g.pxs('J', 14, 14, 15, 14, 16, 14);
        g.pxs('z', 4, 22, 5, 22);
        return g;
    }

    /** Soup: a turtle, brown-green plated shell, olive skin, round head and paddle flippers. */
    private static Grid soup(int f)
    {
        Grid g = new Grid();
        // tail
        part(g, bevel(trap(35, 25, 28, 1, 3, 'P'), (char) 0, 'O'));
        // back flipper
        part(g, bevel(oval(26 - f * 2, 27 + f, 8, 5, 'P'), 'M', 'O'));
        // underside rim and shell
        part(g, bevel(oval(10, 23, 24, 7, 'T'), (char) 0, 'S'));
        Grid shell = bevel(oval(9, 13, 25, 16, 'S'), 'T', 'U');
        shell.pxs('U', 21, 15, 21, 16, 21, 17, 21, 24, 21, 25, 21, 26, 22, 26);
        shell.pxs('U', 14, 18, 15, 19, 16, 20, 17, 21, 18, 22, 28, 18, 27, 19, 26, 20, 25, 21, 24, 22);
        shell.pxs('U', 16, 25, 17, 25, 18, 25, 25, 25, 26, 25, 27, 25);
        shell.pxs('T', 18, 15, 19, 15, 24, 15, 25, 16, 12, 22, 13, 21, 30, 22, 31, 23);
        part(g, shell);
        // head on a short neck
        part(g, bevel(rect(8, 21, 6, 5, 'P'), 'M', 'O'));
        part(g, bevel(oval(2, 15 + f, 10, 9, 'P'), 'M', 'O'));
        g.pxs('z', 5, 18 + f, 6, 18 + f, 5, 19 + f, 6, 19 + f).pxs('x', 5, 18 + f);
        g.pxs('O', 2, 21 + f, 3, 22 + f, 4, 22 + f);
        // front flipper
        part(g, bevel(oval(9 + f * 2, 27 + f, 8, 5, 'P'), 'M', 'O'));
        return g;
    }

    /** Tangleroot: a little walking stump with twiggy arms, root feet, mossy bark and a leafy sprout on its head. */
    private static Grid tangleroot(int f)
    {
        Grid g = new Grid();
        int dy = f;
        // root feet
        feet(g, new int[] {14, 23}, 5, 28, 'X', 'W', 'z', f);
        // twig arms
        Grid armL = new Grid();
        armL.stamp(8, 17 + dy, "WW.", ".WW", ".WW", "..W", "..W");
        armL.stamp(6, 15 + dy, "W");
        armL.stamp(6, 21 + dy, "W.W");
        part(g, armL);
        Grid armR = new Grid();
        armR.stamp(31, 18 - dy + 1, ".WW", "WW.", "WW.", "W..", "W..");
        armR.stamp(35, 16 - dy + 1, "W");
        part(g, armR);
        // trunk and head bulge
        Grid trunk = bevel(trap(21, 19, 29, 8, 9, 'W'), 'V', 'X');
        trunk.pxs('X', 16, 24, 16, 25, 16, 26, 22, 25, 22, 26, 22, 27, 27, 23, 27, 24, 20, 22);
        trunk.pxs('1', 25, 28, 26, 28, 15, 22);
        part(g, trunk);
        Grid head = bevel(oval(11, 11, 20, 14, 'W'), 'V', 'X');
        head.pxs('X', 14, 13, 15, 14, 27, 13, 26, 14, 21, 12, 22, 12);
        part(g, head);
        // face
        g.pxs('X', 14, 16, 15, 16, 16, 16, 21, 16, 22, 16, 23, 16);
        g.pxs('A', 15, 17, 16, 17, 22, 17, 23, 17).pxs('z', 15, 18, 16, 18, 22, 18, 23, 18);
        g.pxs('z', 18, 21, 19, 21, 20, 21, 17, 20, 21, 20);
        // leafy sprout
        Grid stem = new Grid();
        stem.stamp(20, 6, "W", "W", "W", "W");
        part(g, stem);
        g.pxs('W', 21, 10);
        Grid leafL = bevel(oval(10, 1 + f, 11, 6, '1'), '2', '3');
        leafL.pxs('3', 14, 4 + f, 15, 4 + f, 16, 4 + f, 17, 4 + f);
        part(g, leafL);
        Grid leafR = bevel(oval(21, 3 - f, 9, 5, '1'), '2', '3');
        leafR.pxs('3', 23, 5 - f, 24, 5 - f, 25, 5 - f);
        part(g, leafR);
        return g;
    }
}
