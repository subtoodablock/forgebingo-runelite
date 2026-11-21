package com.forgebingo.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * The tile's tasks as the website counts them: tasks of an "all" tile that share a group number are the "OR"
     * options of one task; every other task stands alone. Order follows the server's.
     */
    public static List<List<ApiModels.Task>> taskGroups(ApiModels.Tile t)
    {
        List<List<ApiModels.Task>> out = new ArrayList<>();
        if (t == null || t.tasks == null) return out;
        Map<Integer, List<ApiModels.Task>> byGroup = new HashMap<>();
        boolean all = !"any".equals(t.completionMode);
        for (ApiModels.Task k : t.tasks)
        {
            if (all && k.group != null)
            {
                List<ApiModels.Task> g = byGroup.get(k.group);
                if (g != null)
                {
                    g.add(k);
                    continue;
                }
                g = new ArrayList<>();
                g.add(k);
                byGroup.put(k.group, g);
                out.add(g);
            }
            else
            {
                List<ApiModels.Task> g = new ArrayList<>();
                g.add(k);
                out.add(g);
            }
        }
        return out;
    }

    private static int pts(ApiModels.Task k)
    {
        return k.points == null ? 0 : Math.max(0, k.points);
    }

    private static boolean hasTaskPoints(ApiModels.Tile t)
    {
        if (t == null || t.tasks == null) return false;
        for (ApiModels.Task k : t.tasks) if (k.points != null) return true;
        return false;
    }

    /**
     * The most the tile can score, matching the server: every task adds its points, a task with "OR" options adds
     * its best option once, an older "any one" tile its best task (servers before task points: the tile's own points).
     */
    public static int worth(ApiModels.Tile t)
    {
        if (t == null) return 0;
        if (!hasTaskPoints(t)) return t.points;
        int sum = 0;
        int best = 0;
        for (List<ApiModels.Task> g : taskGroups(t))
        {
            int top = 0;
            for (ApiModels.Task k : g) top = Math.max(top, pts(k));
            sum += top;
            best = Math.max(best, top);
        }
        return "any".equals(t.completionMode) ? best : sum;
    }

    /**
     * Points the team has scored on the tile so far, matching the server: each finished task scores as soon as it is
     * done (the tile need not be complete); a task with "OR" options scores only its finished option (the best one if
     * more than one is done).
     */
    public static int earned(ApiModels.Tile t)
    {
        if (t == null) return 0;
        if (!hasTaskPoints(t)) return t.completed ? t.points : 0;
        int sum = 0;
        for (List<ApiModels.Task> g : taskGroups(t))
        {
            int top = 0;
            for (ApiModels.Task k : g) if (k.completed) top = Math.max(top, pts(k));
            sum += top;
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
        // a task with "OR" options is as far along as its best option
        double total = 0;
        double best = 0;
        List<List<ApiModels.Task>> groups = taskGroups(t);
        for (List<ApiModels.Task> g : groups)
        {
            double p = 0;
            for (ApiModels.Task k : g) p = Math.max(p, k.completed ? 1 : k.requiredQty <= 0 ? 0 : Math.min(1, (double) k.approvedQty / k.requiredQty));
            total += p;
            best = Math.max(best, p);
        }
        return "any".equals(t.completionMode) ? best : total / groups.size();
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
        // tasks, not options: a task with "OR" options counts once, done when any option is
        List<List<ApiModels.Task>> groups = taskGroups(t);
        int done = 0;
        for (List<ApiModels.Task> g : groups)
        {
            for (ApiModels.Task k : g)
            {
                if (k.completed)
                {
                    done++;
                    break;
                }
            }
        }
        return groups.size() > 1 ? done + "/" + groups.size() + " tasks" : "";
    }
}
