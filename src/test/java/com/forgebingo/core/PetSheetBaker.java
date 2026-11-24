package com.forgebingo.core;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bakes every pet's two walk frames (drawn by PetSpritesA..F through PetArt) into
 * src/main/resources/com/forgebingo/sprites/pets.png + pets.txt, which PetSprites loads in the plugin.
 * One row per drawn pet, frame 0 then frame 1; keys are "PET_NAME|frame".
 *
 * Re-bake after ANY change to the pet art, from the plugin directory, either by running this main() (IDE, or a
 * JavaExec on the test classpath) or with:
 *   bash:        FORGE_BAKE=1 ./gradlew cleanTest test --tests "*PetSpritesBakedTest"
 *   PowerShell:  $env:FORGE_BAKE='1'; ./gradlew cleanTest test --tests "*PetSpritesBakedTest"; $env:FORGE_BAKE=$null
 * then run ./gradlew test again (without FORGE_BAKE) to prove the sheet matches.
 */
public final class PetSheetBaker
{
    public static final String REBAKE = "from plugin/: FORGE_BAKE=1 ./gradlew cleanTest test --tests \"*PetSpritesBakedTest\""
        + " (PowerShell: $env:FORGE_BAKE='1' first), or run com.forgebingo.core.PetSheetBaker.main; then ./gradlew test";

    private PetSheetBaker()
    {
    }

    /** Every pet x frame as the art code draws it (null for a pet with no picture), in sheet order. */
    public static Map<String, BufferedImage> drawn()
    {
        Map<String, BufferedImage> out = new LinkedHashMap<>();
        for (Pet p : Pet.values())
        {
            for (int f = 0; f < 2; f++) out.put(p.name() + "|" + f, PetArt.image(p, f));
        }
        return out;
    }

    public static void bake() throws IOException
    {
        SheetBaker.bake("pets", drawn(), 2);
    }

    public static void main(String[] args) throws IOException
    {
        bake();
    }
}
