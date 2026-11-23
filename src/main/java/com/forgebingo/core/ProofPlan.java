package com.forgebingo.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where a drop's proof picture goes: one upload per team the drop counted for (a player can be on teams in several
 * bingos at once), attached to that team's entries, named after the first task and stamped with the tile names.
 */
public final class ProofPlan
{
    /** the server takes 1-25 entries per picture */
    public static final int MAX_TARGETS = 25;

    private ProofPlan()
    {
    }

    /** how often one proof picture is sent before giving up (the first send included) */
    public static final int UPLOAD_TRIES = 3;
    /** the longest wait before another try, whatever the server asks for */
    public static final long RETRY_MAX_MILLIS = 2 * 60_000L;

    /**
     * After a failed upload: how long to wait before sending the same picture again, or -1 to give up. Only a busy
     * (429), unreachable or failing (5xx) server is worth another try; an answer that will not change (the board is
     * gone, the bingo is not open, the entry was already reviewed, a bad key...) is not.
     *
     * @param status the failed upload's HTTP status (0 = it never completed)
     * @param retryAfterMillis the server's Retry-After, or 0
     * @param attempt which send just failed (1 = the first)
     * @param ageMillis how long ago the drop's screenshot was started
     * @param ttlMillis how long a screenshot is kept at most
     */
    public static long retryDelay(int status, String errorCode, long retryAfterMillis, int attempt, long ageMillis, long ttlMillis)
    {
        if (ErrorClass.of(status, errorCode) != ErrorClass.TRANSIENT || attempt >= UPLOAD_TRIES) return -1;
        long delay = Math.min(RETRY_MAX_MILLIS, Backoff.delayMillis(attempt, retryAfterMillis, null));
        return ageMillis + delay < ttlMillis ? delay : -1;
    }

    /** One team's upload. */
    public static final class Team
    {
        public final String teamId;
        /** the entries (matched[].submissionId) the picture is attached to, in order, no repeats, at most 25 */
        public final List<String> submissionIds = new ArrayList<>();
        /** the file is named after the (first) task this drop counted for; null when it has no name */
        public String label;
        /** tiles this drop counted for on this team (repeats possible), for the stamp */
        public final List<String> tileIds = new ArrayList<>();

        Team(String teamId)
        {
            this.teamId = teamId;
        }
    }

    /**
     * A raid's reward chest is also the proof of the raid itself: its pictures go to the chest's own matches and to
     * the tiles the raid's completion (the kill count, a moment earlier) counted for.
     *
     * @param chest the server's answer to the chest's loot
     * @param raidKill the answer to that raid's kill count, or null when there is none (or it is too old)
     * @return chest itself when there is nothing to add, else a new result holding both lists of matches
     */
    public static ApiModels.EventResult withRaidKill(ApiModels.EventResult chest, ApiModels.EventResult raidKill)
    {
        if (chest == null || raidKill == null || raidKill.matched == null) return chest;
        ApiModels.EventResult both = new ApiModels.EventResult();
        both.eventId = chest.eventId;
        both.matched = new ArrayList<>();
        if (chest.matched != null) both.matched.addAll(chest.matched);
        both.matched.addAll(raidKill.matched);
        return both;
    }

    /**
     * @param matched the server's matches for the event (and, for a raid chest, the raid's own kill-count matches)
     * @param taskNames task id -> description of the board on screen
     * @param tileTitles tile id -> shown title of the board on screen
     * @return the teams to upload to, in the order the server named them; teams with no entry to attach to are left out
     */
    public static List<Team> teams(List<ApiModels.Match> matched, Map<String, String> taskNames, Map<String, String> tileTitles)
    {
        Map<String, Team> byTeam = new LinkedHashMap<>();
        if (matched == null) return new ArrayList<>();
        for (ApiModels.Match m : matched)
        {
            if (m == null || m.teamId == null || m.submissionId == null) continue;
            Team t = byTeam.computeIfAbsent(m.teamId, Team::new);
            if (!t.submissionIds.contains(m.submissionId) && t.submissionIds.size() < MAX_TARGETS) t.submissionIds.add(m.submissionId);
            String name = MatchNotices.matchName(m, taskNames, tileTitles);
            if (name != null && t.label == null) t.label = name;
        }
        for (ApiModels.Match m : matched)
        {
            if (m == null || m.teamId == null || m.tileId == null) continue;
            Team t = byTeam.get(m.teamId);
            if (t != null) t.tileIds.add(m.tileId);
        }
        return new ArrayList<>(byTeam.values());
    }

    /**
     * The tile names stamped on a team's picture: from that team's own board, else from the board on screen;
     * "a, b +N" beyond two. Null when no tile has a name.
     */
    public static String tiles(List<String> tileIds, ApiModels.BoardResponse teamBoard, Map<String, String> tileTitles)
    {
        List<String> names = new ArrayList<>();
        for (String tileId : tileIds)
        {
            String title = null;
            if (teamBoard != null && teamBoard.tiles != null)
            {
                for (ApiModels.Tile x : teamBoard.tiles) if (x != null && tileId.equals(x.id)) { title = TileInfo.shownTitle(x); break; }
            }
            if (title == null) title = tileTitles.get(tileId);
            if (title != null && !title.isEmpty() && !names.contains(title)) names.add(title);
        }
        return MatchNotices.joinNames(names);
    }

    /** The tile line of one frame's stamp: the tile names, plus "1 of 2" when a drop has several frames. */
    public static String stampText(String tiles, int frame, int frames)
    {
        String part = frames > 1 ? frame + " of " + frames : null;
        return part == null ? tiles : tiles == null ? part : tiles + " (" + part + ")";
    }
}
