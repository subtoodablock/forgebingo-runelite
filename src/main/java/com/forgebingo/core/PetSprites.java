package com.forgebingo.core;

import java.awt.image.BufferedImage;

/** One door to every pet's picture, whichever file draws it. Null when a pet has no picture yet. */
public final class PetSprites
{
    private PetSprites()
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

    public static boolean drawn(Pet pet)
    {
        return image(pet, 0) != null;
    }
}
