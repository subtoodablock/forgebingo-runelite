package com.forgebingo;

import com.forgebingo.core.Pet;
import com.forgebingo.core.PetSprites;
import com.forgebingo.core.PixelArt;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Every pet on one sheet (first walk frame, 3x), with its name and rarity. Not a test; writes build/preview/pets-all.png. */
public final class PetSheet
{
    private PetSheet()
    {
    }

    public static void main(String[] args) throws Exception
    {
        File out = new File(args.length > 0 ? args[0] : "build/preview");
        out.mkdirs();
        int cols = 9;
        int scale = 3;
        int cw = PixelArt.WIDTH * scale + 14;
        int ch = PixelArt.HEIGHT * scale + 30;
        Pet[] pets = Pet.values();
        int rows = (pets.length + cols - 1) / cols;
        BufferedImage img = new BufferedImage(cols * cw + 8, rows * ch + 8, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x1f281b));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        int missing = 0;
        for (int i = 0; i < pets.length; i++)
        {
            int x = 4 + (i % cols) * cw;
            int y = 4 + (i / cols) * ch;
            g.setColor(new Color(0x2f3b27));
            g.fillRect(x + 2, y + 2, cw - 4, ch - 4);
            BufferedImage s = PetSprites.image(pets[i], 0);
            if (s == null) missing++;
            else
            {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g.drawImage(s, x + 7, y + 4, PixelArt.WIDTH * scale, PixelArt.HEIGHT * scale, null);
            }
            Color tint;
            switch (pets[i].rarity)
            {
                case COMMON: tint = new Color(0xe8ecd9); break;
                case UNCOMMON: tint = new Color(0x7fd67a); break;
                case RARE: tint = new Color(0x6cc4f5); break;
                case EPIC: tint = new Color(0xc79bff); break;
                default: tint = new Color(0xffb83f); break;
            }
            g.setColor(tint);
            String name = pets[i].label.replace("Pet ", "");
            g.drawString(name.length() > 20 ? name.substring(0, 20) : name, x + 6, y + ch - 9);
        }
        g.dispose();
        ImageIO.write(img, "png", new File(out, "pets-all.png"));
        System.out.println("Wrote pets-all.png, missing pictures: " + missing);
    }
}
