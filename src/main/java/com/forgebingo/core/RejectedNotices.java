package com.forgebingo.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "Proof sent back" lines for the top of the side panel (owner, 2026-11-21): one per tile whose newest proof a
 * reviewer rejected, whoever on the team sent it, e.g. "Rejected: Bandos hilt - Drop message not visible".
 *
 * The board only says that a tile's newest proof was rejected and why; it carries no time and no id for the
 * rejection. So this class remembers when it first saw each one (tile + reason) and whether the player dismissed it:
 * <ul>
 * <li>a line shows for {@link #WINDOW_MS} after it was first seen, newest first, unless dismissed;</li>
 * <li>once the tile stops being "rejected" (new proof sent, approved, tile done) it is forgotten, so a later
 * rejection of the same tile shows again, even with the same reason; a different reason is a new line at once.</li>
 * </ul>
 * A hidden secret tile only ever gives its shown name ({@link TileInfo#shownTitle}), never the reason.
 */
public final class RejectedNotices
{
    /** how long a rejection counts as recent, from when this plugin first saw it */
    public static final long WINDOW_MS = 48L * 60 * 60 * 1000;
    /** lines shown at once; the rest are summed up as "+N more" */
    public static final int MAX_SHOWN = 5;
    static final int MAX_REASON = 140;
    static final int MAX_REMEMBERED = 60;

    private RejectedNotices()
    {
    }

    public static final class Notice
    {
        /** tile + reason: stays the same from poll to poll, changes when the reviewer gives another reason */
        public final String key;
        public final String tileId;
        public final String title;
        /** the reviewer's reason; null for a hidden secret tile */
        public final String reason;
        long seenAt;

        Notice(String key, String tileId, String title, String reason)
        {
            this.key = key;
            this.tileId = tileId;
            this.title = title;
            this.reason = reason;
        }

        /** when this plugin first saw the rejection */
        public long seenAt()
        {
            return seenAt;
        }

        public String text()
        {
            return "Rejected: " + title + (reason == null || reason.isEmpty() ? "" : " - " + reason);
        }
    }

    /** Every tile on the board whose newest proof was sent back, in board order. */
    public static List<Notice> find(List<ApiModels.Tile> tiles)
    {
        List<Notice> out = new ArrayList<>();
        if (tiles == null) return out;
        Set<String> seen = new HashSet<>();
        for (ApiModels.Tile t : tiles)
        {
            if (t == null || t.id == null || t.id.isEmpty()) continue;
            String reason = TileInfo.needsChangesReason(t);
            if (reason == null) continue;
            String key = key(t.id, reason);
            if (key == null || !seen.add(key)) continue;
            // a hidden secret tile: its shown name and nothing else
            boolean hidden = TileInfo.isSecret(t) && !t.completed;
            String title = TileInfo.shownTitle(t);
            out.add(new Notice(key, t.id, title.isEmpty() ? "A tile" : title, hidden ? null : shorten(reason.trim())));
        }
        return out;
    }

    static String key(String tileId, String reason)
    {
        String k = tileId + ":" + Integer.toHexString(reason.trim().hashCode());
        return validKey(k) ? k : null;
    }

    private static String shorten(String s)
    {
        return s.length() <= MAX_REASON ? s : s.substring(0, MAX_REASON - 3).trim() + "...";
    }

    private static boolean validKey(String k)
    {
        if (k == null || k.isEmpty() || k.length() > 80) return false;
        for (int i = 0; i < k.length(); i++)
        {
            char c = k.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == ':';
            if (!ok) return false;
        }
        return true;
    }

    /** "+2 more", or "" when everything fits. */
    public static String moreText(int total)
    {
        return total > MAX_SHOWN ? "+" + (total - MAX_SHOWN) + " more" : "";
    }

    /** What one board's rejections looked like so far: first seen and dismissed, kept in the player's settings. */
    public static final class Log
    {
        /** key -> {first seen (ms), dismissed (1/0)} */
        private final Map<String, long[]> seen = new LinkedHashMap<>();
        private List<Notice> current = new ArrayList<>();

        /**
         * Takes the board's current rejections: new ones are stamped with now, ones the board no longer reports are
         * forgotten. Returns true when anything to remember changed.
         */
        public boolean update(List<Notice> now, long nowMs)
        {
            boolean changed = false;
            Set<String> keys = new HashSet<>();
            List<Notice> list = now == null ? new ArrayList<>() : now;
            for (Notice n : list)
            {
                keys.add(n.key);
                long[] e = seen.get(n.key);
                if (e == null)
                {
                    if (seen.size() >= MAX_REMEMBERED) continue;
                    e = new long[] {nowMs, 0};
                    seen.put(n.key, e);
                    changed = true;
                }
                else if (e[0] > nowMs)
                {
                    // the computer's clock went back: never keep a line alive past its window because of it
                    e[0] = nowMs;
                    changed = true;
                }
                n.seenAt = e[0];
            }
            for (Iterator<String> it = seen.keySet().iterator(); it.hasNext(); )
            {
                if (!keys.contains(it.next()))
                {
                    it.remove();
                    changed = true;
                }
            }
            current = list;
            return changed;
        }

        /** Every line to show right now, newest first (board order between lines seen together); not capped. */
        public List<Notice> visible(long nowMs)
        {
            List<Notice> out = new ArrayList<>();
            for (Notice n : current)
            {
                long[] e = seen.get(n.key);
                if (e == null || e[1] != 0 || nowMs - e[0] >= WINDOW_MS) continue;
                out.add(n);
            }
            out.sort(Comparator.comparingLong((Notice n) -> -n.seenAt));
            return out;
        }

        /** The player closed a line: it stays away until the tile is rejected anew. */
        public boolean dismiss(String key)
        {
            long[] e = seen.get(key);
            if (e == null || e[1] != 0) return false;
            e[1] = 1;
            return true;
        }

        /** "key~firstSeen~0|1;..." for the settings file. */
        public String save()
        {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, long[]> e : seen.entrySet())
            {
                if (sb.length() > 0) sb.append(';');
                sb.append(e.getKey()).append('~').append(e.getValue()[0]).append('~').append(e.getValue()[1] != 0 ? 1 : 0);
            }
            return sb.toString();
        }

        /** Replaces what is remembered with a saved value; anything malformed is skipped. */
        public void load(String raw)
        {
            seen.clear();
            current = new ArrayList<>();
            if (raw == null || raw.isEmpty() || raw.length() > 8000) return;
            for (String part : raw.split(";"))
            {
                String[] f = part.split("~");
                if (f.length != 3 || !validKey(f[0]) || seen.size() >= MAX_REMEMBERED) continue;
                try
                {
                    long at = Long.parseLong(f[1]);
                    if (at < 0) continue;
                    seen.put(f[0], new long[] {at, "1".equals(f[2]) ? 1 : 0});
                }
                catch (NumberFormatException ignored)
                {
                    // skip
                }
            }
        }
    }
}
