package com.forgebingo;

import com.forgebingo.core.ActivityWatcher;
import com.forgebingo.core.ApiClient;
import com.forgebingo.core.ApiModels;
import com.forgebingo.core.ApiResult;
import com.forgebingo.core.BoardCelebrations;
import com.forgebingo.core.BoardSelection;
import com.forgebingo.core.ChatParsers;
import com.forgebingo.core.ConnectionStatus;
import com.forgebingo.core.ErrorClass;
import com.forgebingo.core.EventDeduplicator;
import com.forgebingo.core.GameEvent;
import com.forgebingo.core.InventoryDiff;
import com.forgebingo.core.ItemNames;
import com.forgebingo.core.MatchNotices;
import com.forgebingo.core.OfflineQueue;
import com.forgebingo.core.PetDetector;
import com.forgebingo.core.ProofPlan;
import com.forgebingo.core.ProofPolicy;
import com.forgebingo.core.QueueProcessor;
import com.forgebingo.core.QueuedEvent;
import com.forgebingo.core.Sanitizer;
import com.forgebingo.core.ScreenshotStamp;
import com.forgebingo.core.ScreenshotEncode;
import com.forgebingo.core.ScreenshotStack;
import com.forgebingo.core.WindowShot;
import com.forgebingo.core.SourceClassifier;
import com.forgebingo.core.TileInfo;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.TileItem;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.ImageUtil;
import okhttp3.OkHttpClient;

@PluginDescriptor(
    name = "ForgeBingo",
    internalName = "forgebingo",
    legacyDataDirectory = "forgebingo",
    description = "Sends your bingo-relevant drops, collection log, pets, KC and levels to ForgeBingo and shows your team board",
    tags = {"bingo", "clan", "team", "loot", "event", "collection log"}
)
@Slf4j
public class ForgeBingoPlugin extends Plugin
{
    static final String VERSION = "2.1.4";
    private static final long BOARD_POLL_SECONDS = 8;
    /** spot animation id of the level-up fireworks (the one the game plays at 99) */
    private static final int LEVEL_UP_FIREWORKS = 199;
    /** long enough to outlast a rate-limit pause (the server answers 429 with a 60 s wait) */
    private static final long SCREENSHOT_TTL_MILLIS = 10 * 60_000L;
    private static final int MAX_SCREENSHOTS = 8;
    /** gameval InventoryID.INV (the backpack). */
    private static final int INVENTORY_CONTAINER_ID = 93;
    /** worn equipment (InventoryID.WORN) */
    private static final int WORN_CONTAINER_ID = 94;
    private static final long CATALOGUE_REFRESH_MILLIS = 6 * 60 * 60_000L;
    private static final long CATALOGUE_RETRY_MILLIS = 5 * 60_000L;
    private static final int MAX_BOARD_ITEM_NAMES = 2_000;

    @Inject private Client client;
    @Inject private ForgeBingoConfig config;
    @Inject private ConfigManager configManager;
    @Inject private ClientThread clientThread;
    @Inject private ClientToolbar clientToolbar;
    @Inject private ChatMessageManager chatMessageManager;
    @Inject private ItemManager itemManager;
    @Inject private DrawManager drawManager;
    /**
     * Our own worker for every server call, picture encode and upload (2026-11-25). RuneLite's injected executor is
     * ONE thread shared by every plugin, and an upload can hold it for up to 90 s, stalling the others meanwhile.
     * Made in startUp, shut down in shutDown; anything handed to it after that is dropped (every task checks running).
     */
    private volatile ScheduledExecutorService executor = newWorker();

    private static ScheduledExecutorService newWorker()
    {
        java.util.concurrent.ScheduledThreadPoolExecutor w = new java.util.concurrent.ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "ForgeBingo worker");
            t.setDaemon(true);
            return t;
        }, new java.util.concurrent.ThreadPoolExecutor.DiscardPolicy());
        w.setRemoveOnCancelPolicy(true);
        return w;
    }
    @Inject private OkHttpClient okHttpClient;
    @Inject private OverlayManager overlayManager;
    @Inject private EffectsOverlay effects;
    @Inject private BoardOverlay boardOverlay;
    @Inject private MouseManager mouseManager;
    @Inject private KeyManager keyManager;
    @Inject private Gson gson;
    private BoardOverlayInput boardInput;

    private ApiClient api;
    private OfflineQueue queue;
    private QueueProcessor processor;
    private final EventDeduplicator dedup = new EventDeduplicator(3_000, 200);
    private final Random random = new Random();
    private final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
    private final Map<String, Screenshot> screenshots = new ConcurrentHashMap<>();
    /** Ids queued at startup (restart survivors): their proof screenshots lived only in memory, so a match counts without one. */
    private final java.util.Set<String> restoredNoProof = ConcurrentHashMap.newKeySet();
    private volatile boolean restoredNoProofNoticed;

    private ForgeBingoPanel panel;
    private NavigationButton navigationButton;
    private ScheduledFuture<?> queueFuture;
    private ScheduledFuture<?> boardFuture;
    /** teamBoardId -> updatedAt last rendered; the board is refetched when /v1/boards reports a change. */
    private final Map<String, String> boardVersions = new ConcurrentHashMap<>();
    /** tileId -> title of the board on screen, to name tiles in notices. */
    private volatile Map<String, String> tileTitles = new ConcurrentHashMap<>();
    /** hidden secret tiles on the board: chat never shows their amounts (owner, 2026-11-21: only the message shows) */
    private volatile java.util.Set<String> hiddenTiles = java.util.Collections.emptySet();
    /** teamBoardId -> tile ids seen completed, so a refresh celebrates only what is new (and a blackout only once). */
    private final Map<String, Set<String>> celebrated = new ConcurrentHashMap<>();
    private final Set<String> blackoutShown = ConcurrentHashMap.newKeySet();
    /** the board on screen, for the panel's tile detail view */
    private volatile ApiModels.BoardResponse currentBoard;
    private volatile int boardFailures;
    private volatile boolean automationNoticeShown;
    /** Pairs the "funny feeling" chat line with the pet it was about (core/PetDetector). Client thread only. */
    private final PetDetector petDetector = new PetDetector();
    /** Collection log item name -> id: server catalogue + this board's task items + the static pet table. */
    private final ItemNames itemNames = new ItemNames();
    private volatile long catalogueLoadedAt;
    private volatile long catalogueTriedAt;
    /** Inventory item counts at the last ItemContainerChanged (to see a pet arrive in the backpack). */
    private Map<Integer, Integer> lastInventory;
    /** Other containers (bank, equipment, storage...): an item leaving one into the backpack is a move, not a drop. */
    private final Map<Integer, Map<Integer, Integer>> lastContainers = new java.util.HashMap<>();
    private final ActivityWatcher activityWatcher = new ActivityWatcher();
    /** Spent at reward shops; their leaving the backpack does not make the reward a transformation. */
    private static final Set<Integer> CURRENCY_ITEMS = new HashSet<>(java.util.Arrays.asList(
        995,    // coins
        13204,  // platinum token
        6529,   // tokkul
        2140,   // castle wars ticket
        8951,   // pieces of eight (Trouble Brewing)
        12012,  // golden nugget (Motherlode Mine)
        2996,   // agility arena ticket
        21555,  // numulite
        25588   // Castle wars ticket (tradeable, newer)
    ));
    /** Item ids the board's tasks list: pickpocket loot is only sent when it holds one of them (blood shards...). */
    private volatile Set<Integer> boardItemIds = java.util.Collections.emptySet();
    private volatile boolean running;
    /** POST /v1/hello is due (login, key change, Refresh); sent from the client thread once the RSN is known. */
    private volatile boolean helloPending = true;
    private volatile long helloNotBefore;
    private volatile String lastIdentityNotice;
    /** bingos the last hello reported (null until the first one), to spot a team the player was just put on */
    private volatile java.util.Set<String> helloEvents;
    /** boards the last list had (null until the first list), to spot one that just opened (shared reveal) */
    private volatile java.util.Set<String> knownBoards;
    /** how many of the player's boards are live right now (a drop can count for one that is not selected) */
    private volatile int liveBoards;
    /**
     * The team board on screen (null before the first one shows). The saved choice (config activeBoardId) stays empty
     * until the player picks a board, while the first live board is shown anyway: this is that board.
     */
    private volatile String selectedBoardId;
    /** counts key / address changes: a board list fetched for the old key is thrown away, not compared with the new */
    private final java.util.concurrent.atomic.AtomicInteger boardEpoch = new java.util.concurrent.atomic.AtomicInteger();
    /** own finished tiles celebrated on boards not looked at yet in this session (once per tile) */
    private final Set<String> ownCelebrated = ConcurrentHashMap.newKeySet();
    /** guards queueFuture: drains are scheduled from the client thread, the UI thread and the worker */
    private final Object queueLock = new Object();

    /**
     * A drop screenshot waiting for the server's answer. Kept unstamped: one drop can count for several teams in
     * different bingos, and each team's copy is stamped with that team's own code and tile names when it is sent.
     */
    private static final class Screenshot
    {
        /** the frames, in the order they were asked for (a chest's loot window is taken twice); gaps are null */
        final byte[][] raw;
        final java.time.ZonedDateTime[] taken;
        final long at;
        volatile String player;

        Screenshot(int shots, long at)
        {
            this.raw = new byte[shots][];
            this.taken = new java.time.ZonedDateTime[shots];
            this.at = at;
        }
    }

    @Provides
    ForgeBingoConfig provideConfig(ConfigManager manager)
    {
        return manager.getConfig(ForgeBingoConfig.class);
    }

    @Override
    protected void startUp()
    {
        if (executor.isShutdown()) executor = newWorker();
        running = true;
        // an address saved by an early build was the placeholder: drop it so the live default applies
        if (ApiClient.isPlaceholder(config.apiBaseUrl()))
        {
            configManager.unsetConfiguration(ForgeBingoConfig.GROUP, "apiBaseUrl");
        }
        api = new ApiClient(okHttpClient, gson, this::effectiveBaseUrl, config::apiKey, VERSION);
        // saved by the worker (flushQueue), never on the game thread
        queue = new OfflineQueue(queueFile(), gson, true);
        queue.load();
        // these survived a restart, so their in-memory proof screenshots are gone (see maybeUploadScreenshot)
        restoredNoProof.clear();
        restoredNoProof.addAll(queue.ids());
        restoredNoProofNoticed = false;
        processor = new QueueProcessor(queue, api::sendEvent, new QueueListener(), System::currentTimeMillis, random);

        SwingUtilities.invokeLater(() -> {
            if (!running)
            {
                return;
            }
            ForgeBingoPanel.usePixelFonts(config.panelFont() == ForgeBingoConfig.PanelFont.RUNESCAPE);
            PanelUi.animations = config.panelAnimations();
            ForgeBingoPanel.useTheme(config.panelTheme());
            PanelUi.effects = config.panelEffects();
            panel = new ForgeBingoPanel(this, api);
            applyEffects(panel);
            BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
            navigationButton = NavigationButton.builder()
                .tooltip("ForgeBingo")
                .icon(icon)
                .priority(7)
                .panel(panel)
                .build();
            // Deliberately NOT opened automatically.
            clientToolbar.addNavigation(navigationButton);
            panel.setQueueSize(queue.size(), null);
        });
        effects.hideWhile = this::takingProof;
        overlayManager.add(effects);
        overlayManager.add(boardOverlay);
        boardInput = new BoardOverlayInput(client, boardOverlay, config);
        boardInput.register(mouseManager, keyManager);
        connect();
        scheduleQueueDrain(2_000);
        boardFuture = executor.scheduleWithFixedDelay(this::pollBoardSafely, 5, BOARD_POLL_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    protected void shutDown()
    {
        running = false;
        synchronized (queueLock)
        {
            if (queueFuture != null) queueFuture.cancel(false);
            queueFuture = null;
        }
        if (boardFuture != null) boardFuture.cancel(false);
        // whatever the worker has not written yet (an event added a moment ago) goes to disk before the queue is let go
        flushQueue(queue);
        // what is already queued still runs (each task checks running); nothing new is taken after this
        executor.shutdown();
        overlayManager.remove(effects);
        effects.clear();
        if (boardInput != null) boardInput.unregister(mouseManager, keyManager);
        boardInput = null;
        overlayManager.remove(boardOverlay);
        boardOverlay.reset();
        celebrated.clear();
        blackoutShown.clear();
        currentBoard = null;
        boardVersions.clear(); // or the next start would think the board is already loaded and never show it
        namedIds = null;
        knownBoards = null;
        liveBoards = 0;
        selectedBoardId = null;
        ownCelebrated.clear();
        helloEvents = null;
        archiveChests.reset();
        wasInstance = false;
        raidShot = null;
        raidKcResult = null;
        if (navigationButton != null) clientToolbar.removeNavigation(navigationButton);
        navigationButton = null;
        ForgeBingoPanel closingPanel = panel;
        if (closingPanel != null) SwingUtilities.invokeLater(closingPanel::dispose);
        panel = null;
        screenshots.clear();
        restoredNoProof.clear();
        restoredNoProofNoticed = false;
        proofRetries.clear();
        frameJobs.clear();
        shotsInProgress.clear();
        proofFramesPending.set(0);
        dedup.clear();
        // these belong to the client thread (its event handlers read and write them): reset there, not from here
        clientThread.invoke(() -> {
            if (running) return; // switched back on meanwhile: the new session's state stays
            levels.clear();
            petDetector.reset();
            lastInventory = null;
            lastContainers.clear();
        });
        api = null;
        processor = null;
        queue = null;
    }

    // ---------- server address check ----------

    /** host of a non-ForgeBingo address waiting for the player's OK, else null */
    private volatile String pendingHost;
    /** host the warning was already shown for (once per address) */
    private volatile String askedHost;

    /**
     * The API address to use. Anything other than the ForgeBingo server (or localhost for development) is only used
     * after the player approves it in a warning naming the host: a pasted scam address would otherwise receive their
     * key, RuneScape name, drops and screenshots. Until then nothing is sent.
     */
    String effectiveBaseUrl()
    {
        String raw = config.apiBaseUrl();
        String trusted = config.trustedApiHost();
        // asked for on every event and hello: the address is only parsed again when a setting changed
        BaseUrlCheck c = baseUrlCheck;
        if (c == null || !java.util.Objects.equals(c.raw, raw) || !java.util.Objects.equals(c.trusted, trusted))
        {
            c = BaseUrlCheck.of(raw, trusted);
            baseUrlCheck = c;
        }
        if (c.host == null) return raw;
        if (c.allowed)
        {
            pendingHost = null;
            return raw;
        }
        pendingHost = c.host;
        askToTrust(c.host);
        return null;
    }

    /** The verdict on one pair of settings (API address, approved host), kept until either changes. */
    private static final class BaseUrlCheck
    {
        final String raw;
        final String trusted;
        /** the address's host, or null when it (or the built-in default) is not a usable address */
        final String host;
        /** the ForgeBingo server, localhost, or the host the player approved */
        final boolean allowed;

        private BaseUrlCheck(String raw, String trusted, String host, boolean allowed)
        {
            this.raw = raw;
            this.trusted = trusted;
            this.host = host;
            this.allowed = allowed;
        }

        static BaseUrlCheck of(String raw, String trusted)
        {
            okhttp3.HttpUrl u = ApiClient.parseBaseUrl(raw);
            okhttp3.HttpUrl def = ApiClient.parseBaseUrl(ForgeBingoConfig.DEFAULT_BASE_URL);
            if (u == null || def == null) return new BaseUrlCheck(raw, trusted, null, false);
            String host = u.host();
            boolean allowed = host.equals(def.host()) || "localhost".equals(host) || "127.0.0.1".equals(host)
                || (trusted != null && host.equalsIgnoreCase(trusted.trim()));
            return new BaseUrlCheck(raw, trusted, host, allowed);
        }
    }

    private volatile BaseUrlCheck baseUrlCheck;

    /** The offline queue lives in .runelite/plugin-data/forgebingo (RuneLite's Filepath API, moved from .runelite/forgebingo). */
    private Filepath queueFile()
    {
        try
        {
            return getPluginDirectory().joinSegment("queue.json");
        }
        catch (IOException ex)
        {
            throw new UncheckedIOException("ForgeBingo could not open its data folder", ex);
        }
    }

    private void askToTrust(String host)
    {
        if (host.equals(askedHost)) return;
        askedHost = host;
        SwingUtilities.invokeLater(() -> {
            int answer = javax.swing.JOptionPane.showConfirmDialog(panel,
                "<html><body style='width:280px'>The ForgeBingo API address points to <b>" + host.replace("<", "") + "</b>, not the ForgeBingo server.<br><br>"
                    + "If you continue, that site gets your API key, RuneScape name, drops and proof screenshots.<br><br>"
                    + "Only say Yes if your organizer told you to use it. Otherwise click No and press Reset on the API base URL.</body></html>",
                "ForgeBingo: unknown server", javax.swing.JOptionPane.YES_NO_OPTION, javax.swing.JOptionPane.WARNING_MESSAGE);
            if (answer == javax.swing.JOptionPane.YES_OPTION) configManager.setConfiguration(ForgeBingoConfig.GROUP, "trustedApiHost", host);
        });
    }

    // ---------- connection / panel actions ----------

    void connect()
    {
        executor.execute(() -> {
            if (!running || api == null) return;
            if (!api.configured())
            {
                String why = pendingHost != null ? "Approve the server address " + pendingHost + " first (or reset API base URL)"
                    : config.apiKey().trim().isEmpty() ? "Paste your API key in the ForgeBingo settings" : "Check the API base URL in settings";
                notifyPanel(p -> p.setStatus(false, why));
                return;
            }
            ApiResult<ApiModels.Me> me = api.me();
            if (me.ok() && me.body != null)
            {
                notifyPanel(p -> {
                    p.setStatus(true, "Connected");
                    p.setScope((me.body.displayName == null ? "" : "ForgeBingo account: " + me.body.displayName + "\n") + ConnectionStatus.keyScope(me.body));
                });
                helloPending = true;
                helloNotBefore = 0;
                helloEvents = null; // the hello this triggers asks for the board again (it can put the player on a team)
                boardFailures = 0;  // a fresh key or Refresh never waits out the back-off of earlier failures
                catalogueTriedAt = 0;
                loadCatalogue();
                pollBoardSafely();
            }
            else if (me.status == 401)
            {
                String msg = ConnectionStatus.authMessage(me.errorCode);
                notifyPanel(p -> {
                    p.setStatus(false, "api_key_revoked".equals(me.errorCode) ? "Not connected: key stopped working" : "Not connected: key not recognised");
                    p.setScope("");
                    p.setIdentity(java.util.Collections.singletonList(new ConnectionStatus.Line(ConnectionStatus.Level.ERROR, msg)));
                });
            }
            else
            {
                notifyPanel(p -> p.setStatus(false, me.describe()));
            }
        });
    }

    /**
     * The "Theme effects" level on top of the creature and item-rain switches: Calm keeps the creatures (at half
     * speed) and drops what falls and flows; Off leaves the colours only.
     */
    private void applyEffects(ForgeBingoPanel p)
    {
        ForgeBingoConfig.PanelEffects level = config.panelEffects();
        p.setEffects(level);
        p.setImps(config.panelImps() && level != ForgeBingoConfig.PanelEffects.OFF);
        p.setRain(config.panelRain() && level == ForgeBingoConfig.PanelEffects.FULL);
    }

    /** The side panel's own theme picker. */
    void choosePanelTheme(PanelTheme theme)
    {
        if (configManager != null && theme != null) configManager.setConfiguration(ForgeBingoConfig.GROUP, "panelTheme", theme);
    }

    /** The side panel's own effects picker. */
    void choosePanelEffects(ForgeBingoConfig.PanelEffects level)
    {
        if (configManager != null && level != null) configManager.setConfiguration(ForgeBingoConfig.GROUP, "panelEffects", level);
    }

    /** the last few things that counted, shown as one line in the side panel */
    private final com.forgebingo.core.ActivityLog activity = new com.forgebingo.core.ActivityLog();

    private void showActivity()
    {
        long now = System.currentTimeMillis();
        String newest = activity.newest(now);
        List<String> lines = activity.lines(now);
        notifyPanel(p -> p.setActivity(newest, lines));
    }

    /** Client thread: once logged in, report the RSN + account hash (POST /v1/hello) so the server can verify the name. */
    private void maybeHello()
    {
        if (!helloPending || System.currentTimeMillis() < helloNotBefore || api == null || !api.configured())
        {
            return;
        }
        Player me = client.getLocalPlayer();
        long hash = client.getAccountHash();
        String rsn = me == null ? null : Sanitizer.normalizeRsn(me.getName());
        if (!Sanitizer.validRsn(rsn) || hash == -1)
        {
            return;
        }
        helloPending = false;
        String accountHash = Long.toString(hash);
        executor.execute(() -> sendHello(rsn, accountHash));
    }

    /**
     * Wise Old Man connector: after a session (logout / world hop) ask ForgeBingo to refresh this player's WOM stats, so
     * XP / boss KC / EHP tiles catch up straight away. The server queues it for bingos with WOM tracking on and rate
     * limits it. A server without the connector answers 404: then we stop asking for this session.
     */
    private void requestWomUpdate()
    {
        String rsn = lastHelloRsn;
        ApiClient a = api;
        if (!config.womUpdateOnLogout() || womUnavailable || rsn == null || a == null || !a.configured()) return;
        executor.execute(() -> {
            ApiResult<ApiModels.WomUpdateResponse> r = a.womUpdate(rsn);
            if (r.status == 404 || r.status == 501) womUnavailable = true;
            else if (!r.ok()) log.debug("wom update failed: {}", r.describe());
        });
    }

    /** the name the last hello was sent for (the player's RSN) */
    private volatile String lastHelloRsn;
    /** this server has no Wise Old Man connector: don't keep asking */
    private volatile boolean womUnavailable;

    private void sendHello(String rsn, String accountHash)
    {
        lastHelloRsn = rsn;
        ApiClient a = api;
        if (!running || a == null)
        {
            return;
        }
        ApiResult<ApiModels.HelloResponse> r = a.hello(rsn, accountHash);
        if (r.ok() && r.body != null)
        {
            List<ConnectionStatus.Line> lines = new ArrayList<>(ConnectionStatus.fromHello(r.body));
            // the server knows the newest version players can get: say so when this one is behind
            if (com.forgebingo.core.Versions.newer(r.body.latestPluginVersion, VERSION))
            {
                lines.add(new ConnectionStatus.Line(ConnectionStatus.Level.WARNING,
                    "A newer ForgeBingo plugin is out (" + r.body.latestPluginVersion + ", you have " + VERSION + "). Restart RuneLite to update."));
            }
            notifyPanel(p -> p.setIdentity(lines));
            // Logging in can put the player on a team (the organizer listed their RSN): fetch the boards straight
            // away when the server reports a bingo we did not know about, so the new board shows without a wait.
            java.util.Set<String> now = new java.util.HashSet<>();
            if (r.body.memberships != null) for (ApiModels.Membership m : r.body.memberships) if (m != null && m.eventId != null) now.add(m.eventId);
            // The first hello after connecting (a key just pasted, Refresh, a login) can itself put the player on a team,
            // and the board was asked for a moment before it: ask again now instead of waiting (owner, 2026-11-14).
            boolean firstHello = helloEvents == null;
            boolean newBingo = !now.isEmpty() && (firstHello || !helloEvents.containsAll(now));
            helloEvents = now;
            // bingos whose board hasn't opened yet: the side panel counts down to their start
            List<ApiModels.Membership> waiting = new ArrayList<>();
            if (r.body.memberships != null) for (ApiModels.Membership m : r.body.memberships) if (m != null && Boolean.FALSE.equals(m.boardOpen)) waiting.add(m);
            notifyPanel(p -> p.setUpcoming(waiting));
            if (newBingo)
            {
                if (!firstHello) notice("You were added to a new bingo board.", false);
                boardVersions.clear();
                boardFailures = 0;
                pollBoardSafely();
            }
            for (ConnectionStatus.Line l : lines)
            {
                if (l.level == ConnectionStatus.Level.ERROR && !l.text.equals(lastIdentityNotice))
                {
                    lastIdentityNotice = l.text;
                    notice(l.text, true);
                }
            }
        }
        else if (r.status == 401)
        {
            String msg = ConnectionStatus.authMessage(r.errorCode);
            notifyPanel(p -> p.setIdentity(java.util.Collections.singletonList(new ConnectionStatus.Line(ConnectionStatus.Level.ERROR, msg))));
        }
        else if (r.errorClass() == ErrorClass.TRANSIENT)
        {
            helloNotBefore = System.currentTimeMillis() + 60_000;
            helloPending = true;
        }
        else
        {
            log.debug("hello failed: {}", r.describe());
        }
    }

    /** Fetches a proxied icon off the EDT and hands the bytes to the EDT. */
    /** Uploaded tile icons from the server, shared by the side panel and the board overlay. */
    private final Map<String, BufferedImage> tileImages = new ConcurrentHashMap<>();
    /** Paths that came back empty: never asked again this session (they used to be re-asked on every refresh). */
    private final java.util.Set<String> tileImageMisses = ConcurrentHashMap.newKeySet();
    private final java.util.Set<String> tileImageLoading = ConcurrentHashMap.newKeySet();

    /**
     * A tile's uploaded icon (/v1/icons/tiles/...), fetched once and cached. Returns null while it loads (the panel
     * is told to redraw when it arrives) or when there is none (see {@link #tileImageMissing}). Any thread.
     */
    /** An item's inventory icon from the client (it fills in by itself once loaded). */
    BufferedImage itemImage(int itemId)
    {
        return itemManager == null ? null : itemManager.getImage(itemId);
    }

    BufferedImage tileImage(String proxyPath)
    {
        if (proxyPath == null || !com.forgebingo.core.IconRef.isProxyPath(proxyPath)) return null;
        BufferedImage img = tileImages.get(proxyPath);
        if (img != null || tileImageMisses.contains(proxyPath) || tileImages.size() > 300 || !tileImageLoading.add(proxyPath)) return img;
        executor.execute(() -> {
            BufferedImage got = null;
            try
            {
                ApiClient a = api;
                byte[] bytes = a == null ? null : a.icon(proxyPath);
                if (bytes != null)
                {
                    got = readSmallImage(bytes);
                }
            }
            catch (java.io.IOException | RuntimeException ex)
            {
                log.debug("tile icon failed", ex);
            }
            finally
            {
                if (got != null) tileImages.put(proxyPath, got); else tileImageMisses.add(proxyPath);
                tileImageLoading.remove(proxyPath);
            }
            notifyPanel(ForgeBingoPanel::iconsChanged);
        });
        return null;
    }

    boolean tileImageMissing(String proxyPath)
    {
        return proxyPath == null || tileImageMisses.contains(proxyPath);
    }

    // ---------- "working on" tiles (this player's own list, kept in their RuneLite settings) ----------

    java.util.Set<String> workingOn()
    {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        String raw = config.workingOn();
        if (raw == null) return out;
        for (String s : raw.split(","))
        {
            String id = s.trim();
            if (!id.isEmpty() && id.length() <= 40 && out.size() < 10) out.add(id);
        }
        return out;
    }

    void setWorkingOn(String tileId, boolean on)
    {
        if (tileId == null) return;
        java.util.Set<String> ids = workingOn();
        if (on) ids.add(tileId); else ids.remove(tileId);
        configManager.setConfiguration(ForgeBingoConfig.GROUP, "workingOn", String.join(",", ids));
        // tell the server so the whole team sees it (servers before 2026-11-11 answer 404: the local list still works)
        String team = shownBoardId();
        ApiClient a = api;
        if (team == null || team.isEmpty() || a == null) return;
        executor.execute(() -> {
            ApiResult<Object> r = a.setWorkingOn(team, tileId, on);
            if (r.ok())
            {
                boardVersions.clear();
                pollBoardSafely();
            }
            else if (r.status != 404)
            {
                notice("Couldn't update Working on: " + r.describe(), false);
            }
        });
    }

    /**
     * The team board on screen: the one the last poll showed, else the saved choice. The saved choice alone is not
     * enough: it is empty until the player picks a board, while the first live board is shown without a pick.
     */
    private String shownBoardId()
    {
        String shown = selectedBoardId;
        return shown != null && !shown.isEmpty() ? shown : config.activeBoardId();
    }

    // ---------- "Rejected" lines in the side panel: which ones this player has seen and dismissed, per team board ----------

    private static String rejectedKey(String teamBoardId)
    {
        if (teamBoardId == null || teamBoardId.isEmpty() || teamBoardId.length() > 64) return null;
        for (int i = 0; i < teamBoardId.length(); i++)
        {
            char c = teamBoardId.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '-' || c == '_')) return null;
        }
        return "rejected." + teamBoardId;
    }

    /** What RejectedNotices.Log saved for this board ("" when nothing yet). */
    String rejectedMemory(String teamBoardId)
    {
        String key = rejectedKey(teamBoardId);
        if (key == null || configManager == null) return "";
        String v = configManager.getConfiguration(ForgeBingoConfig.GROUP, key);
        return v == null ? "" : v;
    }

    void setRejectedMemory(String teamBoardId, String value)
    {
        String key = rejectedKey(teamBoardId);
        if (key == null || configManager == null) return;
        if (value == null || value.isEmpty()) configManager.unsetConfiguration(ForgeBingoConfig.GROUP, key);
        else configManager.setConfiguration(ForgeBingoConfig.GROUP, key, value);
    }

    boolean panelHeaderCollapsed()
    {
        return config.panelHeaderCollapsed();
    }

    void setPanelHeaderCollapsed(boolean collapsed)
    {
        configManager.setConfiguration(ForgeBingoConfig.GROUP, "panelHeaderCollapsed", collapsed);
    }

    /** Is the player working on this tile? The team list from the server when it sends one, else the local list. */
    boolean isWorkingOn(ApiModels.Tile t)
    {
        if (t == null || t.id == null) return false;
        if (t.workingOn != null)
        {
            for (ApiModels.WorkingOn w : t.workingOn) if (w.me) return true;
            return false;
        }
        return workingOn().contains(t.id);
    }

    void selectBoard(String teamBoardId)
    {
        configManager.setConfiguration(ForgeBingoConfig.GROUP, "activeBoardId", teamBoardId == null ? "" : teamBoardId);
        boardVersions.clear();
        executor.execute(this::pollBoardSafely);
    }

    void refreshNow()
    {
        boardVersions.clear();
        tileImageMisses.clear(); // Refresh retries icons that failed (e.g. while rate limited)
        connect();
        if (processor != null)
        {
            processor.resumeAfterAuthChange();
            scheduleQueueDrain(0);
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged e)
    {
        if (!ForgeBingoConfig.GROUP.equals(e.getGroup()))
        {
            return;
        }
        if ("panelFont".equals(e.getKey()))
        {
            boolean pixel = config.panelFont() == ForgeBingoConfig.PanelFont.RUNESCAPE;
            ForgeBingoPanel p = panel;
            if (p != null) javax.swing.SwingUtilities.invokeLater(() -> p.setPixelFonts(pixel));
            else ForgeBingoPanel.usePixelFonts(pixel);
            return;
        }
        if ("panelTheme".equals(e.getKey()))
        {
            PanelTheme theme = config.panelTheme();
            ForgeBingoConfig.PanelEffects level = config.panelEffects();
            ForgeBingoPanel p = panel;
            if (p != null) javax.swing.SwingUtilities.invokeLater(() -> {
                p.setTheme(theme);
                p.showLook(theme, level);
            });
            else ForgeBingoPanel.useTheme(theme);
            return;
        }
        if ("panelEffects".equals(e.getKey()))
        {
            PanelTheme theme = config.panelTheme();
            ForgeBingoConfig.PanelEffects level = config.panelEffects();
            ForgeBingoPanel p = panel;
            if (p != null) javax.swing.SwingUtilities.invokeLater(() -> {
                applyEffects(p);
                p.showLook(theme, level);
            });
            else PanelUi.effects = level;
            return;
        }
        if ("panelAnimations".equals(e.getKey()) || "panelImps".equals(e.getKey()) || "panelRain".equals(e.getKey()))
        {
            boolean animate = config.panelAnimations();
            ForgeBingoPanel p = panel;
            if (p != null) javax.swing.SwingUtilities.invokeLater(() -> {
                p.setAnimations(animate);
                applyEffects(p);
            });
            return;
        }
        if ("apiBaseUrl".equals(e.getKey())) askedHost = null; // ask again for a newly typed address
        if ("apiKey".equals(e.getKey()) || "apiBaseUrl".equals(e.getKey()) || "trustedApiHost".equals(e.getKey()))
        {
            // another key (or server) has other boards: forget the old list, or its live board would look newly
            // opened ("The bingo is live!", the reveal, the saved board choice overwritten)
            boardEpoch.incrementAndGet();
            knownBoards = null;
            liveBoards = 0;
            selectedBoardId = null;
            boardVersions.clear();
            if (processor != null) processor.resumeAfterAuthChange();
            connect();
            scheduleQueueDrain(1_000);
        }
    }

    // ---------- item names (collection log lines carry names only) ----------

    /** GET /v1/catalogue/items, at most every 5 min on failure and every 6 h on success. Executor thread. */
    private void loadCatalogue()
    {
        ApiClient a = api;
        long now = System.currentTimeMillis();
        if (a == null || !a.configured() || now - catalogueTriedAt < CATALOGUE_RETRY_MILLIS
            || (catalogueLoadedAt > 0 && now - catalogueLoadedAt < CATALOGUE_REFRESH_MILLIS))
        {
            return;
        }
        catalogueTriedAt = now;
        ApiResult<ApiModels.CatalogueResponse> r = a.catalogueItems();
        if (r.ok() && r.body != null)
        {
            itemNames.setCatalogue(r.body.items);
            catalogueLoadedAt = now;
        }
        else
        {
            log.debug("item catalogue unavailable: {}", r.describe());
        }
    }

    /** Names of every item the board's tasks ask for, from the item definitions (works for untradeables too). */
    private void indexBoardItems(ApiModels.BoardResponse board)
    {
        Set<Integer> ids = new LinkedHashSet<>();
        for (ApiModels.Tile t : board.tiles)
        {
            if (t.tasks == null) continue;
            for (ApiModels.Task k : t.tasks)
            {
                if (k.itemIds == null) continue;
                for (Integer id : k.itemIds)
                {
                    if (id != null && id >= 0 && ids.size() < MAX_BOARD_ITEM_NAMES) ids.add(id);
                }
            }
        }
        Set<Integer> all = new HashSet<>(ids);
        if (board.hiddenItemIds != null) all.addAll(board.hiddenItemIds); // secret tiles: ids only, never named in the panel
        boardItemIds = java.util.Collections.unmodifiableSet(all);
        // Every board refresh used to look up all of these (up to 2,000) in one go on the client thread, which froze
        // the game for a moment on big boards (2026-11-25). Same items as last time: nothing to do. Otherwise a few
        // per frame, and a newer board's lookup replaces one still in progress.
        if (ids.equals(namedIds)) return;
        namedIds = ids;
        final List<Integer> todo = new ArrayList<>(ids);
        final List<ApiModels.CatalogueItem> named = new ArrayList<>();
        final int[] next = {0};
        clientThread.invoke(() -> {
            if (!running || namedIds != ids) return true; // switched off, or a newer board took over
            int end = Math.min(todo.size(), next[0] + ITEM_NAMES_PER_FRAME);
            for (int n = next[0]; n < end; n++)
            {
                int id = todo.get(n);
                try
                {
                    String name = itemManager.getItemComposition(id).getName();
                    if (name != null && !name.isEmpty() && !"null".equalsIgnoreCase(name)) named.add(new ApiModels.CatalogueItem(id, name));
                }
                catch (RuntimeException ex)
                {
                    log.debug("no item definition for {}", id);
                }
            }
            next[0] = end;
            if (end < todo.size()) return false; // the rest on the next frame
            itemNames.setBoard(named);
            notifyPanel(ForgeBingoPanel::iconsChanged); // the per-item lists can show real names now
            return true;
        });
    }

    /** item names looked up per client frame (see indexBoardItems) */
    static final int ITEM_NAMES_PER_FRAME = 50;
    /** the item ids whose names were last looked up; a refresh with the same ones skips the lookup */
    private volatile Set<Integer> namedIds;

    /** An item's name for the panel and overlay ("Item 526" until the names load). Any thread. */
    String itemName(int itemId)
    {
        return itemNames.name(itemId);
    }

    // ---------- board polling ----------

    private void pollBoardSafely()
    {
        try
        {
            pollBoard();
        }
        catch (RuntimeException ex)
        {
            log.debug("board poll failed", ex);
        }
    }

    /** which poll last failed ("boards" or "board"), so the panel goes back to "Connected" once it works again */
    private volatile String pollProblem;

    private void pollRecovered()
    {
        pollProblem = null;
        notifyPanel(p -> p.setStatus(true, "Connected"));
    }

    /** GET /v1/boards (cheap, every 10 s), then refetch the selected board only when its updatedAt changed. */
    private void pollBoard()
    {
        ApiClient a = api;
        if (!running || a == null || !a.configured())
        {
            return;
        }
        // Back off when failing: skip polls.
        if (boardFailures > 0 && random.nextInt(1 << Math.min(boardFailures, 4)) != 0)
        {
            return;
        }
        loadCatalogue();
        int epoch = boardEpoch.get();
        ApiResult<ApiModels.BoardsResponse> list = a.boards();
        // the key or address changed while this was on its way: the answer is for the old one
        if (epoch != boardEpoch.get()) return;
        if (!list.ok() || list.body == null)
        {
            boardFailures++;
            pollProblem = "boards";
            notifyPanel(p -> p.setStatus(false, "Boards: " + list.describe()));
            return;
        }
        boardFailures = 0;
        if ("boards".equals(pollProblem)) pollRecovered();
        List<ApiModels.BoardSummary> boards = list.body.boards;
        // Shared reveal: a board that appears while we're running has just opened for everyone (the bingo started).
        // Switch to it, say so in chat and open it over the game with a Legendary animation.
        liveBoards = BoardSelection.liveCount(boards);
        java.util.Set<String> before = knownBoards;
        knownBoards = BoardSelection.ids(boards);
        if (epoch != boardEpoch.get())
        {
            // the key changed in this very moment: this list must not be what the new key's boards are compared with
            knownBoards = null;
            return;
        }
        ApiModels.BoardSummary opened = BoardSelection.newlyOpened(boards, before);
        if (opened != null)
        {
            notice("The bingo is live! " + (opened.eventName == null ? "Your board" : opened.eventName) + " is open. Good luck!", true);
            configManager.setConfiguration(ForgeBingoConfig.GROUP, "activeBoardId", opened.teamBoardId);
            boardVersions.clear();
            String ev = opened.eventId;
            notifyPanel(p -> p.removeUpcoming(ev));
            if (config.boardOverlayAnimations()) boardOverlay.openReveal();
        }
        final ApiModels.BoardSummary sel = BoardSelection.select(boards, config.activeBoardId());
        notifyPanel(p -> p.setBoards(boards, sel == null ? null : sel.teamBoardId));
        if (sel == null)
        {
            selectedBoardId = null;
            return;
        }
        String version = sel.updatedAt == null ? "?" : sel.updatedAt;
        if (version.equals(boardVersions.get(sel.teamBoardId)))
        {
            selectedBoardId = sel.teamBoardId;
            return;
        }
        ApiResult<ApiModels.BoardResponse> r = a.board(sel.teamBoardId);
        if (epoch != boardEpoch.get()) return;
        if (!r.ok() || r.body == null)
        {
            boardFailures++;
            pollProblem = "board";
            notifyPanel(p -> p.setStatus(false, "Board: " + r.describe()));
            return;
        }
        if (pollProblem != null) pollRecovered();
        ApiResult<ApiModels.LeaderboardResponse> lb = a.leaderboard(sel.teamBoardId);
        if (epoch != boardEpoch.get()) return;
        boardVersions.put(sel.teamBoardId, version);
        Map<String, String> titles = new ConcurrentHashMap<>();
        java.util.Set<String> hidden = ConcurrentHashMap.newKeySet();
        for (ApiModels.Tile t : r.body.tiles)
        {
            if (t.id == null) continue;
            // a hidden secret tile goes by the name its organizer gave it ("Secret tile" when none), never "?"
            String shown = TileInfo.shownTitle(t);
            if (!shown.isEmpty()) titles.put(t.id, shown);
            if (TileInfo.isSecret(t) && !t.completed) hidden.add(t.id);
        }
        tileTitles = titles;
        hiddenTiles = hidden;
        indexBoardItems(r.body);
        currentBoard = r.body;
        selectedBoardId = sel.teamBoardId;
        celebrateChanges(sel.teamBoardId, r.body);
        notifyPanel(p -> p.setBoard(r.body, lb.ok() ? lb.body : null, itemManager));
    }

    // ---------- celebrations (mirror the website: tile banner + fireworks, line sweep, BLACKOUT show) ----------

    /** Compares the freshly fetched board with what was celebrated before; the first fetch only records. */
    private void celebrateChanges(String teamBoardId, ApiModels.BoardResponse board)
    {
        Set<String> seen = celebrated.get(teamBoardId);
        BoardCelebrations.Change change = BoardCelebrations.changes(seen, board);
        if (seen == null)
        {
            // first look at this board in this session: no replay of old history, but a finished board still gets its show
            celebrated.put(teamBoardId, ConcurrentHashMap.newKeySet());
            celebrated.get(teamBoardId).addAll(change.done);
            if (change.all && config.effects() && blackoutShown.add(teamBoardId)) effects.push(EffectsOverlay.Kind.BLACKOUT, "");
            return;
        }
        seen.addAll(change.done);
        if (!config.effects()) return;
        // Newly finished tiles found on a refresh were finished by a teammate (your own drops celebrate the moment the
        // server answers, which marks them seen first): no on-screen banner, a fireworks card in the side panel instead.
        for (String title : change.finishedTitles) notifyPanel(p -> p.teamFinished(title));
        if (change.all && blackoutShown.add(teamBoardId)) effects.push(EffectsOverlay.Kind.BLACKOUT, "");
        else if (change.newLines > 0) effects.push(EffectsOverlay.Kind.LINE, change.lineText());
    }

    /** Banner over the game plus the level-up fireworks on the player, once per tile. */
    private void celebrateTile(String teamBoardId, String tileId, String title)
    {
        // Marked seen on its own board, so the next refresh does not announce it again as a teammate's. A board not
        // looked at yet in this session has no list to mark (its first look records everything already finished).
        Set<String> seen = teamBoardId == null ? null : celebrated.get(teamBoardId);
        boolean first = seen != null ? seen.add(tileId) : ownCelebrated.add(tileId);
        if (!first || !config.effects()) return;
        effects.push(EffectsOverlay.Kind.TILE, title == null ? "" : title);
        clientThread.invoke(() -> {
            Player me = client.getLocalPlayer();
            if (me != null && client.getGameState() == GameState.LOGGED_IN) me.createSpotAnim(LEVEL_UP_FIREWORKS, 0, 0, 0);
        });
    }

    /** Panel button: open or close the board over the game. Any thread. */
    void toggleBoardOverlay()
    {
        boardOverlay.toggle();
    }

    /** Taking damage closes the board overlay so the player can react (config "Close when attacked"). */
    @Subscribe
    public void onHitsplatApplied(HitsplatApplied e)
    {
        if (!boardOverlay.isOpen() || !config.boardOverlayCloseOnHit()) return;
        if (e.getActor() != null && e.getActor() == client.getLocalPlayer() && e.getHitsplat() != null && e.getHitsplat().getAmount() > 0)
        {
            boardOverlay.close();
        }
    }

    /** The current board for the panel's tile detail view (may be null before the first fetch). */
    ApiModels.BoardResponse currentBoard()
    {
        return currentBoard;
    }

    private void notifyPanel(java.util.function.Consumer<ForgeBingoPanel> action)
    {
        SwingUtilities.invokeLater(() -> {
            ForgeBingoPanel p = panel;
            if (p != null) action.accept(p);
        });
    }

    // ---------- event handlers (client thread) ----------

    @Subscribe
    public void onNpcLootReceived(NpcLootReceived e)
    {
        NPC npc = e.getNpc();
        if (!config.sendNpcLoot() || npc == null) return;
        GameEvent ev = base(GameEvent.NPC);
        petDetector.onSource(npc.getName(), client.getTickCount());
        ev.npcId = npc.getId();
        ev.npcIndex = npc.getIndex();
        ev.npcName = npc.getName();
        // only what the monster itself dropped: not an item the player just dropped there, nor another player's item
        int tick = client.getTickCount();
        net.runelite.api.coords.WorldPoint at = npc.getWorldLocation();
        int size = npc.getComposition() == null ? 1 : npc.getComposition().getSize();
        List<ItemStack> own = new ArrayList<>();
        if (e.getItems() != null)
        {
            for (ItemStack s : e.getItems())
            {
                int id = itemManager.canonicalize(s.getId());
                boolean keep = at == null
                    ? groundGuard.fromMonster(id, tick, Integer.MIN_VALUE / 2, Integer.MIN_VALUE / 2, -1, 1)
                    : groundGuard.fromMonster(id, tick, at.getX(), at.getY(), at.getPlane(), size);
                if (keep) own.add(s);
            }
        }
        addItems(ev, own);
        // not sent yet: the game's own report of this kill (onServerNpcLoot) is sent instead when there is one
        npcLootMerge.onLegacy(npc.getName(), tick, ev);
    }

    /**
     * What the game itself says a monster gave. It is exact, and it is the only report for monsters whose loot never
     * lies under them: the Maggot King's corpse (straight to the backpack), The Whisperer, Araxxor, the Royal Titans,
     * the Mad Angel. Until 2026-11-27 none of those counted by themselves (208 Maggot King kills, no loot seen).
     * Ordinary monsters are reported here too, and again by onNpcLootReceived: NpcLootMerge sends each kill once.
     * Sent as the same monster drop as any other, named after the monster.
     */
    @Subscribe
    public void onServerNpcLoot(net.runelite.client.events.ServerNpcLoot e)
    {
        net.runelite.api.NPCComposition npc = e.getComposition();
        if (!config.sendNpcLoot() || npc == null || npc.getName() == null || e.getItems() == null || e.getItems().isEmpty()) return;
        int tick = client.getTickCount();
        // the game reports pickpocket loot the same way: that is handled as pickpocketing (onLootReceived), not as a kill
        if (tick - lastPickpocketTick <= 1) return;
        npcLootMerge.onServer(npc.getName(), tick);
        GameEvent ev = base(GameEvent.NPC);
        petDetector.onSource(npc.getName(), tick);
        ev.npcId = npc.getId();
        ev.npcName = npc.getName();
        addItems(ev, new ArrayList<>(e.getItems()));
        submit(ev, true);
    }

    private final com.forgebingo.core.GroundGuard groundGuard = new com.forgebingo.core.GroundGuard();
    /** pairs the game's report of a kill with RuneLite's older one, so each kill is sent once */
    private final com.forgebingo.core.NpcLootMerge<GameEvent> npcLootMerge = new com.forgebingo.core.NpcLootMerge<>();
    /** the activity name Sailing rewards are listed under in activity-items.txt */
    private static final String SAILING = "Sailing";

    /** "Drop" on a backpack item: that item lying on the ground next is the player's own, not a monster's drop. */
    @Subscribe
    public void onMenuOptionClicked(net.runelite.api.events.MenuOptionClicked e)
    {
        if (e.getItemId() > 0 && "Drop".equalsIgnoreCase(e.getMenuOption()))
        {
            groundGuard.onDropClicked(itemManager.canonicalize(e.getItemId()), client.getTickCount());
        }
        // Perdu sells lost untradeables back for coins: what he hands over is the player's own, never a reward
        String target = e.getMenuTarget();
        if (target != null && net.runelite.client.util.Text.removeTags(target).toLowerCase(Locale.ROOT).startsWith("perdu"))
        {
            buyBackUntilTick = client.getTickCount() + BUY_BACK_TICKS;
        }
    }

    /** how long after talking to Perdu nothing counts (his dialogue can be left open a while): about two minutes */
    private static final int BUY_BACK_TICKS = 200;
    private int buyBackUntilTick = Integer.MIN_VALUE / 2;
    /** Death's office (Death's Domain): everything handed over there is the player's own */
    private static final int DEATHS_DOMAIN_REGION = 12633;

    /**
     * The player is getting items back after a death: a gravestone or a boss's item-retrieval service is open, they
     * are in Death's office or at Death's coffer, or they are dealing with Perdu. Whatever reaches the backpack now
     * is their own (owner, 2026-11-23), whenever and wherever the death was (another device, the plugin switched off).
     */
    private boolean reclaimingAfterDeath(int tick)
    {
        if (tick <= buyBackUntilTick) return true;
        if (widgetShowing(net.runelite.api.gameval.InterfaceID.GRAVESTONE_RETRIEVAL, net.runelite.api.gameval.InterfaceID.DEATH_OFFICE,
            net.runelite.api.gameval.InterfaceID.DEATH_COFFER, net.runelite.api.gameval.InterfaceID.DEATHKEEP))
        {
            return true;
        }
        Player me = client.getLocalPlayer();
        if (me == null) return false;
        net.runelite.api.Actor with = me.getInteracting();
        if (with instanceof NPC && with.getName() != null && with.getName().equalsIgnoreCase("Perdu"))
        {
            buyBackUntilTick = Math.max(buyBackUntilTick, tick + 20);
            return true;
        }
        if (me.getLocalLocation() == null) return false;
        WorldPoint w = WorldPoint.fromLocalInstance(client, me.getLocalLocation());
        return w != null && w.getRegionID() == DEATHS_DOMAIN_REGION;
    }

    /** A ground item that belongs to another player showing up: never part of a monster's loot for this player. */
    @Subscribe
    public void onItemSpawned(net.runelite.api.events.ItemSpawned e)
    {
        TileItem item = e.getItem();
        // the player's own Sailing loot appearing on the ground (a sea creature killed from a distance): remembered so
        // that picking it up counts (see onItemDespawned). An item they dropped themselves is told apart by its "Drop".
        if (item != null && e.getTile() != null && item.getOwnership() == TileItem.OWNERSHIP_SELF)
        {
            int own = itemManager.canonicalize(item.getId());
            if (SAILING.equals(com.forgebingo.core.ActivityItems.activityFor(own)))
            {
                net.runelite.api.coords.WorldPoint at = e.getTile().getWorldLocation();
                if (at != null) groundGuard.onOwnItem(own, client.getTickCount(), at.getX(), at.getY(), at.getPlane());
            }
        }
        // another player's item, or one a group ironman teammate put down (those show to the group at once)
        if (item != null && e.getTile() != null
            && (item.getOwnership() == TileItem.OWNERSHIP_OTHER || item.getOwnership() == TileItem.OWNERSHIP_GROUP))
        {
            net.runelite.api.coords.WorldPoint w = e.getTile().getWorldLocation();
            groundGuard.onForeignItem(itemManager.canonicalize(item.getId()), client.getTickCount(), w.getX(), w.getY(), w.getPlane());
        }
    }

    @Subscribe
    public void onLootReceived(LootReceived e)
    {
        // NPC loot arrives via NpcLootReceived; PLAYER (PvP) is not reported. Compared by name so this
        // does not depend on where RuneLite keeps the LootRecordType enum.
        String recordType = e.getType() == null ? "" : e.getType().name();
        if (recordType.equals("PLAYER"))
        {
            // A player kill: the loot itself never counts (PvP items are not drops), but a "PKer" task counts kills.
            // One kc event for the player_kills source, with a screenshot when a task matches.
            if (!config.sendKc()) return;
            GameEvent ev = base(GameEvent.KC);
            ev.sourceName = "Player kill";
            submit(ev, true);
            return;
        }
        if (recordType.equals("NPC")) return;
        // Wilderness loot key chests never count (they return other players' items)
        if (SourceClassifier.ignored(e.getName())) return;
        // The Fractured Archive's chests are read straight from the loot window (onWidgetLoaded): both of them
        if (ARCHIVE_LOOT_NAME.equalsIgnoreCase(e.getName())) return;
        if (recordType.equals("PICKPOCKET"))
        {
            // Pickpocketing (e.g. a Blood shard from a Vyre): sent only when the loot holds an item the board's
            // tasks list, so coin pouches do not flood the server. It counts for tasks from anywhere or naming that NPC.
            if (!config.sendChestLoot() || e.getItems() == null) return;
            List<ItemStack> wanted = new ArrayList<>();
            for (ItemStack s : e.getItems())
            {
                if (s.getQuantity() > 0 && boardItemIds.contains(itemManager.canonicalize(s.getId()))) wanted.add(s);
            }
            if (wanted.isEmpty()) return;
            GameEvent ev = base(GameEvent.RAID_CHEST);
            ev.sourceName = e.getName();
            addItems(ev, wanted);
            submit(ev, true);
            return;
        }
        String type = SourceClassifier.forEventLoot(e.getName());
        petDetector.onSource(e.getName(), client.getTickCount());
        if (!config.sendChestLoot()) return;
        GameEvent ev = base(type);
        String tier = GameEvent.CLUE.equals(type) ? SourceClassifier.clueTier(e.getName()) : null;
        ev.sourceName = tier != null ? tier : e.getName();
        addItems(ev, e.getItems());
        // Raid, Barrows, Moons and other reward chests post their loot as the loot window opens (owner, 2026-11-12):
        // the proof is that window, taken twice a moment apart so one of the two shows it fully drawn.
        submit(ev, true, !GameEvent.CLUE.equals(type));
    }

    /** "You pick the Master Farmer's pocket." (also "You pick the Elf's pocket.") */
    private static final java.util.regex.Pattern PICKPOCKET = java.util.regex.Pattern.compile("You pick (?:the )?(.{1,40}?)'s pocket");
    private volatile String lastPickpocketNpc;
    private volatile int lastPickpocketTick = -100;

    /** Pickpocket loot that went into the seed box: sent like other pickpocket loot (only items the board wants). */
    private void reportPickpocket(String npc, Map<Integer, Integer> gained)
    {
        if (!config.sendChestLoot()) return;
        GameEvent ev = base(GameEvent.RAID_CHEST);
        ev.sourceName = npc;
        for (Map.Entry<Integer, Integer> g : gained.entrySet())
        {
            int id = itemManager.canonicalize(g.getKey());
            if (g.getValue() > 0 && boardItemIds.contains(id)) ev.addItem(id, g.getValue());
        }
        if (ev.items == null || ev.items.isEmpty()) return;
        submit(ev, true);
    }

    /** "Name found something special: Item" (Theatre of Blood, Tombs of Amascut and raids worded the same way) */
    private static final java.util.regex.Pattern FOUND_SPECIAL = java.util.regex.Pattern.compile("^(.{1,12}?) found something special: (.+)$");
    /** Chambers of Xeric: a "Special loot:" line, then "Name - Item" on the next */
    private static final java.util.regex.Pattern SPECIAL_LINE = java.util.regex.Pattern.compile("^(.{1,12}?) - (.+)$");
    private static final long RAID_SHOT_TTL_MILLIS = 15 * 60_000L;
    /**
     * A raid's completion that counted for a kill-count tile (owner, 2026-11-13): the kill message comes before the
     * reward chest, so the chest opened next is photographed and added to those same tiles as proof.
     */
    private volatile ApiModels.EventResult raidKcResult;
    private volatile long raidKcAt;

    private ApiModels.EventResult freshRaidKc()
    {
        ApiModels.EventResult r = raidKcResult;
        return r != null && System.currentTimeMillis() - raidKcAt < RAID_SHOT_TTL_MILLIS ? r : null;
    }
    private volatile int specialLootTick = -100;
    /** the frame taken when a raid's chat named us for special loot; goes in front of that chest's two shots */
    private volatile byte[] raidShot;
    private volatile java.time.ZonedDateTime raidShotTaken;
    private volatile long raidShotAt;

    /**
     * The purple moment (owner, 2026-11-12): when a raid's chat line says we found something special, keep a
     * screenshot of it. It is only ever sent together with that raid's chest loot, as the first of its proof shots.
     */
    private void watchSpecialLoot(String raw, int tick)
    {
        if (raw == null) return;
        String msg = net.runelite.client.util.Text.removeTags(raw).replace(' ', ' ').trim();
        if (msg.equalsIgnoreCase("Special loot:"))
        {
            specialLootTick = tick;
            return;
        }
        java.util.regex.Matcher m = FOUND_SPECIAL.matcher(msg);
        if (!m.find())
        {
            if (tick - specialLootTick > 2) return;
            m = SPECIAL_LINE.matcher(msg);
            if (!m.find()) return;
        }
        Player me = client.getLocalPlayer();
        String mine = me == null ? null : Sanitizer.normalizeRsn(me.getName());
        if (mine == null || !mine.equalsIgnoreCase(Sanitizer.normalizeRsn(m.group(1)))) return;
        // the player turned automatic proof screenshots off: take none
        if (!config.screenshotOnMatch()) return;
        // a moment later, so the chat line (and the purple light where the raid shows one) is drawn
        proofFrame(600, image -> {
            if (client.getGameState() != GameState.LOGGED_IN || bankPinOpen()) return;
            // the moment the frame was drawn, not when the worker gets round to saving it
            final java.time.ZonedDateTime taken = java.time.ZonedDateTime.now();
            final long at = System.currentTimeMillis();
            executor.execute(() -> {
                try
                {
                    BufferedImage img = ImageUtil.bufferedImageFromImage(image);
                    if (config.screenshotSidebar()) img = withWindow(img);
                    byte[] shot = ScreenshotEncode.jpeg(img, ApiClient.MAX_SCREENSHOT_BYTES);
                    if (shot == null) return;
                    raidShotTaken = taken;
                    raidShotAt = at;
                    raidShot = shot;
                }
                catch (Exception ex)
                {
                    log.debug("special loot screenshot failed", ex);
                }
            });
        }, null);
    }

    @Subscribe
    public void onChatMessage(ChatMessage e)
    {
        String type = e.getType() == null ? "" : e.getType().name();
        String msg = e.getMessage();
        int tick = client.getTickCount();
        // "Accepted trade." is written by the game when a player trade goes through: what arrives now was traded
        if (type.equals("TRADE")) activityWatcher.onTrade(tick);
        // only lines the game itself writes: a player typing the same words in chat must not set this off
        if (type.equals("GAMEMESSAGE") || type.equals("FRIENDSCHATNOTIFICATION") || type.equals("SPAM")) watchSpecialLoot(msg, tick);
        if (type.equals("CLAN_MESSAGE") || type.equals("CLAN_GIM_MESSAGE") || type.equals("FRIENDSCHATNOTIFICATION"))
        {
            // Clan pet broadcasts are the only line that names a duplicate pet. Only our own.
            ChatParsers.petBroadcast(msg).ifPresent(b -> {
                Player me = client.getLocalPlayer();
                String mine = me == null ? null : Sanitizer.normalizeRsn(me.getName());
                if (mine != null && mine.equalsIgnoreCase(Sanitizer.normalizeRsn(b.player)))
                {
                    petDetector.onBroadcast(b.petName, b.duplicate, tick).ifPresent(this::reportPet);
                }
            });
            return;
        }
        if (e.getType() != ChatMessageType.GAMEMESSAGE && e.getType() != ChatMessageType.SPAM) return;
        java.util.regex.Matcher pick = PICKPOCKET.matcher(msg == null ? "" : msg);
        if (pick.find())
        {
            // remembered for the seed box (its seeds never reach the loot tracker)
            lastPickpocketNpc = pick.group(1);
            lastPickpocketTick = tick;
        }
        if (e.getType() != ChatMessageType.GAMEMESSAGE) return;
        ChatParsers.collectionLogItem(msg).ifPresent(name -> {
            Integer itemId = resolveItemId(name);
            if (itemId != null && config.sendCollectionLog())
            {
                GameEvent ev = base(GameEvent.COLLECTION_LOG);
                ev.addItem(itemId, 1);
                // a new slot that counts for a tile gets its picture: the "New item added to your collection log"
                // line in the chat (owner, 2026-11-22)
                submit(ev, true);
            }
            petDetector.onCollectionLog(name, tick).ifPresent(this::reportPet);
        });
        ChatParsers.pet(msg).ifPresent(kind -> petDetector.onPetMessage(kind, tick).ifPresent(this::reportPet));
        ChatParsers.combatTask(msg).ifPresent(ct -> {
            // hidden for now (owner, 2026-11-11): only sent once the server takes combat_task events
            if (!config.sendCombatTasks()) return;
            GameEvent ev = base(GameEvent.COMBAT_TASK);
            ev.sourceName = ct.name;
            submit(ev, true);
        });
        ChatParsers.killCount(msg).ifPresent(k -> {
            petDetector.onSource(k.boss, tick);
            if (k.boss != null && k.boss.toLowerCase(Locale.ROOT).contains("gauntlet"))
            {
                gauntletDoneTick = tick;
                gauntletDoneName = k.boss.toLowerCase(Locale.ROOT).contains("corrupted") ? "Corrupted Gauntlet" : "The Gauntlet";
            }
            if (!config.sendKc()) return;
            GameEvent ev = base(GameEvent.KC);
            ev.sourceName = k.boss; // one event per kill; the server matches the name against its catalogue
            // a kill that counts for a kill-count tile gets its screenshot too (owner, 2026-11-13)
            submit(ev, true);
        });
        ChatParsers.clueCompletion(msg).ifPresent(c -> {
            petDetector.onSource(c.sourceName(), tick);
            if (!config.sendKc()) return;
            // One completion = one kc event for the clue_<tier> catalogue source (the casket loot is a separate clue event).
            GameEvent ev = base(GameEvent.KC);
            ev.sourceName = c.sourceName();
            submit(ev, true);
        });
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged e)
    {
        ItemContainer c = e.getItemContainer();
        if (c == null) return;
        if (e.getContainerId() != INVENTORY_CONTAINER_ID)
        {
            Map<Integer, Integer> after = containerCounts(c);
            Map<Integer, Integer> was = lastContainers.put(e.getContainerId(), after);
            if (e.getContainerId() == WORN_CONTAINER_ID && !after.equals(was))
            {
                // Worn gear changed (equip, unequip, swap): whatever reaches the backpack now came off the player, even
                // when the worn item has another id than its backpack form (an Elemental amulet taken off counted as a
                // Fractured Archive reward, owner 2026-11-14).
                activityWatcher.onWornChanged(client.getTickCount());
            }
            if (was != null && e.getContainerId() == net.runelite.api.gameval.InventoryID.SEED_BOX)
            {
                // An open seed box takes pickpocketed seeds straight in, so RuneLite's loot tracker never reports
                // them (owner, 2026-11-11). Seeds that land in the box right after "You pick the ...'s pocket"
                // count as that pickpocket's loot.
                int tick = client.getTickCount();
                String npc = lastPickpocketNpc;
                if (npc != null && tick - lastPickpocketTick <= 2)
                {
                    Map<Integer, Integer> gained = InventoryDiff.added(was, after);
                    if (!gained.isEmpty()) reportPickpocket(npc, gained);
                }
                return;
            }
            if (was != null)
            {
                int tick = client.getTickCount();
                // anything that left this container (bank withdraw, unequip, storage, trade) is a move, not a drop
                for (Integer id : InventoryDiff.added(after, was).keySet()) activityWatcher.onLeftContainer(itemManager.canonicalize(id), tick);
            }
            return;
        }
        Map<Integer, Integer> now = containerCounts(c);
        Map<Integer, Integer> before = lastInventory;
        lastInventory = now;
        if (before != null)
        {
            int tick = client.getTickCount();
            for (Map.Entry<Integer, Integer> a : InventoryDiff.added(before, now).entrySet())
            {
                activityWatcher.onInventoryAdded(itemManager.canonicalize(a.getKey()), a.getValue(), tick);
            }
            // something other than a currency left the backpack this tick: an item changing form, not a reward
            for (Integer gone : InventoryDiff.added(now, before).keySet())
            {
                if (!CURRENCY_ITEMS.contains(itemManager.canonicalize(gone)))
                {
                    activityWatcher.onItemsLeftInventory(tick);
                    break;
                }
            }
        }
        if (before != null && petDetector.wantsInventory())
        {
            List<Integer> added = new ArrayList<>();
            for (Integer id : InventoryDiff.added(before, now).keySet()) added.add(itemManager.canonicalize(id));
            petDetector.onInventoryAdded(added, client.getTickCount()).ifPresent(this::reportPet);
        }
    }

    private static Map<Integer, Integer> containerCounts(ItemContainer c)
    {
        Item[] slots = c.getItems();
        int[] ids = new int[slots.length];
        int[] qty = new int[slots.length];
        for (int i = 0; i < slots.length; i++)
        {
            ids[i] = slots[i].getId();
            qty[i] = slots[i].getQuantity();
        }
        return InventoryDiff.counts(ids, qty);
    }

    @Subscribe
    public void onItemDespawned(ItemDespawned e)
    {
        // a ground item vanishing as the same item reaches the backpack is a pickup (next to us, or from a distance
        // with Telekinetic Grab): picked-up items, whoever dropped them, never count (PvP loot included)
        TileItem item = e.getItem();
        if (item == null) return;
        int id = itemManager.canonicalize(item.getId());
        // except the player's own Sailing loot left where the sea creature died (owner, 2026-11-27): taking that,
        // by sailing over it or with Telekinetic Grab from a distance, is collecting the reward
        if (e.getTile() != null && SAILING.equals(com.forgebingo.core.ActivityItems.activityFor(id)))
        {
            net.runelite.api.coords.WorldPoint w = e.getTile().getWorldLocation();
            if (w != null && groundGuard.takeOwnLoot(id, w.getX(), w.getY(), w.getPlane())) return;
        }
        activityWatcher.onGroundItemGone(id, client.getTickCount());
    }

    /**
     * The player died (anywhere, safe deaths like the Colosseum included): the items they had coming back to them
     * afterwards are their own, never a reward (owner, 2026-11-19).
     */
    @Subscribe
    public void onActorDeath(net.runelite.api.events.ActorDeath e)
    {
        if (e.getActor() == null || e.getActor() != client.getLocalPlayer()) return;
        playerDied();
    }

    /** RuneLite's death event, or hitpoints reaching 0 (a backup for raids and minigames); one death counts once */
    private void playerDied()
    {
        activityWatcher.onDeath(client.getTickCount(), holdings(), System.currentTimeMillis());
        saveDeathMemory();
    }

    /** what the player carries and wears right now, item id (canonical) to amount */
    private Map<Integer, Integer> holdings()
    {
        Map<Integer, Integer> held = new java.util.HashMap<>();
        Map<Integer, Integer> pack = heldIn(INVENTORY_CONTAINER_ID, lastInventory);
        pack.forEach((id, q) -> held.merge(itemManager.canonicalize(id), q, Integer::sum));
        Map<Integer, Integer> worn = heldIn(WORN_CONTAINER_ID, lastContainers.get(WORN_CONTAINER_ID));
        worn.forEach((id, q) -> held.merge(itemManager.canonicalize(id), q, Integer::sum));
        return held;
    }

    /**
     * What one container holds: what the game says right now, and what the plugin last saw in it, whichever is more
     * per item (2026-11-27: a fire cape reclaimed after a death counted as a Jad reward). The remembered copy is only
     * filled when the container changes, so gear worn since before the plugin was switched on was not in it and the
     * death memory never learned the player had it; the game's own copy may already be empty a moment after a death.
     */
    private Map<Integer, Integer> heldIn(int containerId, Map<Integer, Integer> remembered)
    {
        Map<Integer, Integer> out = new java.util.HashMap<>();
        if (remembered != null) out.putAll(remembered);
        ItemContainer live = client.getItemContainer(containerId);
        if (live != null) containerCounts(live).forEach((id, q) -> out.merge(id, q, Math::max));
        return out;
    }

    /**
     * Items lost at a death are kept per account between sessions, so reclaiming them after a restart still does not
     * count. Only item ids, amounts and times: nothing is sent anywhere.
     */
    private static final String DEATH_MEMORY_KEY = "deathItems";
    private boolean deathMemoryRestorePending = true;

    private void saveDeathMemory()
    {
        try
        {
            configManager.setRSProfileConfiguration(ForgeBingoConfig.GROUP, DEATH_MEMORY_KEY, activityWatcher.saveDeathMemory());
        }
        catch (RuntimeException ex)
        {
            log.debug("could not save the death memory", ex);
        }
    }

    private void restoreDeathMemory()
    {
        try
        {
            activityWatcher.clearDeathMemory();
            activityWatcher.restoreDeathMemory(configManager.getRSProfileConfiguration(ForgeBingoConfig.GROUP, DEATH_MEMORY_KEY), System.currentTimeMillis());
        }
        catch (RuntimeException ex)
        {
            log.debug("could not read the death memory", ex);
        }
    }

    @Subscribe
    public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged e)
    {
        activityWatcher.onGrandExchange(client.getTickCount());
    }

    /** Wilderness (varbit 5963) or a PvP / Deadman world: nothing from the backpack watcher counts there. */
    /** the map squares player-owned houses are built in */
    private static final Set<Integer> HOUSE_REGIONS = new HashSet<>(java.util.Arrays.asList(7257, 7513, 7514, 7769, 7770, 8025, 8026));
    /** interface groups: the Grand Exchange offers window and the collection box */
    private static final int GE_OFFERS_GROUP = 465;
    private static final int GE_COLLECT_GROUP = 402;

    /** The player is inside a player-owned house. */
    private boolean inHouse()
    {
        Player me = client.getLocalPlayer();
        if (me == null || me.getLocalLocation() == null) return false;
        WorldPoint w = WorldPoint.fromLocalInstance(client, me.getLocalLocation());
        return w != null && HOUSE_REGIONS.contains(w.getRegionID());
    }

    private boolean grandExchangeOpen()
    {
        return widgetShowing(GE_OFFERS_GROUP, GE_COLLECT_GROUP);
    }

    /** One of these interface groups is open and showing. Client thread. */
    private boolean widgetShowing(int... groups)
    {
        for (int group : groups)
        {
            net.runelite.api.widgets.Widget w = client.getWidget(group, 0);
            if (w != null && !w.isHidden()) return true;
        }
        return false;
    }

    private boolean inPvp()
    {
        if (client.getVarbitValue(5963) == 1) return true;
        for (Object w : client.getWorldType())
        {
            String n = String.valueOf(w);
            if (n.contains("PVP") || n.contains("DEADMAN")) return true;
        }
        return false;
    }

    /** Sends a pet event with every item id of that pet (any variant a task lists matches), or tells the player. */
    private void reportPet(PetDetector.Drop drop)
    {
        if (!config.sendPets()) return;
        if (!drop.identified())
        {
            notice((drop.duplicate ? "Duplicate pet" : "Pet") + " received, but the plugin could not tell which one: submit it on the website.", true);
            return;
        }
        GameEvent ev = base(GameEvent.PET);
        for (int id : drop.pet.itemIds) ev.addItem(id, 1);
        ev.sourceName = drop.pet.name + (drop.duplicate ? " (duplicate)" : "");
        log.debug("pet {} identified via {}", drop.pet.name, drop.via);
        submit(ev, true);
    }

    @Subscribe
    public void onStatChanged(StatChanged e)
    {
        Skill skill = e.getSkill();
        int level = e.getLevel();
        if (skill == Skill.OVERALL) return;
        // hitpoints at 0 while logged in is a death, in raids too (Chambers of Xeric, Theatre of Blood, Tombs of Amascut)
        if (skill == Skill.HITPOINTS && e.getBoostedLevel() == 0 && level > 0 && client.getGameState() == GameState.LOGGED_IN) playerDied();
        Integer previous = levels.put(skill, level);
        if (previous == null || level <= previous || !config.sendLevels()) return;
        GameEvent ev = base(GameEvent.LEVEL);
        ev.skill = skill.getName().toLowerCase(Locale.ROOT);
        ev.level = level;
        submit(ev, true); // a level that counts for a tile gets its picture: the level-up message (owner, 2026-11-22)
        int total = client.getTotalLevel();
        if (total > 0)
        {
            GameEvent overall = base(GameEvent.LEVEL);
            overall.skill = "overall";
            overall.level = total;
            submit(overall, true);
        }
    }

    /**
     * Decodes a tile icon only after its header says it is small: a tiny file can claim a huge picture, and decoding
     * that would use up the client's memory.
     */
    private static BufferedImage readSmallImage(byte[] bytes) throws java.io.IOException
    {
        try (javax.imageio.stream.ImageInputStream in = ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes)))
        {
            if (in == null) return null;
            java.util.Iterator<javax.imageio.ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) return null;
            javax.imageio.ImageReader reader = readers.next();
            try
            {
                reader.setInput(in, true, true);
                if (reader.getWidth(0) > 256 || reader.getHeight(0) > 256) return null;
                return reader.read(0);
            }
            finally
            {
                reader.dispose();
            }
        }
    }

    /** what RuneLite's loot tracker calls The Fractured Archive's chest loot (it reports only the first chest opened) */
    private static final String ARCHIVE_LOOT_NAME = "Fractured Archive";
    private final com.forgebingo.core.ChestOpens archiveChests = new com.forgebingo.core.ChestOpens();
    private boolean wasInstance;
    private boolean freshLogin = true;

    /**
     * The Fractured Archive has two reward chests: the standard one, and a second for whoever rolled the unique
     * (owner, 2026-11-13). Each one opened sends its own loot with the loot window as proof.
     */
    @Subscribe
    public void onWidgetLoaded(net.runelite.api.events.WidgetLoaded e)
    {
        if (isTradeWindow(e.getGroupId()))
        {
            openTradeWindows.add(e.getGroupId());
            activityWatcher.onTrade(client.getTickCount());
            return;
        }
        if (e.getGroupId() != net.runelite.api.gameval.InterfaceID.TFA_CHESTS) return;
        ItemContainer c = client.getItemContainer(net.runelite.api.gameval.InventoryID.TFA_CHESTS);
        if (c == null) return;
        Map<Integer, Integer> contents = new java.util.LinkedHashMap<>();
        for (net.runelite.api.Item i : c.getItems())
        {
            if (i == null || i.getId() <= 0 || i.getQuantity() < 1) continue;
            contents.merge(itemManager.canonicalize(i.getId()), i.getQuantity(), Integer::sum);
        }
        Map<Integer, Integer> fresh = archiveChests.opened(contents);
        if (fresh.isEmpty()) return;
        petDetector.onSource(ARCHIVE_LOOT_NAME, client.getTickCount());
        if (!config.sendChestLoot()) return;
        GameEvent ev = base(GameEvent.RAID_CHEST);
        ev.sourceName = ARCHIVE_LOOT_NAME;
        for (Map.Entry<Integer, Integer> f : fresh.entrySet())
        {
            ev.addItem(f.getKey(), f.getValue());
            activityWatcher.onLoot(f.getKey(), client.getTickCount());
        }
        submit(ev, true, true);
    }

    /**
     * The two player-trade windows (offer and confirm). Items another player hands over are never a drop
     * (owner, 2026-11-17), so backpack gains while one is open, or just after it closes, are not reported.
     */
    private final java.util.Set<Integer> openTradeWindows = new java.util.HashSet<>();

    private static boolean isTradeWindow(int groupId)
    {
        return groupId == net.runelite.api.gameval.InterfaceID.TRADEMAIN || groupId == net.runelite.api.gameval.InterfaceID.TRADECONFIRM;
    }

    @Subscribe
    public void onWidgetClosed(net.runelite.api.events.WidgetClosed e)
    {
        if (!isTradeWindow(e.getGroupId())) return;
        openTradeWindows.remove(e.getGroupId());
        activityWatcher.onTrade(client.getTickCount());
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged e)
    {
        if (e.getGameState() == GameState.LOGIN_SCREEN || e.getGameState() == GameState.HOPPING) openTradeWindows.clear();
        if (e.getGameState() == GameState.LOGIN_SCREEN || e.getGameState() == GameState.HOPPING) archiveChests.reset();
        // logging out or hopping closes the board (map LOADING does not; the input ignores clicks until LOGGED_IN)
        if (e.getGameState() == GameState.LOGIN_SCREEN || e.getGameState() == GameState.HOPPING || e.getGameState() == GameState.CONNECTION_LOST) boardOverlay.close();
        if (e.getGameState() == GameState.LOGIN_SCREEN || e.getGameState() == GameState.HOPPING) requestWomUpdate();
        // LOGGED_IN also follows every map load: only a real login (or hop) needs the account verified again
        if (e.getGameState() == GameState.LOGIN_SCREEN || e.getGameState() == GameState.HOPPING || e.getGameState() == GameState.LOGGING_IN) freshLogin = true;
        if (e.getGameState() == GameState.LOGGED_IN && freshLogin)
        {
            freshLogin = false;
            helloPending = true; // another account may have logged in: verify again
            deathMemoryRestorePending = true;
            helloNotBefore = 0;
        }
        if (e.getGameState() == GameState.LOGIN_SCREEN)
        {
            levels.clear(); // first StatChanged per skill after login is a baseline, not a level-up
            petDetector.reset();
            lastInventory = null;
        }
    }

    @Subscribe
    public void onGameTick(GameTick e)
    {
        int tick = client.getTickCount();
        // leaving (or entering) a raid's instance: the next Archive chests belong to another raid
        boolean instance = client.getTopLevelWorldView() != null && client.getTopLevelWorldView().isInstance();
        if (instance != wasInstance)
        {
            wasInstance = instance;
            archiveChests.reset();
        }
        maybeHello();
        if (petDetector.wantsFollower())
        {
            NPC follower = client.getFollower();
            if (follower != null)
            {
                petDetector.onFollower(follower.getId(), tick).ifPresent(this::reportPet);
            }
        }
        petDetector.onTick(tick).ifPresent(this::reportPet);
        if (!openTradeWindows.isEmpty()) activityWatcher.onTrade(tick);
        groundGuard.prune(tick);
        // kills the game itself did not report: sent now, from RuneLite's older report
        for (GameEvent held : npcLootMerge.flush(tick)) submit(held, true);
        // one event (and one screenshot) per activity per tick: a chest or shop trip with several uniques counts each
        Map<String, GameEvent> byActivity = new java.util.LinkedHashMap<>();
        // the account's death memory, once its profile is loaded after logging in
        if (deathMemoryRestorePending && client.getLocalPlayer() != null && lastInventory != null)
        {
            deathMemoryRestorePending = false;
            restoreDeathMemory();
        }
        // the death moment is over: what the player still has was kept, not waiting to come back
        if (activityWatcher.wantsHoldingsAfterDeath(tick, instance)) activityWatcher.onHoldingsAfterDeath(holdings(), System.currentTimeMillis());
        // items taken out of a Grand Exchange offer or the collection box were bought, not earned: a part-filled
        // offer does not always report a change when it is collected, so the open window itself is the signal
        if (grandExchangeOpen()) activityWatcher.onGrandExchange(tick);
        if (reclaimingAfterDeath(tick)) activityWatcher.onReclaim(tick);
        // nothing is earned inside a player's house: gear taken out of its costume room and cape rack is the
        // player's own (those are not item containers, so nothing else tells it apart from a reward)
        List<ActivityWatcher.Drop> drops = activityWatcher.flush(tick, inPvp() || inHouse(), System.currentTimeMillis());
        if (activityWatcher.deathMemoryChanged()) saveDeathMemory();
        for (ActivityWatcher.Drop d : drops)
        {
            if (!config.sendChestLoot()) continue;
            GameEvent ev = byActivity.computeIfAbsent(gauntletActivity(d.itemId, d.activity, tick), k -> {
                GameEvent n = base(GameEvent.RAID_CHEST);
                n.sourceName = k; // the server knows every activity name (npc_sources aliases)
                return n;
            });
            ev.addItem(d.itemId, d.quantity);
        }
        for (GameEvent ev : byActivity.values()) submit(ev, true);
    }

    /** crystal seeds: the Gauntlet's reward chest gives them straight to the backpack (the weapon seed is also an LMS buy) */
    private static final java.util.Set<Integer> GAUNTLET_SEEDS = java.util.Set.of(4207, 23956, 25859);
    /** the reward chest is opened in the lobby right after the boss; about five minutes is plenty */
    private static final int GAUNTLET_CHEST_TICKS = 500;
    private volatile int gauntletDoneTick = Integer.MIN_VALUE / 2;
    private volatile String gauntletDoneName = "The Gauntlet";

    /** A crystal seed reaching the backpack just after a Gauntlet completion is that Gauntlet's reward. */
    private String gauntletActivity(int itemId, String activity, int tick)
    {
        if (GAUNTLET_SEEDS.contains(itemId) && tick - gauntletDoneTick <= GAUNTLET_CHEST_TICKS) return gauntletDoneName;
        return activity;
    }

    /**
     * Collection log lines carry the item name only. Pets, the board's own items and the server catalogue are
     * resolved by name (core/ItemNames); {@code ItemManager.search} only knows tradeables and is a last resort.
     */
    private Integer resolveItemId(String name)
    {
        java.util.Optional<Integer> known = itemNames.resolve(name);
        if (known.isPresent())
        {
            return known.get();
        }
        for (var price : itemManager.search(name))
        {
            if (price.getName().equalsIgnoreCase(name))
            {
                return itemManager.canonicalize(price.getId());
            }
        }
        log.debug("could not resolve collection log item '{}'", name);
        return null;
    }

    // ---------- building / sending ----------

    /** @return a new event; rsn / accountHash are null when the player is not available (submit() then skips it). */
    private GameEvent base(String type)
    {
        GameEvent ev = new GameEvent();
        ev.id = UUID.randomUUID().toString();
        ev.sourceType = type;
        ev.occurredAt = Instant.now().toString();
        ev.world = client.getWorld();
        Player me = client.getLocalPlayer();
        ev.rsn = me == null ? null : Sanitizer.normalizeRsn(me.getName());
        long hash = client.getAccountHash();
        ev.accountHash = hash == -1 ? null : Long.toString(hash);
        return ev;
    }

    private void addItems(GameEvent ev, Collection<ItemStack> stacks)
    {
        if (stacks == null) return;
        for (ItemStack s : stacks)
        {
            if (s.getQuantity() < 1) continue;
            int id = itemManager.canonicalize(s.getId());
            ev.addItem(id, s.getQuantity());
            activityWatcher.onLoot(id, client.getTickCount());
        }
    }

    private void submit(GameEvent ev, boolean maybeScreenshot)
    {
        submit(ev, maybeScreenshot, false);
    }

    /** @param chestWindow the loot came from a reward chest whose loot window is on screen right now */
    private void submit(GameEvent ev, boolean maybeScreenshot, boolean chestWindow)
    {
        // read once: switching the plugin off clears these fields from another thread
        OfflineQueue q = queue;
        ApiClient a = api;
        if (!running || q == null || !Sanitizer.validRsn(ev.rsn) || ev.accountHash == null || a == null || !a.configured())
        {
            return;
        }
        // The server rejects item-based events without items; do not queue what can never be accepted.
        boolean needsItems = GameEvent.NPC.equals(ev.sourceType) || GameEvent.RAID_CHEST.equals(ev.sourceType)
            || GameEvent.CLUE.equals(ev.sourceType) || GameEvent.COLLECTION_LOG.equals(ev.sourceType) || GameEvent.PET.equals(ev.sourceType);
        if (needsItems && (ev.items == null || ev.items.isEmpty()))
        {
            return;
        }
        if (!dedup.firstSeen(EventDeduplicator.signature(ev, client.getTickCount()), System.currentTimeMillis()))
        {
            return;
        }
        log.debug("queue event {} {}", ev.sourceType, ev.label());
        // a raid chest is photographed when its loot could count, or when the raid itself just counted for a tile
        if (maybeScreenshot && config.screenshotOnMatch() && (ProofPolicy.couldCount(ev, currentBoard(), liveBoards) || (chestWindow && ProofPolicy.raidWithChest(ev.sourceName) && freshRaidKc() != null)))
        {
            captureScreenshot(ev.id, shotDelays(ev.sourceType, ev.sourceName, chestWindow), chestWindow);
        }
        // kept in memory here (this is the game thread); the worker writes the file, ahead of the send that follows
        List<QueuedEvent> evicted = q.add(new QueuedEvent(ev, System.currentTimeMillis()));
        executor.execute(() -> flushQueue(q));
        for (QueuedEvent old : evicted) notice("Queue full, dropped oldest event: " + old.event.label(), true);
        int size = q.size();
        notifyPanel(p -> p.setQueueSize(size, null));
        scheduleQueueDrain(0);
    }

    /** Any thread: the newest request replaces the one waiting. */
    private void scheduleQueueDrain(long delayMillis)
    {
        synchronized (queueLock)
        {
            if (!running) return;
            if (queueFuture != null) queueFuture.cancel(false);
            queueFuture = executor.schedule(this::drainQueue, delayMillis, TimeUnit.MILLISECONDS);
        }
    }

    /** Worker thread: sends what is due, then writes the queue file once for everything the drain changed. */
    private void drainQueue()
    {
        QueueProcessor p = processor;
        OfflineQueue q = queue;
        if (!running || p == null) return;
        long next;
        try
        {
            next = p.drain();
        }
        catch (RuntimeException ex)
        {
            log.debug("queue drain failed", ex);
            next = 60_000;
        }
        finally
        {
            flushQueue(q);
        }
        if (next >= 0) scheduleQueueDrain(next);
    }

    /** Writes the offline queue's file when it is behind what is in memory. Worker thread (or switching off). */
    private void flushQueue(OfflineQueue q)
    {
        try
        {
            if (q != null) q.flush();
        }
        catch (RuntimeException ex)
        {
            log.debug("queue save failed", ex);
        }
    }

    /** RuneLite is closing: an event added a moment ago may not be on disk yet. */
    @Subscribe
    public void onClientShutdown(net.runelite.client.events.ClientShutdown e)
    {
        flushQueue(queue);
    }

    // ---------- screenshots (kept in memory; uploaded only after server confirms a match) ----------

    private void captureScreenshot(String eventId, long[] delays, boolean chest)
    {
        // only in game, and never with the bank PIN keypad on screen (its layout would be in the proof)
        if (client.getGameState() != GameState.LOGGED_IN || bankPinOpen()) return;
        long now = System.currentTimeMillis();
        screenshots.values().removeIf(s -> now - s.at > SCREENSHOT_TTL_MILLIS);
        // full: drop the oldest instead of skipping this one (the newest drop is the likeliest to count). Pictures
        // waiting for another try at their upload share the same few slots.
        while (screenshots.size() + proofRetries.size() >= MAX_SCREENSHOTS)
        {
            String oldest = null;
            long at = Long.MAX_VALUE;
            for (Map.Entry<String, Screenshot> s : screenshots.entrySet()) if (s.getValue().at < at) { at = s.getValue().at; oldest = s.getKey(); }
            if (oldest != null) screenshots.remove(oldest);
            else if (!dropOldestProofRetry()) break;
        }
        // a raid chest opened soon after the chat named us for special loot: that moment goes first
        byte[] before = chest ? raidShot : null;
        java.time.ZonedDateTime beforeTaken = raidShotTaken;
        boolean lead = before != null && beforeTaken != null && now - raidShotAt < RAID_SHOT_TTL_MILLIS;
        if (chest) raidShot = null;
        int off = lead ? 1 : 0;
        Screenshot holder = new Screenshot(delays.length + off, now);
        if (lead)
        {
            holder.raw[0] = before;
            holder.taken[0] = beforeTaken;
        }
        screenshots.put(eventId, holder);
        for (int i = 0; i < delays.length; i++)
        {
            int index = i + off;
            // marked before the frame arrives: the server's answer can come back first, and the upload waits for this
            shotsInProgress.add(eventId + "#" + index);
            requestDropFrame(eventId, index, delays[i]);
        }
    }

    /** How long after an event its screenshot(s) are taken, see {@link ProofPolicy#shotDelays}. */
    static long[] shotDelays(String sourceType, String sourceName, boolean chestWindow)
    {
        return ProofPolicy.shotDelays(sourceType, sourceName, chestWindow);
    }

    /** minimum time between "Send screenshot" proofs */
    private static final long SCREENSHOT_COOLDOWN_MS = 5000;
    private volatile long lastProofShotAt;

    private void requestDropFrame(String eventId, int index, long delayMillis)
    {
        String key = eventId + "#" + index;
        proofFrame(delayMillis, image -> {
            // the server may already have said this drop does not count; never with the bank PIN up or logged out
            if (!running || !screenshots.containsKey(eventId) || client.getGameState() != GameState.LOGGED_IN || bankPinOpen())
            {
                shotsInProgress.remove(key);
                return;
            }
            // the moment the frame was drawn (the stamp and capturedAt), not when the worker gets round to saving it
            final java.time.ZonedDateTime taken = java.time.ZonedDateTime.now();
            // the player's name, read on the client thread as the frame is captured
            String player = client.getLocalPlayer() == null ? null : client.getLocalPlayer().getName();
            executor.execute(() -> {
            try
            {
                BufferedImage img = ImageUtil.bufferedImageFromImage(image);
                if (config.screenshotSidebar())
                {
                    img = withWindow(img);
                }
                // stored unstamped; each team's copy is stamped with its own code and tiles when it is sent
                byte[] shot = ScreenshotEncode.jpeg(img, ApiClient.MAX_SCREENSHOT_BYTES);
                if (shot == null) return;
                Screenshot s = screenshots.get(eventId);
                if (s == null) return; // the server already said this drop does not count
                s.player = player;
                s.taken[index] = taken;
                s.raw[index] = shot;
            }
            catch (Exception ex)
            {
                log.debug("screenshot failed", ex);
            }
            finally
            {
                shotsInProgress.remove(key);
            }
        });
        }, () -> shotsInProgress.remove(key));
    }

    /** Event ids whose screenshot is being taken right now (requested, not yet stored or given up). */
    private final java.util.Set<String> shotsInProgress = ConcurrentHashMap.newKeySet();

    /** proof frames waiting to be taken; our board and celebrations are not drawn meanwhile */
    private final java.util.concurrent.atomic.AtomicInteger proofFramesPending = new java.util.concurrent.atomic.AtomicInteger();
    private volatile long proofHideSince;
    /** wait this long after hiding our overlays, so the captured frame was drawn without them (one frame at 12+ fps) */
    static final long PROOF_HIDE_LEAD_MS = 80;
    /** never hide our overlays longer than this, whatever happens to a frame request */
    static final long PROOF_HIDE_MAX_MS = 2000;

    /** True while a proof screenshot is being taken (owner, 2026-11-22: the board and banners covered the loot). */
    boolean takingProof()
    {
        return proofFramesPending.get() > 0 && System.currentTimeMillis() - proofHideSince < PROOF_HIDE_MAX_MS;
    }

    /**
     * Takes the next game frame for a proof screenshot, with our own board overlay and celebration effects hidden:
     * they are switched off first and the frame is requested a moment later, so the captured frame never has them.
     */
    private void proofFrame(java.util.function.Consumer<java.awt.Image> then)
    {
        proofFrame(0, then, null);
    }

    /** A proof frame waiting for its moment. */
    private static final class FrameJob
    {
        final long at;
        final java.util.function.Consumer<java.awt.Image> then;
        final Runnable skipped;
        /** our overlays were switched off for this frame at this time (0 = not yet) */
        long hiddenAt;

        FrameJob(long at, java.util.function.Consumer<java.awt.Image> then, Runnable skipped)
        {
            this.at = at;
            this.then = then;
            this.skipped = skipped;
        }
    }

    private final java.util.Queue<FrameJob> frameJobs = new java.util.concurrent.ConcurrentLinkedQueue<>();
    /** a frame that could not be taken this long after its moment is given up (the game was not drawing) */
    static final long PROOF_FRAME_GIVE_UP_MS = 3000;

    /**
     * As above, delayMillis from now. Timed on the client thread (onClientTick), never on the shared worker: that one
     * thread also does every server call, so a slow send or a picture upload in progress used to hold the next
     * picture back by seconds (live proofs were taken 1.5-3 s after the drop instead of the planned delay).
     * skipped runs instead of then when the frame is given up. Any thread.
     */
    private void proofFrame(long delayMillis, java.util.function.Consumer<java.awt.Image> then, Runnable skipped)
    {
        frameJobs.add(new FrameJob(System.currentTimeMillis() + Math.max(0, delayMillis), then, skipped));
    }

    /** Client thread, every 20 ms: hides our overlays when a proof frame is due, then takes the frame after that. */
    @Subscribe
    public void onClientTick(net.runelite.api.events.ClientTick e)
    {
        if (frameJobs.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (java.util.Iterator<FrameJob> it = frameJobs.iterator(); it.hasNext();)
        {
            FrameJob job = it.next();
            if (now < job.at) continue;
            if (!running || now - job.at > PROOF_FRAME_GIVE_UP_MS)
            {
                it.remove();
                if (job.hiddenAt != 0) proofFramesPending.updateAndGet(n -> Math.max(0, n - 1));
                if (job.skipped != null) job.skipped.run();
                continue;
            }
            if (job.hiddenAt == 0)
            {
                job.hiddenAt = now;
                proofHideSince = now;
                proofFramesPending.incrementAndGet();
            }
            else if (now - job.hiddenAt >= PROOF_HIDE_LEAD_MS)
            {
                it.remove();
                drawManager.requestNextFrameListener(image -> {
                    try
                    {
                        job.then.accept(image);
                    }
                    finally
                    {
                        proofFramesPending.updateAndGet(n -> Math.max(0, n - 1));
                    }
                });
            }
        }
    }

    /**
     * "Send screenshot as proof" in the side panel: takes the next game frame (stamped like drop proof) and sends it
     * as a new proof for that task, waiting for review like proof added on the website. Any thread.
     */
    void sendScreenshotProof(String taskId, String what)
    {
        sendScreenshotProof(null, taskId, what, null);
    }

    /**
     * As above, for the board the tile card came from (null = the selected board); tileName is stamped on the shot
     * with the player's name, that bingo's code and the time.
     */
    void sendScreenshotProof(String teamBoardId, String taskId, String what, String tileName)
    {
        // 5-second cooldown between proof screenshots (owner, 2026-11-12)
        long since = System.currentTimeMillis() - lastProofShotAt;
        if (since < SCREENSHOT_COOLDOWN_MS)
        {
            notice("Wait " + ((SCREENSHOT_COOLDOWN_MS - since + 999) / 1000) + "s before the next screenshot.", true);
            return;
        }
        String team = teamBoardId != null && !teamBoardId.isEmpty() ? teamBoardId : shownBoardId();
        ApiClient a = api;
        if (taskId == null || team == null || team.isEmpty() || a == null || !a.configured())
        {
            notice("Connect the plugin to your team first.", true);
            return;
        }
        // the bank PIN is checked below, on the client thread, as the frame is captured (this runs on the UI thread)
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            notice("Log in to take a screenshot.", true);
            return;
        }
        lastProofShotAt = System.currentTimeMillis();
        proofFrame(image -> {
            // checked again as the frame is captured: a logout or the bank PIN may have come up since the click
            if (client.getGameState() != GameState.LOGGED_IN || bankPinOpen())
            {
                lastProofShotAt = 0;
                notice("Close the bank PIN to take a screenshot.", true);
                return;
            }
            // read the player's name here, on the client thread, as the frame is captured
            String player = client.getLocalPlayer() == null ? null : client.getLocalPlayer().getName();
            executor.execute(() -> {
            try
            {
                BufferedImage img = ImageUtil.bufferedImageFromImage(image);
                if (config.screenshotSidebar()) img = withWindow(img);
                ApiModels.BoardResponse cb = currentBoard();
                String kw = cb != null && cb.event != null ? cb.event.keyword : null;
                java.time.ZonedDateTime taken = java.time.ZonedDateTime.now();
                img = ScreenshotStamp.stamp(img, kw, taken, player, tileName == null ? what : tileName);
                byte[] shot = ScreenshotEncode.jpeg(img, ApiClient.MAX_SCREENSHOT_BYTES);
                if (shot == null) return;
                ApiResult<ApiModels.ProofResponse> r = a.uploadTaskScreenshot(team, taskId, shot, kw,
                    taken.withZoneSameInstant(java.time.ZoneOffset.UTC).toInstant().toString(), what);
                if (r.ok())
                {
                    notice("Screenshot sent for " + what + " - waiting for review.", true);
                    boardVersions.clear();
                    pollBoardSafely();
                }
                else if (r.status == 404)
                {
                    // the selected board is gone (team deleted, or the bingo is not live yet): reload the list
                    notice("That board isn't available any more. Pick your board again in the ForgeBingo panel.", true);
                    boardVersions.clear();
                    pollBoardSafely();
                }
                else if ("invalid_targets".equals(r.errorCode))
                {
                    notice("The server doesn't take plugin screenshots yet - add proof on the website for now.", true);
                }
                else if ("too_soon".equals(r.errorCode))
                {
                    notice("Screenshot already sent a moment ago.", true);
                }
                else
                {
                    notice("Screenshot not sent: " + r.describe(), true);
                }
            }
            catch (Exception ex)
            {
                log.debug("screenshot proof failed", ex);
                notice("Screenshot not sent: something went wrong taking it.", true);
            }
            });
        });
    }

    /** The bank PIN keypad (interface group 213) is showing. */
    private boolean bankPinOpen()
    {
        return widgetShowing(BANK_PIN_GROUP);
    }

    private static final int BANK_PIN_GROUP = 213;

    /**
     * Opt-in whole-window shot: paints the RuneLite window (sidebar, plugin panel) on the Swing thread and lays the
     * game frame over the canvas area (the GPU-drawn game paints blank through Swing). Any failure keeps the game shot.
     */
    private BufferedImage withWindow(BufferedImage game)
    {
        final java.awt.Canvas canvas = client.getCanvas();
        if (canvas == null) return game;
        final BufferedImage[] window = new BufferedImage[1];
        final java.awt.Rectangle[] at = new java.awt.Rectangle[1];
        try
        {
            SwingUtilities.invokeAndWait(() -> {
                // only while our own panel is the open one: another plugin's panel (notes, bank value) stays out
                if (panel == null || !panel.isShowing()) return;
                java.awt.Window w = SwingUtilities.getWindowAncestor(canvas);
                if (!(w instanceof javax.swing.RootPaneContainer)) return;
                java.awt.Container pane = ((javax.swing.RootPaneContainer) w).getContentPane();
                int pw = pane.getWidth();
                int ph = pane.getHeight();
                if (pw <= 0 || ph <= 0 || pw > WindowShot.MAX_SIDE || ph > WindowShot.MAX_SIDE) return;
                BufferedImage shot = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D g = shot.createGraphics();
                try
                {
                    pane.paint(g);
                }
                finally
                {
                    g.dispose();
                }
                java.awt.Point p = SwingUtilities.convertPoint(canvas, 0, 0, pane);
                window[0] = shot;
                at[0] = new java.awt.Rectangle(p.x, p.y, canvas.getWidth(), canvas.getHeight());
            });
        }
        catch (Exception ex)
        {
            log.debug("window screenshot failed", ex);
            return game;
        }
        if (window[0] == null || at[0] == null) return game;
        return WindowShot.compose(window[0], game, at[0].x, at[0].y, at[0].width, at[0].height);
    }

    private void maybeUploadScreenshot(GameEvent event, ApiModels.EventResult result)
    {
        maybeUploadScreenshot(event, result, 0);
    }

    /**
     * The server often answers before the screenshot of the same drop is stored (it is taken on the next frame and
     * saved on this same worker), so when one is still being taken, try again shortly instead of giving up. This
     * race is why almost no drop screenshots were uploaded (2026-11-11).
     */
    private void maybeUploadScreenshot(GameEvent event, ApiModels.EventResult result, int attempt)
    {
        // a duplicate is the server's stored answer to a send whose reply was lost (the queue sent it again): it
        // still names the entries this drop made, so the picture still goes to them. It is uploaded once at most:
        // the first answer to arrive takes the picture out of the map.
        if (result == null || !result.isMatched() || !config.screenshotOnMatch())
        {
            screenshots.remove(event.id);
            return;
        }
        boolean pending = false;
        for (String key : shotsInProgress) if (key.startsWith(event.id + "#")) { pending = true; break; }
        if (pending && attempt < 24 && running)
        {
            executor.schedule(() -> maybeUploadScreenshot(event, result, attempt + 1), 250, TimeUnit.MILLISECONDS);
            return;
        }
        Screenshot s = screenshots.remove(event.id);
        if (s == null)
        {
            // A drop queued before a restart counts here, but its proof screenshot lived only in memory and is gone
            // (owner, 2026-11-25). Tell the player once so they can add a picture on the board if their clan needs it.
            if (restoredNoProof.remove(event.id) && !restoredNoProofNoticed)
            {
                restoredNoProofNoticed = true;
                notice("A ForgeBingo drop saved before you last closed the client was counted, but its proof screenshot could not be saved. Open that tile on the board and send a screenshot if your clan needs proof.", true);
            }
            return;
        }
        // the frames that were really taken (a frame is skipped when the bank PIN is up or the game logged out)
        List<Integer> shots = new ArrayList<>();
        for (int i = 0; i < s.raw.length; i++) if (s.raw[i] != null && s.taken[i] != null) shots.add(i);
        if (shots.isEmpty()) return;
        // One upload per team, attached to that team's submissions from matched[].submissionId; the file is named
        // after the (first) task this drop counted for, e.g. big-boners_3f2a9c1e.png
        Map<String, String> titles = tileTitles;
        for (ProofPlan.Team t : ProofPlan.teams(result.matched, MatchNotices.taskNames(currentBoard()), titles))
        {
            try
            {
                // that team's own board: its bingo's code word and the names of the tiles the drop counted for
                ApiClient a = api;
                if (a == null) return; // the plugin was switched off meanwhile
                ApiResult<ApiModels.BoardResponse> br = a.board(t.teamId);
                ApiModels.BoardResponse tb = br.ok() ? br.body : null;
                String kw = tb != null && tb.event != null ? tb.event.keyword : null;
                String tiles = ProofPlan.tiles(t.tileIds, tb, titles);
                // Several frames of one drop go up as ONE picture, stacked top to bottom in the order taken
                // (owner, 2026-11-14). Each frame keeps its own stamp: its time and which it is ("1 of 2").
                List<BufferedImage> frames = new ArrayList<>();
                java.time.ZonedDateTime first = null;
                for (int n = 0; n < shots.size(); n++)
                {
                    int i = shots.get(n);
                    BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(s.raw[i]));
                    if (img == null) continue;
                    frames.add(ScreenshotStamp.stamp(img, kw, s.taken[i], s.player, ProofPlan.stampText(tiles, n + 1, shots.size())));
                    if (first == null) first = s.taken[i];
                }
                BufferedImage stacked = ScreenshotStack.vertical(frames);
                if (stacked == null) continue;
                byte[] stamped = ScreenshotEncode.jpeg(stacked, ApiClient.MAX_SCREENSHOT_BYTES);
                if (stamped == null || stamped.length > ApiClient.MAX_SCREENSHOT_BYTES)
                {
                    // too big even at low quality: send the first frame alone rather than nothing
                    stamped = ScreenshotEncode.jpeg(frames.get(0), ApiClient.MAX_SCREENSHOT_BYTES);
                    if (stamped == null) continue;
                }
                String capturedAt = first.withZoneSameInstant(java.time.ZoneOffset.UTC).toInstant().toString();
                sendProof(new ProofUpload(event.id, t.teamId, stamped, t.submissionIds, kw, capturedAt, t.label, s.at), 1);
            }
            catch (java.io.IOException ex)
            {
                log.debug("screenshot stamp failed", ex);
            }
        }
    }

    /** One team's finished (stamped) proof picture, on its way to the server. */
    private static final class ProofUpload
    {
        final String eventId;
        final String team;
        final byte[] picture;
        final List<String> submissionIds;
        final String keyword;
        final String capturedAt;
        final String label;
        /** when the drop's screenshot was started: the picture is given up with the other screenshots' time limit */
        final long shotAt;

        ProofUpload(String eventId, String team, byte[] picture, List<String> submissionIds, String keyword, String capturedAt, String label, long shotAt)
        {
            this.eventId = eventId;
            this.team = team;
            this.picture = picture;
            this.submissionIds = submissionIds;
            this.keyword = keyword;
            this.capturedAt = capturedAt;
            this.label = label;
            this.shotAt = shotAt;
        }
    }

    /**
     * Proof pictures waiting for another try after the server was busy or unreachable. They count towards the same
     * MAX_SCREENSHOTS slots as screenshots waiting for the server's answer.
     */
    private final Map<Long, ProofUpload> proofRetries = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong proofRetryIds = new java.util.concurrent.atomic.AtomicLong();

    /**
     * Sends one team's proof picture. When the server is busy (429), unreachable or failing (5xx), the same picture
     * is sent again a little later, a few times, instead of being lost; an answer that will not change (the board
     * is gone, the bingo is not open, the entry was already reviewed...) is not retried. The player is told once,
     * only when the picture is finally given up. Worker thread.
     */
    private void sendProof(ProofUpload u, int attempt)
    {
        ApiClient a = api;
        if (!running || a == null) return; // the plugin was switched off meanwhile
        ApiResult<ApiModels.ProofResponse> r = a.uploadProof(u.team, u.picture, u.submissionIds, u.keyword, u.capturedAt, u.label);
        if (r.ok())
        {
            activity.pictureSent(u.eventId);
            showActivity();
            return;
        }
        long delay = ProofPlan.retryDelay(r.status, r.errorCode, r.retryAfterMillis, attempt, System.currentTimeMillis() - u.shotAt, SCREENSHOT_TTL_MILLIS);
        // kept for another try only while there is room: waiting pictures share the screenshots' few slots
        if (delay >= 0 && screenshots.size() + proofRetries.size() < MAX_SCREENSHOTS)
        {
            long id = proofRetryIds.incrementAndGet();
            proofRetries.put(id, u);
            log.debug("proof upload failed ({}), try {} of {} in {} ms", r.describe(), attempt + 1, ProofPlan.UPLOAD_TRIES, delay);
            executor.schedule(() -> retryProof(id, attempt + 1), delay, TimeUnit.MILLISECONDS);
            return;
        }
        notice("Screenshot upload failed: " + r.describe(), true);
    }

    private void retryProof(long id, int attempt)
    {
        // gone when a newer drop needed its slot, or the plugin was switched off
        ProofUpload u = proofRetries.remove(id);
        if (u == null) return;
        try
        {
            sendProof(u, attempt);
        }
        catch (RuntimeException ex)
        {
            log.debug("proof upload retry failed", ex);
        }
    }

    /** Gives up the oldest picture waiting for another upload try, to make room. @return false when none waits */
    private boolean dropOldestProofRetry()
    {
        Long oldest = null;
        long at = Long.MAX_VALUE;
        for (Map.Entry<Long, ProofUpload> r : proofRetries.entrySet()) if (r.getValue().shotAt < at) { at = r.getValue().shotAt; oldest = r.getKey(); }
        if (oldest == null) return false;
        if (proofRetries.remove(oldest) != null) notice("Screenshot upload failed: the server stayed busy and newer drops needed the room.", true);
        return true;
    }

    // ---------- user-visible notices ----------

    private void notice(String text, boolean important)
    {
        notifyPanel(p -> p.addNotice(text));
        if (config.chatNotices() && important && client.getGameState() == GameState.LOGGED_IN)
        {
            clientThread.invoke(() -> chatMessageManager.queue(QueuedMessage.builder()
                .type(ChatMessageType.CONSOLE)
                .runeLiteFormattedMessage(net.runelite.client.util.ColorUtil.wrapWithColorTag(
                    "[ForgeBingo] " + text.replace("<", "").replace(">", ""),
                    config.chatColor() == null ? ForgeBingoConfig.DEFAULT_CHAT_COLOR : config.chatColor()))
                .build()));
        }
    }

    private final class QueueListener implements QueueProcessor.Listener
    {
        @Override
        public void onAccepted(GameEvent event, ApiModels.EventResult result)
        {
            if (result != null && result.automationDisabled)
            {
                screenshots.remove(event.id);
                if (!automationNoticeShown)
                {
                    automationNoticeShown = true;
                    notice("Automatic reporting is currently disabled by the ForgeBingo admins; events are not recorded.", true);
                }
                return;
            }
            automationNoticeShown = false;
            // the pictures of a raid's chest also go to the tiles its completion just counted for
            ApiModels.EventResult proofFor = result;
            if (result != null && !result.duplicate && GameEvent.RAID_CHEST.equals(event.sourceType) && ProofPolicy.raidWithChest(event.sourceName))
            {
                proofFor = ProofPlan.withRaidKill(result, freshRaidKc());
            }
            if (result != null && result.isMatched() && !result.duplicate && GameEvent.KC.equals(event.sourceType) && ProofPolicy.raidWithChest(event.sourceName))
            {
                raidKcResult = result;
                raidKcAt = System.currentTimeMillis();
            }
            if (result != null && result.isMatched() && !result.duplicate)
            {
                // what to say (core/MatchNotices): tiles already finished before this drop still count, but get no
                // second celebration; a hidden secret tile never shows its amounts
                MatchNotices.Plan plan = MatchNotices.plan(result, currentBoard(), tileTitles, hiddenTiles);
                // one quiet line in the side panel: what just counted
                if (plan.counted != null)
                {
                    activity.counted(event.id, plan.counted, System.currentTimeMillis());
                    showActivity();
                }
                for (MatchNotices.Notice n : plan.notices)
                {
                    notice(n.text, config.chatNotices());
                    if (n.taskEffect != null && config.effects()) effects.push(EffectsOverlay.Kind.TASK, n.taskEffect);
                }
                maybeUploadScreenshot(event, proofFor);
                // celebrated on the tile's own team board (the server names it), else the board on screen
                for (MatchNotices.Celebration c : plan.celebrations)
                {
                    celebrateTile(c.teamBoardId != null ? c.teamBoardId : shownBoardId(), c.tileId, c.title);
                }
                boardVersions.clear();
                executor.execute(ForgeBingoPlugin.this::pollBoardSafely);
            }
            else if (proofFor != result)
            {
                // the chest's own loot counted for nothing, but the raid did: its pictures are that raid's proof
                maybeUploadScreenshot(event, proofFor);
            }
            else if (result != null && result.duplicate && result.isMatched())
            {
                // the first send counted but its answer was lost: no second "Counted" message, but the picture
                // still goes up, or those entries would sit there without proof (2026-11-22)
                maybeUploadScreenshot(event, result);
            }
            else
            {
                screenshots.remove(event.id);
            }
        }

        @Override
        public void onDropped(GameEvent event, String reason)
        {
            screenshots.remove(event.id);
            if (reason != null && (reason.contains("too old") || reason.contains("occurred_outside_event")))
            {
                // review, 2026-11-22: say what to do, not just that it failed
                notice("Not counted: " + event.label() + " waited too long to send (over 23 hours offline). "
                    + "Send a screenshot with Send screenshot on the tile, or on the website.", true);
            }
            else
            {
                notice("Dropped " + event.sourceType + " event (" + event.label() + "): " + reason, true);
            }
            if (reason != null && reason.contains("not_a_member"))
            {
                boardVersions.clear();
                executor.execute(ForgeBingoPlugin.this::pollBoardSafely);
            }
        }

        @Override
        public void onAuthProblem(String message)
        {
            notifyPanel(p -> p.setStatus(false, message));
            notice(message, true);
        }

        @Override
        public void onQueueChanged(int size, String lastError)
        {
            notifyPanel(p -> p.setQueueSize(size, lastError));
        }
    }
}
