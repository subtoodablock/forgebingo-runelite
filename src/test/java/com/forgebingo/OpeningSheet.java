package com.forgebingo;

import com.forgebingo.core.OpeningAnim;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * The opening animations at a glance. Not a test; run its main with the test classpath (args: output folder, default
 * art). Writes opening-tiers.png (one mid-animation frame each for a sample of every tier, labelled) and
 * opening-legendary.gif (three Legendary ones back to back).
 * Optional second arg "all" also writes opening-all-*.png: every animation, a frame part way through.
 */
public class OpeningSheet
{
    private static final int W = 1280, H = 720;

    public static void main(String[] args) throws Exception
    {
        File out = new File(args.length > 0 ? args[0] : "art");
        out.mkdirs();
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"Dealer's Rows", "Spiral Turn", "Pond Ripple", "Skydive"});
        rows.add(new String[] {"Trampoline", "Autumn Leaves", "Pendulums", "Raindrops"});
        rows.add(new String[] {"Thunderstrike", "Pillars of Light", "Firefly Swarm", "Frost Nova"});
        rows.add(new String[] {"The Vortex", "Starfall", "Summoning Circle", "Northern Lights"});
        rows.add(new String[] {"Total Eclipse", "Phoenix Rising", "Tempest", "Rainbow Cyclone"});
        List<OpeningAnim.Spec> specs = new ArrayList<>();
        for (String[] r : rows) for (String n : r) specs.add(OpeningAnim.byName(n));
        sheet(new File(out, "opening-tiers.png"), specs, 4, 1250);
        if (args.length > 1 && "all".equals(args[1]))
        {
            for (int part = 0; part < 4; part++)
            {
                int from = part * 25, to = Math.min(100, from + 25);
                sheet(new File(out, "opening-all-" + (part + 1) + ".png"), OpeningAnim.ALL.subList(from, to), 5, 1250);
            }
        }
        // a smaller client for the GIF keeps the file a sensible size
        BoardOverlayPreview.animation(out, 960, 540, Arrays.asList(OpeningAnim.byName("The Vortex"), OpeningAnim.byName("Phoenix Rising"),
            OpeningAnim.byName("Summoning Circle")), "opening-legendary.gif", false);
        System.out.println("Wrote " + out.getAbsolutePath());
        System.exit(0);
    }

    /** A grid of cells: each the board part way through one opening, with its tier and name under it. */
    private static void sheet(File file, List<OpeningAnim.Spec> specs, int cols, int at) throws Exception
    {
        Rectangle b = BoardOverlayPreview.boardRect(W, H, 5);
        int margin = 22;
        Rectangle crop = new Rectangle(b.x - margin, b.y - margin, b.width + 2 * margin, b.height + 2 * margin).intersection(new Rectangle(0, 0, W, H));
        int cellW = 330, cellH = (int) Math.round(crop.height * cellW / (double) crop.width), label = 40, gap = 14, title = 48;
        int rows = (specs.size() + cols - 1) / cols;
        BufferedImage sheet = new BufferedImage(cols * (cellW + gap) + gap, title + rows * (cellH + label + gap) + gap, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x14110f));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setColor(new Color(0xffd666));
        g.setFont(new Font("SansSerif", Font.BOLD, 22));
        g.drawString("ForgeBingo board openings: 100 in 4 tiers (frame at " + at + " ms of 3000)", gap, 32);
        for (int i = 0; i < specs.size(); i++)
        {
            OpeningAnim.Spec spec = specs.get(i);
            int x = gap + (i % cols) * (cellW + gap), y = title + (i / cols) * (cellH + label + gap);
            BufferedImage[] got = new BufferedImage[1];
            BoardOverlayPreview.play(W, H, 5, spec, (t, img) -> got[0] = img, at);
            BufferedImage part = got[0].getSubimage(crop.x, crop.y, crop.width, crop.height);
            g.drawImage(part, x, y, cellW, cellH, null);
            g.setColor(spec.rarity.colour);
            g.drawRect(x - 1, y - 1, cellW + 1, cellH + 1);
            g.setFont(new Font("SansSerif", Font.BOLD, 15));
            String tier = spec.rarity.label.toUpperCase();
            g.drawString(tier, x, y + cellH + 18);
            int tw = g.getFontMetrics().stringWidth(tier + "  ");
            g.setColor(new Color(0xf2e6cf));
            g.drawString(spec.name, x + tw, y + cellH + 18);
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.setColor(new Color(0x9a8f80));
            g.drawString(spec.order.name().toLowerCase() + " / " + spec.entrance.name().toLowerCase() + " / " + spec.pace.name().toLowerCase()
                + (spec.fx == OpeningAnim.Fx.NONE ? "" : " / " + spec.fx.name().toLowerCase()), x, y + cellH + 34);
        }
        g.dispose();
        ImageIO.write(sheet, "png", file);
        System.out.println("Wrote " + file);
    }
}
