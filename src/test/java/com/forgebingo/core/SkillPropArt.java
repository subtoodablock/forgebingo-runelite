package com.forgebingo.core;

import com.forgebingo.core.PixelArt.Grid;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pixel art for the skill props (see SkillProp): one little original prop per Old School skill that grows in three
 * stages (0 hint, 1 half, 2 complete) and has a gentle 2-frame animation. Chunky 1px dark outline, 2-3 shades per
 * colour, drawn on a 24 x 24 grid with the ground on the bottom row, centred on the middle.
 *
 * This is the DRAWING code (it was core/SkillProps in main before the art was baked into sprites/skillprops.png).
 * Source of truth for SkillPropSheetBaker and SkillPropsBakedTest; the plugin's SkillProps loads the baked sheet.
 */
public final class SkillPropArt
{
    private static final int S = 24;
    private static final Map<String, BufferedImage> CACHE = new ConcurrentHashMap<>();

    private SkillPropArt()
    {
    }

    /**
     * One prop at a growth stage (0, 1 or 2) and animation frame (0 or 1), as a small transparent image whose
     * BOTTOM edge is the ground it stands on. At most 26 x 26.
     */
    public static BufferedImage image(SkillProp prop, int stage, int frame)
    {
        if (prop == null) return null;
        int st = Math.max(0, Math.min(2, stage));
        int f = frame == 0 ? 0 : 1;
        return CACHE.computeIfAbsent(prop + "|" + st + "|" + f, k -> PixelArt.render(build(prop, st, f), SkillPropArt::colour));
    }

    private static int colour(int c)
    {
        switch ((char) c)
        {
            case 'k': return 0x16140f; // outline
            case 'z': return 0x1d1a22; // darkest
            case 'w': return 0xfff7e0; // white
            case 'x': return 0xe6dcc0; // cream
            case 'X': return 0xb9ab8c;
            case 'n': return 0x7a5232; // wood
            case 'N': return 0xa47450;
            case 'F': return 0xc89a68;
            case 'm': return 0x49301c;
            case '1': return 0x58a840; // leaf green
            case '2': return 0x94d868;
            case '3': return 0x2e6a2e;
            case 'g': return 0x8d9098; // stone
            case 'G': return 0xb9bcc4;
            case 'D': return 0x555962;
            case 't': return 0x9aa6b4; // steel
            case 'T': return 0xdbe5ee;
            case 'u': return 0x5a6676;
            case 'i': return 0x5c616c; // iron
            case 'I': return 0x8b92a0;
            case 'j': return 0x363a44;
            case 'r': return 0xd2402e; // red
            case 'R': return 0xf27a62;
            case 'v': return 0x7a2018;
            case 'y': return 0xe8a828; // gold
            case 'Y': return 0xf8d060;
            case 'o': return 0xb08828;
            case 'b': return 0x4a78d0; // blue
            case 'B': return 0x8ab4f0;
            case 'd': return 0x2a4890;
            case 'p': return 0x8a52c8; // purple
            case 'P': return 0xb88ae8;
            case 'q': return 0x523088;
            case 'f': return 0xe03a12; // fire
            case 'a': return 0xff8a1e;
            case 'A': return 0xffd04a;
            case 'e': return 0x6c5634; // dirt
            case 'E': return 0x8a7040;
            case 'l': return 0x3ce870; // mark of grace green
            case 'L': return 0xc0ffd0;
            case 'h': return 0x1f9a4c;
            case 's': return 0xf08cb0; // pink
            case 'c': return 0x5ff0ff; // rune cyan
            case 'C': return 0xd8ffff;
            case 'Z': return 0x2aa0b4;
            case 'H': return 0xc8e6f4; // glass
            case 'O': return 0x6a9cc0;
            default: return 0xff00ff;
        }
    }

    // ---------------------------------------------------------------- helpers

    private static Grid L()
    {
        return new Grid(S, S);
    }

    private static Grid rc(Grid l, int x, int y, int w, int h, char c)
    {
        for (int j = 0; j < h; j++)
        {
            for (int i = 0; i < w; i++) l.set(x + i, y + j, c);
        }
        return l;
    }

    private static Grid ov(Grid l, int x, int y, int w, int h, char c)
    {
        double cx = x + w / 2.0;
        double cy = y + h / 2.0;
        for (int j = 0; j < h; j++)
        {
            for (int i = 0; i < w; i++)
            {
                double dx = (x + i + 0.5 - cx) / (w / 2.0);
                double dy = (y + j + 0.5 - cy) / (h / 2.0);
                if (dx * dx + dy * dy <= 1.0) l.set(x + i, y + j, c);
            }
        }
        return l;
    }

    private static Grid ln(Grid l, int x0, int y0, int x1, int y1, char c)
    {
        int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= n; i++)
        {
            double t = n == 0 ? 0 : i / (double) n;
            l.set((int) Math.round(x0 + (x1 - x0) * t), (int) Math.round(y0 + (y1 - y0) * t), c);
        }
        return l;
    }

    private static boolean occ(boolean[][] o, int x, int y)
    {
        return y >= 0 && y < o.length && x >= 0 && x < o[0].length && o[y][x];
    }

    /** Light from the top left: rim pixels of {@code base} facing down or right turn dark, facing up or left light. */
    private static Grid shade(Grid l, char base, char hi, char lo)
    {
        boolean[][] o = new boolean[l.h][l.w];
        for (int j = 0; j < l.h; j++)
        {
            for (int i = 0; i < l.w; i++) o[j][i] = l.px[j][i] != 0;
        }
        for (int j = 0; j < l.h; j++)
        {
            for (int i = 0; i < l.w; i++)
            {
                if (l.px[j][i] != base) continue;
                boolean up = !occ(o, i, j - 1);
                boolean left = !occ(o, i - 1, j);
                boolean right = !occ(o, i + 1, j);
                boolean down = !occ(o, i, j + 1);
                if (down || right) l.px[j][i] = lo;
                else if (up || left) l.px[j][i] = hi;
            }
        }
        return l;
    }

    private static void P(Grid g, Grid l)
    {
        PixelArt.part(g, l, 'k');
    }

    private static void P(Grid g, Grid l, char outline)
    {
        PixelArt.part(g, l, outline);
    }

    /** Paints a layer without an outline. */
    private static void put(Grid g, Grid l)
    {
        for (int j = 0; j < l.h; j++)
        {
            for (int i = 0; i < l.w; i++)
            {
                if (l.px[j][i] != 0) g.set(i, j, l.px[j][i]);
            }
        }
    }

    private static void sp(Grid g, int x, int y, boolean big, char core, char arm)
    {
        g.set(x, y, core);
        if (big)
        {
            g.set(x - 1, y, arm);
            g.set(x + 1, y, arm);
            g.set(x, y - 1, arm);
            g.set(x, y + 1, arm);
        }
    }

    private static String[] mir(String[] rows)
    {
        String[] out = new String[rows.length];
        for (int i = 0; i < rows.length; i++) out[i] = new StringBuilder(rows[i]).reverse().toString();
        return out;
    }

    private static final String[] BIG = {
        ".......f...",
        ".......ff..",
        "......fff..",
        "......ffaf.",
        ".....fffaf.",
        "....fffaaf.",
        "...fffaaaff",
        "...ffaaaaff",
        "..fffaaAaaf",
        "..ffaaAAaaf",
        ".fffaaAAAaf",
        ".ffaaAAAAaf",
        ".ffaaAAAAaf",
        ".fffaAAAAff",
        "..ffaaAAaf.",
        "...fffffff."};

    private static final String[] SMALL = {
        "...f...",
        "...ff..",
        "..fff..",
        "..faaf.",
        ".faaaaf",
        ".faAAaf",
        ".faAAaf",
        "..ffff."};

    private static void flame(Grid g, int x, int y, String[] rows, boolean mirror)
    {
        Grid l = L();
        l.stamp(x, y, mirror ? mir(rows) : rows);
        P(g, l, 'v');
    }

    private static Grid build(SkillProp p, int st, int f)
    {
        Grid g = new Grid(S, S);
        switch (p)
        {
            case WOODCUTTING: woodcutting(g, st, f); break;
            case FARMING: farming(g, st, f); break;
            case MINING: mining(g, st, f); break;
            case SMITHING: smithing(g, st, f); break;
            case FIREMAKING: firemaking(g, st, f); break;
            case COOKING: cooking(g, st, f); break;
            case FISHING: fishing(g, st, f); break;
            case RUNECRAFT: runecraft(g, st, f); break;
            case MAGIC: magic(g, st, f); break;
            case PRAYER: prayer(g, st, f); break;
            case HERBLORE: herblore(g, st, f); break;
            case CRAFTING: crafting(g, st, f); break;
            case FLETCHING: fletching2(g, st, f); break;
            case CONSTRUCTION: construction(g, st, f); break;
            case AGILITY: agility(g, st, f); break;
            case THIEVING: thieving2(g, st, f); break;
            case HUNTER: hunter2(g, st, f); break;
            case SLAYER: slayer(g, st, f); break;
            case ATTACK: attack(g, st, f); break;
            case STRENGTH: strength(g, st, f); break;
            case DEFENCE: defence(g, st, f); break;
            case RANGED: ranged(g, st, f); break;
            case HITPOINTS: hitpoints(g, st, f); break;
            case SAILING: sailing(g, st, f); break;
            default: break;
        }
        return g;
    }

    // ---------------------------------------------------------------- props

    private static void hunter2(Grid g, int st, int f)
    {
        Grid c = L();
        rc(c, 4, 16, 18, 8, 'N');
        rc(c, 4, 16, 18, 1, 'F');
        rc(c, 6, 18, 14, 5, 'z');
        rc(c, 7, 20, 12, 1, 'm');
        rc(c, 4, 18, 2, 6, 'n');
        rc(c, 20, 18, 2, 6, 'n');
        rc(c, 4, 23, 18, 1, 'n');
        c.pxs('n', 9, 17, 13, 17, 17, 17);
        Grid l = L();
        if (st == 0)
        {
            ov(l, 10, 20, 4, 4, 'r');
            shade(l, 'r', 'R', 'v');
            P(g, l);
            l = L();
            ln(l, 5, 23, 6, 14, 'N');
            ln(l, 6, 23, 7, 14, 'N');
            P(g, l);
            ln(g, 6 - f, 15, 1, 21, 'w');
            return;
        }
        if (st == 1)
        {
            P(g, c);
            l = L();
            ln(l, 1, 23, 2, 17, 'N');
            ln(l, 2, 23, 3, 17, 'N');
            P(g, l);
            return;
        }
        ov(l, 8, 21, 3, 3, 'r');
        shade(l, 'r', 'R', 'v');
        P(g, l);
        g.pxs('w', 9, 21);
        Grid t = L();
        for (int x = 4; x <= 21; x++)
        {
            int lift = (int) Math.round((21 - x) * 4 / 17.0);
            for (int y = 0; y < S; y++)
            {
                if (c.px[y][x] != 0 && y - lift >= 0) t.px[y - lift][x] = c.px[y][x];
            }
        }
        P(g, t);
        l = L();
        ln(l, 1, 23, 3 - f, 20, 'N');
        ln(l, 2, 23, 4 - f, 20, 'N');
        P(g, l);
        ln(g, 0, 18, 0, 22, 'w');
        g.pxs('w', 1, 17 + f);
    }

    private static void thieving2(Grid g, int st, int f)
    {
        Grid l = L();
        if (st == 0)
        {
            ov(l, 8, 16, 9, 8, 'n');
            rc(l, 10, 13, 5, 4, 'n');
            rc(l, 9, 11, 7, 3, 'n');
            shade(l, 'n', 'N', 'm');
            P(g, l);
            g.pxs('y', 11, 14, 12, 14, 13, 14);
            return;
        }
        if (st == 2)
        {
            ov(l, 10, 1, 5, 6, 'y');
            shade(l, 'y', 'Y', 'o');
            P(g, l);
            l = L();
        }
        ov(l, 4, 11, 16, 13, 'n');
        rc(l, 5, 19, 14, 5, 'n');
        rc(l, 9, 8, 6, 4, 'n');
        rc(l, 7, 5, 10, 4, 'n');
        shade(l, 'n', 'N', 'm');
        P(g, l);
        g.pxs('m', 8, 7, 11, 7, 14, 7);
        g.pxs('y', 9, 10, 10, 10, 11, 10, 12, 10, 13, 10, 14, 10);
        g.pxs('Y', 15, 10, 16, 11, 15, 12, 16, 13);
        g.pxs('N', 7, 15, 7, 16, 8, 14);
        if (st == 1)
        {
            l = L();
            ov(l, 19, 21, 5, 3, 'y');
            shade(l, 'y', 'Y', 'o');
            P(g, l);
            return;
        }
        l = L();
        ov(l, 19, 21, 5, 3, 'y');
        ov(l, 14, 22, 5, 2, 'y');
        shade(l, 'y', 'Y', 'o');
        P(g, l);
        l = L();
        l.stamp(0, 19, ".jjjjj.", "jjjjjjj", "jxxjxxj", ".jjjjj.");
        P(g, l);
        if (f == 0) g.pxs('w', 11, 3);
        else sp(g, 12, 3, true, 'w', 'C');
    }

    private static void arrow2(Grid g, int bx, int tx, int ty, char fea, char fea2)
    {
        Grid l = L();
        ln(l, bx, 12, tx, ty + 3, 'F');
        ln(l, bx + 1, 12, tx + 1, ty + 3, 'F');
        P(g, l);
        l = L();
        rc(l, tx - 1, ty, 4, 4, fea);
        l.pxs(fea2, tx - 1, ty, tx, ty + 1, tx + 1, ty + 2, tx + 2, ty + 3);
        P(g, l);
    }

    private static void fletching2(Grid g, int st, int f)
    {
        if (st == 0)
        {
            arrow2(g, 11, 11, 10, 'r', 'R');
        }
        else
        {
            arrow2(g, 9, 5, 3, 'r', 'R');
            arrow2(g, 11, 11 + f, 0, 'x', 'w');
            arrow2(g, 14, 16, 3, 'b', 'B');
        }
        Grid l = L();
        rc(l, 8, st == 0 ? 15 : 11, 8, st == 0 ? 9 : 13, 'n');
        shade(l, 'n', 'N', 'm');
        P(g, l);
        l = L();
        rc(l, 7, st == 0 ? 14 : 10, 10, 2, 'F');
        shade(l, 'F', 'x', 'N');
        P(g, l);
        if (st == 0) return;
        ln(g, 8, 14, 15, 21, 'm');
        g.pxs('Y', 11, 17, 12, 17, 11, 18, 12, 18);
        if (st == 1) return;
        l = L();
        for (int k = 0; k < 2; k++)
        {
            ln(l, 20 - k, 3, 22 - k, 9, 'N');
            ln(l, 22 - k, 9, 22 - k, 15, 'N');
            ln(l, 22 - k, 15, 20 - k, 21, 'N');
        }
        P(g, l);
        ln(g, 20, 4, 18, 7, 'w');
        ln(g, 20, 20, 18, 17, 'w');
        g.pxs('F', 22, 10, 22, 12);
    }

    private static void woodcutting(Grid g, int st, int f)
    {
        Grid l = L();
        if (st == 0)
        {
            rc(l, 11, 19, 2, 5, 'n');
            shade(l, 'n', 'N', 'm');
            P(g, l);
            l = L();
            ov(l, 8, 15, 8, 5, '1');
            shade(l, '1', '2', '3');
            P(g, l);
            return;
        }
        if (st == 1)
        {
            rc(l, 10, 13, 4, 11, 'n');
            rc(l, 9, 21, 6, 3, 'n');
            shade(l, 'n', 'N', 'm');
            P(g, l);
            l = L();
            ov(l, 5, 4, 14, 11, '1');
            shade(l, '1', '2', '3');
            P(g, l);
            g.pxs('3', 9, 10, 14, 8, 15, 11);
            g.pxs('2', 9, 7, 12, 6);
            return;
        }
        rc(l, 10, 14, 5, 10, 'n');
        rc(l, 8, 21, 9, 3, 'n');
        shade(l, 'n', 'N', 'm');
        P(g, l);
        l = L();
        ov(l, 2, 6, 11, 11, '1');
        ov(l, 11, 6, 11, 11, '1');
        ov(l, 6 + f, 0, 13, 13, '1');
        shade(l, '1', '2', '3');
        P(g, l);
        g.pxs('3', 8 + f, 11, 15, 10, 11, 14, 18 - f, 13, 5, 10, 12, 7);
        g.pxs('2', 9 + f, 3, 13 + f, 2, 5, 9, 17, 8, 10, 8);
        if (f == 0) g.pxs('2', 23, 12, 23, 13);
        else g.pxs('2', 22, 17, 23, 17);
    }

    private static void farming(Grid g, int st, int f)
    {
        Grid l = L();
        if (st == 0)
        {
            ov(l, 5, 19, 14, 5, 'e');
            shade(l, 'e', 'E', 'm');
            P(g, l);
            l = L();
            rc(l, 12, 14, 1, 6, '1');
            ov(l, 8, 13, 5, 3, '1');
            ov(l, 13, 12, 5, 3, '1');
            shade(l, '1', '2', '3');
            P(g, l);
            return;
        }
        if (st == 1)
        {
            ov(l, 3, 17, 18, 7, 'e');
            shade(l, 'e', 'E', 'm');
            P(g, l);
            l = L();
            rc(l, 11, 9, 2, 10, '3');
            ov(l, 4, 9, 8, 5, '1');
            ov(l, 12, 8, 8, 5, '1');
            ov(l, 8, 3, 8, 6, '1');
            shade(l, '1', '2', '3');
            P(g, l);
            return;
        }
        ov(l, 1, 17, 22, 7, 'e');
        shade(l, 'e', 'E', 'm');
        P(g, l);
        g.pxs('m', 4, 20, 5, 20, 6, 20, 16, 21, 17, 21, 18, 21, 9, 22, 10, 22, 11, 22);
        l = L();
        rc(l, 11, 6, 2, 12, '3');
        P(g, l);
        int[][] leaves = {{3, 14, 8, 4}, {13, 14, 8, 4}, {4, 9, 7, 4}, {13, 9, 7, 4}, {10, 4, 4, 6}};
        for (int k = 0; k < leaves.length; k++)
        {
            int[] v = leaves[k];
            l = L();
            ov(l, v[0], v[1] + (k < 4 ? f * (k % 2 == 0 ? 1 : 0) : 0), v[2], v[3], '1');
            shade(l, '1', '2', '3');
            P(g, l);
        }
        l = L();
        l.pxs('s', 12 + f, 1, 11 + f, 2, 13 + f, 2, 12 + f, 3);
        l.set(12 + f, 2, 'Y');
        P(g, l);
        g.set(12 + f, 2, 'Y');
    }

    private static void ore(Grid g, int x, int y)
    {
        g.pxs('y', x + 1, y, x, y + 1, x + 1, y + 1);
        g.set(x, y, 'Y');
        g.set(x + 2, y + 2, 'o');
    }

    private static void mining(Grid g, int st, int f)
    {
        Grid l = L();
        if (st == 0)
        {
            ov(l, 7, 19, 8, 5, 'g');
            ov(l, 14, 21, 5, 3, 'g');
            ov(l, 3, 21, 5, 3, 'g');
            shade(l, 'g', 'G', 'D');
            P(g, l);
            return;
        }
        if (st == 1)
        {
            ov(l, 2, 13, 12, 11, 'g');
            ov(l, 8, 10, 11, 14, 'g');
            rc(l, 2, 19, 17, 5, 'g');
            shade(l, 'g', 'G', 'D');
            P(g, l);
            g.pxs('D', 9, 14, 10, 15, 10, 16, 15, 19, 16, 20, 7, 20);
            g.pxs('y', 13, 15);
            return;
        }
        ov(l, 1, 10, 11, 14, 'g');
        ov(l, 6, 4, 12, 20, 'g');
        rc(l, 1, 19, 17, 5, 'g');
        shade(l, 'g', 'G', 'D');
        P(g, l);
        g.pxs('D', 9, 11, 9, 12, 14, 16, 3, 20, 4, 21);
        ore(g, 3, 14);
        ore(g, 9, 7);
        ore(g, 12, 12);
        ore(g, 7, 17);
        ore(g, 13, 18);
        sp(g, 4, 15, f == 0, 'w', 'Y');
        sp(g, 10, 8, f == 1, 'w', 'Y');
        l = L();
        ln(l, 21, 23, 20, 10, 't');
        ln(l, 22, 23, 21, 10, 't');
        ln(l, 16, 12, 17, 9, 't');
        ln(l, 17, 9, 19, 8, 't');
        ln(l, 19, 8, 21, 8, 't');
        ln(l, 21, 8, 23, 10, 't');
        ln(l, 17, 10, 18, 9, 't');
        ln(l, 19, 9, 21, 9, 't');
        ln(l, 22, 10, 23, 12, 't');
        P(g, l);
        for (int y = 11; y < 23; y++) g.set(21, y, 'n');
        for (int y = 11; y < 23; y++) g.set(22, y, 'm');
        g.pxs('T', 16, 12, 17, 9, 18, 8, 19, 8, 20, 8);
        g.pxs('u', 21, 8, 22, 9, 23, 10, 23, 11);
        g.pxs('N', 21, 13, 21, 17, 21, 21);
    }

    private static void smithing(Grid g, int st, int f)
    {
        Grid l = L();
        if (st == 0)
        {
            rc(l, 7, 17, 10, 7, 't');
            shade(l, 't', 'T', 'u');
            P(g, l);
            return;
        }
        rc(l, 4, 10, 17, 3, 't');
        rc(l, 0, 10, 4, 1, 't');
        rc(l, 1, 11, 3, 1, 't');
        rc(l, 2, 12, 2, 1, 't');
        rc(l, 8, 13, 9, 6, 't');
        rc(l, 6, 19, 13, 2, 't');
        rc(l, 4, 21, 17, 3, 't');
        shade(l, 't', 'T', 'u');
        for (int x = 0; x < S; x++)
        {
            if (l.px[10][x] != 0) l.px[10][x] = 'T';
        }
        P(g, l);
        g.pxs('u', 9, 13, 10, 13, 15, 13);
        if (st == 1) return;
        int dy = f == 0 ? 0 : -2;
        l = L();
        rc(l, 9, 5 + dy, 7, 5, 'i');
        shade(l, 'i', 'I', 'j');
        rc(l, 16, 7 + dy, 7, 3, 'n');
        P(g, l);
        g.pxs('N', 17, 7 + dy, 18, 7 + dy, 19, 7 + dy, 20, 7 + dy);
        g.pxs('T', 10, 6 + dy, 11, 6 + dy);
        if (f == 0)
        {
            g.pxs('A', 5, 7);
        }
        else
        {
            g.pxs('A', 3, 5, 7, 3, 8, 7, 5, 8, 2, 8);
            g.pxs('a', 6, 5, 9, 5, 4, 3);
        }
    }

    private static void logs(Grid g, int inset)
    {
        int x0 = inset;
        int x1 = S - 1 - inset;
        Grid l = L();
        for (int k = 0; k < 3; k++)
        {
            ln(l, x0, 16 + k, x1, 21 + k, 'n');
            ln(l, x0, 21 + k, x1, 16 + k, 'n');
        }
        shade(l, 'n', 'N', 'm');
        P(g, l);
        g.pxs('E', x0, 17, x0, 22, x0 + 1, 17, x0 + 1, 22);
        g.pxs('E', x1, 17, x1, 22, x1 - 1, 17, x1 - 1, 22);
    }

    private static void firemaking(Grid g, int st, int f)
    {
        if (st == 0)
        {
            logs(g, 4);
            g.pxs('a', 12, 18, 11, 19);
            return;
        }
        if (st == 1)
        {
            logs(g, 3);
            flame(g, 9, 11, SMALL, f == 1);
            return;
        }
        logs(g, 1);
        if (f == 0) flame(g, 7, 3, BIG, false);
        else flame(g, 7, 4, Arrays.copyOfRange(BIG, 1, BIG.length), true);
        if (f == 0) g.pxs('A', 5, 6, 18, 4, 4, 11);
        else g.pxs('A', 4, 4, 19, 7, 20, 3, 5, 9);
    }

    private static void puff(Grid g, int x, int y)
    {
        rc(g.stamp(0, 0), x, y, 2, 2, 'x');
        g.set(x, y, 'w');
    }

    private static void cooking(Grid g, int st, int f)
    {
        flame(g, 9, 15, SMALL, f == 1);
        Grid l = L();
        ov(l, 0, 18, 8, 6, 'g');
        ov(l, 16, 18, 8, 6, 'g');
        shade(l, 'g', 'G', 'D');
        P(g, l);
        if (st == 0) return;
        l = L();
        ln(l, 2, 8, 1, 11, 'I');
        ln(l, 1, 11, 3, 13, 'I');
        ln(l, 21, 8, 22, 11, 'I');
        ln(l, 22, 11, 20, 13, 'I');
        P(g, l);
        l = L();
        ov(l, 3, 6, 18, 12, 'i');
        shade(l, 'i', 'I', 'j');
        P(g, l);
        l = L();
        ov(l, 5, 6, 14, 5, st == 1 ? 'j' : '1');
        put(g, l);
        if (st == 1) return;
        g.pxs('2', 7, 8, 10, 7, 15, 9, 8, 9);
        g.pxs('L', 9 + f * 3, 8, 15 - f * 3, 7);
        if (f == 0)
        {
            puff(g, 8, 3);
            puff(g, 13, 1);
        }
        else
        {
            puff(g, 9, 2);
            puff(g, 14, 0);
            g.pxs('w', 6, 4);
        }
    }

    private static void fishing(Grid g, int st, int f)
    {
        Grid l = L();
        if (st == 0)
        {
            ov(l, 5, 18, 14, 6, 'b');
        }
        else
        {
            ov(l, 1, 16, 22, 8, 'b');
        }
        shade(l, 'b', 'B', 'd');
        P(g, l);
        // ripples
        int cx = 12;
        g.pxs('w', cx - 2, 20, cx + 2, 20, cx - 1, 19, cx, 19, cx + 1, 19, cx - 1, 21, cx, 21, cx + 1, 21);
        if (st == 0) return;
        g.pxs('B', 5, 21, 6, 22, 17, 22, 18, 21);
        // bubbles
        int[][] bub = st == 1 ? new int[][] {{8, 13}, {13, 10}, {16, 14}} : new int[0][];
        for (int[] b : bub)
        {
            Grid bl = L();
            rc(bl, b[0], b[1], 2, 2, 'B');
            P(g, bl, 'd');
            g.set(b[0], b[1], 'w');
        }
        if (st == 1) return;
        // rod
        l = L();
        ln(l, 22, 23, 17, 3, 'n');
        ln(l, 23, 23, 18, 3, 'n');
        P(g, l);
        ln(g, 22, 22, 17, 4, 'N');
        for (int y = 4; y < 15; y++) g.set(16, y, 'w');
        g.pxs('r', 16, 15, 17, 15);
        g.pxs('w', 16, 16, 17, 16);
        // fish
        int fy = f == 0 ? 7 : 3;
        int fx = 4;
        l = L();
        ov(l, fx + 2, fy, 8, 5, 'a');
        shade(l, 'a', 'A', 'f');
        l.pxs('f', fx, fy, fx, fy + 4, fx + 1, fy + 1, fx + 1, fy + 2, fx + 1, fy + 3);
        P(g, l, 'v');
        g.set(fx + 8, fy + 1, 'z');
        g.pxs('w', fx + 4, fy + 1);
        if (f == 1) g.pxs('B', 5, 14, 9, 13, 12, 14, 3, 12, 13, 11);
        else g.pxs('B', 8, 15, 11, 15);
    }

    private static void runecraft(Grid g, int st, int f)
    {
        Grid l = L();
        rc(l, 2, 19, 20, 5, 'g');
        shade(l, 'g', 'G', 'D');
        P(g, l);
        if (st == 0)
        {
            g.pxs('c', 12, 17);
            return;
        }
        l = L();
        rc(l, 4, 13, 16, 6, 'g');
        shade(l, 'g', 'G', 'D');
        P(g, l);
        l = L();
        rc(l, 2, 9, 20, 4, 'g');
        shade(l, 'g', 'G', 'D');
        P(g, l);
        int[][] glyph = {{12, 14}, {11, 15}, {12, 15}, {13, 15}, {10, 16}, {12, 16}, {14, 16}, {11, 17}, {12, 17}, {13, 17}, {12, 18}};
        int[][] pts = {{12, 13}, {11, 14}, {12, 14}, {13, 14}, {10, 15}, {12, 15}, {14, 15}, {11, 16}, {12, 16}, {13, 16}, {12, 17}};
        if (st == 1)
        {
            for (int[] q : pts) g.set(q[0], q[1], 'D');
            return;
        }
        if (f == 1)
        {
            g.pxs('Z', 8, 15, 16, 15, 9, 13, 15, 13, 9, 17, 15, 17, 12, 18, 12, 12);
        }
        for (int[] q : pts) g.set(q[0], q[1], f == 0 ? 'c' : 'C');
        g.set(12, 15, f == 0 ? 'C' : 'w');
        if (f == 0) g.pxs('c', 8, 6, 15, 3);
        else g.pxs('c', 9, 4, 14, 6, 16, 2);
    }

    private static void magic(Grid g, int st, int f)
    {
        Grid l = L();
        if (st == 0)
        {
            ov(l, 6, 19, 12, 5, 'p');
            shade(l, 'p', 'P', 'q');
            P(g, l);
            g.pxs('A', 12, 15);
            return;
        }
        ov(l, 1, 17, 22, 7, 'p');
        shade(l, 'p', 'P', 'q');
        P(g, l);
        int top = st == 1 ? 9 : 3;
        int bot = 19;
        l = L();
        for (int y = top; y <= bot; y++)
        {
            double t = (y - top) / (double) (bot - top);
            int hw = (int) Math.round(1 + t * 6);
            int bend = (st == 2 && y < top + 6) ? (top + 6 - y) / 2 : 0;
            rc(l, 12 - hw + bend, y, 2 * hw + 1, 1, 'p');
        }
        if (st == 2) l.pxs('p', 15, 2, 16, 2, 17, 3, 17, 4);
        shade(l, 'p', 'P', 'q');
        P(g, l);
        if (st == 1) return;
        for (int x = 4; x < 20; x++)
        {
            if (g.px[16][x] == 'p' || g.px[16][x] == 'P' || g.px[16][x] == 'q') g.set(x, 16, 'y');
            if (g.px[17][x] == 'p' || g.px[17][x] == 'P' || g.px[17][x] == 'q') g.set(x, 17, 'o');
        }
        g.pxs('Y', 11, 16, 12, 16, 13, 16);
        g.pxs('Y', 10, 9, 13, 12);
        sp(g, 3, 6, f == 0, 'w', 'Y');
        sp(g, 20, 10, f == 1, 'w', 'Y');
        sp(g, 7, 1, f == 1, 'w', 'Y');
        sp(g, 19, 3, f == 0, 'w', 'Y');
    }

    private static void prayer(Grid g, int st, int f)
    {
        Grid l = L();
        rc(l, 4, 18, 16, 6, 'x');
        shade(l, 'x', 'w', 'X');
        P(g, l);
        if (st == 0) return;
        l = L();
        rc(l, 2, 15, 20, 4, 'x');
        shade(l, 'x', 'w', 'X');
        P(g, l);
        if (st == 1) return;
        l = L();
        rc(l, 8, 19, 8, 5, 'p');
        shade(l, 'p', 'P', 'q');
        P(g, l);
        g.pxs('y', 8, 19, 9, 19, 10, 19, 11, 19, 12, 19, 13, 19, 14, 19, 15, 19);
        g.pxs('Y', 11, 21, 12, 21, 12, 22);
        // candle
        l = L();
        rc(l, 11, 9, 3, 6, 'Y');
        shade(l, 'Y', 'w', 'y');
        P(g, l);
        // small skull
        l = L();
        ov(l, 15, 9, 6, 6, 'x');
        shade(l, 'x', 'w', 'X');
        P(g, l);
        g.pxs('z', 16, 11, 19, 11, 17, 13, 18, 13);
        // flame and glow
        l = L();
        l.stamp(11, 4 + f, ".a.", "aAa", "aAa", ".a.");
        P(g, l, 'v');
        if (f == 0) g.pxs('Y', 9, 6, 15, 6, 12, 2);
        else g.pxs('Y', 8, 5, 16, 5, 9, 3, 15, 3, 12, 1, 7, 7, 17, 7);
        g.pxs('A', 5, 11, 6, 12);
    }

    private static void flask(Grid g, int bx, int by, int bw, int bh, int nx, int ny, int nw, int nh, int level, int f, boolean bubbles)
    {
        Grid l = L();
        rc(l, nx, ny, nw, nh, 'H');
        ov(l, bx, by, bw, bh, 'H');
        shade(l, 'H', 'w', 'O');
        P(g, l);
        l = L();
        rc(l, nx - 1, ny - 3, nw + 2, 3, 'n');
        shade(l, 'n', 'N', 'm');
        P(g, l);
        if (level >= 0)
        {
            for (int y = level; y < S; y++)
            {
                for (int x = 0; x < S; x++)
                {
                    if (g.px[y][x] == 'H') g.px[y][x] = y == level ? '2' : '1';
                }
            }
            g.pxs('3', bx + 3, level + 3, bx + bw - 4, level + 4);
        }
        g.pxs('w', bx + 2, by + 4, bx + 2, by + 5, bx + 3, by + 3);
        if (bubbles)
        {
            if (f == 0) g.pxs('L', 10, 15, 14, 18, 9, 20, 12, 12);
            else g.pxs('L', 10, 12, 14, 15, 9, 17, 12, 9, 13, 5);
        }
    }

    private static void herblore(Grid g, int st, int f)
    {
        if (st == 0) flask(g, 8, 15, 8, 8, 10, 12, 4, 4, -1, f, false);
        else if (st == 1) flask(g, 4, 10, 16, 14, 9, 5, 6, 7, 17, f, false);
        else flask(g, 4, 10, 16, 14, 9, 5, 6, 7, 13, f, true);
    }

    private static void crafting(Grid g, int st, int f)
    {
        Grid l = L();
        if (st == 0)
        {
            rc(l, 7, 19, 10, 5, 'r');
        }
        else
        {
            rc(l, 3, 15, 18, 8, 'r');
        }
        shade(l, 'r', 'R', 'v');
        P(g, l);
        l = L();
        if (st == 0) l.pxs('Y', 6, 19, 17, 19, 6, 23, 17, 23);
        else l.pxs('Y', 2, 15, 21, 15, 2, 22, 21, 22);
        P(g, l);
        g.pxs('y', st == 0 ? 12 : 12, st == 0 ? 21 : 19);
        if (st == 0) return;
        if (st == 1)
        {
            l = L();
            ov(l, 7, 8, 10, 8, 'g');
            shade(l, 'g', 'G', 'D');
            P(g, l);
            g.pxs('D', 10, 12, 11, 13, 14, 10);
            return;
        }
        String[] gem = {
            "..BBBBBB..",
            ".BwwBBbbd.",
            "BBwBBBbbdd",
            "BBBBBbbbdd",
            ".dBBBbbdd.",
            "..ddBbdd..",
            "...ddbd...",
            "....dd...."};
        l = L();
        l.stamp(7, 7, gem);
        P(g, l);
        sp(g, 19, 6, f == 0, 'w', 'C');
        sp(g, 4, 9, f == 1, 'w', 'C');
        if (f == 0) g.pxs('w', 4, 9);
        else g.pxs('w', 19, 6);
    }

    private static void arrow(Grid g, int x, int top, int lean, char feather, char feather2)
    {
        Grid l = L();
        ln(l, x, 22, x + lean, top + 3, 'n');
        P(g, l);
        g.pxs('N', x, 20, x + lean, top + 5);
        l = L();
        int fx = x + lean;
        rc(l, fx - 1, top, 3, 4, feather);
        l.set(fx, top, feather2);
        l.set(fx, top + 1, 'n');
        l.set(fx, top + 2, 'n');
        l.set(fx, top + 3, 'n');
        P(g, l);
        g.set(fx - 1, top + 1, feather2);
        g.set(fx + 1, top + 2, feather2);
    }

    private static void fletching(Grid g, int st, int f)
    {
        Grid l = L();
        ov(l, 3, 20, 18, 4, 'e');
        shade(l, 'e', 'E', 'm');
        P(g, l);
        if (st == 0)
        {
            arrow(g, 17, 16, 0, 'r', 'R');
            return;
        }
        if (st == 1)
        {
            arrow(g, 8, 11, -1, 'r', 'R');
            arrow(g, 15, 8, 1, 'x', 'w');
            return;
        }
        arrow(g, 6, 10, f == 0 ? -1 : -2, 'r', 'R');
        arrow(g, 18, 9, f == 0 ? 1 : 2, 'b', 'B');
        arrow(g, 12, 4, f == 0 ? 0 : 1, 'x', 'w');
        g.pxs('Y', 12, 21, 11, 21);
    }

    private static void construction(Grid g, int st, int f)
    {
        Grid l = L();
        if (st == 0)
        {
            rc(l, 3, 20, 18, 4, 'N');
            rc(l, 5, 16, 14, 4, 'N');
            shade(l, 'N', 'F', 'n');
            P(g, l);
            g.pxs('m', 6, 22, 17, 22, 8, 18, 15, 18);
            return;
        }
        if (st == 1)
        {
            rc(l, 3, 11, 3, 13, 'N');
            rc(l, 18, 11, 3, 13, 'N');
            rc(l, 2, 21, 20, 3, 'N');
            rc(l, 3, 15, 18, 2, 'N');
            for (int k = 0; k < 2; k++)
            {
                ln(l, 3, 10 + k, 12, 3 + k, 'N');
                ln(l, 12, 3 + k, 20, 10 + k, 'N');
            }
            shade(l, 'N', 'F', 'n');
            P(g, l);
            g.pxs('m', 4, 18, 19, 18, 4, 13, 19, 13);
            return;
        }
        // hut
        rc(l, 2, 13, 14, 11, 'N');
        shade(l, 'N', 'F', 'n');
        P(g, l);
        for (int x = 5; x < 16; x += 3)
        {
            for (int y = 14; y < 23; y++) g.set(x, y, 'n');
        }
        l = L();
        rc(l, 11, 3, 3, 5, 'g');
        shade(l, 'g', 'G', 'D');
        P(g, l);
        l = L();
        for (int y = 4; y <= 12; y++)
        {
            int hw = 1 + (int) Math.round((y - 4) * 1.0);
            rc(l, 9 - hw, y, 2 * hw + 1, 1, 'r');
        }
        shade(l, 'r', 'R', 'v');
        P(g, l);
        l = L();
        rc(l, 6, 17, 4, 7, 'm');
        P(g, l);
        g.pxs('Y', 9, 20);
        l = L();
        rc(l, 11, 16, 3, 3, 'B');
        P(g, l);
        g.pxs('w', 11, 16);
        // hammer
        int hx = f == 0 ? 0 : -1;
        l = L();
        ln(l, 21, 23, 21 + hx, 10, 'n');
        ln(l, 22, 23, 22 + hx, 10, 'n');
        rc(l, 18 + hx, 6, 6, 4, 'i');
        P(g, l);
        g.pxs('I', 18 + hx, 6, 19 + hx, 6, 20 + hx, 6, 21 + hx, 6);
        for (int y = 11; y < 23; y += 3) g.set(21, y, 'N');
        if (f == 0) g.pxs('x', 12, 1, 13, 0);
        else g.pxs('x', 13, 1, 12, 0, 14, 0);
    }

    private static void agility(Grid g, int st, int f)
    {
        Grid l = L();
        rc(l, 3, 13, 4, 11, 'n');
        if (st >= 1) rc(l, 17, 13, 4, 11, 'n');
        shade(l, 'n', 'N', 'm');
        P(g, l);
        g.pxs('1', 2, 23, 7, 23, 16, 23, 21, 23, 1, 22);
        if (st < 2) return;
        l = L();
        ov(l, 0, 8, 24, 6, 'N');
        shade(l, 'N', 'F', 'n');
        P(g, l);
        g.pxs('E', 1, 10, 1, 11, 22, 10, 22, 11);
        g.pxs('m', 2, 10, 2, 11, 21, 10, 21, 11);
        g.pxs('n', 7, 12, 14, 12, 18, 9);
        int my = f == 0 ? 3 : 2;
        l = L();
        l.stamp(9, my, "...l...", "..lLl..", ".lLLLl.", "..lLl..", "...l...");
        P(g, l, 'h');
        if (f == 1)
        {
            g.pxs('L', 8, my - 1, 16, my - 1, 7, my + 4, 17, my + 4);
            g.pxs('w', 12, my + 2);
        }
        else g.pxs('w', 12, my + 1);
    }

    private static void thieving(Grid g, int st, int f)
    {
        Grid l = L();
        if (st == 0)
        {
            ov(l, 8, 17, 8, 7, 'n');
            rc(l, 10, 15, 4, 3, 'n');
        }
        else if (st == 1)
        {
            ov(l, 6, 12, 12, 12, 'n');
            rc(l, 9, 9, 6, 4, 'n');
            rc(l, 7, 6, 10, 3, 'n');
        }
        else
        {
            ov(l, 4, 10, 15, 14, 'n');
            rc(l, 9, 7, 6, 5, 'n');
            rc(l, 6, 3, 12, 4, 'n');
            l.pxs('n', 5, 2, 18, 2);
        }
        shade(l, 'n', 'N', 'm');
        P(g, l);
        if (st == 0)
        {
            g.pxs('y', 10, 16, 13, 16);
            g.pxs('Y', 10, 19);
            return;
        }
        int ny = st == 1 ? 12 : 10;
        g.pxs('y', 9, ny, 10, ny, 11, ny, 12, ny, 13, ny, 14, ny);
        g.pxs('o', 10, ny + 1, 13, ny + 1);
        if (st == 1) return;
        l = L();
        ov(l, 8, 15, 7, 7, 'y');
        shade(l, 'y', 'Y', 'o');
        P(g, l);
        g.pxs('o', 11, 17, 11, 18, 11, 19);
        l = L();
        ov(l, 19, 21, 5, 3, 'y');
        ov(l, 1, 22, 4, 2, 'y');
        shade(l, 'y', 'Y', 'o');
        P(g, l);
        sp(g, 10, 16, f == 0, 'w', 'Y');
        sp(g, 21, 21, f == 1, 'w', 'Y');
    }

    private static void hunter(Grid g, int st, int f)
    {
        Grid l = L();
        if (st == 0)
        {
            ln(l, 9, 23, 10, 14, 'n');
            ln(l, 10, 18, 6, 13, 'n');
            ln(l, 10, 17, 13, 13, 'n');
            shade(l, 'n', 'N', 'm');
            P(g, l);
            g.pxs('1', 5, 23, 14, 23, 15, 22);
            return;
        }
        if (st == 1)
        {
            rc(l, 5, 15, 15, 8, 'n');
            shade(l, 'n', 'N', 'm');
            P(g, l);
            for (int x = 8; x < 20; x += 3)
            {
                for (int y = 16; y < 22; y++) g.set(x, y, 'm');
            }
            l = L();
            ln(l, 2, 23, 2, 16, 'n');
            P(g, l);
            return;
        }
        // bait
        l = L();
        rc(l, 6, 21, 4, 2, 'a');
        P(g, l);
        g.pxs('1', 5, 20, 6, 20);
        // box, slanted: its left end is propped up by the stick
        l = L();
        for (int x = 3; x <= 21; x++)
        {
            double lift = f * (21 - x) / 18.0;
            int yTop = (int) Math.round(8 + (x - 3) * 7 / 18.0 - lift);
            rc(l, x, yTop, 1, 8, 'n');
        }
        shade(l, 'n', 'N', 'm');
        for (int x = 5; x <= 21; x++)
        {
            if ((x - 3) % 3 != 2) continue;
            int first = -1;
            for (int y = 0; y < S; y++)
            {
                if (l.px[y][x] == 0) continue;
                if (first < 0) first = y;
                if (y >= first + 2 && y <= first + 6) l.px[y][x] = 'z';
            }
        }
        P(g, l);
        l = L();
        ln(l, 1 + f, 23, 3 + f, 16, 'N');
        P(g, l);
        g.pxs('w', 3 + f, 15);
        g.pxs('F', 2, 21);
    }

    private static void slayer(Grid g, int st, int f)
    {
        Grid l = L();
        ov(l, 5, 21, 14, 3, 'g');
        rc(l, 10, 14, 4, 9, 'n');
        shade(l, 'n', 'N', 'm');
        P(g, l);
        if (st == 0)
        {
            return;
        }
        l = L();
        ov(l, 5, 2, 14, 12, 'x');
        rc(l, 8, 11, 8, 5, 'x');
        shade(l, 'x', 'w', 'X');
        P(g, l);
        l = L();
        rc(l, 7, 7, 4, 4, 'z');
        rc(l, 13, 7, 4, 4, 'z');
        put(g, l);
        g.pxs('z', 12, 11, 11, 12, 12, 12);
        g.pxs('X', 10, 14, 12, 14, 14, 14, 10, 15, 14, 15);
        if (st == 1)
        {
            g.pxs('v', 9, 9, 15, 9);
            return;
        }
        if (f == 0)
        {
            g.pxs('r', 9, 9, 15, 9, 8, 8, 14, 8);
        }
        else
        {
            rc(g.stamp(0, 0), 8, 8, 3, 3, 'R');
            rc(g.stamp(0, 0), 14, 8, 3, 3, 'R');
            g.pxs('A', 9, 9, 15, 9);
        }
        l = L();
        rc(l, 8, 18, 8, 2, 'r');
        l.pxs('r', 7, 19, 16, 19, 6, 20, 17, 20);
        shade(l, 'r', 'R', 'v');
        P(g, l);
    }

    private static void attack(Grid g, int st, int f)
    {
        Grid l = L();
        if (st == 0)
        {
            rc(l, 11, 16, 2, 5, 't');
            shade(l, 't', 'T', 'u');
            P(g, l);
        }
        else
        {
            rc(l, 10, 10, 4, 12, 't');
            l.pxs('t', 11, 8, 12, 8, 11, 9, 12, 9);
            shade(l, 't', 'T', 'u');
            for (int y = 11; y < 21; y++) l.set(12, y, 'T');
            P(g, l);
            l = L();
            rc(l, 5, 7, 14, 3, 'y');
            shade(l, 'y', 'Y', 'o');
            P(g, l);
            if (st == 2)
            {
                l = L();
                rc(l, 10, 2, 4, 5, 'n');
                shade(l, 'n', 'N', 'm');
                l.pxs('m', 10, 4, 13, 5);
                P(g, l);
                l = L();
                ov(l, 9, 0, 6, 3, 'y');
                shade(l, 'y', 'Y', 'o');
                P(g, l);
                sp(g, 11, f == 0 ? 13 : 17, true, 'w', 'T');
            }
        }
        l = L();
        ov(l, 3, 19, 18, 5, 'e');
        shade(l, 'e', 'E', 'm');
        P(g, l);
        g.pxs('1', 3, 21, 4, 20, 19, 21, 18, 20);
    }

    private static void strength(Grid g, int st, int f)
    {
        Grid l = L();
        if (st == 0)
        {
            rc(l, 9, 14, 6, 10, 'i');
            shade(l, 'i', 'I', 'j');
            P(g, l);
            return;
        }
        rc(l, 7, 17, 10, 3, 't');
        shade(l, 't', 'T', 'u');
        P(g, l);
        l = L();
        rc(l, 4, 11, 4, 13, 'i');
        rc(l, 16, 11, 4, 13, 'i');
        shade(l, 'i', 'I', 'j');
        P(g, l);
        if (st == 1) return;
        l = L();
        rc(l, 0, 9, 4, 15, 'r');
        rc(l, 20, 9, 4, 15, 'r');
        shade(l, 'r', 'R', 'v');
        P(g, l);
        g.pxs('w', f == 0 ? 1 : 21, 11, f == 0 ? 1 : 21, 12);
        if (f == 1)
        {
            g.pxs('Y', 7, 6, 7, 5, 12, 3, 12, 4, 17, 6, 17, 5, 3, 3, 21, 3);
        }
    }

    private static void shp(Grid l, int top, int bot, int hw, char c)
    {
        for (int y = top; y <= bot; y++)
        {
            double t = (y - top) / (double) (bot - top);
            int w = t < 0.45 ? 2 * hw : (int) Math.round(2 * hw * (1 - (t - 0.45) / 0.55 * 0.95));
            if (w < 2) w = 2;
            if (y == top) w -= 2;
            rc(l, 12 - w / 2, y, w, 1, c);
        }
    }

    private static void defence(Grid g, int st, int f)
    {
        Grid l = L();
        if (st == 0)
        {
            shp(l, 15, 23, 4, 't');
            shade(l, 't', 'T', 'u');
            P(g, l);
            return;
        }
        if (st == 1)
        {
            shp(l, 8, 23, 6, 't');
            shade(l, 't', 'T', 'u');
            P(g, l);
            l = L();
            ov(l, 10, 12, 4, 4, 'I');
            shade(l, 'I', 'T', 'u');
            P(g, l);
            return;
        }
        shp(l, 2, 23, 8, 'y');
        shade(l, 'y', 'Y', 'o');
        P(g, l);
        l = L();
        shp(l, 4, 20, 6, 'b');
        shade(l, 'b', 'B', 'd');
        P(g, l);
        l = L();
        ov(l, 9, 8, 6, 6, 'y');
        shade(l, 'y', 'Y', 'o');
        P(g, l);
        g.pxs('w', 8, 6, 9, 5);
        if (f == 0) g.pxs('w', 7, 7, 8, 6, 4, 3);
        else g.pxs('w', 15, 9, 16, 8, 17, 3);
        g.pxs('x', 11, 10);
    }

    private static void ranged(Grid g, int st, int f)
    {
        Grid l = L();
        ln(l, 12, 16, 6, 23, 'n');
        ln(l, 13, 16, 7, 23, 'n');
        ln(l, 11, 16, 17, 23, 'n');
        ln(l, 12, 16, 18, 23, 'n');
        shade(l, 'n', 'N', 'm');
        P(g, l);
        l = L();
        if (st == 0)
        {
            ov(l, 7, 11, 10, 10, 'y');
            shade(l, 'y', 'Y', 'o');
            P(g, l);
            return;
        }
        for (int y = 0; y < 21; y++)
        {
            for (int x = 0; x < S; x++)
            {
                double d = Math.hypot(x + 0.5 - 12, y + 0.5 - 10.5);
                if (d <= 2.3) l.set(x, y, 'y');
                else if (d <= 4.6) l.set(x, y, 'r');
                else if (d <= 7.2) l.set(x, y, 'w');
                else if (d <= 10.3) l.set(x, y, 'b');
            }
        }
        P(g, l);
        g.pxs('Y', 11, 9);
        g.pxs('B', 5, 6, 6, 5);
        if (st == 1) return;
        int q = f;
        l = L();
        ln(l, 12, 10, 19, 4 + q, 'N');
        P(g, l);
        l = L();
        rc(l, 19, 1 + q, 4, 4, 'r');
        l.pxs('w', 20, 2 + q, 21, 3 + q);
        P(g, l);
        g.pxs('T', 12, 10);
    }

    private static void hitpoints(Grid g, int st, int f)
    {
        Grid l = L();
        rc(l, 5, 20, 14, 4, 'g');
        shade(l, 'g', 'G', 'D');
        P(g, l);
        l = L();
        rc(l, 8, 17, 8, 3, 'g');
        shade(l, 'g', 'G', 'D');
        P(g, l);
        if (st == 0) return;
        String[] rows;
        int x;
        int y;
        if (st == 1)
        {
            rows = new String[] {".rr.rr.", "rrrrrrr", "rrrrrrr", ".rrrrr.", "..rrr..", "...r..."};
            x = 9;
            y = 11;
        }
        else if (f == 0)
        {
            rows = new String[] {".rrr...rrr.", "rrrrr.rrrrr", "rrrrrrrrrrr", "rrrrrrrrrrr", ".rrrrrrrrr.", "..rrrrrrr..", "...rrrrr...", "....rrr....", ".....r....."};
            x = 7;
            y = 8;
        }
        else
        {
            rows = new String[] {"..rrr...rrr..", ".rrrrr.rrrrr.", "rrrrrrrrrrrrr", "rrrrrrrrrrrrr", "rrrrrrrrrrrrr", ".rrrrrrrrrrr.", "..rrrrrrrrr..", "...rrrrrrr...", "....rrrrr....", ".....rrr.....", "......r......"};
            x = 6;
            y = 6;
        }
        l = L();
        l.stamp(x, y, rows);
        shade(l, 'r', 'R', 'v');
        P(g, l);
        g.pxs('w', x + 2, y + 1, x + 1, y + 2);
        if (st == 2 && f == 1) g.pxs('R', 3, 7, 21, 7, 2, 11, 22, 11);
    }

    private static void sailing(Grid g, int st, int f)
    {
        int dy = f == 0 ? 0 : -1;
        if (st >= 1)
        {
            Grid l = L();
            l.stamp(4, 16 + dy, "nnnnnnnnnnnnnnnn", ".nnnnnnnnnnnnnn.", "..nnnnnnnnnnnn..", "...nnnnnnnnnn...", "....nnnnnnnn....");
            rc(l, 12, 3 + dy, 1, 14, 'n');
            shade(l, 'n', 'N', 'm');
            P(g, l);
            g.pxs('r', 12, 16 + dy, 5, 16 + dy, 6, 16 + dy, 18, 16 + dy, 19, 16 + dy);
            if (st == 2)
            {
                l = L();
                for (int y = 4; y <= 14; y++)
                {
                    int w = 1 + (int) Math.round((y - 4) * 0.8);
                    rc(l, 14, y + dy, w, 1, 'x');
                }
                for (int y = 7; y <= 14; y++)
                {
                    int w = 1 + (int) Math.round((y - 7) * 0.55);
                    rc(l, 12 - w, y + dy, w, 1, 'x');
                }
                shade(l, 'x', 'w', 'X');
                P(g, l);
                l = L();
                l.stamp(13, 1 + dy, "rrr", ".rr");
                P(g, l);
                g.pxs('R', 13, 1 + dy);
            }
        }
        int shift = f * 3;
        Grid w = L();
        if (st == 0) rc(w, 4, 21, 16, 3, 'b');
        else rc(w, 1, 21, 22, 3, 'b');
        for (int x = 1; x < 23; x++)
        {
            if ((x + shift) % 6 < 3 && (st > 0 || (x >= 4 && x < 20))) w.set(x, 20, 'b');
        }
        shade(w, 'b', 'B', 'd');
        P(g, w);
        for (int x = 1; x < 23; x++)
        {
            if ((x + shift) % 6 == 1 && w.px[20][x] != 0) g.set(x, 20, 'w');
        }
        g.pxs('B', 3 + shift % 5, 22, 12 - shift / 3, 22, 17 + shift / 3, 23);
    }
}
