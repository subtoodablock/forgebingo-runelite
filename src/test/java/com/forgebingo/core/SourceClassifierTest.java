package com.forgebingo.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SourceClassifierTest
{
    @Test
    public void lootKeyChestsNeverCount()
    {
        assertTrue(SourceClassifier.ignored("Loot Chest"));
        assertTrue(SourceClassifier.ignored("Loot key"));
        assertFalse(SourceClassifier.ignored("Barrows"));
        assertFalse(SourceClassifier.ignored("Chambers of Xeric"));
        assertFalse(SourceClassifier.ignored(null));
    }

    @Test
    public void cluesAndChests()
    {
        assertEquals(GameEvent.CLUE, SourceClassifier.forEventLoot("Reward casket (hard)"));
        assertEquals("hard", SourceClassifier.clueTier("Reward casket (hard)"));
        assertEquals(GameEvent.RAID_CHEST, SourceClassifier.forEventLoot("Barrows"));
    }
}
