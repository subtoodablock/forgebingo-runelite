package com.forgebingo.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class ActivityWatcherTest
{
    private static final int FIGHTER_TORSO = 10551;   // Barbarian Assault store
    private static final int OATHPLATE_HELM = 30750;  // Forgotten lockbox
    private static final int WHIP = 4151;             // not an activity unique

    @Test
    public void storePurchaseIsReportedNextTick()
    {
        ActivityWatcher w = new ActivityWatcher();
        w.onInventoryAdded(FIGHTER_TORSO, 1, 100);
        assertTrue(w.flush(100, false).isEmpty()); // held until the tick's other container events are in
        List<ActivityWatcher.Drop> d = w.flush(101, false);
        assertEquals(1, d.size());
        assertEquals(FIGHTER_TORSO, d.get(0).itemId);
        assertEquals("Barbarian Assault", d.get(0).activity);
    }

    @Test
    public void duplicatesCountEveryTime()
    {
        ActivityWatcher w = new ActivityWatcher();
        w.onInventoryAdded(OATHPLATE_HELM, 1, 10);
        assertEquals(1, w.flush(11, false).size());
        w.onInventoryAdded(OATHPLATE_HELM, 1, 50);
        assertEquals(1, w.flush(51, false).size());
    }

    /** owner, 2026-11-17: an item another player traded over counted as an Archive reward */
    @Test
    public void tradedItemsNeverCount()
    {
        ActivityWatcher w = new ActivityWatcher();
        // the trade window is open for a while, closes on tick 203, the items land on that tick
        for (int t = 190; t <= 203; t++) w.onTrade(t);
        w.onInventoryAdded(34717, 1, 203);
        assertTrue(w.flush(204, false).isEmpty());
        // items that land a couple of ticks after the window closed are still the trade's
        w.onTrade(300);
        w.onInventoryAdded(34717, 1, 302);
        assertTrue(w.flush(303, false).isEmpty());
        // a real reward long after any trade still counts
        w.onInventoryAdded(34717, 1, 400);
        assertEquals(1, w.flush(401, false).size());
    }

    /** owner, 2026-11-14: taking off an Elemental amulet (whose worn form has another id) counted as an Archive reward */
    @Test
    public void gearTakenOffNeverCountsEvenWithAnotherId()
    {
        ActivityWatcher w = new ActivityWatcher();
        w.onWornChanged(300);
        w.onInventoryAdded(34428, 1, 300); // Elemental amulet back in the backpack
        assertTrue(w.flush(301, false).isEmpty());
        w.onWornChanged(400);
        w.onInventoryAdded(34428, 1, 401); // a tick later still counts as the same swap
        assertTrue(w.flush(402, false).isEmpty());
        // a real reward with no gear change still counts
        w.onInventoryAdded(34428, 1, 500);
        assertEquals(1, w.flush(501, false).size());
    }

    @Test
    public void bankWithdrawEquipmentAndTradeAreMoves()
    {
        ActivityWatcher w = new ActivityWatcher();
        w.onLeftContainer(FIGHTER_TORSO, 200);
        w.onInventoryAdded(FIGHTER_TORSO, 1, 200);
        assertTrue(w.flush(201, false).isEmpty());
    }

    @Test
    public void groundPickupsNeverCount()
    {
        ActivityWatcher w = new ActivityWatcher();
        w.onGroundItemGone(OATHPLATE_HELM, 300);
        w.onInventoryAdded(OATHPLATE_HELM, 1, 300);
        assertTrue(w.flush(301, false).isEmpty());
    }

    @Test
    public void nothingCountsInPvp()
    {
        ActivityWatcher w = new ActivityWatcher();
        w.onInventoryAdded(FIGHTER_TORSO, 1, 5);
        assertTrue(w.flush(6, true).isEmpty());
    }

    @Test
    public void lootEventAlreadyReportedIt()
    {
        ActivityWatcher w = new ActivityWatcher();
        w.onLoot(OATHPLATE_HELM, 400);
        w.onInventoryAdded(OATHPLATE_HELM, 1, 403);
        assertTrue(w.flush(404, false).isEmpty());
    }

    @Test
    public void grandExchangeCollectIsNotADrop()
    {
        ActivityWatcher w = new ActivityWatcher();
        w.onGrandExchange(700);
        w.onInventoryAdded(OATHPLATE_HELM, 1, 701);
        assertTrue(w.flush(702, false).isEmpty());
    }

    @Test
    public void anItemChangingFormIsNotADrop()
    {
        // closing a looting bag: 22586 leaves, 11941 (Last Man Standing store item) arrives in the same tick
        ActivityWatcher w = new ActivityWatcher();
        w.onItemsLeftInventory(500);
        w.onInventoryAdded(11941, 1, 500);
        assertTrue(w.flush(501, false).isEmpty());
        // but a plain purchase (only a currency left) still counts
        w.onInventoryAdded(FIGHTER_TORSO, 1, 600);
        assertEquals(1, w.flush(601, false).size());
    }

    @Test
    public void chestItemsClaimedMinutesAfterTheLootEventAreNotCountedTwice()
    {
        ActivityWatcher w = new ActivityWatcher();
        w.onLoot(OATHPLATE_HELM, 1000);
        w.onInventoryAdded(OATHPLATE_HELM, 1, 1000 + 200); // claimed 2 minutes later
        assertTrue(w.flush(1201, false).isEmpty());
    }

    @Test
    public void ordinaryItemsAreIgnored()
    {
        ActivityWatcher w = new ActivityWatcher();
        w.onInventoryAdded(WHIP, 1, 1);
        assertTrue(w.flush(2, false).isEmpty());
        assertEquals(0, w.pendingCount());
    }

    @Test
    public void tableCoversTheRequestedActivities()
    {
        assertEquals("Forgotten lockbox", ActivityItems.activityFor(OATHPLATE_HELM));
        assertEquals("Mage Training Arena", ActivityItems.activityFor(6916)); // Infinity top
        assertEquals("Pyramid Plunder", ActivityItems.activityFor(9044));     // Pharaoh's sceptre
        assertEquals("Brimstone chest", ActivityItems.activityFor(22963));    // Broken dragon hasta
        assertEquals("The Fractured Archive", ActivityItems.activityFor(34428)); // Elemental amulet
        assertEquals("Doom of Mokhaiotl", ActivityItems.activityFor(31088)); // Avernic treads
        assertEquals("TzTok-Jad", ActivityItems.activityFor(6570));          // Fire cape, every one (duplicates too)
        assertEquals("TzKal-Zuk", ActivityItems.activityFor(21295));         // Infernal cape
    }
}
