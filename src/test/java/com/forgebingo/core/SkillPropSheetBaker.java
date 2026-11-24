package com.forgebingo.core;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bakes every skill prop (drawn by SkillPropArt) into
 * src/main/resources/com/forgebingo/sprites/skillprops.png + skillprops.txt, which SkillProps loads in the plugin.
 * One row per prop: stage 0 frame 0, stage 0 frame 1, stage 1 frame 0, ... stage 2 frame 1. Keys "SKILL|stage|frame".
 *
 * Re-bake after ANY change to that art, from the plugin directory, either by running this main() or with:
 *   bash:        FORGE_BAKE=1 ./gradlew cleanTest test --tests "*SkillPropsBakedTest"
 *   PowerShell:  $env:FORGE_BAKE='1'; ./gradlew cleanTest test --tests "*SkillPropsBakedTest"; $env:FORGE_BAKE=$null
 * then run ./gradlew test again (without FORGE_BAKE) to prove the sheet matches.
 */
public final class SkillPropSheetBaker
{
    public static final String REBAKE = "from plugin/: FORGE_BAKE=1 ./gradlew cleanTest test --tests \"*SkillPropsBakedTest\""
        + " (PowerShell: $env:FORGE_BAKE='1' first), or run com.forgebingo.core.SkillPropSheetBaker.main; then ./gradlew test";

    private SkillPropSheetBaker()
    {
    }

    /** Every prop x stage x frame as the art code draws it (null where it draws nothing), in sheet order. */
    public static Map<String, BufferedImage> drawn()
    {
        Map<String, BufferedImage> out = new LinkedHashMap<>();
        for (SkillProp p : SkillProp.values())
        {
            for (int st = 0; st < SkillProp.STAGES; st++)
            {
                for (int f = 0; f < 2; f++) out.put(p.name() + "|" + st + "|" + f, SkillPropArt.image(p, st, f));
            }
        }
        return out;
    }

    public static void bake() throws IOException
    {
        SheetBaker.bake("skillprops", drawn(), SkillProp.STAGES * 2);
    }

    public static void main(String[] args) throws IOException
    {
        bake();
    }
}
