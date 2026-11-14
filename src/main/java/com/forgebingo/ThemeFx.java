package com.forgebingo;

import com.forgebingo.core.SkillProp;
import com.forgebingo.core.SkillProps;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The extra touches each theme has besides its colours and creatures (owner, 2026-11-14):
 * crystals growing on the cards (Chambers of Xeric), sand that falls, piles up on the cards and gets blown or
 * knocked away (Tombs of Amascut), and tiny volcanoes that erupt and send a lava stream off the side of their card
 * (Inferno). The moving ones live in the same layer as the creatures (PanelImps drives them); the crystals are
 * painted by the cards themselves. Everything is in the column's coordinates, like the creatures.
 */
final class ThemeFx
{
    // ------------------------------------------------------------------ crystals (painted by cards and headers)

    // lit face, shaded face, top facet, outline, glint
    private static final Color[] CRYSTAL = {new Color(0x4fe3c4), new Color(0x1c917d), new Color(0xb6fff0), new Color(0x083a34), new Color(0xffffff)};

    /**
     * A little cluster of crystals growing up from (x, baseY): chunky six-sided prisms with a pointed cap, straight
     * sided so they read as gems and not as blades of grass. The same seed always grows the same cluster.
     * {@code size} is the tallest crystal's height in pixels.
     */
    static void crystals(Graphics2D g, long seed, int x, int baseY, int size)
    {
        Random r = new Random(seed);
        int n = size >= 12 ? 3 : 2;
        // drawn back to front: the short outer ones first, the tall middle one last
        int[] order = n == 3 ? new int[] {0, 2, 1} : new int[] {0, 1};
        float w = Math.max(4f, size * 0.44f);
        for (int i : order)
        {
            boolean main = n == 3 ? i == 1 : i == 0;
            float h = main ? size : size * (0.5f + r.nextFloat() * 0.2f);
            float cw = main ? w : w * 0.8f;
            float off = n == 3 ? (i - 1) * w * 0.78f : (i == 0 ? -w * 0.3f : w * 0.55f);
            // how far the top leans away from the middle
            float lean = (main ? (r.nextFloat() - 0.5f) * 0.12f : (off < 0 ? -0.34f : 0.34f)) * h;
            float bx = x + off;
            float cap = Math.min(h * 0.34f, cw * 0.9f);
            float sy = baseY - (h - cap); // the shoulders, where the cap starts
            float sx = bx + lean * (h - cap) / h;
            float tx = bx + lean;
            float ty = baseY - h;
            Path2D.Float left = new Path2D.Float();
            left.moveTo(bx - cw / 2, baseY);
            left.lineTo(sx - cw / 2, sy);
            left.lineTo(sx, sy + cap * 0.35f);
            left.lineTo(bx, baseY);
            left.closePath();
            Path2D.Float right = new Path2D.Float();
            right.moveTo(bx, baseY);
            right.lineTo(sx, sy + cap * 0.35f);
            right.lineTo(sx + cw / 2, sy);
            right.lineTo(bx + cw / 2, baseY);
            right.closePath();
            Path2D.Float top = new Path2D.Float();
            top.moveTo(sx - cw / 2, sy);
            top.lineTo(tx, ty);
            top.lineTo(sx + cw / 2, sy);
            top.lineTo(sx, sy + cap * 0.35f);
            top.closePath();
            Path2D.Float all = new Path2D.Float();
            all.moveTo(bx - cw / 2, baseY);
            all.lineTo(sx - cw / 2, sy);
            all.lineTo(tx, ty);
            all.lineTo(sx + cw / 2, sy);
            all.lineTo(bx + cw / 2, baseY);
            all.closePath();
            g.setColor(CRYSTAL[0]);
            g.fill(left);
            g.setColor(CRYSTAL[1]);
            g.fill(right);
            g.setColor(CRYSTAL[2]);
            g.fill(top);
            g.setStroke(new BasicStroke(1f));
            g.setColor(CRYSTAL[3]);
            g.draw(all);
            if (main && size >= 10)
            {
                // a glint on the lit face
                int gx = Math.round(sx - cw * 0.22f);
                int gy = Math.round(sy + (baseY - sy) * 0.3f);
                g.setColor(PanelUi.alpha(CRYSTAL[4], 230));
                g.fillRect(gx, gy - 1, 1, 3);
                g.fillRect(gx - 1, gy, 3, 1);
            }
        }
    }

    /**
     * Glowing cracks of lava in the rock behind the cards (Inferno). The same place always cracks the same way;
     * only the glow breathes.
     */
    static void lavaGround(Graphics2D g, int w, int h, Rectangle clip, float glow)
    {
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        int band = 58;
        for (int i = 0; i * band < h + band; i++)
        {
            int cy = i * band + 20;
            if (clip != null && (cy + 30 < clip.y || cy - 30 > clip.y + clip.height)) continue;
            Random r = new Random(i * 7919L + 13);
            Path2D.Float crack = new Path2D.Float();
            float y = cy + r.nextInt(17) - 8;
            crack.moveTo(-2, y);
            for (int x = 10 + r.nextInt(8); x < w + 12; x += 9 + r.nextInt(12))
            {
                y = Math.max(cy - 16, Math.min(cy + 16, y + r.nextInt(15) - 7));
                crack.lineTo(x, y);
                if (r.nextInt(5) == 0)
                {
                    // a short side branch
                    crack.lineTo(x + 4 + r.nextInt(5), y + (r.nextBoolean() ? 9 : -9));
                    crack.moveTo(x, y);
                }
            }
            g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(150, 30, 6, Math.round(34 + 30 * glow)));
            g.draw(crack);
            g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(PanelUi.alpha(LAVA_DEEP, Math.round(150 + 60 * glow)));
            g.draw(crack);
            g.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(PanelUi.alpha(PanelUi.mix(LAVA, LAVA_HOT, glow * 0.6f), Math.round(170 + 80 * glow)));
            g.draw(crack);
        }
    }

    // ------------------------------------------------------------------ the moving effects

    private static final Color SAND = new Color(0xdcb96c);
    private static final Color SAND_DARK = new Color(0xb38d45);
    private static final Color SAND_LIGHT = new Color(0xf4dd9c);
    private static final Color LAVA = new Color(0xff7a1a);
    private static final Color LAVA_HOT = new Color(0xffd24a);
    private static final Color LAVA_DEEP = new Color(0xc2300c);
    private static final Color ROCK = new Color(0x2a1a16);
    private static final Color ROCK_LIT = new Color(0x4d2d22);

    private static final int MAX_PILES = 12;
    private static final float PILE_FULL = 26f;
    private static final int MAX_VOLCANOES = 3;

    /** x, y, vx, vy, life left, kind (0 sand grain, 1 dust, 2 lava spark, 3 lava drip) */
    private final List<float[]> bits = new ArrayList<>();

    private static final class Pile
    {
        float x, amount, age;
        int y, l, r;
    }

    private static final class Volcano
    {
        int x, y, l, r;
        /** which side of its card the lava runs off: -1 left, 1 right */
        int side;
        int drop;
        float next, erupt, dripT;
    }

    /** A skill's little prop standing on a card (Skilling theme): it grows in, stays a while and fades. */
    private static final class Prop
    {
        SkillProp skill;
        int x, y, l, r;
        float age, life;
    }

    private static final int MAX_PROPS = 5;
    private static final float GROW = 0.55f;
    private static final float FADE = 0.9f;
    private final List<Prop> props = new ArrayList<>();
    private float nextProp = 1.5f;
    private SkillProp lastSkill;

    private final List<Pile> piles = new ArrayList<>();
    private final List<Volcano> volcanoes = new ArrayList<>();
    private float spawn;

    static boolean moving(PanelTheme t)
    {
        return t == PanelTheme.AMASCUT || t == PanelTheme.INFERNO || t == PanelTheme.SKILLING;
    }

    void reset()
    {
        bits.clear();
        piles.clear();
        volcanoes.clear();
        props.clear();
    }

    boolean isEmpty()
    {
        return bits.isEmpty() && piles.isEmpty() && volcanoes.isEmpty() && props.isEmpty();
    }

    private static boolean onPlat(List<int[]> plats, int l, int r, int y)
    {
        for (int[] p : plats) if (p[2] == y && p[0] == l && p[1] == r) return true;
        return false;
    }

    void step(PanelTheme theme, float dt, List<int[]> plats, Rectangle view, int vw, Random rnd)
    {
        if (theme == PanelTheme.AMASCUT) stepSand(dt, plats, view, vw, rnd);
        else piles.clear();
        if (theme == PanelTheme.INFERNO) stepLava(dt, plats, view, rnd);
        else volcanoes.clear();
        if (theme == PanelTheme.SKILLING) stepProps(dt, plats, view, rnd);
        else props.clear();
        // everything loose: fall, drift, fade
        for (int i = bits.size() - 1; i >= 0; i--)
        {
            float[] b = bits.get(i);
            int kind = (int) b[5];
            float py = b[1];
            if (kind == 0) b[2] += (rnd.nextFloat() - 0.5f) * 30f * dt;
            else if (kind == 1) { b[3] += 40f * dt; b[2] *= 1 - Math.min(1, dt * 1.2f); }
            else b[3] += 320f * dt;
            b[0] += b[2] * dt;
            b[1] += b[3] * dt;
            b[4] -= dt;
            boolean gone = b[4] <= 0 || b[1] > view.y + view.height + 12 || b[0] < -10 || b[0] > vw + 10;
            if (!gone && kind == 0)
            {
                // a grain that reaches the top of a card joins (or starts) a pile there
                for (int[] p : plats)
                {
                    if (b[0] >= p[0] + 4 && b[0] <= p[1] - 4 && py <= p[2] && b[1] >= p[2] && rnd.nextFloat() < 0.14f)
                    {
                        land(b[0], p, rnd);
                        gone = true;
                        break;
                    }
                }
            }
            if (gone) bits.remove(i);
        }
    }

    // ---- skill props

    private void stepProps(float dt, List<int[]> plats, Rectangle view, Random rnd)
    {
        for (int i = props.size() - 1; i >= 0; i--)
        {
            Prop p = props.get(i);
            p.age += dt;
            if (p.age >= p.life || !onPlat(plats, p.l, p.r, p.y)) props.remove(i);
        }
        nextProp -= dt;
        if (nextProp > 0 || props.size() >= MAX_PROPS) return;
        nextProp = 1.6f + rnd.nextFloat() * 2.6f;
        // a card you can see, with room for it away from the other props
        List<int[]> ok = new ArrayList<>();
        for (int[] p : plats)
        {
            if (p[1] - p[0] < 80 || p[2] < view.y + 26 || p[2] > view.y + view.height - 8) continue;
            ok.add(p);
        }
        if (ok.isEmpty()) return;
        int[] plat = ok.get(rnd.nextInt(ok.size()));
        int x = plat[0] + 18 + rnd.nextInt(Math.max(1, plat[1] - plat[0] - 36));
        for (Prop o : props) if (o.y == plat[2] && Math.abs(o.x - x) < 34) return;
        // a skill that is not standing somewhere already, and not the one before
        SkillProp[] all = SkillProp.values();
        SkillProp skill = null;
        for (int tries = 0; tries < 12 && skill == null; tries++)
        {
            SkillProp c = all[rnd.nextInt(all.length)];
            if (c == lastSkill || SkillProps.image(c, 2, 0) == null) continue;
            boolean here = false;
            for (Prop o : props) if (o.skill == c) here = true;
            if (!here) skill = c;
        }
        if (skill == null) return;
        Prop p = new Prop();
        p.skill = skill;
        p.x = x;
        p.y = plat[2];
        p.l = plat[0];
        p.r = plat[1];
        p.life = 9f + rnd.nextFloat() * 7f;
        props.add(p);
        lastSkill = skill;
    }

    private void paintProp(Graphics2D g, Prop p, float clock)
    {
        int stage = p.age < GROW ? 0 : p.age < GROW * 2 ? 1 : 2;
        java.awt.image.BufferedImage img = SkillProps.image(p.skill, stage, ((int) (clock * 3f + p.x)) % 2);
        if (img == null) return;
        float left = p.life - p.age;
        float alpha = left < FADE ? Math.max(0f, left / FADE) : 1f;
        // a small hop as each stage arrives, so it reads as growing
        float inStage = p.age < GROW * 2 ? (p.age % GROW) / GROW : 1f;
        int hop = inStage < 0.35f && p.age < GROW * 3 ? -1 : 0;
        java.awt.Composite old = g.getComposite();
        if (alpha < 1f) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g.drawImage(img, p.x - img.getWidth() / 2, p.y - img.getHeight() + 1 + hop, null);
        g.setComposite(old);
    }

    // ---- sand

    private void stepSand(float dt, List<int[]> plats, Rectangle view, int vw, Random rnd)
    {
        spawn += dt * 9f;
        while (spawn >= 1f && bits.size() < 90)
        {
            spawn -= 1f;
            bits.add(new float[] {6 + rnd.nextFloat() * Math.max(10, vw - 12), view.y - 4 - rnd.nextFloat() * 30, (rnd.nextFloat() - 0.5f) * 10f,
                55f + rnd.nextFloat() * 45f, 30f, 0});
        }
        if (spawn > 4f) spawn = 0;
        for (int i = piles.size() - 1; i >= 0; i--)
        {
            Pile p = piles.get(i);
            p.age += dt;
            if (!onPlat(plats, p.l, p.r, p.y))
            {
                piles.remove(i);
                continue;
            }
            // a full pile sooner or later gets blown away; so does a very old one
            boolean windy = p.amount >= PILE_FULL && rnd.nextFloat() < dt * 0.35f;
            if (windy || p.age > 55f) scatter(p, rnd.nextBoolean() ? 1 : -1, rnd);
        }
    }

    private void land(float x, int[] plat, Random rnd)
    {
        Pile best = null;
        for (Pile p : piles) if (p.y == plat[2] && Math.abs(p.x - x) < 22f) { best = p; break; }
        if (best == null)
        {
            // not every grain starts a pile, so the cards do not fill up edge to edge
            if (rnd.nextFloat() > 0.55f) return;
            if (piles.size() >= MAX_PILES) scatter(piles.get(0), rnd.nextBoolean() ? 1 : -1, rnd);
            best = new Pile();
            best.x = Math.max(plat[0] + 26, Math.min(plat[1] - 26, x));
            best.y = plat[2];
            best.l = plat[0];
            best.r = plat[1];
            piles.add(best);
        }
        best.amount = Math.min(PILE_FULL, best.amount + 2.5f);
    }

    /** The pile breaks up into dust that drifts off to one side. */
    private void scatter(Pile p, int dir, Random rnd)
    {
        int n = 6 + Math.round(p.amount * 0.5f);
        float w = pileWidth(p);
        for (int i = 0; i < n; i++)
        {
            bits.add(new float[] {p.x + (rnd.nextFloat() - 0.5f) * w, p.y - rnd.nextFloat() * pileHeight(p), dir * (40f + rnd.nextFloat() * 90f),
                -(10f + rnd.nextFloat() * 50f), 0.5f + rnd.nextFloat() * 0.7f, 1});
        }
        piles.remove(p);
    }

    private static float pileWidth(Pile p)
    {
        return 14f + p.amount * 1.35f;
    }

    private static float pileHeight(Pile p)
    {
        return 4f + p.amount * 0.46f;
    }

    /** The mouse brushed past: any pile under it is knocked away, off in the direction the mouse was moving. */
    boolean mouseAt(int x, int y, int dx, Random rnd)
    {
        for (int i = piles.size() - 1; i >= 0; i--)
        {
            Pile p = piles.get(i);
            float w = pileWidth(p);
            if (Math.abs(x - p.x) <= w / 2 + 3 && y <= p.y + 3 && y >= p.y - pileHeight(p) - 5)
            {
                scatter(p, dx < 0 ? -1 : 1, rnd);
                return true;
            }
        }
        return false;
    }

    // ---- lava

    private void stepLava(float dt, List<int[]> plats, Rectangle view, Random rnd)
    {
        for (int i = volcanoes.size() - 1; i >= 0; i--)
        {
            Volcano v = volcanoes.get(i);
            if (!onPlat(plats, v.l, v.r, v.y))
            {
                volcanoes.remove(i);
                continue;
            }
            v.next -= dt;
            if (v.next <= 0)
            {
                v.next = 7f + rnd.nextFloat() * 9f;
                v.erupt = 1.3f;
            }
            if (v.erupt > 0)
            {
                v.erupt -= dt;
                if (rnd.nextFloat() < dt * 34f && bits.size() < 140)
                {
                    bits.add(new float[] {v.x + (rnd.nextFloat() - 0.5f) * 3f, v.y - 10, (rnd.nextFloat() - 0.5f) * 90f, -(90f + rnd.nextFloat() * 120f),
                        0.7f + rnd.nextFloat() * 0.6f, 2});
                }
            }
            // now and then a drop lets go of the end of the stream
            v.dripT -= dt;
            if (v.dripT <= 0)
            {
                v.dripT = 1.6f + rnd.nextFloat() * 2.6f;
                int ex = v.side < 0 ? v.l + 1 : v.r - 2;
                bits.add(new float[] {ex, v.y + v.drop, 0, 12f, 1.4f, 3});
            }
        }
        if (volcanoes.size() < MAX_VOLCANOES && rnd.nextFloat() < dt * 0.8f)
        {
            // a new one on a card you can see, away from the others
            List<int[]> ok = new ArrayList<>();
            for (int[] p : plats)
            {
                if (p[1] - p[0] < 110 || p[2] < view.y + 12 || p[2] > view.y + view.height - 30) continue;
                boolean taken = false;
                for (Volcano v : volcanoes) if (v.y == p[2]) taken = true;
                if (!taken) ok.add(p);
            }
            if (!ok.isEmpty())
            {
                int[] p = ok.get(rnd.nextInt(ok.size()));
                Volcano v = new Volcano();
                v.l = p[0];
                v.r = p[1];
                v.y = p[2];
                v.side = rnd.nextBoolean() ? 1 : -1;
                int span = (p[1] - p[0]) / 3;
                v.x = v.side < 0 ? p[0] + 26 + rnd.nextInt(Math.max(1, span)) : p[1] - 26 - rnd.nextInt(Math.max(1, span));
                v.drop = 16 + rnd.nextInt(22);
                v.next = 1.5f + rnd.nextFloat() * 5f;
                v.dripT = 1f + rnd.nextFloat() * 2f;
                volcanoes.add(v);
            }
        }
    }

    // ------------------------------------------------------------------ painting

    void paint(Graphics2D g, float clock)
    {
        for (Prop p : props) paintProp(g, p, clock);
        for (Volcano v : volcanoes) paintVolcano(g, v, clock);
        for (Pile p : piles) paintPile(g, p);
        for (float[] b : bits)
        {
            int kind = (int) b[5];
            int x = Math.round(b[0]);
            int y = Math.round(b[1]);
            if (kind == 0)
            {
                g.setColor(PanelUi.alpha(SAND, 200));
                g.fillRect(x, y, 1, 2);
            }
            else if (kind == 1)
            {
                g.setColor(PanelUi.alpha(SAND_LIGHT, Math.max(0, Math.min(220, Math.round(b[4] * 260)))));
                g.fillRect(x, y, 2, 2);
            }
            else if (kind == 2)
            {
                float k = Math.max(0f, Math.min(1f, b[4]));
                g.setColor(PanelUi.alpha(PanelUi.mix(LAVA_DEEP, LAVA_HOT, k), Math.round(120 + 135 * k)));
                g.fillRect(x, y, 2, 2);
            }
            else
            {
                g.setColor(LAVA);
                g.fillRoundRect(x - 1, y - 1, 3, 5, 3, 3);
                g.setColor(LAVA_HOT);
                g.fillRect(x, y, 1, 2);
            }
        }
    }

    private static void paintPile(Graphics2D g, Pile p)
    {
        float w = pileWidth(p);
        float h = pileHeight(p);
        Path2D.Float mound = new Path2D.Float();
        mound.moveTo(p.x - w / 2, p.y);
        mound.quadTo(p.x - w * 0.22f, p.y - h * 1.25f, p.x + w * 0.06f, p.y - h);
        mound.quadTo(p.x + w * 0.3f, p.y - h * 0.8f, p.x + w / 2, p.y);
        mound.closePath();
        g.setColor(SAND_DARK);
        g.fill(mound);
        Path2D.Float lit = new Path2D.Float();
        lit.moveTo(p.x - w / 2 + 1, p.y);
        lit.quadTo(p.x - w * 0.22f, p.y - h * 1.2f, p.x + w * 0.04f, p.y - h + 0.5f);
        lit.quadTo(p.x - w * 0.05f, p.y - h * 0.3f, p.x + w * 0.12f, p.y);
        lit.closePath();
        g.setColor(SAND);
        g.fill(lit);
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(0x6e5424));
        g.draw(mound);
        Random r = new Random((long) p.y * 31 + p.l);
        int grains = Math.round(w / 5);
        for (int i = 0; i < grains; i++)
        {
            float fx = (r.nextFloat() - 0.5f) * w * 0.7f;
            float top = h * (1f - Math.abs(fx) / (w * 0.5f));
            float fy = r.nextFloat() * Math.max(1f, top - 2f) + 1f;
            g.setColor(i % 2 == 0 ? PanelUi.alpha(SAND_LIGHT, 230) : PanelUi.alpha(new Color(0x8f6f2f), 220));
            g.fillRect(Math.round(p.x + fx), Math.round(p.y - fy), 1, 1);
        }
    }

    private static void paintVolcano(Graphics2D g, Volcano v, float clock)
    {
        float glow = 0.6f + 0.4f * (float) Math.sin(clock * 3.1f + v.x);
        boolean erupting = v.erupt > 0;
        // the stream: out of the crater, down the cone, along the top of the card, off its side and down
        int edge = v.side < 0 ? v.l + 1 : v.r - 2;
        int foot = v.x + v.side * 8;
        Path2D.Float river = new Path2D.Float();
        river.moveTo(v.x + v.side, v.y - 9);
        river.lineTo(foot, v.y - 1);
        river.lineTo(edge, v.y - 1);
        river.lineTo(edge, v.y + v.drop);
        g.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(PanelUi.alpha(LAVA_DEEP, 150));
        g.draw(river);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(PanelUi.mix(LAVA_DEEP, LAVA, 0.6f + 0.4f * glow));
        g.draw(river);
        // brighter beads running along it, so it reads as flowing
        float len = Math.abs(edge - foot) + v.drop;
        g.setColor(PanelUi.alpha(LAVA_HOT, 230));
        for (int i = 0; i < 4; i++)
        {
            float d = ((clock * 22f + i * len / 4f) % Math.max(1f, len));
            int bx;
            int by;
            if (d < Math.abs(edge - foot))
            {
                bx = Math.round(foot + v.side * d);
                by = v.y - 1;
            }
            else
            {
                bx = edge;
                by = Math.round(v.y - 1 + (d - Math.abs(edge - foot)));
            }
            g.fillRect(bx, by - 1, 2, 2);
        }
        // the bulb at the end of the stream
        g.setColor(LAVA);
        g.fillOval(edge - 2, v.y + v.drop - 1, 5, 5);
        g.setColor(LAVA_HOT);
        g.fillRect(edge, v.y + v.drop, 1, 2);

        // the cone
        Path2D.Float cone = new Path2D.Float();
        cone.moveTo(v.x - 10, v.y);
        cone.lineTo(v.x - 3, v.y - 10);
        cone.lineTo(v.x + 3, v.y - 10);
        cone.lineTo(v.x + 10, v.y);
        cone.closePath();
        g.setColor(ROCK);
        g.fill(cone);
        Path2D.Float face = new Path2D.Float();
        face.moveTo(v.x - 10, v.y);
        face.lineTo(v.x - 3, v.y - 10);
        face.lineTo(v.x, v.y - 10);
        face.lineTo(v.x - 3, v.y);
        face.closePath();
        g.setColor(ROCK_LIT);
        g.fill(face);
        g.setStroke(new BasicStroke(1f));
        g.setColor(Color.BLACK);
        g.draw(cone);
        // the crater, brighter while it erupts
        g.setColor(PanelUi.mix(LAVA_DEEP, LAVA_HOT, erupting ? 1f : glow * 0.7f));
        g.fillRect(v.x - 3, v.y - 11, 6, 2);
        if (erupting)
        {
            g.setColor(PanelUi.alpha(LAVA_HOT, 90));
            g.fillOval(v.x - 7, v.y - 17, 14, 10);
        }
        // a lick of lava down the cone on the stream's side
        g.setColor(LAVA);
        g.drawLine(v.x + v.side * 2, v.y - 9, v.x + v.side * 7, v.y - 2);
    }
}
