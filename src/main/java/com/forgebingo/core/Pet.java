package com.forgebingo.core;

/**
 * Every pet in the game's collection log (72 on 2026-11-14), in the order of the wiki's lists: boss pets, skilling
 * pets, then the other collectible ones. They wander over the side panel in the Skilling theme. Each has a rarity:
 * the skilling and activity pets are the everyday ones, and the boss pets turn up more or less often the way their
 * real drops do. Our own original pixel art of each (PetSprites).
 */
public enum Pet
{
    // ---- boss pets
    ABYSSAL_ORPHAN("Abyssal orphan", Rarity.RARE), AGGY("Aggy", Rarity.RARE), BABY_MOLE("Baby Mole", Rarity.RARE),
    BARON("Baron", Rarity.RARE), BEEF("Beef", Rarity.UNCOMMON), BRAN("Bran", Rarity.RARE), BUTCH("Butch", Rarity.RARE),
    CALLISTO_CUB("Callisto cub", Rarity.RARE), DOM("Dom", Rarity.UNCOMMON), GULL("Gull", Rarity.RARE),
    HELLPUPPY("Hellpuppy", Rarity.RARE), HUBERTE("Huberte", Rarity.UNCOMMON), IKKLE_HYDRA("Ikkle Hydra", Rarity.RARE),
    JAL_NIB_REK("Jal-Nib-Rek", Rarity.LEGENDARY), KALPHITE_PRINCESS("Kalphite Princess", Rarity.RARE),
    LIL_ZIK("Lil' Zik", Rarity.EPIC), LILVIATHAN("Lil'viathan", Rarity.RARE), LITTLE_NIGHTMARE("Little Nightmare", Rarity.EPIC),
    MAGGOT_MARQUESS("Maggot marquess", Rarity.RARE), MOXI("Moxi", Rarity.RARE), MUPHIN("Muphin", Rarity.RARE),
    NEXLING("Nexling", Rarity.EPIC), NID("Nid", Rarity.RARE), NOON("Noon", Rarity.RARE), OLMLET("Olmlet", Rarity.EPIC),
    ORION("Orion", Rarity.UNCOMMON), CHAOS_ELEMENTAL("Pet chaos elemental", Rarity.UNCOMMON),
    DAGANNOTH_PRIME("Pet Dagannoth Prime", Rarity.EPIC), DAGANNOTH_REX("Pet Dagannoth Rex", Rarity.EPIC),
    DAGANNOTH_SUPREME("Pet Dagannoth Supreme", Rarity.EPIC), DARK_CORE("Pet dark core", Rarity.EPIC),
    GENERAL_GRAARDOR("Pet General Graardor", Rarity.EPIC), KRIL_TSUTSAROTH("Pet K'ril Tsutsaroth", Rarity.EPIC),
    KRAKEN("Pet Kraken", Rarity.RARE), KREEARRA("Pet Kree'arra", Rarity.EPIC), SMOKE_DEVIL("Pet Smoke Devil", Rarity.RARE),
    SNAKELING("Pet Snakeling", Rarity.RARE), ZILYANA("Pet Zilyana", Rarity.EPIC), PHOENIX("Phoenix", Rarity.UNCOMMON),
    PRINCE_BLACK_DRAGON("Prince Black Dragon", Rarity.RARE), SCORPIAS_OFFSPRING("Scorpia's offspring", Rarity.RARE),
    SCURRY("Scurry", Rarity.UNCOMMON), SKOTOS("Skotos", Rarity.EPIC), SMOL_HEREDIT("Smol Heredit", Rarity.LEGENDARY),
    SMOLCANO("Smolcano", Rarity.UNCOMMON), SRARACHA("Sraracha", Rarity.RARE), TINY_TEMPOR("Tiny tempor", Rarity.UNCOMMON),
    TUMEKENS_GUARDIAN("Tumeken's guardian", Rarity.EPIC), TZREK_JAD("TzRek-Jad", Rarity.EPIC),
    VENENATIS_SPIDERLING("Venenatis spiderling", Rarity.RARE), VETION_JR("Vet'ion Jr.", Rarity.RARE), VORKI("Vorki", Rarity.RARE),
    WISP("Wisp", Rarity.RARE), YAMI("Yami", Rarity.EPIC), YOUNGLLEF("Youngllef", Rarity.EPIC),
    // ---- skilling pets
    BABY_CHINCHOMPA("Baby chinchompa", Rarity.COMMON), BEAVER("Beaver", Rarity.COMMON), GIANT_SQUIRREL("Giant Squirrel", Rarity.COMMON),
    HERON("Heron", Rarity.COMMON), RIFT_GUARDIAN("Rift guardian", Rarity.COMMON), ROCK_GOLEM("Rock golem", Rarity.COMMON),
    ROCKY("Rocky", Rarity.COMMON), SOUP("Soup", Rarity.COMMON), TANGLEROOT("Tangleroot", Rarity.COMMON),
    // ---- other collectible pets
    ABYSSAL_PROTECTOR("Abyssal protector", Rarity.UNCOMMON), BLOODHOUND("Bloodhound", Rarity.UNCOMMON),
    CHOMPY_CHICK("Chompy chick", Rarity.UNCOMMON), HERBI("Herbi", Rarity.UNCOMMON), LIL_CREATOR("Lil' Creator", Rarity.UNCOMMON),
    MR_MCGROOT("Mr McGroot", Rarity.UNCOMMON), PENANCE_QUEEN("Pet Penance Queen", Rarity.EPIC), QUETZIN("Quetzin", Rarity.UNCOMMON);

    /** How often a pet turns up: the weight is its share of the draw. */
    public enum Rarity
    {
        COMMON(400), UNCOMMON(90), RARE(18), EPIC(4), LEGENDARY(1);

        public final int weight;

        Rarity(int weight)
        {
            this.weight = weight;
        }
    }

    public final String label;
    public final Rarity rarity;

    Pet(String label, Rarity rarity)
    {
        this.label = label;
        this.rarity = rarity;
    }

    private static final Pet[] ALL = values();

    public static Pet of(int index)
    {
        return ALL[Math.floorMod(index, ALL.length)];
    }

    /**
     * A pet drawn by rarity from the ones that have a picture. {@code roll} is a random number from 0 (inclusive)
     * to 1 (exclusive). Null when none is drawn yet.
     */
    public static Pet pick(double roll, java.util.function.Predicate<Pet> drawn)
    {
        long total = 0;
        for (Pet p : ALL) if (drawn.test(p)) total += p.rarity.weight;
        if (total == 0) return null;
        long at = (long) (roll * total);
        for (Pet p : ALL)
        {
            if (!drawn.test(p)) continue;
            at -= p.rarity.weight;
            if (at < 0) return p;
        }
        return null;
    }
}
