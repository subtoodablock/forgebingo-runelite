package com.forgebingo.core;

import java.awt.image.BufferedImage;

/**
 * Pixel art for the skill props (see SkillProp): one little original prop per Old School skill that grows in three
 * stages (0 hint, 1 half, 2 complete) and has a gentle 2-frame animation.
 *
 * The pictures are baked: they come from the bundled sheet sprites/skillprops.png + skillprops.txt (see SpriteSheet),
 * keyed "SKILL|stage|frame". The code that draws them is SkillPropArt in src/test/java; after changing it, re-bake
 * with SkillPropSheetBaker, or SkillPropsBakedTest fails.
 */
public final class SkillProps
{
    private static final SpriteSheet SHEET = new SpriteSheet("skillprops");

    private SkillProps()
    {
    }

    /**
     * One prop at a growth stage (0, 1 or 2; anything outside is pulled to the nearest) and animation frame (0, or
     * anything else for the second), as a small transparent image whose BOTTOM edge is the ground it stands on.
     * At most 26 x 26. Null for a null prop or one with no picture.
     */
    public static BufferedImage image(SkillProp prop, int stage, int frame)
    {
        return prop == null ? null
            : SHEET.get(prop.name() + "|" + Math.max(0, Math.min(2, stage)) + (frame == 0 ? "|0" : "|1"));
    }
}
