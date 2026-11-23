package com.forgebingo;

import com.forgebingo.core.Critter;
import com.forgebingo.core.RaidSprites;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;

/**
 * Writes build/preview/sprites-raid.png: every raid creature, variant and walk frame at 6x, plus the falling things.
 * Set RAID_ONLY=CRAB,NYLO to draw just some critters (handy for zoomed checks). Also prints stray-pixel warnings.
 */
public final class RaidSpritesPreview
{
    private static final int S = 6;

    public static void main(String[] args) throws Exception
    {
        String only = System.getenv("RAID_ONLY");
        Set<String> wanted = new HashSet<>();
        if (only != null && !only.isEmpty()) wanted.addAll(Arrays.asList(only.toUpperCase().split(",")));

        List<Object[]> entries = new ArrayList<>();
        Critter[] order = {Critter.CRAB, Critter.NYLO, Critter.XARPUS, Critter.VERZIK, Critter.AKKHA, Critter.ZEBAK, Critter.KEPHRI, Critter.BABA, Critter.WARDEN};
        for (Critter c : order)
        {
            if (!wanted.isEmpty() && !wanted.contains(c.name())) continue;
            for (int v = 0; v < c.variants; v++) entries.add(new Object[] {c, v});
        }

        int cols = 2;
        int cw = 2 * 42 * S + 16 + 24;
        int ch = 34 * S + 30;
        int rows = (entries.size() + cols - 1) / cols;
        int fallH = 18 * S + 40;
        int stripH = 34 * 2 + 30;
        int width = cols * cw + 24;
        int height = rows * ch + fallH + stripH + 20;
        BufferedImage sheet = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x2b2722));
        g.fillRect(0, 0, width, height);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));

        for (int i = 0; i < entries.size(); i++)
        {
            Critter c = (Critter) entries.get(i)[0];
            int v = (Integer) entries.get(i)[1];
            int x = 24 + (i % cols) * cw;
            int y = 10 + (i / cols) * ch;
            g.setColor(new Color(0xe8dcc0));
            g.drawString(c.name() + " v" + v, x, y + 14);
            for (int f = 0; f < 2; f++)
            {
                BufferedImage img = RaidSprites.image(c, v, f);
                int ix = x + f * (42 * S + 16);
                g.setColor(new Color(0x3a342d));
                g.fillRect(ix, y + 20, 42 * S, 34 * S);
                g.setColor(new Color(0x4a4238));
                g.drawLine(ix, y + 20 + 33 * S + S, ix + 42 * S, y + 20 + 33 * S + S);
                g.drawImage(img, ix, y + 20, 42 * S, 34 * S, null);
                report(c, v, f, img);
            }
        }

        int fy = 10 + rows * ch;
        g.setColor(new Color(0xe8dcc0));
        g.drawString("falling things", 24, fy + 14);
        int fx = 24;
        Object[][] falls = {
            {Critter.Fall.BLOOD_DROP, 0}, {Critter.Fall.BLOOD_DROP, 1}, {Critter.Fall.CRYSTAL, 0}, {Critter.Fall.CRYSTAL, 1},
            {Critter.Fall.KEPHRI_BOMB, 0}, {Critter.Fall.WATER_DROP, 0}};
        for (Object[] fl : falls)
        {
            BufferedImage img = RaidSprites.fall((Critter.Fall) fl[0], (Integer) fl[1]);
            g.setColor(new Color(0x3a342d));
            g.fillRect(fx, fy + 20, img.getWidth() * S, img.getHeight() * S);
            g.drawImage(img, fx, fy + 20, img.getWidth() * S, img.getHeight() * S, null);
            g.setColor(new Color(0xe8dcc0));
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            g.drawString(fl[0] + " " + fl[1] + " (" + img.getWidth() + "x" + img.getHeight() + ")", fx, fy + 30 + img.getHeight() * S);
            fx += Math.max(img.getWidth() * S, 150) + 14;
        }

        // 2x strip: how they look at roughly real size
        int sy = fy + fallH;
        int sx = 24;
        for (Object[] e : entries)
        {
            BufferedImage img = RaidSprites.image((Critter) e[0], (Integer) e[1], 0);
            g.drawImage(img, sx, sy, 84, 68, null);
            sx += 88;
            if (sx > width - 90) break;
        }
        g.dispose();

        File out = new File("build/preview/sprites-raid" + (wanted.isEmpty() ? "" : "-part") + ".png");
        out.getParentFile().mkdirs();
        ImageIO.write(sheet, "png", out);
        System.out.println("wrote " + out.getAbsolutePath());
    }

    /** Prints connected-component count (stray pixels) and the bounding box of an image. */
    private static void report(Critter c, int v, int f, BufferedImage img)
    {
        int w = img.getWidth();
        int h = img.getHeight();
        boolean[][] seen = new boolean[h][w];
        int comps = 0;
        int x0 = w;
        int x1 = -1;
        int y0 = h;
        int y1 = -1;
        StringBuilder small = new StringBuilder();
        for (int y = 0; y < h; y++)
        {
            for (int x = 0; x < w; x++)
            {
                if ((img.getRGB(x, y) >>> 24) == 0) continue;
                x0 = Math.min(x0, x);
                x1 = Math.max(x1, x);
                y0 = Math.min(y0, y);
                y1 = Math.max(y1, y);
                if (seen[y][x]) continue;
                comps++;
                int n = 0;
                List<int[]> stack = new ArrayList<>();
                stack.add(new int[] {x, y});
                seen[y][x] = true;
                while (!stack.isEmpty())
                {
                    int[] p = stack.remove(stack.size() - 1);
                    n++;
                    for (int dy = -1; dy <= 1; dy++)
                    {
                        for (int dx = -1; dx <= 1; dx++)
                        {
                            int nx = p[0] + dx;
                            int ny = p[1] + dy;
                            if (nx < 0 || ny < 0 || nx >= w || ny >= h || seen[ny][nx]) continue;
                            if ((img.getRGB(nx, ny) >>> 24) == 0) continue;
                            seen[ny][nx] = true;
                            stack.add(new int[] {nx, ny});
                        }
                    }
                }
                if (n < 30) small.append(" [").append(n).append("px at ").append(x).append(',').append(y).append(']');
            }
        }
        System.out.println(c + " v" + v + " f" + f + " comps=" + comps + " bbox x" + x0 + ".." + x1 + " (w" + (x1 - x0 + 1)
            + ") y" + y0 + ".." + y1 + " (h" + (y1 - y0 + 1) + ")" + small);
    }
}
