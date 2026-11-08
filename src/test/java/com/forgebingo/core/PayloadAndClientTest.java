package com.forgebingo.core;

import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PayloadAndClientTest
{
    private final Gson gson = new Gson();
    private MockWebServer server;
    private ApiClient client;

    @Before
    public void start() throws IOException
    {
        server = new MockWebServer();
        server.start();
        String base = server.url("/plugin-api").toString();
        client = new ApiClient(new OkHttpClient(), gson, () -> base, () -> "fb2_test_key", "2.0.0-test");
    }

    @After
    public void stop() throws IOException
    {
        server.shutdown();
    }

    private static GameEvent kcEvent()
    {
        GameEvent e = new GameEvent();
        e.id = "11111111-1111-4111-8111-111111111111";
        e.sourceType = GameEvent.KC;
        e.sourceName = "Zulrah";
        e.rsn = "Test Player";
        e.accountHash = "-9007199254740993";
        e.world = 301;
        e.occurredAt = "2026-10-07T12:00:00Z";
        return e;
    }

    @Test
    public void payloadSerializationMatchesContract()
    {
        GameEvent e = new GameEvent();
        e.id = "11111111-1111-4111-8111-111111111111";
        e.sourceType = GameEvent.NPC;
        e.npcId = 2215;
        e.npcName = "General Graardor";
        e.rsn = "Zezima";
        e.accountHash = "-9007199254740993";
        e.addItem(11832, 1);
        JsonObject o = gson.toJsonTree(e).getAsJsonObject();
        assertEquals("11111111-1111-4111-8111-111111111111", o.get("eventId").getAsString());
        assertFalse("internal field name must not leak", o.has("id"));
        assertEquals("npc", o.get("sourceType").getAsString());
        assertEquals(2215, o.get("npcId").getAsInt());
        assertEquals("General Graardor", o.get("npcName").getAsString());
        // 64-bit account hash must stay a string
        assertTrue(o.get("accountHash").getAsJsonPrimitive().isString());
        assertEquals("-9007199254740993", o.get("accountHash").getAsString());
        JsonObject item = o.getAsJsonArray("items").get(0).getAsJsonObject();
        assertEquals(11832, item.get("itemId").getAsInt());
        assertEquals(1, item.get("quantity").getAsInt());
        assertEquals("only itemId and quantity per item", 2, item.size());
        // null fields are omitted
        assertFalse(o.has("skill"));
        assertFalse(o.has("sourceName"));
    }

    @Test
    public void levelAndKcPayloads()
    {
        GameEvent lvl = new GameEvent();
        lvl.id = "x";
        lvl.sourceType = GameEvent.LEVEL;
        lvl.skill = "attack";
        lvl.level = 99;
        JsonObject o = gson.toJsonTree(lvl).getAsJsonObject();
        assertEquals("attack", o.get("skill").getAsString());
        assertEquals(99, o.get("level").getAsInt());
        assertFalse(o.has("items"));
    }

    @Test
    public void sendEventHitsGameEventsWithDocumentedHeaders() throws Exception
    {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"eventId\":\"x\",\"duplicate\":false,\"automationDisabled\":false,"
            + "\"sourceType\":\"kc\",\"unknownSource\":false,\"matched\":[{\"eventId\":\"e1\",\"teamId\":\"t1\",\"tileId\":\"ti\","
            + "\"taskId\":\"ta\",\"submissionId\":\"s1\",\"status\":\"pending\",\"contributedQuantity\":1,\"approvedQuantity\":0,"
            + "\"currentQuantity\":1,\"requiredQuantity\":3,\"completed\":false}],\"matchedTiles\":[],"
            + "\"skipped\":[{\"teamId\":\"t2\",\"eventId\":\"e2\",\"reason\":\"account_mismatch\"}]}"));
        ApiResult<ApiModels.EventResult> r = client.sendEvent(kcEvent());
        assertTrue(r.ok());
        assertTrue(r.body.isMatched());
        assertEquals("s1", r.body.matched.get(0).submissionId);
        assertEquals(3, r.body.matched.get(0).requiredQuantity);
        assertEquals("account_mismatch", r.body.skipped.get(0).reason);
        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertEquals("/plugin-api/v1/game-events", req.getPath());
        assertEquals("fb2_test_key", req.getHeader("X-API-Key"));
        assertEquals("application/json", req.getHeader("Accept"));
        assertTrue(req.getHeader("Content-Type").startsWith("application/json"));
        assertEquals("2.0.0-test", req.getHeader("X-Plugin-Version"));
        assertNull("idempotency is the eventId in the body", req.getHeader("Idempotency-Key"));
        JsonObject sent = gson.fromJson(req.getBody().readUtf8(), JsonObject.class);
        assertEquals("11111111-1111-4111-8111-111111111111", sent.get("eventId").getAsString());
        assertEquals("2.0.0-test", sent.get("pluginVersion").getAsString());
    }

    @Test
    public void errorBodiesAndRetryAfter()
    {
        server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "30")
            .setBody("{\"error\":\"rate_limited\",\"message\":\"Too many requests\"}"));
        ApiResult<ApiModels.EventResult> r = client.sendEvent(kcEvent());
        assertEquals(ErrorClass.TRANSIENT, r.errorClass());
        assertEquals(30_000, r.retryAfterMillis);
        assertEquals("rate_limited", r.errorCode);
        assertEquals("Too many requests", r.error);

        server.enqueue(new MockResponse().setResponseCode(400).setBody("{\"error\":\"invalid_rsn\",\"message\":\"bad\"}"));
        r = client.sendEvent(kcEvent());
        assertEquals(ErrorClass.PERMANENT, r.errorClass());
        assertEquals("invalid_rsn", r.errorCode);

        server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"error\":\"invalid_api_key\",\"message\":\"x\"}"));
        assertEquals(ErrorClass.AUTH, client.sendEvent(kcEvent()).errorClass());

        server.enqueue(new MockResponse().setResponseCode(409).setBody("{\"error\":\"event_id_conflict\",\"message\":\"x\"}"));
        assertEquals(ErrorClass.CONFLICT, client.sendEvent(kcEvent()).errorClass());

        server.enqueue(new MockResponse().setResponseCode(502).setBody("<html>bad gateway</html>"));
        assertEquals(ErrorClass.TRANSIENT, client.sendEvent(kcEvent()).errorClass());
    }

    @Test
    public void networkFailureIsTransient() throws IOException
    {
        server.shutdown();
        ApiResult<ApiModels.Me> r = client.me();
        assertEquals(0, r.status);
        assertEquals(ErrorClass.TRANSIENT, r.errorClass());
    }

    @Test
    public void meAndBoardsRoutes() throws Exception
    {
        server.enqueue(new MockResponse().setBody("{\"userId\":\"u\",\"displayName\":\"Alice\"}"));
        server.enqueue(new MockResponse().setBody("{\"boards\":[{\"teamBoardId\":\"tb1\",\"teamId\":\"tb1\",\"eventId\":\"e1\","
            + "\"eventName\":\"Forge\",\"boardTitle\":\"B\",\"boardSize\":5,\"teamName\":\"Wolves\",\"role\":\"player\","
            + "\"status\":\"live\",\"startsAt\":null,\"endsAt\":null,\"active\":true,\"completedCount\":2,\"totalTiles\":25,"
            + "\"updatedAt\":\"2026-10-07T12:00:00Z\"}]}"));
        assertEquals("Alice", client.me().body.displayName);
        ApiModels.BoardSummary b = client.boards().body.boards.get(0);
        assertEquals("tb1", b.teamBoardId);
        assertTrue(b.active);
        assertEquals(25, b.totalTiles);
        assertEquals("/plugin-api/v1/me", server.takeRequest().getPath());
        RecordedRequest req = server.takeRequest();
        assertEquals("GET", req.getMethod());
        assertEquals("/plugin-api/v1/boards", req.getPath());
    }

    @Test
    public void boardAndLeaderboardRoutes() throws Exception
    {
        server.enqueue(new MockResponse().setBody("{\"teamBoardId\":\"tb1\",\"teamId\":\"tb1\",\"teamName\":\"Wolves\",\"teamColor\":\"#4f8cc9\","
            + "\"event\":{\"id\":\"e1\",\"name\":\"Forge\",\"status\":\"live\",\"active\":true},"
            + "\"board\":{\"id\":\"b\",\"title\":\"B\",\"size\":3,\"bonuses\":{\"row\":3,\"col\":3,\"diagonal\":5,\"blackout\":25}},"
            + "\"tiles\":[{\"id\":\"t1\",\"row\":0,\"col\":1,\"position\":1,\"title\":\"Whip\",\"points\":3,\"iconItemId\":4151,\"hasIcon\":true,"
            + "\"iconUrl\":\"/v1/icons/tiles/00000000-0000-4000-b000-000000000001\",\"completionMode\":\"all\",\"completed\":false,"
            + "\"tasks\":[{\"id\":\"k\",\"description\":\"d\",\"requiredQty\":2,\"sourceType\":\"npc\",\"itemIds\":[4151],\"npcIds\":[1],"
            + "\"approvedQty\":1,\"pendingQty\":1,\"completed\":false}]}],\"updatedAt\":\"u\"}"));
        server.enqueue(new MockResponse().setBody("{\"teams\":[{\"teamId\":\"t\",\"teamName\":\"Wolves\",\"color\":\"#fff\",\"score\":7,"
            + "\"tilesCompleted\":2,\"rank\":1,\"reachedAt\":null,\"isMyTeam\":true}]}"));
        ApiModels.BoardResponse b = client.board("tb1").body;
        assertEquals(3, b.size());
        assertEquals(25, b.board.bonuses.blackout);
        assertEquals(4151, (int) b.tiles.get(0).iconItemId);
        assertEquals(1, b.tiles.get(0).tasks.get(0).pendingQty);
        assertEquals("/v1/icons/tiles/00000000-0000-4000-b000-000000000001", b.tiles.get(0).iconUrl);
        ApiModels.LeaderboardTeam t = client.leaderboard("tb1").body.teams.get(0);
        assertTrue(t.isMyTeam);
        assertEquals(1, t.rank);
        assertEquals("/plugin-api/v1/boards/tb1", server.takeRequest().getPath());
        assertEquals("/plugin-api/v1/boards/tb1/leaderboard", server.takeRequest().getPath());
    }

    @Test
    public void proofUploadIsMultipartWithSubmissionIds() throws Exception
    {
        server.enqueue(new MockResponse().setResponseCode(201)
            .setBody("{\"success\":true,\"proof\":{\"storagePath\":\"p\",\"submissionIds\":[\"s1\",\"s2\"]}}"));
        ApiResult<ApiModels.ProofResponse> r = client.uploadProof("tb1", new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 1, 2, 3, 4}, java.util.Arrays.asList("s1", "s2"));
        assertTrue(r.ok());
        assertEquals(201, r.status);
        assertEquals(2, r.body.proof.submissionIds.size());
        RecordedRequest req = server.takeRequest();
        assertEquals("/plugin-api/v1/boards/tb1/proofs", req.getPath());
        assertTrue(req.getHeader("Content-Type").startsWith("multipart/form-data"));
        assertEquals("fb2_test_key", req.getHeader("X-API-Key"));
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("name=\"submissionIds\""));
        assertTrue(body.contains("s1,s2"));
        assertTrue(body.contains("name=\"file\"; filename=\"proof.png\""));
        assertTrue(body.contains("image/png"));
        // 1-25 targets
        assertEquals(400, client.uploadProof("tb1", new byte[1], java.util.Collections.emptyList()).status);
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void iconFetchOnlyViaDocumentedEndpoints()
    {
        assertNull(client.icon("https://evil.example/a.png"));
        assertNull(client.icon("/v1/me"));
        assertEquals(0, server.getRequestCount());
        server.enqueue(new MockResponse().setBody("PNGDATA"));
        assertEquals("PNGDATA", new String(client.icon("/v1/icons/items/4151.png")));
        server.enqueue(new MockResponse().setBody("TILE"));
        assertEquals("TILE", new String(client.icon("/v1/icons/tiles/00000000-0000-4000-b000-000000000001")));
        server.enqueue(new MockResponse().setResponseCode(404).setBody("{\"error\":\"not_found\",\"message\":\"x\"}"));
        assertNull(client.icon("/v1/icons/tiles/00000000-0000-4000-b000-000000000002"));
    }

    @Test
    public void iconRequestSendsKeyAndStaysOnApiHost() throws Exception
    {
        server.enqueue(new MockResponse().setBody("X"));
        client.icon("/v1/icons/items/1.png");
        RecordedRequest req = server.takeRequest();
        assertEquals("/plugin-api/v1/icons/items/1.png", req.getPath());
        assertEquals("fb2_test_key", req.getHeader("X-API-Key"));
    }

    @Test
    public void placeholderBaseUrlIsNotConfigured()
    {
        ApiClient c = new ApiClient(new OkHttpClient(), gson, () -> "https://YOUR-PROJECT-REF.supabase.co/functions/v1/plugin-api", () -> "k", "v");
        assertFalse(c.configured());
        assertTrue(client.configured());
    }

    @Test
    public void rsnValidationMatchesServer()
    {
        assertTrue(Sanitizer.validRsn("Zezima"));
        assertTrue(Sanitizer.validRsn("Iron_Man-1 x"));
        assertFalse(Sanitizer.validRsn("ThirteenChars1"));
        assertFalse(Sanitizer.validRsn("bad!name"));
        assertFalse(Sanitizer.validRsn(""));
        assertEquals("Mr Mime", Sanitizer.normalizeRsn("Mr\u00a0Mime"));
    }

    @Test
    public void itemsAreCappedAndQuantitiesPositive()
    {
        GameEvent e = kcEvent();
        e.sourceType = GameEvent.NPC;
        for (int i = 0; i < 100; i++) e.addItem(i, i % 2 == 0 ? 0 : 5);
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
        assertTrue(client.sendEvent(e).ok());
        assertEquals(Sanitizer.MAX_ITEMS_PER_EVENT, e.items.size());
        for (GameEvent.Item i : e.items) assertTrue(i.quantity >= 1);
    }

    @Test
    public void longStringsAreTruncatedBeforeSending() throws Exception
    {
        GameEvent e = kcEvent();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 500; i++) sb.append("x");
        e.sourceName = sb.toString();
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
        assertTrue(client.sendEvent(e).ok());
        JsonObject sent = gson.fromJson(server.takeRequest().getBody().readUtf8(), JsonObject.class);
        assertEquals(Sanitizer.MAX_STRING, sent.get("sourceName").getAsString().length());
    }

    @Test
    public void clueTierExtraction()
    {
        assertEquals("elite", SourceClassifier.clueTier("Reward casket (elite)"));
        assertEquals("master", SourceClassifier.clueTier("Clue scroll (master)"));
        assertNull(SourceClassifier.clueTier("Chambers of Xeric"));
        assertEquals(GameEvent.CLUE, SourceClassifier.forEventLoot("Reward casket (hard)"));
        assertEquals(GameEvent.RAID_CHEST, SourceClassifier.forEventLoot("Tombs of Amascut"));
    }

    @Test
    public void hostileBoardIsClamped()
    {
        StringBuilder tiles = new StringBuilder();
        for (int i = 0; i < 1000; i++) tiles.append(i > 0 ? "," : "").append("{\"id\":\"t\",\"iconUrl\":\"https://evil.example/x.png\",\"title\":\"a\\u0000b\"}");
        server.enqueue(new MockResponse().setBody("{\"board\":{\"size\":9999},\"tiles\":[" + tiles + "]}"));
        ApiModels.BoardResponse b = client.board("e").body;
        assertEquals(Sanitizer.MAX_TILES, b.tiles.size());
        assertEquals(20, b.size());
        assertNull(b.tiles.get(0).iconUrl);
        assertEquals("a b", b.tiles.get(0).title);
    }

    @Test(expected = IllegalArgumentException.class)
    public void pathSegmentsCannotEscape()
    {
        client.board("..");
    }

    @Test
    public void queueFileNeverContainsApiKey() throws Exception
    {
        java.nio.file.Path f = java.nio.file.Files.createTempFile("q", ".json");
        OfflineQueue q = new OfflineQueue(TestFiles.fp(f), gson);
        q.add(new QueuedEvent(kcEvent(), 0));
        server.enqueue(new MockResponse().setBody("{}"));
        client.sendEvent(kcEvent());
        String content = new String(java.nio.file.Files.readAllBytes(f));
        assertFalse(content.contains("fb2_test_key"));
        assertTrue(content.contains("\"eventId\""));
    }

    @Test
    public void catalogueItemsRoute() throws Exception
    {
        server.enqueue(new MockResponse().setBody("{\"items\":[{\"id\":4151,\"name\":\"Abyssal whip\"},{\"id\":-1,\"name\":\"bad\"},null]}"));
        ApiResult<ApiModels.CatalogueResponse> r = client.catalogueItems();
        assertTrue(r.ok());
        assertEquals(1, r.body.items.size());
        assertEquals("Abyssal whip", r.body.items.get(0).name);
        RecordedRequest req = server.takeRequest();
        assertEquals("/plugin-api/v1/catalogue/items", req.getPath());
        assertEquals("fb2_test_key", req.getHeader("X-API-Key"));
    }
}
