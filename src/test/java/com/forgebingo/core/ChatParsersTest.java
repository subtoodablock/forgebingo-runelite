package com.forgebingo.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class ChatParsersTest
{
    @Test
    public void collectionLogSlot()
    {
        assertEquals("Abyssal whip", ChatParsers.collectionLogItem("New item added to your collection log: Abyssal whip").get());
        assertEquals("Tumeken's shadow (uncharged)",
            ChatParsers.collectionLogItem("<col=ef1020>New item added to your collection log: Tumeken's shadow (uncharged)</col>").get());
    }

    @Test
    public void collectionLogRejectsOther()
    {
        assertFalse(ChatParsers.collectionLogItem("You added an item to your bank").isPresent());
        assertFalse(ChatParsers.collectionLogItem("New item added to your collection log:").isPresent());
        assertFalse(ChatParsers.collectionLogItem(null).isPresent());
        // a player cannot spoof via a prefix
        assertFalse(ChatParsers.collectionLogItem("Zezima: New item added to your collection log: Dragon pickaxe").isPresent());
    }

    @Test
    public void petMessages()
    {
        assertEquals(ChatParsers.Pet.FOLLOWING, ChatParsers.pet("You have a funny feeling like you're being followed.").get());
        assertEquals(ChatParsers.Pet.FOLLOWING, ChatParsers.pet("You have a funny feeling like you’re being followed.").get());
        assertEquals(ChatParsers.Pet.BACKPACK, ChatParsers.pet("You feel something weird sneaking into your backpack.").get());
        assertEquals(ChatParsers.Pet.DUPLICATE,
            ChatParsers.pet("You have a funny feeling like you would have been followed...").get());
        assertEquals(ChatParsers.Pet.DUPLICATE,
            ChatParsers.pet("You have a funny feeling like you would have been followed.").get());
        assertEquals(ChatParsers.Pet.FOLLOWING,
            ChatParsers.pet("<col=ef1020>You have a funny feeling like you're being followed.</col>").get());
        assertTrue(ChatParsers.Pet.BACKPACK.isNew());
        assertFalse(ChatParsers.Pet.DUPLICATE.isNew());
        assertFalse(ChatParsers.pet("You have a funny feeling about this.").isPresent());
        assertFalse(ChatParsers.pet("Zezima: You have a funny feeling like you're being followed.").isPresent());
    }

    @Test
    public void killCountVariants()
    {
        ChatParsers.Kc k = ChatParsers.killCount("Your Zulrah kill count is: <col=ff0000>1234</col>.").get();
        assertEquals("Zulrah", k.boss);
        assertEquals(1234, k.count);

        k = ChatParsers.killCount("Your Vorkath kill count is: 5.").get();
        assertEquals("Vorkath", k.boss);
        assertEquals(5, k.count);

        k = ChatParsers.killCount("Your completed Theatre of Blood count is: 12.").get();
        assertEquals("Theatre of Blood", k.boss);
        assertEquals(12, k.count);

        k = ChatParsers.killCount("Your Barrows chest count is: 300.").get();
        assertEquals("Barrows", k.boss);

        k = ChatParsers.killCount("Your subdued Wintertodt count is: 40.").get();
        assertEquals("Wintertodt", k.boss);

        k = ChatParsers.killCount("Your Chambers of Xeric count is: 7.").get();
        assertEquals("Chambers of Xeric", k.boss);
        assertEquals(7, k.count);

        k = ChatParsers.killCount("Your Hallowed Sepulchre lap count is: 9.").get();
        assertEquals("Hallowed Sepulchre", k.boss);
    }

    @Test
    public void killCountThousandsAndCompletionFor()
    {
        ChatParsers.Kc k = ChatParsers.killCount("Your Zulrah kill count is: <col=ff0000>1,234</col>.").get();
        assertEquals("Zulrah", k.boss);
        assertEquals(1234, k.count);
        k = ChatParsers.killCount("Your completion count for Tombs of Amascut: Expert Mode is: 2,001.").get();
        assertEquals("Tombs of Amascut: Expert Mode", k.boss);
        assertEquals(2001, k.count);
        k = ChatParsers.killCount("Your Lunar Chest count is: 3.").get();
        assertEquals("Lunar Chest", k.boss);
        assertFalse(ChatParsers.killCount("Your Zulrah kill count is: 9,999,999,999.").isPresent());
    }

    @Test
    public void clueCompletions()
    {
        ChatParsers.Clue c = ChatParsers.clueCompletion("<col=ef1020>You have completed 12 hard Treasure Trails.</col>").get();
        assertEquals("hard", c.tier);
        assertEquals(12, c.count);
        assertEquals("Clue scroll (hard)", c.sourceName());
        c = ChatParsers.clueCompletion("You have completed 1 beginner Treasure Trail.").get();
        assertEquals("beginner", c.tier);
        assertEquals(1, c.count);
        c = ChatParsers.clueCompletion("You have completed 1,502 Elite Treasure Trails.").get();
        assertEquals("elite", c.tier);
        assertEquals(1502, c.count);
        for (String t : new String[]{"easy", "medium", "master"})
        {
            assertEquals(t, ChatParsers.clueCompletion("You have completed 3 " + t + " Treasure Trails.").get().tier);
        }
        assertFalse(ChatParsers.clueCompletion("You have completed 3 shiny Treasure Trails.").isPresent());
        assertFalse(ChatParsers.clueCompletion("Bob: You have completed 3 hard Treasure Trails.").isPresent());
        assertFalse(ChatParsers.clueCompletion("You have completed the Treasure Trail.").isPresent());
    }

    @Test
    public void petBroadcasts()
    {
        ChatParsers.PetBroadcast b = ChatParsers.petBroadcast(
            "<img=2>Zezima has a funny feeling like he's being followed: Vorki at 1,234 killcount.").get();
        assertEquals("Zezima", b.player);
        assertEquals("Vorki", b.petName);
        assertFalse(b.duplicate);
        b = ChatParsers.petBroadcast("Iron Man 1 feels something weird sneaking into her backpack: Heron at 99 Fishing.").get();
        assertEquals("Iron Man 1", b.player);
        assertEquals("Heron", b.petName);
        assertFalse(b.duplicate);
        b = ChatParsers.petBroadcast("Zezima has a funny feeling like they would have been followed: Lil' zik at 50 killcount.").get();
        assertEquals("Lil' zik", b.petName);
        assertTrue(b.duplicate);
        b = ChatParsers.petBroadcast("Zezima has a funny feeling like they're being followed: Pet snakeling.").get();
        assertEquals("Pet snakeling", b.petName);
        assertFalse(ChatParsers.petBroadcast("Zezima received a drop: Vorki").isPresent());
    }

    @Test
    public void killCountRejects()
    {
        assertFalse(ChatParsers.killCount("Your Zulrah kill count is: lots.").isPresent());
        assertFalse(ChatParsers.killCount("Your Zulrah kill count is: 99999999999999.").isPresent());
        assertFalse(ChatParsers.killCount("Bob: Your Zulrah kill count is: 5.").isPresent());
        assertFalse(ChatParsers.killCount("Fight duration: 1:23. Personal best: 1:20").isPresent());
    }

    @Test
    public void stripTags()
    {
        assertEquals("a b", ChatParsers.stripTags("<col=ff0000>a</col> b"));
        assertEquals("", ChatParsers.stripTags(null));
    }

    @Test
    public void sourceClassifier()
    {
        assertEquals(GameEvent.CLUE, SourceClassifier.forEventLoot("Reward Casket (elite)"));
        assertEquals(GameEvent.RAID_CHEST, SourceClassifier.forEventLoot("Chambers of Xeric"));
        assertEquals(GameEvent.RAID_CHEST, SourceClassifier.forEventLoot("Barrows"));
        assertEquals(GameEvent.RAID_CHEST, SourceClassifier.forEventLoot(null));
        assertEquals(GameEvent.CLUE, SourceClassifier.forEventLoot("Clue Scroll (Master)"));
        assertEquals("master", SourceClassifier.clueTier("Clue Scroll (Master)"));
    }
}
