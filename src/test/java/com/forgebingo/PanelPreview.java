package com.forgebingo;

import com.forgebingo.core.ApiModels;
import com.forgebingo.core.ConnectionStatus;
import com.google.gson.Gson;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.PluginPanel;

/**
 * Draws the side panel to PNG files without starting RuneLite, so its look can be checked after a change:
 * the panel with a sample board, a tile open and the imps a few seconds into their walk. Not a test; run its
 * main with the test classpath and an output folder as the only argument.
 */
public class PanelPreview
{
    /** A plugin that answers the panel's questions from memory instead of the game. */
    static final class Fake extends ForgeBingoPlugin
    {
        @Override
        boolean panelHeaderCollapsed()
        {
            return false;
        }

        @Override
        void setPanelHeaderCollapsed(boolean collapsed)
        {
        }

        @Override
        void refreshNow()
        {
        }

        @Override
        void toggleBoardOverlay()
        {
        }

        @Override
        void selectBoard(String teamBoardId)
        {
        }

        @Override
        boolean isWorkingOn(ApiModels.Tile t)
        {
            return "t1".equals(t.id);
        }

        @Override
        void setWorkingOn(String tileId, boolean on)
        {
        }

        @Override
        BufferedImage tileImage(String proxyPath)
        {
            return null;
        }

        @Override
        BufferedImage itemImage(int itemId)
        {
            // a stand-in for the client's item sprite: a gold blade on a dark hilt
            BufferedImage img = new BufferedImage(36, 32, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setColor(new java.awt.Color(0x3a2a14));
            g.fillRect(4, 22, 9, 5);
            g.setColor(new java.awt.Color(180 + itemId % 60, 150, 60 + itemId % 120));
            g.fillPolygon(new int[] {10, 14, 32, 30}, new int[] {22, 26, 6, 4}, 4);
            g.dispose();
            return img;
        }

        @Override
        boolean tileImageMissing(String proxyPath)
        {
            return true;
        }

        @Override
        String itemName(int itemId)
        {
            return "Item " + itemId;
        }

        /** the "Rejected" lines' memory, kept here instead of in RuneLite's settings */
        private final java.util.Map<String, String> rejectedMemory = new java.util.HashMap<>();

        @Override
        String rejectedMemory(String teamBoardId)
        {
            return rejectedMemory.getOrDefault(teamBoardId, "");
        }

        @Override
        void setRejectedMemory(String teamBoardId, String value)
        {
            rejectedMemory.put(teamBoardId, value);
        }

        @Override
        void sendScreenshotProof(String teamBoardId, String taskId, String what, String tileName)
        {
        }
    }

    private static final String BOARD = "{'teamBoardId':'b1','teamId':'team1','teamName':'Team 1',"
        + "'event':{'id':'e1','name':'BINGIOOOOO','status':'live','active':true},'board':{'id':'bd','title':'Board','size':3},'tiles':["
        + "{'id':'t0','row':0,'col':0,'title':'Barrows item','points':1,'completionMode':'all','completed':false,'tasks':[{'id':'k0','description':'Any Barrows item','requiredQty':1,'sourceType':'npc','approvedQty':0,'rejectedReason':'Drop message not visible'}]},"
        + "{'id':'t1','row':0,'col':1,'title':'10 Araxxor KC','points':1,'completionMode':'all','completed':false,'workingOn':[{'name':'You','me':true}],'tasks':[{'id':'k1','description':'10 kills at Araxxor (team total)','requiredQty':10,'sourceType':'manual','approvedQty':4}]},"
        + "{'id':'t2','row':0,'col':2,'title':'Magic lamp','points':1,'completionMode':'all','completed':false,'tasks':[{'id':'k2','description':'Rub a lamp','requiredQty':1,'sourceType':'manual','approvedQty':0,'pendingQty':1}]},"
        + "{'id':'t3','row':1,'col':0,'title':'Genie','points':1,'completionMode':'all','completed':false,'tasks':[{'id':'k3','description':'Meet the genie','requiredQty':1,'sourceType':'manual','approvedQty':0,'pendingQty':1}]},"
        + "{'id':'t4','row':1,'col':1,'title':'Free space','points':1,'completionMode':'all','completed':true,'tasks':[]},"
        + "{'id':'t5','row':1,'col':2,'title':'Fighter torso','points':1,'completionMode':'all','completed':false,'tasks':[{'id':'k5','description':'Torso','requiredQty':1,'sourceType':'manual','approvedQty':0,'pendingQty':1}]},"
        + "{'id':'t6','row':2,'col':0,'title':'Any 3 Corp uniques','points':3,'completionMode':'all','completed':false,'tasks':[{'id':'k6','description':'Corp uniques','requiredQty':3,'sourceType':'npc','approvedQty':0,'rejectedReason':'Screenshot is from before the bingo started'}]},"
        + "{'id':'t7','row':2,'col':1,'title':'Mystery','secret':true,'points':1,'completionMode':'all','completed':false,'tasks':[]},"
        + "{'id':'t8','row':2,'col':2,'title':'12.5M XP','points':2,'completionMode':'all','completed':false,'tasks':[{'id':'k8','description':'Gain 12.5M XP','requiredQty':12500000,'sourceType':'wom_gain','approvedQty':3100000,'rejectedReason':'Wrong account name in the screenshot'}]}"
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
        for (boolean pixel : new boolean[] {false, true})
        {
            SwingUtilities.invokeAndWait(() -> {
                try
                {
                    render(out, pixel);
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            });
        }
        System.out.println("Wrote previews to " + out.getAbsolutePath());
        int errors = live(out);
        System.out.println(errors == 0 ? "Live run: no errors" : "Live run: " + errors + " ERRORS");
        System.exit(errors == 0 ? 0 : 1);
    }

    /**
     * Runs the panel for real for a few seconds in a window parked off screen, with every timer going, while the
     * things a player does are played at it: opening tiles, a team tile finishing, every setting flipped. Counts
     * anything thrown on the event thread.
     */
    private static int live(File out) throws Exception
    {
        java.util.concurrent.atomic.AtomicInteger errors = new java.util.concurrent.atomic.AtomicInteger();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            errors.incrementAndGet();
            e.printStackTrace();
        });
        ForgeBingoPanel[] holder = new ForgeBingoPanel[1];
        JFrame[] frames = new JFrame[1];
        ApiModels.BoardResponse board = new Gson().fromJson(BOARD, ApiModels.BoardResponse.class);
        SwingUtilities.invokeAndWait(() -> {
            RepaintManager.currentManager(null).setDoubleBufferingEnabled(true);
            PanelUi.animations = true;
            ForgeBingoPanel.usePixelFonts(false);
            ForgeBingoPanel panel = new ForgeBingoPanel(new Fake(), null);
            panel.setStatus(true, "Connected");
            panel.setBoard(board, null, null);
            JFrame frame = new JFrame();
            frame.setUndecorated(true);
            frame.getContentPane().add(panel);
            frame.setSize(PluginPanel.PANEL_WIDTH, 900);
            frame.setLocation(-4000, 0);
            frame.setVisible(true);
            holder[0] = panel;
            frames[0] = frame;
        });
        ForgeBingoPanel panel = holder[0];
        Runnable[] script = {
            () -> panel.showTile(board.tiles.get(1)),
            () -> panel.teamFinished("Barrows item"),
            () -> panel.showTile(board.tiles.get(4)),
            () -> panel.setPixelFonts(true),
            () -> panel.setBoard(board, null, null),
            () -> panel.setPixelFonts(false),
            () -> panel.setAnimations(false),
            () -> panel.showTile(board.tiles.get(6)),
            () -> panel.setAnimations(true),
            () -> panel.setImps(false),
            () -> panel.setImps(true),
            () -> panel.setRain(false),
            () -> panel.setRain(true),
            () -> panel.setImps(false),
            () -> panel.setImps(true),
            panel::closeTile,
            () -> panel.showTile(board.tiles.get(8)),
        };
        for (Runnable step : script)
        {
            SwingUtilities.invokeAndWait(step);
            Thread.sleep(450);
        }
        Thread.sleep(2500);
        SwingUtilities.invokeAndWait(() -> {
            try
            {
                BufferedImage img = new BufferedImage(PluginPanel.PANEL_WIDTH * 2, 1800, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                g.scale(2, 2);
                RepaintManager.currentManager(null).setDoubleBufferingEnabled(false);
                frames[0].getContentPane().paint(g);
                g.dispose();
                ImageIO.write(img, "png", new File(out, "panel-live.png"));
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
            panel.dispose();
            frames[0].dispose();
        });
        return errors.get();
    }

    private static void render(File out, boolean pixel) throws Exception
    {
        RepaintManager.currentManager(null).setDoubleBufferingEnabled(false);
        PanelUi.animations = false;
        ForgeBingoPanel.usePixelFonts(pixel);
        ForgeBingoPanel panel = new ForgeBingoPanel(new Fake(), null);
        panel.setStatus(true, "Connected");
        panel.setScope("Signed in as Adventurer\nThis key works for all your events");
        panel.setQueueSize(0, null);
        panel.setIdentity(Collections.singletonList(new ConnectionStatus.Line(ConnectionStatus.Level.OK,
            "Tracking drops as Goonferno for:\n- Team 1 (BINGIOOOOO)\n- Team 1 (Yama Thanksgiving Bingo)")));
        ApiModels.BoardResponse board = new Gson().fromJson(BOARD, ApiModels.BoardResponse.class);
        ApiModels.BoardSummary summary = new ApiModels.BoardSummary();
        summary.teamBoardId = "b1";
        summary.eventName = "BINGIOOOOO";
        summary.teamName = "Team 1";
        panel.setBoards(Arrays.asList(summary), "b1");
        panel.setBoard(board, null, null);
        panel.showTile(board.tiles.get(1));

        JFrame frame = new JFrame();
        frame.setUndecorated(true);
        frame.getContentPane().add(panel);
        frame.pack();
        int h = 900;
        frame.setSize(PluginPanel.PANEL_WIDTH, h);
        frame.validate();

        // let the imps fall in and wander for a while
        for (int shot = 0; shot < 3; shot++)
        {
            for (int i = 0; i < 150 + shot * 90; i++) panel.imps.advance(0.033f);
            int scale = 2;
            BufferedImage img = new BufferedImage(PluginPanel.PANEL_WIDTH * scale, h * scale, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.scale(scale, scale);
            frame.getContentPane().paint(g);
            g.dispose();
            ImageIO.write(img, "png", new File(out, "panel-" + (pixel ? "pixel" : "clean") + "-" + shot + ".png"));
            if (pixel) break;
        }
        if (!pixel)
        {
            // the "Rejected" lines on their own: the top of the panel with all three, then after one is dismissed
            for (int shot = 0; shot < 2; shot++)
            {
                if (shot == 1)
                {
                    panel.dismissRejected(com.forgebingo.core.RejectedNotices.find(board.tiles).get(0).key);
                    frame.validate();
                }
                int scale = 2;
                int top = 430;
                BufferedImage img = new BufferedImage(PluginPanel.PANEL_WIDTH * scale, top * scale, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g.scale(scale, scale);
                frame.getContentPane().paint(g);
                g.dispose();
                ImageIO.write(img, "png", new File(out, "panel-rejected-" + (shot == 0 ? "three" : "one-dismissed") + ".png"));
            }
        }
        frame.dispose();
    }
}
