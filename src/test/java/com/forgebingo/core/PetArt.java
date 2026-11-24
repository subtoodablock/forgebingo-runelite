package com.forgebingo.core;

import java.awt.image.BufferedImage;

/**
 * The pets as DRAWN by code: one door to PetSpritesA..F (this was the body of PetSprites before the art was baked
 * into sprites/pets.png). Source of truth for PetSheetBaker and PetSpritesBakedTest. Null when a pet has no picture.
 */
public final class PetArt
{
    private PetArt()
    {
    }

    public static BufferedImage image(Pet pet, int frame)
    {
        if (pet == null) return null;
        BufferedImage img = PetSpritesA.image(pet, frame);
        if (img == null) img = PetSpritesB.image(pet, frame);
        if (img == null) img = PetSpritesC.image(pet, frame);
        if (img == null) img = PetSpritesD.image(pet, frame);
        if (img == null) img = PetSpritesE.image(pet, frame);
        if (img == null) img = PetSpritesF.image(pet, frame);
        return img;
    }
}
