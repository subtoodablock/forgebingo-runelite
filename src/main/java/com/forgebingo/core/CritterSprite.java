package com.forgebingo.core;

import java.awt.image.BufferedImage;

/** One door to every creature's picture, whichever file draws it. Anything not drawn yet shows as the imp. */
public final class CritterSprite
{
    private CritterSprite()
    {
    }

    /** One walk frame (0 or 1) on the PixelArt.WIDTH x PixelArt.HEIGHT grid, facing left, feet on the bottom row. */
    public static BufferedImage image(Critter c, int variant, int frame)
    {
        BufferedImage img = null;
        // Chexy is drawn live by Chexy; this is just a still of it
        if (c == Critter.CHEXY) return Chexy.still();
        if (c != null && c != Critter.IMP)
        {
            int v = Math.floorMod(variant, c.variants);
            img = drawn(c, v, frame);
        }
        return img != null ? img : ImpSprite.image(ImpSprite.Hat.NONE, ImpSprite.Item.NONE, frame);
    }

    /** A small falling thing, or null when nobody draws it. */
    public static BufferedImage fall(Critter.Fall f, int variant)
    {
        return f == null ? null : drawnFall(f, variant);
    }

    // SPRITE-SOURCES-START
    private static BufferedImage drawn(Critter c, int variant, int frame)
    {
        BufferedImage img = RaidSprites.image(c, variant, frame);
        return img != null ? img : CryptSprites.image(c, variant, frame);
    }

    private static BufferedImage drawnFall(Critter.Fall f, int variant)
    {
        BufferedImage img = RaidSprites.fall(f, variant);
        return img != null ? img : CryptSprites.fall(f, variant);
    }
    // SPRITE-SOURCES-END
}
