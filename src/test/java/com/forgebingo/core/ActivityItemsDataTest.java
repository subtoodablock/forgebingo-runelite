package com.forgebingo.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Map;
import org.junit.Test;

/**
 * Proof that moving the activity-item table into data/activity-items.txt changed nothing: what the plugin loads
 * (ActivityItems) equals the old Java table (ActivityItemsReference) exactly. Also fails when the data file is
 * missing, empty or malformed (the table would then be empty), and checks the parser refuses bad files whole.
 */
public class ActivityItemsDataTest
{
    private static final String FIX = " - data/activity-items.txt and ActivityItemsReference differ; change both together";

    @Test
    public void theLoadedTableIsExactlyTheOldJavaTable()
    {
        Map<Integer, String> ref = ActivityItemsReference.all();
        assertTrue("the reference has rows", ref.size() > 200);
        assertEquals("size()" + FIX, ref.size(), ActivityItems.size());
        // same keys, same values
        assertEquals("parsed file" + FIX, ref, ActivityItems.parse(DataFile.lines("activity-items")));
        // and through the public door, for every id anywhere near the table and well beyond (so no extra ids either)
        int max = 0;
        for (int id : ref.keySet()) max = Math.max(max, id);
        int hits = 0;
        for (int id = -10; id <= max + 10_000; id++)
        {
            String want = ref.get(id);
            assertEquals("item " + id + FIX, want, ActivityItems.activityFor(id));
            if (want != null) hits++;
        }
        assertEquals(ref.size(), hits);
        System.out.println("ActivityItemsDataTest: compared " + ref.size() + " rows, " + (max + 10_011) + " ids probed");
    }

    @Test
    public void aBrokenFileIsRefusedWhole()
    {
        refused("ids before any activity", "123 456");
        refused("not a number", "[Sailing]", "123 abc");
        refused("an id twice", "[Sailing]", "123", "[Pest Control]", "123");
        refused("an id twice in one activity", "[Sailing]", "123 123");
        refused("no activity name", "[]", "123");
        refused("a decimal", "[Sailing]", "12.5");
        assertEquals(2, ActivityItems.parse(Arrays.asList("[Sailing]", "1  2")).size());
        try
        {
            DataFile.lines("no-such-file");
            fail("a missing data file must be an error");
        }
        catch (IllegalStateException expected)
        {
            // good
        }
    }

    private static void refused(String what, String... lines)
    {
        try
        {
            ActivityItems.parse(Arrays.asList(lines));
            fail("a file with " + what + " must be refused");
        }
        catch (RuntimeException expected)
        {
            // good
        }
    }
}
