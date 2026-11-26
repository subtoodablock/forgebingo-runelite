package com.forgebingo;

import com.forgebingo.core.BoardLayout;
import com.forgebingo.core.OpeningAnim;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * The opening animation's effects, by rarity (see {@link OpeningAnim}). Uncommon: a flourish as each tile lands.
 * Epic: a glow and a strong effect on landing (the trails are drawn by the overlay). Legendary: a whole scene behind
 * the board while the tiles arrive, a glow and sparks on landing and a burst at the end. Nothing names the animation
 * or its rarity on screen (owner, 2026-11-25).
 *
 * <p>Client thread only. Shapes are reused between frames; the only allocations per frame are a few colours and,
 * for Legendary scenes, one clipped Graphics.
 */
final class OpeningFx
{
    private static final long DEAL_MS = OpeningAnim.DEAL_MS;
    private static final long LAND_MS = OpeningAnim.LAND_MS;
    private static final Color WHITE = Color.WHITE;
    private static final Color GOLD = new Color(0xffd666);
    private static final Color DUST = new Color(0xd8d0c0);
    private static final Color EMBER_HOT = new Color(0xffd25a);
    private static final Color FLAME_RED = new Color(0xff3b1a);
    private static final Color FLAME_ORANGE = new Color(0xff8a2a);
    private static final Color AURORA_TEAL = new Color(0x4fd8ff);
    private static final Color AURORA_VIOLET = new Color(0xb98bff);

    /** the overlay's scale and line weight, set before each frame */
    double s = 1;
    int u = 1;

    private final RoundRectangle2D.Double rr = new RoundRectangle2D.Double();
    private final Path2D.Double path = new Path2D.Double();
    private final Ellipse2D.Double oval = new Ellipse2D.Double();
    private final Line2D.Double line = new Line2D.Double();
    private final Arc2D.Double arc = new Arc2D.Double();
    private final BasicStroke[] strokes = new BasicStroke[64];

    private static Color col(Color c, double a)
    {
        int al = (int) Math.round(Math.max(0, Math.min(1, a)) * 255);
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), al);
    }

    /** A stroke of about this width (cached by quarter pixel, so frames do not keep making new ones). */
    private BasicStroke stroke(double w)
    {
        int k = (int) Math.max(1, Math.min(strokes.length - 1, Math.round(w * 4)));
        if (strokes[k] == null) strokes[k] = new BasicStroke(k / 4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        return strokes[k];
    }

    private static double life(long since)
    {
        return BoardLayout.easeInOut(since / 400.0) * BoardLayout.easeInOut((DEAL_MS - since) / 500.0);
    }

    // ---------- landing (per tile) ----------

    /** A tile has just landed at x, y (sinceLand ms ago): its rarity's flourish or effect. */
    void landing(Graphics2D g, OpeningAnim.Spec spec, Rectangle b, int x, int y, int c, int arcSize, int idx, long sinceLand, float alpha)
    {
        OpeningAnim.Rarity rarity = spec.rarity;
        if (rarity == OpeningAnim.Rarity.COMMON) return;
        double t = sinceLand / (double) OpeningAnim.LANDING_FX_MS;
        if (t < 0 || t >= 1) return;
        double fade = 1 - BoardLayout.easeInOut(t);
        double out = OpeningAnim.easeOut(t);
        double cx = x + c / 2.0, cy = y + c / 2.0;
        Color accent = spec.accent(idx);
        switch (spec.fx)
        {
            case SPARKLE: sparkles(g, cx, cy, c, idx, 6, out, fade, alpha, accent, accent); return;
            case RIPPLE:
                for (int j = 0; j < 2; j++)
                {
                    double tj = t * 1.35 - j * 0.35;
                    if (tj <= 0 || tj >= 1) continue;
                    double grow = OpeningAnim.easeOut(tj) * c * 0.7;
                    g.setStroke(stroke(Math.max(1.2, 1.8 * s * (1 - tj))));
                    g.setColor(col(accent, 0.85 * (1 - tj) * alpha));
                    rr.setRoundRect(x - grow / 2, y - grow / 2, c + grow, c + grow, arcSize + grow, arcSize + grow);
                    g.draw(rr);
                }
                return;
            case GLINT:
            {
                // a bright band sweeping across the face, corner to corner
                Shape old = g.getClip();
                rr.setRoundRect(x, y, c, c, arcSize, arcSize);
                g.clip(rr);
                double along = -c * 0.6 + out * c * 2.2, w = c * 0.22;
                path.reset();
                path.moveTo(x + along, y);
                path.lineTo(x + along + w, y);
                path.lineTo(x + along + w - c, y + c);
                path.lineTo(x + along - c, y + c);
                path.closePath();
                g.setColor(col(WHITE, 0.55 * fade * alpha));
                g.fill(path);
                g.setClip(old);
                g.setStroke(stroke(Math.max(1, 1.4 * s)));
                g.setColor(col(accent, 0.6 * fade * alpha));
                rr.setRoundRect(x, y, c, c, arcSize, arcSize);
                g.draw(rr);
                return;
            }
            case DUST:
                for (int k = 0; k < 8; k++)
                {
                    long h = OpeningAnim.hash(idx * 17L + k);
                    int dir = k % 2 == 0 ? -1 : 1;
                    double reach = c * (0.25 + 0.35 * ((h % 100) / 100.0)) * out;
                    double px = cx + dir * (c * 0.4 + reach), py = y + c - c * 0.05 - out * c * (0.08 + (h >>> 8) % 20 / 100.0);
                    double r = c * 0.06 * (1 + 1.4 * t) + 1;
                    g.setColor(col(DUST, 0.7 * fade * alpha));
                    oval.setFrame(px - r, py - r, r * 2, r * 2);
                    g.fill(oval);
                }
                return;
            default: break;
        }
        // Epic and Legendary: a glow ring in the effect colour, then the effect itself
        glowRing(g, x, y, c, arcSize, t, out, fade, alpha, accent);
        switch (spec.fx)
        {
            case BOLT:
                if (t < 0.55) bolt(g, cx + (OpeningAnim.hash(idx * 3L) % 100 / 100.0 - 0.5) * c * 1.5, b.y + u * 2, cx, cy, c * 0.45, idx, 7,
                    (1 - t / 0.55) * alpha, accent);
                sparkles(g, cx, cy, c, idx, 5, out, fade, alpha, accent, WHITE);
                break;
            case BEAM:
            {
                double w = c * 0.75 * (1 - 0.5 * t);
                g.setPaint(new GradientPaint(0, b.y, col(accent, 0), 0, y + c, col(accent, 0.45 * fade * alpha)));
                g.fill(rect(cx - w / 2, b.y + u * 2, w, y + c - b.y - u * 2));
                double core = c * 0.16 * (1 - 0.6 * t);
                g.setPaint(new GradientPaint(0, b.y, col(WHITE, 0), 0, y + c, col(WHITE, 0.7 * fade * alpha)));
                g.fill(rect(cx - core / 2, b.y + u * 2, core, y + c - b.y - u * 2));
                sparkles(g, cx, cy, c, idx, 5, out, fade, alpha, accent, WHITE);
                break;
            }
            case EMBER:
                for (int k = 0; k < 8; k++)
                {
                    long h = OpeningAnim.hash(idx * 23L + k);
                    double px = x + c * (0.1 + 0.8 * ((h % 100) / 100.0)) + Math.sin(t * 7 + k) * c * 0.08;
                    double py = y + c * 0.8 - out * c * (0.8 + (h >>> 9) % 60 / 100.0);
                    double r = Math.max(1, (2.4 * s) * (1 - 0.6 * t));
                    g.setColor(col(k % 3 == 0 ? EMBER_HOT : accent, 0.9 * fade * alpha));
                    oval.setFrame(px - r, py - r, r * 2, r * 2);
                    g.fill(oval);
                }
                break;
            case SHOCK:
                for (int j = 0; j < 2; j++)
                {
                    double tj = t * 1.2 - j * 0.2;
                    if (tj <= 0 || tj >= 1) continue;
                    double r = c * (0.55 + 1.35 * OpeningAnim.easeOut(tj));
                    g.setStroke(stroke(Math.max(1, 3.2 * s * (1 - tj))));
                    g.setColor(col(j == 0 ? accent : WHITE, 0.75 * (1 - tj) * alpha));
                    oval.setFrame(cx - r, cy - r, r * 2, r * 2);
                    g.draw(oval);
                }
                break;
            case PRISM: case PRISMATIC:
                for (int k = 0; k < 9; k++) star(g, cx, cy, c, idx, k, out, fade, alpha, OpeningAnim.RAINBOW[(idx * 5 + k) % OpeningAnim.RAINBOW.length]);
                break;
            case FROST:
            {
                g.setColor(col(WHITE, 0.25 * fade * alpha));
                g.fillRoundRect(x, y, c, c, arcSize, arcSize);
                g.setStroke(stroke(Math.max(1, 1.5 * s)));
                g.setColor(col(accent, 0.9 * fade * alpha));
                double len = c * 0.8 * out;
                for (int k = 0; k < 6; k++)
                {
                    double a = k * Math.PI / 3 + Math.PI / 6, ca = Math.cos(a), sa = Math.sin(a);
                    line.setLine(cx, cy, cx + ca * len, cy + sa * len);
                    g.draw(line);
                    double bx = cx + ca * len * 0.62, by = cy + sa * len * 0.62, tick = len * 0.22;
                    line.setLine(bx, by, bx + Math.cos(a + 0.7) * tick, by + Math.sin(a + 0.7) * tick);
                    g.draw(line);
                    line.setLine(bx, by, bx + Math.cos(a - 0.7) * tick, by + Math.sin(a - 0.7) * tick);
                    g.draw(line);
                }
                break;
            }
            default:
                // Legendary: a spray of sparks in the scene's colour, gold and white
                for (int k = 0; k < 10; k++) star(g, cx, cy, c, idx, k, out, fade, alpha, k % 3 == 0 ? WHITE : k % 3 == 1 ? accent : GOLD);
                break;
        }
    }

    private final java.awt.geom.Rectangle2D.Double box = new java.awt.geom.Rectangle2D.Double();

    private Shape rect(double x, double y, double w, double h)
    {
        box.setRect(x, y, w, Math.max(0, h));
        return box;
    }

    private void glowRing(Graphics2D g, int x, int y, int c, int arcSize, double t, double out, double fade, float alpha, Color col)
    {
        double grow = out * c * 0.35;
        g.setStroke(stroke(Math.max(1.5, 2 * s * (1 - 0.5 * t))));
        g.setColor(col(col, 0.8 * fade * alpha));
        rr.setRoundRect(x - grow / 2, y - grow / 2, c + grow, c + grow, arcSize + grow, arcSize + grow);
        g.draw(rr);
    }

    private void sparkles(Graphics2D g, double cx, double cy, int c, int idx, int count, double out, double fade, float alpha, Color main, Color third)
    {
        for (int k = 0; k < count; k++) star(g, cx, cy, c, idx, k, out, fade, alpha, k % 3 == 0 ? third == main ? WHITE : third : main);
    }

    /** One four-point star with pinched sides, thrown out from the tile's middle. */
    private void star(Graphics2D g, double cx, double cy, int c, int idx, int k, double out, double fade, float alpha, Color sc)
    {
        long h = OpeningAnim.hash(idx * 31L + k);
        double ang = (h % 628) / 100.0;
        double dist = c * (0.35 + 0.5 * out) * (0.7 + (h >>> 10) % 30 / 100.0);
        double size = Math.max(1.5, c * 0.085 * fade + 1);
        double sx = cx + Math.cos(ang) * dist;
        double sy = cy + Math.sin(ang) * dist;
        g.setColor(col(sc, 0.9 * fade * alpha));
        double pinch = size * 0.28;
        path.reset();
        path.moveTo(sx, sy - size);
        path.lineTo(sx + pinch, sy - pinch);
        path.lineTo(sx + size, sy);
        path.lineTo(sx + pinch, sy + pinch);
        path.lineTo(sx, sy + size);
        path.lineTo(sx - pinch, sy + pinch);
        path.lineTo(sx - size, sy);
        path.lineTo(sx - pinch, sy - pinch);
        path.closePath();
        g.fill(path);
    }

    /** A jagged lightning bolt from (x0, y0) to (x1, y1): a wide soft glow and a bright core. */
    private void bolt(Graphics2D g, double x0, double y0, double x1, double y1, double wiggle, long seed, int segments, double a, Color glow)
    {
        if (a <= 0.01) return;
        path.reset();
        path.moveTo(x0, y0);
        for (int k = 1; k < segments; k++)
        {
            double f = k / (double) segments;
            long h = OpeningAnim.hash(seed * 131L + k);
            double off = ((h % 1000) / 1000.0 - 0.5) * 2 * wiggle * (1 - Math.abs(f - 0.5));
            path.lineTo(x0 + (x1 - x0) * f + off, y0 + (y1 - y0) * f);
        }
        path.lineTo(x1, y1);
        g.setStroke(stroke(Math.max(3, 6 * s)));
        g.setColor(col(glow, 0.35 * a));
        g.draw(path);
        g.setStroke(stroke(Math.max(1.2, 1.8 * s)));
        g.setColor(col(WHITE, 0.95 * a));
        g.draw(path);
    }

    // ---------- Legendary scenes ----------

    /** Behind the tiles: the Legendary animation's scene, inside the board's frame, fading in and out over the opening. */
    void scene(Graphics2D g, OpeningAnim.Spec spec, Rectangle b, int frameArc, long since, float alpha)
    {
        if (spec.rarity != OpeningAnim.Rarity.LEGENDARY) return;
        double life = life(since) * alpha;
        if (life <= 0) return;
        Graphics2D gc = (Graphics2D) g.create();
        try
        {
            int in = 2 * u;
            gc.clip(new RoundRectangle2D.Double(b.x + in, b.y + in, b.width - 2 * in, b.height - 2 * in, frameArc, frameArc));
            double cx = b.getCenterX(), cy = b.getCenterY();
            switch (spec.fx)
            {
                case VORTEX: rays(gc, cx, cy, Math.hypot(b.width, b.height), since / 900.0, 12, 12, life, null, spec.colour); break;
                case PRISMATIC:
                    rays(gc, cx, cy, Math.hypot(b.width, b.height), since / 700.0, 12, 14, life * 1.1, OpeningAnim.RAINBOW, null);
                    rays(gc, cx, cy, Math.hypot(b.width, b.height) * 0.7, -since / 1100.0, 6, 8, life * 0.6, OpeningAnim.RAINBOW, null);
                    break;
                case STARFALL: starfall(gc, b, since, life, spec.colour); break;
                case RUNES: runes(gc, b, since, life, spec.colour); break;
                case AURORA: aurora(gc, b, since, life, spec.colour); break;
                case ECLIPSE: eclipse(gc, b, since, life, spec.colour); break;
                case INFERNO: inferno(gc, b, since, life, spec.colour); break;
                case TEMPEST: tempest(gc, b, since, life, spec.colour); break;
                default: break;
            }
        }
        finally
        {
            gc.dispose();
        }
    }

    /** Rotating rays of light from the middle. colours: a colour per ray (or null for one colour, warm to clear). */
    private void rays(Graphics2D g, double cx, double cy, double r, double turn, int count, double width, double a, Color[] colours, Color one)
    {
        Graphics2D gr = (Graphics2D) g.create();
        try
        {
            gr.translate(cx, cy);
            gr.rotate(turn);
            arc.setArc(-r, -r, 2 * r, 2 * r, -width / 2, width, Arc2D.PIE);
            if (colours == null) gr.setPaint(new GradientPaint(0, 0, col(one, 0.33 * a), (float) r * 0.6f, 0, col(one, 0)));
            for (int k = 0; k < count; k++)
            {
                gr.rotate(Math.PI * 2 / count);
                if (colours != null) gr.setColor(col(colours[k % colours.length], 0.2 * a));
                gr.fill(arc);
            }
        }
        finally
        {
            gr.dispose();
        }
    }

    private void wash(Graphics2D g, Rectangle b, int rgb, double a)
    {
        g.setColor(col(new Color(rgb), a));
        g.fillRect(b.x, b.y, b.width, b.height);
    }

    private void starfall(Graphics2D g, Rectangle b, long since, double life, Color c)
    {
        wash(g, b, 0x0a1236, 0.5 * life);
        // twinkling stars
        for (int k = 0; k < 34; k++)
        {
            long h = OpeningAnim.hash(500 + k);
            double x = b.x + (h % 1000) / 1000.0 * b.width, y = b.y + ((h >>> 12) % 1000) / 1000.0 * b.height;
            double tw = 0.35 + 0.65 * Math.abs(Math.sin(since / 260.0 + k * 1.3));
            double r = Math.max(0.8, (0.8 + (h >>> 24) % 3 * 0.5) * s);
            g.setColor(col(k % 4 == 0 ? c : WHITE, 0.8 * tw * life));
            oval.setFrame(x - r, y - r, r * 2, r * 2);
            g.fill(oval);
        }
        // shooting stars, down and to the left
        double reach = Math.max(b.width, b.height), dx = -0.55, dy = 0.83;
        for (int k = 0; k < 9; k++)
        {
            long h = OpeningAnim.hash(700 + k);
            double period = 900 + h % 700;
            double t = ((since + (h >>> 8) % 900) % period) / period;
            double x0 = b.x + b.width * (0.15 + 1.0 * ((h >>> 20) % 1000) / 1000.0), y0 = b.y - reach * 0.1;
            double hx = x0 + dx * reach * 1.1 * t, hy = y0 + dy * reach * 1.1 * t, tail = reach * 0.2;
            for (int j = 0; j < 3; j++)
            {
                double f0 = j / 3.0, f1 = (j + 1) / 3.0;
                g.setStroke(stroke(Math.max(1, (2.2 - j * 0.5) * s)));
                g.setColor(col(j == 0 ? WHITE : c, (0.9 - j * 0.28) * life * Math.sin(Math.PI * t)));
                line.setLine(hx - dx * tail * f0, hy - dy * tail * f0, hx - dx * tail * f1, hy - dy * tail * f1);
                g.draw(line);
            }
        }
    }

    private void runes(Graphics2D g, Rectangle b, long since, double life, Color c)
    {
        wash(g, b, 0x041818, 0.35 * life);
        double cx = b.getCenterX(), cy = b.getCenterY(), r = Math.min(b.width, b.height) * 0.44 * (0.85 + 0.15 * OpeningAnim.easeOut(since / 600.0));
        double pulse = 0.65 + 0.35 * Math.sin(since / 220.0);
        g.setStroke(stroke(Math.max(1.5, 2.2 * s)));
        g.setColor(col(c, 0.75 * life * pulse));
        oval.setFrame(cx - r, cy - r, r * 2, r * 2);
        g.draw(oval);
        double r2 = r * 0.84;
        g.setStroke(stroke(Math.max(1, 1.3 * s)));
        oval.setFrame(cx - r2, cy - r2, r2 * 2, r2 * 2);
        g.draw(oval);
        // runes between the two rings, turning one way
        int count = 20;
        double turn = since / 1500.0, gh = (r - r2) * 0.6;
        g.setStroke(stroke(Math.max(1, 1.4 * s)));
        for (int k = 0; k < count; k++)
        {
            double a = turn + k * Math.PI * 2 / count, rm = (r + r2) / 2;
            double gx = cx + Math.cos(a) * rm, gy = cy + Math.sin(a) * rm;
            double ux = Math.cos(a), uy = Math.sin(a), vx = -uy, vy = ux;
            long h = OpeningAnim.hash(1200 + k);
            path.reset();
            path.moveTo(gx - vx * gh, gy - vy * gh);
            path.lineTo(gx + vx * gh, gy + vy * gh);
            int kind = (int) (h % 4);
            double m = kind < 2 ? 0.2 : -0.3;
            path.moveTo(gx + vx * gh * m, gy + vy * gh * m);
            path.lineTo(gx + vx * gh * (m + 0.5) + ux * gh * (kind % 2 == 0 ? 0.7 : -0.7), gy + vy * gh * (m + 0.5) + uy * gh * (kind % 2 == 0 ? 0.7 : -0.7));
            if (kind == 3)
            {
                path.moveTo(gx - vx * gh * 0.6, gy - vy * gh * 0.6);
                path.lineTo(gx - vx * gh * 0.1 - ux * gh * 0.6, gy - vy * gh * 0.1 - uy * gh * 0.6);
            }
            g.setColor(col(k % 5 == 0 ? WHITE : c, 0.85 * life * pulse));
            g.draw(path);
        }
        // a six-pointed star inside, turning the other way
        double sr = r2 * 0.92;
        for (int tri = 0; tri < 2; tri++)
        {
            path.reset();
            for (int k = 0; k < 3; k++)
            {
                double a = -since / 2100.0 + tri * Math.PI / 3 + k * Math.PI * 2 / 3 - Math.PI / 2;
                if (k == 0) path.moveTo(cx + Math.cos(a) * sr, cy + Math.sin(a) * sr);
                else path.lineTo(cx + Math.cos(a) * sr, cy + Math.sin(a) * sr);
            }
            path.closePath();
            g.setStroke(stroke(Math.max(1, 1.5 * s)));
            g.setColor(col(c, 0.55 * life * pulse));
            g.draw(path);
        }
    }

    private void aurora(Graphics2D g, Rectangle b, long since, double life, Color c)
    {
        wash(g, b, 0x03121a, 0.45 * life);
        Color[] bands = {c, AURORA_TEAL, AURORA_VIOLET};
        int steps = 18;
        for (int j = 0; j < 3; j++)
        {
            double yc = b.y + b.height * (0.24 + 0.24 * j), amp = b.height * 0.07, thick = b.height * 0.16;
            double speed = since / (650.0 + 180 * j);
            for (int layer = 0; layer < 2; layer++)
            {
                double th = thick * (layer == 0 ? 1 : 0.45);
                path.reset();
                for (int k = 0; k <= steps; k++)
                {
                    double f = k / (double) steps, x = b.x + f * b.width;
                    double y = yc + amp * Math.sin(f * Math.PI * 2.4 + speed + j * 1.7);
                    if (k == 0) path.moveTo(x, y); else path.lineTo(x, y);
                }
                for (int k = steps; k >= 0; k--)
                {
                    double f = k / (double) steps, x = b.x + f * b.width;
                    double y = yc + amp * Math.sin(f * Math.PI * 2.4 + speed + j * 1.7) + th * (0.55 + 0.45 * Math.sin(f * Math.PI * 3.1 - speed * 1.3 + j));
                    path.lineTo(x, y);
                }
                path.closePath();
                g.setColor(col(bands[j], (layer == 0 ? 0.22 : 0.3) * life));
                g.fill(path);
            }
        }
    }

    private void eclipse(Graphics2D g, Rectangle b, long since, double life, Color c)
    {
        wash(g, b, 0x05040a, 0.5 * life);
        double cx = b.getCenterX(), cy = b.getCenterY(), r = Math.min(b.width, b.height) * 0.23;
        // the corona: thin rays of light round the dark sun, flickering in length
        g.setStroke(stroke(Math.max(1, 1.8 * s)));
        for (int k = 0; k < 36; k++)
        {
            double a = k * Math.PI * 2 / 36 + since / 4000.0;
            double len = r * (1.35 + 0.55 * Math.abs(Math.sin(since / 300.0 + k * 2.1)));
            g.setColor(col(k % 3 == 0 ? WHITE : c, 0.5 * life));
            line.setLine(cx + Math.cos(a) * r, cy + Math.sin(a) * r, cx + Math.cos(a) * len, cy + Math.sin(a) * len);
            g.draw(line);
        }
        double halo = r * 1.08;
        g.setStroke(stroke(Math.max(3, 7 * s)));
        g.setColor(col(c, 0.35 * life));
        oval.setFrame(cx - halo, cy - halo, halo * 2, halo * 2);
        g.draw(oval);
        g.setStroke(stroke(Math.max(1.2, 2 * s)));
        g.setColor(col(WHITE, 0.9 * life));
        oval.setFrame(cx - r, cy - r, r * 2, r * 2);
        g.draw(oval);
        // the moon, sliding across the sun as the opening plays
        double slide = (1 - OpeningAnim.easeOut(since / (double) LAND_MS)) * r * 0.5;
        g.setColor(col(new Color(0x07060a), 0.95 * life));
        oval.setFrame(cx - r + slide, cy - r, r * 2, r * 2);
        g.fill(oval);
        // the diamond ring: a bright point on the edge
        double d = Math.max(2, 4 * s) * (0.7 + 0.3 * Math.sin(since / 120.0));
        g.setColor(col(WHITE, 0.95 * life));
        oval.setFrame(cx - r * 0.72 - d, cy - r * 0.7 - d, d * 2, d * 2);
        g.fill(oval);
    }

    private void inferno(Graphics2D g, Rectangle b, long since, double life, Color c)
    {
        wash(g, b, 0x2a0800, 0.4 * life);
        double base = b.y + b.height, rise = OpeningAnim.easeOut(since / 700.0);
        int count = 14;
        double w = b.width / (double) (count - 2);
        Color[] layers = {FLAME_RED, FLAME_ORANGE, EMBER_HOT};
        for (int layer = 0; layer < 3; layer++)
        {
            double scale = 1 - layer * 0.3;
            g.setColor(col(layers[layer], (0.5 + layer * 0.08) * life));
            for (int k = 0; k < count; k++)
            {
                double x = b.x + (k - 0.5) * w;
                double hgt = b.height * (0.2 + 0.14 * Math.sin(since / 110.0 + k * 1.7) + 0.08 * Math.sin(since / 47.0 + k)) * rise * scale;
                double sway = Math.sin(since / 160.0 + k) * w * 0.25, half = w * 0.55 * scale;
                path.reset();
                path.moveTo(x - half, base);
                path.quadTo(x - half, base - hgt * 0.55, x + sway, base - hgt);
                path.quadTo(x + half, base - hgt * 0.55, x + half, base);
                path.closePath();
                g.fill(path);
            }
        }
        // embers drifting up across the whole board
        for (int k = 0; k < 28; k++)
        {
            long h = OpeningAnim.hash(1600 + k);
            double speed = 0.08 + (h % 100) / 1000.0;
            double up = ((since * speed * s + (h >>> 8) % 1000)) % b.height;
            double x = b.x + ((h >>> 20) % 1000) / 1000.0 * b.width + Math.sin(since / 300.0 + k) * 6 * s;
            double r = Math.max(1, (1 + (h >>> 30) % 3 * 0.6) * s);
            g.setColor(col(k % 3 == 0 ? EMBER_HOT : c, 0.85 * life * (1 - up / b.height)));
            oval.setFrame(x - r, base - up - r, r * 2, r * 2);
            g.fill(oval);
        }
    }

    private static final long[] STRIKES = {650, 1400, 2150};

    private void tempest(Graphics2D g, Rectangle b, long since, double life, Color c)
    {
        wash(g, b, 0x070b18, 0.5 * life);
        // driving rain
        g.setStroke(stroke(Math.max(1, 1.1 * s)));
        double len = 16 * s;
        for (int k = 0; k < 46; k++)
        {
            long h = OpeningAnim.hash(2000 + k);
            double fall = (since * (0.55 + (h % 30) / 100.0) * s + (h >>> 8) % 1000) % (b.height + len);
            double x = b.x + ((h >>> 20) % 1000) / 1000.0 * (b.width + b.height * 0.3) - fall * 0.3;
            double y = b.y - len + fall;
            g.setColor(col(c, 0.4 * life));
            line.setLine(x, y, x - len * 0.3, y + len);
            g.draw(line);
        }
    }

    /** In front of the tiles: the Tempest's lightning, and every Legendary animation's burst at the end. */
    void front(Graphics2D g, OpeningAnim.Spec spec, Rectangle b, long since, float alpha)
    {
        if (spec.rarity != OpeningAnim.Rarity.LEGENDARY) return;
        if (spec.fx == OpeningAnim.Fx.TEMPEST)
        {
            for (int k = 0; k < STRIKES.length; k++)
            {
                double t = (since - STRIKES[k]) / 260.0;
                if (t < 0 || t >= 1) continue;
                double a = (1 - t) * (1 - t) * alpha;
                Composite base = g.getComposite();
                g.setColor(col(WHITE, 0.28 * a));
                g.fillRect(b.x, b.y, b.width, b.height);
                long h = OpeningAnim.hash(3000 + k);
                double x0 = b.x + b.width * (0.2 + 0.6 * ((h % 1000) / 1000.0));
                double x1 = b.x + b.width * (0.15 + 0.7 * (((h >>> 12) % 1000) / 1000.0));
                bolt(g, x0, b.y, x1, b.y + b.height * 0.92, b.width * 0.08, 3000 + k, 11, a, spec.colour);
                g.setComposite(base);
            }
        }
        burst(g, spec, b, since, alpha);
    }

    /** A ring and spray of sparks from the middle as the last tiles land. */
    private void burst(Graphics2D g, OpeningAnim.Spec spec, Rectangle b, long since, float alpha)
    {
        double t = (since - (LAND_MS - 150)) / (double) (DEAL_MS - LAND_MS + 150);
        if (t <= 0 || t >= 1) return;
        double cx = b.getCenterX(), cy = b.getCenterY(), reach = Math.max(b.width, b.height) * 0.6 * OpeningAnim.easeOut(t);
        double a = (1 - BoardLayout.easeInOut(t)) * alpha * 0.9;
        g.setStroke(stroke(Math.max(2, 3 * s * (1 - 0.6 * t))));
        g.setColor(col(spec.accent(0), a));
        oval.setFrame(cx - reach, cy - reach, reach * 2, reach * 2);
        g.draw(oval);
        for (int k = 0; k < 36; k++)
        {
            long h = OpeningAnim.hash(9000 + k);
            double ang = k * Math.PI * 2 / 36 + (h % 100) / 400.0, d = reach * (0.6 + (h >>> 8) % 40 / 100.0);
            double size = (3 + h % 3) * s * 0.8;
            g.setColor(col(k % 4 == 0 ? WHITE : k % 4 == 2 ? GOLD : spec.accent(k), a));
            oval.setFrame(cx + Math.cos(ang) * d - size / 2.0, cy + Math.sin(ang) * d - size / 2.0, size, size);
            g.fill(oval);
        }
    }
}
