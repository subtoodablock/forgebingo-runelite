package com.forgebingo.core;

import java.util.ArrayList;
import java.util.List;

/** Size caps and defensive cleanup for outgoing events and incoming server data. */
public final class Sanitizer
{
    public static final int MAX_STRING = 200;
    public static final int MAX_ITEMS_PER_EVENT = 40;
    public static final int MAX_EVENT_JSON_BYTES = 16 * 1024;
    public static final int MAX_EVENTS = 100;
    public static final int MAX_TILES = 400;
    public static final int MAX_TASKS = 50;

    private Sanitizer()
    {
    }

    public static String str(String s)
    {
        if (s == null)
        {
            return null;
        }
        StringBuilder sb = new StringBuilder(Math.min(s.length(), MAX_STRING));
        for (int i = 0; i < s.length() && sb.length() < MAX_STRING; i++)
        {
            char c = s.charAt(i);
            sb.append(Character.isISOControl(c) ? ' ' : c);
        }
        return sb.toString();
    }

    private static final java.util.regex.Pattern RSN = java.util.regex.Pattern.compile("^[A-Za-z0-9 _-]{1,12}$");

    /** RuneScape names use non-breaking spaces in some client APIs; the server wants plain spaces. */
    public static String normalizeRsn(String rsn)
    {
        return rsn == null ? null : str(rsn.replace('\u00a0', ' ')).trim();
    }

    /** Same pattern as the server (^[A-Za-z0-9 _-]{1,12}$): events that fail it are never sent. */
    public static boolean validRsn(String rsn)
    {
        return rsn != null && RSN.matcher(rsn).matches();
    }

    /** Trims strings and item lists of an outgoing event in place. */
    public static void event(GameEvent e)
    {
        e.sourceName = str(e.sourceName);
        e.npcName = str(e.npcName);
        e.skill = str(e.skill);
        e.rsn = normalizeRsn(e.rsn);
        if (e.items != null)
        {
            if (e.items.size() > MAX_ITEMS_PER_EVENT)
            {
                e.items = new ArrayList<>(e.items.subList(0, MAX_ITEMS_PER_EVENT));
            }
            for (GameEvent.Item i : e.items)
            {
                if (i.quantity < 1)
                {
                    i.quantity = 1;
                }
            }
        }
    }

    private static <T> List<T> cap(List<T> in, int max)
    {
        if (in == null)
        {
            return new ArrayList<>();
        }
        return in.size() <= max ? in : new ArrayList<>(in.subList(0, max));
    }

    private static int clamp(int v)
    {
        return Math.max(0, Math.min(v, 1_000_000_000));
    }

    public static ApiModels.BoardsResponse boards(ApiModels.BoardsResponse r)
    {
        if (r == null)
        {
            return null;
        }
        r.boards = cap(r.boards, MAX_EVENTS);
        r.boards.removeIf(b -> b == null || b.teamBoardId == null || b.teamBoardId.length() > 64);
        for (ApiModels.BoardSummary b : r.boards)
        {
            b.eventName = str(b.eventName);
            b.boardTitle = str(b.boardTitle);
            b.teamName = str(b.teamName);
            b.role = str(b.role);
            b.status = str(b.status);
            b.boardSize = Math.max(1, Math.min(b.boardSize, 20));
            b.completedCount = clamp(b.completedCount);
            b.totalTiles = clamp(b.totalTiles);
        }
        return r;
    }

    public static ApiModels.BoardResponse board(ApiModels.BoardResponse b)
    {
        if (b == null)
        {
            return null;
        }
        if (b.board == null)
        {
            b.board = new ApiModels.BoardInfo();
            b.board.size = 1;
        }
        b.board.size = Math.max(1, Math.min(b.board.size, 20));
        b.board.title = str(b.board.title);
        b.teamName = str(b.teamName);
        if (b.event != null)
        {
            String kw = str(b.event.keyword);
            b.event.keyword = kw == null ? null : kw.trim().substring(0, Math.min(kw.trim().length(), 40));
        }
        b.tiles = cap(b.tiles, MAX_TILES);
        b.tiles.removeIf(t -> t == null);
        b.hiddenItemIds = cap(b.hiddenItemIds, 500);
        b.hiddenItemIds.removeIf(i -> i == null || i < 0);
        for (ApiModels.Tile t : b.tiles)
        {
            t.title = str(t.title);
            t.description = str(t.description);
            t.points = clamp(t.points);
            t.row = Math.max(0, Math.min(t.row, 19));
            t.col = Math.max(0, Math.min(t.col, 19));
            if (t.iconUrl != null && !IconRef.isProxyPath(t.iconUrl))
            {
                t.iconUrl = null;
            }
            t.tasks = cap(t.tasks, MAX_TASKS);
            t.tasks.removeIf(x -> x == null);
            for (ApiModels.Task k : t.tasks)
            {
                k.description = str(k.description);
                k.rejectedReason = k.rejectedReason == null ? null : str(k.rejectedReason);
                if (k.points != null) k.points = Math.max(0, Math.min(1000, k.points));
                k.requiredQty = clamp(k.requiredQty);
                k.approvedQty = clamp(k.approvedQty);
                k.pendingQty = clamp(k.pendingQty);
                // rule lists are only pre-filter data; the plugin does not need them in memory
                k.itemIds = null;
                k.npcIds = null;
            }
        }
        if (b.event != null)
        {
            b.event.name = str(b.event.name);
        }
        return b;
    }

    public static ApiModels.LeaderboardResponse leaderboard(ApiModels.LeaderboardResponse r)
    {
        if (r == null)
        {
            return null;
        }
        r.teams = cap(r.teams, 200);
        r.teams.removeIf(t -> t == null);
        for (ApiModels.LeaderboardTeam t : r.teams)
        {
            t.teamName = str(t.teamName);
            t.score = clamp(t.score);
        }
        return r;
    }

    public static ApiModels.EventResult result(ApiModels.EventResult r)
    {
        if (r == null)
        {
            return null;
        }
        r.matched = cap(r.matched, 100);
        r.matched.removeIf(m -> m == null);
        r.matchedTiles = cap(r.matchedTiles, 100);
        r.skipped = cap(r.skipped, 100);
        r.skipped.removeIf(x -> x == null);
        for (ApiModels.Skipped x : r.skipped)
        {
            x.reason = str(x.reason);
        }
        return r;
    }

    public static ApiModels.Me me(ApiModels.Me m)
    {
        if (m != null)
        {
            m.displayName = str(m.displayName);
            m.keyScope = str(m.keyScope);
        }
        return m;
    }

    public static ApiModels.HelloResponse hello(ApiModels.HelloResponse r)
    {
        if (r == null)
        {
            return null;
        }
        r.displayName = str(r.displayName);
        r.rsn = str(r.rsn);
        r.keyScope = str(r.keyScope);
        r.memberships = cap(r.memberships, 20);
        r.memberships.removeIf(x -> x == null);
        for (ApiModels.Membership m : r.memberships)
        {
            m.eventName = str(m.eventName);
            m.teamName = str(m.teamName);
            m.joinedRsn = str(m.joinedRsn);
            m.pluginRsn = str(m.pluginRsn);
            m.status = str(m.status);
            m.eventStatus = str(m.eventStatus);
        }
        return r;
    }

    public static final int MAX_CATALOGUE_ITEMS = 20_000;

    public static ApiModels.CatalogueResponse catalogue(ApiModels.CatalogueResponse r)
    {
        if (r == null)
        {
            return null;
        }
        r.items = cap(r.items, MAX_CATALOGUE_ITEMS);
        r.items.removeIf(i -> i == null || i.id < 0 || i.name == null || i.name.isEmpty());
        for (ApiModels.CatalogueItem i : r.items)
        {
            i.name = str(i.name);
        }
        return r;
    }
}
