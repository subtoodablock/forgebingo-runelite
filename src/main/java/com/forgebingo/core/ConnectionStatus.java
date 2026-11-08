package com.forgebingo.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns server answers into the words the sidebar shows: connection state, the verified RSN, the event/team, and
 * what to do about a name mismatch or a revoked key. No RuneLite dependencies (unit-tested).
 */
public final class ConnectionStatus
{
    /** Where players get a key (website wording). */
    public static final String WHERE_TO_GET_A_KEY = "your team board on the ForgeBingo website > Connect RuneLite";

    public enum Level
    {
        OK,
        WARNING,
        ERROR
    }

    /** One line of the sidebar's status block. */
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
            return "Your key was revoked or replaced by a newer one. Make a new key on " + WHERE_TO_GET_A_KEY
                + " and paste it in the ForgeBingo plugin settings.";
        }
        return "Key not recognised. Copy it again from " + WHERE_TO_GET_A_KEY + " and paste it in the ForgeBingo plugin settings.";
    }

    /** Lines for a hello response: one per event the key acts in. */
    public static List<Line> fromHello(ApiModels.HelloResponse r)
    {
        List<Line> out = new ArrayList<>();
        if (r == null || r.memberships == null || r.memberships.isEmpty())
        {
            out.add(new Line(Level.WARNING, "This key is not on any running bingo team. Join through your team link first."));
            return out;
        }
        for (ApiModels.Membership m : r.memberships)
        {
            out.add(line(m));
        }
        return out;
    }

    static Line line(ApiModels.Membership m)
    {
        String where = (m.eventName == null ? "the event" : m.eventName) + (m.teamName == null ? "" : " / " + m.teamName);
        String status = m.status == null ? "" : m.status;
        switch (status)
        {
            case "verified":
                return new Line(Level.OK, "Verified as " + m.pluginRsn + " - " + where);
            case "rsn_mismatch":
                return new Line(Level.ERROR, "RuneLite says you're logged in as " + m.pluginRsn + ", but you joined " + where
                    + " as " + m.joinedRsn + ". Fix your name on the team board (or log in on that account). Until then your drops wait for review.");
            case "account_mismatch":
                return new Line(Level.ERROR, where + " is linked to a different OSRS account. Log in on that account, or ask the organizer.");
            case "account_taken":
                return new Line(Level.ERROR, "This OSRS account is already linked to another player in " + where + ".");
            case "not_approved":
                return new Line(Level.WARNING, "Waiting for the organizer to approve you in " + where + ".");
            default:
                return new Line(Level.WARNING, where + ": " + status);
        }
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

    /** "Player key for X" / "Organizer key" line under the connection state. */
    public static String keyScope(ApiModels.Me me)
    {
        if (me == null)
        {
            return "";
        }
        return "event".equals(me.keyScope) ? "Player key (one event)" : "Account key (all your events)";
    }
}
