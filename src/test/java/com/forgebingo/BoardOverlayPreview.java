package com.forgebingo;

import com.forgebingo.core.ApiModels;
import com.forgebingo.core.BoardLayout;
import com.forgebingo.core.OpeningAnim;
import com.google.gson.Gson;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.File;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import net.runelite.api.Client;
import org.w3c.dom.Node;

/**
 * Draws the "Show board" overlay to PNG files and an animated GIF without starting RuneLite, so its look and its
 * opening animation can be checked after a change. Not a test; run its main with the test classpath:
 * args = output folder, then "before" or "after" (the file name prefix; "after" also writes the hover, card and
 * animation files) or "check" (as "after", plus board-check-*.png: odd board sizes, no board, rarer animations).
 */
public class BoardOverlayPreview
{
    /** the overlay's clock: frames are drawn at exact times */
    private static long now = 1_760_000_000_000L;

    private static final String BOARD = "{'teamBoardId':'b1','teamId':'team1','teamName':'Goblin Slayers',"
        + "'event':{'id':'e1','name':'Winter Clan Bingo 2026','status':'live','active':true},'board':{'id':'bd','title':'Board','size':5},'tiles':["
        + tile(0, 0, "5 Zulrah KC", 12921, 2, "'tasks':[" + task("k00", "Kill Zulrah 5 times (team total)", 5, "kc", 3, 0) + "]")
        + tile(0, 1, "Scythe of vitur", 22325, 5, "'tasks':[" + task("k01", "Scythe of vitur from the Theatre of Blood", 1, "raid_chest", 0, 0) + "]")
        + tile(0, 2, "4 x Mole claw", 7416, 1, "'description':'Whack the mole. Claws only, skins do not count.',"
            + "'workingOn':[{'name':'You','me':true},{'name':'Zezima','me':false}],'tasks':["
            + task("k02", "Mole claws from the Giant Mole (team total)", 4, "npc", 1, 1) + ","
            + task("k02b", "A screenshot of the team at the mole lair", 1, "manual", 0, 0) + "]")
        + tile(0, 3, "Dragon warhammer", 13576, 4, "'completed':true,'tasks':[" + doneTask("k03", "Dragon warhammer from Lizardman shamans") + "]")
        + tile(0, 4, "Abyssal whip", 4151, 1, "'tasks':[" + task("k04", "Abyssal whip", 1, "manual", 0, 1) + "]")
        + tile(1, 0, "Hard clue x10", null, 2, "'tasks':[" + task("k10", "Open 10 hard caskets", 10, "clue", 6, 0) + "]")
        + tile(1, 1, "Fire cape", null, 2, "'completed':true,'tasks':[" + doneTask("k11", "Beat TzTok-Jad") + "]")
        + tile(1, 2, "Any Barrows item", null, 1, "'tasks':[" + task("k12", "Any Barrows item", 1, "npc", 0, 0) + "]")
        + tile(1, 3, "Tome of fire", null, 2, "'tasks':[" + task("k13", "Tome of fire from Wintertodt", 1, "manual", 0, 0) + "]")
        + tile(1, 4, "99 Fishing", 9798, 3, "'tasks':[" + task("k14", "Team gains 2,000,000 Fishing XP", 2000000, "wom_gain", 1240000, 0) + "]")
        + tile(2, 0, "Twisted bow", 20997, 5, "'tasks':[" + task("k20", "Twisted bow from the Chambers of Xeric", 1, "raid_chest", 0, 0) + "]")
        + tile(2, 1, "Any boss pet", null, 5, "'completed':true,'tasks':[" + doneTask("k21", "Any boss pet") + "]")
        + tile(2, 2, "Free space", null, 1, "'completed':true,'tasks':[]")
        + "{'id':'t23','row':2,'col':3,'title':'?','secret':true,'points':3,'completionMode':'all','completed':false,'tasks':[]},"
        + tile(2, 4, "Elder maul", 21003, 4, "'tasks':[{'id':'k24','description':'Elder maul','requiredQty':1,'sourceType':'manual','approvedQty':0,"
            + "'pendingQty':0,'rejectedReason':'Screenshot does not show the drop'}]")
        + tile(3, 0, "Bandos chestplate", 11832, 3, "'tasks':[" + task("k30", "Bandos chestplate", 1, "npc", 0, 0) + "]")
        + tile(3, 1, "3 x Zenyte shard", 19529, 3, "'tasks':[" + task("k31", "Zenyte shards from Demonic gorillas", 3, "npc", 2, 0) + "]")
        + tile(3, 2, "Tumeken's shadow", 27275, 5, "'workingOn':[{'name':'Woox','me':false}],'tasks':[" + task("k32", "Tumeken's shadow", 1, "raid_chest", 0, 0) + "]")
        + tile(3, 3, "Vorkath's head", 21907, 2, "'completed':true,'tasks':[" + doneTask("k33", "Vorkath's head") + "]")
        + "{'id':'t34','row':3,'col':4,'title':'Any Corp sigil','points':5,'hasIcon':true,'iconUrl':'/v1/icons/tiles/t34','completionMode':'all',"
            + "'completed':false,'tasks':[" + task("k34", "Any sigil from the Corporeal Beast", 1, "npc", 0, 0) + "]},"
        + tile(4, 0, "Dragon pickaxe", 11920, 2, "'tasks':[" + task("k40", "Dragon pickaxe", 1, "manual", 0, 1) + "]")
        + tile(4, 1, "50 Vorkath KC", 21992, 2, "'tasks':[" + task("k41", "Kill Vorkath 50 times (team total)", 50, "kc", 22, 0) + "]")
        + tile(4, 2, "Crystal tool seed", 23953, 3, "'tasks':[" + task("k42", "Crystal tool seed from Zalcano", 1, "npc", 0, 0) + "]")
        + tile(4, 3, "5 x Ancient shard", 19677, 1, "'tasks':[" + task("k43", "Ancient shards from the Catacombs", 5, "npc", 0, 0) + "]")
        + tile(4, 4, "Kraken tentacle", 12004, 2, "'completed':true,'tasks':[" + doneTask("k44", "Kraken tentacle") + "]")
        + "{}]}";

    private static String tile(int row, int col, String title, Integer item, int points, String rest)
    {
        return "{'id':'t" + row + col + "','row':" + row + ",'col':" + col + ",'title':\"" + title + "\",'points':" + points
            + (item == null ? "" : ",'iconItemId':" + item) + ",'completionMode':'all'," + rest + "},";
    }

    private static String task(String id, String text, int need, String source, int approved, int pending)
    {
        return "{'id':'" + id + "','description':\"" + text + "\",'requiredQty':" + need + ",'sourceType':'" + source + "','approvedQty':" + approved
            + ",'pendingQty':" + pending + "}";
    }

    private static String doneTask(String id, String text)
    {
        return "{'id':'" + id + "','description':\"" + text + "\",'requiredQty':1,'sourceType':'npc','approvedQty':1,'completed':true}";
    }

    /** A plugin that answers the overlay's questions from memory instead of the game. */
    static final class Fake extends ForgeBingoPlugin
    {
        ApiModels.BoardResponse board = new Gson().fromJson(BOARD, ApiModels.BoardResponse.class);

        Fake()
        {
            // the sample text ends its tile list with a filler entry: drop it (25 tiles)
            board.tiles.remove(board.tiles.size() - 1);
        }
        private final java.util.Map<Integer, BufferedImage> sprites = new java.util.HashMap<>();
        private BufferedImage uploaded;

        @Override
        ApiModels.BoardResponse currentBoard()
        {
            return board;
        }

        @Override
        BufferedImage itemImage(int itemId)
        {
            return sprites.computeIfAbsent(itemId, BoardOverlayPreview::sprite);
        }

        @Override
        BufferedImage tileImage(String proxyPath)
        {
            if (uploaded == null) uploaded = uploadedIcon();
            return uploaded;
        }

        @Override
        boolean tileImageMissing(String proxyPath)
        {
            return false;
        }

        @Override
        String itemName(int itemId)
        {
            return "Item " + itemId;
        }
    }

    /**
     * A stand-in for the client's 36x32 item sprite: hard-edged pixel art with a dark outline, like the real ones.
     * (The real icons only exist inside the running client.)
     */
    static BufferedImage sprite(int itemId)
    {
        BufferedImage img = new BufferedImage(36, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        float hue = (itemId * 0.6180339f) % 1f;
        Color main = Color.getHSBColor(hue, 0.55f, 0.85f);
        Color dark = Color.getHSBColor(hue, 0.6f, 0.55f);
        Color light = Color.getHSBColor(hue, 0.3f, 1f);
        switch (Math.abs(itemId) % 6)
        {
            case 0: // sword
                g.setColor(new Color(0x6b4a22));
                g.fillPolygon(new int[] {6, 10, 13, 9}, new int[] {24, 28, 25, 21}, 4);
                g.setColor(new Color(0xc9a23a));
                g.fillPolygon(new int[] {7, 10, 17, 14}, new int[] {17, 14, 21, 24}, 4);
                g.setColor(main);
                g.fillPolygon(new int[] {12, 15, 30, 29}, new int[] {19, 22, 5, 4}, 4);
                g.setColor(light);
                g.drawLine(13, 19, 29, 5);
                break;
            case 1: // potion
                g.setColor(new Color(0x8a6a3a));
                g.fillRect(15, 5, 6, 4);
                g.setColor(new Color(0xbfd4d8));
                g.fillRect(14, 9, 8, 4);
                g.fillOval(8, 11, 20, 18);
                g.setColor(main);
                g.fillArc(9, 12, 18, 16, 180, 180);
                g.fillRect(10, 19, 16, 3);
                g.setColor(light);
                g.fillRect(12, 15, 2, 5);
                break;
            case 2: // helm
                g.setColor(dark);
                g.fillArc(7, 6, 22, 26, 0, 180);
                g.fillRect(7, 18, 22, 8);
                g.setColor(main);
                g.fillArc(9, 8, 18, 22, 0, 180);
                g.fillRect(9, 18, 8, 6);
                g.setColor(new Color(0x1a1512));
                g.fillRect(17, 15, 9, 3);
                g.fillRect(20, 18, 3, 8);
                g.setColor(light);
                g.fillRect(12, 10, 3, 5);
                break;
            case 3: // gem
                g.setColor(dark);
                g.fillPolygon(new int[] {18, 29, 24, 12, 7}, new int[] {5, 14, 27, 27, 14}, 5);
                g.setColor(main);
                g.fillPolygon(new int[] {18, 26, 22, 14, 10}, new int[] {8, 15, 24, 24, 15}, 5);
                g.setColor(light);
                g.fillPolygon(new int[] {18, 22, 18, 14}, new int[] {8, 15, 18, 15}, 4);
                break;
            case 4: // curved blade
                g.setColor(new Color(0x5d4023));
                g.fillPolygon(new int[] {6, 9, 27, 24}, new int[] {27, 29, 9, 7}, 4);
                g.setColor(main);
                g.fillPolygon(new int[] {22, 26, 31, 30, 24, 15, 8, 14, 21}, new int[] {10, 6, 9, 15, 21, 22, 18, 17, 15}, 9);
                g.setColor(light);
                g.drawLine(10, 18, 22, 19);
                break;
            default: // coins
                for (int i = 0; i < 4; i++)
                {
                    int y = 22 - i * 4;
                    g.setColor(new Color(0x9a7410));
                    g.fillOval(9, y, 18, 8);
                    g.setColor(new Color(0xf2c230));
                    g.fillOval(9, y - 2, 18, 8);
                    g.setColor(new Color(0xfff0a0));
                    g.fillRect(13, y, 5, 1);
                }
                break;
        }
        g.dispose();
        // the client outlines every item in near-black
        BufferedImage out = new BufferedImage(36, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++)
        {
            for (int x = 0; x < 36; x++)
            {
                int p = img.getRGB(x, y);
                if ((p >>> 24) != 0)
                {
                    out.setRGB(x, y, p | 0xff000000);
                    continue;
                }
                boolean edge = false;
                for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
                {
                    int nx = x + d[0], ny = y + d[1];
                    if (nx >= 0 && ny >= 0 && nx < 36 && ny < 32 && (img.getRGB(nx, ny) >>> 24) != 0) edge = true;
                }
                if (edge) out.setRGB(x, y, 0xff120e0c);
            }
        }
        return out;
    }

    /** A stand-in for an icon an organizer uploaded on the website: a smooth 96x96 picture, not pixel art. */
    private static BufferedImage uploadedIcon()
    {
        BufferedImage img = new BufferedImage(96, 96, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 8, new Color(0x9fe8ff), 0, 88, new Color(0x2a6fb8)));
        g.fillPolygon(new int[] {48, 86, 78, 48, 18, 10}, new int[] {6, 20, 66, 90, 66, 20}, 6);
        g.setColor(new Color(255, 255, 255, 200));
        g.setStroke(new java.awt.BasicStroke(5f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        g.drawPolyline(new int[] {34, 48, 62}, new int[] {34, 60, 34}, 3);
        g.setColor(new Color(0x123a66));
        g.setStroke(new java.awt.BasicStroke(3f));
        g.drawPolygon(new int[] {48, 86, 78, 48, 18, 10}, new int[] {6, 20, 66, 90, 66, 20}, 6);
        g.dispose();
        return img;
    }

    private static Client client(int w, int h)
    {
        return (Client) Proxy.newProxyInstance(BoardOverlayPreview.class.getClassLoader(), new Class<?>[] {Client.class}, (proxy, m, a) -> {
            switch (m.getName())
            {
                case "getCanvasWidth": return w;
                case "getCanvasHeight": return h;
                case "hashCode": return 1;
                case "equals": return proxy == a[0];
                case "toString": return "preview client";
                default:
                    Class<?> r = m.getReturnType();
                    if (r == boolean.class) return false;
                    if (r == int.class) return 0;
                    if (r == long.class) return 0L;
                    if (r == float.class) return 0f;
                    if (r == double.class) return 0d;
                    if (r == short.class) return (short) 0;
                    if (r == byte.class) return (byte) 0;
                    if (r == char.class) return (char) 0;
                    return null;
            }
        });
    }

    private static BoardOverlay overlay(int w, int h)
    {
        Fake fake = new Fake();
        BoardOverlay o = new BoardOverlay(client(w, h), null, new ForgeBingoConfig() { }, fake);
        PLUGINS.clear();
        PLUGINS.put(o, fake);
        o.clock = () -> now;
        return o;
    }

    /** Something game-like behind the overlay: grass, a path, water, and the fixed-mode interface on the right and below. */
    private static void paintGame(Graphics2D g, int w, int h)
    {
        g.setPaint(new GradientPaint(0, 0, new Color(0x4c6b2d), 0, h, new Color(0x2f4a20)));
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x7a6a48));
        g.fillPolygon(new int[] {(int) (w * 0.1), (int) (w * 0.22), (int) (w * 0.7), (int) (w * 0.55)}, new int[] {h, h, 0, 0}, 4);
        g.setColor(new Color(0x2c5f86));
        g.fillOval((int) (w * 0.05), (int) (h * 0.08), (int) (w * 0.3), (int) (h * 0.3));
        java.util.Random rng = new java.util.Random(4);
        for (int i = 0; i < 40; i++)
        {
            int x = rng.nextInt(w), y = rng.nextInt(h), s = (int) (h * (0.03 + rng.nextDouble() * 0.05));
            g.setColor(new Color(0x1f3d1a));
            g.fillOval(x, y, s, s);
            g.setColor(new Color(0x3d7a2c));
            g.fillOval(x + s / 6, y + s / 8, s * 2 / 3, s * 2 / 3);
        }
        int side = (int) (h * 0.48), chatH = (int) (h * 0.28);
        g.setColor(new Color(0x3e3529));
        g.fillRect(w - side, 0, side, h);
        g.setColor(new Color(0x5b4f3c));
        g.fillOval(w - side + side / 8, side / 20, side * 3 / 4, side * 3 / 5);
        g.setColor(new Color(0x2a2319));
        g.fillRect(w - side + side / 10, (int) (h * 0.42), side * 4 / 5, (int) (h * 0.5));
        g.setColor(new Color(0xc8b592));
        g.fillRect(0, h - chatH, (int) (w * 0.68) - side / 4, chatH);
        g.setColor(new Color(0x6b5d45));
        g.drawRect(0, h - chatH, (int) (w * 0.68) - side / 4 - 1, chatH - 1);
    }

    private static BufferedImage frame(BoardOverlay o, int w, int h)
    {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        paintGame(g, w, h);
        g.dispose();
        g = img.createGraphics();
        o.render(g);
        g.dispose();
        return img;
    }

    public static void main(String[] args) throws Exception
    {
        File out = new File(args.length > 0 ? args[0] : "build/preview");
        String mode = args.length > 1 ? args[1] : "after";
        // a theme's name as the mode ("blood", "xeric", ...) draws the board in that theme
        for (PanelTheme t : PanelTheme.values()) if (t.name().equalsIgnoreCase(mode)) ForgeBingoPanel.useTheme(t);
        out.mkdirs();
        int[][] sizes = {{765, 503}, {1280, 720}, {1920, 1080}};
        for (int[] s : sizes)
        {
            BoardOverlay o = overlay(s[0], s[1]);
            o.open();
            now += 10_000;
            long t0 = System.nanoTime();
            BufferedImage img = frame(o, s[0], s[1]);
            long first = System.nanoTime() - t0;
            ImageIO.write(img, "png", new File(out, "board-" + mode + "-" + s[0] + "x" + s[1] + ".png"));
            // how long a settled frame takes once everything is cached
            int runs = 200;
            t0 = System.nanoTime();
            for (int i = 0; i < runs; i++)
            {
                now += 16;
                frame(o, s[0], s[1]);
            }
            long game = System.nanoTime();
            for (int i = 0; i < runs; i++)
            {
                BufferedImage b = new BufferedImage(s[0], s[1], BufferedImage.TYPE_INT_RGB);
                Graphics2D g = b.createGraphics();
                paintGame(g, s[0], s[1]);
                g.dispose();
            }
            long bg = System.nanoTime() - game;
            System.out.printf("%dx%d: first frame %.1f ms, settled overlay frame %.2f ms%n", s[0], s[1], first / 1e6,
                ((game - t0) - bg) / 1e6 / runs);
        }
        if (!"before".equals(mode))
        {
            int w = 1280, h = 720;
            BoardOverlay o = overlay(w, h);
            o.open();
            now += 10_000;
            frame(o, w, h);
            BoardLayout l = o.layout();
            Rectangle c = l.cell(3, 1);
            o.hover((int) c.getCenterX(), (int) c.getCenterY());
            now += 1000;
            ImageIO.write(frame(o, w, h), "png", new File(out, "board-" + mode + "-hover-1280x720.png"));

            o = overlay(w, h);
            o.open();
            now += 10_000;
            frame(o, w, h);
            c = l.cell(0, 2);
            o.click((int) c.getCenterX(), (int) c.getCenterY());
            now += 1000;
            ImageIO.write(frame(o, w, h), "png", new File(out, "board-" + mode + "-card-1280x720.png"));

            animation(out, w, h, OpeningAnim.byName("Diagonal Deal"), "board-opening.gif", true);
            animation(out, w, h, OpeningAnim.byName("The Vortex"), "board-opening-legendary.gif", false);
        }
        if ("check".equals(mode)) check(out);
        System.out.println("Wrote board previews to " + out.getAbsolutePath());
        System.exit(0);
    }

    /**
     * Odd cases, to see that nothing breaks or looks wrong away from the usual 5x5: tiny and huge boards, no board
     * at all, the overlay half faded, and one frame each of an Epic and a Legendary opening.
     */
    private static void check(File out) throws Exception
    {
        int[][] cases = {{765, 503, 1}, {765, 503, 3}, {765, 503, 10}, {765, 503, 20}, {1920, 1080, 10}, {1920, 1080, 20}, {2560, 1440, 7}, {320, 240, 5}};
        for (int[] c : cases)
        {
            BoardOverlay o = overlay(c[0], c[1]);
            ((Fake) plugin(o)).board = grid(c[2]);
            o.open();
            now += 10_000;
            frame(o, c[0], c[1]);
            BoardLayout l = o.layout();
            Rectangle cell = l.cell(c[2] / 2, c[2] / 2);
            o.hover((int) cell.getCenterX(), (int) cell.getCenterY());
            now += 1000;
            ImageIO.write(frame(o, c[0], c[1]), "png", new File(out, "board-check-" + c[0] + "x" + c[1] + "-" + c[2] + ".png"));
            o.click((int) cell.getCenterX(), (int) cell.getCenterY());
            now += 100;
            frame(o, c[0], c[1]);
            now += 1000;
            if (c[2] == 3 || c[2] == 7) ImageIO.write(frame(o, c[0], c[1]), "png", new File(out, "board-check-card-" + c[0] + "x" + c[1] + "-" + c[2] + ".png"));
            o.close();
            now += 80;
            frame(o, c[0], c[1]);
        }
        BoardOverlay none = overlay(765, 503);
        ((Fake) plugin(none)).board = null;
        none.open();
        now += 5000;
        ImageIO.write(frame(none, 765, 503), "png", new File(out, "board-check-noboard.png"));
        int k = 0;
        for (OpeningAnim.Spec spec : new OpeningAnim.Spec[] {OpeningAnim.byName("The Vortex"), OpeningAnim.byName("Whirling Heart"),
            OpeningAnim.byName("Card Flip Rows"), OpeningAnim.byName("Raindrops")})
        {
            Rectangle all = new Rectangle(0, 0, 1280, 720);
            ImageIO.write(render(1280, 720, spec, now + 100_000, 1500, all), "png", new File(out, "board-check-anim-" + (++k) + "-" + spec.rarity.label + ".png"));
            ImageIO.write(render(1280, 720, spec, now + 100_000, 2800, all), "png", new File(out, "board-check-anim-" + k + "-end.png"));
        }
        // every animation, start to finish, must draw without complaint
        for (OpeningAnim.Spec spec : OpeningAnim.ALL)
        {
            for (int t = 0; t <= 3100; t += 155) render(640, 480, spec, now + 200_000, t, new Rectangle(0, 0, 640, 480));
        }
        // the card popping open, part way
        BoardOverlay o = overlay(1280, 720);
        o.open();
        now += 10_000;
        frame(o, 1280, 720);
        Rectangle cell = o.layout().cell(0, 2);
        o.click((int) cell.getCenterX(), (int) cell.getCenterY());
        now += 70;
        ImageIO.write(frame(o, 1280, 720), "png", new File(out, "board-check-card-pop.png"));
    }

    private static final java.util.Map<BoardOverlay, ForgeBingoPlugin> PLUGINS = new java.util.IdentityHashMap<>();

    private static ForgeBingoPlugin plugin(BoardOverlay o)
    {
        return PLUGINS.get(o);
    }

    /** An n x n board with every kind of tile scattered over it. */
    private static ApiModels.BoardResponse grid(int n)
    {
        StringBuilder sb = new StringBuilder("{'teamBoardId':'b1','teamName':'The Extremely Long Named Team Of Goblin Slayers And Friends',"
            + "'event':{'id':'e1','name':'A bingo with a very long name indeed, the longest name in Gielinor','status':'live','active':true},"
            + "'board':{'id':'bd','title':'Board','size':" + n + "},'tiles':[");
        for (int r = 0; r < n; r++)
        {
            for (int c = 0; c < n; c++)
            {
                int k = (r * 7 + c * 3) % 9;
                String rest = k == 0 ? "'completed':true,'tasks':[" + doneTask("d", "Done") + "]"
                    : k == 1 ? "'tasks':[" + task("a", "Kill 10 things for the team, a long task text that will need wrapping over lines", 10, "kc", 4, 0) + "]"
                    : k == 2 ? "'tasks':[" + task("a", "Waiting", 1, "manual", 0, 1) + "]"
                    : k == 3 ? "'workingOn':[{'name':'Zezima','me':false}],'tasks':[" + task("a", "One", 1, "npc", 0, 0) + "," + task("b", "Two", 3, "npc", 1, 0) + ","
                        + task("c", "Three", 1, "manual", 0, 0) + "," + task("e", "Four", 1, "manual", 0, 0) + "," + task("f", "Five", 5, "npc", 5, 0) + "]"
                    : "'tasks':[" + task("a", "Not started", 1, "npc", 0, 0) + "]";
                if (k == 4) sb.append("{'id':'t").append(r).append('_').append(c).append("','row':").append(r).append(",'col':").append(c)
                    .append(",'title':'?','secret':true,'points':1,'completionMode':'all','tasks':[]},");
                else sb.append(tile(r, c, k == 5 ? "No icon here" : "Tile " + r + " " + c + " with quite a long name", k == 5 ? null : 1000 + r * 31 + c, 2, rest)
                    .replace("'id':'t" + r + c + "'", "'id':'t" + r + "_" + c + "'"));
            }
        }
        sb.append("{}]}");
        ApiModels.BoardResponse b = new Gson().fromJson(sb.toString(), ApiModels.BoardResponse.class);
        b.tiles.remove(b.tiles.size() - 1);
        return b;
    }

    /**
     * One overlay playing this opening on a boardSize x boardSize board in a w x h client, drawn at each of the given
     * times (ms since it opened). Each frame goes to sink (which may be null: the opening tests only need it to draw).
     */
    static void play(int w, int h, int boardSize, OpeningAnim.Spec spec, java.util.function.BiConsumer<Integer, BufferedImage> sink, int... times)
    {
        BoardOverlay o = overlay(w, h);
        if (boardSize != 5) ((Fake) plugin(o)).board = grid(boardSize);
        long start = now + 100_000;
        now = start;
        o.open();
        o.opening = spec;
        for (int t : times)
        {
            now = start + t;
            BufferedImage img = frame(o, w, h);
            if (sink != null) sink.accept(t, img);
        }
    }

    /** The board's rectangle in a w x h client with an n x n board. */
    static Rectangle boardRect(int w, int h, int n)
    {
        return BoardLayout.compute(w, h, n).board();
    }

    // ---------- the opening animation as a GIF ----------

    /** ~30 fps: GIF delays are whole hundredths of a second, so the frames take turns at 3, 3 and 4 */
    private static final int[] DELAYS = {3, 3, 4};

    private static void animation(File out, int w, int h, OpeningAnim.Spec spec, String name, boolean stills) throws Exception
    {
        animation(out, w, h, java.util.Collections.singletonList(spec), name, stills);
    }

    /** One GIF playing these openings one after another, each held for a moment once finished. */
    static void animation(File out, int w, int h, List<OpeningAnim.Spec> specs, String name, boolean stills) throws Exception
    {
        BoardLayout l = BoardLayout.compute(w, h, 5);
        Rectangle b = l.board();
        int margin = 28;
        Rectangle crop = new Rectangle(b.x - margin, b.y - margin, b.width + 2 * margin, b.height + 2 * margin).intersection(new Rectangle(0, 0, w, h));
        // frame times (ms since the board opened): 3.2 s of animation per opening
        List<Integer> times = new ArrayList<>();
        List<Integer> delays = new ArrayList<>();
        List<OpeningAnim.Spec> whose = new ArrayList<>();
        for (OpeningAnim.Spec sp : specs)
        {
            int t = 0;
            for (int i = 0; t <= 3200; i++)
            {
                times.add(t);
                delays.add(DELAYS[i % 3]);
                whose.add(sp);
                t += DELAYS[i % 3] * 10;
            }
            delays.set(delays.size() - 1, 160); // hold the finished board before the next one (or the loop)
        }
        long start = now + 60_000;
        // pass 1: the colours in use (every third frame is plenty)
        int[] hist = new int[32768];
        for (int i = 0; i < times.size(); i += 3) count(hist, render(w, h, whose.get(i), start, times.get(i), crop));
        count(hist, render(w, h, whose.get(times.size() - 1), start, times.get(times.size() - 1), crop));
        int[] palette = medianCut(hist, 255);
        byte[] lut = new byte[32768];
        boolean[] known = new boolean[32768];
        byte[] r = new byte[256], g = new byte[256], bl = new byte[256];
        for (int i = 0; i < palette.length; i++)
        {
            r[i] = (byte) (palette[i] >> 16);
            g[i] = (byte) (palette[i] >> 8);
            bl[i] = (byte) palette[i];
        }
        IndexColorModel icm = new IndexColorModel(8, 256, r, g, bl, 255);

        File file = new File(out, name);
        file.delete();
        ImageWriter writer = ImageIO.getImageWritersBySuffix("gif").next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(file))
        {
            writer.setOutput(ios);
            // one colour table for the whole file (without this the writer falls back to its stock web palette)
            IIOMetadata stream = writer.getDefaultStreamMetadata(null);
            String sfmt = stream.getNativeMetadataFormatName();
            IIOMetadataNode sroot = (IIOMetadataNode) stream.getAsTree(sfmt);
            IIOMetadataNode screen = child(sroot, "LogicalScreenDescriptor");
            screen.setAttribute("logicalScreenWidth", Integer.toString(crop.width));
            screen.setAttribute("logicalScreenHeight", Integer.toString(crop.height));
            screen.setAttribute("colorResolution", "8");
            screen.setAttribute("pixelAspectRatio", "0");
            IIOMetadataNode old = child(sroot, "GlobalColorTable");
            sroot.removeChild(old);
            IIOMetadataNode table = new IIOMetadataNode("GlobalColorTable");
            table.setAttribute("sizeOfGlobalColorTable", "256");
            table.setAttribute("backgroundColorIndex", "0");
            table.setAttribute("sortFlag", "FALSE");
            for (int i = 0; i < 256; i++)
            {
                IIOMetadataNode e = new IIOMetadataNode("ColorTableEntry");
                e.setAttribute("index", Integer.toString(i));
                e.setAttribute("red", Integer.toString(r[i] & 255));
                e.setAttribute("green", Integer.toString(g[i] & 255));
                e.setAttribute("blue", Integer.toString(bl[i] & 255));
                table.appendChild(e);
            }
            sroot.appendChild(table);
            stream.setFromTree(sfmt, sroot);
            writer.prepareWriteSequence(stream);
            byte[] prev = null;
            int still = 0;
            for (int i = 0; i < times.size(); i++)
            {
                BufferedImage rgb = render(w, h, whose.get(i), start, times.get(i), crop);
                if (stills && (times.get(i) == 200 || times.get(i) == 600 || times.get(i) == 1200 || times.get(i) == 2000 || times.get(i) == 2600))
                {
                    ImageIO.write(rgb, "png", new File(out, "board-anim-frame-" + (++still) + ".png"));
                }
                int cw = crop.width, ch = crop.height;
                byte[] idx = new byte[cw * ch];
                int[] px = rgb.getRGB(0, 0, cw, ch, null, 0, cw);
                for (int k = 0; k < px.length; k++)
                {
                    int c15 = ((px[k] >> 9) & 0x7c00) | ((px[k] >> 6) & 0x3e0) | ((px[k] >> 3) & 0x1f);
                    if (!known[c15])
                    {
                        lut[c15] = (byte) nearest(palette, c15);
                        known[c15] = true;
                    }
                    idx[k] = lut[c15];
                }
                // only the part that changed since the last frame is stored; unchanged pixels inside it are see-through
                int x0 = 0, y0 = 0, x1 = cw - 1, y1 = ch - 1;
                boolean transparent = false;
                byte[] data = idx;
                if (prev != null)
                {
                    x0 = cw; y0 = ch; x1 = -1; y1 = -1;
                    for (int y = 0; y < ch; y++)
                    {
                        for (int x = 0; x < cw; x++)
                        {
                            if (idx[y * cw + x] != prev[y * cw + x])
                            {
                                if (x < x0) x0 = x;
                                if (x > x1) x1 = x;
                                if (y < y0) y0 = y;
                                if (y > y1) y1 = y;
                            }
                        }
                    }
                    if (x1 < 0) { x0 = 0; y0 = 0; x1 = 0; y1 = 0; }
                    int sw = x1 - x0 + 1, sh = y1 - y0 + 1;
                    data = new byte[sw * sh];
                    for (int y = 0; y < sh; y++)
                    {
                        for (int x = 0; x < sw; x++)
                        {
                            int at = (y + y0) * cw + x + x0;
                            data[y * sw + x] = idx[at] == prev[at] ? (byte) 255 : idx[at];
                        }
                    }
                    transparent = true;
                }
                int sw = x1 - x0 + 1, sh = y1 - y0 + 1;
                BufferedImage part = new BufferedImage(sw, sh, BufferedImage.TYPE_BYTE_INDEXED, icm);
                part.getRaster().setDataElements(0, 0, sw, sh, data);
                IIOMetadata md = writer.getDefaultImageMetadata(new ImageTypeSpecifier(part), null);
                String fmt = md.getNativeMetadataFormatName();
                IIOMetadataNode root = (IIOMetadataNode) md.getAsTree(fmt);
                IIOMetadataNode gce = child(root, "GraphicControlExtension");
                gce.setAttribute("disposalMethod", "doNotDispose");
                gce.setAttribute("userInputFlag", "FALSE");
                gce.setAttribute("transparentColorFlag", transparent ? "TRUE" : "FALSE");
                gce.setAttribute("delayTime", Integer.toString(delays.get(i)));
                gce.setAttribute("transparentColorIndex", transparent ? "255" : "0");
                IIOMetadataNode desc = child(root, "ImageDescriptor");
                desc.setAttribute("imageLeftPosition", Integer.toString(x0));
                desc.setAttribute("imageTopPosition", Integer.toString(y0));
                desc.setAttribute("imageWidth", Integer.toString(sw));
                desc.setAttribute("imageHeight", Integer.toString(sh));
                desc.setAttribute("interlaceFlag", "FALSE");
                if (i == 0)
                {
                    IIOMetadataNode apps = child(root, "ApplicationExtensions");
                    IIOMetadataNode app = new IIOMetadataNode("ApplicationExtension");
                    app.setAttribute("applicationID", "NETSCAPE");
                    app.setAttribute("authenticationCode", "2.0");
                    app.setUserObject(new byte[] {1, 0, 0}); // loop forever
                    apps.appendChild(app);
                }
                md.setFromTree(fmt, root);
                writer.writeToSequence(new IIOImage(part, null, md), null);
                prev = idx;
            }
            writer.endWriteSequence();
        }
        writer.dispose();
        System.out.println(name + ": " + specs + ", " + times.size() + " frames, " + crop.width + "x" + crop.height
            + ", " + file.length() / 1024 + " KB");
    }

    /** One frame of the opening animation, sinceOpen ms after the board opened, cropped to the board. */
    private static BufferedImage render(int w, int h, OpeningAnim.Spec spec, long start, int sinceOpen, Rectangle crop)
    {
        now = start;
        BoardOverlay o = overlay(w, h);
        o.open();
        o.opening = spec;
        now = start + sinceOpen;
        BufferedImage full = frame(o, w, h);
        BufferedImage part = new BufferedImage(crop.width, crop.height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = part.createGraphics();
        g.drawImage(full, -crop.x, -crop.y, null);
        g.dispose();
        return part;
    }

    private static IIOMetadataNode child(IIOMetadataNode root, String name)
    {
        for (Node n = root.getFirstChild(); n != null; n = n.getNextSibling())
        {
            if (name.equals(n.getNodeName())) return (IIOMetadataNode) n;
        }
        IIOMetadataNode made = new IIOMetadataNode(name);
        root.appendChild(made);
        return made;
    }

    private static void count(int[] hist, BufferedImage img)
    {
        int[] px = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
        for (int p : px) hist[((p >> 9) & 0x7c00) | ((p >> 6) & 0x3e0) | ((p >> 3) & 0x1f)]++;
    }

    /** Median cut over a 15-bit colour histogram: the n colours that cover the animation best, as 0xRRGGBB. */
    private static int[] medianCut(int[] hist, int n)
    {
        List<int[]> boxes = new ArrayList<>();
        int used = 0;
        for (int c : hist) if (c > 0) used++;
        int[] all = new int[used];
        int k = 0;
        for (int i = 0; i < hist.length; i++) if (hist[i] > 0) all[k++] = i;
        boxes.add(all);
        while (boxes.size() < n)
        {
            int best = -1, bestAxis = 0;
            double bestScore = 0;
            for (int i = 0; i < boxes.size(); i++)
            {
                int[] box = boxes.get(i);
                if (box.length < 2) continue;
                int[] lo = {31, 31, 31}, hi = {0, 0, 0};
                long pop = 0;
                for (int c : box)
                {
                    pop += hist[c];
                    for (int a = 0; a < 3; a++)
                    {
                        int v = (c >> (10 - 5 * a)) & 31;
                        lo[a] = Math.min(lo[a], v);
                        hi[a] = Math.max(hi[a], v);
                    }
                }
                int axis = 0;
                for (int a = 1; a < 3; a++) if (hi[a] - lo[a] > hi[axis] - lo[axis]) axis = a;
                double score = (hi[axis] - lo[axis] + 1) * Math.sqrt(pop);
                if (score > bestScore)
                {
                    bestScore = score;
                    best = i;
                    bestAxis = axis;
                }
            }
            if (best < 0) break;
            int[] box = boxes.remove(best);
            int shift = 10 - 5 * bestAxis;
            long[] keyed = new long[box.length];
            long pop = 0;
            for (int i = 0; i < box.length; i++)
            {
                keyed[i] = ((long) ((box[i] >> shift) & 31) << 32) | box[i];
                pop += hist[box[i]];
            }
            Arrays.sort(keyed);
            long acc = 0;
            int cut = 1;
            for (int i = 0; i < keyed.length - 1; i++)
            {
                acc += hist[(int) keyed[i]];
                cut = i + 1;
                if (acc * 2 >= pop) break;
            }
            int[] a = new int[cut], b = new int[keyed.length - cut];
            for (int i = 0; i < keyed.length; i++)
            {
                if (i < cut) a[i] = (int) keyed[i]; else b[i - cut] = (int) keyed[i];
            }
            boxes.add(a);
            boxes.add(b);
        }
        int[] out = new int[boxes.size()];
        for (int i = 0; i < out.length; i++)
        {
            long r = 0, g = 0, b = 0, pop = 0;
            for (int c : boxes.get(i))
            {
                int p = hist[c];
                r += (long) (((c >> 10) & 31) * 8 + 4) * p;
                g += (long) (((c >> 5) & 31) * 8 + 4) * p;
                b += (long) ((c & 31) * 8 + 4) * p;
                pop += p;
            }
            pop = Math.max(1, pop);
            out[i] = (int) (r / pop) << 16 | (int) (g / pop) << 8 | (int) (b / pop);
        }
        return out;
    }

    private static int nearest(int[] palette, int c15)
    {
        int r = ((c15 >> 10) & 31) * 8 + 4, g = ((c15 >> 5) & 31) * 8 + 4, b = (c15 & 31) * 8 + 4;
        int best = 0, bestD = Integer.MAX_VALUE;
        for (int i = 0; i < palette.length; i++)
        {
            int dr = r - ((palette[i] >> 16) & 255), dg = g - ((palette[i] >> 8) & 255), db = b - (palette[i] & 255);
            int d = dr * dr * 2 + dg * dg * 4 + db * db;
            if (d < bestD)
            {
                bestD = d;
                best = i;
            }
        }
        return best;
    }
}
