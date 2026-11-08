package com.forgebingo.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Tile rules shared by the sidebar panel and the board overlay (owner request, 2026-11-07), matching the website:
 * hidden secret tiles, points per task, "needs changes" after a reviewer sends proof back, and "what's next".
 * Works with older servers too: fields they do not send fall back to what the board already had.
 */
public final class TileInfo
{
    public enum State { EMPTY, SECRET, DONE, NEEDS_CHANGES, WAITING, IN_PROGRESS, NOT_STARTED }

    private TileInfo()
    {
    }

    /** What a tile is called on screen: a hidden secret tile shows the name its organizer gave it, else "Secret tile". */
    public static String shownTitle(ApiModels.Tile t)
    {
        if (t == null) return "";
        if (isSecret(t) && !t.completed && (t.title == null || t.title.isEmpty() || "?".equals(t.title))) return "Secret tile";
        return t.title == null ? "" : t.title;
    }

    /** A hidden secret tile: the server sends "?" and no tasks (and, newer servers, secret=true). */
    public static boolean isSecret(ApiModels.Tile t)
    {
        if (t == null) return false;
        if (t.secret != null) return t.secret;
        return "?".equals(t.title) && (t.tasks == null || t.tasks.isEmpty());
    }

    /** Points the tile is worth: the sum of its task points (servers before task points: the tile's own points). */
    public static int worth(ApiModels.Tile t)
    {
        if (t == null) return 0;
        int sum = 0;
        boolean any = false;
        if (t.tasks != null)
        {
            for (ApiModels.Task k : t.tasks)
            {
                if (k.points != null)
                {
                    sum += Math.max(0, k.points);
                    any = true;
                }
            }
        }
        if (!any) return t.points;
        // "any one task finishes it": the best task counts
        if ("any".equals(t.completionMode))
        {
            int best = 0;
            for (ApiModels.Task k : t.tasks) if (k.points != null) best = Math.max(best, k.points);
            return best;
        }
        return sum;
    }

    /** The newest proof on one of its unfinished tasks was sent back. Returns the reason, or null. */
    public static String needsChangesReason(ApiModels.Tile t)
    {
        if (t == null || t.completed || t.tasks == null) return null;
        for (ApiModels.Task k : t.tasks)
        {
            if (!k.completed && k.pendingQty == 0 && k.rejectedReason != null && !k.rejectedReason.isEmpty()) return k.rejectedReason;
        }
        return null;
    }

    public static boolean waiting(ApiModels.Tile t)
    {
        if (t == null || t.tasks == null) return false;
        for (ApiModels.Task k : t.tasks) if (k.pendingQty > 0) return true;
        return false;
    }

    /** How far along the tile is, 0..1 (approved quantities; "any" tiles take their best task). */
    public static double progress(ApiModels.Tile t)
    {
        if (t == null) return 0;
        if (t.completed) return 1;
        if (t.tasks == null || t.tasks.isEmpty()) return 0;
        double total = 0;
        double best = 0;
        for (ApiModels.Task k : t.tasks)
        {
            double p = k.completed ? 1 : k.requiredQty <= 0 ? 0 : Math.min(1, (double) k.approvedQty / k.requiredQty);
            total += p;
            best = Math.max(best, p);
        }
        return "any".equals(t.completionMode) ? best : total / t.tasks.size();
    }

    public static State state(ApiModels.Tile t)
    {
        if (t == null) return State.EMPTY;
        if (isSecret(t)) return t.completed ? State.DONE : State.SECRET;
        if (t.completed) return State.DONE;
        if (needsChangesReason(t) != null) return State.NEEDS_CHANGES;
        if (waiting(t)) return State.WAITING;
        return progress(t) > 0 ? State.IN_PROGRESS : State.NOT_STARTED;
    }

    /** The open tiles closest to done (secret and empty tiles left out), most progress first, then most points. */
    public static List<ApiModels.Tile> nextUp(List<ApiModels.Tile> tiles, int n)
    {
        List<ApiModels.Tile> open = new ArrayList<>();
        if (tiles == null) return open;
        for (ApiModels.Tile t : tiles)
        {
            if (t == null || t.completed || isSecret(t) || t.tasks == null || t.tasks.isEmpty()) continue;
            open.add(t);
        }
        open.sort(Comparator.comparingDouble((ApiModels.Tile t) -> -progress(t)).thenComparingInt(t -> -worth(t)));
        return open.subList(0, Math.min(n, open.size()));
    }

    /** "3/5" style progress for the tile's least-finished task, or "" when there is nothing to count. */
    public static String countText(ApiModels.Tile t)
    {
        if (t == null || t.tasks == null) return "";
        for (ApiModels.Task k : t.tasks)
        {
            if (!k.completed && k.requiredQty > 1) return Math.min(k.approvedQty, k.requiredQty) + "/" + k.requiredQty;
        }
        int done = 0;
        for (ApiModels.Task k : t.tasks) if (k.completed) done++;
        return t.tasks.size() > 1 ? done + "/" + t.tasks.size() + " tasks" : "";
    }
}
