package com.forgebingo.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * Proof that moving the pet table into data/pets.txt changed nothing: what the plugin loads (PetTable) equals the
 * old Java table (PetTableReference) exactly: same pets in the same order, same names, ids (in order) and sources
 * (in order), and every lookup gives the same pet. Also fails when the data file is missing, empty or malformed (the
 * table would then be empty), and checks the parser refuses bad files whole.
 */
public class PetTableDataTest
{
    private static final String FIX = " - data/pets.txt and PetTableReference differ; change both together";

    @Test
    public void theLoadedTableIsExactlyTheOldJavaTable()
    {
        List<PetTableReference.Pet> ref = PetTableReference.all();
        List<PetTable.Pet> now = PetTable.all();
        assertTrue("the reference has rows", ref.size() >= 60);
        assertEquals("number of pets" + FIX, ref.size(), now.size());
        int ids = 0;
        int sources = 0;
        for (int i = 0; i < ref.size(); i++)
        {
            PetTableReference.Pet r = ref.get(i);
            PetTable.Pet p = now.get(i);
            assertEquals("pet " + i + FIX, r.name, p.name);
            assertEquals(r.name, r.toString(), p.toString());
            assertArrayEquals(r.name + " item ids" + FIX, r.itemIds, p.itemIds);
            assertArrayEquals(r.name + " npc ids" + FIX, r.followerNpcIds, p.followerNpcIds);
            assertEquals(r.name + " sources" + FIX, r.sources, p.sources);
            assertEquals(r.name, r.primaryItemId(), p.primaryItemId());
            ids += r.itemIds.length + r.followerNpcIds.length;
            sources += r.sources.size();
            // every lookup that found this pet still does
            assertSame(r.name, p, PetTable.byName(r.name));
            assertSame(r.name, p, PetTable.byName(r.name.toUpperCase()));
            for (int id : r.itemIds) assertSame(r.name + " item " + id, p, PetTable.byItemId(id));
            for (int id : r.followerNpcIds) assertSame(r.name + " npc " + id, p, PetTable.byFollowerNpcId(id));
            for (String s : r.sources) assertEquals(r.name + " source " + s, names(PetTableReference.bySource(s)), namesNow(PetTable.bySource(s)));
            try
            {
                p.sources.add("x");
                fail("sources stay unmodifiable");
            }
            catch (UnsupportedOperationException expected)
            {
                // good
            }
        }
        // and every id, in and around the table, resolves to the same pet or to none (so nothing extra either)
        for (int id = -10; id <= 60_000; id++)
        {
            assertEquals("item " + id + FIX, name(PetTableReference.byItemId(id)), nameNow(PetTable.byItemId(id)));
            assertEquals("npc " + id + FIX, name(PetTableReference.byFollowerNpcId(id)), nameNow(PetTable.byFollowerNpcId(id)));
        }
        assertNull(PetTable.byName(null));
        assertNull(PetTable.byName("no such pet"));
        assertTrue(PetTable.bySource(null).isEmpty());
        assertTrue(PetTable.bySource("no such boss").isEmpty());
        try
        {
            now.add(null);
            fail("all() stays unmodifiable");
        }
        catch (UnsupportedOperationException expected)
        {
            // good
        }
        System.out.println("PetTableDataTest: compared " + ref.size() + " pets, " + ids + " ids, " + sources + " sources");
    }

    @Test
    public void aBrokenFileIsRefusedWhole()
    {
        refused("three fields", "Heron | 13320 | 6722");
        refused("five fields", "Heron | 13320 | 6722 | a | b");
        refused("no name", " | 13320 | 6722 |");
        refused("no item id", "Heron | | 6722 |");
        refused("no npc id", "Heron | 13320 | |");
        refused("an id that is not a number", "Heron | 13320 x | 6722 |");
        refused("an empty source", "Heron | 13320 | 6722 | a, , b");
        refused("a trailing comma", "Heron | 13320 | 6722 | a,");
        List<PetTable.Pet> ok = PetTable.parse(Arrays.asList("Heron | 13320  13321 | 6722 |", "Vorki|21992|8029|vorkath , x"));
        assertEquals(2, ok.size());
        assertArrayEquals(new int[]{13320, 13321}, ok.get(0).itemIds);
        assertTrue(ok.get(0).sources.isEmpty());
        assertEquals(Arrays.asList("vorkath", "x"), ok.get(1).sources);
        assertNotNull(DataFile.lines("pets"));
    }

    private static void refused(String what, String... lines)
    {
        try
        {
            PetTable.parse(Arrays.asList(lines));
            fail("a file with " + what + " must be refused");
        }
        catch (RuntimeException expected)
        {
            // good
        }
    }

    private static String name(PetTableReference.Pet p)
    {
        return p == null ? null : p.name;
    }

    private static String nameNow(PetTable.Pet p)
    {
        return p == null ? null : p.name;
    }

    private static List<String> names(List<PetTableReference.Pet> pets)
    {
        List<String> out = new ArrayList<>();
        for (PetTableReference.Pet p : pets) out.add(p.name);
        return out;
    }

    private static List<String> namesNow(List<PetTable.Pet> pets)
    {
        List<String> out = new ArrayList<>();
        for (PetTable.Pet p : pets) out.add(p.name);
        return out;
    }
}
