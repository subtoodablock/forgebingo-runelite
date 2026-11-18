package com.forgebingo.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VersionsAndActivityTest
{
    @Test
    public void aHigherVersionIsNewer()
    {
        assertTrue(Versions.newer("2.1.0", "2.0.0"));
        assertTrue(Versions.newer("2.10.0", "2.9.9"));
        assertTrue(Versions.newer("3", "2.9.9"));
        assertTrue(Versions.newer("2.1.1", "2.1"));
        assertFalse(Versions.newer("2.1.0", "2.1.0"));
        assertFalse(Versions.newer("2.0.0", "2.1.0"));
        assertFalse(Versions.newer("2.1.0", "2.1.0-test"));
    }

    @Test
    public void anUnreadableVersionIsNeverNewer()
    {
        assertFalse(Versions.newer(null, "2.0.0"));
        assertFalse(Versions.newer("", "2.0.0"));
        assertFalse(Versions.newer("latest", "2.0.0"));
        assertFalse(Versions.newer("2.1.0", null));
    }

    @Test
    public void theLogKeepsTheNewestFewAndTicksPictures()
    {
        ActivityLog log = new ActivityLog();
        assertNull(log.newest(0));
        for (int i = 0; i < 10; i++) log.counted("e" + i, "Kill " + i, i * 1000L);
        assertEquals(ActivityLog.MAX, log.lines(10_000).size());
        assertTrue(log.newest(9_000).startsWith("Counted: Kill 9 (just now)"));
        log.pictureSent("e9");
        assertTrue(log.lines(10_000).get(0).endsWith("picture sent"));
        assertFalse(log.lines(10_000).get(1).contains("picture"));
        log.pictureSent("gone"); // an entry that already dropped off the end: nothing happens
    }

    @Test
    public void timesReadNaturally()
    {
        assertEquals("just now", ActivityLog.ago(0, 30_000));
        assertEquals("2m ago", ActivityLog.ago(0, 120_000));
        assertEquals("1h ago", ActivityLog.ago(0, 3_600_000));
        assertEquals("2d ago", ActivityLog.ago(0, 2 * 86_400_000L));
    }
}
