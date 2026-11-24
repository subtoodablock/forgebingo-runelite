package com.forgebingo.core;

import java.awt.image.BufferedImage;
import java.util.function.IntUnaryOperator;

/**
 * Shared tools for the side panel's themed pixel-art creatures (the imp has its own older copy in ImpSprite).
 * A creature is drawn on a WIDTH x HEIGHT grid of palette letters, facing LEFT, feet on the bottom row, body centred
 * on column WIDTH / 2, so every creature fits the same walking code.
 *
 * Lives in src/test/java since the art was baked into sprite sheets: only the drawing code (PetSpritesA..F,
 * RaidSprites, CryptSprites, SkillPropArt) uses it now. The plugin itself takes the grid size from ImpSprite.
 */
public final class PixelArt
{
    public static final int WIDTH = ImpSprite.WIDTH;
    public static final int HEIGHT = ImpSprite.HEIGHT;

    private PixelArt()
    {
    }

    /** A pixel grid of palette letters ((0,0) is the top left); anything outside it is dropped. */
    public static final class Grid
    {
        public final int w;
        public final int h;
        public final char[][] px;

        public Grid()
        {
            this(WIDTH, HEIGHT);
        }

        public Grid(int w, int h)
        {
            this.w = w;
            this.h = h;
            this.px = new char[h][w];
        }

        public void set(int x, int y, char c)
        {
            if (x >= 0 && x < w && y >= 0 && y < h) px[y][x] = c;
        }

        public boolean has(int x, int y)
        {
            return x >= 0 && x < w && y >= 0 && y < h && px[y][x] != 0;
        }

        /** Rows of palette letters with their top left at (x0, y0); '.' and ' ' are see-through. */
        public Grid stamp(int x0, int y0, String... rows)
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
            return this;
        }

        /** Single pixels: x, y pairs. */
        public Grid pxs(char c, int... xy)
        {
            for (int i = 0; i + 1 < xy.length; i += 2) set(xy[i], xy[i + 1], c);
            return this;
        }
    }

    public static Grid shape(int w, int h, int x, int y, String... rows)
    {
        return new Grid(w, h).stamp(x, y, rows);
    }

    public static Grid shape(int x, int y, String... rows)
    {
        return new Grid().stamp(x, y, rows);
    }

    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** Paints a part onto the sprite with an automatic 1px outline (palette letter {@code outline}) around it. */
    public static void part(Grid g, Grid fill, char outline)
    {
        for (int j = 0; j < fill.h; j++)
        {
            for (int i = 0; i < fill.w; i++)
            {
                if (fill.px[j][i] == 0) continue;
                for (int[] s : SIDES)
                {
                    if (!fill.has(i + s[0], j + s[1])) g.set(i + s[0], j + s[1], outline);
                }
            }
        }
        for (int j = 0; j < fill.h; j++)
        {
            for (int i = 0; i < fill.w; i++)
            {
                if (fill.px[j][i] != 0) g.set(i, j, fill.px[j][i]);
            }
        }
    }

    /** The grid as a transparent image; {@code colour} gives each palette letter its 0xRRGGBB. */
    public static BufferedImage render(Grid g, IntUnaryOperator colour)
    {
        BufferedImage img = new BufferedImage(g.w, g.h, BufferedImage.TYPE_INT_ARGB);
        for (int j = 0; j < g.h; j++)
        {
            for (int i = 0; i < g.w; i++)
            {
                char c = g.px[j][i];
                if (c != 0) img.setRGB(i, j, 0xff000000 | colour.applyAsInt(c));
            }
        }
        return img;
    }
}
