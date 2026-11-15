package com.forgebingo.core;

import static org.junit.Assert.assertFalse;
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
}
