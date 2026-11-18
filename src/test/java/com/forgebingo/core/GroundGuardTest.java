package com.forgebingo.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GroundGuardTest
{
    private static final int WHIP = 4151;
    private static final int DRAGON_BONES = 536;

    @Test
    public void aRealDropCounts()
    {
        GroundGuard g = new GroundGuard();
        assertTrue(g.fromMonster(WHIP, 100));
    }

    /** dropping your own item under the monster as it dies must not count as its drop */
    @Test
    public void anItemIDroppedMyselfNeverCounts()
    {
        GroundGuard g = new GroundGuard();
        g.onDropClicked(WHIP, 100);
        assertFalse(g.fromMonster(WHIP, 100));
        assertFalse(g.fromMonster(WHIP, 101));
        assertFalse(g.fromMonster(WHIP, 104));
        // the rest of that monster's loot still counts
        assertTrue(g.fromMonster(DRAGON_BONES, 101));
        // and a later, real whip drop counts again
        assertTrue(g.fromMonster(WHIP, 110));
    }

    /** another player's dropped item turning visible on the tile in that tick must not count */
    @Test
    public void anotherPlayersItemNeverCounts()
    {
        GroundGuard g = new GroundGuard();
        g.onForeignItem(WHIP, 200);
        assertFalse(g.fromMonster(WHIP, 200));
        assertFalse(g.fromMonster(WHIP, 201));
        assertTrue(g.fromMonster(WHIP, 205));
    }

    @Test
    public void oldRecordsAreForgotten()
    {
        GroundGuard g = new GroundGuard();
        g.onDropClicked(WHIP, 10);
        g.onForeignItem(WHIP, 10);
        g.prune(100);
        assertTrue(g.fromMonster(WHIP, 10));
    }
}
