package com.forgebingo.core;

import static org.junit.Assert.*;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** POST /v1/hello, RSN verification wording, revoked keys (docs/API.md section 8). */
public class ConnectionStatusTest
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

    private static ApiModels.Membership m(String status, String joined, String plugin)
    {
        ApiModels.Membership m = new ApiModels.Membership();
        m.eventName = "Forge Bingo";
        m.teamName = "Wolves";
        m.status = status;
        m.verified = "verified".equals(status);
        m.joinedRsn = joined;
        m.pluginRsn = plugin;
        return m;
    }

    @Test
    public void helloPostsRsnAndAccountHashOnly() throws Exception
    {
        server.enqueue(new MockResponse().setBody("{\"displayName\":\"Gus\",\"rsn\":\"Gus\",\"keyScope\":\"event\",\"memberships\":["
            + "{\"eventId\":\"e1\",\"eventName\":\"Forge\",\"teamId\":\"t1\",\"teamName\":\"Wolves\",\"joinedRsn\":\"Gus\","
            + "\"pluginRsn\":\"Gus\",\"status\":\"verified\",\"verified\":true}]}"));
        ApiResult<ApiModels.HelloResponse> r = client.hello("Gus B", "-42");
        assertTrue(r.describe(), r.ok());
        assertEquals("event", r.body.keyScope);
        assertTrue(r.body.memberships.get(0).verified);
        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertEquals("/plugin-api/v1/hello", req.getPath());
        assertEquals("fb2_test_key", req.getHeader("X-API-Key"));
        assertEquals("{\"rsn\":\"Gus B\",\"accountHash\":\"-42\"}", req.getBody().readUtf8());
    }

    @Test
    public void helloIsNotSentWhenNotLoggedIn()
    {
        assertEquals(400, client.hello(null, "1").status);
        assertEquals(400, client.hello("bad!name", "1").status);
        assertEquals(400, client.hello("Gus", "abc").status);
        assertEquals(0, server.getRequestCount());
    }

    @Test
    public void helloResponseIsSanitised() throws Exception
    {
        StringBuilder many = new StringBuilder("{\"memberships\":[");
        for (int i = 0; i < 30; i++)
        {
            many.append(i == 0 ? "" : ",").append("{\"status\":\"verified\",\"eventName\":\"E\\u0007vil\"}");
        }
        server.enqueue(new MockResponse().setBody(many.append("]}").toString()));
        ApiModels.HelloResponse r = client.hello("Gus", "1").body;
        assertEquals(20, r.memberships.size());
        assertEquals("E vil", r.memberships.get(0).eventName);
    }

    @Test
    public void revokedKeyMessageSaysWhereToGetANewOne() throws Exception
    {
        server.enqueue(new MockResponse().setResponseCode(401)
            .setBody("{\"error\":\"api_key_revoked\",\"message\":\"This key was revoked or replaced.\"}"));
        ApiResult<ApiModels.Me> r = client.me();
        assertEquals(ErrorClass.AUTH, r.errorClass());
        assertEquals("api_key_revoked", r.errorCode);
        String msg = ConnectionStatus.authMessage(r.errorCode);
        assertTrue(msg, msg.contains("revoked"));
        assertTrue(msg, msg.contains("Connect RuneLite"));
        assertTrue(ConnectionStatus.authMessage("invalid_api_key").contains("not recognised"));
    }

    @Test
    public void verifiedLineShowsRsnEventAndTeam()
    {
        ConnectionStatus.Line l = ConnectionStatus.line(m("verified", "Gus", "Gus"));
        assertEquals(ConnectionStatus.Level.OK, l.level);
        assertEquals("Verified as Gus - Forge Bingo / Wolves", l.text);
    }

    @Test
    public void mismatchLineNamesBothNamesAndTheFix()
    {
        ConnectionStatus.Line l = ConnectionStatus.line(m("rsn_mismatch", "Zezima", "Griefer"));
        assertEquals(ConnectionStatus.Level.ERROR, l.level);
        assertTrue(l.text, l.text.startsWith("RuneLite says you're logged in as Griefer, but you joined Forge Bingo / Wolves as Zezima."));
        assertTrue(l.text, l.text.contains("Fix your name"));
        assertTrue(l.text, l.text.contains("wait for review"));
    }

    @Test
    public void otherStates()
    {
        assertEquals(ConnectionStatus.Level.ERROR, ConnectionStatus.line(m("account_mismatch", "Gus", "Gus")).level);
        assertTrue(ConnectionStatus.line(m("account_taken", "Gus", "Gus")).text.contains("another player"));
        assertEquals(ConnectionStatus.Level.WARNING, ConnectionStatus.line(m("not_approved", "Gus", null)).level);
        List<ConnectionStatus.Line> none = ConnectionStatus.fromHello(new ApiModels.HelloResponse());
        assertEquals(1, none.size());
        assertTrue(none.get(0).text.contains("team link"));
    }

    @Test
    public void allVerifiedNeedsEveryMembership()
    {
        ApiModels.HelloResponse r = new ApiModels.HelloResponse();
        assertFalse(ConnectionStatus.allVerified(r));
        r.memberships = new java.util.ArrayList<>(java.util.Arrays.asList(m("verified", "Gus", "Gus")));
        assertTrue(ConnectionStatus.allVerified(r));
        r.memberships.add(m("rsn_mismatch", "Gus", "Other"));
        assertFalse(ConnectionStatus.allVerified(r));
    }

    @Test
    public void meCarriesKeyScope() throws Exception
    {
        server.enqueue(new MockResponse().setBody("{\"userId\":\"u\",\"displayName\":\"Gus\",\"keyScope\":\"event\",\"eventId\":\"e1\"}"));
        ApiModels.Me me = client.me().body;
        assertEquals("event", me.keyScope);
        assertEquals("e1", me.eventId);
        assertEquals("Player key (one event)", ConnectionStatus.keyScope(me));
        me.keyScope = "account";
        assertEquals("Account key (all your events)", ConnectionStatus.keyScope(me));
    }

    @Test
    public void matchCarriesVerifiedFlag()
    {
        ApiModels.EventResult r = gson.fromJson("{\"matched\":[{\"status\":\"pending\",\"verified\":false}]}", ApiModels.EventResult.class);
        assertEquals(Boolean.FALSE, r.matched.get(0).verified);
        ApiModels.EventResult old = gson.fromJson("{\"matched\":[{\"status\":\"pending\"}]}", ApiModels.EventResult.class);
        assertNull("older servers omit it", old.matched.get(0).verified);
    }
}
