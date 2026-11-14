package com.forgebingo.core;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The website's pixel-art imp (apps/web/src/features/fun/ImpArt.tsx), ported pixel for pixel so the side panel's
 * imps are the same ones that wander over the site. Our own original art: red skin, little horns, bat wings.
 * Drawn on a 42 x 34 grid, facing LEFT, feet on the bottom edge. Two walk frames; optional hat and held item.
 */
public final class ImpSprite
{
    public static final int WIDTH = 42;
    public static final int HEIGHT = 34;
    /** the grid's origin sits at (-6, -2), like the website's viewBox */
    private static final int OX = 6;
    private static final int OY = 2;

    public enum Hat { NONE, CHEF, WIZARD, SANTA, BUNNY }

    public enum Item { NONE, FIRE_STAFF, AYAK, SOULREAPER }

    private static final Map<String, BufferedImage> CACHE = new ConcurrentHashMap<>();

    private ImpSprite()
    {
    }

    /** One frame (0 or 1) as a transparent image; cached. */
    public static BufferedImage image(Hat hat, Item item, int frame)
    {
        Hat h = hat == null ? Hat.NONE : hat;
        Item it = item == null ? Item.NONE : item;
        int f = frame == 0 ? 0 : 1;
        return CACHE.computeIfAbsent(h + "|" + it + "|" + f, k -> render(build(h, it, f)));
    }

    private static int colour(char c)
    {
        switch (c)
        {
            case 'k': return 0x2a0b12; // outline / pupils
            case 'r': return 0xe0402e; // skin
            case 'h': return 0xff7a5c; // highlight
            case 'd': return 0xa82222; // shade
            case 'y': return 0xffe94d;
            case 'w': return 0xffffff;
            case 'b': return 0xefe2b0; // horn
            case 'B': return 0xb79a58;
            case 'v': return 0x8a1c3e; // wing
            case 'u': return 0x5a1030;
            case 'W': return 0xf6f6f2;
            case 'G': return 0xc4cad4;
            case 'P': return 0xf7a8c4;
            case 'N': return 0x2f55d8; // wizard
            case 'n': return 0x6a93ff;
            case 'M': return 0x1d338f;
            case 'Y': return 0xf4c430;
            case 'S': return 0xd9262b; // santa
            case 's': return 0xff6a60;
            case 'Z': return 0x8e1118;
            case 'K': return 0x9a6531; // wood
            case 'J': return 0x5c3a18;
            case 'R': return 0xe02a1a; // fire
            case 'F': return 0xff9a1f;
            case 'E': return 0xffe066;
            case 'C': return 0x2d2840; // ayak
            case 'c': return 0x554c75;
            case 'T': return 0x35e0d0;
            case 't': return 0x1b8f8a;
            case 'Q': return 0xa455ff;
            case 'L': return 0xdbe6f5; // blade
            case 'l': return 0x8fa8cf;
            case 'm': return 0x5c7399;
            default: return 0xff00ff;
        }
    }

    /** A sparse pixel grid; anything outside the sprite's box is dropped. */
    private static final class Grid
    {
        final char[][] px = new char[HEIGHT][WIDTH];

        void set(int x, int y, char c)
        {
            int i = x + OX;
            int j = y + OY;
            if (i >= 0 && i < WIDTH && j >= 0 && j < HEIGHT) px[j][i] = c;
        }

        boolean has(int x, int y)
        {
            int i = x + OX;
            int j = y + OY;
            return i >= 0 && i < WIDTH && j >= 0 && j < HEIGHT && px[j][i] != 0;
        }

        void stamp(int x0, int y0, String... rows)
        {
            for (int j = 0; j < rows.length; j++)
            {
                String row = rows[j];
                for (int i = 0; i < row.length(); i++)
                {
                    char c = row.charAt(i);
                    if (c != '.' && c != ' ') set(x0 + i, y0 + j, c);
                }
            }
        }

        void pxs(char c, int dy, int... xy)
        {
            for (int i = 0; i + 1 < xy.length; i += 2) set(xy[i], xy[i + 1] + dy, c);
        }
    }

    private static Grid shape(int x, int y, String... rows)
    {
        Grid m = new Grid();
        m.stamp(x, y, rows);
        return m;
    }

    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** Paint a part onto the sprite with an automatic 1px dark outline around it. */
    private static void part(Grid g, Grid fill)
    {
        for (int j = 0; j < HEIGHT; j++)
        {
            for (int i = 0; i < WIDTH; i++)
            {
                if (fill.px[j][i] == 0) continue;
                for (int[] s : SIDES)
                {
                    int x = i - OX + s[0];
                    int y = j - OY + s[1];
                    if (!fill.has(x, y)) g.set(x, y, 'k');
                }
            }
        }
        for (int j = 0; j < HEIGHT; j++)
        {
            for (int i = 0; i < WIDTH; i++)
            {
                if (fill.px[j][i] != 0) g.px[j][i] = fill.px[j][i];
            }
        }
    }

    // ---------------------------------------------------------------- weapons

    /** The staff leans outward at the top, so it is gripped at x=7 on y=21. */
    private static int shaftX(int y)
    {
        return 7 - (int) Math.floor((21 - y) / 7.0 + 0.5);
    }

    private static Grid shaft(int top, char a, char b, int dy)
    {
        Grid m = new Grid();
        for (int y = top; y <= 31; y++) m.set(shaftX(y), y + dy, y % 4 == 0 ? b : a);
        return m;
    }

    private static void drawItem(Grid g, Item item, int dy)
    {
        if (item == Item.FIRE_STAFF)
        {
            part(g, shaft(8, 'K', 'J', dy));
            int cx = shaftX(8);
            Grid orb = shape(cx - 2, 3 + dy, ".RRR.", "RFFFR", "RFEFR", "RFFFR", ".RRR.");
            orb.pxs('F', dy, cx - 1, 2, cx + 1, 2);
            orb.pxs('E', dy, cx, 1);
            orb.pxs('R', dy, cx - 1, 1, cx + 2, 2);
            part(g, orb);
        }
        else if (item == Item.AYAK)
        {
            part(g, shaft(8, 'C', 'c', dy));
            int cx = shaftX(8);
            part(g, shape(cx - 3, 2 + dy, "C.....C", "C.....C", "cC...Cc"));
            part(g, shape(cx - 2, 3 + dy, "CTTTC", "TWWWT", "WQkQW", "TWWWT", "CTTTC"));
            g.pxs('T', dy, cx - 4, 6, cx + 4, 6, cx, 1);
        }
        else
        {
            part(g, shaft(3, 'C', 'c', dy));
            Grid blade = shape(-5, 1 + dy, "....LLLLL..", "..LLLLLLLll", ".LLLLllllll", "LLll......m", "Ll.........", "L..........");
            blade.pxs('m', dy, -1, 3);
            part(g, blade);
            g.pxs('l', dy, -4, 3, -3, 3);
        }
    }

    // ---------------------------------------------------------------- hats (fitted over the head)

    private static void drawHat(Grid g, Hat hat)
    {
        if (hat == Hat.CHEF)
        {
            part(g, shape(8, 3,
                "..WWW.WWW.WWW.",
                ".WWWWWWWWWWWW.",
                "WWWWWWWWWGWWWW",
                "WWWGWWWWWWWWWW",
                "WWWWWWWWWWWWGW",
                ".WWWWWWWWWWWW.",
                "..WWWWWWWWWW..",
                ".WWWWWWWWWWWW.",
                ".GGGGGGGGGGGG."));
            g.pxs('G', 0, 13, 6, 17, 7); // pleats
        }
        else if (hat == Hat.WIZARD)
        {
            part(g, shape(6, 1,
                "...........NMN....",
                "..........NNM.....",
                ".........nNNM.....",
                "........nNNM......",
                ".......nNNM.......",
                "......nNNNM.......",
                ".....nNNNNNM......",
                "....nnNNNNNMM.....",
                "....YYYYYYYYYY....",
                "..NNNNNNNNNNNNNN..",
                ".MMMMMMMMMMMMMMMM."));
            g.pxs('Y', 0, 17, 5); // little star on the cone
        }
        else if (hat == Hat.SANTA)
        {
            part(g, shape(8, 3,
                ".....SSSSSS.....",
                "....sSSSSSSSZ...",
                "...sSSSSSSSSSZZ.",
                "..sSSSSSSSSSSZZZ",
                "..SSSSSSSSSSSZZ.",
                "..SSSSSSSSSSZZ..",
                ".WWWWWWWWWWWWW..",
                "WWGWWWWWGWWWWW.."));
            part(g, shape(23, 7, "WW", "WW")); // bobble at the dangling tip
            g.pxs('W', 0, 23, 7, 24, 7, 23, 8, 24, 8);
            g.pxs('G', 0, 24, 8);
        }
        else if (hat == Hat.BUNNY)
        {
            part(g, shape(9, 1, "WPW", "WPW", "WPW", ".WPW", ".WPW", ".WPW", ".WPW", ".WPW", ".WPW", ".WPW"));
            part(g, shape(18, 1, "WPW", "WPW", "WPW", "WPW.", "WPW.", "WPW.", "WPW.", "WPW.", "WPW.", "WPW."));
            g.pxs('W', 0, 11, 11, 18, 11);
        }
    }

    // ---------------------------------------------------------------- the imp

    private static Grid leg(int x, char c, int lift)
    {
        Grid m = new Grid();
        char inner = c == 'd' ? 'd' : 'r';
        for (int y = 25; y <= 29 - lift; y++)
        {
            m.set(x, y, c);
            m.set(x + 1, y, inner);
        }
        int fy = 30 - lift;
        m.set(x - 1, fy, c);
        m.set(x, fy, c);
        m.set(x + 1, fy, inner);
        return m;
    }

    private static Grid build(Hat hat, Item item, int f)
    {
        Grid g = new Grid();

        // bat wings (behind everything), flap a little
        int wy = f == 1 ? -1 : 0;
        part(g, shape(17, 12 + wy,
            ".....v.....",
            "....vv...v.",
            "....vvv.vv.",
            "...vvvvvvv.",
            "...vvuvvuv.",
            "..vvvvvvvv.",
            "..vvvuvv.v.",
            "..vvv..v...",
            "..vv......."));
        // tail with a little spade tip
        Grid tail = new Grid();
        tail.pxs('r', 0, 18, 24, 19, 25, 20, 25, 21, 24, 22, 23, 22, 22);
        tail.pxs('d', 0, 21, 21, 22, 21, 23, 21, 22, 20, 22, 22);
        part(g, tail);

        // legs: back leg first (shaded), then front leg
        part(g, leg(f == 1 ? 16 : 17, 'd', 0));
        part(g, leg(f == 1 ? 13 : 11, 'r', f == 1 ? 1 : 0));

        // torso
        part(g, shape(12, 20, "rrrrrd", "rhrrrd", "rrrrrd", "rrrrdd", "drrrdd"));

        // the tail's root: the leg and torso outlines above cut it off, which left the tail floating beside the
        // feet like stray marks. Painted again here so the tail always joins the hip.
        g.pxs('r', 0, 18, 23, 18, 24, 19, 24, 19, 25);
        g.pxs('d', 0, 20, 24);

        // ears and horns go behind the head so its outline overlaps their roots
        part(g, shape(6, 11, "r...", "rr..", "rdr.", ".rrr"));
        part(g, shape(20, 11, "...r", "..rr", ".rdr", "rrr."));
        Grid hornL = new Grid();
        hornL.pxs('b', 0, 12, 10, 11, 9, 11, 10, 10, 8);
        hornL.pxs('B', 0, 12, 10);
        Grid hornR = new Grid();
        hornR.pxs('b', 0, 17, 10, 18, 9, 18, 10, 19, 8);
        hornR.pxs('B', 0, 17, 10);
        part(g, hornL);
        part(g, hornR);

        // head: big, cheeky, yellow eyes looking left
        part(g, shape(10, 12,
            ".rhhrrrrr.",
            "rhrrrrrrrd",
            "rrrrrrrrrd",
            "ryyrryyrrd",
            "rkyrrkyrrd",
            "rrrrrrrrdd",
            ".kwkwkwkd."));
        g.pxs('h', 0, 10, 16); // snout highlight

        // front arm: holds the weapon when there is one, otherwise swings
        if (item != Item.NONE)
        {
            int dy = f == 1 ? -1 : 0;
            drawItem(g, item, dy);
            part(g, shape(9, 21 + dy, "rrr"));
            part(g, shape(6, 21 + dy, "rrd", "rdd"));
        }
        else
        {
            Grid a = new Grid();
            if (f == 1) a.pxs('r', 0, 11, 21, 10, 22, 9, 23, 9, 24);
            else a.pxs('r', 0, 11, 21, 11, 22, 10, 23, 10, 24);
            part(g, a);
        }

        if (hat != Hat.NONE) drawHat(g, hat);
        return g;
    }

    private static BufferedImage render(Grid g)
    {
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        for (int j = 0; j < HEIGHT; j++)
        {
            for (int i = 0; i < WIDTH; i++)
            {
                char c = g.px[j][i];
                if (c != 0) img.setRGB(i, j, 0xff000000 | colour(c));
            }
        }
        return img;
    }
}
