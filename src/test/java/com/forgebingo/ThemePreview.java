package com.forgebingo;

import com.forgebingo.core.ApiModels;
import com.google.gson.Gson;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.PluginPanel;

/**
 * Draws the side panel once per theme, with its creatures a few seconds into their walk and a few things falling.
 * Not a test; writes build/preview/theme-<name>.png (and theme-all.png, every theme side by side).
 */
public final class ThemePreview
{
    private ThemePreview()
    {
    }

    static final String BOARD = "{'teamBoardId':'b1','teamId':'team1','teamName':'Team 1',"
        + "'event':{'id':'e1','name':'Wear Protection','status':'live','active':true},'board':{'id':'bd','title':'Board','size':3},'tiles':["
        + "{'id':'t0','row':0,'col':0,'title':'Barrows item','points':1,'completionMode':'all','completed':true,'tasks':[{'id':'k0','description':'Any Barrows item','requiredQty':1,'sourceType':'npc','approvedQty':1,'completed':true}]},"
        + "{'id':'t1','row':0,'col':1,'title':'25 Zulrah kills','points':2,'completionMode':'all','completed':false,'tasks':[{'id':'k1','description':'25 kills at Zulrah (team total)','requiredQty':25,'sourceType':'kc','approvedQty':11}]},"
        + "{'id':'t2','row':0,'col':2,'title':'Magic lamp','points':1,'completionMode':'all','completed':false,'tasks':[{'id':'k2','description':'Rub a lamp','requiredQty':1,'sourceType':'manual','approvedQty':0,'pendingQty':1}]},"
        + "{'id':'t3','row':1,'col':0,'title':'Genie','points':1,'completionMode':'all','completed':false,'tasks':[{'id':'k3','description':'Meet the genie','requiredQty':1,'sourceType':'manual','approvedQty':0}]},"
        + "{'id':'t4','row':1,'col':1,'title':'Free space','points':1,'completionMode':'all','completed':true,'tasks':[]},"
        + "{'id':'t5','row':1,'col':2,'title':'Fighter torso','points':1,'completionMode':'all','completed':false,'tasks':[{'id':'k5','description':'Torso','requiredQty':1,'sourceType':'manual','approvedQty':0}]},"
        + "{'id':'t6','row':2,'col':0,'title':'Any 3 Corp uniques','points':3,'completionMode':'all','completed':false,'tasks':[{'id':'k6','description':'Corp uniques','requiredQty':3,'sourceType':'npc','approvedQty':1}]},"
        + "{'id':'t7','row':2,'col':1,'title':'Mystery','secret':true,'points':1,'completionMode':'all','completed':false,'tasks':[]},"
        + "{'id':'t8','row':2,'col':2,'title':'Vorkath head','points':2,'completionMode':'all','completed':false,'tasks':[{'id':'k8','description':'Vorkath head','requiredQty':1,'sourceType':'npc','approvedQty':0}]}"
        + "]}";

    private static final int H = 860;
    private static final int SCALE = 2;

    public static void main(String[] args) throws Exception
    {
        File out = new File(args.length > 0 ? args[0] : "build/preview");
        out.mkdirs();
        try
        {
            net.runelite.client.ui.laf.RuneLiteLAF.setup();
        }
        catch (Throwable t)
        {
            System.out.println("RuneLite look and feel not applied: " + t);
        }
        PanelTheme[] themes = PanelTheme.values();
        BufferedImage[] shots = new BufferedImage[themes.length];
        for (int i = 0; i < themes.length; i++)
        {
            int n = i;
            SwingUtilities.invokeAndWait(() -> {
                try
                {
                    shots[n] = render(themes[n]);
                    ImageIO.write(shots[n], "png", new File(out, "theme-" + themes[n].name().toLowerCase() + ".png"));
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            });
        }
        // every theme side by side, at half size
        int w = PluginPanel.PANEL_WIDTH;
        BufferedImage all = new BufferedImage((w + 8) * themes.length, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = all.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        for (int i = 0; i < themes.length; i++) g.drawImage(shots[i], i * (w + 8), 0, w, H, null);
        g.dispose();
        ImageIO.write(all, "png", new File(out, "theme-all.png"));
        System.out.println("Wrote theme previews to " + out.getAbsolutePath());
        System.exit(0);
    }

    private static BufferedImage render(PanelTheme theme)
    {
        RepaintManager.currentManager(null).setDoubleBufferingEnabled(false);
        PanelUi.animations = false;
        ForgeBingoPanel.usePixelFonts(false);
        ForgeBingoPanel.useTheme(theme);
        ForgeBingoPanel panel = new ForgeBingoPanel(new PanelPreview.Fake(), null);
        panel.setStatus(true, "Connected");
        // the rare bosses are turned up here so the picture shows them; in the plugin they are 1 in 100
        panel.imps.setLinked(true);
        panel.imps.rollChance = 1f;
        panel.imps.rollT = 24f;
        ApiModels.BoardResponse board = new Gson().fromJson(BOARD, ApiModels.BoardResponse.class);
        panel.setBoard(board, null, null);
        panel.setActivity("Counted: 25 Zulrah kills (just now)", java.util.Arrays.asList("25 Zulrah kills - just now, picture sent", "Vorkath head - 4m ago, picture sent"));
        panel.showTile(board.tiles.get(1));
        JFrame frame = new JFrame();
        frame.setUndecorated(true);
        frame.getContentPane().add(panel);
        frame.pack();
        frame.setSize(PluginPanel.PANEL_WIDTH, H);
        frame.validate();
        // let the creatures drop in and wander, and a few things fall
        PanelUi.animations = true; // the sand and the volcanoes only run with animations on
        for (int i = 0; i < 900; i++) panel.imps.advance(0.033f);
        PanelUi.animations = false;
        BufferedImage img = new BufferedImage(PluginPanel.PANEL_WIDTH * SCALE, H * SCALE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.scale(SCALE, SCALE);
        PanelUi.animations = true;
        frame.getContentPane().paint(g);
        PanelUi.animations = false;
        g.dispose();
        // what a frame costs: one step of the world plus one repaint at real size, averaged
        PanelUi.animations = true;
        BufferedImage one = new BufferedImage(PluginPanel.PANEL_WIDTH, H, BufferedImage.TYPE_INT_RGB);
        long t0 = System.nanoTime();
        int n = 200;
        for (int i = 0; i < n; i++)
        {
            panel.imps.advance(0.033f);
            Graphics2D b = one.createGraphics();
            frame.getContentPane().paint(b);
            b.dispose();
        }
        double ms = (System.nanoTime() - t0) / 1e6 / n;
        PanelUi.animations = false;
        System.out.println(String.format("%-10s %.2f ms per frame (%.1f%% of one core at 30 frames a second)", theme.name(), ms, ms * 30 / 10));
        panel.dispose();
        frame.dispose();
        return img;
    }

    private static BufferedImage unused(ForgeBingoPanel panel, JFrame frame)
    {
        BufferedImage img = new BufferedImage(PluginPanel.PANEL_WIDTH * SCALE, H * SCALE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.scale(SCALE, SCALE);
        frame.getContentPane().paint(g);
        g.dispose();
        panel.dispose();
        frame.dispose();
        return img;
    }
}
