package com.forgebingo;

import com.forgebingo.core.ApiModels;
import com.google.gson.Gson;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.PluginPanel;

/**
 * Writes a run of frames of the side panel per theme (build/preview/frames/<theme>-NN.png, one every tenth of a
 * second), so the creatures, the fliers, the sand and the lava can be watched moving. Not a test.
 */
public final class ThemeMotion
{
    private ThemeMotion()
    {
    }

    private static final int H = 560;
    private static final int FRAMES = 90;

    public static void main(String[] args) throws Exception
    {
        File out = new File(args.length > 0 ? args[0] : "build/preview/frames");
        out.mkdirs();
        try
        {
            net.runelite.client.ui.laf.RuneLiteLAF.setup();
        }
        catch (Throwable t)
        {
            System.out.println("RuneLite look and feel not applied: " + t);
        }
        for (PanelTheme theme : PanelTheme.values())
        {
            if (theme == PanelTheme.CLASSIC) continue;
            SwingUtilities.invokeAndWait(() -> {
                try
                {
                    run(theme, out);
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            });
        }
        System.out.println("Wrote frames to " + out.getAbsolutePath());
        System.exit(0);
    }

    private static void run(PanelTheme theme, File out) throws Exception
    {
        RepaintManager.currentManager(null).setDoubleBufferingEnabled(false);
        PanelUi.animations = false;
        ForgeBingoPanel.usePixelFonts(false);
        ForgeBingoPanel.useTheme(theme);
        ForgeBingoPanel panel = new ForgeBingoPanel(new PanelPreview.Fake(), null);
        panel.setStatus(true, "Connected");
        // the roll lands a few seconds into the clip, so the pop out and pop in can be watched
        panel.imps.rollChance = 1f;
        panel.imps.rollT = 18.5f;
        // -Dchexy=true: the visitor is always Chexy
        if (Boolean.getBoolean("chexy")) panel.imps.chexyChance = 1f;
        ApiModels.BoardResponse board = new Gson().fromJson(ThemePreview.BOARD, ApiModels.BoardResponse.class);
        panel.setBoard(board, null, null);
        JFrame frame = new JFrame();
        frame.setUndecorated(true);
        frame.getContentPane().add(panel);
        frame.pack();
        frame.setSize(PluginPanel.PANEL_WIDTH, H);
        frame.validate();
        PanelUi.animations = true;
        for (int i = 0; i < 500; i++) panel.imps.advance(0.033f);
        for (int f = 0; f < FRAMES; f++)
        {
            for (int i = 0; i < 3; i++) panel.imps.advance(0.033f);
            BufferedImage img = new BufferedImage(PluginPanel.PANEL_WIDTH, H, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            frame.getContentPane().paint(g);
            g.dispose();
            ImageIO.write(img, "png", new File(out, theme.name().toLowerCase() + "-" + String.format("%02d", f) + ".png"));
        }
        PanelUi.animations = false;
        panel.dispose();
        frame.dispose();
    }
}
