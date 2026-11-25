package com.forgebingo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import javax.swing.JComponent;
import javax.swing.JLayer;
import javax.swing.JPanel;
import org.junit.Test;

/** The side panel's creatures and falling items: when they are allowed out, and how often it rains. */
public class PanelImpsTest
{
    private static final float STEP = 0.033f;

    private static PanelImps onPanel()
    {
        PanelImps imps = new PanelImps();
        JLayer<JComponent> layer = new JLayer<>(new JPanel(), imps);
        layer.setSize(240, 600);
        return imps;
    }

    private static void run(PanelImps imps, float seconds)
    {
        for (float t = 0; t < seconds; t += STEP) imps.advance(STEP);
    }

    @Test
    public void nothingWalksOrFallsUntilABoardIsLinked()
    {
        PanelImps imps = onPanel();
        imps.setLinked(false);
        run(imps, 900f);
        assertEquals("no creatures before a board is linked", 0, imps.creatures());
        assertEquals("no rain before a board is linked", 0, imps.rainEvents);

        imps.setLinked(true);
        run(imps, 5f);
        assertTrue("the creatures come out once a board is linked", imps.creatures() > 0);

        imps.setLinked(false);
        assertEquals("and leave again when it is not", 0, imps.creatures());
    }

    @Test
    public void theSettingStillWinsOverALinkedBoard()
    {
        PanelImps imps = onPanel();
        imps.setEnabled(false);
        imps.setRain(false);
        imps.setLinked(false);
        imps.setLinked(true);
        run(imps, 1800f);
        assertEquals(0, imps.creatures());
        assertEquals(0, imps.rainEvents);
    }

    @Test
    public void itRainsAboutEveryFiveMinutesAndNeverStraightAway()
    {
        int total = 0;
        for (int i = 0; i < 6; i++)
        {
            PanelImps imps = onPanel();
            imps.setEnabled(false); // the rain alone
            imps.setLinked(false);
            imps.setLinked(true);
            run(imps, 55f);
            assertEquals("the first fall is a surprise, not a welcome shower", 0, imps.rainEvents);
            run(imps, 3600f - 55f);
            // an hour: gaps of 2.5 to 7.5 minutes after a first fall within 5 minutes
            assertTrue("rained " + imps.rainEvents + " times in an hour", imps.rainEvents >= 8 && imps.rainEvents <= 24);
            total += imps.rainEvents;
        }
        // five minutes apart on average: about 12 an hour
        assertTrue("averaged " + total / 6f + " an hour", total / 6f > 9f && total / 6f < 15f);
    }
}
