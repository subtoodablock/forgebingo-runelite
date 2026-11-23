package com.forgebingo;

import com.forgebingo.core.Pet;
import com.forgebingo.core.PetSpritesB;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Writes build/preview/pets-b.png: every pet of PetSpritesB, both frames at 6x, plus a row at 2x. */
public final class PetSpritesBPreview
{
    public static void main(String[] args) throws Exception
    {
        Pet[] pets = {Pet.ABYSSAL_PROTECTOR, Pet.BLOODHOUND, Pet.CHOMPY_CHICK, Pet.HERBI,
            Pet.LIL_CREATOR, Pet.MR_MCGROOT, Pet.PENANCE_QUEEN, Pet.QUETZIN};
        int s = 6;
        int cw = 42 * s;
        int ch = 34 * s;
        int cols = 4;
        int rows = 2;
        int cell = 2 * cw + 20;
        int width = cols * cell / 2 + 40;
        // layout: 2 pets per row of cells -> 2 columns of (2 frames)
        int perRow = 2;
        int pw = 2 * cw + 30;
        width = perRow * pw + 20;
        int height = (pets.length / perRow) * (ch + 40) + 40 + 34 * 2 + 40;
        BufferedImage sheet = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x2b2722));
        g.fillRect(0, 0, width, height);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        for (int i = 0; i < pets.length; i++)
        {
            int x = 10 + (i % perRow) * pw;
            int y = 10 + (i / perRow) * (ch + 40);
            g.setColor(Color.WHITE);
            g.drawString(pets[i].name(), x, y + 14);
            for (int f = 0; f < 2; f++)
            {
                BufferedImage im = PetSpritesB.image(pets[i], f);
                g.drawImage(im, x + f * (cw + 10), y + 20, cw, ch, null);
            }
        }
        int ry = 10 + (pets.length / perRow) * (ch + 40) + 10;
        int x = 10;
        for (Pet p : pets)
        {
            for (int f = 0; f < 2; f++)
            {
                g.drawImage(PetSpritesB.image(p, f), x, ry, 84, 68, null);
                x += 84;
            }
        }
        g.dispose();
        File out = new File("build/preview/pets-b.png");
        out.getParentFile().mkdirs();
        ImageIO.write(sheet, "png", out);
    }
}
