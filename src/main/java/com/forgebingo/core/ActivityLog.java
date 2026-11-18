package com.forgebingo.core;

import java.util.ArrayList;
import java.util.List;

/**
 * The last few things that counted for the player's team, so they can see the plugin working without opening the
 * website (owner, 2026-11-14). Kept small on purpose: the side panel shows only the newest as one line, and the
 * rest in its tooltip. Nothing here is sent anywhere.
 */
public final class ActivityLog
{
    public static final int MAX = 6;

    private static final class Entry
    {
        final String id;
        final String what;
        final long at;
        boolean picture;

        Entry(String id, String what, long at)
        {
            this.id = id;
            this.what = what;
            this.at = at;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    /** Something counted. {@code id} is the game event's id, so its picture can be ticked off later. */
    public synchronized void counted(String id, String what, long nowMillis)
    {
        if (what == null || what.trim().isEmpty()) return;
        String text = what.trim();
        if (text.length() > 60) text = text.substring(0, 57) + "...";
        entries.add(0, new Entry(id, text, nowMillis));
        while (entries.size() > MAX) entries.remove(entries.size() - 1);
    }

    /** That event's proof picture reached the server. */
    public synchronized void pictureSent(String id)
    {
        if (id == null) return;
        for (Entry e : entries) if (id.equals(e.id)) e.picture = true;
    }

    public synchronized boolean isEmpty()
    {
        return entries.isEmpty();
    }

    public synchronized void clear()
    {
        entries.clear();
    }

    /** "2m ago", "just now", "1h ago". */
    static String ago(long thenMillis, long nowMillis)
    {
        long s = Math.max(0, (nowMillis - thenMillis) / 1000);
        if (s < 45) return "just now";
        if (s < 3600) return Math.max(1, Math.round(s / 60f)) + "m ago";
        if (s < 86_400) return Math.round(s / 3600f) + "h ago";
        return Math.round(s / 86_400f) + "d ago";
    }

    /** The newest entry as one short line, or null when nothing has counted yet. */
    public synchronized String newest(long nowMillis)
    {
        if (entries.isEmpty()) return null;
        Entry e = entries.get(0);
        return "Counted: " + e.what + " (" + ago(e.at, nowMillis) + ")";
    }

    /** Every entry, newest first, one per line: what, when, and whether its picture was sent. */
    public synchronized List<String> lines(long nowMillis)
    {
        List<String> out = new ArrayList<>();
        for (Entry e : entries) out.add(e.what + " - " + ago(e.at, nowMillis) + (e.picture ? ", picture sent" : ""));
        return out;
    }
}
