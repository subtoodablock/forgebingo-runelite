package com.forgebingo.core;

import java.awt.image.BufferedImage;

/**
 * One door to every pet's picture. Null when a pet has no picture yet.
 *
 * The pictures are baked: they come from the bundled sheet sprites/pets.png + pets.txt (see SpriteSheet), keyed
 * "PET_NAME|frame". The code that draws them is PetSpritesA..F in src/test/java (reached through PetArt there);
 * after changing any of it, re-bake with PetSheetBaker, or PetSpritesBakedTest fails.
 */
public final class PetSprites
{
    private static final SpriteSheet SHEET = new SpriteSheet("pets");

    private PetSprites()
    {
    }

    /** One walk frame (0, or anything else for the second) on the PixelArt.WIDTH x PixelArt.HEIGHT grid, facing left. */
    public static BufferedImage image(Pet pet, int frame)
    {
        return pet == null ? null : SHEET.get(pet.name() + (frame == 0 ? "|0" : "|1"));
    }

    public static boolean drawn(Pet pet)
    {
        return image(pet, 0) != null;
    }
}
