package com.forgebingo.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What to tell the player when the server says an event counted: "Counted", "Task complete", "Tile complete" or
 * "Extra drop" per match, the one-line summary for the side panel, and which tiles to celebrate. Decides the words
 * only; the plugin shows them.
 */
public final class MatchNotices
{
    private MatchNotices()
    {
    }

    /** One line for the player. */
    public static final class Notice
    {
        public final String text;
        /** set when a task was just finished: the words for the on-screen task banner, else null */
        public final String taskEffect;

        Notice(String text, String taskEffect)
        {
            this.text = text;
            this.taskEffect = taskEffect;
        }
    }

    /** A tile this event finished (it was not finished before): banner and fireworks. */
    public static final class Celebration
    {
        /** the team board the tile is on, when the server said; null = the board on screen */
        public final String teamBoardId;
        public final String tileId;
        /** the tile's shown title, or null when the board on screen does not have it */
        public final String title;

        Celebration(String teamBoardId, String tileId, String title)
        {
            this.teamBoardId = teamBoardId;
            this.tileId = tileId;
            this.title = title;
        }
    }

    public static final class Plan
    {
        /** what counted, for the side panel's one activity line ("a, b +2"); null when nothing has a name */
        public String counted;
        public final List<Notice> notices = new ArrayList<>();
        public final List<Celebration> celebrations = new ArrayList<>();
    }

    /** "a", "a, b" or "a, b +N": at most two names, then how many more. Null for no names. */
    public static String joinNames(Collection<String> names)
    {
        if (names == null || names.isEmpty()) return null;
        List<String> n = names instanceof List ? (List<String>) names : new ArrayList<>(names);
        return n.size() <= 2 ? String.join(", ", n) : n.get(0) + ", " + n.get(1) + " +" + (n.size() - 2);
    }

    /** task id -> its description, for every task on the board (empty for no board). */
    public static Map<String, String> taskNames(ApiModels.BoardResponse board)
    {
        Map<String, String> names = new HashMap<>();
        if (board == null || board.tiles == null) return names;
        for (ApiModels.Tile t : board.tiles)
        {
            if (t == null || t.tasks == null) continue;
            for (ApiModels.Task k : t.tasks) if (k != null && k.id != null) names.put(k.id, k.description);
        }
        return names;
    }

    /** What a match is called: its task's description, else its tile's title, else null. */
    static String matchName(ApiModels.Match m, Map<String, String> taskNames, Map<String, String> tileTitles)
    {
        String n = m.taskId == null ? null : taskNames.get(m.taskId);
        if (n == null || n.isEmpty()) n = m.tileId == null ? null : tileTitles.get(m.tileId);
        return n == null || n.isEmpty() ? null : n;
    }

    /**
     * @param result a matched, non-duplicate answer to an event
     * @param before the board on screen as it was before this event (null when none is loaded)
     * @param tileTitles tile id -> shown title of the board on screen
     * @param hiddenTiles ids of hidden secret tiles on that board: their amounts never show
     */
    public static Plan plan(ApiModels.EventResult result, ApiModels.BoardResponse before, Map<String, String> tileTitles, Set<String> hiddenTiles)
    {
        Plan plan = new Plan();
        if (result == null || result.matched == null) return plan;
        // what was already finished before this drop (no second celebration), and task names for the messages
        Set<String> wasDone = new HashSet<>();
        Set<String> taskWasDone = new HashSet<>();
        Map<String, String> taskNames = taskNames(before);
        if (before != null && before.tiles != null)
        {
            for (ApiModels.Tile t : before.tiles)
            {
                if (t == null) continue;
                if (t.completed && t.id != null) wasDone.add(t.id);
                if (t.tasks != null) for (ApiModels.Task k : t.tasks) if (k != null && k.id != null && k.completed) taskWasDone.add(k.id);
            }
        }
        // one quiet line in the side panel: what just counted
        Set<String> counted = new LinkedHashSet<>();
        for (ApiModels.Match m : result.matched)
        {
            if (m == null) continue;
            String n = matchName(m, taskNames, tileTitles);
            if (n != null) counted.add(n);
        }
        plan.counted = joinNames(counted);
        // a match's "completed" is its TASK; the tile is finished when matchedTiles says so
        Map<String, String> tileNowDone = new LinkedHashMap<>();
        if (result.matchedTiles != null)
        {
            for (ApiModels.MatchedTile mt : result.matchedTiles)
            {
                if (mt == null || !mt.completed || mt.tileId == null) continue;
                String team = tileNowDone.get(mt.tileId);
                if (team == null || team.isEmpty()) tileNowDone.put(mt.tileId, mt.teamBoardId == null ? "" : mt.teamBoardId);
            }
        }
        Set<String> announced = new HashSet<>();
        for (ApiModels.Match m : result.matched)
        {
            if (m == null) continue;
            String title = m.tileId == null ? null : tileTitles.get(m.tileId);
            // the server leaves out which hidden secret tile it was (review, 2026-11-22)
            String tileName = title != null ? title : m.tileId == null ? "a secret tile" : "a tile";
            String state = "approved".equals(m.status) ? ""
                : Boolean.FALSE.equals(m.verified) ? " - waiting for review (your name isn't verified yet)" : " - waiting for review";
            if (m.tileId != null && wasDone.contains(m.tileId))
            {
                plan.notices.add(new Notice("Extra drop logged for " + tileName + " (already done)" + state, null));
                continue;
            }
            // a hidden secret tile's amount would hint at its task: only its name shows. A match without a tile id is
            // one the server stripped for that reason (it leaves the amounts out too; this does not rely on it).
            boolean hiddenTile = m.tileId == null || (hiddenTiles != null && hiddenTiles.contains(m.tileId));
            String progress = !hiddenTile && m.requiredQuantity > 0 ? " (" + Math.min(m.currentQuantity, m.requiredQuantity) + "/" + m.requiredQuantity + ")" : "";
            if (m.tileId != null && tileNowDone.containsKey(m.tileId))
            {
                if (announced.add("tile:" + m.tileId)) plan.notices.add(new Notice("Tile complete: " + tileName + state, null));
            }
            else if (m.completed && m.taskId != null && !taskWasDone.contains(m.taskId))
            {
                String task = taskNames.get(m.taskId);
                boolean unnamed = task == null || task.isEmpty();
                String what = unnamed ? tileName : task + " (" + tileName + ")";
                if (announced.add("task:" + m.taskId)) plan.notices.add(new Notice("Task complete: " + what + state, unnamed ? tileName : task));
            }
            else
            {
                plan.notices.add(new Notice("Counted: " + tileName + progress + state, null));
            }
        }
        for (Map.Entry<String, String> done : tileNowDone.entrySet())
        {
            String tileId = done.getKey();
            if (wasDone.contains(tileId)) continue;
            // the team board: from matchedTiles, else from a match for that tile, else unknown (the board on screen)
            String team = done.getValue();
            if (team.isEmpty())
            {
                for (ApiModels.Match m : result.matched)
                {
                    if (m != null && tileId.equals(m.tileId) && m.teamId != null && !m.teamId.isEmpty()) { team = m.teamId; break; }
                }
            }
            plan.celebrations.add(new Celebration(team.isEmpty() ? null : team, tileId, tileTitles.get(tileId)));
        }
        return plan;
    }
}
