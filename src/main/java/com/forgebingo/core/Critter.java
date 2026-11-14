package com.forgebingo.core;

/**
 * The little creatures that wander over the side panel, by theme. Each is our own original pixel art in the spirit
 * of the place it is named after. {@code variants} is how many looks it has (a click cycles a crab's colour).
 */
public enum Critter
{
    IMP(1),
    // Theatre of Blood
    NYLO(3), XARPUS(1), VERZIK(1),
    // Chambers of Xeric: the jewelled crab; white, red, green, blue. The bosses turn up very rarely.
    CRAB(4), MUTTADILE(1), TEKTON(1), VESPULA(1), VANGUARD(3),
    // Tombs of Amascut
    AKKHA(1), ZEBAK(1), KEPHRI(1), BABA(1), WARDEN(1),
    // Barrows
    AHRIM(1), DHAROK(1), GUTHAN(1), KARIL(1), TORAG(1), VERAC(1),
    // Inferno
    ZUK(1), JAD(1), MELEER(1), NIBBLER(1),
    // Skilling: any of the game's pets (see Pet); which one is kept beside it, not as a variant
    PET(1);

    public final int variants;

    Critter(int variants)
    {
        this.variants = variants;
    }

    /** These never land: they fly around the panel instead of walking along the cards. */
    public boolean flies()
    {
        return this == XARPUS || this == VESPULA;
    }

    /** Small things that fall down the panel between the famous items. */
    public enum Fall
    {
        BLOOD_DROP, CRYSTAL, KEPHRI_BOMB, WATER_DROP, BONE, EMBER
    }
}
