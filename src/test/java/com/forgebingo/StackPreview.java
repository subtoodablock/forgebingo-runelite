package com.forgebingo;

import com.forgebingo.core.ApiClient;
import com.forgebingo.core.ScreenshotEncode;
import com.forgebingo.core.ScreenshotStack;
import com.forgebingo.core.ScreenshotStamp;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.util.Arrays;

/** Renders a sample of two proof pictures stacked into one upload. Not a test; writes build/preview/proof-stacked.jpg. */
public final class StackPreview
{
    private StackPreview()
    {
    }

    public static void main(String[] args) throws Exception
    {
        File out = new File(args.length > 0 ? args[0] : "build/preview");
        out.mkdirs();
        ZonedDateTime t = ZonedDateTime.now();
        BufferedImage a = ScreenshotStamp.stamp(frame("Stand-in game picture: 0.3 s after the kill (loot on the ground)", new Color(0x2f3b25)), "ANVIL", t, "Example Player", "Zulrah kills (1 of 2)");
        BufferedImage b = ScreenshotStamp.stamp(frame("Stand-in game picture: 1.5 s after the kill", new Color(0x26323a)), "ANVIL", t.plusNanos(1_200_000_000L), "Example Player", "Zulrah kills (2 of 2)");
        byte[] jpg = ScreenshotEncode.jpeg(ScreenshotStack.vertical(Arrays.asList(a, b)), ApiClient.MAX_SCREENSHOT_BYTES);
        File f = new File(out, "proof-stacked.jpg");
        Files.write(f.toPath(), jpg);
        System.out.println("Wrote " + f.getAbsolutePath() + " (" + jpg.length / 1024 + " KB)");
    }

    private static BufferedImage frame(String text, Color bg)
    {
        BufferedImage img = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(bg);
        g.fillRect(0, 0, 1280, 720);
        g.setColor(new Color(255, 255, 255, 200));
        g.setFont(new Font("SansSerif", Font.PLAIN, 26));
        g.drawString(text, 60, 370);
        g.dispose();
        return img;
    }
}
