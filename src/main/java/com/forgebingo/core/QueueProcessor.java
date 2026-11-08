package com.forgebingo.core;

import java.util.List;
import java.util.Random;
import java.util.function.LongSupplier;
import java.util.function.Function;

/**
 * Drains the {@link OfflineQueue}. Not thread-safe by itself: call {@link #drain()}
 * from one executor thread at a time (the plugin synchronizes on the instance).
 */
public final class QueueProcessor
{
    /** Receives everything the user should know about. */
    public interface Listener
    {
        /** Server accepted the event (also fires for duplicates). */
        void onAccepted(GameEvent event, ApiModels.EventResult result);

        /** Event permanently dropped; show a notice. */
        void onDropped(GameEvent event, String reason);

        /** Key rejected; sending is paused until the key changes or user retries. */
        void onAuthProblem(String message);

        /** Queue state changed (size). */
        void onQueueChanged(int size, String lastError);
    }

    private final OfflineQueue queue;
    private final Function<GameEvent, ApiResult<ApiModels.EventResult>> sender;
    private final Listener listener;
    private final LongSupplier clock;
    private final Random random;
    private volatile boolean authPaused;

    public QueueProcessor(OfflineQueue queue, Function<GameEvent, ApiResult<ApiModels.EventResult>> sender,
        Listener listener, LongSupplier clock, Random random)
    {
        this.queue = queue;
        this.sender = sender;
        this.listener = listener;
        this.clock = clock;
        this.random = random;
    }

    public void resumeAfterAuthChange()
    {
        authPaused = false;
        queue.retryAllNow(clock.getAsLong());
    }

    public boolean isAuthPaused()
    {
        return authPaused;
    }

    /** @return millis until the next entry becomes due, or -1 when the queue is empty. */
    public synchronized long drain()
    {
        long now = clock.getAsLong();
        for (QueuedEvent expired : queue.expire(now))
        {
            listener.onDropped(expired.event, "too old to be accepted (over 23 hours offline)");
        }
        if (authPaused)
        {
            return queue.size() == 0 ? -1 : 60_000L;
        }
        String lastError = null;
        for (QueuedEvent q : queue.due(now))
        {
            ApiResult<ApiModels.EventResult> r = sender.apply(q.event);
            ErrorClass c = r.errorClass();
            if (c == ErrorClass.SUCCESS)
            {
                queue.remove(q.event.id);
                listener.onAccepted(q.event, r.body);
            }
            else if (c == ErrorClass.PERMANENT)
            {
                queue.remove(q.event.id);
                listener.onDropped(q.event, r.describe());
            }
            else if (c == ErrorClass.CONFLICT)
            {
                // event_id_conflict: the id belongs to someone else (never happens with UUIDv4). New id, retry soon.
                queue.remove(q.event.id);
                q.event.id = java.util.UUID.randomUUID().toString();
                q.nextAttemptAtMillis = clock.getAsLong();
                queue.add(q);
            }
            else if (c == ErrorClass.AUTH)
            {
                authPaused = true;
                lastError = r.describe();
                listener.onAuthProblem(ConnectionStatus.authMessage(r.errorCode));
                break;
            }
            else
            {
                int attempts = q.attempts + 1;
                long delay = Backoff.delayMillis(attempts, r.retryAfterMillis, random);
                queue.scheduleRetry(q.event.id, attempts, clock.getAsLong() + delay, r.describe());
                lastError = r.describe();
                if (r.status == 429 || r.status == 0)
                {
                    break; // do not hammer: the rest would likely fail the same way
                }
            }
        }
        listener.onQueueChanged(queue.size(), lastError);
        return nextDelay();
    }

    private long nextDelay()
    {
        long now = clock.getAsLong();
        long min = Long.MAX_VALUE;
        List<QueuedEvent> all = queue.due(Long.MAX_VALUE);
        for (QueuedEvent q : all)
        {
            min = Math.min(min, q.nextAttemptAtMillis);
        }
        return all.isEmpty() ? -1 : Math.max(1_000L, min - now);
    }
}
