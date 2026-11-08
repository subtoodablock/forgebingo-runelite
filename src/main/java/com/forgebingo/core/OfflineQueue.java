package com.forgebingo.core;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Durable FIFO of events not yet accepted by the server. Persisted as JSON
 * (atomic write via temp file + move) so a crash or client close loses nothing.
 * Bounded by count and age.
 */
public final class OfflineQueue
{
    private static final Logger log = LoggerFactory.getLogger(OfflineQueue.class);
    private static final Type LIST_TYPE = new TypeToken<List<QueuedEvent>>()
    {
    }.getType();

    public static final int MAX_ENTRIES = 500;
    /** The server rejects events older than one day, so there is no point keeping them longer. */
    public static final long MAX_AGE_MILLIS = 23 * 3_600_000L;

    private final Path file;
    private final Gson gson;
    private final Map<String, QueuedEvent> entries = new LinkedHashMap<>();

    public OfflineQueue(Path file, Gson gson)
    {
        this.file = file;
        this.gson = gson;
    }

    public synchronized void load()
    {
        entries.clear();
        if (!Files.isRegularFile(file))
        {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
        {
            List<QueuedEvent> loaded = gson.fromJson(reader, LIST_TYPE);
            if (loaded != null)
            {
                for (QueuedEvent q : loaded)
                {
                    if (q != null && q.event != null && q.event.id != null)
                    {
                        entries.put(q.event.id, q);
                    }
                }
            }
        }
        catch (IOException | RuntimeException ex)
        {
            // Corrupt file: keep a copy for inspection and start fresh.
            log.debug("Could not read queue file {}: {}", file, ex.toString());
            try
            {
                Files.move(file, file.resolveSibling(file.getFileName() + ".corrupt"), StandardCopyOption.REPLACE_EXISTING);
            }
            catch (IOException ignored)
            {
                // nothing more we can do
            }
        }
    }

    /** @return ids evicted because the queue was full (oldest first). */
    public synchronized List<QueuedEvent> add(QueuedEvent q)
    {
        entries.put(q.event.id, q);
        List<QueuedEvent> evicted = new ArrayList<>();
        Iterator<QueuedEvent> it = entries.values().iterator();
        while (entries.size() > MAX_ENTRIES && it.hasNext())
        {
            evicted.add(it.next());
            it.remove();
        }
        save();
        return evicted;
    }

    /** Removes and returns entries older than {@link #MAX_AGE_MILLIS}. */
    public synchronized List<QueuedEvent> expire(long now)
    {
        List<QueuedEvent> expired = new ArrayList<>();
        Iterator<QueuedEvent> it = entries.values().iterator();
        while (it.hasNext())
        {
            QueuedEvent q = it.next();
            if (now - q.createdAtMillis > MAX_AGE_MILLIS)
            {
                expired.add(q);
                it.remove();
            }
        }
        if (!expired.isEmpty())
        {
            save();
        }
        return expired;
    }

    public synchronized List<QueuedEvent> due(long now)
    {
        List<QueuedEvent> due = new ArrayList<>();
        for (QueuedEvent q : entries.values())
        {
            if (q.nextAttemptAtMillis <= now)
            {
                due.add(q);
            }
        }
        return due;
    }

    public synchronized void remove(String eventId)
    {
        if (entries.remove(eventId) != null)
        {
            save();
        }
    }

    public synchronized void scheduleRetry(String eventId, int attempts, long nextAttemptAt, String error)
    {
        QueuedEvent q = entries.get(eventId);
        if (q != null)
        {
            q.attempts = attempts;
            q.nextAttemptAtMillis = nextAttemptAt;
            q.lastError = error;
            save();
        }
    }

    /** Makes everything due now (used after the user fixes their key). */
    public synchronized void retryAllNow(long now)
    {
        for (QueuedEvent q : entries.values())
        {
            q.nextAttemptAtMillis = now;
        }
        save();
    }

    public synchronized int size()
    {
        return entries.size();
    }

    public synchronized void clear()
    {
        entries.clear();
        save();
    }

    private void save()
    {
        try
        {
            Path parent = file.getParent();
            if (parent != null)
            {
                Files.createDirectories(parent);
            }
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8))
            {
                gson.toJson(new ArrayList<>(entries.values()), LIST_TYPE, writer);
            }
            try
            {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (java.nio.file.AtomicMoveNotSupportedException ex)
            {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        catch (IOException ex)
        {
            log.debug("Could not persist queue to {}: {}", file, ex.toString());
        }
    }
}
