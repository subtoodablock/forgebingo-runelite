package com.forgebingo;

import com.forgebingo.core.SkillProp;
import com.forgebingo.core.SkillProps;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Writes build/preview/skill-props.png (and -a/-b/-c parts): every prop, every stage, both stage-2 frames. */
public final class SkillPropsPreview
{
    private static final int Z = 6;
    private static final int IMG = 24;
    private static final int CELL = IMG * Z + 8;
    private static final int LABEL = 16;
    private static final int ROW = IMG * Z + LABEL + 8;
    private static final int BLOCK = CELL * 4 + 16;
    private static final Color BG = new Color(0x1f281b);
    private static final Color GROUND = new Color(0x4d6342);

    public static void main(String[] args) throws Exception
    {
        SkillProp[] all = SkillProp.values();
        int perCol = 8;
        File dir = new File("build/preview");
        dir.mkdirs();

        // main sheet
        int smallH = 2 * IMG + 40 + IMG;
        BufferedImage sheet = new BufferedImage(BLOCK * 3 + 16, perCol * ROW + smallH + 30, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = setup(sheet);
        for (int i = 0; i < all.length; i++)
        {
            block(g, all[i], 8 + (i / perCol) * BLOCK, 8 + (i % perCol) * ROW);
        }
        int y = perCol * ROW + 20;
        g.setColor(Color.WHITE);
        g.drawString("1x (top) and 2x (bottom), stage 2, frame 0", 8, y - 4);
        for (int i = 0; i < all.length; i++)
        {
            BufferedImage im = SkillProps.image(all[i], 2, 0);
            g.drawImage(im, 8 + i * (IMG + 8), y, null);
            g.drawImage(im, 8 + i * (IMG * 2 + 4) / 1 - 0, y + IMG + 12, IMG * 2, IMG * 2, null);
        }
        g.setColor(new Color(0x7f9a70));
        g.fillRect(0, y + IMG, sheet.getWidth(), 1);
        g.fillRect(0, y + IMG + 12 + IMG * 2, sheet.getWidth(), 1);
        g.dispose();
        ImageIO.write(sheet, "png", new File(dir, "skill-props.png"));

        // three part sheets (easier to look at)
        String[] names = {"a", "b", "c"};
        for (int part = 0; part < 3; part++)
        {
            BufferedImage s = new BufferedImage(BLOCK + 16, perCol * ROW + 16, BufferedImage.TYPE_INT_ARGB);
            Graphics2D h = setup(s);
            for (int k = 0; k < perCol; k++)
            {
                block(h, all[part * perCol + k], 8, 8 + k * ROW);
            }
            h.dispose();
            ImageIO.write(s, "png", new File(dir, "skill-props-" + names[part] + ".png"));
        }
        System.out.println("wrote " + new File(dir, "skill-props.png").getAbsolutePath());
    }

    private static Graphics2D setup(BufferedImage img)
    {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setColor(BG);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        return g;
    }

    private static void block(Graphics2D g, SkillProp p, int ox, int oy)
    {
        g.setColor(Color.WHITE);
        g.drawString(p.name(), ox, oy + 12);
        int[][] spec = {{0, 0}, {1, 0}, {2, 0}, {2, 1}};
        for (int k = 0; k < 4; k++)
        {
            BufferedImage im = SkillProps.image(p, spec[k][0], spec[k][1]);
            int x = ox + k * CELL;
            int y = oy + LABEL;
            g.setColor(GROUND);
            g.fillRect(x, y + IMG * Z, IMG * Z, 1);
            if (im != null)
            {
                g.drawImage(im, x, y, im.getWidth() * Z, im.getHeight() * Z, null);
            }
        }
    }
}
