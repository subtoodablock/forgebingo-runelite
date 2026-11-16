package com.forgebingo.core;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * The board's opening animations (owner, 2026-11-15): 100 of them, each 3 seconds, rolled by rarity every time the
 * board opens. Common 45 (60%), Uncommon 30 (25%), Epic 17 (12%), Legendary 8 (3%).
 *
 * <p>Each one is a hand-picked mix of an order (which tiles arrive first), an entrance (the path a tile takes), a pace
 * (how the arrivals are spread over time and how long each flight is) and an effect. Common ones are clean and plain;
 * Uncommon adds a flourish as each tile lands (sparkles, ripples, a glint, a puff of dust); Epic adds motion trails, a
 * glow and a strong effect (lightning, light pillars, embers, shockwaves, prism colours, frost); Legendary is a full
 * show: its own scene behind the board (vortex, starfall, runes, aurora, eclipse, inferno, tempest, prism) and a burst
 * at the end. No two share an order and entrance pair, and every name is unique. Pure logic (no RuneLite), so it is
 * unit-tested; BoardOverlay draws it.
 */
public final class OpeningAnim
{
    /** the whole opening, first tile moving to the last effect fading */
    public static final long DEAL_MS = 3000;
    /** the last tile lands by here, leaving room for the landing effects inside DEAL_MS */
    public static final long LAND_MS = 2550;
    /** sparkles / glow / effect after a tile lands (Uncommon and up) */
    public static final long LANDING_FX_MS = 420;

    public enum Rarity
    {
        COMMON("Common", new Color(0xb4b4b4), 0.60),
        UNCOMMON("Uncommon", new Color(0x3ec46d), 0.25),
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

    /** How a tile arrives (its path, turn, scale and fade). */
    public enum Entrance
    {
        // plain
        DEAL, DROP, RISE, TIDE, GALE, MIST, BLOOM, FLIP, UNROLL, ZOOM,
        // with some character
        BOUNCE, WHIRL, LEAF, JELLY, SWING, QUAKE, BOOMERANG, RAIN, CONVERGE, SLINGSHOT,
        // big moves
        VORTEX, NOVA, CORKSCREW, COMET, WARP, BLINK, SWARM, ORBIT, STAMP, ASCEND
    }

    /** Which tiles arrive first. Tiles with the same key arrive together. */
    public enum Order
    {
        ROW, COLUMN, SERPENT, DIAGONAL, CROSSING, SPIRAL, UNWINDING, HEART, SIEGE, CHEQUER, SCATTER, PINCER,
        RISING, WEAVE, RIPPLE, CORNERS, PLUS, SWEEP, COMB, CURTAIN
    }

    /** How the arrivals are spread over the opening, and how long one tile's flight takes. */
    public enum Pace
    {
        /** evenly spaced */
        EVEN(460),
        /** quick, snappy flights */
        BRISK(300),
        /** long, overlapping flights */
        DRIFT(760),
        /** a slow start that speeds up */
        SURGE(460),
        /** a rush that settles */
        EASE(460),
        /** five bursts of tiles together */
        PULSE(420);

        public final long flightMs;

        Pace(long flightMs)
        {
            this.flightMs = flightMs;
        }
    }

    /** The extra effect: none for Common, a landing flourish for Uncommon, a strong effect for Epic, a whole scene for Legendary. */
    public enum Fx
    {
        NONE(Rarity.COMMON, -1),
        SPARKLE(Rarity.UNCOMMON, -1), RIPPLE(Rarity.UNCOMMON, -1), GLINT(Rarity.UNCOMMON, -1), DUST(Rarity.UNCOMMON, -1),
        BOLT(Rarity.EPIC, 0x8fc8ff), BEAM(Rarity.EPIC, 0xfff0b0), EMBER(Rarity.EPIC, 0xff7a2a), SHOCK(Rarity.EPIC, -1),
        PRISM(Rarity.EPIC, -2), FROST(Rarity.EPIC, 0x9fe6ff),
        VORTEX(Rarity.LEGENDARY, 0xffc65a), STARFALL(Rarity.LEGENDARY, 0xbfe0ff), RUNES(Rarity.LEGENDARY, 0x5ff0e0),
        AURORA(Rarity.LEGENDARY, 0x6dffb0), ECLIPSE(Rarity.LEGENDARY, 0xffe6a0), INFERNO(Rarity.LEGENDARY, 0xff6a2a),
        TEMPEST(Rarity.LEGENDARY, 0x8fb8ff), PRISMATIC(Rarity.LEGENDARY, -2);

        public final Rarity tier;
        /** the effect's colour as 0xRRGGBB; -1 = the rarity's colour, -2 = a rainbow (a hue per tile) */
        public final int accent;

        Fx(Rarity tier, int accent)
        {
            this.tier = tier;
            this.accent = accent;
        }
    }

    public static final class Spec
    {
        public final String name;
        public final Rarity rarity;
        public final Order order;
        public final Entrance entrance;
        public final Pace pace;
        public final Fx fx;
        /** the effect colour (rainbow effects use {@link #accent(int)}) */
        public final Color colour;

        Spec(String name, Rarity rarity, Order order, Entrance entrance, Pace pace, Fx fx)
        {
            this.name = name;
            this.rarity = rarity;
            this.order = order;
            this.entrance = entrance;
            this.pace = pace;
            this.fx = fx;
            this.colour = fx.accent >= 0 ? new Color(fx.accent) : rarity.colour;
        }

        /** The effect colour for one tile: the same for every tile, or a hue of the rainbow for prism effects. */
        public Color accent(int idx)
        {
            return fx.accent == -2 ? RAINBOW[Math.floorMod(idx * 5, RAINBOW.length)] : colour;
        }

        @Override
        public String toString()
        {
            return name + " (" + rarity.label + ")";
        }
    }

    /** twelve bright hues, for prism effects (made once, so drawing allocates nothing) */
    public static final Color[] RAINBOW = new Color[12];

    static
    {
        for (int i = 0; i < RAINBOW.length; i++) RAINBOW[i] = Color.getHSBColor(i / 12f, 0.62f, 1f);
    }

    public static final int COUNT = 100;
    public static final List<Spec> ALL = build();

    private OpeningAnim()
    {
    }

    private static List<Spec> build()
    {
        Rarity c = Rarity.COMMON, u = Rarity.UNCOMMON, e = Rarity.EPIC, l = Rarity.LEGENDARY;
        List<Spec> out = new ArrayList<>();
        // ---- Common (45): clean arrivals, no extra effect ----
        out.add(new Spec("Dealer's Rows", c, Order.ROW, Entrance.DEAL, Pace.EVEN, Fx.NONE));
        out.add(new Spec("Diagonal Deal", c, Order.DIAGONAL, Entrance.DEAL, Pace.EVEN, Fx.NONE));
        out.add(new Spec("Spiral Deal", c, Order.SPIRAL, Entrance.DEAL, Pace.BRISK, Fx.NONE));
        out.add(new Spec("Shuffled Deal", c, Order.SCATTER, Entrance.DEAL, Pace.SURGE, Fx.NONE));
        out.add(new Spec("Serpent Deal", c, Order.SERPENT, Entrance.DEAL, Pace.EVEN, Fx.NONE));
        out.add(new Spec("Two-Ended Deal", c, Order.PINCER, Entrance.DEAL, Pace.EASE, Fx.NONE));
        out.add(new Spec("Round the Table", c, Order.SWEEP, Entrance.DEAL, Pace.EVEN, Fx.NONE));
        out.add(new Spec("Falling Rows", c, Order.ROW, Entrance.DROP, Pace.BRISK, Fx.NONE));
        out.add(new Spec("Falling Columns", c, Order.COLUMN, Entrance.DROP, Pace.EVEN, Fx.NONE));
        out.add(new Spec("Diagonal Drop", c, Order.DIAGONAL, Entrance.DROP, Pace.EASE, Fx.NONE));
        out.add(new Spec("Chequered Drop", c, Order.CHEQUER, Entrance.DROP, Pace.PULSE, Fx.NONE));
        out.add(new Spec("Hailstones", c, Order.SCATTER, Entrance.DROP, Pace.BRISK, Fx.NONE));
        out.add(new Spec("Four Corners", c, Order.CORNERS, Entrance.DROP, Pace.EVEN, Fx.NONE));
        out.add(new Spec("Crosshair Drop", c, Order.PLUS, Entrance.DROP, Pace.PULSE, Fx.NONE));
        out.add(new Spec("Walls Close In", c, Order.SIEGE, Entrance.DROP, Pace.EVEN, Fx.NONE));
        out.add(new Spec("Bottom Up", c, Order.RISING, Entrance.RISE, Pace.EVEN, Fx.NONE));
        out.add(new Spec("Rising Columns", c, Order.COLUMN, Entrance.RISE, Pace.BRISK, Fx.NONE));
        out.add(new Spec("Woven Lift", c, Order.WEAVE, Entrance.RISE, Pace.EVEN, Fx.NONE));
        out.add(new Spec("Comb Lift", c, Order.COMB, Entrance.RISE, Pace.PULSE, Fx.NONE));
        out.add(new Spec("Unwind", c, Order.UNWINDING, Entrance.RISE, Pace.EVEN, Fx.NONE));
        out.add(new Spec("Incoming Tide", c, Order.ROW, Entrance.TIDE, Pace.EVEN, Fx.NONE));
        out.add(new Spec("Snaking Tide", c, Order.SERPENT, Entrance.TIDE, Pace.EASE, Fx.NONE));
        out.add(new Spec("Cross Current", c, Order.CROSSING, Entrance.TIDE, Pace.BRISK, Fx.NONE));
        out.add(new Spec("East Wind", c, Order.COLUMN, Entrance.GALE, Pace.EVEN, Fx.NONE));
        out.add(new Spec("Gust Line", c, Order.DIAGONAL, Entrance.GALE, Pace.SURGE, Fx.NONE));
        out.add(new Spec("Pincer Wind", c, Order.PINCER, Entrance.GALE, Pace.EVEN, Fx.NONE));
        out.add(new Spec("Morning Mist", c, Order.ROW, Entrance.MIST, Pace.DRIFT, Fx.NONE));
        out.add(new Spec("Fog Ripple", c, Order.RIPPLE, Entrance.MIST, Pace.DRIFT, Fx.NONE));
        out.add(new Spec("Scattered Fog", c, Order.SCATTER, Entrance.MIST, Pace.EVEN, Fx.NONE));
        out.add(new Spec("Curtain Call", c, Order.CURTAIN, Entrance.MIST, Pace.EVEN, Fx.NONE));
        out.add(new Spec("Heart Bloom", c, Order.HEART, Entrance.BLOOM, Pace.EVEN, Fx.NONE));
        out.add(new Spec("Closing Bloom", c, Order.SIEGE, Entrance.BLOOM, Pace.EASE, Fx.NONE));
        out.add(new Spec("Chequered Pop", c, Order.CHEQUER, Entrance.BLOOM, Pace.PULSE, Fx.NONE));
        out.add(new Spec("Pond Ripple", c, Order.RIPPLE, Entrance.BLOOM, Pace.EVEN, Fx.NONE));
        out.add(new Spec("Corner Pop", c, Order.CORNERS, Entrance.BLOOM, Pace.BRISK, Fx.NONE));
        out.add(new Spec("Card Flip Rows", c, Order.ROW, Entrance.FLIP, Pace.EVEN, Fx.NONE));
        out.add(new Spec("Domino Flip", c, Order.DIAGONAL, Entrance.FLIP, Pace.BRISK, Fx.NONE));
        out.add(new Spec("Turning Pages", c, Order.COLUMN, Entrance.FLIP, Pace.SURGE, Fx.NONE));
        out.add(new Spec("Spiral Turn", c, Order.SPIRAL, Entrance.FLIP, Pace.EVEN, Fx.NONE));
        out.add(new Spec("Loom Flip", c, Order.WEAVE, Entrance.FLIP, Pace.BRISK, Fx.NONE));
        out.add(new Spec("Scroll Unroll", c, Order.ROW, Entrance.UNROLL, Pace.EVEN, Fx.NONE));
        out.add(new Spec("Blinds", c, Order.CURTAIN, Entrance.UNROLL, Pace.EASE, Fx.NONE));
        out.add(new Spec("Unfurling Cross", c, Order.PLUS, Entrance.UNROLL, Pace.BRISK, Fx.NONE));
        out.add(new Spec("Touchdown", c, Order.HEART, Entrance.ZOOM, Pace.EVEN, Fx.NONE));
        out.add(new Spec("Skydive", c, Order.SCATTER, Entrance.ZOOM, Pace.DRIFT, Fx.NONE));

        // ---- Uncommon (30): characterful arrivals plus a landing flourish ----
        out.add(new Spec("Bouncing Rows", u, Order.ROW, Entrance.BOUNCE, Pace.EVEN, Fx.DUST));
        out.add(new Spec("Trampoline", u, Order.CHEQUER, Entrance.BOUNCE, Pace.PULSE, Fx.SPARKLE));
        out.add(new Spec("Bouncing Spiral", u, Order.SPIRAL, Entrance.BOUNCE, Pace.BRISK, Fx.RIPPLE));
        out.add(new Spec("Pinwheel Snake", u, Order.SERPENT, Entrance.WHIRL, Pace.EVEN, Fx.SPARKLE));
        out.add(new Spec("Whirling Heart", u, Order.HEART, Entrance.WHIRL, Pace.EASE, Fx.RIPPLE));
        out.add(new Spec("Spinning Corners", u, Order.CORNERS, Entrance.WHIRL, Pace.BRISK, Fx.GLINT));
        out.add(new Spec("Autumn Leaves", u, Order.SCATTER, Entrance.LEAF, Pace.DRIFT, Fx.DUST));
        out.add(new Spec("Leaf Fall", u, Order.ROW, Entrance.LEAF, Pace.EVEN, Fx.SPARKLE));
        out.add(new Spec("Windblown", u, Order.DIAGONAL, Entrance.LEAF, Pace.SURGE, Fx.GLINT));
        out.add(new Spec("Jelly Columns", u, Order.COLUMN, Entrance.JELLY, Pace.EVEN, Fx.RIPPLE));
        out.add(new Spec("Jelly Heart", u, Order.HEART, Entrance.JELLY, Pace.PULSE, Fx.SPARKLE));
        out.add(new Spec("Wobble Ripple", u, Order.RIPPLE, Entrance.JELLY, Pace.EVEN, Fx.GLINT));
        out.add(new Spec("Pendulums", u, Order.ROW, Entrance.SWING, Pace.EVEN, Fx.GLINT));
        out.add(new Spec("Swinging Signs", u, Order.COMB, Entrance.SWING, Pace.PULSE, Fx.DUST));
        out.add(new Spec("Chandelier", u, Order.CURTAIN, Entrance.SWING, Pace.EASE, Fx.SPARKLE));
        out.add(new Spec("Tremor", u, Order.SCATTER, Entrance.QUAKE, Pace.BRISK, Fx.DUST));
        out.add(new Spec("Aftershock", u, Order.RIPPLE, Entrance.QUAKE, Pace.EVEN, Fx.RIPPLE));
        out.add(new Spec("Rumble", u, Order.RISING, Entrance.QUAKE, Pace.SURGE, Fx.GLINT));
        out.add(new Spec("Boomerang", u, Order.COLUMN, Entrance.BOOMERANG, Pace.EVEN, Fx.SPARKLE));
        out.add(new Spec("Return Throw", u, Order.SWEEP, Entrance.BOOMERANG, Pace.BRISK, Fx.RIPPLE));
        out.add(new Spec("Overshoot", u, Order.PINCER, Entrance.BOOMERANG, Pace.PULSE, Fx.GLINT));
        out.add(new Spec("Raindrops", u, Order.SCATTER, Entrance.RAIN, Pace.EVEN, Fx.RIPPLE));
        out.add(new Spec("Downpour", u, Order.ROW, Entrance.RAIN, Pace.BRISK, Fx.DUST));
        out.add(new Spec("Drizzle", u, Order.WEAVE, Entrance.RAIN, Pace.DRIFT, Fx.SPARKLE));
        out.add(new Spec("Rally", u, Order.SIEGE, Entrance.CONVERGE, Pace.EVEN, Fx.SPARKLE));
        out.add(new Spec("Muster", u, Order.PLUS, Entrance.CONVERGE, Pace.EASE, Fx.GLINT));
        out.add(new Spec("Four Winds", u, Order.CORNERS, Entrance.CONVERGE, Pace.BRISK, Fx.DUST));
        out.add(new Spec("Slingshot", u, Order.DIAGONAL, Entrance.SLINGSHOT, Pace.EVEN, Fx.SPARKLE));
        out.add(new Spec("Catapults", u, Order.CHEQUER, Entrance.SLINGSHOT, Pace.BRISK, Fx.DUST));
        out.add(new Spec("Spring Loaded", u, Order.UNWINDING, Entrance.SLINGSHOT, Pace.SURGE, Fx.RIPPLE));

        // ---- Epic (17): big moves, trails and glow, and a strong effect ----
        out.add(new Spec("Thunderstrike", e, Order.SCATTER, Entrance.STAMP, Pace.BRISK, Fx.BOLT));
        out.add(new Spec("Hammerfall", e, Order.ROW, Entrance.STAMP, Pace.PULSE, Fx.SHOCK));
        out.add(new Spec("Pillars of Light", e, Order.SPIRAL, Entrance.WARP, Pace.EVEN, Fx.BEAM));
        out.add(new Spec("Teleporters", e, Order.CHEQUER, Entrance.WARP, Pace.PULSE, Fx.BEAM));
        out.add(new Spec("Firewhirl", e, Order.HEART, Entrance.CORKSCREW, Pace.EVEN, Fx.EMBER));
        out.add(new Spec("Prism Twister", e, Order.SERPENT, Entrance.CORKSCREW, Pace.BRISK, Fx.PRISM));
        out.add(new Spec("Comet Shower", e, Order.DIAGONAL, Entrance.COMET, Pace.EVEN, Fx.EMBER));
        out.add(new Spec("Meteor Strike", e, Order.COLUMN, Entrance.COMET, Pace.SURGE, Fx.SHOCK));
        out.add(new Spec("Blink Step", e, Order.SWEEP, Entrance.BLINK, Pace.EVEN, Fx.PRISM));
        out.add(new Spec("Phase Shift", e, Order.RIPPLE, Entrance.BLINK, Pace.DRIFT, Fx.FROST));
        out.add(new Spec("Firefly Swarm", e, Order.SCATTER, Entrance.SWARM, Pace.EVEN, Fx.EMBER));
        out.add(new Spec("Hornet Storm", e, Order.SIEGE, Entrance.SWARM, Pace.BRISK, Fx.BOLT));
        out.add(new Spec("Ice Orbit", e, Order.CURTAIN, Entrance.ORBIT, Pace.EVEN, Fx.FROST));
        out.add(new Spec("Satellites", e, Order.PLUS, Entrance.ORBIT, Pace.SURGE, Fx.PRISM));
        out.add(new Spec("Shockwave", e, Order.RIPPLE, Entrance.NOVA, Pace.EVEN, Fx.SHOCK));
        out.add(new Spec("Frost Nova", e, Order.CORNERS, Entrance.NOVA, Pace.PULSE, Fx.FROST));
        out.add(new Spec("Ball Lightning", e, Order.UNWINDING, Entrance.WHIRL, Pace.BRISK, Fx.BOLT));

        // ---- Legendary (8): a whole scene behind the board, trails, glow and a finale burst ----
        out.add(new Spec("The Vortex", l, Order.SPIRAL, Entrance.VORTEX, Pace.EVEN, Fx.VORTEX));
        out.add(new Spec("Starfall", l, Order.SCATTER, Entrance.COMET, Pace.DRIFT, Fx.STARFALL));
        out.add(new Spec("Summoning Circle", l, Order.SWEEP, Entrance.ORBIT, Pace.EVEN, Fx.RUNES));
        out.add(new Spec("Northern Lights", l, Order.WEAVE, Entrance.WARP, Pace.EVEN, Fx.AURORA));
        out.add(new Spec("Total Eclipse", l, Order.SIEGE, Entrance.NOVA, Pace.EVEN, Fx.ECLIPSE));
        out.add(new Spec("Phoenix Rising", l, Order.RISING, Entrance.ASCEND, Pace.SURGE, Fx.INFERNO));
        out.add(new Spec("Tempest", l, Order.CROSSING, Entrance.BLINK, Pace.EVEN, Fx.TEMPEST));
        out.add(new Spec("Rainbow Cyclone", l, Order.RIPPLE, Entrance.CORKSCREW, Pace.EVEN, Fx.PRISMATIC));
        return Collections.unmodifiableList(out);
    }

    /** Rolls a rarity by its chance, then any animation of that rarity. */
    public static Spec roll(Random rng)
    {
        double x = rng.nextDouble(), acc = 0;
        Rarity pick = Rarity.COMMON;
        for (Rarity r : new Rarity[] {Rarity.LEGENDARY, Rarity.EPIC, Rarity.UNCOMMON, Rarity.COMMON})
        {
            acc += r.chance;
            if (x < acc) { pick = r; break; }
        }
        return rollOf(pick, rng);
    }

    /** Any animation of the given rarity (the shared reveal at a bingo's start always plays a Legendary one). */
    public static Spec rollOf(Rarity rarity, Random rng)
    {
        List<Spec> of = new ArrayList<>();
        for (Spec s : ALL) if (s.rarity == rarity) of.add(s);
        return of.get(rng.nextInt(of.size()));
    }

    /** The animation with this name, or null. */
    public static Spec byName(String name)
    {
        for (Spec s : ALL) if (s.name.equalsIgnoreCase(name)) return s;
        return null;
    }

    /**
     * When a tile with this place in the arrival order leaves (ms after the board opened). The last place leaves
     * so that it lands exactly at LAND_MS.
     */
    public static double startMs(Pace pace, int rank, int maxRank)
    {
        double t = maxRank <= 0 ? 0 : Math.max(0, Math.min(1, rank / (double) maxRank));
        switch (pace)
        {
            case SURGE: t = Math.pow(t, 1.7); break;
            case EASE: t = 1 - Math.pow(1 - t, 1.7); break;
            case PULSE: t = Math.floor(t * 4.9999) / 4; break;
            default: break;
        }
        return (LAND_MS - pace.flightMs) * t;
    }

    /**
     * For each cell (row * size + col), its place in the arrival order: 0 arrives first. Cells with the same key share
     * a place (they arrive together), so the places run 0..k with no gaps, k at most n-1.
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
                double angle = Math.atan2(r - mid, c - mid);
                switch (order)
                {
                    case ROW: key[i] = i; break;
                    case COLUMN: key[i] = c * size + r; break;
                    case SERPENT: key[i] = r * size + (r % 2 == 0 ? c : size - 1 - c); break;
                    case DIAGONAL: key[i] = (r + c) * size + r; break;
                    case CROSSING: key[i] = (r + (size - 1 - c)) * size + r; break;
                    case SPIRAL: key[i] = spiral[i]; break;
                    case UNWINDING: key[i] = n - 1 - spiral[i]; break;
                    case HEART: key[i] = ring * 100 + angle; break;
                    case SIEGE: key[i] = (size - ring) * 100 + angle; break;
                    case CHEQUER: key[i] = ((r + c) % 2) * n + i; break;
                    case SCATTER: key[i] = rng.nextDouble(); break;
                    case PINCER: key[i] = Math.min(i, n - 1 - i) * 2 + (i < n / 2.0 ? 0 : 1); break;
                    case RISING: key[i] = (size - 1 - r) * size + c; break;
                    case WEAVE: key[i] = c * size + (c % 2 == 0 ? r : size - 1 - r); break;
                    // rings of equal distance from the middle arrive together, like a ripple on a pond
                    case RIPPLE: key[i] = Math.round(Math.hypot(r - mid, c - mid) * 2); break;
                    // the four corners first, then inwards, all four in step
                    case CORNERS: key[i] = Math.min(r, size - 1 - r) + Math.min(c, size - 1 - c); break;
                    // the middle row and column first, then outwards
                    case PLUS: key[i] = Math.round(Math.min(Math.abs(r - mid), Math.abs(c - mid)) * 2) * size + Math.round(Math.max(Math.abs(r - mid), Math.abs(c - mid)) * 2); break;
                    // a clock hand sweeping round from twelve o'clock (cells along the hand arrive together)
                    case SWEEP:
                    {
                        double clock = Math.atan2(c - mid, mid - r);
                        if (clock < 0) clock += Math.PI * 2;
                        key[i] = Math.round(clock * 100);
                        break;
                    }
                    // every column at once, even ones from the top and odd ones from the bottom
                    case COMB: key[i] = c % 2 == 0 ? r : size - 1 - r; break;
                    // the middle columns first, top to bottom, opening out like a curtain
                    case CURTAIN: key[i] = Math.round(Math.abs(c - mid) * 2) * size + r; break;
                    default: key[i] = i;
                }
            }
        }
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        Arrays.sort(idx, Comparator.comparingDouble(i -> key[i]));
        int[] rank = new int[n];
        int place = -1;
        double last = Double.NaN;
        for (int k = 0; k < n; k++)
        {
            double v = key[idx[k]];
            if (k == 0 || v != last) place++;
            last = v;
            rank[idx[k]] = place;
        }
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
        double[] out = new double[6];
        pose(out, e, fp, idx, tx, ty, cell, bx, by, bw, bh, deckX, deckY);
        return out;
    }

    /** As {@link #pose(Entrance, double, int, double, double, double, double, double, double, double, double, double)}, into out (no allocation). */
    public static void pose(double[] out, Entrance e, double fp, int idx, double tx, double ty, double cell,
                            double bx, double by, double bw, double bh, double deckX, double deckY)
    {
        double p = Math.max(0, Math.min(1, fp));
        double eo = easeOut(p), eb = easeOutBack(p), q = 1 - eo;
        double cx = tx, cy = ty, sx = 1, sy = 1, rot = 0, a = Math.min(1, 0.25 + p * 1.5);
        double bcx = bx + bw / 2, bcy = by + bh / 2;
        long h = hash(idx);
        double phase = (h % 628) / 100.0;
        int side = idx % 2 == 0 ? 1 : -1;
        switch (e)
        {
            case DEAL:
                cx = deckX + (tx - deckX) * eo;
                cy = deckY + (ty - deckY) * eo - Math.sin(Math.PI * eo) * cell * 0.9;
                sx = sy = 0.35 + 0.65 * eb;
                rot = q * 0.6 * -side;
                break;
            case DROP: cy = ty - q * (bh * 0.6); break;
            case RISE: cy = ty + q * (bh * 0.6); break;
            case TIDE: cx = tx - q * (bw * 0.7); break;
            case GALE: cx = tx + q * (bw * 0.7); break;
            case MIST: sx = sy = 0.9 + 0.1 * eo; a = eo; break;
            case BLOOM: sx = sy = Math.max(0.01, eb); break;
            case FLIP: sx = Math.max(0.02, eo); sy = 0.85 + 0.15 * eb; break;
            case UNROLL: sy = Math.max(0.02, eb); sx = 0.92 + 0.08 * eo; break;
            case ZOOM: sx = sy = 1 + 1.8 * q * q; a = eo; break;
            case BOUNCE: cy = ty - (1 - easeOutBounce(p)) * (bh * 0.55); break;
            case WHIRL: rot = q * Math.PI * 2 * side; sx = sy = Math.max(0.05, eb); break;
            case LEAF:
            {
                // drifts down, swaying side to side like a falling leaf
                double sway = Math.sin(p * Math.PI * 2.5 + phase) * q;
                cy = ty - q * (bh * 0.7);
                cx = tx + sway * cell * 0.7;
                rot = sway * 0.55;
                break;
            }
            case JELLY:
            {
                double grow = easeOut(Math.min(1, p * 1.8));
                double w = Math.sin(p * Math.PI * 4) * (1 - p) * 0.32;
                sx = Math.max(0.02, grow * (1 + w));
                sy = Math.max(0.02, grow * (1 - w));
                break;
            }
            case SWING:
            {
                // hangs from a point above its slot and swings to rest while it comes down
                double len = cell * 1.4, th = 1.25 * q * Math.cos(p * Math.PI * 2.5);
                cx = tx + len * Math.sin(th);
                cy = ty - len + len * Math.cos(th) - q * bh * 0.35;
                rot = -th;
                a = Math.min(1, p * 3);
                break;
            }
            case QUAKE:
            {
                double amp = cell * 0.11 * (1 - p);
                cx = tx + Math.sin(p * 61 + phase) * amp;
                cy = ty + Math.cos(p * 47 + phase) * amp;
                sx = sy = 1 + 0.25 * q;
                a = Math.min(1, p * 2.5);
                break;
            }
            case BOOMERANG:
            {
                // flies in from the right, overshoots its slot and swings back
                cx = tx + (1 - easeOutBack(p, 3.2)) * bw * 0.8;
                cy = ty - Math.sin(Math.PI * p) * cell * 0.6;
                rot = q * -Math.PI * 1.5;
                break;
            }
            case RAIN:
            {
                double startX = bx + (h % 1000) / 1000.0 * bw;
                cx = startX + (tx - startX) * eo + Math.sin(p * Math.PI * 3 + idx) * cell * 0.15 * q;
                cy = ty - q * (bh * 0.9);
                break;
            }
            case CONVERGE:
            {
                double ox = tx < bcx ? bx - cell : bx + bw + cell, oy = ty < bcy ? by - cell : by + bh + cell;
                cx = ox + (tx - ox) * eo; cy = oy + (ty - oy) * eo; rot = q * 0.8;
                break;
            }
            case SLINGSHOT:
            {
                // shows up small, pulls back below its slot, then snaps up into place
                if (p < 0.35)
                {
                    double k = p / 0.35;
                    cy = ty + cell * 0.6 * easeOut(k);
                    sx = sy = 0.5 + 0.3 * k;
                    a = k;
                }
                else
                {
                    double k = easeOutBack((p - 0.35) / 0.65, 2.4);
                    cy = ty + cell * 0.6 * (1 - k);
                    sx = sy = 0.8 + 0.2 * k;
                    a = 1;
                }
                break;
            }
            case VORTEX:
            {
                double dx = tx - bcx, dy = ty - bcy, ang = q * Math.PI * 1.6;
                double cs = Math.cos(ang), sn = Math.sin(ang), rad = 0.25 + 0.75 * eo;
                cx = bcx + (dx * cs - dy * sn) * rad;
                cy = bcy + (dx * sn + dy * cs) * rad;
                rot = ang; sx = sy = 0.3 + 0.7 * eo;
                break;
            }
            case NOVA: cx = bcx + (tx - bcx) * eb; cy = bcy + (ty - bcy) * eb; sx = sy = 0.2 + 0.8 * eo; break;
            case CORKSCREW:
            {
                // spirals down around its slot
                double ang = phase + q * Math.PI * 4, r = cell * 1.2 * q;
                cx = tx + Math.cos(ang) * r;
                cy = ty - q * bh * 0.5 + Math.sin(ang) * r * 0.45;
                rot = q * Math.PI * 2 * side;
                sx = sy = 0.4 + 0.6 * eo;
                break;
            }
            case COMET:
            {
                // a curved dive in from above the top right
                double sx0 = bx + bw + cell * 1.5, sy0 = by - cell * 2, kx = tx + (sx0 - tx) * 0.15, ky = sy0;
                cx = q * q * sx0 + 2 * q * eo * kx + eo * eo * tx;
                cy = q * q * sy0 + 2 * q * eo * ky + eo * eo * ty;
                rot = q * -0.9;
                sx = sy = 0.5 + 0.5 * eo;
                break;
            }
            case WARP:
            {
                // stretched thin and tall like a beam, then snapping to shape
                cy = ty - q * cell * 1.2;
                sx = 0.06 + 0.94 * eo;
                sy = 1 + 1.8 * q;
                a = Math.min(1, p * 2.5);
                break;
            }
            case BLINK:
            {
                // flickers in at three spots on the board, then dashes home
                double hopLen = 0.55 / 3;
                if (p < 0.55)
                {
                    int hop = Math.min(2, (int) (p / hopLen));
                    long hh = hash(idx * 7L + hop);
                    cx = bx + cell * 0.5 + (hh % 1000) / 1000.0 * Math.max(0, bw - cell);
                    cy = by + cell * 0.5 + ((hh >>> 12) % 1000) / 1000.0 * Math.max(0, bh - cell);
                    sx = sy = 0.55;
                    a = Math.sin((p - hop * hopLen) / hopLen * Math.PI);
                }
                else
                {
                    long hh = hash(idx * 7L + 2);
                    double fx0 = bx + cell * 0.5 + (hh % 1000) / 1000.0 * Math.max(0, bw - cell);
                    double fy0 = by + cell * 0.5 + ((hh >>> 12) % 1000) / 1000.0 * Math.max(0, bh - cell);
                    double k = (p - 0.55) / 0.45, ke = easeOut(k);
                    cx = fx0 + (tx - fx0) * ke;
                    cy = fy0 + (ty - fy0) * ke;
                    sx = sy = 0.55 + 0.45 * easeOutBack(k);
                    a = 1;
                }
                break;
            }
            case SWARM:
            {
                // from a ring around the board, curving in on a weaving path
                double r = Math.max(bw, bh) * 0.85;
                double sx0 = bcx + Math.cos(phase) * r, sy0 = bcy + Math.sin(phase) * r;
                double bend = Math.sin(Math.PI * eo) * cell * 1.4 * side;
                double dx = tx - sx0, dy = ty - sy0, len = Math.max(1, Math.hypot(dx, dy));
                cx = sx0 + dx * eo - dy / len * bend;
                cy = sy0 + dy * eo + dx / len * bend;
                rot = q * 1.4 * side;
                sx = sy = 0.4 + 0.6 * eo;
                break;
            }
            case ORBIT:
            {
                // circles its slot, closing in
                double ang = phase + q * Math.PI * 3, r = cell * 1.5 * q;
                cx = tx + Math.cos(ang) * r;
                cy = ty + Math.sin(ang) * r;
                rot = q * Math.PI * 1.5 * side;
                sx = sy = 0.45 + 0.55 * eo;
                break;
            }
            case STAMP:
            {
                // slams down from above the screen, big, then squashes as it hits
                double k = Math.min(1, p / 0.7);
                sx = sy = 2.1 - 1.1 * k * k;
                a = Math.min(1, k * 1.6);
                if (p > 0.7)
                {
                    double w = Math.sin((p - 0.7) / 0.3 * Math.PI) * 0.14;
                    sx = 1 + w;
                    sy = 1 - w;
                }
                break;
            }
            case ASCEND:
            {
                // rises from below the board, fanning out from the middle
                double sy0 = by + bh + cell;
                cx = bcx + (tx - bcx) * eo;
                cy = sy0 + (ty - sy0) * eb;
                rot = q * (tx < bcx ? -0.9 : 0.9);
                sx = sy = 0.3 + 0.7 * eo;
                break;
            }
            default: break;
        }
        out[0] = cx; out[1] = cy; out[2] = sx; out[3] = sy; out[4] = rot; out[5] = Math.max(0, Math.min(1, a));
    }

    public static double easeOut(double t)
    {
        double u = 1 - Math.max(0, Math.min(1, t));
        return 1 - u * u * u;
    }

    public static double easeOutBack(double t)
    {
        return easeOutBack(t, 1.70158);
    }

    /** Overshoots past 1 and settles back; a bigger c1 overshoots further. */
    public static double easeOutBack(double t, double c1)
    {
        double c3 = c1 + 1, u = Math.max(0, Math.min(1, t)) - 1;
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
