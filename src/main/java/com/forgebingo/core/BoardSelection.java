package com.forgebingo.core;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Which of the player's boards is shown, from the list GET /v1/boards returns: the one they picked, else the first
 * live one, else the first. Also how many are live and whether one has just opened (the shared reveal).
 */
public final class BoardSelection
{
    private BoardSelection()
    {
    }

    /** The team board ids in the list. */
    public static Set<String> ids(List<ApiModels.BoardSummary> boards)
    {
        Set<String> ids = new HashSet<>();
        if (boards != null) for (ApiModels.BoardSummary b : boards) if (b != null) ids.add(b.teamBoardId);
        return ids;
    }

    /** How many of the boards are live right now (a drop can count for one that is not selected). */
    public static int liveCount(List<ApiModels.BoardSummary> boards)
    {
        int live = 0;
        if (boards != null) for (ApiModels.BoardSummary b : boards) if (b != null && b.active) live++;
        return live;
    }

    /**
     * A live board that was not in the list before: its bingo has just started. Null on the first list of a session
     * (knownBefore is null then: nothing can be new yet) and when nothing opened. The first one in list order wins.
     */
    public static ApiModels.BoardSummary newlyOpened(List<ApiModels.BoardSummary> boards, Set<String> knownBefore)
    {
        if (boards == null || knownBefore == null) return null;
        for (ApiModels.BoardSummary b : boards)
        {
            if (b == null || knownBefore.contains(b.teamBoardId) || !b.active) continue;
            return b;
        }
        return null;
    }

    /** The board to show: the wanted one when it is in the list, else the first live one, else the first, else null. */
    public static ApiModels.BoardSummary select(List<ApiModels.BoardSummary> boards, String wanted)
    {
        if (boards == null) return null;
        ApiModels.BoardSummary selected = null;
        for (ApiModels.BoardSummary b : boards)
        {
            if (b != null && b.teamBoardId != null && b.teamBoardId.equals(wanted)) selected = b;
        }
        if (selected == null)
        {
            for (ApiModels.BoardSummary b : boards)
            {
                if (b != null && b.active) { selected = b; break; }
            }
            if (selected == null && !boards.isEmpty()) selected = boards.get(0);
        }
        return selected;
    }
}
