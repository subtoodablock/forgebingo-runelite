package com.forgebingo.core;

/** A {@link GameEvent} waiting to be delivered, plus its retry state. */
public final class QueuedEvent
{
    public GameEvent event;
    public int attempts;
    public long createdAtMillis;
    public long nextAttemptAtMillis;
    public String lastError;

    public QueuedEvent()
    {
    }

    public QueuedEvent(GameEvent event, long now)
    {
        this.event = event;
        this.createdAtMillis = now;
        this.nextAttemptAtMillis = now;
    }
}
