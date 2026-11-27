package com.forgebingo.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GroundGuardTest
{
    private static final int WHIP = 4151;
    private static final int DRAGON_BONES = 536;
    // a size-1 monster at (3200, 3200) on plane 0
    private static final int X = 3200, Y = 3200, P = 0;

    @Test
    public void aRealDropCounts()
    {
        GroundGuard g = new GroundGuard();
        assertTrue(g.fromMonster(WHIP, 100, X, Y, P, 1));
    }

    /** dropping your own item under the monster as it dies must not count as its drop */
    @Test
    public void anItemIDroppedMyselfNeverCounts()
    {
        GroundGuard g = new GroundGuard();
        g.onDropClicked(WHIP, 100);
        assertFalse(g.fromMonster(WHIP, 100, X, Y, P, 1));
        assertFalse(g.fromMonster(WHIP, 101, X, Y, P, 1));
        assertFalse(g.fromMonster(WHIP, 104, X, Y, P, 1));
        // the rest of that monster's loot still counts
        assertTrue(g.fromMonster(DRAGON_BONES, 101, X, Y, P, 1));
        // and a later, real whip drop counts again
        assertTrue(g.fromMonster(WHIP, 110, X, Y, P, 1));
    }

    /** another player's dropped item turning visible on the monster's tile in that tick must not count */
    @Test
    public void anotherPlayersItemNeverCounts()
    {
        GroundGuard g = new GroundGuard();
        g.onForeignItem(WHIP, 200, X, Y, P);
        assertFalse(g.fromMonster(WHIP, 200, X, Y, P, 1));
        assertFalse(g.fromMonster(WHIP, 201, X, Y, P, 1));
        assertTrue(g.fromMonster(WHIP, 205, X, Y, P, 1));
    }

    /** a busy spot: another player's copy of the item appearing elsewhere on screen does not hide a real drop */
    @Test
    public void anotherPlayersItemFarAwayDoesNotHideARealDrop()
    {
        GroundGuard g = new GroundGuard();
        g.onForeignItem(DRAGON_BONES, 300, X + 6, Y, P);
        g.onForeignItem(DRAGON_BONES, 300, X, Y, P + 1);
        assertTrue(g.fromMonster(DRAGON_BONES, 300, X, Y, P, 1));
    }

    /** a big monster: its loot can land on any of its tiles */
    @Test
    public void bigMonstersCoverTheirWholeArea()
    {
        GroundGuard g = new GroundGuard();
        g.onForeignItem(WHIP, 400, X + 4, Y + 4, P);
        assertFalse(g.fromMonster(WHIP, 400, X, Y, P, 5));
        assertTrue(g.fromMonster(WHIP, 400, X, Y, P, 1));
    }

    @Test
    public void oldRecordsAreForgotten()
    {
        GroundGuard g = new GroundGuard();
        g.onDropClicked(WHIP, 10);
        g.onForeignItem(WHIP, 10, X, Y, P);
        g.prune(100);
        assertTrue(g.fromMonster(WHIP, 10, X, Y, P, 1));
    }

    // a sea creature killed from a distance leaves the loot where it died (owner, 2026-11-27)
    @Test
    public void myOwnLootOnTheGroundIsMineToCollectOnce()
    {
        GroundGuard g = new GroundGuard();
        g.onOwnItem(WHIP, 100, X, Y, P);
        g.prune(400);
        assertTrue(g.takeOwnLoot(WHIP, X, Y, P));
        // answered once: the same tile does not count a second time
        assertFalse(g.takeOwnLoot(WHIP, X, Y, P));
    }

    @Test
    public void anItemIPutDownMyselfIsNotLoot()
    {
        GroundGuard g = new GroundGuard();
        g.onDropClicked(WHIP, 100);
        g.onOwnItem(WHIP, 101, X, Y, P);
        assertFalse(g.takeOwnLoot(WHIP, X, Y, P));
    }

    @Test
    public void lootElsewhereOrOfAnotherKindIsNotMixedUp()
    {
        GroundGuard g = new GroundGuard();
        g.onOwnItem(WHIP, 100, X, Y, P);
        assertFalse(g.takeOwnLoot(WHIP, X + 1, Y, P));
        assertFalse(g.takeOwnLoot(DRAGON_BONES, X, Y, P));
        assertTrue(g.takeOwnLoot(WHIP, X, Y, P));
    }

    @Test
    public void lootLeftLyingForTooLongIsForgotten()
    {
        GroundGuard g = new GroundGuard();
        g.onOwnItem(WHIP, 100, X, Y, P);
        g.prune(100 + GroundGuard.OWN_LOOT_KEEP + 1);
        assertFalse(g.takeOwnLoot(WHIP, X, Y, P));
    }
}
