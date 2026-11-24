package com.forgebingo.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Bakes pictures drawn by code into a sprite sheet that the plugin's SpriteSheet reads back (NAME.png + NAME.txt in
 * src/main/resources/com/forgebingo/sprites), and checks a baked sheet against the drawing code. Shared by every
 * *SheetBaker / *BakedTest pair. Run from the plugin directory.
 */
public final class SheetBaker
{
    /** Where the sheets live, relative to the plugin directory (Gradle's working directory for tests and mains). */
    public static final File DIR = new File("src/main/resources/com/forgebingo/sprites");

    /** Set this environment variable (to anything) to make the *BakedTest tests re-bake instead of compare. */
    public static final String BAKE_ENV = "FORGE_BAKE";

    private SheetBaker()
    {
    }

    public static boolean bakeRequested()
    {
        String v = System.getenv(BAKE_ENV);
        return v != null && !v.isEmpty();
    }

    /**
     * Writes NAME.png and NAME.txt. Pictures are packed left to right, {@code cols} per row, in the map's order, in
     * cells the size of the biggest picture; null pictures are left out (so the loader returns null for them too).
     * Keys must not contain spaces.
     */
    public static void bake(String name, Map<String, BufferedImage> images, int cols) throws IOException
    {
        int cw = 1;
        int ch = 1;
        int n = 0;
        for (BufferedImage img : images.values())
        {
            if (img == null) continue;
            cw = Math.max(cw, img.getWidth());
            ch = Math.max(ch, img.getHeight());
            n++;
        }
        int rows = Math.max(1, (n + cols - 1) / cols);
        BufferedImage sheet = new BufferedImage(cw * cols, ch * rows, BufferedImage.TYPE_INT_ARGB);
        StringBuilder index = new StringBuilder();
        index.append("# ").append(name).append(": ").append(n).append(" pictures, cell ").append(cw).append('x').append(ch)
            .append(", ").append(cols).append(" per row. Written by SheetBaker - do not edit by hand.\n");
        index.append("# KEY x y w h\n");
        int i = 0;
        for (Map.Entry<String, BufferedImage> e : images.entrySet())
        {
            BufferedImage img = e.getValue();
            if (img == null) continue;
            if (e.getKey().indexOf(' ') >= 0) throw new IllegalArgumentException("key with a space: " + e.getKey());
            int x = (i % cols) * cw;
            int y = (i / cols) * ch;
            int w = img.getWidth();
            int h = img.getHeight();
            sheet.setRGB(x, y, w, h, img.getRGB(0, 0, w, h, null, 0, w), 0, w);
            index.append(e.getKey()).append(' ').append(x).append(' ').append(y).append(' ').append(w).append(' ').append(h).append('\n');
            i++;
        }
        Files.createDirectories(DIR.toPath());
        File png = new File(DIR, name + ".png");
        if (!ImageIO.write(sheet, "png", png)) throw new IOException("no PNG writer");
        Files.write(new File(DIR, name + ".txt").toPath(), index.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("baked " + n + " pictures into " + png.getPath() + " (" + sheet.getWidth() + "x" + sheet.getHeight()
            + ", " + png.length() + " bytes)");
    }

    /**
     * Asserts the baked picture is exactly the drawn one: both null, or the same type, size and every ARGB pixel.
     * {@code rebake} is the command to print when they differ.
     */
    public static void assertSame(String key, BufferedImage drawn, BufferedImage baked, String rebake)
    {
        String hint = " - the art code and the baked sheet differ. If the art was changed on purpose, re-bake: " + rebake;
        if (drawn == null)
        {
            assertNull(key + " is not drawn by the art code but is in the baked sheet" + hint, baked);
            return;
        }
        assertNotNull(key + " is drawn by the art code but missing from the baked sheet" + hint, baked);
        assertEquals(key + " image type" + hint, drawn.getType(), baked.getType());
        assertEquals(key + " width" + hint, drawn.getWidth(), baked.getWidth());
        assertEquals(key + " height" + hint, drawn.getHeight(), baked.getHeight());
        for (int y = 0; y < drawn.getHeight(); y++)
        {
            for (int x = 0; x < drawn.getWidth(); x++)
            {
                int a = drawn.getRGB(x, y);
                int b = baked.getRGB(x, y);
                if (a != b)
                {
                    fail(key + " pixel (" + x + "," + y + ") drawn " + Integer.toHexString(a) + " baked " + Integer.toHexString(b) + hint);
                }
            }
        }
    }
}
