package com.forgebingo.core;

import static org.junit.Assert.*;

import java.util.Random;
import org.junit.Test;

public class BackoffAndErrorTest
{
    @Test
    public void exponentialWithoutJitter()
    {
        assertEquals(5_000, Backoff.delayMillis(1, 0, null));
        assertEquals(10_000, Backoff.delayMillis(2, 0, null));
        assertEquals(20_000, Backoff.delayMillis(3, 0, null));
        assertEquals(Backoff.MAX_MILLIS, Backoff.delayMillis(30, 0, null));
    }

    @Test
    public void jitterStaysWithinTwentyPercent()
    {
        Random r = new Random(1);
        for (int i = 0; i < 200; i++)
        {
            long d = Backoff.delayMillis(3, 0, r);
            assertTrue(d >= 16_000 && d <= 24_000);
        }
    }

    @Test
    public void retryAfterWins()
    {
        assertEquals(60_000, Backoff.delayMillis(1, 60_000, null));
        assertEquals(20_000, Backoff.delayMillis(3, 1_000, null));
        assertEquals(60_000, Backoff.parseRetryAfterMillis(" 60 "));
        assertEquals(0, Backoff.parseRetryAfterMillis("Wed, 21 Oct 2026 07:28:00 GMT"));
        assertEquals(0, Backoff.parseRetryAfterMillis(null));
    }

    @Test
    public void classification()
    {
        assertEquals(ErrorClass.SUCCESS, ErrorClass.of(200));
        assertEquals(ErrorClass.SUCCESS, ErrorClass.of(201));
        // transient: network, 429 (Retry-After), 5xx
        assertEquals(ErrorClass.TRANSIENT, ErrorClass.of(0));
        assertEquals(ErrorClass.TRANSIENT, ErrorClass.of(429, "rate_limited"));
        assertEquals(ErrorClass.TRANSIENT, ErrorClass.of(500, "internal_error"));
        assertEquals(ErrorClass.TRANSIENT, ErrorClass.of(503));
        // auth: only 401 invalid_api_key pauses the queue
        assertEquals(ErrorClass.AUTH, ErrorClass.of(401, "invalid_api_key"));
        // permanent: every documented 400 code, 403 not_a_member, 404, 413, 415
        for (String code : new String[] {"invalid_payload", "invalid_items", "invalid_rsn", "invalid_account_hash",
            "unsupported_source_type", "occurred_at_out_of_range", "invalid_json"})
        {
            assertEquals(code, ErrorClass.PERMANENT, ErrorClass.of(400, code));
        }
        assertEquals(ErrorClass.PERMANENT, ErrorClass.of(403, "not_a_member"));
        assertEquals(ErrorClass.PERMANENT, ErrorClass.of(404, "not_found"));
        assertEquals(ErrorClass.PERMANENT, ErrorClass.of(413, "payload_too_large"));
        assertEquals(ErrorClass.PERMANENT, ErrorClass.of(415, "unsupported_media_type"));
        // 409 event_id_conflict: regenerate the id
        assertEquals(ErrorClass.CONFLICT, ErrorClass.of(409, "event_id_conflict"));
        assertEquals(ErrorClass.PERMANENT, ErrorClass.of(409, "something_else"));
    }

    @Test
    public void iconPathGuard()
    {
        assertTrue(IconRef.isProxyPath("/v1/icons/items/4151.png"));
        assertTrue(IconRef.isProxyPath("/v1/icons/tiles/00000000-0000-4000-b000-000000000001"));
        assertFalse(IconRef.isProxyPath("/v1/icons/items/4151.png?x=1"));
        assertFalse(IconRef.isProxyPath("/v1/icons/items/abc.png"));
        assertFalse(IconRef.isProxyPath("/v1/icons/item/4151.png"));
        assertFalse(IconRef.isProxyPath("https://evil.example/x.png"));
        assertFalse(IconRef.isProxyPath("/v1/icons/../me"));
        assertFalse(IconRef.isProxyPath("//evil.example/v1/icons/x"));
        assertFalse(IconRef.isProxyPath("/v1/me"));
        assertFalse(IconRef.isProxyPath(null));
    }

    @Test
    public void baseUrlValidation()
    {
        assertNotNull(ApiClient.parseBaseUrl("https://api.example.com/plugin-api"));
        assertNotNull(ApiClient.parseBaseUrl("http://localhost:54321/functions/v1/plugin-api"));
        assertNull(ApiClient.parseBaseUrl("http://example.com/"));
        assertNull(ApiClient.parseBaseUrl("ftp://example.com/"));
        assertNull(ApiClient.parseBaseUrl("not a url"));
        assertTrue(ApiClient.isPlaceholder("https://YOUR-PROJECT-REF.supabase.co/functions/v1/plugin-api"));
        assertFalse(ApiClient.isPlaceholder("https://abcdefgh.supabase.co/functions/v1/plugin-api"));
        assertNull(ApiClient.parseBaseUrl(null));
    }
}
