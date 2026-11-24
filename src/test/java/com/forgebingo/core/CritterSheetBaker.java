package com.forgebingo.core;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bakes every creature (drawn by RaidSprites and CryptSprites through CritterArt) and every falling thing into
 * src/main/resources/com/forgebingo/sprites/critters.png + critters.txt, which CritterSprite loads in the plugin.
 * Keys: "CRITTER|variant|frame" for every variant 0..variants-1 and frame 0..1 of each creature that has its own
 * picture, then "fall|FALL|variant" for each distinct look of a falling thing (variant 1 is left out when the art
 * code hands back the very same picture as variant 0).
 *
 * Re-bake after ANY change to that art, from the plugin directory, either by running this main() or with:
 *   bash:        FORGE_BAKE=1 ./gradlew cleanTest test --tests "*CritterSpritesBakedTest"
 *   PowerShell:  $env:FORGE_BAKE='1'; ./gradlew cleanTest test --tests "*CritterSpritesBakedTest"; $env:FORGE_BAKE=$null
 * then run ./gradlew test again (without FORGE_BAKE) to prove the sheet matches.
 */
public final class CritterSheetBaker
{
    public static final String REBAKE = "from plugin/: FORGE_BAKE=1 ./gradlew cleanTest test --tests \"*CritterSpritesBakedTest\""
        + " (PowerShell: $env:FORGE_BAKE='1' first), or run com.forgebingo.core.CritterSheetBaker.main; then ./gradlew test";

    private CritterSheetBaker()
    {
    }

    /** Every canonical picture as the art code draws it (null when a creature has no picture of its own), in sheet order. */
    public static Map<String, BufferedImage> drawn()
    {
        Map<String, BufferedImage> out = new LinkedHashMap<>();
        for (Critter c : Critter.values())
        {
            for (int v = 0; v < c.variants; v++)
            {
                for (int f = 0; f < 2; f++) out.put(c.name() + "|" + v + "|" + f, CritterArt.drawn(c, v, f));
            }
        }
        for (Critter.Fall fall : Critter.Fall.values())
        {
            BufferedImage first = CritterArt.drawnFall(fall, 0);
            out.put("fall|" + fall.name() + "|0", first);
            BufferedImage second = CritterArt.drawnFall(fall, 1);
            if (second != first) out.put("fall|" + fall.name() + "|1", second);
        }
        return out;
    }

    public static void bake() throws IOException
    {
        SheetBaker.bake("critters", drawn(), 2);
    }

    public static void main(String[] args) throws IOException
    {
        bake();
    }
}
