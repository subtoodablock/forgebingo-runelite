package com.forgebingo.core;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Points can be decimals (0.04, 0.25, 1.5). They are kept to 2 decimal places, sums are done in whole hundredths so
 * they never drift (0.1 + 0.2 is 0.3), and they print without trailing zeros (0.25, 1.5, 3).
 */
public final class Points
{
    private Points()
    {
    }

    /** A points value in hundredths (0.25 is 25). Unreadable or negative values count as 0. */
    public static long cents(double v)
    {
        if (Double.isNaN(v) || v <= 0) return 0;
        return Math.round(Math.min(v, 1_000_000_000d) * 100d);
    }

    public static double fromCents(long cents)
    {
        return cents / 100d;
    }

    /** Rounded to 2 decimal places, never negative. */
    public static double round(double v)
    {
        return fromCents(cents(v));
    }

    /** "0.25", "1.5", "3": at most 2 decimals, no trailing zeros. */
    public static String text(double v)
    {
        return BigDecimal.valueOf(cents(v), 2).setScale(2, RoundingMode.UNNECESSARY).stripTrailingZeros().toPlainString();
    }

    /** "1 pt", "0.25 pts", "3 pts". */
    public static String label(double v)
    {
        return text(v) + (cents(v) == 100 ? " pt" : " pts");
    }
}
