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
 * Draws the side panel with a FINISHED tile open, to check "Send screenshot" stays on a finished task. Not a test;
 * writes build/preview/panel-done-card.png.
 */
public final class DoneCardPreview
{
    private DoneCardPreview()
    {
    }

    private static final String BOARD = "{'teamBoardId':'b1','teamId':'team1','teamName':'Team 1',"
        + "'event':{'id':'e1','name':'Wear Protection','status':'live','active':true},'board':{'id':'bd','title':'Board','size':2},'tiles':["
        + "{'id':'t0','row':0,'col':0,'title':'25 Zulrah kills','points':2,'completionMode':'all','completed':true,'tasks':["
        + "{'id':'k0','description':'25 kills at Zulrah (team total)','requiredQty':25,'sourceType':'kc','approvedQty':25,'completed':true}]},"
        + "{'id':'t1','row':0,'col':1,'title':'Vorkath head','points':1,'completionMode':'all','completed':false,'tasks':[{'id':'k1','description':'Vorkath head','requiredQty':1,'sourceType':'npc','approvedQty':0}]},"
        + "{'id':'t2','row':1,'col':0,'title':'Magic lamp','points':1,'completionMode':'all','completed':false,'tasks':[{'id':'k2','description':'Rub a lamp','requiredQty':1,'sourceType':'manual','approvedQty':0}]},"
        + "{'id':'t3','row':1,'col':1,'title':'Giant Mole','points':1,'completionMode':'all','completed':false,'tasks':[{'id':'k3','description':'10 kills at Giant Mole','requiredQty':10,'sourceType':'kc','approvedQty':4}]}"
        + "]}";

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
        SwingUtilities.invokeAndWait(() -> {
            try
            {
                RepaintManager.currentManager(null).setDoubleBufferingEnabled(false);
                PanelUi.animations = false;
                ForgeBingoPanel.usePixelFonts(false);
                ForgeBingoPanel panel = new ForgeBingoPanel(new PanelPreview.Fake(), null);
                panel.setStatus(true, "Connected");
                ApiModels.BoardResponse board = new Gson().fromJson(BOARD, ApiModels.BoardResponse.class);
                panel.setBoard(board, null, null);
                panel.showTile(board.tiles.get(0));
                JFrame frame = new JFrame();
                frame.setUndecorated(true);
                frame.getContentPane().add(panel);
                frame.pack();
                int h = 760;
                frame.setSize(PluginPanel.PANEL_WIDTH, h);
                frame.validate();
                BufferedImage img = new BufferedImage(PluginPanel.PANEL_WIDTH * 2, h * 2, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                g.scale(2, 2);
                frame.getContentPane().paint(g);
                g.dispose();
                ImageIO.write(img, "png", new File(out, "panel-done-card.png"));
                panel.dispose();
                frame.dispose();
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        });
        System.out.println("Wrote panel-done-card.png");
        System.exit(0);
    }
}
