package com.forgebingo.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Static table of every pet: in-game item name, item ids (base form first, then metamorph variants) and the follower
 * NPC ids that walk behind the player. Pets are untradeable, so {@code ItemManager.search} (tradeables only) can
 * never resolve them; this table is the plugin's only source for pet ids.
 *
 * <p>Ids come from the RuneLite gameval tables ({@code ItemID} / {@code NpcID}, follower NPCs only: the house
 * menagerie copies are left out). {@code sources} are lower-case boss / activity names as the plugin sees them in
 * loot, kill count and clue events; they are only used to attribute a duplicate pet, which names no pet at all.
 */
public final class PetTable
{
    public static final class Pet
    {
        public final String name;
        /** Base item first. Every id is sent, so a task listing any variant matches. */
        public final int[] itemIds;
        public final int[] followerNpcIds;
        public final List<String> sources;

        Pet(String name, int[] itemIds, int[] followerNpcIds, List<String> sources)
        {
            this.name = name;
            this.itemIds = itemIds;
            this.followerNpcIds = followerNpcIds;
            this.sources = sources;
        }

        public int primaryItemId()
        {
            return itemIds[0];
        }

        @Override
        public String toString()
        {
            return name;
        }
    }

    private static final List<Pet> PETS = Collections.unmodifiableList(build());
    private static final Map<String, Pet> BY_NAME = new HashMap<>();
    private static final Map<Integer, Pet> BY_ITEM = new HashMap<>();
    private static final Map<Integer, Pet> BY_NPC = new HashMap<>();
    private static final Map<String, List<Pet>> BY_SOURCE = new HashMap<>();

    static
    {
        for (Pet p : PETS)
        {
            put(BY_NAME, ItemNames.normalize(p.name), p);
            for (int id : p.itemIds) put(BY_ITEM, id, p);
            for (int id : p.followerNpcIds) put(BY_NPC, id, p);
            for (String s : p.sources) BY_SOURCE.computeIfAbsent(ItemNames.normalize(s), k -> new ArrayList<>()).add(p);
        }
    }

    private PetTable()
    {
    }

    private static <K> void put(Map<K, Pet> map, K key, Pet p)
    {
        Pet old = map.put(key, p);
        if (old != null && old != p)
        {
            throw new IllegalStateException("pet table: " + key + " used by " + old + " and " + p);
        }
    }

    public static List<Pet> all()
    {
        return PETS;
    }

    /** By in-game item name (case, apostrophe and whitespace insensitive). */
    public static Pet byName(String name)
    {
        return name == null ? null : BY_NAME.get(ItemNames.normalize(name));
    }

    public static Pet byItemId(int itemId)
    {
        return BY_ITEM.get(itemId);
    }

    public static Pet byFollowerNpcId(int npcId)
    {
        return BY_NPC.get(npcId);
    }

    /** Pets dropped by a boss / activity name (loot, kill count or clue source); empty when unknown. */
    public static List<Pet> bySource(String source)
    {
        if (source == null)
        {
            return Collections.emptyList();
        }
        List<Pet> pets = BY_SOURCE.get(ItemNames.normalize(source));
        return pets == null ? Collections.emptyList() : pets;
    }

    private static int[] ids(int... ids)
    {
        return ids;
    }

    private static Pet pet(String name, int[] items, int[] npcs, String... sources)
    {
        List<String> s = new ArrayList<>();
        Collections.addAll(s, sources);
        return new Pet(name, items, npcs, Collections.unmodifiableList(s));
    }

    // GENERATED from the RuneLite gameval ItemID / NpcID tables (follower NPCs, no POH copies).
    private static List<Pet> build()
    {
        List<Pet> l = new ArrayList<>();
        Collections.addAll(l,
            pet("Abyssal orphan", ids(13262), ids(5884), "abyssal sire"),
            pet("Baby mole", ids(12646), ids(6635), "giant mole"),
            pet("Callisto cub", ids(13178, 27649), ids(5558, 11986), "callisto", "artio"),
            pet("Hellpuppy", ids(13247), ids(3099), "cerberus"),
            pet("Kalphite princess", ids(12647, 12654), ids(6637, 6638), "kalphite queen"),
            pet("Pet chaos elemental", ids(11995), ids(2055), "chaos elemental", "chaos fanatic"),
            pet("Pet dagannoth prime", ids(12644), ids(6629), "dagannoth prime"),
            pet("Pet dagannoth rex", ids(12645), ids(6630), "dagannoth rex"),
            pet("Pet dagannoth supreme", ids(12643), ids(6628), "dagannoth supreme"),
            pet("Pet dark core", ids(12816), ids(318, 8010), "corporeal beast"),
            pet("Pet general graardor", ids(12650), ids(6632), "general graardor"),
            pet("Pet k'ril tsutsaroth", ids(12652), ids(6634), "k'ril tsutsaroth"),
            pet("Pet kraken", ids(12655), ids(6640), "kraken"),
            pet("Pet kree'arra", ids(12649), ids(6631), "kree'arra"),
            pet("Pet smoke devil", ids(12648, 22663), ids(6639, 8483), "thermonuclear smoke devil"),
            pet("Pet snakeling", ids(12921, 12939, 12940), ids(2130, 2131, 2132), "zulrah"),
            pet("Pet zilyana", ids(12651), ids(6633), "commander zilyana"),
            pet("Prince black dragon", ids(12653), ids(6636), "king black dragon"),
            pet("Scorpia's offspring", ids(13181), ids(5561), "scorpia"),
            pet("Skotos", ids(21273), ids(7671), "skotizo"),
            pet("Tzrek-jad", ids(13225), ids(5893), "tztok-jad"),
            pet("Venenatis spiderling", ids(13177, 27648), ids(5557, 11985), "venenatis", "spindel"),
            pet("Vet'ion jr.", ids(13179, 13180, 27650, 27651), ids(5559, 5560, 11987, 11988), "vet'ion", "calvar'ion"),
            pet("Vorki", ids(21992), ids(8029), "vorkath"),
            pet("Noon", ids(21748, 21750), ids(7892, 7893), "grotesque guardians", "dusk"),
            pet("Ikkle hydra", ids(22746, 22748, 22750, 22752), ids(8492, 8493, 8494, 8495), "alchemical hydra"),
            pet("Sraracha", ids(23495, 25842, 25843), ids(2144, 11159, 11160), "sarachnis"),
            pet("Olmlet", ids(20851), ids(7520), "chambers of xeric", "chambers of xeric challenge mode"),
            pet("Lil' zik", ids(22473), ids(8337), "theatre of blood", "theatre of blood: hard mode", "theatre of blood: entry mode"),
            pet("Little nightmare", ids(24491), ids(9399), "the nightmare", "nightmare", "phosani's nightmare"),
            pet("Jal-nib-rek", ids(21291), ids(7675), "tzkal-zuk"),
            pet("Youngllef", ids(23757, 23759), ids(8737, 8738), "the gauntlet", "gauntlet", "corrupted gauntlet", "the corrupted gauntlet"),
            pet("Smolcano", ids(23760), ids(8739), "zalcano"),
            pet("Tiny tempor", ids(25602), ids(10637), "tempoross"),
            pet("Nexling", ids(26348), ids(11277), "nex"),
            pet("Abyssal protector", ids(26901), ids(11429), "guardians of the rift"),
            pet("Tumeken's guardian", ids(27352), ids(11812), "tombs of amascut", "tombs of amascut: expert mode", "tombs of amascut: entry mode"),
            pet("Muphin", ids(27590, 27592, 27593), ids(12014, 12015, 12016), "phantom muspah"),
            pet("Wisp", ids(28246), ids(12157), "the whisperer", "whisperer"),
            pet("Butch", ids(28248), ids(12158), "vardorvis"),
            pet("Baron", ids(28250), ids(12159), "duke sucellus"),
            pet("Lil'viathan", ids(28252), ids(12160), "the leviathan", "leviathan"),
            pet("Scurry", ids(28801), ids(7616), "scurrius"),
            pet("Smol heredit", ids(28960), ids(12857), "fortis colosseum", "sol heredit"),
            pet("Quetzin", ids(28962), ids(12858)),
            pet("Nid", ids(29836), ids(13683), "araxxor"),
            pet("Huberte", ids(30152), ids(14045), "the hueycoatl", "hueycoatl"),
            pet("Moxi", ids(30154), ids(14046), "amoxliatl"),
            pet("Bran", ids(30622), ids(12593), "the royal titans", "royal titans", "branda the fire queen", "eldric the ice king"),
            pet("Yami", ids(30888), ids(14204), "yama"),
            pet("Dom", ids(31130), ids(14785), "doom of mokhaiotl"),
            pet("Heron", ids(13320), ids(6722)),
            pet("Rock golem", ids(13321, 21187, 21188, 21189, 21190, 21191, 21192, 21193, 21194, 21195, 21196, 21197, 21340, 21358, 21359, 21360, 31276, 31277, 31278), ids(7451, 7452, 7453, 7454, 7455, 7642, 7643, 7644, 7645, 7646, 7647, 7648, 7711, 7739, 7740, 7741, 14923, 14924, 14925)),
            pet("Beaver", ids(13322, 28229, 28230, 28231, 28232, 28233, 28234, 28235, 28236, 28237, 31279, 31280, 31281, 31282), ids(12181, 12182, 12183, 12184, 12185, 12186, 12187, 12188, 12189, 12190, 14926, 14927, 14928, 14929)),
            pet("Baby chinchompa", ids(13323, 13324, 13325, 13326), ids(6756, 6757, 6758, 6759)),
            pet("Giant squirrel", ids(20659), ids(7351)),
            pet("Tangleroot", ids(20661, 24555, 24557, 24559, 24561, 24563), ids(7352, 9497, 9498, 9499, 9500, 9501)),
            pet("Rocky", ids(20663), ids(7353)),
            pet("Rift guardian", ids(20665, 20667, 20669, 20671, 20673, 20675, 20677, 20679, 20681, 20683, 20685, 20687, 20689, 20691, 21990), ids(7354, 7355, 7356, 7357, 7358, 7359, 7360, 7361, 7362, 7363, 7364, 7365, 7366, 7367, 8028)),
            pet("Phoenix", ids(20693, 24483, 24484, 24485, 24486), ids(3081, 3082, 3083, 3084, 7370), "wintertodt"),
            pet("Herbi", ids(21509), ids(7760), "herbiboar"),
            pet("Chompy chick", ids(13071), ids(4002)),
            pet("Pet penance queen", ids(12703), ids(6674), "barbarian assault", "penance queen"),
            pet("Bloodhound", ids(19730), ids(7232), "clue scroll (master)"),
            pet("Lil' creator", ids(25348, 25350), ids(3566, 5008), "soul wars", "spoils of war"),
            pet("Soup", ids(31283), ids(14930)),
            pet("Gull", ids(31285), ids(14931)),
            pet("Beef", ids(33124), ids(15631))
        );
        return l;
    }
}
