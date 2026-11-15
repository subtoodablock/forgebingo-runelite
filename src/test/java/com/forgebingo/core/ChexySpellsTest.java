package com.forgebingo.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import org.junit.Test;

public class ChexySpellsTest
{
    @Test
    public void spellsFitTheVisitAndKeepClearOfTheCharges()
    {
        // a spell visit: flowers, the barrier once they have faded, one volley, then a second bloom, all over before
        // the shortest visit ends (60 s)
        float flowersGone = ChexySpells.FLOWER_AT + ChexySpells.FLOWER_SPAWN + ChexySpells.FLOWER_LIFE;
        assertTrue(flowersGone < ChexySpells.BARRIER_AT);
        assertTrue(ChexySpells.BARRIER_AT + ChexySpells.BARRIER_TIME < ChexySpells.VOLLEYS[0]);
        assertEquals(1, ChexySpells.VOLLEYS.length);
        assertTrue(ChexySpells.VOLLEYS[0] + ChexySpells.VOLLEY_TIME < ChexySpells.FLOWER_AGAIN);
        assertTrue(ChexySpells.FLOWER_AGAIN + ChexySpells.FLOWER_SPAWN + ChexySpells.FLOWER_LIFE < 60f);
        // it stands still to fire
        assertEquals(0f, ChexySpells.flySpeed(ChexySpells.VOLLEYS[0] + 1f), 0f);
        assertEquals(1f, ChexySpells.flySpeed(40f), 0f);
    }

    @Test
    public void aVisitIsThePowerUpOrTheSpellsNeverBoth()
    {
        for (boolean spellVisit : new boolean[] {false, true})
        {
            boolean powered = false, cast = false;
            for (float age = 0f; age < 95f; age += 0.05f)
            {
                float power = Chexy.powerAge(spellVisit, age);
                float spell = ChexySpells.spellAge(spellVisit, age);
                boolean powering = Chexy.form(power) != Chexy.BASE || Chexy.charge(power) > 0 || Chexy.spin(power) != 1f;
                boolean casting = ChexySpells.flowerPhase(spell) >= 0 || ChexySpells.barrierPhase(spell) >= 0
                    || ChexySpells.volleyPhase(spell) >= 0;
                assertTrue("both at once at " + age, !(powering && casting));
                powered |= powering;
                cast |= casting;
            }
            // a spell visit casts and never powers up; a power-up visit powers up and never casts
            assertEquals(!spellVisit, powered);
            assertEquals(spellVisit, cast);
        }
        // a power-up visit reaches its blue form well inside the shortest visit
        assertEquals(Chexy.BLUE, Chexy.form(Chexy.powerAge(false, 45f)));
    }

    @Test
    public void everySpellDrawsThroughItsWholeLength()
    {
        Rectangle box = new Rectangle(0, 0, 160, 120);
        float[] kick = new float[2];
        for (boolean calm : new boolean[] {false, true})
        {
            for (float p = 0f; p < ChexySpells.VOLLEY_TIME; p += 0.13f)
            {
                BufferedImage img = new BufferedImage(160, 120, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = img.createGraphics();
                ChexySpells.paintVolley(g, 40, 60, 1, p, p * 1.3f, Chexy.GOLD, calm, box);
                ChexySpells.paintVolley(g, 120, 60, -1, p, p * 1.3f, Chexy.BLUE, calm, box);
                ChexySpells.recoil(p, 1, calm, kick);
                assertTrue(Math.abs(kick[0]) < 5 && Math.abs(kick[1]) < 5);
                g.dispose();
            }
            for (float p = 0f; p < ChexySpells.BARRIER_TIME; p += 0.13f)
            {
                BufferedImage img = new BufferedImage(120, 120, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = img.createGraphics();
                ChexySpells.paintBarrier(g, 60, 60, 25, p, p, Chexy.GOLD, calm);
                g.dispose();
            }
            BufferedImage img = new BufferedImage(120, 80, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            for (float s = -0.5f; s < ChexySpells.FLOWER_LIFE + 0.5f; s += 0.1f)
            {
                for (int kind = 0; kind < ChexySpells.FLOWER_KINDS; kind++)
                {
                    ChexySpells.paintFlower(g, 20 + kind * 20, 60, kind, s, ChexySpells.FLOWER_LIFE, s);
                    ChexySpells.paintPetals(g, 20 + kind * 20, 60, kind, s, kind, calm);
                }
                ChexySpells.paintWave(g, 60, 60, s, calm);
            }
            g.dispose();
        }
    }

    @Test
    public void aFlowerShowsOnceGrownAndIsGoneAfterItsLife()
    {
        BufferedImage img = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        ChexySpells.paintFlower(g, 10, 18, 0, 2f, ChexySpells.FLOWER_LIFE, 0f);
        g.dispose();
        assertTrue(lit(img) > 10);
        BufferedImage gone = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        g = gone.createGraphics();
        ChexySpells.paintFlower(g, 10, 18, 0, ChexySpells.FLOWER_LIFE + 0.1f, ChexySpells.FLOWER_LIFE, 0f);
        g.dispose();
        assertEquals(0, lit(gone));
    }

    private static int lit(BufferedImage img)
    {
        int n = 0;
        for (int y = 0; y < img.getHeight(); y++)
        {
            for (int x = 0; x < img.getWidth(); x++) if ((img.getRGB(x, y) >>> 24) > 40) n++;
        }
        return n;
    }
}
