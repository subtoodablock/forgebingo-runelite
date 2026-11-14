package com.forgebingo.core;

import com.forgebingo.core.PixelArt.Grid;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pixel-art creatures for the Barrows brothers and the Inferno, plus the bones and embers that fall down the panel.
 * All original, chunky chibi art in the style of ImpSprite: big head, 1px dark outline, 2-3 shades per colour, two walk
 * frames. Drawn on the shared 42 x 34 grid, facing LEFT, feet on the bottom rows, body centred on column 21.
 */
public final class CryptSprites
{
    private static final Map<String, BufferedImage> CACHE = new ConcurrentHashMap<>();

    private CryptSprites()
    {
    }

    /** One walk frame (0 or 1); cached; null for critters this class does not draw. */
    public static BufferedImage image(Critter c, int variant, int frame)
    {
        if (c == null) return null;
        switch (c)
        {
            case AHRIM: case DHAROK: case GUTHAN: case KARIL: case TORAG: case VERAC:
            case ZUK: case JAD: case MELEER: case NIBBLER:
            case MUTTADILE: case TEKTON: case VESPULA: case VANGUARD:
                break;
            default:
                return null;
        }
        int f = frame == 0 ? 0 : 1;
        int v = c == Critter.VANGUARD ? Math.floorMod(variant, 3) : 0;
        return CACHE.computeIfAbsent(c + "|" + v + "|" + f, k -> PixelArt.render(build(c, v, f), CryptSprites::colour));
    }

    /** A small falling thing (16 x 16); cached; null for ones not drawn here. */
    public static BufferedImage fall(Critter.Fall fall, int variant)
    {
        if (fall != Critter.Fall.BONE && fall != Critter.Fall.EMBER) return null;
        int v = Math.floorMod(variant, 2);
        return CACHE.computeIfAbsent("fall|" + fall + "|" + v, k -> PixelArt.render(buildFall(fall, v), CryptSprites::colour));
    }

    private static int colour(int c)
    {
        switch ((char) c)
        {
            case 'k': return 0x16140f; // outline
            case 'z': return 0x1d1a22; // darkest (faces, mouths)
            case 'b': return 0xdcd2b2; // bone
            case 'B': return 0xa89d7c;
            case 'x': return 0xfff2c8; // hot white
            case 'r': return 0x8a5a36; // dark bronze
            case 'R': return 0x4e301c;
            case 'j': return 0xb88252;
            case 'e': return 0xd6ecff; // faint pale ghost glow
            case 'E': return 0x7f97c4;
            case 'g': return 0x8d8f94; // dull steel
            case 'G': return 0xb9bbc0;
            case 'D': return 0x4c4e55;
            case 'c': return 0x4a4a52; // charcoal
            case 'C': return 0x74747e;
            case 'u': return 0x2b2b32;
            case 'n': return 0x5b5870; // Ahrim's dusty robe
            case 'm': return 0x8582a0;
            case 'N': return 0x38364a;
            case 'l': return 0x6e5a44; // leather
            case 'L': return 0x41352a;
            case 's': return 0xc2c9cf; // bright steel
            case 'S': return 0x727b84;
            case 'd': return 0x474e55;
            case 'w': return 0x8a6538; // wood
            case 'W': return 0x57401f;
            case 'q': return 0x8f8774; // Guthan's dull bronze-steel
            case 'Q': return 0xbdb49a;
            case 'V': return 0x57503f;
            case 'o': return 0x2d2536; // obsidian
            case 'O': return 0x4a3d5a;
            case 'p': return 0x7a6a92;
            case 'a': return 0xff8a1e; // lava
            case 'A': return 0xffd04a;
            case 'f': return 0xe03a12;
            case 'F': return 0x5e2a1e;
            case 'h': return 0x93503a; // hot rock
            case 'H': return 0xc27a48;
            case 'K': return 0x8a1f3e; // Jad's maroon
            case '1': return 0xb02c4c;
            case '2': return 0x56102c;
            case '3': return 0xc23440;
            case '4': return 0x2a1030;
            case '5': return 0x6a3a80;
            case 'y': return 0x6f9a5a; // Muttadile green
            case 'Y': return 0x9bc47a;
            case 'Z': return 0x3f6a3a;
            case 'M': return 0xc42a22; // Vespula red
            case 'P': return 0x6a1010;
            case 'T': return 0xc8e03a; // lime glow
            case 't': return 0x7c9418;
            case 'X': return 0x6a3030; // Zuk's dark red-brown flesh
            case 'U': return 0x40191c;
            case 'i': return 0x4a8cff; // mage blue
            case 'I': return 0x9fd0ff;
            case 'J': return 0x2a4ea8;
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

    /** A widening (or narrowing) block: half width hw0 on row y0 to hw1 on row y1, x from cx - hw to cx + hw - 1. */
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

    private static Grid row(Grid m, int y, char c)
    {
        for (int i = 0; i < m.w; i++)
        {
            if (m.has(i, y)) m.px[y][i] = c;
        }
        return m;
    }

    /** Shifts every row from fromY down one pixel to the right when f is 1: a hem swaying as the feet move. */
    private static Grid sway(Grid m, int fromY, int f)
    {
        if (f == 0) return m;
        for (int y = fromY; y < m.h; y++)
        {
            for (int x = m.w - 1; x > 0; x--) m.px[y][x] = m.px[y][x - 1];
            m.px[y][0] = 0;
        }
        return m;
    }

    private static void line(Grid m, int x0, int y0, int x1, int y1, char a, char b)
    {
        int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= n; i++)
        {
            int x = (int) Math.round(x0 + (x1 - x0) * (double) i / n);
            int y = (int) Math.round(y0 + (y1 - y0) * (double) i / n);
            m.set(x, y, i % 4 == 3 ? b : a);
        }
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

    private static Grid shaft(int x, int y0, int y1, char a, char b)
    {
        Grid m = new Grid();
        for (int y = y0; y <= y1; y++) m.set(x, y, y % 4 == 0 ? b : a);
        return m;
    }

    /**
     * Two legs. In frame 1 the left one lifts and shifts right, the right one shifts left (up to 2 px when there is room).
     * Legs end on row 32, the lifted one on row 31.
     */
    private static void legs(Grid g, int xl, int xr, int w, int top, char c, char light, char dark, int f)
    {
        int s = Math.max(1, Math.min(2, (xr - xl - w) / 2)) * f;
        part(g, bevel(rect(xl + s, top, w, 33 - top - f, c), light, dark));
        part(g, bevel(rect(xr - s, top, w, 33 - top, c), light, dark));
    }

    private static void orb(Grid g, int cx, int cy)
    {
        Grid o = oval(cx - 3, cy - 3, 7, 7, 'E');
        with(o, oval(cx - 2, cy - 2, 5, 5, 'e'));
        o.set(cx - 1, cy - 1, 'x');
        o.set(cx, cy - 1, 'x');
        part(g, o);
    }

    // ---------------------------------------------------------------- dispatch

    private static Grid build(Critter c, int v, int f)
    {
        switch (c)
        {
            case MUTTADILE: return muttadile(f);
            case TEKTON: return tekton(f);
            case VESPULA: return vespula(f);
            case VANGUARD: return vanguard(v, f);
            case AHRIM: return ahrim(f);
            case DHAROK: return dharok(f);
            case GUTHAN: return guthan(f);
            case KARIL: return karil(f);
            case TORAG: return torag(f);
            case VERAC: return verac(f);
            case ZUK: return zuk(f);
            case JAD: return jad(f);
            case MELEER: return meleer(f);
            default: return nibbler(f);
        }
    }

    // ---------------------------------------------------------------- Barrows

    /** Ahrim the Blighted: hooded mage in dusty blue-grey robes, staff with a glowing orb. */
    private static Grid ahrim(int f)
    {
        Grid g = new Grid();
        int dy = -f;
        part(g, shaft(10, 10 + dy, 31 + dy, 'w', 'W'));
        orb(g, 10, 6 + dy);
        legs(g, 16, 25, 3, 28, 'c', (char) 0, 'z', f);
        Grid sk = bevel(trap(21, 22, 30, 6, 8, 'n'), 'm', 'N');
        row(sk, 29, 'r');
        row(sk, 30, 'R');
        sway(sk, 27, f);
        part(g, sk);
        g.pxs('N', 18, 24, 18, 26, 23, 24, 23, 26, 26 + f, 28);
        Grid torso = bevel(rect(16, 15, 11, 8, 'n'), 'm', 'N');
        row(torso, 21, 'l');
        part(g, torso);
        g.pxs('B', 20, 21);
        part(g, bevel(trap(24, 0, 3, 1, 3, 'n'), 'm', 'N'));
        part(g, bevel(oval(14, 3, 14, 13, 'n'), 'm', 'N'));
        g.pxs('m', 22, 3, 23, 3);
        part(g, oval(15, 7, 9, 7, 'z'));
        g.pxs('e', 17, 9, 21, 9).pxs('E', 17, 10, 21, 10);
        part(g, bevel(rect(13, 17 + dy, 4, 5, 'n'), 'm', 'N'));
        part(g, rect(9, 20 + dy, 3, 3, 'b'));
        return g;
    }

    /** Dharok the Wretched: bulky dark plate, thick horns, a giant greataxe resting on his shoulder. */
    private static Grid dharok(int f)
    {
        Grid g = new Grid();
        int dy = -f;
        legs(g, 15, 23, 5, 27, 'u', 'c', 'z', f);
        part(g, bevel(trap(21, 24, 30, 7, 9, 'r'), 'j', 'R'));
        part(g, bevel(rect(14, 16, 15, 10, 'c'), 'C', 'u'));
        for (int x = 15; x <= 28; x++) g.set(x, 24, 'R');
        g.pxs('B', 21, 24, 22, 24);
        part(g, bevel(oval(11, 14, 7, 7, 'r'), 'j', 'R'));
        part(g, bevel(oval(25, 14, 7, 7, 'r'), 'j', 'R'));
        // the greataxe: diagonal shaft from the fist, over the shoulder, huge double blade up behind the head
        Grid axe = new Grid();
        line(axe, 7, 27 + dy, 32, 11 + dy, 'w', 'W');
        double dx = 0.852;
        double dyy = -0.525;
        double px = 0.525;
        double py = 0.852;
        for (int y = 0; y < 34; y++)
        {
            for (int x = 0; x < 42; x++)
            {
                double ux = x + 0.5 - 32;
                double uy = y + 0.5 - (11 + dy);
                double u = ux * dx + uy * dyy;
                double v = ux * px + uy * py;
                if (Math.abs(v) > 8.2) continue;
                double hw = 1.5 + 3.9 * Math.pow(Math.abs(v) / 8.2, 1.3);
                if (Math.abs(u) <= hw) axe.set(x, y, Math.abs(v) > 6.8 ? 'x' : (u < 0 ? 's' : 'S'));
            }
        }
        part(g, axe);
        Grid horns = new Grid();
        horns.stamp(9, 5, "CC.....", "CCC....", "CccC...", ".CccC..", ".CcccC.", "..CcccC", "..CCCCC");
        part(g, horns);
        Grid hornR = new Grid();
        hornR.stamp(26, 5, ".....CC", "....CCC", "...CccC", "..CccC.", ".CcccC.", "CcccC..", "CCCCC..");
        part(g, hornR);
        part(g, bevel(oval(14, 5, 15, 12, 'c'), 'C', 'u'));
        g.pxs('r', 20, 6, 21, 6, 22, 6);
        part(g, rect(15, 11, 11, 3, 'z'));
        g.pxs('e', 17, 12, 18, 12, 22, 12, 23, 12);
        part(g, bevel(rect(11, 20 + dy, 4, 4, 'c'), 'C', 'u'));
        part(g, rect(8, 21 + dy, 4, 4, 'b'));
        return g;
    }

    /** Guthan the Infested: dull bronze-steel plate, flat-topped helm with a wide flat brim, a broad warspear. */
    private static Grid guthan(int f)
    {
        Grid g = new Grid();
        int dy = -f;
        Grid spear = shaft(9, 7 + dy, 31 + dy, 'w', 'W');
        spear.stamp(6, 0 + dy,
            "...x...",
            "..xsS..",
            ".xssSS.",
            "xsssSSS",
            "xsssSSS",
            ".xssSS.",
            "..xsS..",
            "...w...");
        part(g, spear);
        legs(g, 16, 25, 4, 27, 'V', (char) 0, 'z', f);
        Grid sk = bevel(trap(21, 23, 30, 6, 7, 'q'), 'Q', 'V');
        sway(sk, 27, f);
        part(g, sk);
        g.pxs('V', 16, 27, 19 + f, 28, 22 + f, 28, 25 + f, 27);
        Grid torso = bevel(rect(16, 15, 11, 9, 'q'), 'Q', 'V');
        row(torso, 22, 'V');
        part(g, torso);
        part(g, oval(16, 11, 10, 7, 'z'));
        g.pxs('e', 18, 14, 22, 14).pxs('E', 18, 15, 22, 15);
        g.pxs('V', 21, 12, 21, 13);
        Grid helm = rect(15, 5, 13, 6, 'q');
        helm.px[5][15] = 0;
        helm.px[5][27] = 0;
        bevel(helm, 'Q', 'V');
        for (int y = 6; y <= 9; y++) helm.set(21, y, 'V');
        part(g, helm);
        Grid brim = rect(10, 10, 23, 2, 'q');
        bevel(brim, 'Q', 'V');
        part(g, brim);
        part(g, bevel(rect(14, 12, 3, 5, 'q'), 'Q', 'V'));
        part(g, bevel(rect(26, 12, 3, 5, 'q'), 'Q', 'V'));
        part(g, bevel(rect(18, 17, 6, 2, 'q'), 'Q', 'V'));
        part(g, bevel(rect(12, 17 + dy, 5, 4, 'q'), 'Q', 'V'));
        part(g, rect(8, 19 + dy, 3, 3, 'b'));
        return g;
    }

    /** Karil the Tainted: slim ranger in a dark leather coif and skirt, a clear crossbow, a quiver. */
    private static Grid karil(int f)
    {
        Grid g = new Grid();
        int dy = -f;
        Grid quiver = rect(27, 14, 3, 9, 'w');
        quiver.stamp(27, 13, "b.b");
        bevel(quiver, 'l', 'W');
        part(g, quiver);
        legs(g, 17, 25, 3, 28, 'L', (char) 0, 'z', f);
        Grid sk = bevel(trap(21, 23, 30, 5, 7, 'l'), 'j', 'L');
        sway(sk, 27, f);
        part(g, sk);
        g.pxs('L', 17, 26, 20, 27 + 0, 23 + f, 26, 25 + f, 27);
        Grid torso = bevel(rect(17, 15, 9, 8, 'l'), 'j', 'L');
        row(torso, 21, 'L');
        part(g, torso);
        g.pxs('B', 21, 21).pxs('r', 18, 17, 19, 18, 20, 19, 24, 17);
        part(g, bevel(oval(15, 3, 12, 12, 'L'), 'l', 'z'));
        part(g, oval(15, 8, 8, 6, 'z'));
        g.pxs('e', 17, 10, 21, 10).pxs('E', 17, 11, 21, 11);
        // crossbow: level stock, curved prod across the front, string, loaded bolt
        Grid bow = new Grid();
        int[] arc = {7, 6, 5, 4, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 5, 6, 7};
        for (int i = 0; i < arc.length; i++)
        {
            int y = 12 + i + dy;
            bow.set(arc[i], y, 'W');
            bow.set(arc[i] + 1, y, 'w');
        }
        for (int y = 13; y <= 27; y++) bow.set(8, y + dy, 'b');
        with(bow, bevel(rect(3, 20 + dy, 13, 3, 'w'), 'j', 'W'));
        with(bow, rect(1, 19 + dy, 10, 1, 'S'));
        bow.set(0, 19 + dy, 'x');
        bow.set(1, 19 + dy, 's');
        part(g, bow);
        g.pxs('x', 1, 19 + dy);
        part(g, bevel(rect(14, 17 + dy, 4, 3, 'l'), 'j', 'L'));
        part(g, rect(12, 21 + dy, 4, 3, 'b'));
        return g;
    }

    /** Torag the Corrupted: short and stocky, bronze dome helm with ear flares, a small hammer in each fist. */
    private static Grid torag(int f)
    {
        Grid g = new Grid();
        int dy = -f;
        int dy2 = f - 1;
        // offhand hammer behind
        Grid h2 = shaft(32, 14 + dy2, 24 + dy2, 'w', 'W');
        with(h2, bevel(rect(29, 9 + dy2, 7, 5, 'S'), 's', 'd'));
        part(g, h2);
        legs(g, 15, 23, 5, 25, 'L', 'l', 'z', f);
        part(g, bevel(rect(14, 16, 15, 9, 'r'), 'j', 'R'));
        for (int x = 15; x <= 28; x++) g.set(x, 23, 'L');
        g.pxs('B', 21, 23, 22, 23);
        part(g, bevel(rect(28, 17 + dy2, 4, 4, 'r'), 'j', 'R'));
        part(g, rect(30, 20 + dy2, 3, 3, 'b'));
        part(g, bevel(oval(12, 8, 5, 6, 'r'), 'j', 'R'));
        part(g, bevel(oval(26, 8, 5, 6, 'r'), 'j', 'R'));
        part(g, bevel(oval(14, 5, 14, 13, 'j'), 'x', 'r'));
        part(g, rect(15, 10, 10, 2, 'z'));
        g.pxs('e', 17, 10, 18, 10, 22, 10, 23, 10);
        g.pxs('R', 20, 12, 20, 13, 20, 14);
        // front hammer
        Grid h1 = shaft(9, 14 + dy, 24 + dy, 'w', 'W');
        with(h1, bevel(rect(5, 9 + dy, 7, 5, 'S'), 's', 'd'));
        part(g, h1);
        part(g, bevel(rect(11, 19 + dy, 4, 3, 'r'), 'j', 'R'));
        part(g, rect(8, 20 + dy, 3, 3, 'b'));
        return g;
    }

    /** Verac the Defiled: tall spiked helm, brown brassard, plate skirt and a spiked flail. */
    private static Grid verac(int f)
    {
        Grid g = new Grid();
        int dy = -f;
        legs(g, 17, 25, 3, 28, 'u', (char) 0, 'z', f);
        Grid skirt = bevel(trap(21, 21, 30, 6, 8, 'g'), 'G', 'D');
        row(skirt, 24, 'D');
        row(skirt, 27, 'D');
        sway(skirt, 26, f);
        part(g, skirt);
        Grid torso = bevel(rect(16, 14, 11, 8, 'l'), 'j', 'L');
        row(torso, 20, 'L');
        part(g, torso);
        g.pxs('B', 21, 20);
        Grid helm = bevel(trap(21, 1, 13, 1, 7, 'c'), 'C', 'u');
        part(g, helm);
        g.pxs('x', 20, 2, 21, 2);
        g.pxs('C', 21, 4, 21, 5, 21, 6);
        part(g, bevel(rect(13, 10, 3, 4, 'c'), 'C', 'u'));
        part(g, bevel(rect(27, 10, 3, 4, 'c'), 'C', 'u'));
        part(g, rect(15, 8, 9, 5, 'z'));
        g.pxs('e', 17, 10, 21, 10).pxs('E', 17, 11, 21, 11);
        // flail
        Grid fl = new Grid();
        fl.stamp(10, 20 + dy, "w", "w", "w", "w", "w", "B");
        part(g, fl);
        g.pxs('S', 10, 18 + dy, 9, 17 + dy, 9, 16 + dy, 8, 15 + dy);
        Grid ball = oval(3, 8 + dy, 7, 7, 'S');
        bevel(ball, 's', 'd');
        ball.pxs('s', 6, 7 + dy, 2, 11 + dy, 10, 11 + dy, 3, 8 + dy, 9, 8 + dy, 3, 14 + dy, 9, 14 + dy, 6, 15 + dy);
        part(g, ball);
        part(g, bevel(rect(12, 17 + dy, 5, 4, 'l'), 'j', 'L'));
        part(g, rect(9, 20 + dy, 3, 3, 'b'));
        return g;
    }

    // ---------------------------------------------------------------- Inferno

    /** TzKal-Zuk: hulking top-heavy brute: huge rocky shoulders, plated helm with one red eye, clawed gauntlets, sunk in lava. */
    private static Grid zuk(int f)
    {
        Grid g = new Grid();
        int a1 = f;
        int a2 = 1 - f;
        // dark red-brown body with horizontal bands
        Grid body = trap(21, 11, 31, 12, 8, 'X');
        for (int y = 17; y <= 29; y += 3) row(body, y, 'U');
        part(g, body);
        // lava he stands in
        Grid lava = bevel(oval(3, 28, 36, 6, 'a'), 'A', 'f');
        part(g, lava);
        g.pxs('A', 9 + 2 * f, 30, 17 - 2 * f, 31, 25 + 2 * f, 30, 32 - 2 * f, 31, 13, 32, 29, 32);
        // huge rocky shoulders
        part(g, bevel(oval(1, 7, 17, 13, 'c'), 'C', 'u'));
        part(g, bevel(oval(24, 7, 17, 13, 'c'), 'C', 'u'));
        g.pxs('u', 5, 11, 6, 12, 7, 13, 4, 15, 5, 16, 35, 11, 34, 12, 33, 13, 37, 15, 36, 16);
        // clawed gauntlets held out
        Grid lf = bevel(oval(0, 19 + a1, 11, 9, 'c'), 'C', 'u');
        lf.stamp(1, 28 + a1, "u.u.u");
        lf.stamp(-1 + 1, 24 + a1, "u");
        part(g, lf);
        Grid rf = bevel(oval(31, 19 + a2, 11, 9, 'c'), 'C', 'u');
        rf.stamp(35, 28 + a2, "u.u.u");
        part(g, rf);
        g.pxs('u', 3, 22 + a1, 4, 23 + a1, 37, 22 + a2, 38, 23 + a2);
        // plated helmet head
        part(g, bevel(oval(9, 1, 7, 14, 'C'), (char) 0, 'c'));
        part(g, bevel(oval(26, 1, 7, 14, 'C'), (char) 0, 'c'));
        part(g, bevel(oval(13, 0, 16, 16, 'c'), 'C', 'u'));
        g.pxs('u', 21, 1, 21, 2, 21, 3, 17, 3, 25, 3);
        part(g, oval(17, 5, 8, 10, 'z'));
        part(g, rect(19, 8, 4, 2, 'f'));
        g.pxs('a', 20, 8, 21, 8).pxs('x', 20, 9);
        return g;
    }

    private static Grid jadSpike(int x, int y)
    {
        Grid m = new Grid();
        m.stamp(x, y, ".5.", ".44", "444");
        return m;
    }

    /** One pillar leg: thick maroon column flaring into a red hoof-like foot with dark claws. */
    private static void jadLeg(Grid g, int cx, int lift)
    {
        Grid leg = bevel(rect(cx - 3, 17, 7, 8 - lift, 'K'), '1', '2');
        part(g, leg);
        Grid foot = bevel(trap(cx + 1, 24 - lift, 31 - lift, 4, 6, '3'), '1', '2');
        foot.stamp(cx - 5, 32 - lift, "444.444.444", ".4...4...4.");
        part(g, foot);
    }

    /** JalTok-Jad: a massive hunched maroon beast on pillar legs, spiked hump, low head with a fanged open jaw. */
    private static Grid jad(int f)
    {
        Grid g = new Grid();
        int dy = f;
        int liftA = f;
        int liftB = 1 - f;
        int[][] sp = {{12, 5}, {17, 1}, {22, -1}, {27, -1}, {32, 1}, {36, 5}, {38, 10}};
        for (int[] s : sp) part(g, jadSpike(s[0], s[1] + dy + 1));
        jadLeg(g, 33, liftA);
        jadLeg(g, 20, liftB);
        part(g, bevel(oval(9, 3 + dy, 31, 18, 'K'), '1', '2'));
        // rows of spikes across the hump and on the knee
        for (int[] s : new int[][] {{16, 8}, {22, 6}, {28, 6}, {33, 9}}) part(g, jadSpike(s[0], s[1] + dy));
        part(g, jadSpike(14, 16 + dy));
        g.pxs('a', 20, 13 + dy, 21, 14 + dy, 22, 15 + dy, 29, 12 + dy, 30, 13 + dy, 31, 14 + dy, 25, 19 + dy);
        part(g, jadSpike(14, 15 + dy));
        jadLeg(g, 8, liftA);
        // head low at the front of the hump
        part(g, bevel(oval(1, 9 + dy, 17, 14, 'K'), '1', '2'));
        part(g, jadSpike(7, 6 + dy));
        part(g, jadSpike(12, 5 + dy));
        // heavy brow and slanted glowing eye
        g.pxs('2', 3, 11 + dy, 4, 11 + dy, 5, 11 + dy, 6, 11 + dy, 7, 11 + dy, 8, 12 + dy, 9, 12 + dy);
        g.pxs('a', 3, 13 + dy, 4, 13 + dy, 5, 12 + dy, 6, 12 + dy, 7, 12 + dy).pxs('A', 5, 13 + dy, 6, 13 + dy);
        // open jaw full of teeth
        Grid mouth = oval(1, 16 + dy, 14, 8, '4');
        mouth.pxs('x', 3, 17 + dy, 5, 17 + dy, 7, 17 + dy, 9, 17 + dy, 11, 17 + dy, 13, 17 + dy);
        mouth.pxs('x', 3, 18 + dy, 7, 18 + dy, 11, 18 + dy);
        mouth.pxs('x', 4, 22 + dy, 6, 22 + dy, 8, 22 + dy, 10, 22 + dy, 12, 22 + dy);
        mouth.pxs('x', 4, 21 + dy, 8, 21 + dy, 12, 21 + dy);
        mouth.pxs('f', 6, 19 + dy, 7, 19 + dy, 8, 20 + dy, 9, 20 + dy, 10, 19 + dy);
        part(g, mouth);
        return g;
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

    // ---------------------------------------------------------------- Chambers of Xeric bosses

    /** Muttadile: a chubby little green crocodile-dog with a smile, big friendly eye, stubby legs and a rocky shell. */
    private static Grid muttadile(int f)
    {
        Grid g = new Grid();
        // short pointed tail that wags
        Grid tail = bevel(oval(31, 20, 6, 6, 'y'), 'Y', 'Z');
        tail.stamp(36, 19 + 2 * f, ".yy", "yy.", "y..");
        part(g, tail);
        int[] xs = {13, 28, 7, 22};
        for (int i = 0; i < 4; i++)
        {
            boolean lifted = (i % 2 == 0) == (f == 1);
            int lift = lifted ? 1 : 0;
            part(g, bevel(rect(xs[i], 26 - lift, 5, 7 - lift, i < 2 ? 'Z' : 'y'), (char) 0, 'z'));
        }
        part(g, bevel(oval(10, 13, 24, 16, 'y'), 'Y', 'Z'));
        part(g, bevel(oval(21, 5, 9, 8, 'c'), 'C', 'u'));
        part(g, bevel(oval(12, 7, 9, 8, 'c'), 'C', 'u'));
        part(g, bevel(oval(27, 9, 7, 7, 'c'), 'C', 'u'));
        part(g, bevel(oval(15, 21, 12, 7, 'b'), (char) 0, 'B'));
        // round head with a longer snout
        part(g, bevel(oval(4, 10, 14, 14, 'y'), 'Y', 'Z'));
        part(g, bevel(oval(0, 16, 12, 8, 'y'), 'Y', 'Z'));
        part(g, bevel(oval(1, 21, 10, 4, 'b'), (char) 0, 'B'));
        g.pxs('z', 3, 23, 4, 24, 5, 24, 6, 24, 7, 23).pxs('x', 3, 21, 6, 21);
        g.pxs('Z', 1, 18, 2, 18);
        part(g, oval(9, 12, 6, 6, 'z'));
        g.pxs('x', 10, 13, 11, 13, 10, 14).pxs('e', 13, 16);
        g.pxs('j', 8, 20, 9, 20);
        return g;
    }

    /** Tekton: dark stone-armoured knight with molten seams, a tall curved horn, a stone fist and a big hammer fist. */
    private static Grid tekton(int f)
    {
        Grid g = new Grid();
        int dy = -f;
        int s = 2 * f;
        legs(g, 11, 25, 6, 22, 'c', 'C', 'u', f);
        g.pxs('a', 13 + s, 26, 13 + s, 27, 27 - s, 26, 27 - s, 27);
        part(g, bevel(rect(12, 19, 18, 5, 'c'), 'C', 'u'));
        part(g, bevel(rect(13, 10, 16, 11, 'c'), 'C', 'u'));
        for (int y = 15; y <= 19; y++) g.pxs('a', 18, y, 20, y, 22, y, 24, y);
        g.pxs('A', 20, 17, 22, 17, 18, 16, 24, 16);
        g.pxs('a', 19, 12, 20, 13, 21, 14, 22, 13, 23, 12);
        part(g, bevel(oval(8, 9, 9, 8, 'c'), 'C', 'u'));
        part(g, bevel(oval(26, 9, 9, 8, 'c'), 'C', 'u'));
        g.pxs('a', 10, 12, 31, 12);
        Grid horn = new Grid();
        horn.pxs('r', 23, 4, 24, 3, 24, 4, 25, 2, 25, 3, 26, 1, 26, 2, 27, 0, 26, 0);
        part(g, horn);
        part(g, bevel(oval(16, 3, 10, 10, 'c'), 'C', 'u'));
        part(g, rect(19, 5, 4, 6, 'z'));
        g.pxs('a', 20, 5, 21, 5, 20, 6, 21, 6, 20, 7, 21, 7, 20, 8, 21, 8, 20, 9, 21, 9, 20, 10);
        g.pxs('A', 20, 6, 20, 8);
        // plain stone fist on the far side
        part(g, bevel(rect(30, 13, 4, 8, 'c'), 'C', 'u'));
        part(g, bevel(oval(30, 20, 5, 5, 'c'), 'C', 'u'));
        // the hammer arm: a blocky molten-faced hammer head carried low and forward
        part(g, bevel(rect(8, 14 + dy, 5, 7, 'c'), 'C', 'u'));
        Grid hm = bevel(rect(2, 19 + dy, 11, 10, 'c'), 'C', 'u');
        with(hm, rect(2, 20 + dy, 3, 8, 'a'));
        with(hm, rect(2, 22 + dy, 1, 4, 'A'));
        for (int y = 20; y <= 27; y += 7) row(hm, y + dy, 'C');
        part(g, hm);
        g.pxs('a', 7, 22 + dy, 7, 23 + dy, 10, 24 + dy, 10, 25 + dy);
        return g;
    }

    private static Grid shift(Grid m, int dx)
    {
        Grid out = new Grid();
        for (int y = 0; y < m.h; y++)
        {
            for (int x = 0; x < m.w; x++)
            {
                if (m.px[y][x] != 0) out.set(x + dx, y, m.px[y][x]);
            }
        }
        return out;
    }

    private static void wing(Grid g, double bx, double by, double len, double thick, double deg, char c, char l, char d)
    {
        double r = Math.toRadians(deg);
        part(g, bevel(ellipseRot(bx + Math.cos(r) * len, by + Math.sin(r) * len, len, thick, deg, c), l, d));
    }

    /** Vespula: big red wasp, two pairs of wings that beat up and down, lime glow, proboscis, dangling legs. */
    private static Grid vespula(int f)
    {
        Grid g = new Grid();
        int dy = f;
        Grid legs = new Grid();
        line(legs, 16, 22 + dy, 13, 28, 'u', 'u');
        line(legs, 19, 23 + dy, 19, 28, 'u', 'u');
        line(legs, 23, 22 + dy, 27, 28, 'u', 'u');
        legs.pxs('u', 12, 28, 18, 28, 28, 28);
        part(g, legs);
        // front pair of wings (towards the head), behind the body
        if (f == 0)
        {
            wing2(g, 17, 12 + dy, 6.5, 2.2, -138);
            wing2(g, 17, 12 + dy, 9, 2.7, -116);
        }
        else
        {
            wing2(g, 17, 15 + dy, 6.5, 2.2, 140);
            wing2(g, 17, 15 + dy, 8.5, 2.7, 114);
        }
        part(g, bevel(ellipseRot(27, 19 + dy, 9, 6, 25, 'M'), 'f', 'P'));
        g.pxs('T', 24, 16 + dy, 25, 16 + dy, 27, 18 + dy, 28, 18 + dy, 30, 20 + dy, 31, 20 + dy, 25, 21 + dy, 26, 21 + dy, 29, 23 + dy);
        g.pxs('t', 24, 17 + dy, 27, 19 + dy, 30, 21 + dy, 25, 22 + dy);
        part(g, bevel(oval(13, 12 + dy, 13, 10, 'M'), 'f', 'P'));
        part(g, bevel(oval(15, 12 + dy, 8, 4, 'T'), (char) 0, 't'));
        part(g, bevel(oval(6, 14 + dy, 9, 8, 'M'), 'f', 'P'));
        part(g, rect(7, 16 + dy, 3, 3, 'T'));
        g.pxs('x', 7, 16 + dy);
        Grid pro = new Grid();
        line(pro, 7, 20 + dy, 2, 22 + dy, 'u', 'u');
        pro.pxs('u', 1, 21 + dy, 0, 20 + dy, 1, 24 + dy, 0, 25 + dy, 1, 22 + dy);
        part(g, pro);
        // rear pair of wings (up and back), over the body
        if (f == 0)
        {
            wing2(g, 22, 12 + dy, 9, 2.7, -62);
            wing2(g, 22, 12 + dy, 6.5, 2.2, -40);
        }
        else
        {
            wing2(g, 22, 15 + dy, 9, 2.7, 62);
            wing2(g, 22, 15 + dy, 6.5, 2.2, 36);
        }
        return shift(g, 2);
    }

    /** A wing: lighter translucent-looking membrane with a darker leading edge. */
    private static void wing2(Grid g, double bx, double by, double len, double thick, double deg)
    {
        double r = Math.toRadians(deg);
        double cx = bx + Math.cos(r) * len;
        double cy = by + Math.sin(r) * len;
        double cs = Math.cos(r);
        double sn = Math.sin(r);
        Grid m = new Grid();
        boolean up = deg < 0;
        for (int y = 0; y < m.h; y++)
        {
            for (int x = 0; x < m.w; x++)
            {
                double dx = x + 0.5 - cx;
                double dy = y + 0.5 - cy;
                double u = dx * cs + dy * sn;
                double v = -dx * sn + dy * cs;
                if (u * u / (len * len) + v * v / (thick * thick) > 1) continue;
                boolean lead = up ? v < -thick * 0.45 : v > thick * 0.45;
                boolean inner = up ? v > thick * 0.1 : v < -thick * 0.1;
                m.set(x, y, lead ? 'P' : (inner ? 'a' : 'f'));
            }
        }
        part(g, m);
    }

    /** A smooth 2 px thick cubic curve; thins to 1 px near the end, accent colour on the tip, dark claw at the very end. */
    private static void tentacle(Grid m, double[] p, char body, char tip, char claw)
    {
        int n = 90;
        for (int i = 0; i <= n; i++)
        {
            double t = i / (double) n;
            double u = 1 - t;
            double x = u * u * u * p[0] + 3 * u * u * t * p[2] + 3 * u * t * t * p[4] + t * t * t * p[6];
            double y = u * u * u * p[1] + 3 * u * u * t * p[3] + 3 * u * t * t * p[5] + t * t * t * p[7];
            char c = t > 0.92 ? claw : (t > 0.7 ? tip : body);
            int xi = (int) Math.round(x);
            int yi = (int) Math.round(y);
            m.set(xi, yi, c);
            if (t < 0.85)
            {
                m.set(xi + 1, yi, c);
                m.set(xi, yi + 1, c);
                m.set(xi + 1, yi + 1, c);
            }
        }
    }

    private static void ring(Grid m, double cx, double cy, double rx, double ry, boolean front, char c)
    {
        for (int i = 0; i < 400; i++)
        {
            double a = Math.PI * 2 * i / 400;
            boolean isFront = Math.sin(a) >= 0;
            if (isFront != front) continue;
            int x = (int) Math.round(cx + Math.cos(a) * rx);
            int y = (int) Math.round(cy + Math.sin(a) * ry);
            m.set(x, y, c);
            if (Math.abs(Math.sin(a)) > 0.5) m.set(x, y + 1, c);
            else m.set(x + 1, y, c);
        }
    }

    /** A broad flat pointed tentacle: wide at the root, tapering to a point, accent edge and a darker midrib. */
    private static Grid petal(double rx, double ry, double deg, double len, double wd, char edge)
    {
        Grid m = new Grid();
        double r = Math.toRadians(deg);
        double dx = Math.cos(r);
        double dy = Math.sin(r);
        for (int y = 0; y < m.h; y++)
        {
            for (int x = 0; x < m.w; x++)
            {
                double ux = x + 0.5 - rx;
                double uy = y + 0.5 - ry;
                double t = (ux * dx + uy * dy) / len;
                if (t < -0.03 || t > 1) continue;
                double p = Math.abs(-ux * dy + uy * dx);
                double hw = wd / 2 * Math.pow(Math.max(0, 1 - t), 0.65);
                if (p > hw) continue;
                m.set(x, y, (p > hw - 1.0 || t > 0.85) ? edge : (p < 0.6 ? 'd' : 'S'));
            }
        }
        return m;
    }

    /** Vanguard: a tall pointed grey hood with a tail spike, a cute red-orange face with a beak, and tentacles. */
    private static Grid vanguard(int v, int f)
    {
        char tip = v == 1 ? 'T' : (v == 2 ? 'i' : 'a');
        Grid g = new Grid();
        int oy = v == 2 ? 0 : 3;
        double w = f;
        Grid back = new Grid();
        Grid front = new Grid();
        if (v == 0)
        {
            double[][] ts = {
                {16, 25, 3, 30, 1 + w, 10, 12 + w, 4},
                {18, 26, 9, 31, 7 + w, 12, 15 + w, 7},
                {25, 25, 38, 30, 40 - w, 10, 29 - w, 4},
                {23, 26, 31, 31, 34 - w, 14, 27 - w, 9}};
            for (double[] t : ts) tentacle(back, shiftY(t, oy), 'r', tip, 'u');
        }
        else if (v == 1)
        {
            double[][] ts = {
                {17, 24, 2, 19, 15 + 1.5 * w, 9, 7 + w, 0},
                {24, 24, 39, 19, 27 - 1.5 * w, 9, 34 - w, 0}};
            for (double[] t : ts) tentacle(back, shiftY(t, 3), 'r', tip, 'u');
            ring(back, 21, 28 + f, 13, 3, false, 'r');
            ring(front, 21, 28 + f, 13, 3, true, 'r');
        }
        else
        {
            // a fan of broad flat pointed petals along the ground, outer ones first
            double[][] pet = {{9, 20}, {22, 15.5}, {40, 10.5}, {66, 7.5}};
            for (int i = 0; i < pet.length; i++)
            {
                double sw = (i % 2 == 0 ? 3 : -3) * (f == 1 ? 1 : 0);
                part(g, petal(21, 25, 180 - pet[i][0] - sw, pet[i][1], 9, tip));
                part(g, petal(21, 25, pet[i][0] + sw, pet[i][1], 9, tip));
            }
        }
        part(g, back);
        part(g, bevel(ellipseRot(31, 24 + oy + f, 7, 2.3, 14, 'c'), 'C', 'u'));
        Grid hood = new Grid();
        for (int y = 1 + oy; y <= 20 + oy; y++)
        {
            double t = (y - (1 + oy)) / 19.0;
            double hw = 0.7 + 6.9 * Math.pow(t, 0.7);
            for (int x = (int) Math.round(21 - hw); x < (int) Math.round(21 + hw); x++) hood.set(x, y, 'c');
        }
        with(hood, oval(14, 13 + oy, 15, 13, 'c'));
        bevel(hood, 'C', 'u');
        part(g, hood);
        part(g, bevel(oval(15, 13 + oy, 12, 13, 'f'), 'a', 'P'));
        part(g, rect(17, 17 + oy, 3, 3, 'z'));
        part(g, rect(23, 17 + oy, 3, 3, 'z'));
        g.pxs(tip, 18, 18 + oy, 24, 18 + oy).pxs('x', 17, 17 + oy, 23, 17 + oy);
        part(g, rect(19, 21 + oy, 5, 2, 'A'));
        g.pxs('A', 20, 23 + oy, 21, 23 + oy, 22, 23 + oy);
        part(g, front);
        return g;
    }

    private static double[] shiftY(double[] t, int dy)
    {
        double[] o = t.clone();
        for (int i = 1; i < o.length; i += 2) o[i] += dy;
        return o;
    }

    /** A crescent blade arm hugging the lower left of two circles; (mirror) flips it about the sprite's centre. */
    private static Grid crescent(int dy, boolean mirror, double scale)
    {
        Grid m = new Grid();
        for (int y = 0; y < 22; y++)
        {
            for (int x = 0; x < 20; x++)
            {
                double ox = x + 0.5;
                double oy = y + 0.5 - dy;
                double d1 = Math.hypot(ox - 9, oy - 10.5);
                double d2 = Math.hypot(ox - 12.5, oy - 8.5);
                if (d1 > 8.8 * scale || d2 <= 7.2 * scale) continue;
                int xx = mirror ? 41 - x : x;
                m.set(xx, y, d1 > 7.6 * scale ? 's' : 'p');
            }
        }
        return m;
    }

    /** Jal-ImKot: a snake-like obsidian creature: upright torso on a thick slithering coil, big scythe-blade arms. */
    private static Grid meleer(int f)
    {
        Grid g = new Grid();
        int dy = -f;
        int dy2 = f - 1;
        // back arm and blade (mirrored)
        part(g, crescent(dy2 + 1, true, 0.95));
        part(g, bevel(rect(26, 15 + dy2, 8, 4, 'O'), 'p', 'o'));
        part(g, rect(31, 17 + dy2, 3, 3, 'p'));
        // tail and coil
        part(g, bevel(oval(36, 24 + f, 6, 6, 'o'), 'O', 'z'));
        part(g, bevel(oval(29, 25 - f, 10, 8, 'o'), 'O', 'z'));
        part(g, bevel(oval(10, 24, 24, 9, 'o'), 'O', 'z'));
        part(g, bevel(oval(14, 22 + f, 10, 9, 'o'), 'O', 'z'));
        g.pxs('a', 13, 29, 17, 30, 21, 30, 25, 29 - f, 29, 28, 33, 28 - f, 37, 28 + f);
        g.pxs('a', 15, 26 + f, 19, 27, 23, 27, 27, 26, 31, 26 - f);
        // torso and head
        part(g, bevel(oval(14, 11, 14, 15, 'o'), 'O', 'z'));
        g.pxs('a', 17, 17, 18, 18, 19, 19, 24, 17, 23, 18, 22, 19, 21, 21, 21, 22);
        part(g, bevel(oval(14, 2, 14, 11, 'o'), 'O', 'z'));
        part(g, rect(16, 6, 3, 2, 'A'));
        part(g, rect(23, 6, 3, 2, 'A'));
        g.pxs('z', 16, 6, 23, 6).pxs('x', 17, 11, 20, 11, 23, 11);
        g.pxs('a', 18, 9, 19, 9, 20, 9, 21, 9, 22, 9);
        g.pxs('O', 17, 1, 24, 1, 17, 0);
        // front blade arm
        part(g, crescent(dy, false, 1.0));
        part(g, bevel(rect(10, 15 + dy, 6, 4, 'O'), 'p', 'o'));
        part(g, rect(8, 16 + dy, 3, 3, 'p'));
        return g;
    }

    /** Jal-Nib: a little lava grub: segmented dark body low to the ground, glowing seams, two big tusks. */
    private static Grid nibbler(int f)
    {
        Grid g = new Grid();
        part(g, bevel(oval(31, 28 - f, 6, 5 + f, 'o'), 'O', 'z'));
        part(g, bevel(oval(26, 26 - f, 8, 7 + f, 'o'), 'O', 'z'));
        part(g, bevel(oval(20, 25 + f, 8, 8 - f, 'o'), 'O', 'z'));
        part(g, bevel(oval(13, 24, 9, 9, 'o'), 'O', 'z'));
        // glowing seams between the segments
        g.pxs('a', 26, 28 - f, 26, 29 - f, 26, 30, 31, 30, 31, 31, 20, 28, 20, 29 + f, 20, 30);
        g.pxs('a', 23, 26 + f, 29, 27 - f, 34, 29 - f);
        // eyes and tusks
        g.pxs('A', 16, 27, 17, 27, 19, 27, 20, 27);
        g.pxs('f', 16, 28, 19, 28);
        Grid tusks = new Grid();
        tusks.stamp(8, 24, "bb..", "bbb.", ".bbb", "..bb");
        tusks.stamp(8, 29, "..bb", ".bbb", "bbb.", "bb..");
        part(g, tusks);
        g.pxs('z', 13, 30, 14, 30, 15, 30, 16, 30);
        return g;
    }

    // ---------------------------------------------------------------- falling things

    private static Grid buildFall(Critter.Fall fall, int v)
    {
        Grid g = new Grid(16, 16);
        if (fall == Critter.Fall.BONE)
        {
            Grid b = v == 0 ? bone(4, 12, 12, 4) : bone(3.5, 5.5, 12, 10.5);
            bevel(b, (char) 0, 'B');
            PixelArt.part(g, b, 'k');
            return g;
        }
        if (v == 0)
        {
            Grid e = new Grid(16, 16);
            with(e, ovalN(4, 5, 8, 8, 'f'));
            with(e, ovalN(5, 6, 6, 6, 'a'));
            with(e, ovalN(6, 7, 3, 3, 'A'));
            e.set(7, 8, 'x');
            e.stamp(6, 2, "..f.", ".faa");
            e.set(8, 4, 'a');
            e.set(7, 4, 'f');
            PixelArt.part(g, e, 'F');
            return g;
        }
        Grid rock = new Grid(16, 16);
        rock.stamp(3, 4,
            "..HH.Hh...",
            ".HHHHhhhh.",
            "HHhhhahhhh",
            "HhhahhhhhF",
            "hhhhhaahhF",
            ".hhhhhhhFF",
            "..hFFhhFF.");
        PixelArt.part(g, rock, 'k');
        g.pxs('A', 8, 7, 9, 8, 11, 9);
        return g;
    }

    private static Grid ovalN(int x, int y, int w, int h, char c)
    {
        Grid m = new Grid(16, 16);
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

    /** A cartoon bone from (ax, ay) to (ex, ey) in pixel coordinates: thin shaft, two round bumps at each end. */
    private static Grid bone(double ax, double ay, double ex, double ey)
    {
        Grid m = new Grid(16, 16);
        double dx = ex - ax;
        double dy = ey - ay;
        double len = Math.hypot(dx, dy);
        dx /= len;
        dy /= len;
        double px = -dy;
        double py = dx;
        for (int j = 0; j < 16; j++)
        {
            for (int i = 0; i < 16; i++)
            {
                double x = i + 0.5;
                double y = j + 0.5;
                double t = Math.max(0, Math.min(len, (x - ax) * dx + (y - ay) * dy));
                double d = Math.hypot(x - (ax + dx * t), y - (ay + dy * t));
                boolean on = d <= 1.1;
                for (int end = 0; end < 2 && !on; end++)
                {
                    double bx = end == 0 ? ax : ex;
                    double by = end == 0 ? ay : ey;
                    for (int s = -1; s <= 1; s += 2)
                    {
                        double cx = bx + px * 1.6 * s;
                        double cy = by + py * 1.6 * s;
                        if (Math.hypot(x - cx, y - cy) <= 1.75) on = true;
                    }
                }
                if (on) m.set(i, j, 'b');
            }
        }
        return m;
    }
}
