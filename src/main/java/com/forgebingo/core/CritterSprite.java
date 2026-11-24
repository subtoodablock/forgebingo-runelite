package com.forgebingo.core;

import java.awt.image.BufferedImage;

/**
 * One door to every creature's picture. Anything not drawn yet shows as the imp.
 *
 * The pictures are baked: they come from the bundled sheet sprites/critters.png + critters.txt (see SpriteSheet),
 * keyed "CRITTER|variant|frame" and "fall|FALL|variant". The code that draws them is RaidSprites and CryptSprites in
 * src/test/java (reached through CritterArt there, which is this class as it was before baking); after changing any
 * of it, re-bake with CritterSheetBaker, or CritterSpritesBakedTest fails.
 *
 * The variant and frame arithmetic below is exactly what the drawing code did with out-of-range values, so every
 * input gets the picture it always got.
 */
public final class CritterSprite
{
    private static final SpriteSheet SHEET = new SpriteSheet("critters");

    private CritterSprite()
    {
    }

    /** One walk frame (0 or 1) on the PixelArt.WIDTH x PixelArt.HEIGHT grid, facing left, feet on the bottom row. */
    public static BufferedImage image(Critter c, int variant, int frame)
    {
        // Chexy is drawn live by Chexy; this is just a still of it
        if (c == Critter.CHEXY) return Chexy.still();
        // the variant wraps; any frame but 0 is the second. IMP and PET are not on the sheet, so they fall through.
        BufferedImage img = c == null ? null
            : SHEET.get(c.name() + "|" + Math.floorMod(variant, c.variants) + (frame == 0 ? "|0" : "|1"));
        return img != null ? img : ImpSprite.image(ImpSprite.Hat.NONE, ImpSprite.Item.NONE, frame);
    }

    /** A small falling thing, or null when nobody draws it. */
    public static BufferedImage fall(Critter.Fall f, int variant)
    {
        if (f == null) return null;
        // bones and embers wrap between their two looks; blood drops and crystals are look 0 up to variant 0 and
        // look 1 above it; the Kephri bomb and the water drop have one look
        int v = f == Critter.Fall.BONE || f == Critter.Fall.EMBER ? Math.floorMod(variant, 2)
            : (f == Critter.Fall.BLOOD_DROP || f == Critter.Fall.CRYSTAL) && variant > 0 ? 1 : 0;
        return SHEET.get("fall|" + f.name() + "|" + v);
    }
}
