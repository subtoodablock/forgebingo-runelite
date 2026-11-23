package com.forgebingo;

import com.forgebingo.core.Pet;
import com.forgebingo.core.PetSpritesE;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public final class PetSpritesEPreview
{
    public static void main(String[] args) throws Exception
    {
        Pet[] pets = {Pet.DAGANNOTH_REX, Pet.DAGANNOTH_SUPREME, Pet.DARK_CORE, Pet.GENERAL_GRAARDOR,
            Pet.KRIL_TSUTSAROTH, Pet.KRAKEN, Pet.KREEARRA, Pet.SMOKE_DEVIL, Pet.SNAKELING, Pet.ZILYANA,
            Pet.PHOENIX, Pet.PRINCE_BLACK_DRAGON, Pet.SCORPIAS_OFFSPRING, Pet.SCURRY};
        int s = 6;
        int cw = 42 * s * 2 + 20;
        int ch = 34 * s + 40;
        int cols = 3;
        int rows = (pets.length + cols - 1) / cols;
        int rowW = pets.length * 2 * 84;
        int w = Math.max(cols * cw, rowW) + 10;
        int h = rows * ch + 34 * 2 + 30;
        BufferedImage sheet = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x2b2722));
        g.fillRect(0, 0, w, h);
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        for (int i = 0; i < pets.length; i++)
        {
            int cx = (i % cols) * cw + 5;
            int cy = (i / cols) * ch;
            g.setColor(Color.WHITE);
            g.drawString(pets[i].name(), cx + 8, cy + 20);
            for (int f = 0; f < 2; f++)
            {
                BufferedImage img = PetSpritesE.image(pets[i], f);
                g.drawImage(img, cx + f * 42 * s, cy + 30, 42 * s, 34 * s, null);
            }
        }
        int ry = rows * ch + 10;
        for (int i = 0; i < pets.length; i++)
        {
            for (int f = 0; f < 2; f++)
            {
                g.drawImage(PetSpritesE.image(pets[i], f), 5 + (i * 2 + f) * 84, ry, 84, 68, null);
            }
        }
        g.dispose();
        File out = new File("build/preview/pets-e.png");
        out.getParentFile().mkdirs();
        ImageIO.write(sheet, "png", out);
    }
}
