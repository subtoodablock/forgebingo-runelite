package com.forgebingo.core;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Screenshots go up as JPEG: a full game window as PNG is several MB and timed out on ordinary home upload speeds
 * (owner report 2026-11-12, "SocketTimeoutException"). JPEG at 85% is roughly a tenth of that and still reads fine
 * for proof. Falls back to lower quality if a shot is still over the limit.
 */
public final class ScreenshotEncode
{
    private ScreenshotEncode()
    {
    }

    /** JPEG bytes no bigger than maxBytes (tries 85%, 70%, 55%), or null if the image cannot be encoded. */
    public static byte[] jpeg(BufferedImage img, int maxBytes) throws IOException
    {
        BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try
        {
            g.setColor(Color.BLACK); // JPEG has no transparency
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(img, 0, 0, null);
        }
        finally
        {
            g.dispose();
        }
        byte[] out = null;
        for (float q : new float[] {0.85f, 0.7f, 0.55f})
        {
            out = encode(rgb, q);
            if (out == null || out.length <= maxBytes) return out;
        }
        return out;
    }

    private static byte[] encode(BufferedImage rgb, float quality) throws IOException
    {
        Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName("jpeg");
        if (!it.hasNext()) return null;
        ImageWriter w = it.next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(bytes))
        {
            w.setOutput(ios);
            ImageWriteParam p = w.getDefaultWriteParam();
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(quality);
            w.write(null, new IIOImage(rgb, null, null), p);
        }
        finally
        {
            w.dispose();
        }
        return bytes.toByteArray();
    }

    /** True when the bytes are a JPEG (FF D8 FF), else they are treated as PNG. */
    public static boolean isJpeg(byte[] b)
    {
        return b != null && b.length > 3 && (b[0] & 0xff) == 0xff && (b[1] & 0xff) == 0xd8 && (b[2] & 0xff) == 0xff;
    }
}
