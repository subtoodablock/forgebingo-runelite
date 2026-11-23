package com.forgebingo;

import static org.junit.Assert.assertTrue;

import com.forgebingo.core.OpeningAnim;
import java.awt.Rectangle;
import org.junit.Test;

/** Every opening animation draws, start to finish, without an error on a 5x5 and a 7x7 board. */
public class OpeningDrawTest
{
    private static final int[] TIMES = new int[17];

    static
    {
        for (int i = 0; i < TIMES.length; i++) TIMES[i] = i * 195;
    }

    @Test
    public void everyAnimationDrawsAtFiveAndSeven()
    {
        for (int size : new int[] {5, 7})
        {
            for (OpeningAnim.Spec spec : OpeningAnim.ALL)
            {
                try
                {
                    BoardOverlayPreview.play(640, 480, size, spec, null, TIMES);
                }
                catch (RuntimeException e)
                {
                    throw new AssertionError(spec + " on a " + size + "x" + size + " board", e);
                }
            }
        }
    }

    @Test
    public void theBoardIsWholeOnceTheOpeningEnds()
    {
        // after DEAL_MS every tile has landed: the frame equals a plain open board's
        Rectangle b = BoardOverlayPreview.boardRect(640, 480, 5);
        int[][] plain = new int[1][];
        BoardOverlayPreview.play(640, 480, 5, OpeningAnim.ALL.get(0), (t, img) -> plain[0] = img.getRGB(b.x, b.y, b.width, b.height, null, 0, b.width), 9000);
        for (OpeningAnim.Spec spec : new OpeningAnim.Spec[] {OpeningAnim.byName("The Vortex"), OpeningAnim.byName("Tempest"), OpeningAnim.byName("Thunderstrike")})
        {
            BoardOverlayPreview.play(640, 480, 5, spec, (t, img) -> {
                int[] px = img.getRGB(b.x, b.y, b.width, b.height, null, 0, b.width);
                int diff = 0;
                for (int i = 0; i < px.length; i++) if (px[i] != plain[0][i]) diff++;
                assertTrue(spec + " still differs in " + diff + " pixels", diff == 0);
            }, (int) OpeningAnim.DEAL_MS + 50);
        }
    }
}
