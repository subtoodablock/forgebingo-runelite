package com.forgebingo.core;

import java.util.List;

/** JSON shapes exchanged with the plugin-api. Field names mirror docs/API.md section 8 exactly. */
public final class ApiModels
{
    private ApiModels()
    {
    }

    public static final class Me
    {
        public String userId;
        public String displayName;
        /** event = a player's key for one event (made on the team board); account = an organizer's key. */
        public String keyScope;
        public String eventId;
    }

    /** {@code POST /v1/hello} body: who is logged in. Sent on login and when the key changes. */
    public static final class HelloRequest
    {
        public String rsn;
        public String accountHash;

        public HelloRequest(String rsn, String accountHash)
        {
            this.rsn = rsn;
            this.accountHash = accountHash;
        }
    }

    /** One membership in a hello response. */
    /** {@code POST /v1/wom/update}: how many bingos queued a Wise Old Man refresh for this player. */
    public static final class WomUpdateResponse
    {
        public int queued;
        /** false when none of the player's bingos tracks Wise Old Man */
        public boolean womEnabled;
    }

    public static final class Membership
    {
        public String eventId;
        public String eventName;
        public String eventStatus;
        public String teamId;
        public String teamName;
        /** The name the player joined with on the website. */
        public String joinedRsn;
        /** The name this plugin reported. */
        public String pluginRsn;
        /** verified | rsn_mismatch | account_mismatch | account_taken | not_approved */
        public String status;
        public boolean verified;
        /** when the bingo starts (ISO), or null */
        public String startsAt;
        /** false while the board is still hidden from this player (shared reveal); null from older servers */
        public Boolean boardOpen;
    }

    public static final class HelloResponse
    {
        public String displayName;
        public String rsn;
        /** event | account */
        public String keyScope;
        public List<Membership> memberships;
    }

    /** One entry of {@code GET /v1/boards}. {@code teamBoardId} equals the team id. */
    public static final class BoardSummary
    {
        public String teamBoardId;
        public String teamId;
        public String eventId;
        public String eventName;
        public String boardTitle;
        public int boardSize;
        public String teamName;
        public String role;
        public String status;
        public String startsAt;
        public String endsAt;
        public boolean active;
        public int completedCount;
        public int totalTiles;
        public String updatedAt;

        @Override
        public String toString()
        {
            String e = eventName == null ? "(unnamed)" : eventName;
            return teamName == null ? e : e + " - " + teamName;
        }
    }

    public static final class BoardsResponse
    {
        public List<BoardSummary> boards;
    }

    public static final class Task
    {
        public String id;
        public String description;
        public int requiredQty;
        public String sourceType;
        public List<Integer> itemIds;
        public List<Integer> npcIds;
        public String sourceKey;
        public String skill;
        public int approvedQty;
        public int pendingQty;
        public boolean completed;
        /** points this task is worth (null from servers before per-task points) */
        public Integer points;
        /** the reviewer's reason when the newest proof was sent back, else null */
        public String rejectedReason;
        /** "one of each": every item (or item group) is its own sub-task (servers from 2026-11-11; null before) */
        public Boolean eachItem;
        /** groups of item ids where any one counts for the same sub-task (like the website's "Any one: ...") */
        public List<List<Integer>> itemGroups;
        /** per sub-task progress, keyed like the server's item_slot: "i&lt;itemId&gt;" or "g&lt;groupIndex&gt;" */
        public java.util.Map<String, SlotProgress> itemProgress;
    }

    public static final class SlotProgress
    {
        public int approved;
        public int pending;
    }

    /** A teammate going for a tile (the "Working on it" button). */
    public static final class WorkingOn
    {
        public String name;
        /** true for the player whose key fetched the board */
        public boolean me;
    }

    public static final class Tile
    {
        public String id;
        public int row;
        public int col;
        public int position;
        public String title;
        public String description;
        public String color;
        public int points;
        public Integer iconItemId;
        public boolean hasIcon;
        /** Relative to the API base URL: /v1/icons/tiles/&lt;id&gt;, or null. */
        public String iconUrl;
        /** The website's emoji icon (servers from 2026-11-11), else null; mapped to an item by TileIcons. */
        public String iconEmoji;
        /** teammates who marked this tile "working on" (null from servers before 2026-11-11: use the local list) */
        public List<WorkingOn> workingOn;
        public String completionMode;
        /** a hidden secret tile (null from older servers: see TileInfo.isSecret) */
        public Boolean secret;
        public boolean completed;
        public String completedAt;
        public List<Task> tasks;
    }

    public static final class BoardEvent
    {
        public String id;
        public String name;
        public String status;
        public String startsAt;
        public String endsAt;
        public boolean active;
        public String keyword;
    }

    public static final class Bonuses
    {
        public int row;
        public int col;
        public int diagonal;
        public int blackout;
    }

    public static final class BoardInfo
    {
        public String id;
        public String title;
        public int size;
        public Bonuses bonuses;
    }

    /** {@code GET /v1/boards/{teamBoardId}}. */
    public static final class BoardResponse
    {
        public String teamBoardId;
        public String teamId;
        public String teamName;
        public String teamColor;
        public BoardEvent event;
        public BoardInfo board;
        public List<Tile> tiles;
        /** item ids that tasks on hidden secret tiles ask for (no names): pickpocket loot for them still counts */
        public List<Integer> hiddenItemIds;
        public String updatedAt;

        public int size()
        {
            return board == null ? 1 : board.size;
        }
    }

    public static final class LeaderboardTeam
    {
        public String teamId;
        public String teamName;
        public String color;
        public int score;
        public int tilesCompleted;
        public int rank;
        public String reachedAt;
        public boolean isMyTeam;
    }

    public static final class LeaderboardResponse
    {
        public List<LeaderboardTeam> teams;
    }

    /** One entry of {@code matched[]} in a game-event response. */
    public static final class Match
    {
        public String eventId;
        public String teamId;
        public String tileId;
        public String taskId;
        public String submissionId;
        /** pending | approved */
        public String status;
        /** False when the player's RSN is not verified: the match waits for review ("From RuneLite (unverified)"). */
        public Boolean verified;
        public int contributedQuantity;
        public int approvedQuantity;
        public int currentQuantity;
        public int requiredQuantity;
        public boolean completed;
    }

    public static final class MatchedTile
    {
        public String teamBoardId;
        public String tileId;
        public boolean completed;
        public List<Match> tasks;
    }

    public static final class Skipped
    {
        public String teamId;
        public String eventId;
        public String reason;
    }

    public static final class EventResult
    {
        public String eventId;
        public boolean duplicate;
        public boolean automationDisabled;
        public String sourceType;
        public String sourceKey;
        public boolean unknownSource;
        public List<Match> matched;
        public List<MatchedTile> matchedTiles;
        public List<Skipped> skipped;

        public boolean isMatched()
        {
            return matched != null && !matched.isEmpty();
        }
    }

    public static final class ProofResponse
    {
        public boolean success;
        public Proof proof;
    }

    public static final class Proof
    {
        public String storagePath;
        public List<String> submissionIds;
    }

    /** Error body: {"error": "<code>", "message": "<human text>"}. */
    public static final class ErrorBody
    {
        public String error;
        public String message;
    }

    /** One entry of {@code GET /v1/catalogue/items}. */
    public static final class CatalogueItem
    {
        public int id;
        public String name;

        public CatalogueItem()
        {
        }

        public CatalogueItem(int id, String name)
        {
            this.id = id;
            this.name = name;
        }
    }

    public static final class CatalogueResponse
    {
        public List<CatalogueItem> items;
    }
}
