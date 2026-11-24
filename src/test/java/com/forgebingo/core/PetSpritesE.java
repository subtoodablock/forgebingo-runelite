package com.forgebingo.core;

import com.forgebingo.core.PixelArt.Grid;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pixel art for the boss pets Dagannoth Rex and Supreme, dark core, General Graardor, K'ril Tsutsaroth, Kraken,
 * Kree'arra, Smoke Devil, Snakeling, Zilyana, Phoenix, Prince Black Dragon, Scorpia's offspring and Scurry. Original
 * chunky chibi art in the style of PetSpritesA: big head, 1px dark outline, 2-3 shades per colour, two walk frames,
 * drawn facing LEFT on the shared grid. Returns null for a pet this file does not draw.
 */
public final class PetSpritesE
{
    private static final Map<String, BufferedImage> CACHE = new ConcurrentHashMap<>();

    private PetSpritesE()
    {
    }

    /** One walk frame (0 or 1) on the PixelArt.WIDTH x PixelArt.HEIGHT grid, facing left, feet on the bottom rows. */
    public static BufferedImage image(Pet pet, int frame)
    {
        if (pet == null) return null;
        switch (pet)
        {
            case DAGANNOTH_REX: case DAGANNOTH_SUPREME: case DARK_CORE: case GENERAL_GRAARDOR: case KRIL_TSUTSAROTH:
            case KRAKEN: case KREEARRA: case SMOKE_DEVIL: case SNAKELING: case ZILYANA: case PHOENIX:
            case PRINCE_BLACK_DRAGON: case SCORPIAS_OFFSPRING: case SCURRY:
                break;
            default:
                return null;
        }
        int f = frame == 0 ? 0 : 1;
        return CACHE.computeIfAbsent(pet + "|" + f, k -> PixelArt.render(build(pet, f), PetSpritesE::colour));
    }

    private static int colour(int c)
    {
        switch ((char) c)
        {
            case 'k': return 0x16140f; // outline
            case 'z': return 0x1d1a22; // darkest
            case 'x': return 0xfff7e0; // white
            case 't': return 0xc4a45a; // dagannoth rex
            case 'T': return 0xe6cc84;
            case 'u': return 0x8a6c34;
            case 's': return 0x5e9a5a; // dagannoth supreme
            case 'S': return 0x90c888;
            case 'r': return 0x366838;
            case 'q': return 0xd8d0a8; // dagannoth belly
            case 'E': return 0xf8d040; // yellow eye
            case 'v': return 0x5a3a82; // dark core
            case 'V': return 0x8a64b8;
            case 'w': return 0x33204f;
            case 'W': return 0xb890e0;
            case '9': return 0xff80d8;
            case 'o': return 0x6b8c4a; // graardor
            case 'O': return 0x94b868;
            case 'n': return 0x3f5a2e;
            case 'm': return 0x8a8a92;
            case 'M': return 0xb8b8c0;
            case 'N': return 0x55555e;
            case 'b': return 0x7a5632;
            case 'R': return 0xc0382c; // k'ril
            case 'e': return 0xe8664a;
            case 'D': return 0x7a1c1c;
            case 'h': return 0xe8dcb0;
            case 'H': return 0xb8a878;
            case 'F': return 0x8a2020;
            case 'y': return 0xb8b030; // kraken
            case 'Y': return 0xe0d860;
            case 'l': return 0x7c7418;
            case 'c': return 0x8e8e98; // kree'arra
            case 'C': return 0xbbbbc4;
            case 'd': return 0x5a5a66;
            case 'a': return 0xe0a828;
            case 'A': return 0xf8d060;
            case 'B': return 0xb08828;
            case 'g': return 0x7a8a70; // smoke devil
            case 'G': return 0xa8b8a0;
            case 'j': return 0x4a5a48;
            case 'Z': return 0xf0c020;
            case 'X': return 0xffe870;
            case 'p': return 0x9a9aa4;
            case '1': return 0x4e9a3a; // snakeling
            case '2': return 0x86cc5c;
            case '3': return 0x2c6a28;
            case '4': return 0xd8e090;
            case '5': return 0xc8c030;
            case '6': return 0x8a3a24; // zilyana
            case '7': return 0xb05a38;
            case '8': return 0xf0c8a0;
            case '0': return 0xd09878;
            case 'i': return 0xd8dce8;
            case 'I': return 0xf4f6fc;
            case 'L': return 0xa0a8c0;
            case 'K': return 0xc0c8d8;
            case 'f': return 0xd83a14; // phoenix
            case 'P': return 0xf07818;
            case 'Q': return 0xffb83a;
            case 'U': return 0x3a3a46; // black dragon
            case '@': return 0x62627a;
            case '$': return 0x232330;
            case '&': return 0x4a3050;
            case '[': return 0x5a5a66; // scorpion
            case ']': return 0x86869a;
            case '<': return 0x36363e;
            case '>': return 0xd8c040;
            case '{': return 0x8a6038; // scurry
            case '}': return 0xb08458;
            case '/': return 0x5a3a22;
            case '*': return 0xe0908c;
            case '+': return 0x8ec8e0;
            case '=': return 0xc8ecf8;
            case '-': return 0x5a90b0;
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

    /** A thick polyline through x, y pairs. */
    private static Grid chain(char c, int th, int... xy)
    {
        Grid m = new Grid();
        for (int i = 0; i + 3 < xy.length; i += 2)
        {
            int x0 = xy[i];
            int y0 = xy[i + 1];
            int x1 = xy[i + 2];
            int y1 = xy[i + 3];
            int n = Math.max(1, Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)));
            for (int s = 0; s <= n; s++)
            {
                int x = x0 + Math.round((float) (x1 - x0) * s / n);
                int y = y0 + Math.round((float) (y1 - y0) * s / n);
                for (int j = 0; j < th; j++)
                {
                    for (int a = 0; a < th; a++) m.set(x + a, y + j, c);
                }
            }
        }
        return m;
    }

    /** A pointed spike from (cx, cy) outward: dir 0 up, 1 down, 2 left, 3 right. */
    private static void spike(Grid m, int cx, int cy, int dir, int len, int base, char c)
    {
        for (int k = 0; k < len; k++)
        {
            int w = Math.max(1, base - 2 * (k * (base / 2) / Math.max(1, len - 1)));
            if (k == len - 1) w = 1;
            for (int a = -(w / 2); a <= w / 2; a++)
            {
                switch (dir)
                {
                    case 0: m.set(cx + a, cy - k, c); break;
                    case 1: m.set(cx + a, cy + k, c); break;
                    case 2: m.set(cx - k, cy + a, c); break;
                    default: m.set(cx + k, cy + a, c); break;
                }
            }
        }
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
            case DAGANNOTH_REX: return dagannoth(f, true);
            case DAGANNOTH_SUPREME: return dagannoth(f, false);
            case DARK_CORE: return darkCore(f);
            case GENERAL_GRAARDOR: return graardor(f);
            case KRIL_TSUTSAROTH: return kril(f);
            case KRAKEN: return kraken(f);
            case KREEARRA: return kreearra(f);
            case SMOKE_DEVIL: return smokeDevil(f);
            case SNAKELING: return snakeling(f);
            case ZILYANA: return zilyana(f);
            case PHOENIX: return phoenix(f);
            case PRINCE_BLACK_DRAGON: return blackDragon(f);
            case SCORPIAS_OFFSPRING: return scorpion(f);
            default: return scurry(f);
        }
    }

    // ---------------------------------------------------------------- pets


    /** Dark core: a floating dark purple spiky orb with a glowing face and a wispy tail. */
    private static Grid darkCore(int f)
    {
        Grid g = new Grid();
        int dy = f;
        // wispy tail
        part(g, bevel(oval(23, 20 + dy, 6, 5, 'w'), (char) 0, 'z'));
        part(g, bevel(oval(26 + f, 23 + dy, 5, 4, 'w'), (char) 0, 'z'));
        part(g, bevel(oval(29 - f, 26 + dy, 4, 3, 'w'), (char) 0, 'z'));
        part(g, rect(30, 29 + dy, 2, 2, 'w'));
        // spikes and orb
        Grid orb = new Grid();
        spike(orb, 16, 7 + dy, 0, 5, 3, 'v');
        spike(orb, 23, 7 + dy, 0, 4, 3, 'v');
        spike(orb, 30, 11 + dy, 3, 5, 3, 'v');
        spike(orb, 10, 12 + dy, 2, 4, 3, 'v');
        spike(orb, 11, 19 + dy, 2, 4, 3, 'v');
        spike(orb, 18, 21 + dy, 1, 4, 3, 'v');
        spike(orb, 28, 6 + dy, 3, 4, 3, 'v');
        with(orb, bevel(oval(11, 6 + dy, 19, 17, 'v'), 'V', 'w'));
        part(g, orb);
        g.pxs('W', 15, 9 + dy, 16, 9 + dy, 14, 10 + dy, 21, 8 + dy, 22, 8 + dy);
        g.pxs('w', 26, 14 + dy, 27, 15 + dy, 25, 18 + dy, 24, 12 + dy);
        // face
        g.pxs('9', 14, 12 + dy, 15, 12 + dy, 14, 13 + dy, 15, 13 + dy, 19, 12 + dy, 20, 12 + dy, 19, 13 + dy, 20, 13 + dy);
        g.pxs('x', 14, 12 + dy, 19, 12 + dy);
        g.pxs('z', 14, 17 + dy, 15, 18 + dy, 16, 17 + dy, 17, 18 + dy, 18, 17 + dy);
        return g;
    }


    /** K'ril Tsutsaroth: a red demon with big curved horns, bat wings and hooves. */
    private static Grid kril(int f)
    {
        Grid g = new Grid();
        int dy = f;
        // wing
        Grid wing = new Grid();
        wing.stamp(24, 7 + dy,
            "........FF",
            "FF.....FFF",
            "FFF...FFFF",
            "FFFF.FFFFF",
            "FFFFFFFFFF",
            ".FFFFFFFFF",
            ".FFF.FFFF.",
            "..FF..FFF.",
            "...F...FF.",
            ".......F..");
        wing.pxs('D', 24, 8 + dy, 25, 9 + dy, 26, 10 + dy, 27, 11 + dy, 28, 12 + dy, 33, 7 + dy, 33, 8 + dy, 32, 9 + dy);
        part(g, wing);
        // tail
        part(g, bevel(chain('R', 2, 29, 27, 33, 28, 35, 25 + f), (char) 0, 'D'));
        feet(g, new int[] {14, 22}, 5, 27, 'R', 'e', 'z', f);
        g.pxs('z', 15, 32, 16, 32, 17, 32, 18, 32);
        Grid body = bevel(oval(11, 17, 18, 13, 'R'), 'e', 'D');
        body.pxs('D', 19, 23, 20, 24, 21, 25, 22, 26);
        part(g, body);
        // horns, behind the head: wide curved bone horns
        Grid horns = new Grid();
        with(horns, chain('h', 3, 12, 11, 9, 8, 7, 5));
        with(horns, chain('h', 2, 7, 5, 6, 2, 8, 0));
        with(horns, chain('h', 3, 19, 11, 21, 7, 24, 5));
        with(horns, chain('h', 2, 24, 5, 27, 4, 28, 1));
        horns.pxs('H', 12, 13, 13, 12, 20, 13, 21, 12, 9, 10, 22, 9);
        part(g, horns);
        // arm
        part(g, bevel(oval(8, 21 + f, 5, 9, 'R'), 'e', 'D'));
        part(g, bevel(oval(6, 9, 17, 12, 'R'), 'e', 'D'));
        part(g, bevel(oval(2, 13, 9, 7, 'e'), (char) 0, 'R'));
        g.pxs('D', 8, 11, 9, 11, 10, 11, 11, 11).pxs('E', 9, 12, 10, 12).pxs('z', 9, 13, 10, 13);
        g.pxs('z', 2, 15, 3, 16).pxs('x', 4, 18, 6, 18).pxs('D', 4, 19, 5, 20, 6, 20);
        return g;
    }

    /** Kraken: a yellow-green bulb-headed baby kraken with tentacles spread out, sliding along the ground. */
    private static Grid kraken(int f)
    {
        Grid g = new Grid();
        // tentacles
        char[] tc = {'y'};
        part(g, bevel(chain('y', 2, 15, 24, 10, 28, 6, 27 - f, 3, 23), 'Y', 'l'));
        part(g, bevel(chain('y', 2, 16, 26, 12, 30, 8, 31, 5, 30 - f), 'Y', 'l'));
        part(g, bevel(chain('y', 2, 20, 27, 19, 31, 15, 31, 13, 31), 'Y', 'l'));
        part(g, bevel(chain('y', 2, 24, 27, 28, 31, 32, 31, 34, 30 + f), 'Y', 'l'));
        part(g, bevel(chain('y', 2, 26, 24, 32, 26, 36, 24 + f, 38, 20), 'Y', 'l'));
        part(g, bevel(chain('y', 2, 26, 21, 31, 17, 34, 17 - f, 36, 13), 'Y', 'l'));
        // body bulb
        part(g, bevel(oval(12, 15, 17, 12, 'y'), 'Y', 'l'));
        Grid head = bevel(oval(13, 2, 15, 18, 'y'), 'Y', 'l');
        head.pxs('l', 22, 5, 23, 6, 24, 10, 25, 11, 22, 14, 24, 15, 20, 3);
        part(g, head);
        // big eyes
        part(g, oval(13, 8, 6, 6, 'x'));
        part(g, oval(20, 7, 6, 6, 'x'));
        g.pxs('z', 13, 10, 13, 11, 14, 10, 14, 11, 20, 9, 20, 10, 21, 9, 21, 10);
        g.pxs('l', 16, 16, 17, 17, 18, 16, 22, 15, 23, 16);
        g.pxs('Y', 15, 4, 16, 4, 15, 5, 17, 4);
        return g;
    }


    /** Smoke devil: a floating grey-green tentacled devil with yellow glowing vents and wisps of smoke. */
    private static Grid smokeDevil(int f)
    {
        Grid g = new Grid();
        int dy = f;
        // smoke wisps
        part(g, bevel(oval(29, 9 + dy, 6, 4, 'p'), (char) 0, 'd'));
        part(g, bevel(oval(32, 14 - dy + 1, 5, 4, 'p'), (char) 0, 'd'));
        part(g, bevel(oval(6, 5 - dy + 1, 5, 4, 'p'), (char) 0, 'd'));
        part(g, bevel(oval(26 + f, 3, 4, 3, 'p'), (char) 0, 'd'));
        // hanging tentacles
        part(g, bevel(chain('g', 2, 14, 22 + dy, 12, 26, 11 - f, 29), 'G', 'j'));
        part(g, bevel(chain('g', 2, 18, 24 + dy, 17, 28, 16 + f, 31), 'G', 'j'));
        part(g, bevel(chain('g', 2, 24, 24 + dy, 25, 28, 26 - f, 31), 'G', 'j'));
        part(g, bevel(chain('g', 2, 28, 22 + dy, 30, 26, 31 + f, 29), 'G', 'j'));
        // body
        Grid body = bevel(trap(21, 12 + dy, 25 + dy, 6, 10, 'g'), 'G', 'j');
        part(g, body);
        g.pxs('j', 14, 15 + dy, 14, 16 + dy, 15, 17 + dy, 28, 16 + dy, 27, 17 + dy);
        // belly vents
        Grid vent = rect(17, 18 + dy, 9, 4, 'Z');
        vent.pxs('X', 18, 19 + dy, 19, 19 + dy, 20, 19 + dy, 22, 20 + dy, 23, 20 + dy, 24, 20 + dy);
        part(g, vent);
        // head
        part(g, bevel(oval(12, 2 + dy, 17, 13, 'g'), 'G', 'j'));
        part(g, bevel(chain('j', 2, 14, 10 + dy, 11, 13 + dy, 10, 17 + dy), (char) 0, 'z'));
        g.pxs('Z', 15, 6 + dy, 16, 6 + dy, 17, 6 + dy, 21, 6 + dy, 22, 6 + dy, 23, 6 + dy);
        g.pxs('X', 16, 7 + dy, 17, 7 + dy, 22, 7 + dy, 23, 7 + dy).pxs('z', 15, 7 + dy, 21, 7 + dy);
        g.pxs('j', 14, 5 + dy, 15, 5 + dy, 16, 5 + dy, 17, 5 + dy, 21, 4 + dy, 22, 4 + dy, 23, 4 + dy);
        return g;
    }

    /** Snakeling: Zulrah's baby, a green snake with a hooded cobra-like head, upright on its coil. */
    private static Grid snakeling(int f)
    {
        Grid g = new Grid();
        int sway = f == 0 ? 0 : -1;
        // tail tip out of the coil
        part(g, bevel(chain('1', 2, 31, 29, 35, 28, 37, 25 - f), (char) 0, '3'));
        // coils, the lower in front
        part(g, bevel(oval(14, 19, 14, 6, '1'), '2', '3'));
        part(g, bevel(oval(11, 23, 20, 6, '1'), '2', '3'));
        Grid low = bevel(oval(9, 27, 24, 6, '1'), '2', '3');
        low.pxs('4', 14, 31, 15, 31, 16, 31, 17, 31, 22, 31, 23, 31, 24, 31, 25, 31);
        part(g, low);
        g.pxs('3', 15, 25, 16, 26, 22, 25, 23, 26, 17, 29, 18, 29, 27, 29, 28, 30);
        // neck and hood
        part(g, bevel(chain('1', 4, 19, 21, 19 + sway, 14), '2', '3'));
        Grid hood = bevel(oval(10 + sway, 7, 14, 13, '1'), '2', '3');
        hood.pxs('5', 16 + sway, 10, 17 + sway, 10, 16 + sway, 12, 17 + sway, 12, 16 + sway, 14, 17 + sway, 14, 14 + sway, 16, 15 + sway, 16);
        hood.pxs('3', 12 + sway, 9, 12 + sway, 10, 21 + sway, 10, 21 + sway, 12);
        part(g, hood);
        part(g, bevel(oval(5 + sway, 3, 11, 8, '1'), '2', '3'));
        g.pxs('Z', 8 + sway, 5, 9 + sway, 5).pxs('z', 8 + sway, 6, 9 + sway, 6);
        g.pxs('3', 5 + sway, 8, 6 + sway, 8);
        g.pxs('R', 3 + sway, 7, 2 + sway, 6, 2 + sway, 8, 4 + sway, 7);
        return g;
    }

    /** Commander Zilyana: a tiny icyene lady with red-brown hair, white wings, pale armour and a sword. */
    private static Grid zilyana(int f)
    {
        Grid g = new Grid();
        int dy = f;
        // wings
        Grid wing = new Grid();
        wing.stamp(23, 2 - dy + 1,
            ".........IIII",
            "........IIIII",
            ".......IIIIII",
            "......IIIIIII",
            ".....IIIIIIII",
            "....IIIIIIIII",
            "...IIIIIIIII.",
            "...IIIIIIII..",
            "....IIIIIII..",
            ".....IIIII...",
            "......III....");
        wing.pxs('L', 26, 8 - dy + 1, 28, 9 - dy + 1, 30, 10 - dy + 1, 31, 8 - dy + 1, 30, 6 - dy + 1, 33, 5 - dy + 1, 28, 12 - dy + 1);
        part(g, wing);
        // long hair behind
        part(g, bevel(oval(21, 8, 8, 15, '6'), '7', 'z'));
        feet(g, new int[] {17, 22}, 3, 28, 'L', 'i', 'z', f);
        // armour body and skirt
        Grid body = bevel(trap(21, 15, 22, 5, 7, 'i'), 'I', 'L');
        body.pxs('L', 20, 17, 21, 18, 20, 19);
        part(g, body);
        part(g, bevel(trap(21, 23, 28, 7, 8, 'I'), 'I', 'L'));
        // sword arm
        part(g, bevel(chain('8', 2, 15, 18, 12, 22), '8', '0'));
        Grid sword = new Grid();
        sword.stamp(8, 7, "K", "K", "K", "K", "K", "K", "K", "K", "K", "K", "K", "K", "K");
        part(g, sword);
        g.pxs('A', 6, 20, 7, 20, 8, 20, 9, 20, 10, 20).pxs('b', 8, 21, 8, 22);
        g.pxs('8', 11, 21, 11, 22, 10, 21);
        // head with hair
        part(g, bevel(oval(11, 3, 14, 12, '8'), (char) 0, '0'));
        part(g, bevel(oval(10, 1, 15, 7, '7'), '7', '6'));
        g.pxs('7', 10, 7, 10, 8, 11, 9);
        g.pxs('z', 13, 9, 13, 10, 17, 9, 17, 10);
        g.pxs('0', 12, 12, 13, 12).pxs('R', 15, 12, 16, 12);
        return g;
    }


    /** Scorpia's offspring: a dark grey scorpion with big pincers and a curled stinger tail. */
    private static Grid scorpion(int f)
    {
        Grid g = new Grid();
        // legs
        int s = f == 0 ? 0 : 1;
        part(g, chain('<', 2, 14, 26, 11 - s, 29, 10 - s, 32));
        part(g, chain('<', 2, 18, 27, 16 + s, 30, 16 + s, 32));
        part(g, chain('<', 2, 24, 27, 26 - s, 30, 26 - s, 32));
        part(g, chain('<', 2, 28, 26, 31 + s, 29, 32 + s, 32));
        // curled tail
        Grid tail = new Grid();
        with(tail, chain('[', 3, 28, 23, 33, 20, 34, 14, 31, 9, 26, 7));
        part(g, bevel(tail, ']', '<'));
        part(g, bevel(chain('[', 3, 24, 7 + f, 21, 9 + f), ']', '<'));
        g.pxs('>', 20, 11 + f, 20, 12 + f, 19, 12 + f);
        // body
        Grid body = bevel(oval(10, 20, 20, 9, '['), ']', '<');
        body.pxs('<', 19, 21, 19, 22, 24, 21, 24, 22, 24, 23, 28, 22, 28, 23);
        body.pxs(']', 14, 22, 15, 22, 21, 22, 22, 22, 26, 22);
        part(g, body);
        // arms and pincers
        part(g, bevel(chain('[', 3, 13, 21, 9, 18, 7, 15), ']', '<'));
        Grid claw = bevel(oval(1, 10 + f, 11, 8, '['), ']', '<');
        claw.pxs('z', 2, 14 + f, 3, 14 + f, 4, 14 + f, 5, 14 + f, 6, 13 + f);
        part(g, claw);
        part(g, bevel(chain('[', 2, 14, 24, 11, 25, 8, 24), ']', '<'));
        part(g, bevel(oval(4, 21, 7, 6, '['), ']', '<'));
        g.pxs('z', 5, 24, 6, 24, 7, 24);
        // eyes
        g.pxs('R', 14, 21, 15, 21, 17, 20, 18, 20);
        g.pxs('z', 14, 22, 17, 21);
        return g;
    }

    /** Scurry: a big brown rat with a pale-blue crystal chunk on its back, red eyes and bared teeth. */
    private static Grid scurry(int f)
    {
        Grid g = new Grid();
        // tail
        part(g, chain('*', 2, 31, 26, 35, 25, 38, 21 + f, 37, 17));
        feet(g, new int[] {13, 25}, 4, 28, '/', (char) 0, 'z', f);
        part(g, bevel(rect(18 - f, 28, 3, 5 - f, '{'), (char) 0, '/'));
        part(g, bevel(rect(30 - f, 28, 3, 5 - f, '{'), (char) 0, '/'));
        // body
        Grid body = bevel(oval(10, 15, 22, 15, '{'), '}', '/');
        with(body, oval(12, 22, 12, 7, '}'));
        part(g, body);
        // crystal chunk on the back
        Grid cry = new Grid();
        Grid s1 = new Grid();
        spike(s1, 25, 17, 0, 11, 7, '+');
        Grid s2 = new Grid();
        spike(s2, 20, 17, 0, 8, 7, '+');
        Grid s3 = new Grid();
        spike(s3, 29, 18, 0, 7, 5, '+');
        for (Grid s : new Grid[] {s3, s2, s1})
        {
            for (int j = 0; j < s.h; j++)
            {
                for (int i = 0; i < s.w; i++)
                {
                    if (s.px[j][i] == 0) continue;
                    // left face light, right face shadow, a bright ridge between
                    int cx = s == s1 ? 25 : s == s2 ? 20 : 29;
                    s.px[j][i] = i < cx ? '=' : i == cx ? '+' : '-';
                }
            }
            part(g, s);
        }
        g.pxs('x', 24, 8, 24, 9, 19, 12, 28, 14);
        // front paw
        part(g, bevel(oval(7, 23, 5, 8, '{'), '}', '/'));
        g.pxs('*', 7, 30, 8, 31, 9, 30);
        // head with pointed snout and a round ear
        part(g, bevel(oval(13, 8, 7, 8, '{'), '}', '/'));
        g.pxs('*', 15, 10, 16, 10, 15, 11, 16, 11);
        part(g, bevel(oval(4, 12, 16, 13, '{'), '}', '/'));
        part(g, bevel(oval(1, 17, 9, 7, '}'), (char) 0, '{'));
        g.pxs('z', 1, 19, 1, 20, 2, 19).pxs('*', 2, 18);
        g.pxs('R', 9, 15, 10, 15, 9, 16, 10, 16).pxs('z', 8, 14, 9, 14, 10, 14, 11, 14).pxs('x', 9, 15);
        g.pxs('z', 3, 22, 4, 22, 5, 22, 6, 22, 7, 22, 8, 22);
        g.pxs('x', 3, 23, 4, 23, 6, 23, 7, 23);
        g.pxs('*', 5, 23);
        return g;
    }

    /** Dagannoth Rex (tan, stocky) and Supreme (green, spiky): a hunched upright sea-monster with long clawed arms. */
    private static Grid dagannoth(int f, boolean rex)
    {
        char b = rex ? 't' : 's';
        char l = rex ? 'T' : 'S';
        char d = rex ? 'u' : 'r';
        Grid g = new Grid();
        int sw = f;
        // tail
        part(g, bevel(chain(b, 3, 24, 25, 27, 27, 28, 25 + f), l, d));
        // spiky fin down the back
        Grid fin = new Grid();
        int sl = rex ? 4 : 6;
        spike(fin, 22, 12, 0, sl, 3, l);
        spike(fin, 25, 15, 3, sl, 3, l);
        spike(fin, 26, 20, 3, sl - 1, 3, l);
        part(g, bevel(fin, l, d));
        // back arm, long and clawed
        part(g, bevel(chain(b, 3, 23, 16, 26, 22, 26 - f, 27), l, d));
        g.pxs('h', 25 - f, 30, 27 - f, 30, 26 - f, 31);
        // legs with toe claws
        feet(g, new int[] {rex ? 12 : 13, rex ? 20 : 20}, rex ? 6 : 5, 27, d, (char) 0, 'z', f);
        g.pxs('h', rex ? 11 : 12, 32, rex ? 19 : 19, 32);
        // hunched torso with a pale belly
        Grid body = bevel(oval(rex ? 9 : 11, 11, rex ? 18 : 15, 17, b), l, d);
        with(body, oval(rex ? 10 : 12, 17, 7, 9, 'q'));
        body.pxs(d, 20, 16, 21, 19, 22, 22);
        part(g, body);
        // front arm, long, dragging claws
        part(g, bevel(chain(b, 3, 12, 15, 9 + sw, 21, 8 + sw, 27), l, d));
        g.pxs('h', 6 + sw, 30, 8 + sw, 30, 7 + sw, 31);
        g.pxs(b, 7 + sw, 28, 8 + sw, 29);
        // finned crest on the head
        Grid cr = new Grid();
        spike(cr, 9, 8, 0, rex ? 4 : 6, 3, l);
        spike(cr, 13, 7, 0, rex ? 6 : 8, 3, l);
        spike(cr, 17, 8, 0, rex ? 4 : 6, 3, l);
        part(g, bevel(cr, l, d));
        // head and toothy snout
        part(g, bevel(oval(7, 6, rex ? 15 : 14, 11, b), l, d));
        part(g, bevel(oval(3, 10, 9, 6, l), (char) 0, b));
        g.pxs('d', 9, 8, 10, 8, 11, 8, 12, 8);
        g.pxs('E', 10, 9, 11, 9, 12, 9).pxs('z', 10, 10, 11, 10, 12, 10).pxs('x', 10, 9);
        g.pxs('z', 4, 12, 5, 12);
        g.pxs('z', 4, 15, 5, 15, 6, 15, 7, 15, 8, 15, 9, 15, 10, 15);
        g.pxs('x', 4, 14, 6, 14, 8, 14);
        g.pxs(d, 5, 17 + f, 8, 17 + f, 10, 18);
        return g;
    }

    /** General Graardor: an ogre with huge shoulders and fists, small legs, a heavy tusked jaw and armour straps. */
    private static Grid graardor(int f)
    {
        Grid g = new Grid();
        int dy = f;
        // back arm
        part(g, bevel(oval(27, 14, 8, 14, 'o'), 'O', 'n'));
        part(g, bevel(oval(27, 25 + dy, 8, 7, 'O'), 'O', 'n'));
        // small legs
        feet(g, new int[] {15, 22}, 5, 28, 'N', 'M', 'z', f);
        // torso
        Grid body = bevel(trap(21, 13, 29, 8, 8, 'o'), 'O', 'n');
        part(g, body);
        part(g, bevel(rect(16, 19, 10, 5, 'm'), 'M', 'N'));
        g.pxs('N', 20, 20, 21, 21);
        g.pxs('b', 15, 14, 16, 15, 17, 16, 18, 17, 19, 18, 22, 24, 23, 25, 24, 26);
        part(g, bevel(rect(14, 25, 14, 3, 'b'), 'N', 'z'));
        g.pxs('A', 20, 26, 21, 26);
        // back pauldron
        Grid pb = bevel(oval(24, 9, 10, 7, 'm'), 'M', 'N');
        part(g, pb);
        g.pxs('M', 28, 8, 31, 9);
        // front arm and fist
        part(g, bevel(oval(7, 14, 9, 14, 'o'), 'O', 'n'));
        part(g, bevel(oval(6, 25 - dy + 1, 9, 7, 'O'), 'O', 'n'));
        g.pxs('n', 8, 29 - dy + 1, 10, 29 - dy + 1, 12, 29 - dy + 1);
        Grid pf = bevel(oval(7, 9, 11, 7, 'm'), 'M', 'N');
        part(g, pf);
        g.pxs('M', 9, 8, 12, 8, 15, 9);
        // head: heavy jaw, tusks, studded cap
        part(g, bevel(oval(11, 2, 13, 11, 'o'), 'O', 'n'));
        part(g, bevel(oval(8, 7, 10, 8, 'O'), 'O', 'o'));
        part(g, bevel(rect(13, 1, 9, 3, 'm'), 'M', 'N'));
        g.pxs('M', 14, 0, 17, 0, 20, 0);
        g.pxs('z', 11, 6, 12, 6, 13, 6, 14, 6).pxs('E', 12, 7, 13, 7).pxs('z', 12, 8, 13, 8);
        g.pxs('z', 9, 12, 10, 12, 11, 12, 12, 12, 13, 12, 14, 12);
        g.pxs('x', 9, 10, 9, 11, 12, 11, 14, 11);
        return g;
    }

    /** Kree'arra: a grey aviansie bird-man, upright with wing-arms at its sides, taloned legs, hooked beak and red crest. */
    private static Grid kreearra(int f)
    {
        Grid g = new Grid();
        int dy = f;
        // tail feathers
        part(g, bevel(chain('c', 2, 26, 26, 29, 29, 31, 29 + f), (char) 0, 'd'));
        // back wing-arm
        Grid bw = bevel(oval(25, 13 + dy, 7, 15, 'd'), 'c', 'z');
        bw.pxs('c', 28, 26 + dy, 29, 24 + dy, 30, 22 + dy);
        part(g, bw);
        // taloned legs
        int lx = 16 + f;
        int rx = 22 - f;
        part(g, rect(lx, 27, 2, 5, 'B'));
        part(g, rect(rx, 27, 2, 5, 'B'));
        g.pxs('B', lx - 3, 32, lx - 2, 32, lx - 1, 32, lx, 32, rx - 3, 32, rx - 2, 32, rx - 1, 32, rx, 32);
        g.pxs('a', lx - 3, 32, rx - 3, 32);
        // upright body with a pale chest
        Grid body = bevel(oval(13, 12 + dy, 15, 17, 'c'), 'C', 'd');
        with(body, oval(14, 16 + dy, 8, 11, 'C'));
        body.pxs('c', 16, 19 + dy, 18, 21 + dy, 17, 24 + dy);
        part(g, body);
        // front wing-arm with feather fingers
        Grid fw = bevel(oval(9, 15 + dy, 7, 14, 'c'), 'C', 'd');
        fw.pxs('d', 10, 28 + dy, 12, 28 + dy, 14, 28 + dy);
        fw.pxs('C', 11, 18 + dy, 11, 19 + dy);
        part(g, fw);
        // crest
        Grid crest = new Grid();
        spike(crest, 12, 6 + dy, 0, 4, 3, 'R');
        spike(crest, 16, 5 + dy, 0, 6, 3, 'R');
        spike(crest, 20, 6 + dy, 0, 4, 3, 'R');
        part(g, bevel(crest, 'e', 'D'));
        // head and hooked beak
        part(g, bevel(oval(10, 5 + dy, 13, 11, 'c'), 'C', 'd'));
        Grid beak = new Grid();
        beak.stamp(4, 8 + dy, "aaaaaa", "AAaaaaa", ".aaaaaa", "..aaaa", "..aaa", "..aa", "..a");
        part(g, beak);
        g.pxs('A', 11, 8 + dy, 12, 8 + dy);
        g.pxs('z', 13, 8 + dy, 14, 8 + dy, 13, 9 + dy, 14, 9 + dy).pxs('x', 13, 8 + dy);
        g.pxs('R', 15, 11 + dy, 16, 11 + dy);
        return g;
    }

    /** Phoenix: an orange-red fire bird with a flaming tail; it hops with its wings up, then down. */
    private static Grid phoenix(int f)
    {
        Grid g = new Grid();
        int dy = f == 0 ? 0 : -2;
        // flaming tail
        part(g, bevel(chain('P', 3, 24, 22 + dy, 28, 24 + dy, 31, 22 + dy + f), 'Q', 'f'));
        part(g, bevel(chain('f', 2, 24, 19 + dy, 28, 18 + dy, 31, 14 + dy - f), 'Q', 'f'));
        g.pxs('X', 31, 22 + dy + f, 31, 14 + dy - f);
        // legs
        int ll = f == 0 ? 6 : 4;
        int a = 16;
        int c = 21;
        part(g, rect(a, 26 + dy, 2, ll, 'a'));
        part(g, rect(c, 26 + dy, 2, ll, 'a'));
        int fy = 26 + dy + ll - 1;
        g.pxs('a', a - 2, fy, a - 1, fy, c - 2, fy, c - 1, fy);
        Grid wing = new Grid();
        if (f == 0)
        {
            wing.stamp(19, 3,
                ".....QPP",
                "....QPPf",
                "...QPPPf",
                "..QPPPf.",
                "..PPPPf.",
                ".QPPPf..",
                ".PPPPf..",
                "PPPPf...",
                "PPPf....",
                "PPf.....",
                "Pf......");
            part(g, wing);
        }
        Grid body = bevel(oval(10, 15 + dy, 16, 12, 'P'), 'Q', 'f');
        with(body, oval(11, 20 + dy, 9, 6, 'Q'));
        part(g, body);
        if (f == 1)
        {
            wing.stamp(18, 17 + dy,
                "PQ......",
                "PPQ.....",
                "PPPQ....",
                ".PPPQ...",
                ".PPPPf..",
                "..PPPPf.",
                "..PPPPf.",
                "...PPPf.",
                "....PPf.",
                ".....Pf.");
            part(g, wing);
        }
        // flaming crest
        Grid crest = new Grid();
        spike(crest, 9, 9 + dy, 0, 4, 3, 'Q');
        spike(crest, 13, 8 + dy, 0, 6, 3, 'P');
        spike(crest, 17, 9 + dy, 0, 4, 3, 'f');
        part(g, crest);
        part(g, bevel(oval(6, 8 + dy, 13, 11, 'P'), 'Q', 'f'));
        Grid beak = new Grid();
        beak.stamp(3, 12 + dy, "aaaa", ".aaa", "..aa");
        part(g, beak);
        g.pxs('z', 9, 12 + dy, 10, 12 + dy, 9, 13 + dy, 10, 13 + dy).pxs('x', 9, 12 + dy);
        return g;
    }

    /** One of the dragon's heads: a skull with a snout, a horn, a red eye and little teeth, snout pointing left. */
    private static void dragonHead(Grid g, int hx, int hy)
    {
        Grid skull = bevel(oval(hx + 4, hy, 8, 7, 'U'), '@', '$');
        with(skull, bevel(oval(hx, hy + 2, 7, 5, 'U'), '@', '$'));
        part(g, skull);
        g.pxs('h', hx + 8, hy - 1, hx + 9, hy - 2, hx + 6, hy - 1);
        g.pxs('R', hx + 6, hy + 2, hx + 7, hy + 2).pxs('z', hx + 7, hy + 3);
        g.pxs('z', hx, hy + 3);
        g.pxs('x', hx + 1, hy + 6, hx + 3, hy + 6);
    }

    /** Prince Black Dragon: a small black dragon with three distinct heads on necks and a wing. */
    private static Grid blackDragon(int f)
    {
        Grid g = new Grid();
        int dy = f;
        // wing
        Grid wing = new Grid();
        wing.stamp(22, 4 + dy,
            "U.......",
            "&U......",
            "&&U...&&",
            "&&&U.&&&",
            "&&&&U&&&",
            "&&&&&&&&",
            ".&&&&&&.",
            ".&&.&&&.",
            "..&..&&.");
        part(g, wing);
        part(g, bevel(chain('U', 2, 27, 26, 30, 28, 31, 26 + f), '@', '$'));
        feet(g, new int[] {15, 23}, 5, 27, 'U', '@', 'z', f);
        Grid body = bevel(oval(13, 16, 16, 13, 'U'), '@', '$');
        with(body, oval(14, 22, 8, 6, '@'));
        part(g, body);
        // three necks
        part(g, bevel(chain('U', 3, 20, 19, 20, 13, 18, 7), '@', '$'));
        part(g, bevel(chain('U', 3, 18, 20, 15, 16, 13, 13), '@', '$'));
        part(g, bevel(chain('U', 3, 16, 22, 12, 21, 10, 20), '@', '$'));
        dragonHead(g, 11, 1 + dy);
        dragonHead(g, 6, 9 - dy + 1);
        dragonHead(g, 4, 17 + dy);
        return g;
    }
}
