package com.forgebingo.core;

import java.util.Random;

/** Exponential backoff with jitter that honours Retry-After. */
public final class Backoff
{
    public static final long BASE_MILLIS = 5_000L;
    public static final long MAX_MILLIS = 15 * 60_000L;

    private Backoff()
    {
    }

    /**
     * @param attempt number of failed attempts so far (1 = first failure)
     * @param retryAfterMillis server hint, or &lt;= 0 for none
     * @param random jitter source (null for no jitter)
     */
    public static long delayMillis(int attempt, long retryAfterMillis, Random random)
    {
        int exponent = Math.min(Math.max(0, attempt - 1), 10);
        long delay = Math.min(MAX_MILLIS, BASE_MILLIS * (1L << exponent));
        if (random != null)
        {
            delay = (long) (delay * (0.8 + 0.4 * random.nextDouble()));
        }
        if (retryAfterMillis > 0)
        {
            delay = Math.max(delay, Math.min(retryAfterMillis, MAX_MILLIS));
        }
        return delay;
    }

    /** Parses a Retry-After header holding delta-seconds; HTTP-dates are ignored. */
    public static long parseRetryAfterMillis(String header)
    {
        if (header == null)
        {
            return 0;
        }
        try
        {
            long seconds = Long.parseLong(header.trim());
            return seconds <= 0 ? 0 : Math.min(seconds * 1000L, MAX_MILLIS);
        }
        catch (NumberFormatException ex)
        {
            return 0;
        }
    }
}
