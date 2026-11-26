package com.forgebingo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import org.junit.Test;

/** The "not open yet" card in the side panel: what it says under the bingo and team name. */
public class UpcomingTextTest
{
    private static final long NOW = Instant.parse("2026-11-26T12:00:00Z").toEpochMilli();

    @Test
    public void countsDownToAStartTimeThatIsAhead()
    {
        assertEquals(65, ForgeBingoPanel.secondsUntil("2026-11-26T12:01:05Z", NOW));
        assertEquals("Opens in 1m 05s", ForgeBingoPanel.opensText(65));
        assertEquals("Opens in 2h 13m 05s", ForgeBingoPanel.opensText(2 * 3600 + 13 * 60 + 5));
        assertEquals("Opens in 3d 4h 0m", ForgeBingoPanel.opensText(3 * 86400 + 4 * 3600));
        assertEquals(3600, ForgeBingoPanel.secondsUntil("2026-11-26T14:00:00+01:00", NOW));
    }

    @Test
    public void saysNotOpenYetWhenThereIsNothingToCountDownTo()
    {
        // a start time that has passed while the bingo is still closed (set up, or paused): no endless "opening..."
        assertEquals(0, ForgeBingoPanel.secondsUntil("2026-11-21T08:25:38Z", NOW));
        assertTrue(ForgeBingoPanel.opensText(0).startsWith("Not open yet"));
        // no start time, or one that cannot be read
        assertEquals(-1, ForgeBingoPanel.secondsUntil(null, NOW));
        assertEquals(-1, ForgeBingoPanel.secondsUntil("soon", NOW));
        assertTrue(ForgeBingoPanel.opensText(-1).startsWith("Not open yet"));
    }
}
