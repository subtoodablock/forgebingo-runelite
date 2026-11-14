package com.forgebingo;

import com.forgebingo.core.Critter;
import net.runelite.api.gameval.ItemID;

/**
 * The plugin's looks (owner, 2026-11-14): one palette for the side panel, the board over the game and the
 * celebration banners, plus who wanders over the panel and what falls down it. CLASSIC is the look the plugin has
 * always had. The palette order is the order of the colour fields in PanelUi.
 */
public enum PanelTheme
{
    CLASSIC("Classic (imps)",
        //        bg        cardTop   cardBot   cardEdge  inset     insetEdge gold      goldSoft  goldDark  orange    text      muted
        new int[] {0x282828, 0x363029, 0x2b2722, 0x4d4437, 0x1f1c18, 0x3d362c, 0xffbf1f, 0xe9b84e, 0xa07222, 0xff981f, 0xe6dfd3, 0xa69d8f},
        new Critter[] {Critter.IMP}, 2, new Critter.Fall[0], null, false),

    BLOOD("Theatre of Blood",
        new int[] {0x1a0e10, 0x3d171a, 0x230c0f, 0x74292c, 0x14080a, 0x4f1d20, 0xffc53d, 0xe8b552, 0x9a6a20, 0xe2332f, 0xf2dcd7, 0xb58c88},
        new Critter[] {Critter.NYLO}, 3, new Critter.Fall[] {Critter.Fall.BLOOD_DROP},
        new int[] {ItemID.SCYTHE_OF_VITUR, ItemID.SANGUINESTI_STAFF, 22324 /* Ghrazi rapier */, 22326, 22327, 22328 /* Justiciar */,
            22477 /* Avernic defender hilt */}, true),

    XERIC("Chambers of Xeric",
        new int[] {0x111a15, 0x25382d, 0x17251d, 0x3f6b52, 0x0d1511, 0x2c4d3b, 0x8df7ab, 0x7ad89b, 0x2f7d54, 0xb690ff, 0xdcf0e3, 0x8faa9b},
        new Critter[] {Critter.CRAB}, 3, new Critter.Fall[] {Critter.Fall.CRYSTAL},
        new int[] {ItemID.TWISTED_BOW, ItemID.KODAI_WAND, ItemID.ELDER_MAUL, ItemID.DRAGON_CLAWS, ItemID.ANCESTRAL_HAT,
            ItemID.ANCESTRAL_ROBE_TOP, ItemID.ANCESTRAL_ROBE_BOTTOM, ItemID.DRAGONHUNTER_XBOW}, false),

    AMASCUT("Tombs of Amascut",
        new int[] {0x1c1710, 0x42351f, 0x2c2415, 0x7d6434, 0x1a150c, 0x57451f, 0xffd44a, 0xecc35c, 0xa67c22, 0x35d0c0, 0xf3e9d1, 0xb7a682},
        new Critter[] {Critter.AKKHA, Critter.ZEBAK, Critter.KEPHRI, Critter.BABA}, 3,
        new Critter.Fall[] {Critter.Fall.KEPHRI_BOMB, Critter.Fall.WATER_DROP},
        new int[] {ItemID.TUMEKENS_SHADOW, ItemID.OSMUMTENS_FANG, ItemID.MASORI_MASK, ItemID.MASORI_BODY, ItemID.MASORI_CHAPS}, false),

    BARROWS("Barrows",
        new int[] {0x141614, 0x2d312b, 0x1e211d, 0x4c5447, 0x111310, 0x373d34, 0xcbd8a2, 0xb6c094, 0x67714b, 0x8fbf6a, 0xdadfd1, 0x8f9587},
        new Critter[] {Critter.AHRIM, Critter.DHAROK, Critter.GUTHAN, Critter.KARIL, Critter.TORAG, Critter.VERAC}, 3,
        new Critter.Fall[] {Critter.Fall.BONE},
        new int[] {4708, 4710, 4716, 4718, 4724, 4726, 4732, 4734, 4745, 4747, 4753, 4755}, false),

    INFERNO("Inferno",
        new int[] {0x140d0a, 0x35201a, 0x1c1210, 0x7d3b1c, 0x100a08, 0x57290f, 0xffa32c, 0xf08c3c, 0xa84a14, 0xff5a1f, 0xf4e1d3, 0xb59585},
        new Critter[] {Critter.NIBBLER, Critter.MELEER}, 3,
        new Critter.Fall[] {Critter.Fall.EMBER},
        // the TzHaar's obsidian weapons from Karamja, and the two capes
        new int[] {6522 /* toktz-xil-ul */, 6523 /* toktz-xil-ak */, 6524 /* toktz-ket-xil */, 6525 /* toktz-xil-ek */, 6526 /* toktz-mej-tal */,
            6527 /* tzhaar-ket-em */, 6528 /* tzhaar-ket-om */, 6568 /* obsidian cape */, 6570 /* fire cape */, 21295 /* infernal cape */}, false),

    // Skilling (owner, 2026-11-14): forest green and worn wood; the game's pets wander about, each as rare as it
    // deserves (Pet.Rarity), and what falls is what you gather: logs, ore, fish, herbs, runes and the dragon tools.
    SKILLING("Skilling (pets)",
        new int[] {0x141a12, 0x2f3b27, 0x1f281b, 0x566a40, 0x12170f, 0x3b4a2d, 0xffd866, 0xe8c766, 0x9a7a2a, 0x6cc4f5, 0xe8ecd9, 0x9aa58a},
        new Critter[] {Critter.PET}, 3, new Critter.Fall[0],
        new int[] {1511 /* logs */, 1521 /* oak */, 1519 /* willow */, 1515 /* yew */, 1513 /* magic */, 377 /* raw lobster */,
            371 /* raw swordfish */, 383 /* raw shark */, 453 /* coal */, 444 /* gold ore */, 451 /* runite ore */, 1617 /* uncut diamond */,
            1631 /* uncut dragonstone */, 207 /* grimy ranarr */, 561 /* nature rune */, 563 /* law rune */, 11920 /* dragon pickaxe */,
            6739 /* dragon axe */, 11849 /* mark of grace */}, false);

    private final String label;
    final int[] palette;
    /** who walks over the panel, and how many of them at once */
    final Critter[] cast;
    final int count;
    /** small drawn things that fall between the famous items */
    final Critter.Fall[] falls;
    /** the famous items that fall; null = the plugin's usual mix */
    final int[] rainItems;
    /** blood runs down from the bottom edge of the cards */
    final boolean drips;

    PanelTheme(String label, int[] palette, Critter[] cast, int count, Critter.Fall[] falls, int[] rainItems, boolean drips)
    {
        this.label = label;
        this.palette = palette;
        this.cast = cast;
        this.count = count;
        this.falls = falls;
        this.rainItems = rainItems;
        this.drips = drips;
    }

    /**
     * The rare visitors (owner, 2026-11-14): only two or three creatures walk about at once, and every
     * ROLL_SECONDS the theme rolls for one of these to take a regular's place for a little while. A name listed
     * twice is twice as likely. Classic, Barrows and Skilling choose their visitor another way (PanelImps.dress).
     */
    Critter[] rare()
    {
        switch (this)
        {
            case BLOOD: return new Critter[] {Critter.XARPUS, Critter.XARPUS, Critter.VERZIK};
            case XERIC: return new Critter[] {Critter.MUTTADILE, Critter.TEKTON, Critter.VESPULA, Critter.VANGUARD};
            case AMASCUT: return new Critter[] {Critter.WARDEN};
            case INFERNO: return new Critter[] {Critter.JAD, Critter.JAD, Critter.ZUK};
            default: return new Critter[0];
        }
    }

    /** how often a theme rolls for a rare visitor */
    static final float ROLL_SECONDS = 300f;
    /** the odds that a roll brings one */
    static final float ROLL_CHANCE = 0.5f;
    /** how long a visitor stays before the regular comes back (seconds, from and to) */
    static final float VISIT_MIN = 60f;
    static final float VISIT_MAX = 90f;

    @Override
    public String toString()
    {
        return label;
    }
}
