package com.forgebingo;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Proxy;
import javax.imageio.ImageIO;
import net.runelite.api.Client;

/**
 * Renders the in-game celebration banners off screen, to check they match the side panel and the board. Not a test;
 * run its main (see PanelPreview for how). Writes build/preview/effect-*.png.
 */
public final class EffectsPreview
{
    private EffectsPreview()
    {
    }

    public static void main(String[] args) throws Exception
    {
        File out = new File(args.length > 0 ? args[0] : "build/preview");
        out.mkdirs();
        int[][] sizes = {{765, 503}, {1280, 720}, {1920, 1080}};
        for (int[] s : sizes)
        {
            shot(out, s[0], s[1], EffectsOverlay.Kind.TASK, "Mole claws from the Giant Mole", "task");
            shot(out, s[0], s[1], EffectsOverlay.Kind.TILE, "Scythe of vitur", "tile");
        }
        shot(out, 1280, 720, EffectsOverlay.Kind.LINE, "Row 3 complete", "line");
        shot(out, 1280, 720, EffectsOverlay.Kind.BLACKOUT, "", "blackout");
        System.out.println("Wrote effect previews to " + out.getAbsolutePath());
    }

    private static void shot(File out, int w, int h, EffectsOverlay.Kind kind, String title, String name) throws Exception
    {
        Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class}, (p, m, a) -> {
            if (m.getName().equals("getCanvasWidth")) return w;
            if (m.getName().equals("getCanvasHeight")) return h;
            Class<?> r = m.getReturnType();
            if (r == boolean.class) return false;
            if (r == int.class) return 0;
            if (r == long.class) return 0L;
            return null;
        });
        EffectsOverlay fx = new EffectsOverlay(client);
        fx.push(kind, title);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        fx.render(g); // starts the effect's clock
        g.dispose();
        Thread.sleep(kind == EffectsOverlay.Kind.BLACKOUT ? 2600 : 900);
        g = img.createGraphics();
        g.setColor(new Color(0x2f3b25));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(0x566b3d));
        g.fillRect(0, h * 2 / 3, w, h / 3);
        fx.render(g);
        g.dispose();
        ImageIO.write(img, "png", new File(out, "effect-" + name + "-" + w + "x" + h + ".png"));
    }
}
