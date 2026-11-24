package com.forgebingo.core;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fixed pixel art that ships as a picture instead of as drawing code.
 *
 * A sheet is two files bundled in the jar under /com/forgebingo/sprites/ (nothing is ever downloaded):
 *   NAME.png  every picture packed into one lossless 32-bit ARGB image
 *   NAME.txt  one line per picture: "KEY x y w h" (where it sits in the png); lines that are not five words
 *             (blank lines, the "# ..." header) are skipped. Keys have no spaces and do not depend on enum order.
 *
 * Both files are WRITTEN BY A BAKER in src/test/java (SheetBaker and e.g. PetSheetBaker) from the original drawing
 * code, which lives in src/test/java too and stays the source of truth for the art. A test (e.g. PetSpritesBakedTest)
 * compares every baked picture with the drawing code pixel for pixel, so the two cannot drift apart unnoticed.
 *
 * The sheet is read once, on first use; every picture is copied into its own TYPE_INT_ARGB image (the same type,
 * size and pixels the drawing code produced) and the same instance is handed out on every call. If the files are
 * missing or unreadable this logs once and every get() returns null ("no art"); it never throws into a paint loop.
 * Safe to call from any thread.
 */
public final class SpriteSheet
{
    private static final Logger log = LoggerFactory.getLogger(SpriteSheet.class);

    private final String name;
    private Map<String, BufferedImage> cells;

    public SpriteSheet(String name)
    {
        this.name = name;
    }

    /** The picture baked under this key, or null when there is none. */
    public synchronized BufferedImage get(String key)
    {
        if (cells == null) cells = load();
        return cells.get(key);
    }

    private Map<String, BufferedImage> load()
    {
        Map<String, BufferedImage> out = new HashMap<>();
        String base = "/com/forgebingo/sprites/" + name;
        try (InputStream png = SpriteSheet.class.getResourceAsStream(base + ".png");
             InputStream txt = SpriteSheet.class.getResourceAsStream(base + ".txt"))
        {
            BufferedImage sheet;
            synchronized (ImageIO.class) // ImageIO is not thread-safe; RuneLite's own loader takes the same lock
            {
                sheet = ImageIO.read(png);
            }
            for (String line : new String(txt.readAllBytes(), StandardCharsets.UTF_8).split("\n"))
            {
                String[] p = line.trim().split(" ");
                if (p.length != 5) continue;
                int w = Integer.parseInt(p[3]);
                int h = Integer.parseInt(p[4]);
                BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                img.setRGB(0, 0, w, h, sheet.getRGB(Integer.parseInt(p[1]), Integer.parseInt(p[2]), w, h, null, 0, w), 0, w);
                out.put(p[0], img);
            }
        }
        catch (Exception e) // a missing file shows up here as a null stream (IllegalArgument / NullPointer)
        {
            log.warn("ForgeBingo: sprite sheet '{}' could not be loaded; its art is hidden", name, e);
            out.clear();
        }
        return out;
    }
}
