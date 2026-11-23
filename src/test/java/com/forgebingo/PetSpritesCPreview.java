package com.forgebingo;

import com.forgebingo.core.Pet;
import com.forgebingo.core.PetSpritesC;
import com.forgebingo.core.PixelArt;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Writes build/preview/pets-c.png: every PetSpritesC pet, both frames at 6x, plus a 2x row. */
public final class PetSpritesCPreview
{
    private static final Pet[] PETS = {
        Pet.ABYSSAL_ORPHAN, Pet.AGGY, Pet.BABY_MOLE, Pet.BARON, Pet.BEEF, Pet.BRAN, Pet.BUTCH,
        Pet.CALLISTO_CUB, Pet.DOM, Pet.GULL, Pet.HELLPUPPY, Pet.HUBERTE, Pet.IKKLE_HYDRA, Pet.JAL_NIB_REK
    };

    public static void main(String[] args) throws Exception
    {
        int s = 6;
        int cropX = 0;
        int cropW = 42;
        int cropY = 0;
        int cropH = PixelArt.HEIGHT;
        int cellW = cropW * s;
        int cellH = cropH * s + 22;
        int perRow = 3;
        int rows = (PETS.length + perRow - 1) / perRow;
        int pad = 12;
        int w = perRow * (2 * cellW + pad * 2) + pad;
        int rowH = cellH + pad;
        int smallH = PixelArt.HEIGHT * 2 + 24;
        int h = rows * rowH + smallH + pad * 2;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setColor(new Color(0x2b2722));
        g.fillRect(0, 0, w, h);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        for (int i = 0; i < PETS.length; i++)
        {
            int cx = pad + (i % perRow) * (2 * cellW + pad * 2);
            int cy = pad + (i / perRow) * rowH;
            g.setColor(Color.WHITE);
            g.drawString(PETS[i].name(), cx, cy + 14);
            for (int f = 0; f < 2; f++)
            {
                BufferedImage img = PetSpritesC.image(PETS[i], f);
                int lo = 99;
                int hi = -1;
                for (int yy = 0; yy < img.getHeight(); yy++)
                {
                    for (int xx = 0; xx < img.getWidth(); xx++)
                    {
                        if ((img.getRGB(xx, yy) >>> 24) != 0)
                        {
                            lo = Math.min(lo, xx);
                            hi = Math.max(hi, xx);
                        }
                    }
                }
                System.out.println(PETS[i] + " f" + f + " width " + (hi - lo + 1));
                int x = cx + f * cellW;
                g.setColor(new Color(0x3a352f));
                g.fillRect(x, cy + 20, cellW - 2, cropH * s);
                g.drawImage(img, x, cy + 20, x + cellW - 2, cy + 20 + cropH * s, cropX, cropY, cropX + cropW - 1 + 0, cropY + cropH, null);
            }
        }
        int y0 = rows * rowH + pad * 2;
        int x = pad;
        for (Pet p : PETS)
        {
            for (int f = 0; f < 2; f++)
            {
                BufferedImage img = PetSpritesC.image(p, f);
                g.drawImage(img, x, y0, PixelArt.WIDTH * 2, PixelArt.HEIGHT * 2, null);
                x += 50;
            }
            x += 8;
        }
        g.dispose();
        File f = new File("build/preview/pets-c.png");
        f.getParentFile().mkdirs();
        ImageIO.write(out, "png", f);
        System.out.println("wrote " + f.getAbsolutePath());
    }
}
