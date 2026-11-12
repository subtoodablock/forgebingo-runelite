package com.forgebingo.core;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * The only class that knows the HTTP routes. Blocking: call from the plugin's
 * executor, never the client or EDT thread. Every request carries the key in
 * {@code X-API-Key} (plus {@code Accept} and {@code X-Plugin-Version}; JSON bodies are {@code Content-Type: application/json}) and only ever goes to the configured base URL.
 */
public final class ApiClient
{
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final long MAX_BODY_BYTES = 2L * 1024 * 1024;
    private static final long MAX_ICON_BYTES = 1024L * 1024;
    public static final int MAX_SCREENSHOT_BYTES = 8 * 1024 * 1024;

    private final OkHttpClient http;
    /** same client with longer timeouts, for screenshot uploads only (a slow upload used to time out) */
    private final OkHttpClient uploadHttp;
    private final Gson gson;
    private final Supplier<String> baseUrl;
    private final Supplier<String> apiKey;
    private final String pluginVersion;

    public ApiClient(OkHttpClient http, Gson gson, Supplier<String> baseUrl, Supplier<String> apiKey, String pluginVersion)
    {
        // no redirects: OkHttp keeps custom headers (X-API-Key) on a redirect to another host, so a redirecting
        // server or proxy could collect the key
        this.http = http.newBuilder().followRedirects(false).followSslRedirects(false).build();
        this.uploadHttp = this.http.newBuilder().writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS).callTimeout(90, java.util.concurrent.TimeUnit.SECONDS).build();
        this.gson = gson;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.pluginVersion = pluginVersion;
    }

    /** Validates a user-supplied base URL: https only (http allowed for localhost dev). */
    public static HttpUrl parseBaseUrl(String raw)
    {
        if (raw == null)
        {
            return null;
        }
        HttpUrl url = HttpUrl.parse(raw.trim());
        if (url == null)
        {
            return null;
        }
        boolean local = "localhost".equals(url.host()) || "127.0.0.1".equals(url.host());
        if (!url.isHttps() && !(local && "http".equals(url.scheme())))
        {
            return null;
        }
        return url;
    }

    /** The default base URL is a placeholder; it must be replaced before anything is sent. */
    public static boolean isPlaceholder(String raw)
    {
        return raw == null || raw.contains("YOUR-PROJECT-REF") || raw.contains("<");
    }

    public boolean configured()
    {
        String base = baseUrl.get();
        return !isPlaceholder(base) && parseBaseUrl(base) != null && !apiKey.get().trim().isEmpty();
    }

    // --- routes (single place; keep in sync with docs/API.md section 8) ---

    /** GET /v1/me */
    public ApiResult<ApiModels.Me> me()
    {
        return get(url("v1", "me"), ApiModels.Me.class);
    }

    /**
     * POST /v1/hello: reports the logged-in RSN + account hash. The server binds each membership to that OSRS account
     * and says per event whether the name matches the one the player joined with.
     */
    public ApiResult<ApiModels.HelloResponse> hello(String rsn, String accountHash)
    {
        String name = Sanitizer.normalizeRsn(rsn);
        if (!Sanitizer.validRsn(name) || accountHash == null || !accountHash.matches("^-?[0-9]{1,20}$"))
        {
            return new ApiResult<>(400, null, "not logged in", "invalid_rsn", null, 0);
        }
        RequestBody body = RequestBody.create(JSON, gson.toJson(new ApiModels.HelloRequest(name, accountHash)));
        return execute(builder(url("v1", "hello")).post(body).build(), ApiModels.HelloResponse.class);
    }

    /**
     * POST /v1/wom/update {rsn}: asks the server to refresh this player's Wise Old Man stats for the bingos that track
     * them (sent on logout / world hop). The server rate-limits it; a 404 means this server has no WOM connector yet.
     */
    public ApiResult<ApiModels.WomUpdateResponse> womUpdate(String rsn)
    {
        String name = Sanitizer.normalizeRsn(rsn);
        if (!Sanitizer.validRsn(name)) return new ApiResult<>(400, null, "not logged in", "invalid_rsn", null, 0);
        RequestBody body = RequestBody.create(JSON, gson.toJson(java.util.Collections.singletonMap("rsn", name)));
        return execute(builder(url("v1", "wom", "update")).post(body).build(), ApiModels.WomUpdateResponse.class);
    }

    /** GET /v1/boards: non-draft events the user plays in. Poll no faster than every 5 s. */
    public ApiResult<ApiModels.BoardsResponse> boards()
    {
        return get(url("v1", "boards"), ApiModels.BoardsResponse.class);
    }

    /** GET /v1/boards/{teamBoardId} */
    public ApiResult<ApiModels.BoardResponse> board(String teamBoardId)
    {
        return get(url("v1", "boards", teamBoardId), ApiModels.BoardResponse.class);
    }

    /** POST /v1/boards/{teamBoardId}/working-on: mark or unmark a tile you are going for (your team sees it). */
    public ApiResult<Object> setWorkingOn(String teamBoardId, String tileId, boolean on)
    {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("tileId", tileId);
        m.put("on", on);
        RequestBody body = RequestBody.create(JSON, gson.toJson(m));
        return execute(builder(url("v1", "boards", teamBoardId, "working-on")).post(body).build(), Object.class);
    }

    /** GET /v1/boards/{teamBoardId}/leaderboard */
    public ApiResult<ApiModels.LeaderboardResponse> leaderboard(String teamBoardId)
    {
        return get(url("v1", "boards", teamBoardId, "leaderboard"), ApiModels.LeaderboardResponse.class);
    }

    /** GET /v1/catalogue/items: id/name of the server's item catalogue (to resolve collection log names). */
    public ApiResult<ApiModels.CatalogueResponse> catalogueItems()
    {
        return get(url("v1", "catalogue", "items"), ApiModels.CatalogueResponse.class);
    }

    /**
     * POST /v1/game-events. The server fans the event out to every open event the player is in.
     * The eventId in the body is the idempotency key, so retries are safe.
     */
    public ApiResult<ApiModels.EventResult> sendEvent(GameEvent event)
    {
        event.pluginVersion = pluginVersion;
        Sanitizer.event(event);
        String json = gson.toJson(event);
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > Sanitizer.MAX_EVENT_JSON_BYTES)
        {
            return new ApiResult<>(413, null, "event payload too large", "payload_too_large", null, 0);
        }
        RequestBody body = RequestBody.create(JSON, json);
        return execute(builder(url("v1", "game-events")).post(body).build(), ApiModels.EventResult.class);
    }

    /**
     * POST /v1/boards/{teamBoardId}/proofs (multipart). Attaches the screenshot to the given submissions
     * (from {@code matched[].submissionId}); 1-25 targets.
     */
    public ApiResult<ApiModels.ProofResponse> uploadProof(String teamBoardId, byte[] png, List<String> submissionIds)
    {
        return uploadProof(teamBoardId, png, submissionIds, null, null);
    }

    /** As above, also telling the server which keyword was stamped on the shot and when it was taken (ISO instant). */
    public ApiResult<ApiModels.ProofResponse> uploadProof(String teamBoardId, byte[] png, List<String> submissionIds, String stampKeyword, String capturedAt)
    {
        return uploadProof(teamBoardId, png, submissionIds, stampKeyword, capturedAt, null);
    }

    /** As above; label names the stored file after the task it proves ("Big boners" becomes big-boners_....png). */
    public ApiResult<ApiModels.ProofResponse> uploadProof(String teamBoardId, byte[] png, List<String> submissionIds, String stampKeyword, String capturedAt, String label)
    {
        if (png.length > MAX_SCREENSHOT_BYTES)
        {
            return new ApiResult<>(413, null, "screenshot too large", "payload_too_large", null, 0);
        }
        if (submissionIds.isEmpty() || submissionIds.size() > 25)
        {
            return new ApiResult<>(400, null, "1-25 submission ids required", "invalid_targets", null, 0);
        }
        MultipartBody.Builder mb = new MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("submissionIds", String.join(",", submissionIds));
        addLabel(mb, label);
        if (stampKeyword != null && !stampKeyword.isEmpty()) mb.addFormDataPart("stampKeyword", stampKeyword.length() > 40 ? stampKeyword.substring(0, 40) : stampKeyword);
        if (capturedAt != null && !capturedAt.isEmpty()) mb.addFormDataPart("capturedAt", capturedAt.length() > 40 ? capturedAt.substring(0, 40) : capturedAt);
        RequestBody body = mb.addFormDataPart("file", fileName(png), RequestBody.create(MediaType.parse(mime(png)), png)).build();
        return execute(uploadHttp, builder(url("v1", "boards", teamBoardId, "proofs")).post(body).build(), ApiModels.ProofResponse.class);
    }

    private static String fileName(byte[] b) { return ScreenshotEncode.isJpeg(b) ? "proof.jpg" : "proof.png"; }
    private static String mime(byte[] b) { return ScreenshotEncode.isJpeg(b) ? "image/jpeg" : "image/png"; }

    /** POST /v1/boards/{team}/proofs with a taskId: a new pending submission for that task with this screenshot. */
    public ApiResult<ApiModels.ProofResponse> uploadTaskScreenshot(String teamBoardId, String taskId, byte[] png, String stampKeyword, String capturedAt, String label)
    {
        if (png.length > MAX_SCREENSHOT_BYTES)
        {
            return new ApiResult<>(413, null, "screenshot too large", "payload_too_large", null, 0);
        }
        MultipartBody.Builder mb = new MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("taskId", taskId);
        addLabel(mb, label);
        if (stampKeyword != null && !stampKeyword.isEmpty()) mb.addFormDataPart("stampKeyword", stampKeyword.length() > 40 ? stampKeyword.substring(0, 40) : stampKeyword);
        if (capturedAt != null && !capturedAt.isEmpty()) mb.addFormDataPart("capturedAt", capturedAt.length() > 40 ? capturedAt.substring(0, 40) : capturedAt);
        RequestBody body = mb.addFormDataPart("file", fileName(png), RequestBody.create(MediaType.parse(mime(png)), png)).build();
        return execute(uploadHttp, builder(url("v1", "boards", teamBoardId, "proofs")).post(body).build(), ApiModels.ProofResponse.class);
    }

    /** The task name the server uses for the file name (letters, digits and spaces only, at most 60). */
    private static void addLabel(MultipartBody.Builder mb, String label)
    {
        if (label == null) return;
        String clean = label.replaceAll("[^A-Za-z0-9 ]", " ").trim().replaceAll("\\s+", " ");
        if (clean.isEmpty()) return;
        mb.addFormDataPart("label", clean.length() > 60 ? clean.substring(0, 60) : clean);
    }

    /** Fetches an icon from the server's icon proxy. Returns null on any failure. */
    public byte[] icon(String proxyPath)
    {
        if (!IconRef.isProxyPath(proxyPath))
        {
            return null;
        }
        HttpUrl base = parseBaseUrl(baseUrl.get());
        if (base == null)
        {
            return null;
        }
        HttpUrl.Builder b = base.newBuilder();
        for (String seg : proxyPath.substring(1).split("/"))
        {
            b.addPathSegment(seg);
        }
        try (Response r = http.newCall(builder(b.build()).get().build()).execute())
        {
            ResponseBody rb = r.body();
            if (!r.isSuccessful() || rb == null || rb.contentLength() > MAX_ICON_BYTES)
            {
                return null;
            }
            byte[] bytes = rb.bytes();
            return bytes.length > MAX_ICON_BYTES ? null : bytes;
        }
        catch (IOException ex)
        {
            return null;
        }
    }

    // --- plumbing ---

    private HttpUrl url(String... segments)
    {
        HttpUrl base = parseBaseUrl(baseUrl.get());
        if (base == null)
        {
            throw new IllegalStateException("API base URL is not a valid https URL");
        }
        HttpUrl.Builder b = base.newBuilder();
        for (String s : segments)
        {
            if (s == null || s.isEmpty() || ".".equals(s) || "..".equals(s))
            {
                throw new IllegalArgumentException("bad path segment");
            }
            b.addPathSegment(s);
        }
        return b.build();
    }

    private Request.Builder builder(HttpUrl url)
    {
        return new Request.Builder().url(url)
            .header("X-API-Key", apiKey.get().trim())
            .header("Accept", "application/json")
            .header("X-Plugin-Version", pluginVersion)
            .header("User-Agent", "ForgeBingo-RuneLite/" + pluginVersion);
    }

    private <T> ApiResult<T> get(HttpUrl url, Class<T> type)
    {
        return execute(builder(url).get().build(), type);
    }

    private <T> ApiResult<T> execute(Request request, Class<T> type)
    {
        return execute(http, request, type);
    }

    private <T> ApiResult<T> execute(OkHttpClient client, Request request, Class<T> type)
    {
        try (Response r = client.newCall(request).execute())
        {
            long retryAfter = Backoff.parseRetryAfterMillis(r.header("Retry-After"));
            String etag = r.header("ETag");
            ResponseBody rb = r.body();
            String text = rb == null ? "" : readLimited(rb);
            if (r.isSuccessful())
            {
                T parsed = null;
                if (type != Void.class && !text.isEmpty())
                {
                    try
                    {
                        parsed = sanitize(gson.fromJson(text, type));
                    }
                    catch (JsonSyntaxException ex)
                    {
                        // 2xx with garbage: treat as transient so we do not lose the event on a bad proxy page.
                        return new ApiResult<>(0, null, "unreadable response", null, null, 0);
                    }
                }
                return new ApiResult<>(r.code(), parsed, null, null, etag, retryAfter);
            }
            String error = null;
            String code = null;
            try
            {
                ApiModels.ErrorBody eb = gson.fromJson(text, ApiModels.ErrorBody.class);
                if (eb != null)
                {
                    code = eb.error;
                    error = eb.message;
                }
            }
            catch (RuntimeException ignored)
            {
                // non-JSON error body
            }
            return new ApiResult<>(r.code(), null, error, code, etag, retryAfter);
        }
        catch (IOException ex)
        {
            return new ApiResult<>(0, null, ex.getClass().getSimpleName(), null, null, 0);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T sanitize(T v)
    {
        if (v instanceof ApiModels.BoardsResponse) return (T) Sanitizer.boards((ApiModels.BoardsResponse) v);
        if (v instanceof ApiModels.LeaderboardResponse) return (T) Sanitizer.leaderboard((ApiModels.LeaderboardResponse) v);
        if (v instanceof ApiModels.BoardResponse) return (T) Sanitizer.board((ApiModels.BoardResponse) v);
        if (v instanceof ApiModels.EventResult) return (T) Sanitizer.result((ApiModels.EventResult) v);
        if (v instanceof ApiModels.Me) return (T) Sanitizer.me((ApiModels.Me) v);
        if (v instanceof ApiModels.CatalogueResponse) return (T) Sanitizer.catalogue((ApiModels.CatalogueResponse) v);
        if (v instanceof ApiModels.HelloResponse) return (T) Sanitizer.hello((ApiModels.HelloResponse) v);
        return v;
    }

    private static String readLimited(ResponseBody rb) throws IOException
    {
        okio.BufferedSource source = rb.source();
        source.request(MAX_BODY_BYTES + 1);
        if (source.getBuffer().size() > MAX_BODY_BYTES)
        {
            throw new IOException("response too large");
        }
        return source.readUtf8();
    }
}
