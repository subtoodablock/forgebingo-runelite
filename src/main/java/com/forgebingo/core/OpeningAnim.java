package com.forgebingo.core;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * The board's opening animations (owner, 2026-11-12): 109 of them, each 3 seconds, rolled by rarity every time the
 * board opens. Each is an order (which tiles arrive first) and an entrance (how a tile arrives). Rarer ones are the
 * showier combinations, and the overlay adds more on top: Rare = landing sparkles, Epic = motion trails and glow
 * flashes, Legendary = a light vortex behind the board and a golden burst at the end.
 * Pure logic (no RuneLite), so it is unit-tested.
 */
public final class OpeningAnim
{
    public enum Rarity
    {
        COMMON("Common", new Color(0xb4b4b4), 0.60),
        RARE("Rare", new Color(0x4fa3ff), 0.25),
        EPIC("Epic", new Color(0xb36bff), 0.12),
        LEGENDARY("Legendary", new Color(0xffa62b), 0.03);

        public final String label;
        public final Color colour;
        public final double chance;

        Rarity(String label, Color colour, double chance)
        {
            this.label = label;
            this.colour = colour;
            this.chance = chance;
        }
    }

    /** How a tile arrives. */
    public enum Entrance
    {
        DEAL("Deal", 1), DROP("Drop", 1), RISE("Rise", 1), TIDE("Tide", 1), GALE("Gale", 1), MIST("Mist", 0),
        BLOOM("Bloom", 2), FLIP("Flip", 2), WHIRL("Whirl", 3), BOUNCE("Bounce", 2), VORTEX("Vortex", 4),
        NOVA("Nova", 4), CONVERGE("Converge", 3), RAIN("Rain", 3);

        public final String label;
        final int show;

        Entrance(String label, int show)
        {
            this.label = label;
            this.show = show;
        }
    }

    /** Which tiles arrive first. */
    public enum Order
    {
        ROW("Row", 0), COLUMN("Column", 0), SERPENT("Serpent", 1), DIAGONAL("Diagonal", 1), CROSSING("Crossing", 1),
        SPIRAL("Spiral", 2), UNWINDING("Unwinding", 2), HEART("Heart", 2), SIEGE("Siege", 2), CHEQUER("Chequer", 1),
        SCATTER("Scatter", 1), PINCER("Pincer", 1);

        public final String label;
        final int show;

        Order(String label, int show)
        {
            this.label = label;
            this.show = show;
        }
    }

    public static final class Spec
    {
        public final Order order;
        public final Entrance entrance;
        public final Rarity rarity;
        public final String name;

        Spec(Order order, Entrance entrance, Rarity rarity)
        {
            this.order = order;
            this.entrance = entrance;
            this.rarity = rarity;
            this.name = order.label + " " + entrance.label;
        }
    }

    public static final int COUNT = 109;
    private static final int[] PER_RARITY = {60, 28, 15, 6}; // common, rare, epic, legendary
    public static final List<Spec> ALL = build();

    private OpeningAnim()
    {
    }

    /** All order x entrance pairs, showiest first; the top 6 are legendary, the next 15 epic, the next 28 rare, and the
     *  60 plainest of the rest common (109 in all). */
    private static List<Spec> build()
    {
        List<int[]> pairs = new ArrayList<>();
        for (Order o : Order.values()) for (Entrance e : Entrance.values()) pairs.add(new int[] {o.ordinal(), e.ordinal()});
        Comparator<int[]> showiest = Comparator.comparingInt((int[] p) -> -(Entrance.values()[p[1]].show * 10 + Order.values()[p[0]].show))
            .thenComparingInt(p -> p[0] * 31 + p[1] * 7 % 13);
        pairs.sort(showiest);
        List<Spec> out = new ArrayList<>();
        int at = 0;
        Rarity[] top = {Rarity.LEGENDARY, Rarity.EPIC, Rarity.RARE};
        int[] topN = {PER_RARITY[3], PER_RARITY[2], PER_RARITY[1]};
        for (int i = 0; i < top.length; i++)
        {
            for (int k = 0; k < topN[i]; k++, at++) out.add(spec(pairs.get(at), top[i]));
        }
        List<int[]> rest = new ArrayList<>(pairs.subList(at, pairs.size()));
        Collections.reverse(rest); // plainest first
        for (int k = 0; k < PER_RARITY[0]; k++) out.add(spec(rest.get(k), Rarity.COMMON));
        return Collections.unmodifiableList(out);
    }

    private static Spec spec(int[] p, Rarity r)
    {
        return new Spec(Order.values()[p[0]], Entrance.values()[p[1]], r);
    }

    /** Rolls a rarity by its chance, then any animation of that rarity. */
    public static Spec roll(Random rng)
    {
        double x = rng.nextDouble(), acc = 0;
        Rarity pick = Rarity.COMMON;
        for (Rarity r : new Rarity[] {Rarity.LEGENDARY, Rarity.EPIC, Rarity.RARE, Rarity.COMMON})
        {
            acc += r.chance;
            if (x < acc) { pick = r; break; }
        }
        List<Spec> of = new ArrayList<>();
        for (Spec s : ALL) if (s.rarity == pick) of.add(s);
        return of.get(rng.nextInt(of.size()));
    }

    /** Any animation of the given rarity (the shared reveal at a bingo's start always plays a Legendary one). */
    public static Spec rollOf(Rarity rarity, Random rng)
    {
        List<Spec> of = new ArrayList<>();
        for (Spec s : ALL) if (s.rarity == rarity) of.add(s);
        return of.get(rng.nextInt(of.size()));
    }

    /**
     * For each cell (row * size + col), its place in the arrival order: 0 arrives first, n-1 last. Ties are fine
     * (those tiles arrive together).
     */
    public static int[] ranks(Order order, int size, long seed)
    {
        int n = size * size;
        double[] key = new double[n];
        Random rng = new Random(seed);
        double mid = (size - 1) / 2.0;
        int[] spiral = spiralIndex(size);
        for (int r = 0; r < size; r++)
        {
            for (int c = 0; c < size; c++)
            {
                int i = r * size + c;
                double ring = Math.max(Math.abs(r - mid), Math.abs(c - mid));
                switch (order)
                {
                    case ROW: key[i] = i; break;
                    case COLUMN: key[i] = c * size + r; break;
                    case SERPENT: key[i] = r * size + (r % 2 == 0 ? c : size - 1 - c); break;
                    case DIAGONAL: key[i] = (r + c) * size + r; break;
                    case CROSSING: key[i] = (r + (size - 1 - c)) * size + r; break;
                    case SPIRAL: key[i] = spiral[i]; break;
                    case UNWINDING: key[i] = n - 1 - spiral[i]; break;
                    case HEART: key[i] = ring * 100 + Math.atan2(r - mid, c - mid); break;
                    case SIEGE: key[i] = (size - ring) * 100 + Math.atan2(r - mid, c - mid); break;
                    case CHEQUER: key[i] = ((r + c) % 2) * n + i; break;
                    case SCATTER: key[i] = rng.nextDouble(); break;
                    case PINCER: { int k = r * size + c; key[i] = Math.min(k, n - 1 - k) * 2 + (k < n / 2.0 ? 0 : 1); break; }
                    default: key[i] = i;
                }
            }
        }
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        Arrays.sort(idx, Comparator.comparingDouble(i -> key[i]));
        int[] rank = new int[n];
        for (int k = 0; k < n; k++) rank[idx[k]] = k;
        return rank;
    }

    /** Spiral from the outside in: the position of each cell along the spiral. */
    private static int[] spiralIndex(int size)
    {
        int[] out = new int[size * size];
        int top = 0, bottom = size - 1, left = 0, right = size - 1, k = 0;
        while (top <= bottom && left <= right)
        {
            for (int c = left; c <= right; c++) out[top * size + c] = k++;
            for (int r = top + 1; r <= bottom; r++) out[r * size + right] = k++;
            if (top < bottom) for (int c = right - 1; c >= left; c--) out[bottom * size + c] = k++;
            if (left < right) for (int r = bottom - 1; r > top; r--) out[r * size + left] = k++;
            top++; bottom--; left++; right--;
        }
        return out;
    }

    /**
     * Where a tile is while it arrives. fp: 0 = just starting, 1 = landed. Returns {centreX, centreY, scaleX, scaleY,
     * rotation (radians), alpha}. tx/ty: the tile's own centre; bx/by/bw/bh: the board; deckX/deckY: the deal deck.
     */
    public static double[] pose(Entrance e, double fp, int idx, double tx, double ty, double cell,
                                double bx, double by, double bw, double bh, double deckX, double deckY)
    {
        double p = Math.max(0, Math.min(1, fp));
        double eo = easeOut(p), eb = easeOutBack(p);
        double cx = tx, cy = ty, sx = 1, sy = 1, rot = 0, a = Math.min(1, 0.25 + p * 1.5);
        double bcx = bx + bw / 2, bcy = by + bh / 2;
        long h = hash(idx);
        switch (e)
        {
            case DEAL:
                cx = deckX + (tx - deckX) * eo;
                cy = deckY + (ty - deckY) * eo - Math.sin(Math.PI * eo) * cell * 0.9;
                sx = sy = 0.35 + 0.65 * eb;
                rot = (1 - eo) * (idx % 2 == 0 ? -0.6 : 0.6);
                break;
            case DROP: cy = ty - (1 - eo) * (bh * 0.6); break;
            case RISE: cy = ty + (1 - eo) * (bh * 0.6); break;
            case TIDE: cx = tx - (1 - eo) * (bw * 0.7); break;
            case GALE: cx = tx + (1 - eo) * (bw * 0.7); break;
            case MIST: sx = sy = 0.9 + 0.1 * eo; a = eo; break;
            case BLOOM: sx = sy = Math.max(0.01, eb); break;
            case FLIP: sx = Math.max(0.02, eo); sy = 0.85 + 0.15 * eb; break;
            case WHIRL: rot = (1 - eo) * Math.PI * 2 * (idx % 2 == 0 ? 1 : -1); sx = sy = Math.max(0.05, eb); break;
            case BOUNCE: cy = ty - (1 - easeOutBounce(p)) * (bh * 0.55); break;
            case VORTEX:
            {
                double dx = tx - bcx, dy = ty - bcy, ang = (1 - eo) * Math.PI * 1.6;
                double cs = Math.cos(ang), sn = Math.sin(ang), rad = 0.25 + 0.75 * eo;
                cx = bcx + (dx * cs - dy * sn) * rad;
                cy = bcy + (dx * sn + dy * cs) * rad;
                rot = ang; sx = sy = 0.3 + 0.7 * eo;
                break;
            }
            case NOVA: cx = bcx + (tx - bcx) * eb; cy = bcy + (ty - bcy) * eb; sx = sy = 0.2 + 0.8 * eo; break;
            case CONVERGE:
            {
                double ox = tx < bcx ? bx - cell : bx + bw + cell, oy = ty < bcy ? by - cell : by + bh + cell;
                cx = ox + (tx - ox) * eo; cy = oy + (ty - oy) * eo; rot = (1 - eo) * 0.8;
                break;
            }
            case RAIN:
            {
                double startX = bx + (h % 1000) / 1000.0 * bw;
                cx = startX + (tx - startX) * eo + Math.sin(p * Math.PI * 3 + idx) * cell * 0.15 * (1 - eo);
                cy = ty - (1 - eo) * (bh * 0.9);
                break;
            }
            default: break;
        }
        return new double[] {cx, cy, sx, sy, rot, a};
    }

    public static double easeOut(double t)
    {
        double u = 1 - Math.max(0, Math.min(1, t));
        return 1 - u * u * u;
    }

    public static double easeOutBack(double t)
    {
        double c1 = 1.70158, c3 = c1 + 1, u = Math.max(0, Math.min(1, t)) - 1;
        return 1 + c3 * u * u * u + c1 * u * u;
    }

    public static double easeOutBounce(double t)
    {
        double x = Math.max(0, Math.min(1, t)), n1 = 7.5625, d1 = 2.75;
        if (x < 1 / d1) return n1 * x * x;
        if (x < 2 / d1) { x -= 1.5 / d1; return n1 * x * x + 0.75; }
        if (x < 2.5 / d1) { x -= 2.25 / d1; return n1 * x * x + 0.9375; }
        x -= 2.625 / d1;
        return n1 * x * x + 0.984375;
    }

    /** A stable pseudo-random number per tile (sparkle positions, rain start points). */
    public static long hash(long x)
    {
        x ^= x >>> 33; x *= 0xff51afd7ed558ccdL; x ^= x >>> 33; x *= 0xc4ceb9fe1a85ec53L; x ^= x >>> 33;
        return x & Long.MAX_VALUE;
    }
}
