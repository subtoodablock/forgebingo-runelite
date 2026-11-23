package com.forgebingo.core;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.util.Filepath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Durable FIFO of events not yet accepted by the server. Persisted as JSON in the plugin's own data folder through
 * RuneLite's Filepath API (atomic write via temp file + move) so a crash or client close loses nothing.
 * Bounded by count and age. With deferred saves (the plugin's mode) an event is on disk once {@link #flush()} has
 * run after it was added; until then it is only in memory.
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

    private final Filepath file;
    private final Gson gson;
    private final Map<String, QueuedEvent> entries = new LinkedHashMap<>();
    /** changes are kept in memory and written by {@link #flush()} (from a worker thread), not by the caller */
    private final boolean deferSaves;
    /** deferred mode: the file is behind what is in memory */
    private boolean dirty;
    /** one writer of the file at a time; always taken before this queue's own lock */
    private final Object saveLock = new Object();

    /** A queue that writes its file on every change, on the calling thread. */
    public OfflineQueue(Filepath file, Gson gson)
    {
        this(file, gson, false);
    }

    /**
     * @param deferSaves true: a change only marks the queue as changed, and {@link #flush()} writes the file. The
     *                   plugin adds events on the game thread, which must not write files; its worker flushes.
     */
    public OfflineQueue(Filepath file, Gson gson, boolean deferSaves)
    {
        this.file = file;
        this.gson = gson;
        this.deferSaves = deferSaves;
    }

    public synchronized void load()
    {
        entries.clear();
        if (!file.isFile())
        {
            return;
        }
        try (Reader reader = file.openBufferedReader())
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
                file.moveTo(file.getParent().joinSegment(file.getFileName() + ".corrupt"), StandardCopyOption.REPLACE_EXISTING);
            }
            catch (IOException | RuntimeException ignored)
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
        changed();
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
            changed();
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
            changed();
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
            changed();
        }
    }

    /** Makes everything due now (used after the user fixes their key). */
    public synchronized void retryAllNow(long now)
    {
        for (QueuedEvent q : entries.values())
        {
            q.nextAttemptAtMillis = now;
        }
        changed();
    }

    public synchronized int size()
    {
        return entries.size();
    }

    public synchronized void clear()
    {
        entries.clear();
        changed();
    }

    /** Called with this queue's lock held, after a change. */
    private void changed()
    {
        if (deferSaves)
        {
            dirty = true;
        }
        else
        {
            write(gson.toJson(new ArrayList<>(entries.values()), LIST_TYPE));
        }
    }

    /**
     * Deferred mode: writes the file when something changed since the last write (does nothing otherwise, and
     * nothing in the write-on-every-change mode). Call from a worker thread. A failed write is tried again by the
     * next flush.
     *
     * @return true when the file was written
     */
    public boolean flush()
    {
        if (!deferSaves) return false;
        synchronized (saveLock)
        {
            String json;
            synchronized (this)
            {
                if (!dirty) return false;
                json = gson.toJson(new ArrayList<>(entries.values()), LIST_TYPE);
                dirty = false;
            }
            // the file is written outside the queue's lock: the game thread adding an event never waits for the disk
            if (write(json)) return true;
            synchronized (this)
            {
                dirty = true;
            }
            return false;
        }
    }

    /** Deferred mode: something changed that {@link #flush()} has not written yet. */
    public synchronized boolean unsaved()
    {
        return dirty;
    }

    private boolean write(String json)
    {
        try
        {
            Filepath parent = file.getParent();
            parent.createDirectories();
            Filepath tmp = parent.joinSegment(file.getFileName() + ".tmp");
            try (Writer writer = tmp.openBufferedWriter())
            {
                writer.write(json);
            }
            try
            {
                tmp.moveTo(file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException ex)
            {
                tmp.moveTo(file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        }
        catch (IOException | RuntimeException ex)
        {
            log.debug("Could not persist queue to {}: {}", file, ex.toString());
            return false;
        }
    }
}
