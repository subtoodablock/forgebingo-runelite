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
 * Draws the side panel at its real size (one image pixel per screen pixel), so the text can be judged exactly as a
 * player sees it. Not a test; writes build/preview/text-1x.png (or the name given as the second argument).
 */
public final class TextPreview
{
    private TextPreview()
    {
    }

    public static void main(String[] args) throws Exception
    {
        File out = new File(args.length > 0 ? args[0] : "build/preview");
        String name = args.length > 1 ? args[1] : "text-1x.png";
        out.mkdirs();
        try
        {
            net.runelite.client.ui.laf.RuneLiteLAF.setup();
        }
        catch (Throwable t)
        {
            System.out.println("RuneLite look and feel not applied: " + t);
        }
        SwingUtilities.invokeAndWait(() -> {
            try
            {
                RepaintManager.currentManager(null).setDoubleBufferingEnabled(false);
                PanelUi.animations = false;
                ForgeBingoPanel.usePixelFonts(false);
                ForgeBingoPanel.useTheme(PanelTheme.CLASSIC);
                ForgeBingoPanel panel = new ForgeBingoPanel(new PanelPreview.Fake(), null);
                panel.setImps(false);
                panel.setRain(false);
                panel.setStatus(true, "Connected");
                ApiModels.BoardResponse board = new Gson().fromJson(ThemePreview.BOARD, ApiModels.BoardResponse.class);
                panel.setBoard(board, null, null);
                panel.showTile(board.tiles.get(1));
                JFrame frame = new JFrame();
                frame.setUndecorated(true);
                frame.getContentPane().add(panel);
                frame.pack();
                int h = 860;
                frame.setSize(PluginPanel.PANEL_WIDTH, h);
                frame.validate();
                BufferedImage img = new BufferedImage(PluginPanel.PANEL_WIDTH, h, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                frame.getContentPane().paint(g);
                g.dispose();
                ImageIO.write(img, "png", new File(out, name));
                panel.dispose();
                frame.dispose();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        });
        System.out.println("Wrote " + name);
        System.exit(0);
    }
}
