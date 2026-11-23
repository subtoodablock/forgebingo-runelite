package com.forgebingo;

import com.forgebingo.core.Critter;
import com.forgebingo.core.CryptSprites;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Writes build/preview/sprites-crypt.png (and a bigger sheet per group) so the crypt sprites can be eyeballed. */
public final class CryptSpritesPreview
{
    private static final int SCALE = 6;
    private static final Color BG = new Color(0x2b2722);

    private CryptSpritesPreview()
    {
    }

    private static final class Entry
    {
        final Critter c;
        final int v;

        Entry(Critter c, int v)
        {
            this.c = c;
            this.v = v;
        }
    }

    private static Entry[] of(Critter... cs)
    {
        Entry[] e = new Entry[cs.length];
        for (int i = 0; i < cs.length; i++) e[i] = new Entry(cs[i], 0);
        return e;
    }

    private static Entry[] join(Entry[]... parts)
    {
        int n = 0;
        for (Entry[] p : parts) n += p.length;
        Entry[] out = new Entry[n];
        int k = 0;
        for (Entry[] p : parts)
        {
            for (Entry e : p) out[k++] = e;
        }
        return out;
    }

    public static void main(String[] args) throws Exception
    {
        File dir = new File("build/preview");
        dir.mkdirs();
        Entry[] barrows = of(Critter.AHRIM, Critter.DHAROK, Critter.GUTHAN, Critter.KARIL, Critter.TORAG, Critter.VERAC);
        Entry[] inferno = of(Critter.ZUK, Critter.JAD, Critter.MELEER, Critter.NIBBLER);
        Entry[] cox = join(of(Critter.MUTTADILE, Critter.TEKTON, Critter.VESPULA),
            new Entry[] {new Entry(Critter.VANGUARD, 0), new Entry(Critter.VANGUARD, 1), new Entry(Critter.VANGUARD, 2)});
        ImageIO.write(sheet(join(barrows, inferno, cox), 3, true), "png", new File(dir, "sprites-crypt.png"));
        ImageIO.write(sheet(barrows, 3, false), "png", new File(dir, "sprites-crypt-barrows.png"));
        ImageIO.write(sheet(inferno, 2, false), "png", new File(dir, "sprites-crypt-inferno.png"));
        ImageIO.write(sheet(cox, 3, false), "png", new File(dir, "sprites-crypt-cox.png"));
        System.out.println("wrote " + dir.getAbsolutePath());
    }

    private static BufferedImage sheet(Entry[] list, int cols, boolean falls)
    {
        int cellW = 2 * 42 * SCALE + 40;
        int cellH = 34 * SCALE + 40;
        int rows = (list.length + cols - 1) / cols;
        int fallH = falls ? 16 * SCALE + 50 : 0;
        BufferedImage out = new BufferedImage(cols * cellW + 20, rows * cellH + fallH + 20, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setColor(BG);
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        for (int i = 0; i < list.length; i++)
        {
            int x = 10 + (i % cols) * cellW;
            int y = 10 + (i / cols) * cellH;
            g.setColor(Color.WHITE);
            g.drawString(list[i].c.name() + (list[i].c.variants > 1 ? " v" + list[i].v : ""), x, y + 14);
            for (int f = 0; f < 2; f++)
            {
                BufferedImage img = CryptSprites.image(list[i].c, list[i].v, f);
                g.drawImage(img, x + f * 42 * SCALE, y + 24, 42 * SCALE, 34 * SCALE, null);
            }
        }
        if (falls)
        {
            int y = 10 + rows * cellH;
            g.setColor(Color.WHITE);
            g.drawString("falling: BONE x2, EMBER x2", 10, y + 14);
            int x = 10;
            Critter.Fall[] fs = {Critter.Fall.BONE, Critter.Fall.EMBER};
            for (Critter.Fall fl : fs)
            {
                for (int v = 0; v < 2; v++)
                {
                    BufferedImage img = CryptSprites.fall(fl, v);
                    g.drawImage(img, x, y + 24, 16 * SCALE, 16 * SCALE, null);
                    x += 16 * SCALE + 20;
                }
            }
        }
        g.dispose();
        return out;
    }
}
