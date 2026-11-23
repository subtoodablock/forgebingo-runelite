package com.forgebingo;

import com.forgebingo.core.Pet;
import com.forgebingo.core.PetSpritesD;
import com.forgebingo.core.PixelArt;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Writes build/preview/pets-d.png: every pet in PetSpritesD, both frames at 6x, plus one row at 2x. */
public final class PetSpritesDPreview
{
    private static final Pet[] PETS = {
        Pet.KALPHITE_PRINCESS, Pet.LIL_ZIK, Pet.LILVIATHAN, Pet.LITTLE_NIGHTMARE, Pet.MAGGOT_MARQUESS, Pet.MOXI,
        Pet.MUPHIN, Pet.NEXLING, Pet.NID, Pet.NOON, Pet.OLMLET, Pet.ORION, Pet.CHAOS_ELEMENTAL, Pet.DAGANNOTH_PRIME};

    private PetSpritesDPreview()
    {
    }

    public static void main(String[] args) throws Exception
    {
        int big = 6;
        int sw = PixelArt.WIDTH * big;
        int sh = PixelArt.HEIGHT * big;
        int cellW = sw * 2 + 30;
        int cellH = sh + 30;
        int cols = 2;
        int rows = (PETS.length + cols - 1) / cols;
        int small = 2;
        int stripW = PETS.length * 2 * PixelArt.WIDTH * small;
        int width = Math.max(cols * cellW, stripW) + 20;
        int height = rows * cellH + PixelArt.HEIGHT * small + 50;
        BufferedImage sheet = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x2b2722));
        g.fillRect(0, 0, width, height);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setColor(Color.WHITE);
        for (int i = 0; i < PETS.length; i++)
        {
            int x = 10 + (i % cols) * cellW;
            int y = 10 + (i / cols) * cellH;
            g.drawString(PETS[i].name(), x, y + 12);
            for (int f = 0; f < 2; f++)
            {
                BufferedImage img = PetSpritesD.image(PETS[i], f);
                g.drawImage(img, x + f * (sw + 10), y + 16, sw, sh, null);
            }
        }
        int sy = rows * cellH + 30;
        for (int i = 0; i < PETS.length; i++)
        {
            for (int f = 0; f < 2; f++)
            {
                BufferedImage img = PetSpritesD.image(PETS[i], f);
                g.drawImage(img, 10 + (i * 2 + f) * PixelArt.WIDTH * small, sy, PixelArt.WIDTH * small,
                    PixelArt.HEIGHT * small, null);
            }
        }
        g.dispose();
        File out = new File("build/preview/pets-d.png");
        out.getParentFile().mkdirs();
        ImageIO.write(sheet, "png", out);
        System.out.println("wrote " + out.getAbsolutePath());
    }
}
