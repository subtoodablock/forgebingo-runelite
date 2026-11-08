package com.forgebingo.core;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import org.junit.Test;

public class PetDetectorTest
{
    private final PetDetector d = new PetDetector();

    @Test
    public void followerIdentifiesNewPet()
    {
        assertFalse(d.onPetMessage(ChatParsers.Pet.FOLLOWING, 100).isPresent());
        assertTrue(d.wantsFollower());
        assertFalse(d.wantsInventory());
        assertFalse(d.onFollower(2215, 100).isPresent()); // not a pet follower
        PetDetector.Drop drop = d.onFollower(6635, 101).get();
        assertEquals("Baby mole", drop.pet.name);
        assertFalse(drop.duplicate);
        assertEquals("follower", drop.via);
        assertFalse(d.pending());
        assertFalse(d.onFollower(6635, 102).isPresent()); // reported once
    }

    @Test
    public void followerIgnoredForBackpackCase()
    {
        // a pet that was already following must not be mistaken for the one that went into the backpack
        d.onPetMessage(ChatParsers.Pet.BACKPACK, 50);
        assertFalse(d.onFollower(8029, 50).isPresent());
        assertFalse(d.onInventoryAdded(Collections.singletonList(995), 51).isPresent());
        PetDetector.Drop drop = d.onInventoryAdded(Arrays.asList(995, 13320), 51).get();
        assertEquals("Heron", drop.pet.name);
        assertEquals("inventory", drop.via);
    }

    @Test
    public void inventoryIgnoredWithoutBackpackLine()
    {
        assertFalse(d.onInventoryAdded(Collections.singletonList(12646), 5).isPresent());
        d.onPetMessage(ChatParsers.Pet.FOLLOWING, 6);
        assertFalse(d.onInventoryAdded(Collections.singletonList(12646), 6).isPresent());
    }

    @Test
    public void collectionLogAfterOrBeforeTheLine()
    {
        d.onPetMessage(ChatParsers.Pet.FOLLOWING, 10);
        assertEquals("Olmlet", d.onCollectionLog("Olmlet", 10).get().pet.name);
        assertFalse(d.onFollower(7520, 11).isPresent()); // already reported

        PetDetector d2 = new PetDetector();
        assertFalse(d2.onCollectionLog("Tiny tempor", 20).isPresent());
        PetDetector.Drop drop = d2.onPetMessage(ChatParsers.Pet.BACKPACK, 21).get();
        assertEquals("Tiny tempor", drop.pet.name);
        assertEquals("collection_log", drop.via);
    }

    @Test
    public void collectionLogOfOrdinaryItemsIsIgnored()
    {
        d.onPetMessage(ChatParsers.Pet.FOLLOWING, 10);
        assertFalse(d.onCollectionLog("Abyssal whip", 10).isPresent());
        assertTrue(d.pending());
    }

    @Test
    public void staleEarlySignalIsNotPaired()
    {
        d.onCollectionLog("Vorki", 0);
        d.onTick(PetDetector.WINDOW_TICKS + 1);
        assertFalse(d.onPetMessage(ChatParsers.Pet.FOLLOWING, 500).isPresent());
    }

    @Test
    public void duplicateNamedByBroadcast()
    {
        d.onPetMessage(ChatParsers.Pet.DUPLICATE, 30);
        assertFalse(d.wantsFollower());
        PetDetector.Drop drop = d.onBroadcast("Vorki", true, 31).get();
        assertEquals("Vorki", drop.pet.name);
        assertTrue(drop.duplicate);
        assertEquals("broadcast", drop.via);
    }

    @Test
    public void duplicateAttributedToTheBossJustKilled()
    {
        d.onSource("Vorkath", 39);
        d.onPetMessage(ChatParsers.Pet.DUPLICATE, 40);
        assertFalse(d.onTick(40 + PetDetector.WINDOW_TICKS).isPresent());
        PetDetector.Drop drop = d.onTick(41 + PetDetector.WINDOW_TICKS).get();
        assertEquals("Vorki", drop.pet.name);
        assertTrue(drop.duplicate);
        assertEquals("source", drop.via);
    }

    @Test
    public void unidentifiedWhenNothingNamesThePet()
    {
        d.onSource("Goblin", 40);
        d.onPetMessage(ChatParsers.Pet.DUPLICATE, 40);
        Optional<PetDetector.Drop> drop = d.onTick(100);
        assertTrue(drop.isPresent());
        assertFalse(drop.get().identified());
        assertFalse(d.pending());
        assertFalse(d.onTick(101).isPresent());
    }

    @Test
    public void staleSourceIsNotUsed()
    {
        d.onSource("Vorkath", 0);
        d.onPetMessage(ChatParsers.Pet.DUPLICATE, 1000);
        assertFalse(d.onTick(1100).get().identified());
    }

    @Test
    public void newPetWithoutAnySignalIsReportedUnidentified()
    {
        d.onPetMessage(ChatParsers.Pet.FOLLOWING, 5);
        PetDetector.Drop drop = d.onTick(50).get();
        assertFalse(drop.identified());
        assertFalse(drop.duplicate);
    }

    @Test
    public void resetClearsState()
    {
        d.onPetMessage(ChatParsers.Pet.FOLLOWING, 1);
        d.reset();
        assertFalse(d.pending());
        assertFalse(d.onFollower(6635, 2).isPresent());
    }
}
