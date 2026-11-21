package com.forgebingo.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class TileInfoTest
{
    private static ApiModels.Task task(int need, int have, Integer points)
    {
        ApiModels.Task k = new ApiModels.Task();
        k.requiredQty = need;
        k.approvedQty = have;
        k.completed = have >= need;
        k.points = points == null ? null : points.doubleValue();
        k.description = "task";
        return k;
    }

    private static ApiModels.Tile tile(String title, ApiModels.Task... tasks)
    {
        ApiModels.Tile t = new ApiModels.Tile();
        t.title = title;
        t.points = 1;
        t.tasks = new ArrayList<>(Arrays.asList(tasks));
        return t;
    }

    @Test
    public void secretTilesWithAndWithoutTheServerFlag()
    {
        assertTrue(TileInfo.isSecret(tile("?")));                // older server: "?" and no tasks
        ApiModels.Tile flagged = tile("?");
        flagged.secret = false;                                  // revealed tile literally named "?"
        assertFalse(TileInfo.isSecret(flagged));
        assertEquals(TileInfo.State.SECRET, TileInfo.state(tile("?")));
    }

    @Test
    public void hiddenTileShowsTheOrganizersNameOrSecretTile()
    {
        assertEquals("Secret tile", TileInfo.shownTitle(tile("?")));
        ApiModels.Tile named = tile("Mystery Wildy tile");
        named.secret = true;
        assertEquals("Mystery Wildy tile", TileInfo.shownTitle(named));
        assertEquals("Bandos hilt", TileInfo.shownTitle(tile("Bandos hilt", new ApiModels.Task())));
    }

    @Test
    public void worthAddsTaskPointsAndFallsBackToTilePoints()
    {
        assertEquals(3, TileInfo.worth(tile("Rings", task(1, 0, 1), task(1, 0, 1), task(1, 0, 1))), 0);
        assertEquals(1, TileInfo.worth(tile("Old server", task(1, 0, null))), 0);
        ApiModels.Tile any = tile("Any", task(1, 0, 2), task(1, 0, 5));
        any.completionMode = "any";
        assertEquals(5, TileInfo.worth(any), 0);
    }

    private static ApiModels.Task option(int group, int points, boolean done)
    {
        ApiModels.Task k = task(1, done ? 1 : 0, points);
        k.group = group;
        return k;
    }

    @Test
    public void orOptionsScoreOnlyTheFinishedOptionLikeTheServer()
    {
        // Task 1: Dragon spear (2) OR Zulrah drop (4); Task 2: Bandos drop (3)
        ApiModels.Tile t = tile("Mixed", option(1, 2, false), option(1, 4, false), task(1, 0, 3));
        t.completionMode = "all";
        assertEquals(7, TileInfo.worth(t), 0);                       // best option + the other task, not 2 + 4 + 3
        assertEquals(0, TileInfo.earned(t), 0);
        t.tasks.get(0).completed = true;                          // the spear: only its 2 points
        t.tasks.get(0).approvedQty = 1;
        assertEquals(2, TileInfo.earned(t), 0);
        assertEquals(0.5, TileInfo.progress(t), 1e-9);            // 1 of 2 tasks, not 1 of 3 options
        assertEquals("1/2 tasks", TileInfo.countText(t));
        t.tasks.get(1).completed = true;                          // both options done: counts once, the best
        assertEquals(4, TileInfo.earned(t), 0);
        t.tasks.get(2).completed = true;                          // the "and" task scores on its own
        assertEquals(7, TileInfo.earned(t), 0);
        assertEquals(2, TileInfo.taskGroups(t).size());
    }

    private static ApiModels.Task dec(double points, boolean done, Integer group)
    {
        ApiModels.Task k = task(1, done ? 1 : 0, null);
        k.points = points;
        k.group = group;
        return k;
    }

    @Test
    public void decimalPointsAddUpExactlyAndPrintWithoutTrailingZeros()
    {
        // 0.1 and 0.2 on their own, then a task with OR options worth 0.04 or 0.25
        ApiModels.Tile t = tile("Decimals", dec(0.1, true, null), dec(0.2, true, null), dec(0.04, true, 1), dec(0.25, false, 1));
        t.completionMode = "all";
        assertEquals(0.55, TileInfo.worth(t), 0);                 // 0.1 + 0.2 + best option 0.25, no float drift
        assertEquals(0.34, TileInfo.earned(t), 0);                // only the finished option (0.04) counts
        t.tasks.get(3).completed = true;
        assertEquals(0.55, TileInfo.earned(t), 0);                // both options done: the best one, once
        ApiModels.Tile free = tile("Free space");
        free.points = 0.25;
        free.completed = true;
        assertEquals(0.25, TileInfo.worth(free), 0);
        assertEquals(0.25, TileInfo.earned(free), 0);

        assertEquals("0.25", Points.text(0.25));
        assertEquals("1.5", Points.text(1.50));
        assertEquals("3", Points.text(3.0));
        assertEquals("0.3", Points.text(0.1 + 0.2));
        assertEquals("0.04", Points.text(0.04));
        assertEquals("1234.5", Points.text(1234.5));
        assertEquals("0", Points.text(Double.NaN));
        assertEquals("0", Points.text(-2));
        assertEquals("1 pt", Points.label(1));
        assertEquals("0.25 pts", Points.label(0.25));
        assertEquals("3 pts", Points.label(3));
        assertEquals("0 pts", Points.label(0));
        assertEquals(0.3, Points.round(0.1 + 0.2), 0);
        assertEquals(0.13, Points.round(0.129), 0);
    }

    @Test
    public void andTasksScoreAsSoonAsEachIsDone()
    {
        ApiModels.Tile t = tile("Two", task(1, 1, 3), task(1, 0, 5));
        assertEquals(8, TileInfo.worth(t), 0);
        assertEquals(3, TileInfo.earned(t), 0);
    }

    @Test
    public void needsChangesOnlyWhileNothingIsWaiting()
    {
        ApiModels.Task k = task(1, 0, 1);
        k.rejectedReason = "Drop message not visible";
        ApiModels.Tile t = tile("Whip", k);
        assertEquals("Drop message not visible", TileInfo.needsChangesReason(t));
        assertEquals(TileInfo.State.NEEDS_CHANGES, TileInfo.state(t));
        k.pendingQty = 1;                                        // new proof sent: waiting again
        assertNull(TileInfo.needsChangesReason(t));
        assertEquals(TileInfo.State.WAITING, TileInfo.state(t));
    }

    @Test
    public void nextUpPrefersTheClosestTiles()
    {
        ApiModels.Tile far = tile("Far", task(10, 1, 1));
        ApiModels.Tile close = tile("Close", task(10, 8, 1));
        ApiModels.Tile done = tile("Done", task(1, 1, 1));
        done.completed = true;
        List<ApiModels.Tile> next = TileInfo.nextUp(Arrays.asList(far, close, done, tile("?")), 3);
        assertEquals(2, next.size());
        assertEquals("Close", next.get(0).title);
        assertEquals("8/10", TileInfo.countText(close));
    }
}
