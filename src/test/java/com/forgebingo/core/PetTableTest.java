package com.forgebingo.core;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class PetTableTest
{
    @Test
    public void wellKnownPets()
    {
        PetTable.Pet mole = PetTable.byName("Baby mole");
        assertNotNull(mole);
        assertEquals(12646, mole.primaryItemId());
        assertSame(mole, PetTable.byItemId(12646));
        assertSame(mole, PetTable.byFollowerNpcId(6635));
        assertSame(mole, PetTable.bySource("Giant Mole").get(0));

        assertEquals(21992, PetTable.byName("vorki").primaryItemId());
        assertSame(PetTable.byName("Vorki"), PetTable.byFollowerNpcId(8029));
        assertEquals(20851, PetTable.byName("Olmlet").primaryItemId());
        assertEquals(20693, PetTable.byName("Phoenix").primaryItemId());
        assertEquals(12921, PetTable.byName("Pet snakeling").primaryItemId());
        assertEquals(12650, PetTable.byName("Pet general graardor").primaryItemId());
        assertSame(PetTable.byName("Pet general graardor"), PetTable.byFollowerNpcId(6632));
        // typographic apostrophe and odd casing from chat
        assertEquals(22473, PetTable.byName("Lil’ Zik").primaryItemId());
        assertEquals(13179, PetTable.byName("VET'ION JR.").primaryItemId());
    }

    @Test
    public void variantsMapToTheirPet()
    {
        PetTable.Pet hydra = PetTable.byName("Ikkle hydra");
        for (int id : new int[]{22746, 22748, 22750, 22752})
        {
            assertSame(hydra, PetTable.byItemId(id));
        }
        assertSame(PetTable.byName("Noon"), PetTable.byItemId(21750)); // Midnight
        assertSame(PetTable.byName("Youngllef"), PetTable.byItemId(23759)); // Corrupted youngllef
    }

    @Test
    public void tableIsConsistent()
    {
        assertTrue(PetTable.all().size() >= 60);
        Set<String> names = new HashSet<>();
        for (PetTable.Pet p : PetTable.all())
        {
            assertTrue(p.name, names.add(ItemNames.normalize(p.name)));
            assertTrue(p.name, p.itemIds.length > 0);
            assertTrue(p.name, p.followerNpcIds.length > 0);
            for (int id : p.itemIds) assertSame(p.name, p, PetTable.byItemId(id));
            for (int id : p.followerNpcIds) assertSame(p.name, p, PetTable.byFollowerNpcId(id));
        }
    }

    @Test
    public void unknowns()
    {
        assertNull(PetTable.byName("Abyssal whip"));
        assertNull(PetTable.byName(null));
        assertNull(PetTable.byItemId(4151));
        assertNull(PetTable.byFollowerNpcId(2215)); // General Graardor himself, not his pet
        assertNull(PetTable.byFollowerNpcId(494)); // the Kraken boss
        assertTrue(PetTable.bySource("Goblin").isEmpty());
        assertTrue(PetTable.bySource(null).isEmpty());
    }
}
