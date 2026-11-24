package com.forgebingo.core;

import java.awt.image.BufferedImage;

/**
 * The creatures as DRAWN by code: CritterSprite exactly as it was before the art was baked into sprites/critters.png
 * (same variant wrapping, same imp fallback, same Chexy still). Source of truth for CritterSheetBaker and
 * CritterSpritesBakedTest.
 */
public final class CritterArt
{
    private CritterArt()
    {
    }

    public static BufferedImage image(Critter c, int variant, int frame)
    {
        BufferedImage img = null;
        if (c == Critter.CHEXY) return Chexy.still();
        if (c != null && c != Critter.IMP)
        {
            int v = Math.floorMod(variant, c.variants);
            img = drawn(c, v, frame);
        }
        return img != null ? img : ImpSprite.image(ImpSprite.Hat.NONE, ImpSprite.Item.NONE, frame);
    }

    public static BufferedImage fall(Critter.Fall f, int variant)
    {
        return f == null ? null : drawnFall(f, variant);
    }

    /** The creature's own picture, or null when neither file draws it (IMP, PET, CHEXY). */
    public static BufferedImage drawn(Critter c, int variant, int frame)
    {
        BufferedImage img = RaidSprites.image(c, variant, frame);
        return img != null ? img : CryptSprites.image(c, variant, frame);
    }

    public static BufferedImage drawnFall(Critter.Fall f, int variant)
    {
        BufferedImage img = RaidSprites.fall(f, variant);
        return img != null ? img : CryptSprites.fall(f, variant);
    }
}
