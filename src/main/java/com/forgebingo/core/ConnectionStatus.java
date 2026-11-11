package com.forgebingo.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns server answers into the words the sidebar (and chat) shows: connection state, the verified RSN, the
 * event/team, and what to do about a name mismatch or a revoked key. Written for players: say what is wrong,
 * what it means for their drops, and the one thing to do about it. No RuneLite dependencies (unit-tested).
 */
public final class ConnectionStatus
{
    /** Where players get a key (website wording). */
    public static final String WHERE_TO_GET_A_KEY = "the ForgeBingo website (your team board > Connect RuneLite)";

    public enum Level
    {
        OK,
        INFO,
        WARNING,
        ERROR
    }

    /** One message of the sidebar's status block. May contain "\n" line breaks. */
    public static final class Line
    {
        public final Level level;
        public final String text;

        public Line(Level level, String text)
        {
            this.level = level;
            this.text = text;
        }

        @Override
        public String toString()
        {
            return level + ": " + text;
        }
    }

    private ConnectionStatus()
    {
    }

    /** Message for a 401 (queue paused until the key changes). */
    public static String authMessage(String errorCode)
    {
        if ("api_key_revoked".equals(errorCode))
        {
            return "Your plugin key stopped working (it was revoked or replaced by a newer one). Get a new key from "
                + WHERE_TO_GET_A_KEY + " and paste it into the ForgeBingo plugin settings.";
        }
        return "Your plugin key wasn't recognised. Copy it again from " + WHERE_TO_GET_A_KEY
            + " and paste it into the ForgeBingo plugin settings.";
    }

    /** Messages for a hello response: every verified board in one card, then one card per problem. */
    public static List<Line> fromHello(ApiModels.HelloResponse r)
    {
        List<Line> out = new ArrayList<>();
        if (r == null || r.memberships == null || r.memberships.isEmpty())
        {
            out.add(new Line(Level.WARNING, "This key isn't on any bingo team yet. Join a team with your team link on the website, then press Refresh."));
            return out;
        }
        List<ApiModels.Membership> verified = new ArrayList<>();
        List<Line> problems = new ArrayList<>();
        for (ApiModels.Membership m : r.memberships)
        {
            if ("verified".equals(m.status)) verified.add(m);
            else problems.add(line(m));
        }
        if (!verified.isEmpty()) out.add(verifiedLine(verified));
        out.addAll(problems);
        return out;
    }

    /** "Tracking as Gus. Drops count for:" + one bullet per board. */
    static Line verifiedLine(List<ApiModels.Membership> ms)
    {
        StringBuilder s = new StringBuilder("Tracking drops as ").append(ms.get(0).pluginRsn).append(" for:");
        for (ApiModels.Membership m : ms)
        {
            s.append("\n- ").append(where(m));
        }
        return new Line(Level.OK, s.toString());
    }

    static Line line(ApiModels.Membership m)
    {
        String where = where(m);
        String status = m.status == null ? "" : m.status;
        switch (status)
        {
            case "verified":
                return new Line(Level.OK, "Tracking drops as " + m.pluginRsn + " for " + where + ".");
            case "rsn_mismatch":
                return new Line(Level.ERROR, "Name doesn't match on " + where + ": you're playing as " + m.pluginRsn
                    + ", but the team has you down as " + m.joinedRsn + ". Your drops still send, but a reviewer has to approve them. "
                    + "To fix it, change your name to " + m.pluginRsn + " on the team board, or log in as " + m.joinedRsn + ".");
            case "account_mismatch":
                return new Line(Level.ERROR, where + " is linked to a different OSRS account, so drops from this one won't count. "
                    + "Log in on the linked account, or ask your organizer to switch it.");
            case "account_taken":
                return new Line(Level.ERROR, "This OSRS account is already linked to another player in " + where
                    + ", so drops from it won't count for you. Ask your organizer to sort it out.");
            case "not_approved":
                return new Line(Level.WARNING, "Waiting for the organizer to let you into " + where + ". Drops will count once you're approved.");
            default:
                return new Line(Level.WARNING, where + ": " + status);
        }
    }

    /** "Goblin Squad (Plugin Beta Test)": the team first, since that's what players recognise. */
    static String where(ApiModels.Membership m)
    {
        if (m.teamName == null) return m.eventName == null ? "your event" : m.eventName;
        return m.teamName + (m.eventName == null ? "" : " (" + m.eventName + ")");
    }

    /** True when every membership is verified (drops count automatically where the event auto-approves). */
    public static boolean allVerified(ApiModels.HelloResponse r)
    {
        if (r == null || r.memberships == null || r.memberships.isEmpty())
        {
            return false;
        }
        for (ApiModels.Membership m : r.memberships)
        {
            if (!m.verified)
            {
                return false;
            }
        }
        return true;
    }

    /** What the pasted key can do, under the connection state. */
    public static String keyScope(ApiModels.Me me)
    {
        if (me == null)
        {
            return "";
        }
        return "event".equals(me.keyScope) ? "This key works for one event" : "This key works for all your events";
    }
}
