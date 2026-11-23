package com.forgebingo;

import com.forgebingo.core.Pet;
import com.forgebingo.core.PetSpritesF;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Writes build/preview/pets-f.png: every pet from PetSpritesF, both frames at 6x, plus a row at 2x. */
public final class PetSpritesFPreview
{
    public static void main(String[] args) throws Exception
    {
        Pet[] pets = {Pet.SKOTOS, Pet.SMOL_HEREDIT, Pet.SMOLCANO, Pet.SRARACHA, Pet.TINY_TEMPOR, Pet.TUMEKENS_GUARDIAN,
            Pet.TZREK_JAD, Pet.VENENATIS_SPIDERLING, Pet.VETION_JR, Pet.VORKI, Pet.WISP, Pet.YAMI, Pet.YOUNGLLEF};
        int s = 6;
        int cw = 42 * s;
        int ch = 34 * s + 22;
        int cols = 3;
        int rows = (pets.length + cols - 1) / cols;
        int width = cols * (2 * cw + 24) + 8;
        int row2 = 34 * 2 + 8;
        int height = rows * ch + 20 + row2 + 10;
        BufferedImage sheet = new BufferedImage(Math.max(width, pets.length * 84 + 8), height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x2b2722));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        for (int i = 0; i < pets.length; i++)
        {
            int x = 4 + (i % cols) * (2 * cw + 24);
            int y = (i / cols) * ch;
            g.setColor(Color.WHITE);
            g.drawString((i + 43) + " " + pets[i].name(), x + 4, y + 16);
            for (int f = 0; f < 2; f++)
            {
                g.setColor(new Color(0x38332d));
                g.fillRect(x + f * cw, y + 20, cw - 2, 34 * s);
                BufferedImage img = PetSpritesF.image(pets[i], f);
                g.drawImage(img, x + f * cw, y + 20, cw - 2, 34 * s, null);
            }
        }
        int y2 = rows * ch + 10;
        for (int i = 0; i < pets.length; i++)
        {
            g.drawImage(PetSpritesF.image(pets[i], 0), 4 + i * 84, y2, 84, 68, null);
        }
        g.dispose();
        File out = new File("build/preview/pets-f.png");
        out.getParentFile().mkdirs();
        ImageIO.write(sheet, "png", out);
    }
}
