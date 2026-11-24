package com.forgebingo.core;

import com.forgebingo.core.PixelArt.Grid;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pixel art for the boss pets (Abyssal orphan, Aggy, Baby Mole, Baron, Beef, Bran, Butch, Callisto cub, Dom, Gull,
 * Hellpuppy, Huberte, Ikkle Hydra and Jal-Nib-Rek). Original chunky chibi art in the style of PetSpritesA: 1px dark
 * outline, 2-3 shades per colour, two walk frames, drawn facing LEFT with the feet on the bottom rows.
 * Palette letters are per pet: a/b/c = main body colour (mid, light, dark), d/e/f = second material.
 */
public final class PetSpritesC
{
    private static final Map<String, BufferedImage> CACHE = new ConcurrentHashMap<>();

    private PetSpritesC()
    {
    }

    /** One walk frame (0 or 1) on the PixelArt.WIDTH x PixelArt.HEIGHT grid, facing left, feet on the bottom rows. */
    public static BufferedImage image(Pet pet, int frame)
    {
        if (pet == null) return null;
        switch (pet)
        {
            case ABYSSAL_ORPHAN: case AGGY: case BABY_MOLE: case BARON: case BEEF: case BRAN: case BUTCH:
            case CALLISTO_CUB: case DOM: case GULL: case HELLPUPPY: case HUBERTE: case IKKLE_HYDRA: case JAL_NIB_REK:
                break;
            default:
                return null;
        }
        int f = frame == 0 ? 0 : 1;
        return CACHE.computeIfAbsent(pet + "|" + f, k -> PixelArt.render(shift(build(pet, f), centreShift(pet)), c -> colour(pet, c)));
    }

    /** Columns to move a pet so frame 0 sits centred on column 21 (same shift for both frames, so no jitter). */
    private static int centreShift(Pet pet)
    {
        Grid g = build(pet, 0);
        int lo = g.w;
        int hi = -1;
        for (int j = 0; j < g.h; j++)
        {
            for (int i = 0; i < g.w; i++)
            {
                if (g.px[j][i] == 0) continue;
                lo = Math.min(lo, i);
                hi = Math.max(hi, i);
            }
        }
        return hi < 0 ? 0 : PixelArt.WIDTH / 2 - (lo + hi + 1) / 2;
    }

    private static Grid shift(Grid g, int dx)
    {
        if (dx == 0) return g;
        Grid m = new Grid();
        for (int j = 0; j < g.h; j++)
        {
            for (int i = 0; i < g.w; i++)
            {
                if (g.px[j][i] != 0) m.set(i + dx, j, g.px[j][i]);
            }
        }
        return m;
    }

    private static int colour(Pet pet, int c)
    {
        switch ((char) c)
        {
            case 'k': return 0x16140f; // outline
            case 'z': return 0x1d1a22; // darkest (eyes, noses)
            case 'x': return 0xfff7e0; // white
            case 'X': return 0xd6cdb8; // off-white
            case 'y': return 0xf0c030; // gold / yellow glow
            case 'Y': return 0xfff088;
            case 'r': return 0xe02a1a; // red glow
            case 'R': return 0xff7a40;
            case 'p': return 0xe88c9c; // pink
            case 's': return 0xc4ccd4; // steel
            case 'S': return 0x7c8696;
            default: break;
        }
        switch (pet)
        {
            case ABYSSAL_ORPHAN:
                switch ((char) c)
                {
                    case 'a': return 0x4e4c58;
                    case 'b': return 0x7a7886;
                    case 'c': return 0x2c2a34;
                    case 'd': return 0x8c1c18;
                    default: break;
                }
                break;
            case AGGY:
                switch ((char) c)
                {
                    case 'a': return 0xa8c098; // pale grey-green skin
                    case 'b': return 0xd0e2c0;
                    case 'c': return 0x6a8060;
                    case 'd': return 0x30343c; // charcoal wing membrane
                    case 'e': return 0xb4bcb4; // bone / rim
                    case 'f': return 0x1c1f26;
                    case 'g': return 0x6a4a2a; // hilt
                    case 'h': return 0x5e6a34; // olive robe
                    case 'i': return 0x84924a;
                    case 'j': return 0x38401e;
                    default: break;
                }
                break;
            case BABY_MOLE:
                switch ((char) c)
                {
                    case 'a': return 0x80583a;
                    case 'b': return 0xa87c58;
                    case 'c': return 0x4e3322;
                    case 'd': return 0xe8dcb8; // claws
                    case 'e': return 0xb8a880;
                    default: break;
                }
                break;
            case BARON:
                switch ((char) c)
                {
                    case 'a': return 0x7a6a92;
                    case 'b': return 0xa696bc;
                    case 'c': return 0x4c3e62;
                    case 'd': return 0x3a1430; // mouth
                    case 'e': return 0xd8607c; // tongue
                    default: break;
                }
                break;
            case BEEF:
                switch ((char) c)
                {
                    case 'a': return 0x40302a;
                    case 'b': return 0x66503e;
                    case 'c': return 0x241a18;
                    case 'd': return 0xe0d0a0; // horn
                    case 'e': return 0xfff0c8;
                    case 'f': return 0x8c6c58; // snout
                    default: break;
                }
                break;
            case BRAN:
                switch ((char) c)
                {
                    case 'a': return 0x4a3830; // dark rock
                    case 'b': return 0x70584a;
                    case 'c': return 0x2a1e1c;
                    case 'd': return 0xe0501a; // fire
                    case 'e': return 0xff9a30;
                    case 'f': return 0x8a5a30; // club wood
                    default: break;
                }
                break;
            case BUTCH:
                switch ((char) c)
                {
                    case 'a': return 0xcabab0; // pale skin
                    case 'b': return 0xeee2d8;
                    case 'c': return 0x8e7e7a;
                    case 'd': return 0xb02030; // tendrils
                    case 'e': return 0xe04858;
                    case 'f': return 0x6a1424;
                    case 'g': return 0x6a4a2a; // handle
                    default: break;
                }
                break;
            case CALLISTO_CUB:
                switch ((char) c)
                {
                    case 'a': return 0x4e4e58;
                    case 'b': return 0x78788a;
                    case 'c': return 0x2c2c34;
                    case 'd': return 0xd8d0c0; // muzzle
                    case 'e': return 0xf0eadc;
                    case 'f': return 0xaaa294;
                    default: break;
                }
                break;
            case DOM:
                switch ((char) c)
                {
                    case 'a': return 0x8a3a22;
                    case 'b': return 0xb85c38;
                    case 'c': return 0x54200f;
                    case 'd': return 0x5c3a2c; // head
                    case 'e': return 0x886454;
                    case 'f': return 0x38201a;
                    case 'h': return 0xe8dcb8; // horn tips
                    default: break;
                }
                break;
            case GULL:
                switch ((char) c)
                {
                    case 'a': return 0xf2efe4;
                    case 'b': return 0xffffff;
                    case 'c': return 0xc2bfb8;
                    case 'd': return 0xf09a1c; // beak / claws
                    case 'e': return 0xffc860;
                    case 'f': return 0xb06c10;
                    case 'g': return 0x9a98a0; // wing tips
                    case 'h': return 0xd8a860; // lion hind body
                    case 'i': return 0xf0c888;
                    case 'j': return 0x9a6a30;
                    default: break;
                }
                break;
            case HELLPUPPY:
                switch ((char) c)
                {
                    case 'a': return 0x74221e;
                    case 'b': return 0xa4402c;
                    case 'c': return 0x44100e;
                    case 'd': return 0xb8745a; // pale muzzle
                    case 'e': return 0xe0a888;
                    default: break;
                }
                break;
            case HUBERTE:
                switch ((char) c)
                {
                    case 'a': return 0x3a9c9c; // teal
                    case 'b': return 0x78cec4;
                    case 'c': return 0x1e6264;
                    case 'd': return 0xe0788c; // pink feathers
                    case 'e': return 0xf8b0bc;
                    case 'f': return 0xa84862;
                    default: break;
                }
                break;
            case IKKLE_HYDRA:
                switch ((char) c)
                {
                    case 'a': return 0x2c4a30;
                    case 'b': return 0x4c7248;
                    case 'c': return 0x16261c;
                    case 'd': return 0xa8c030; // belly
                    case 'e': return 0xd2e060;
                    case 'f': return 0x6e8a1c;
                    default: break;
                }
                break;
            default: // JAL_NIB_REK
                switch ((char) c)
                {
                    case 'a': return 0x40363c;
                    case 'b': return 0x6a5c62;
                    case 'c': return 0x241c20;
                    default: break;
                }
                break;
        }
        return 0xff00ff;
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

    /** A straight stroke th pixels thick. */
    private static Grid line(int x0, int y0, int x1, int y1, int th, char c)
    {
        Grid m = new Grid();
        int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= n; i++)
        {
            double t = n == 0 ? 0 : (double) i / n;
            int x = (int) Math.round(x0 + (x1 - x0) * t);
            int y = (int) Math.round(y0 + (y1 - y0) * t);
            for (int a = 0; a < th; a++)
            {
                for (int b = 0; b < th; b++) m.set(x + a, y + b, c);
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

    /** A 2x2 eye with a white glint on its top left. */
    private static void eye(Grid g, int x, int y)
    {
        g.pxs('z', x, y, x + 1, y, x, y + 1, x + 1, y + 1).pxs('x', x, y);
    }

    private static Grid build(Pet pet, int f)
    {
        switch (pet)
        {
            case ABYSSAL_ORPHAN: return abyssal(f);
            case AGGY: return aggy(f);
            case BABY_MOLE: return mole(f);
            case BARON: return baron(f);
            case BEEF: return beef(f);
            case BRAN: return bran(f);
            case BUTCH: return butch(f);
            case CALLISTO_CUB: return callisto(f);
            case DOM: return dom(f);
            case GULL: return gull(f);
            case HELLPUPPY: return hellpuppy(f);
            case HUBERTE: return huberte(f);
            case IKKLE_HYDRA: return hydra(f);
            default: return nibRek(f);
        }
    }

    // ---------------------------------------------------------------- pets

    /** Filled polygon (even-odd) through the given corner points. */
    private static Grid poly(int[] xs, int[] ys, char c)
    {
        Grid m = new Grid();
        for (int y = 0; y < m.h; y++)
        {
            for (int x = 0; x < m.w; x++)
            {
                boolean in = false;
                for (int i = 0, j = xs.length - 1; i < xs.length; j = i++)
                {
                    if ((ys[i] > y + 0.5) != (ys[j] > y + 0.5)
                        && x + 0.5 < (xs[j] - xs[i]) * (y + 0.5 - ys[i]) / (double) (ys[j] - ys[i]) + xs[i]) in = !in;
                }
                if (in) m.set(x, y, c);
            }
        }
        return m;
    }

    /** Abyssal orphan: a dark grey demon-spider, four spread legs with red glowing tips, red eyes and chest. */
    private static Grid abyssal(int f)
    {
        Grid g = new Grid();
        int dy = f;
        int[][] legs = {{26, 20, 31, 30}, {16, 22, 12, 30}, {24, 22, 28, 30}, {14, 20, 8, 30}};
        for (int i = 0; i < legs.length; i++)
        {
            boolean up = (i % 2 == 0) == (f == 1);
            int fx = legs[i][2] + (up ? (legs[i][2] > 20 ? -1 : 1) : 0);
            int fy = legs[i][3] - (up ? 2 : 0);
            part(g, bevel(line(legs[i][0], legs[i][1], fx, fy, 2, 'a'), 'b', 'c'));
            g.pxs('d', fx, fy + 1, fx + 1, fy + 1).pxs('r', fx, fy + 1);
            g.pxs('r', fx, fy + 2, fx + 1, fy + 2);
        }
        Grid body = bevel(oval(13, 11 + dy, 16, 13, 'a'), 'b', 'c');
        body.pxs('c', 17, 14 + dy, 18, 13 + dy, 25, 14 + dy, 24, 13 + dy, 20, 12 + dy, 21, 12 + dy);
        part(g, body);
        g.pxs('d', 21, 15 + dy, 21, 16 + dy, 20, 17 + dy, 22, 17 + dy, 21, 18 + dy, 21, 19 + dy);
        g.pxs('r', 21, 16 + dy, 21, 18 + dy).pxs('R', 21, 17 + dy);
        part(g, bevel(oval(6, 9 + dy, 11, 9, 'a'), 'b', 'c'));
        part(g, line(7, 6 + dy, 8, 9 + dy, 1, 'c'));
        part(g, line(14, 6 + dy, 14, 9 + dy, 1, 'c'));
        g.pxs('r', 9, 12 + dy, 10, 12 + dy, 13, 12 + dy, 14, 12 + dy).pxs('R', 9, 12 + dy, 13, 12 + dy);
        g.pxs('z', 8, 15 + dy, 9, 15 + dy, 10, 15 + dy);
        return g;
    }

    /** Aggy: a hunched pale grey-green figure in an olive robe, with big dark ragged bony wings and a bright sword. */
    private static Grid aggy(int f)
    {
        Grid g = new Grid();
        int dy = f;
        // dark charcoal wing with a scalloped lower edge, light rim and bone line along the top
        int[] px = {15, 23, 23, 27, 25, 27, 23, 21, 18, 17};
        int[] py = {12 + dy, 5 + dy + f, 12 + dy, 14 + dy, 19 + dy, 25 + dy, 23 + dy, 28 + dy, 23 + dy, 20 + dy};
        part(g, bevel(poly(px, py, 'd'), 'e', 'f'));
        part(g, line(16, 12 + dy, 23, 6 + dy + f, 1, 'e'));
        g.pxs('e', 24, 6 + dy + f);
        int[][] tips = {{27, 14 + dy}, {27, 25 + dy}, {21, 28 + dy}};
        for (int[] t : tips)
        {
            for (int i = 0; i <= 10; i++)
            {
                int x = 17 + (t[0] - 17) * i / 10;
                int y = 13 + dy + (t[1] - 13 - dy) * i / 10;
                g.set(x, y, 'e');
            }
        }
        feet(g, new int[] {7, 12}, 4, 28, 'h', 'i', 'z', f);
        // olive robe body with a belt, pale skin showing at the neck and hands
        Grid robe = bevel(oval(6, 14 + dy, 12, 16, 'h'), 'i', 'j');
        robe.pxs('j', 8, 24 + dy, 9, 24 + dy, 10, 24 + dy, 11, 24 + dy, 12, 24 + dy, 13, 24 + dy);
        robe.pxs('y', 10, 24 + dy);
        part(g, robe);
        // hunched head, pale skin, pointed ears, dark brow and yellow eyes
        part(g, bevel(oval(3, 8 + dy, 11, 9, 'a'), 'b', 'c'));
        part(g, line(4, 5 + dy, 5, 9 + dy, 1, 'c'));
        part(g, line(11, 5 + dy, 11, 9 + dy, 1, 'c'));
        g.pxs('z', 4, 11 + dy, 5, 11 + dy, 6, 12 + dy, 9, 12 + dy, 10, 11 + dy, 11, 11 + dy);
        g.pxs('y', 5, 12 + dy, 6, 13 + dy, 10, 12 + dy, 9, 13 + dy).pxs('Y', 5, 12 + dy, 10, 12 + dy);
        g.pxs('z', 5, 13 + dy, 10, 13 + dy);
        g.pxs('c', 6, 15 + dy, 7, 15 + dy, 8, 15 + dy);
        // arm and sword
        part(g, line(8, 20 + dy, 5, 24, 2, 'a'));
        Grid blade = rect(1, 9, 2, 15, 'x');
        blade.pxs('s', 2, 12, 2, 13, 2, 14, 2, 15, 2, 16, 2, 17, 2, 18, 2, 19, 2, 20, 2, 21, 2, 22, 2, 23);
        blade.pxs('S', 1, 20, 1, 21, 1, 22, 1, 23);
        part(g, blade);
        part(g, rect(0, 24, 5, 2, 'y'));
        g.pxs('Y', 0, 24, 1, 24);
        part(g, rect(1, 26, 2, 3, 'g'));
        g.pxs('a', 3, 25);
        return g;
    }

    /** Baby Mole: a compact brown mole with a tiny eye, a pink nose and big pale digging claws. */
    private static Grid mole(int f)
    {
        Grid g = new Grid();
        int dy = f;
        part(g, bevel(oval(24, 20, 4, 4, 'a'), 'b', 'c'));
        feet(g, new int[] {16, 21}, 4, 28, 'c', (char) 0, 'z', f);
        part(g, bevel(oval(9, 13 + dy, 17, 16, 'a'), 'b', 'c'));
        part(g, bevel(oval(3, 11 + dy, 13, 14, 'a'), 'b', 'c'));
        g.pxs('b', 12, 15 + dy, 13, 15 + dy, 14, 16 + dy, 18, 16 + dy, 19, 17 + dy);
        part(g, oval(0, 17 + dy, 6, 6, 'p'));
        g.pxs('x', 1, 18 + dy).pxs('z', 2, 21 + dy, 3, 21 + dy).pxs('R', 1, 19 + dy);
        g.pxs('z', 8, 16 + dy, 8, 17 + dy);
        // big digging arm and claws, scratching
        int ax = 2 + f * 2;
        int ay = 23 - f;
        part(g, bevel(oval(ax + 1, ay, 9, 6, 'a'), 'b', 'c'));
        part(g, bevel(line(ax, ay + 4, ax - 1, 31 - f, 2, 'd'), 'x', 'e'));
        part(g, bevel(line(ax + 3, ay + 5, ax + 3, 32 - f, 2, 'd'), 'x', 'e'));
        part(g, bevel(line(ax + 6, ay + 5, ax + 7, 32 - f, 2, 'd'), 'x', 'e'));
        return g;
    }

    /** Baron: a wide purple-grey toad head with a huge toothy grin, bulging eyes and tiny legs. */
    private static Grid baron(int f)
    {
        Grid g = new Grid();
        int dy = f;
        feet(g, new int[] {10, 14, 25, 29}, 3, 29, 'c', (char) 0, 'z', f);
        part(g, bevel(oval(6, 10 + dy, 30, 20, 'a'), 'b', 'c'));
        part(g, bevel(oval(8, 5 + dy, 9, 8, 'a'), 'b', 'c'));
        part(g, bevel(oval(24, 5 + dy, 9, 8, 'a'), 'b', 'c'));
        g.pxs('y', 10, 8 + dy, 11, 8 + dy, 10, 9 + dy, 11, 9 + dy, 26, 8 + dy, 27, 8 + dy, 26, 9 + dy, 27, 9 + dy);
        g.pxs('z', 10, 9 + dy, 11, 9 + dy, 26, 9 + dy, 27, 9 + dy).pxs('Y', 10, 8 + dy, 26, 8 + dy);
        Grid mouth = rect(8, 18 + dy, 26, 8, 'd');
        mouth.pxs('d', 7, 19 + dy, 34, 19 + dy);
        with(g, mouth);
        for (int x = 9; x <= 32; x += 3)
        {
            g.pxs('x', x, 18 + dy, x + 1, 18 + dy, x, 19 + dy);
            g.pxs('x', x + 1, 25 + dy, x + 2, 25 + dy, x + 2, 24 + dy);
        }
        g.pxs('e', 16, 23 + dy, 17, 23 + dy, 18, 23 + dy, 19, 23 + dy, 20, 23 + dy, 21, 23 + dy, 22, 23 + dy);
        g.pxs('e', 17, 22 + dy, 18, 22 + dy, 19, 22 + dy, 20, 22 + dy);
        g.pxs('c', 20, 14 + dy, 21, 14 + dy, 14, 15 + dy, 28, 15 + dy, 18, 12 + dy, 24, 12 + dy);
        g.pxs('b', 21, 13 + dy, 15, 14 + dy, 27, 14 + dy, 10, 13 + dy, 31, 13 + dy);
        return g;
    }

    /** Beef: a compact dark brown-black bull calf with pale curved horns and a lighter snout. */
    private static Grid beef(int f)
    {
        Grid g = new Grid();
        int dy = f;
        part(g, line(26, 16 + dy, 28, 22, 1, 'c'));
        part(g, bevel(oval(27, 21, 3, 4, 'a'), 'b', 'c'));
        feet(g, new int[] {9, 13, 20, 24}, 3, 25, 'a', 'b', 'c', f);
        g.pxs('d', 9, 32, 13, 32, 20, 32, 24, 32);
        part(g, bevel(oval(8, 13 + dy, 19, 13, 'a'), 'b', 'c'));
        part(g, bevel(oval(8, 10 + dy, 10, 8, 'a'), 'b', 'c'));
        part(g, bevel(oval(2, 13 + dy, 12, 11, 'a'), 'b', 'c'));
        part(g, bevel(oval(0, 18 + dy, 7, 6, 'f'), 'e', 'c'));
        g.pxs('z', 1, 20 + dy, 1, 21 + dy, 4, 20 + dy);
        part(g, line(4, 13 + dy, 2, 8 + dy, 2, 'd'));
        part(g, line(11, 13 + dy, 13, 8 + dy, 2, 'd'));
        g.pxs('e', 2, 8 + dy, 13, 8 + dy, 3, 10 + dy, 12, 10 + dy);
        part(g, bevel(oval(10, 15 + dy, 4, 3, 'b'), (char) 0, 'c'));
        eye(g, 6, 16 + dy);
        g.pxs('b', 15, 14 + dy, 16, 14 + dy, 21, 15 + dy, 22, 16 + dy);
        return g;
    }

    /** Bran: a slim fire-and-rock titan: dark rock armour, glowing red-orange body, flame hair, flaming torch-club. */
    private static Grid bran(int f)
    {
        Grid g = new Grid();
        int dy = f;
        int fl = f == 1 ? 1 : 0;
        feet(g, new int[] {16, 22}, 4, 25, 'a', 'b', 'c', f);
        // back arm
        part(g, bevel(line(26, 16 + dy, 28, 23 - dy, 2, 'd'), 'e', 'a'));
        // glowing torso with rock armour plates
        Grid torso = bevel(trap(21, 13 + dy, 25, 7, 5, 'd'), 'e', 'a');
        torso.pxs('R', 20, 16 + dy, 21, 17 + dy, 21, 18 + dy, 22, 19 + dy, 21, 20 + dy, 20, 21 + dy);
        torso.pxs('a', 15, 13 + dy, 16, 13 + dy, 26, 13 + dy, 27, 13 + dy, 15, 14 + dy, 27, 14 + dy);
        torso.pxs('c', 16, 24 + dy, 17, 24 + dy, 18, 24 + dy, 19, 24 + dy, 20, 24 + dy, 21, 24 + dy, 22, 24 + dy, 23, 24 + dy, 24, 24 + dy, 25, 24 + dy);
        part(g, torso);
        // head: dark rock with an ember face and flame hair
        part(g, bevel(oval(14, 4 + dy, 12, 11, 'a'), 'b', 'c'));
        Grid flame = new Grid();
        flame.stamp(16, 0 + dy + fl, ".d.d.", "ddedd", "deeed");
        part(g, flame);
        g.pxs('y', 16, 8 + dy, 17, 8 + dy, 21, 8 + dy, 22, 8 + dy).pxs('d', 16, 9 + dy, 17, 9 + dy, 21, 9 + dy, 22, 9 + dy);
        g.pxs('d', 18, 12 + dy, 19, 12 + dy, 20, 12 + dy);
        // front arm and torch-club topped with fire
        part(g, bevel(line(15, 16 + dy, 9, 20, 3, 'd'), 'e', 'a'));
        part(g, bevel(rect(6, 10, 3, 18, 'f'), 'b', 'c'));
        Grid fire = oval(3, 3 + fl, 9, 9, 'd');
        with(fire, oval(5, 5 + fl, 5, 5, 'e'));
        fire.pxs('d', 7, 2 + fl, 6, 1 + fl);
        part(g, fire);
        g.pxs('y', 7, 7 + fl, 6, 6 + fl);
        g.pxs('a', 7, 20, 8, 21);
        return g;
    }

    /** Butch: a pale muscular executioner with a red-eyed mask, red tendrils and a big axe. */
    private static Grid butch(int f)
    {
        Grid g = new Grid();
        int dy = f;
        int w = f == 1 ? 1 : 0;
        part(g, line(26, 14 + dy, 31, 10 + w, 2, 'd'));
        part(g, line(31, 10 + w, 33, 8 + w, 1, 'd'));
        part(g, line(26, 18 + dy, 33, 17 - w, 2, 'd'));
        part(g, line(25, 22 + dy, 31, 26 + w, 2, 'd'));
        g.pxs('e', 28, 12 + dy, 31, 17 - w, 29, 24 + w);
        feet(g, new int[] {15, 22}, 5, 26, 'c', 'a', 'z', f);
        Grid torso = bevel(trap(21, 13 + dy, 27, 9, 6, 'a'), 'b', 'c');
        torso.pxs('c', 19, 16 + dy, 20, 16 + dy, 21, 16 + dy, 20, 19 + dy, 20, 20 + dy);
        part(g, torso);
        g.pxs('f', 16, 25, 17, 25, 18, 25, 19, 25, 20, 25, 21, 25, 22, 25, 23, 25, 24, 25, 25, 25);
        part(g, bevel(oval(13, 3 + dy, 13, 11, 'a'), 'b', 'c'));
        part(g, line(15, 1 + dy, 14, 4 + dy, 1, 'd'));
        part(g, line(19, 0 + dy, 19, 3 + dy, 1, 'd'));
        part(g, line(23, 1 + dy, 24, 4 + dy, 1, 'd'));
        with(g, rect(13, 7 + dy, 12, 3, 'f'));
        g.pxs('r', 14, 8 + dy, 15, 8 + dy, 19, 8 + dy, 20, 8 + dy).pxs('R', 14, 8 + dy, 19, 8 + dy);
        g.pxs('c', 16, 12 + dy, 17, 12 + dy, 18, 12 + dy);
        part(g, bevel(line(14, 15 + dy, 9, 20, 3, 'a'), 'b', 'c'));
        part(g, rect(7, 8, 2, 21, 'g'));
        Grid blade = new Grid();
        blade.stamp(1, 5, "..ss", ".sss", "ssss", "ssss", "ssss", "ssss", ".sss", "..ss");
        blade.pxs('x', 1, 7, 1, 8, 1, 9, 1, 10, 2, 6, 2, 11);
        blade.pxs('S', 3, 6, 4, 7, 4, 8, 4, 9, 4, 10, 3, 11);
        part(g, blade);
        part(g, rect(6, 17, 4, 2, 'a'));
        g.pxs('b', 6, 17, 7, 17);
        return g;
    }

    /** Callisto cub: a chunky dark grey bear cub with round ears, a pale muzzle and a pale chest. */
    private static Grid callisto(int f)
    {
        Grid g = new Grid();
        int dy = f;
        part(g, bevel(oval(31, 19, 5, 5, 'a'), 'b', 'c'));
        feet(g, new int[] {11, 15, 25, 29}, 4, 27, 'c', 'a', 'z', f);
        part(g, bevel(oval(10, 14 + dy, 24, 16, 'a'), 'b', 'c'));
        part(g, bevel(oval(10, 22 + dy, 9, 7, 'd'), 'e', 'f'));
        part(g, bevel(oval(4, 6 + dy, 6, 6, 'a'), 'b', 'c'));
        part(g, bevel(oval(14, 6 + dy, 6, 6, 'a'), 'b', 'c'));
        g.pxs('p', 6, 8 + dy, 17, 8 + dy, 6, 9 + dy);
        part(g, bevel(oval(3, 9 + dy, 17, 14, 'a'), 'b', 'c'));
        part(g, bevel(oval(2, 16 + dy, 9, 7, 'd'), 'e', 'f'));
        g.pxs('z', 3, 17 + dy, 4, 17 + dy, 3, 18 + dy);
        g.pxs('z', 6, 21 + dy, 7, 21 + dy);
        eye(g, 10, 14 + dy);
        eye(g, 15, 14 + dy);
        return g;
    }

    /** Dom: a compact red-brown armoured beetle with a plated shell, gold eyes and pale horn tips. */
    private static Grid dom(int f)
    {
        Grid g = new Grid();
        int dy = f;
        feet(g, new int[] {9, 13, 20, 24}, 3, 27, 'f', 'e', 'z', f);
        part(g, line(4, 15 + dy, 2, 10 + dy, 2, 'h'));
        part(g, line(9, 14 + dy, 11, 9 + dy, 2, 'h'));
        part(g, bevel(oval(2, 14 + dy, 11, 11, 'd'), 'e', 'f'));
        g.pxs('y', 4, 18 + dy, 5, 18 + dy, 4, 19 + dy, 5, 19 + dy, 9, 18 + dy, 10, 18 + dy, 9, 19 + dy, 10, 19 + dy);
        g.pxs('Y', 4, 18 + dy, 9, 18 + dy).pxs('z', 5, 19 + dy, 10, 19 + dy);
        g.pxs('f', 5, 22 + dy, 6, 22 + dy, 7, 22 + dy, 8, 22 + dy);
        Grid shell = bevel(oval(8, 8 + dy, 20, 20, 'a'), 'b', 'c');
        for (int y = 11; y <= 25; y++) shell.set(18, y + dy, 'c');
        shell.pxs('c', 13, 13 + dy, 14, 14 + dy, 14, 15 + dy, 14, 16 + dy, 13, 17 + dy);
        shell.pxs('c', 23, 13 + dy, 22, 14 + dy, 22, 15 + dy, 22, 16 + dy, 23, 17 + dy);
        shell.pxs('b', 15, 11 + dy, 16, 11 + dy, 21, 11 + dy, 22, 12 + dy);
        part(g, shell);
        g.pxs('y', 13, 10 + dy, 14, 10 + dy, 22, 10 + dy, 23, 10 + dy, 11, 22 + dy, 25, 22 + dy);
        g.pxs('y', 17, 17 + dy, 19, 17 + dy, 17, 18 + dy, 19, 18 + dy, 18, 16 + dy, 18, 19 + dy);
        g.pxs('Y', 17, 17 + dy);
        return g;
    }

    /** Gull: a small white gryphon chick: eagle head, beak, wings and claws at the front, a short tawny lion hind body and tufted tail. */
    private static Grid gull(int f)
    {
        Grid g = new Grid();
        int dy = f;
        part(g, line(21, 23 + dy, 24, 20 + dy, 1, 'j'));
        part(g, bevel(oval(23, 15 + dy, 4, 7, 'h'), 'i', 'j'));
        feet(g, new int[] {16, 20}, 4, 27, 'h', 'i', 'j', f);
        feet(g, new int[] {6, 10}, 3, 27, 'd', 'e', 'f', 1 - f);
        part(g, bevel(oval(13, 18 + dy, 10, 10, 'h'), 'i', 'j'));
        g.pxs('j', 17, 22 + dy, 18, 23 + dy, 20, 21 + dy);
        part(g, bevel(oval(5, 14 + dy, 12, 13, 'a'), 'b', 'c'));
        Grid wing = bevel(oval(10, 12 + dy, 11, 8, 'b'), 'x', 'c');
        wing.pxs('c', 13, 15 + dy, 15, 16 + dy, 17, 16 + dy);
        wing.pxs('g', 20, 12 + dy - f, 21, 13 + dy - f, 19, 11 + dy - f);
        part(g, wing);
        part(g, bevel(oval(2, 4 + dy, 13, 12, 'a'), 'b', 'c'));
        part(g, line(7, 1 + dy, 8, 4 + dy, 1, 'c'));
        part(g, line(11, 1 + dy, 11, 4 + dy, 1, 'c'));
        Grid beak = bevel(oval(0, 9 + dy, 7, 6, 'd'), 'e', 'f');
        beak.pxs('f', 0, 14 + dy, 0, 15 + dy, 1, 15 + dy);
        beak.pxs('d', 0, 13 + dy, 1, 14 + dy);
        part(g, beak);
        eye(g, 6, 8 + dy);
        g.pxs('f', 3, 12 + dy, 4, 12 + dy);
        return g;
    }

    /** Hellpuppy: a compact dark red three-headed puppy; each head has a pale muzzle and glowing eyes. */
    private static Grid hellpuppy(int f)
    {
        Grid g = new Grid();
        int dy = f;
        part(g, line(27, 20 + dy, 30, 14 + dy - f, 2, 'a'));
        feet(g, new int[] {12, 15, 22, 25}, 3, 26, 'a', 'b', 'c', f);
        part(g, bevel(oval(11, 17 + dy, 17, 11, 'a'), 'b', 'c'));
        // right head (back), middle head, then the main head in front
        part(g, bevel(rect(18, 11 + dy, 4, 9, 'a'), 'b', 'c'));
        part(g, bevel(oval(17, 3 + dy, 10, 9, 'a'), 'b', 'c'));
        part(g, bevel(oval(14, 6 + dy, 6, 5, 'd'), 'e', 'c'));
        part(g, line(24, 1 + dy, 24, 4 + dy, 1, 'b'));
        g.pxs('y', 19, 5 + dy, 20, 5 + dy).pxs('Y', 19, 5 + dy).pxs('z', 14, 8 + dy);
        part(g, bevel(rect(10, 12 + dy, 4, 8, 'a'), 'b', 'c'));
        part(g, bevel(oval(7, 4 + dy, 9, 9, 'a'), 'b', 'c'));
        part(g, bevel(oval(3, 8 + dy, 6, 5, 'd'), 'e', 'c'));
        part(g, line(8, 1 + dy, 9, 4 + dy, 1, 'b'));
        g.pxs('y', 10, 7 + dy, 11, 7 + dy).pxs('Y', 10, 7 + dy).pxs('z', 3, 10 + dy);
        part(g, bevel(oval(2, 15 + dy, 11, 11, 'a'), 'b', 'c'));
        part(g, bevel(oval(0, 19 + dy, 7, 6, 'd'), 'e', 'c'));
        part(g, line(3, 13 + dy, 4, 16 + dy, 1, 'b'));
        part(g, line(10, 13 + dy, 10, 16 + dy, 1, 'b'));
        g.pxs('z', 0, 20 + dy, 1, 20 + dy);
        g.pxs('y', 6, 18 + dy, 7, 18 + dy, 10, 18 + dy, 11, 18 + dy).pxs('Y', 6, 18 + dy, 10, 18 + dy);
        g.pxs('r', 6, 19 + dy, 10, 19 + dy).pxs('x', 3, 24 + dy, 5, 24 + dy);
        return g;
    }

    /** Huberte: a small coiled feathered serpent, teal scales, a pink feather frill and a fanged head. */
    private static Grid huberte(int f)
    {
        Grid g = new Grid();
        int dy = f;
        part(g, line(23, 29, 25, 27 - f, 2, 'a'));
        part(g, line(25, 27 - f, 26, 23 - f, 1, 'a'));
        g.pxs('d', 26, 22 - f, 25, 22 - f, 26, 21 - f).pxs('e', 26, 21 - f);
        Grid c1 = bevel(oval(6, 24, 21, 9, 'a'), 'b', 'c');
        for (int x = 9; x < 25; x += 4) c1.pxs('c', x, 27, x + 1, 28);
        part(g, c1);
        Grid c2 = bevel(oval(9, 19 + dy, 16, 8, 'a'), 'b', 'c');
        for (int x = 12; x < 22; x += 4) c2.pxs('c', x, 22 + dy, x + 1, 23 + dy);
        part(g, c2);
        part(g, bevel(rect(8, 13 + dy, 6, 9, 'a'), 'b', 'c'));
        int[][] fan = {{11, 4}, {15, 3}, {19, 6}, {20, 11}, {18, 15}};
        for (int[] t : fan)
        {
            part(g, bevel(line(9, 11 + dy, t[0], t[1] + dy, 3, 'd'), 'e', 'f'));
            g.pxs('e', t[0], t[1] + dy);
        }
        part(g, bevel(oval(1, 6 + dy, 14, 11, 'a'), 'b', 'c'));
        part(g, bevel(oval(1, 11 + dy, 7, 5, 'a'), 'b', 'c'));
        g.pxs('d', 2, 5 + dy, 3, 4 + dy, 4, 5 + dy, 3, 5 + dy);
        eye(g, 6, 9 + dy);
        g.pxs('y', 7, 9 + dy, 7, 10 + dy);
        g.pxs('z', 1, 12 + dy, 2, 14 + dy, 3, 14 + dy);
        g.pxs('x', 1, 15 + dy, 4, 15 + dy);
        return g;
    }

    /** Ikkle Hydra: a compact dark green baby hydra, yellow-green belly, two heads and a short tail. */
    private static Grid hydra(int f)
    {
        Grid g = new Grid();
        int dy = f;
        part(g, bevel(line(24, 21 + dy, 28, 24 - f, 3, 'a'), 'b', 'c'));
        feet(g, new int[] {10, 13, 20, 23}, 3, 26, 'a', 'b', 'c', f);
        part(g, bevel(oval(9, 16 + dy, 17, 12, 'a'), 'b', 'c'));
        with(g, bevel(oval(10, 22 + dy, 14, 5, 'd'), 'e', 'f'));
        g.pxs('f', 15, 24 + dy, 19, 25 + dy, 22, 24 + dy);
        g.pxs('c', 15, 17 + dy, 19, 17 + dy, 23, 18 + dy);
        // rear head, taller
        part(g, bevel(line(12, 17 + dy, 13, 10 + dy, 4, 'a'), 'b', 'c'));
        part(g, bevel(oval(8, 3 + dy, 12, 8, 'a'), 'b', 'c'));
        part(g, bevel(oval(5, 6 + dy, 7, 5, 'a'), 'b', 'c'));
        g.pxs('y', 13, 6 + dy, 14, 6 + dy).pxs('z', 14, 7 + dy);
        g.pxs('d', 6, 10 + dy, 8, 10 + dy).pxs('c', 6, 9 + dy, 7, 9 + dy, 8, 9 + dy);
        g.pxs('f', 17, 4 + dy, 18, 6 + dy, 17, 8 + dy);
        // front head, low
        part(g, bevel(line(10, 20 + dy, 7, 15 + dy, 4, 'a'), 'b', 'c'));
        part(g, bevel(oval(1, 12 + dy, 10, 8, 'a'), 'b', 'c'));
        part(g, bevel(oval(0, 16 + dy, 6, 5, 'a'), 'b', 'c'));
        g.pxs('y', 7, 15 + dy, 8, 15 + dy).pxs('z', 8, 16 + dy);
        g.pxs('d', 1, 20 + dy, 3, 20 + dy).pxs('c', 1, 18 + dy, 2, 18 + dy, 3, 18 + dy, 4, 18 + dy);
        g.pxs('f', 9, 11 + dy, 10, 13 + dy);
        return g;
    }

    /** Jal-Nib-Rek: a dark segmented lava grub with a glowing red seam and tusks; it inches along, arching its back. */
    private static Grid nibRek(int f)
    {
        Grid g = new Grid();
        // frame 0: stretched out low; frame 1: bunched up with the middle arched
        int[] sx = f == 0 ? new int[] {22, 14, 6} : new int[] {19, 12, 6};
        int[] sy = f == 0 ? new int[] {19, 17, 15} : new int[] {15, 12, 14};
        int tail = f == 0 ? 28 : 25;
        part(g, bevel(oval(tail, 24 + (f == 0 ? 0 : -2), 6, 7, 'a'), 'b', 'c'));
        int[] lx = f == 0 ? new int[] {9, 13, 22, 26} : new int[] {9, 12, 21, 24};
        feet(g, lx, 3, 29, 'c', (char) 0, 'z', 0);
        part(g, bevel(oval(sx[0], sy[0] + 3, 10, 11, 'a'), 'b', 'c'));
        part(g, bevel(oval(sx[1], sy[1] + 3, 11, 12, 'a'), 'b', 'c'));
        g.pxs('r', sx[0] + 4, sy[0] + 4, sx[0] + 5, sy[0] + 4, sx[1] + 5, sy[1] + 4, sx[1] + 6, sy[1] + 4);
        // head
        int hy = sy[2] + 3;
        part(g, bevel(oval(sx[2] - 2, hy, 13, 14, 'a'), 'b', 'c'));
        int hx = sx[2] - 2;
        g.pxs('r', hx + 4, hy + 1, hx + 5, hy + 1, hx + 6, hy + 1, hx + 7, hy + 1, hx + 8, hy + 2);
        g.pxs('R', hx + 5, hy + 1, hx + 7, hy + 1);
        g.pxs('r', hx + 3, hy + 5, hx + 4, hy + 5, hx + 8, hy + 5, hx + 9, hy + 5).pxs('R', hx + 3, hy + 5, hx + 8, hy + 5);
        g.pxs('z', hx + 3, hy + 6, hx + 4, hy + 6, hx + 8, hy + 6, hx + 9, hy + 6);
        g.pxs('z', hx + 3, hy + 10, hx + 4, hy + 10, hx + 5, hy + 10, hx + 6, hy + 10, hx + 7, hy + 10);
        part(g, line(hx, hy + 6, hx, hy + 11, 1, 'x'));
        part(g, line(hx + 10, hy + 8, hx + 10, hy + 11, 1, 'x'));
        // glowing seam along the back between segments
        g.pxs('r', sx[1] + 1, sy[1] + 6, sx[1] + 1, sy[1] + 8, sx[1] + 1, sy[1] + 10, sx[0] + 1, sy[0] + 7, sx[0] + 1, sy[0] + 9);
        g.pxs('R', sx[1] + 1, sy[1] + 8, sx[0] + 1, sy[0] + 8);
        return g;
    }
}
