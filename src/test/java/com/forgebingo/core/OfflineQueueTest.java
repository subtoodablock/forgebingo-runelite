package com.forgebingo.core;

import static org.junit.Assert.*;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class OfflineQueueTest
{
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private final Gson gson = new Gson();

    private static GameEvent event(String id)
    {
        GameEvent e = new GameEvent();
        e.id = id;
        e.sourceType = GameEvent.NPC;
        e.npcId = 8061;
        e.npcName = "Vorkath";
        e.rsn = "Test Player";
        e.accountHash = "123";
        e.occurredAt = "2026-10-07T00:00:00Z";
        e.addItem(11286, 1);
        return e;
    }

    @Test
    public void survivesRestart() throws IOException
    {
        Path f = tmp.getRoot().toPath().resolve("forgebingo").resolve("queue.json");
        OfflineQueue q = new OfflineQueue(f, gson);
        q.add(new QueuedEvent(event("a"), 1000));
        q.add(new QueuedEvent(event("b"), 1000));
        q.scheduleRetry("b", 3, 99_000, "HTTP 503");

        OfflineQueue reloaded = new OfflineQueue(f, gson);
        reloaded.load();
        assertEquals(2, reloaded.size());
        assertEquals(1, reloaded.due(1000).size());
        assertEquals("a", reloaded.due(1000).get(0).event.id);
        QueuedEvent b = reloaded.due(99_000).get(1);
        assertEquals(3, b.attempts);
        assertEquals("HTTP 503", b.lastError);
        assertEquals(11286, b.event.items.get(0).itemId);
    }

    @Test
    public void corruptFileIsSetAsideNotCrash() throws IOException
    {
        Path f = tmp.getRoot().toPath().resolve("queue.json");
        Files.write(f, "{{{ not json".getBytes());
        OfflineQueue q = new OfflineQueue(f, gson);
        q.load();
        assertEquals(0, q.size());
        assertTrue(Files.exists(tmp.getRoot().toPath().resolve("queue.json.corrupt")));
    }

    @Test
    public void boundedAndExpiring() throws IOException
    {
        OfflineQueue q = new OfflineQueue(tmp.getRoot().toPath().resolve("q.json"), gson);
        List<QueuedEvent> evicted = new ArrayList<>();
        for (int i = 0; i < OfflineQueue.MAX_ENTRIES + 3; i++)
        {
            evicted.addAll(q.add(new QueuedEvent(event("e" + i), 0)));
        }
        assertEquals(OfflineQueue.MAX_ENTRIES, q.size());
        assertEquals(3, evicted.size());
        assertEquals("e0", evicted.get(0).event.id);

        assertEquals(OfflineQueue.MAX_ENTRIES, q.expire(OfflineQueue.MAX_AGE_MILLIS + 1).size());
        assertEquals(0, q.size());
    }

    // --- processor ---

    private static final class Recorder implements QueueProcessor.Listener
    {
        final List<String> accepted = new ArrayList<>();
        final List<String> dropped = new ArrayList<>();
        final List<String> auth = new ArrayList<>();

        public void onAccepted(GameEvent e, ApiModels.EventResult r)
        {
            accepted.add(e.id);
        }

        public void onDropped(GameEvent e, String reason)
        {
            dropped.add(e.id + ":" + reason);
        }

        public void onAuthProblem(String m)
        {
            auth.add(m);
        }

        public void onQueueChanged(int size, String lastError)
        {
        }
    }

    private static ApiResult<ApiModels.EventResult> result(int status)
    {
        return new ApiResult<>(status, null, null, null, null, 0);
    }

    @Test
    public void processorRetriesTransientDropsPermanentPausesOnAuth() throws IOException
    {
        Path f = tmp.getRoot().toPath().resolve("q.json");
        OfflineQueue q = new OfflineQueue(f, gson);
        AtomicLong now = new AtomicLong(1_000_000);
        q.add(new QueuedEvent(event("ok"), now.get()));
        q.add(new QueuedEvent(event("bad"), now.get()));
        q.add(new QueuedEvent(event("flaky"), now.get()));
        Recorder rec = new Recorder();
        java.util.Map<String, Integer> statuses = new java.util.HashMap<>();
        statuses.put("ok", 200);
        statuses.put("bad", 422);
        statuses.put("flaky", 503);
        QueueProcessor p = new QueueProcessor(q, e -> result(statuses.get(e.id)), rec, now::get, new Random(0));

        long next = p.drain();
        assertEquals(java.util.Collections.singletonList("ok"), rec.accepted);
        assertEquals(1, rec.dropped.size());
        assertTrue(rec.dropped.get(0).startsWith("bad:"));
        assertEquals(1, q.size());
        assertTrue(next >= 4_000 && next <= 6_000);
        assertEquals(1, q.due(Long.MAX_VALUE).get(0).attempts);

        // not due yet
        assertEquals(0, q.due(now.get()).size());

        // later: still failing, backoff grows
        now.addAndGet(10_000);
        p.drain();
        assertEquals(2, q.due(Long.MAX_VALUE).get(0).attempts);

        // key rejected: event kept, paused
        statuses.put("flaky", 401);
        now.addAndGet(600_000);
        p.drain();
        assertEquals(1, q.size());
        assertTrue(p.isAuthPaused());
        assertEquals(1, rec.auth.size());

        // fixed key: resumes immediately
        statuses.put("flaky", 200);
        p.resumeAfterAuthChange();
        p.drain();
        assertEquals(0, q.size());
        assertEquals(2, rec.accepted.size());
    }

    @Test
    public void processorRegeneratesIdOnEventIdConflict() throws IOException
    {
        OfflineQueue q = new OfflineQueue(tmp.getRoot().toPath().resolve("q.json"), gson);
        q.add(new QueuedEvent(event("dup"), 1000));
        Recorder rec = new Recorder();
        java.util.Set<String> seen = new java.util.HashSet<>();
        QueueProcessor p = new QueueProcessor(q, e -> {
            seen.add(e.id);
            return seen.size() == 1 ? new ApiResult<>(409, null, "taken", "event_id_conflict", null, 0) : result(200);
        }, rec, () -> 2000, new Random(0));
        p.drain();
        assertEquals(1, q.size());
        assertNotEquals("dup", q.due(Long.MAX_VALUE).get(0).event.id);
        p.drain();
        assertEquals(0, q.size());
        assertEquals(2, seen.size());
        assertEquals(1, rec.accepted.size());
    }

    @Test
    public void processorDropsExpired() throws IOException
    {
        OfflineQueue q = new OfflineQueue(tmp.getRoot().toPath().resolve("q.json"), gson);
        q.add(new QueuedEvent(event("old"), 0));
        Recorder rec = new Recorder();
        QueueProcessor p = new QueueProcessor(q, e -> result(200), rec,
            () -> OfflineQueue.MAX_AGE_MILLIS + 5, new Random(0));
        assertEquals(-1, p.drain());
        assertEquals(1, rec.dropped.size());
        assertTrue(rec.accepted.isEmpty());
    }

    @Test
    public void deduplicatorWindow()
    {
        EventDeduplicator d = new EventDeduplicator(2_000, 10);
        GameEvent e = event("x");
        String sig = EventDeduplicator.signature(e, 100);
        assertTrue(d.firstSeen(sig, 0));
        assertFalse(d.firstSeen(sig, 1_000));
        assertTrue(d.firstSeen(sig, 5_000));
    }
}
