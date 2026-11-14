package com.forgebingo.core;

/** Comparing plugin version numbers like "2.1.0". */
public final class Versions
{
    private Versions()
    {
    }

    /** The numbers of a version, ignoring anything after them ("2.1.0-test" is 2, 1, 0). Empty when unreadable. */
    static int[] parts(String v)
    {
        if (v == null) return new int[0];
        String[] bits = v.trim().split("[.]");
        int[] out = new int[bits.length];
        for (int i = 0; i < bits.length; i++)
        {
            String digits = bits[i].replaceAll("^(\\d+).*$", "$1");
            if (!digits.matches("\\d{1,6}")) return new int[0];
            out[i] = Integer.parseInt(digits);
        }
        return out;
    }

    /** True when {@code latest} is a higher version than {@code mine}. Unreadable versions are never "newer". */
    public static boolean newer(String latest, String mine)
    {
        int[] a = parts(latest);
        int[] b = parts(mine);
        if (a.length == 0 || b.length == 0) return false;
        for (int i = 0; i < Math.max(a.length, b.length); i++)
        {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) return x > y;
        }
        return false;
    }
}
