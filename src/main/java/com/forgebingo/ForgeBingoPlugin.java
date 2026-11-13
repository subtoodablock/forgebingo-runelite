package com.forgebingo;

import com.forgebingo.core.ActivityWatcher;
import com.forgebingo.core.ApiClient;
import com.forgebingo.core.ApiModels;
import com.forgebingo.core.ApiResult;
import com.forgebingo.core.ChatParsers;
import com.forgebingo.core.ConnectionStatus;
import com.forgebingo.core.ErrorClass;
import com.forgebingo.core.EventDeduplicator;
import com.forgebingo.core.GameEvent;
import com.forgebingo.core.InventoryDiff;
import com.forgebingo.core.ItemNames;
import com.forgebingo.core.OfflineQueue;
import com.forgebingo.core.PetDetector;
import com.forgebingo.core.QueueProcessor;
import com.forgebingo.core.QueuedEvent;
import com.forgebingo.core.Sanitizer;
import com.forgebingo.core.ScreenshotStamp;
import com.forgebingo.core.ScreenshotEncode;
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
    static final String VERSION = "2.0.0";
    private static final long BOARD_POLL_SECONDS = 8;
    /** spot animation id of the level-up fireworks (the one the game plays at 99) */
    private static final int LEVEL_UP_FIREWORKS = 199;
    /** long enough to outlast a rate-limit pause (the server answers 429 with a 60 s wait) */
    private static final long SCREENSHOT_TTL_MILLIS = 10 * 60_000L;
    private static final int MAX_SCREENSHOTS = 8;
    /** gameval InventoryID.INV (the backpack). */
    private static final int INVENTORY_CONTAINER_ID = 93;
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
    @Inject private ScheduledExecutorService executor;
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

    private ForgeBingoPanel panel;
    private NavigationButton navigationButton;
    private ScheduledFuture<?> queueFuture;
    private ScheduledFuture<?> boardFuture;
    /** teamBoardId -> updatedAt last rendered; the board is refetched when /v1/boards reports a change. */
    private final Map<String, String> boardVersions = new ConcurrentHashMap<>();
    /** tileId -> title of the board on screen, to name tiles in notices. */
    private volatile Map<String, String> tileTitles = new ConcurrentHashMap<>();
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
        running = true;
        // an address saved by an early build was the placeholder: drop it so the live default applies
        if (ApiClient.isPlaceholder(config.apiBaseUrl()))
        {
            configManager.unsetConfiguration(ForgeBingoConfig.GROUP, "apiBaseUrl");
        }
        api = new ApiClient(okHttpClient, gson, this::effectiveBaseUrl, config::apiKey, VERSION);
        queue = new OfflineQueue(queueFile(), gson);
        queue.load();
        processor = new QueueProcessor(queue, api::sendEvent, new QueueListener(), System::currentTimeMillis, random);

        SwingUtilities.invokeLater(() -> {
            if (!running)
            {
                return;
            }
            ForgeBingoPanel.usePixelFonts(config.panelFont() == ForgeBingoConfig.PanelFont.RUNESCAPE);
            PanelUi.animations = config.panelAnimations();
            panel = new ForgeBingoPanel(this, api);
            panel.setImps(config.panelImps());
            panel.setRain(config.panelRain());
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
        if (queueFuture != null) queueFuture.cancel(false);
        if (boardFuture != null) boardFuture.cancel(false);
        overlayManager.remove(effects);
        effects.clear();
        if (boardInput != null) boardInput.unregister(mouseManager, keyManager);
        boardInput = null;
        overlayManager.remove(boardOverlay);
        boardOverlay.reset();
        celebrated.clear();
        blackoutShown.clear();
        currentBoard = null;
        if (navigationButton != null) clientToolbar.removeNavigation(navigationButton);
        navigationButton = null;
        ForgeBingoPanel closing = panel;
        if (closing != null) SwingUtilities.invokeLater(closing::dispose);
        panel = null;
        screenshots.clear();
        levels.clear();
        dedup.clear();
        petDetector.reset();
        lastInventory = null;
        lastContainers.clear();
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
        okhttp3.HttpUrl u = ApiClient.parseBaseUrl(raw);
        okhttp3.HttpUrl def = ApiClient.parseBaseUrl(ForgeBingoConfig.DEFAULT_BASE_URL);
        if (u == null || def == null) return raw;
        String host = u.host();
        if (host.equals(def.host()) || "localhost".equals(host) || "127.0.0.1".equals(host) || host.equalsIgnoreCase(config.trustedApiHost().trim()))
        {
            pendingHost = null;
            return raw;
        }
        pendingHost = host;
        askToTrust(host);
        return null;
    }

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
                    p.setScope((me.body.displayName == null ? "" : "Signed in as " + me.body.displayName + "\n") + ConnectionStatus.keyScope(me.body));
                });
                helloPending = true;
                helloNotBefore = 0;
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
            List<ConnectionStatus.Line> lines = ConnectionStatus.fromHello(r.body);
            notifyPanel(p -> p.setIdentity(lines));
            // Logging in can put the player on a team (the organizer listed their RSN): fetch the boards straight
            // away when the server reports a bingo we did not know about, so the new board shows without a wait.
            java.util.Set<String> now = new java.util.HashSet<>();
            if (r.body.memberships != null) for (ApiModels.Membership m : r.body.memberships) if (m != null && m.eventId != null) now.add(m.eventId);
            boolean newBingo = helloEvents != null && !helloEvents.containsAll(now);
            helloEvents = now;
            // bingos whose board hasn't opened yet: the side panel counts down to their start
            List<ApiModels.Membership> waiting = new ArrayList<>();
            if (r.body.memberships != null) for (ApiModels.Membership m : r.body.memberships) if (m != null && Boolean.FALSE.equals(m.boardOpen)) waiting.add(m);
            notifyPanel(p -> p.setUpcoming(waiting));
            if (newBingo)
            {
                notice("You were added to a new bingo board.", false);
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
                    BufferedImage read = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
                    if (read != null && read.getWidth() <= 256 && read.getHeight() <= 256) got = read;
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
        String team = config.activeBoardId();
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
        if ("panelAnimations".equals(e.getKey()) || "panelImps".equals(e.getKey()) || "panelRain".equals(e.getKey()))
        {
            boolean animate = config.panelAnimations();
            boolean imps = config.panelImps();
            boolean rain = config.panelRain();
            ForgeBingoPanel p = panel;
            if (p != null) javax.swing.SwingUtilities.invokeLater(() -> {
                p.setAnimations(animate);
                p.setImps(imps);
                p.setRain(rain);
            });
            return;
        }
        if ("apiBaseUrl".equals(e.getKey())) askedHost = null; // ask again for a newly typed address
        if ("apiKey".equals(e.getKey()) || "apiBaseUrl".equals(e.getKey()) || "trustedApiHost".equals(e.getKey()))
        {
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
        clientThread.invoke(() -> {
            List<ApiModels.CatalogueItem> named = new ArrayList<>();
            for (int id : ids)
            {
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
            itemNames.setBoard(named);
            notifyPanel(ForgeBingoPanel::iconsChanged); // the per-item lists can show real names now
        });
    }

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
        ApiResult<ApiModels.BoardsResponse> list = a.boards();
        if (!list.ok() || list.body == null)
        {
            boardFailures++;
            notifyPanel(p -> p.setStatus(false, "Boards: " + list.describe()));
            return;
        }
        boardFailures = 0;
        List<ApiModels.BoardSummary> boards = list.body.boards;
        // Shared reveal: a board that appears while we're running has just opened for everyone (the bingo started).
        // Switch to it, say so in chat and open it over the game with a Legendary animation.
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (ApiModels.BoardSummary b : boards) ids.add(b.teamBoardId);
        java.util.Set<String> before = knownBoards;
        knownBoards = ids;
        if (before != null)
        {
            for (ApiModels.BoardSummary b : boards)
            {
                if (before.contains(b.teamBoardId) || !b.active) continue;
                notice("The bingo is live! " + (b.eventName == null ? "Your board" : b.eventName) + " is open. Good luck!", true);
                configManager.setConfiguration(ForgeBingoConfig.GROUP, "activeBoardId", b.teamBoardId);
                boardVersions.clear();
                String ev = b.eventId;
                notifyPanel(p -> p.removeUpcoming(ev));
                if (config.boardOverlayAnimations()) boardOverlay.openReveal();
                break;
            }
        }
        String wanted = config.activeBoardId();
        ApiModels.BoardSummary selected = null;
        for (ApiModels.BoardSummary b : boards)
        {
            if (b.teamBoardId.equals(wanted)) selected = b;
        }
        if (selected == null)
        {
            for (ApiModels.BoardSummary b : boards)
            {
                if (b.active) { selected = b; break; }
            }
            if (selected == null && !boards.isEmpty()) selected = boards.get(0);
        }
        final ApiModels.BoardSummary sel = selected;
        notifyPanel(p -> p.setBoards(boards, sel == null ? null : sel.teamBoardId));
        if (sel == null)
        {
            return;
        }
        String version = sel.updatedAt == null ? "?" : sel.updatedAt;
        if (version.equals(boardVersions.get(sel.teamBoardId)))
        {
            return;
        }
        ApiResult<ApiModels.BoardResponse> r = a.board(sel.teamBoardId);
        if (!r.ok() || r.body == null)
        {
            boardFailures++;
            notifyPanel(p -> p.setStatus(false, "Board: " + r.describe()));
            return;
        }
        ApiResult<ApiModels.LeaderboardResponse> lb = a.leaderboard(sel.teamBoardId);
        boardVersions.put(sel.teamBoardId, version);
        Map<String, String> titles = new ConcurrentHashMap<>();
        for (ApiModels.Tile t : r.body.tiles)
        {
            if (t.id != null && t.title != null) titles.put(t.id, t.title);
        }
        tileTitles = titles;
        indexBoardItems(r.body);
        currentBoard = r.body;
        celebrateChanges(sel.teamBoardId, r.body);
        notifyPanel(p -> p.setBoard(r.body, lb.ok() ? lb.body : null, itemManager));
    }

    // ---------- celebrations (mirror the website: tile banner + fireworks, line sweep, BLACKOUT show) ----------

    /** Compares the freshly fetched board with what was celebrated before; the first fetch only records. */
    private void celebrateChanges(String teamBoardId, ApiModels.BoardResponse board)
    {
        Set<String> seen = celebrated.get(teamBoardId);
        Set<String> now = new HashSet<>();
        for (ApiModels.Tile t : board.tiles)
        {
            if (t.completed && t.id != null) now.add(t.id);
        }
        boolean all = !board.tiles.isEmpty() && now.size() >= board.tiles.size();
        if (seen == null)
        {
            // first look at this board in this session: no replay of old history, but a finished board still gets its show
            celebrated.put(teamBoardId, ConcurrentHashMap.newKeySet());
            celebrated.get(teamBoardId).addAll(now);
            if (all && config.effects() && blackoutShown.add(teamBoardId)) effects.push(EffectsOverlay.Kind.BLACKOUT, "");
            return;
        }
        int size = board.size();
        int before = seen.size();
        int linesBefore = countLines(seen, board, size);
        // Newly finished tiles found on a refresh were finished by a teammate (your own drops celebrate the moment the
        // server answers, which marks them seen first): no on-screen banner, a fireworks card in the side panel instead.
        for (ApiModels.Tile t : board.tiles)
        {
            if (t.completed && t.id != null && seen.add(t.id) && config.effects())
            {
                String title = TileInfo.shownTitle(t);
                notifyPanel(p -> p.teamFinished(title));
            }
        }
        seen.addAll(now);
        int linesAfter = countLines(seen, board, size);
        if (!config.effects()) return;
        if (all && blackoutShown.add(teamBoardId)) effects.push(EffectsOverlay.Kind.BLACKOUT, "");
        else if (linesAfter > linesBefore && now.size() > before) effects.push(EffectsOverlay.Kind.LINE, linesAfter - linesBefore > 1 ? "Double line complete!" : "Line complete!");
    }

    private static int countLines(Set<String> doneIds, ApiModels.BoardResponse board, int size)
    {
        boolean[][] d = new boolean[size][size];
        for (ApiModels.Tile t : board.tiles)
        {
            if (t.id != null && doneIds.contains(t.id) && t.row < size && t.col < size) d[t.row][t.col] = true;
        }
        int n = 0;
        for (int i = 0; i < size; i++)
        {
            boolean row = true;
            boolean col = true;
            for (int j = 0; j < size; j++)
            {
                row &= d[i][j];
                col &= d[j][i];
            }
            if (row) n++;
            if (col) n++;
        }
        boolean diag = true;
        boolean anti = true;
        for (int i = 0; i < size; i++)
        {
            diag &= d[i][i];
            anti &= d[i][size - 1 - i];
        }
        if (diag) n++;
        if (anti) n++;
        return n;
    }

    /** Banner over the game plus the level-up fireworks on the player, once per tile. */
    private void celebrateTile(String teamBoardId, String tileId, String title)
    {
        Set<String> seen = celebrated.computeIfAbsent(teamBoardId == null ? "" : teamBoardId, k -> ConcurrentHashMap.newKeySet());
        if (!seen.add(tileId) || !config.effects()) return;
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
        ev.npcName = npc.getName();
        addItems(ev, e.getItems());
        submit(ev, true);
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
        // a moment later, so the chat line (and the purple light where the raid shows one) is drawn
        executor.schedule(() -> {
            if (!running) return;
            drawManager.requestNextFrameListener(image -> {
                if (client.getGameState() != GameState.LOGGED_IN || bankPinOpen()) return;
                executor.execute(() -> {
                    try
                    {
                        BufferedImage img = ImageUtil.bufferedImageFromImage(image);
                        if (config.screenshotSidebar()) img = withWindow(img);
                        byte[] shot = ScreenshotEncode.jpeg(img, ApiClient.MAX_SCREENSHOT_BYTES);
                        if (shot == null) return;
                        raidShotTaken = java.time.ZonedDateTime.now();
                        raidShotAt = System.currentTimeMillis();
                        raidShot = shot;
                    }
                    catch (Exception ex)
                    {
                        log.debug("special loot screenshot failed", ex);
                    }
                });
            });
        }, 600, TimeUnit.MILLISECONDS);
    }

    @Subscribe
    public void onChatMessage(ChatMessage e)
    {
        String type = e.getType() == null ? "" : e.getType().name();
        String msg = e.getMessage();
        int tick = client.getTickCount();
        watchSpecialLoot(msg, tick);
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
                submit(ev, false);
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
            if (!config.sendKc()) return;
            GameEvent ev = base(GameEvent.KC);
            ev.sourceName = k.boss; // one event per kill; the server matches the name against its catalogue
            submit(ev, false);
        });
        ChatParsers.clueCompletion(msg).ifPresent(c -> {
            petDetector.onSource(c.sourceName(), tick);
            if (!config.sendKc()) return;
            // One completion = one kc event for the clue_<tier> catalogue source (the casket loot is a separate clue event).
            GameEvent ev = base(GameEvent.KC);
            ev.sourceName = c.sourceName();
            submit(ev, false);
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
        Item[] slots = c.getItems();
        int[] ids = new int[slots.length];
        int[] qty = new int[slots.length];
        for (int i = 0; i < slots.length; i++)
        {
            ids[i] = slots[i].getId();
            qty[i] = slots[i].getQuantity();
        }
        Map<Integer, Integer> now = InventoryDiff.counts(ids, qty);
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
        // a ground item vanishing next to us is most likely our pickup: picked-up items (PvP loot included) never count
        Player me = client.getLocalPlayer();
        TileItem item = e.getItem();
        if (me == null || item == null || e.getTile() == null) return;
        WorldPoint at = e.getTile().getWorldLocation();
        WorldPoint mine = me.getWorldLocation();
        if (at != null && mine != null && at.getPlane() == mine.getPlane() && at.distanceTo2D(mine) <= 1)
        {
            activityWatcher.onGroundItemGone(itemManager.canonicalize(item.getId()), client.getTickCount());
        }
    }

    @Subscribe
    public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged e)
    {
        activityWatcher.onGrandExchange(client.getTickCount());
    }

    /** Wilderness (varbit 5963) or a PvP / Deadman world: nothing from the backpack watcher counts there. */
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
        Integer previous = levels.put(skill, level);
        if (previous == null || level <= previous || !config.sendLevels()) return;
        GameEvent ev = base(GameEvent.LEVEL);
        ev.skill = skill.getName().toLowerCase(Locale.ROOT);
        ev.level = level;
        submit(ev, false);
        int total = client.getTotalLevel();
        if (total > 0)
        {
            GameEvent overall = base(GameEvent.LEVEL);
            overall.skill = "overall";
            overall.level = total;
            submit(overall, false);
        }
    }

    /** what RuneLite's loot tracker calls The Fractured Archive's chest loot (it reports only the first chest opened) */
    private static final String ARCHIVE_LOOT_NAME = "Fractured Archive";
    private final com.forgebingo.core.ChestOpens archiveChests = new com.forgebingo.core.ChestOpens();
    private boolean wasInstance;

    /**
     * The Fractured Archive has two reward chests: the standard one, and a second for whoever rolled the unique
     * (owner, 2026-11-13). Each one opened sends its own loot with the loot window as proof.
     */
    @Subscribe
    public void onWidgetLoaded(net.runelite.api.events.WidgetLoaded e)
    {
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

    @Subscribe
    public void onGameStateChanged(GameStateChanged e)
    {
        if (e.getGameState() == GameState.LOGIN_SCREEN || e.getGameState() == GameState.HOPPING) archiveChests.reset();
        // logging out or hopping closes the board (map LOADING does not; the input ignores clicks until LOGGED_IN)
        if (e.getGameState() == GameState.LOGIN_SCREEN || e.getGameState() == GameState.HOPPING || e.getGameState() == GameState.CONNECTION_LOST) boardOverlay.close();
        if (e.getGameState() == GameState.LOGIN_SCREEN || e.getGameState() == GameState.HOPPING) requestWomUpdate();
        if (e.getGameState() == GameState.LOGGED_IN)
        {
            helloPending = true; // another account may have logged in: verify again
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
        // one event (and one screenshot) per activity per tick: a chest or shop trip with several uniques counts each
        Map<String, GameEvent> byActivity = new java.util.LinkedHashMap<>();
        for (ActivityWatcher.Drop d : activityWatcher.flush(tick, inPvp()))
        {
            if (!config.sendChestLoot()) continue;
            GameEvent ev = byActivity.computeIfAbsent(d.activity, k -> {
                GameEvent n = base(GameEvent.RAID_CHEST);
                n.sourceName = k; // the server knows every activity name (npc_sources aliases)
                return n;
            });
            ev.addItem(d.itemId, d.quantity);
        }
        for (GameEvent ev : byActivity.values()) submit(ev, true);
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
        if (!running || queue == null || !Sanitizer.validRsn(ev.rsn) || ev.accountHash == null || api == null || !api.configured())
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
        if (maybeScreenshot && couldCount(ev))
        {
            captureScreenshot(ev.id, chestWindow ? CHEST_SHOT_DELAYS_MS : DROP_SHOT_DELAYS_MS, chestWindow);
        }
        List<QueuedEvent> evicted = queue.add(new QueuedEvent(ev, System.currentTimeMillis()));
        for (QueuedEvent q : evicted) notice("Queue full, dropped oldest event: " + q.event.label(), true);
        notifyPanel(p -> p.setQueueSize(queue == null ? 0 : queue.size(), null));
        scheduleQueueDrain(0);
    }

    private void scheduleQueueDrain(long delayMillis)
    {
        if (!running) return;
        if (queueFuture != null) queueFuture.cancel(false);
        queueFuture = executor.schedule(() -> {
            QueueProcessor p = processor;
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
            if (next >= 0) scheduleQueueDrain(next);
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    // ---------- screenshots (kept in memory; uploaded only after server confirms a match) ----------

    /**
     * Could this event count for an open task on the current board? Only those get a screenshot, so ordinary
     * drops no longer fill the few screenshot slots before the drop that matters (the server still decides).
     */
    private boolean couldCount(GameEvent ev)
    {
        ApiModels.BoardResponse b = currentBoard();
        if (b == null || b.tiles == null) return true;
        // finished tiles count too: drops keep counting (with proof) until the bingo is over (owner, 2026-11-11)
        for (ApiModels.Tile t : b.tiles)
        {
            if (t == null || t.tasks == null) continue;
            for (ApiModels.Task k : t.tasks)
            {
                if (k == null) continue;
                if (k.itemIds != null && !k.itemIds.isEmpty())
                {
                    if (ev.items == null) continue;
                    for (GameEvent.Item it : ev.items) if (k.itemIds.contains(it.itemId)) return true;
                }
                else if (k.sourceType != null && k.sourceType.equals(ev.sourceType))
                {
                    return true;
                }
            }
        }
        // secret tiles hide their tasks: their items come as hiddenItemIds
        if (b.hiddenItemIds != null && ev.items != null)
        {
            for (GameEvent.Item it : ev.items) if (b.hiddenItemIds.contains(it.itemId)) return true;
        }
        return false;
    }

    private void captureScreenshot(String eventId, long[] delays, boolean chest)
    {
        // only in game, and never with the bank PIN keypad on screen (its layout would be in the proof)
        if (client.getGameState() != GameState.LOGGED_IN || bankPinOpen()) return;
        long now = System.currentTimeMillis();
        screenshots.values().removeIf(s -> now - s.at > SCREENSHOT_TTL_MILLIS);
        // full: drop the oldest instead of skipping this one (the newest drop is the likeliest to count)
        while (screenshots.size() >= MAX_SCREENSHOTS)
        {
            String oldest = null;
            long at = Long.MAX_VALUE;
            for (Map.Entry<String, Screenshot> s : screenshots.entrySet()) if (s.getValue().at < at) { at = s.getValue().at; oldest = s.getKey(); }
            if (oldest == null) break;
            screenshots.remove(oldest);
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
            executor.schedule(() -> requestDropFrame(eventId, index), delays[i], TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Milliseconds between spotting a drop and taking its screenshot. ~2 game ticks (owner, 2026-11-12): by then
     * the drop's chat message, the loot on the ground and the collection log pop-up are all on screen.
     */
    private static final long[] DROP_SHOT_DELAYS_MS = {1200};
    /** A reward chest's loot window: once as it opens and again a little later (sent as "1 of 2" and "2 of 2"). */
    private static final long[] CHEST_SHOT_DELAYS_MS = {300, 1800};
    /** minimum time between "Send screenshot" proofs */
    private static final long SCREENSHOT_COOLDOWN_MS = 5000;
    private volatile long lastProofShotAt;

    private void requestDropFrame(String eventId, int index)
    {
        String key = eventId + "#" + index;
        if (!running || !screenshots.containsKey(eventId)) { shotsInProgress.remove(key); return; }
        drawManager.requestNextFrameListener(image -> {
            if (client.getGameState() != GameState.LOGGED_IN || bankPinOpen())
            {
                shotsInProgress.remove(key);
                return;
            }
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
                java.time.ZonedDateTime taken = java.time.ZonedDateTime.now();
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
        });
    }

    /** Event ids whose screenshot is being taken right now (requested, not yet stored or given up). */
    private final java.util.Set<String> shotsInProgress = ConcurrentHashMap.newKeySet();

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
        String team = teamBoardId != null && !teamBoardId.isEmpty() ? teamBoardId : config.activeBoardId();
        ApiClient a = api;
        if (taskId == null || team == null || team.isEmpty() || a == null || !a.configured())
        {
            notice("Connect the plugin to your team first.", true);
            return;
        }
        if (client.getGameState() != GameState.LOGGED_IN || bankPinOpen())
        {
            notice("Log in (and close the bank PIN) to take a screenshot.", true);
            return;
        }
        lastProofShotAt = System.currentTimeMillis();
        drawManager.requestNextFrameListener(image -> {
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
        net.runelite.api.widgets.Widget pin = client.getWidget(BANK_PIN_GROUP, 0);
        return pin != null && !pin.isHidden();
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
        if (result == null || !result.isMatched() || result.duplicate)
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
        if (s == null) return;
        // the frames that were really taken (a frame is skipped when the bank PIN is up or the game logged out)
        List<Integer> shots = new ArrayList<>();
        for (int i = 0; i < s.raw.length; i++) if (s.raw[i] != null && s.taken[i] != null) shots.add(i);
        if (shots.isEmpty()) return;
        // One upload per team, attached to that team's submissions from matched[].submissionId.
        Map<String, List<String>> byTeam = new java.util.LinkedHashMap<>();
        // the file is named after the (first) task this drop counted for, e.g. big-boners_3f2a9c1e.png
        Map<String, String> labels = new java.util.HashMap<>();
        Map<String, String> taskNames = new java.util.HashMap<>();
        ApiModels.BoardResponse cb = currentBoard();
        if (cb != null && cb.tiles != null)
        {
            for (ApiModels.Tile t : cb.tiles)
            {
                if (t != null && t.tasks != null) for (ApiModels.Task k : t.tasks) if (k != null && k.id != null) taskNames.put(k.id, k.description);
            }
        }
        for (ApiModels.Match m : result.matched)
        {
            if (m.teamId == null || m.submissionId == null) continue;
            byTeam.computeIfAbsent(m.teamId, k -> new ArrayList<>());
            List<String> ids = byTeam.get(m.teamId);
            if (!ids.contains(m.submissionId) && ids.size() < 25) ids.add(m.submissionId);
            String name = m.taskId == null ? null : taskNames.get(m.taskId);
            if (name == null || name.isEmpty()) name = m.tileId == null ? null : tileTitles.get(m.tileId);
            if (name != null && !name.isEmpty()) labels.putIfAbsent(m.teamId, name);
        }
        // tiles this drop counted for, per team (a player can be on several teams in different bingos at once)
        Map<String, List<String>> tilesByTeam = new java.util.HashMap<>();
        for (ApiModels.Match m : result.matched)
        {
            if (m.teamId != null && m.tileId != null) tilesByTeam.computeIfAbsent(m.teamId, k -> new ArrayList<>()).add(m.tileId);
        }
        for (Map.Entry<String, List<String>> t : byTeam.entrySet())
        {
            try
            {
                // that team's own board: its bingo's code word and the names of the tiles the drop counted for
                ApiResult<ApiModels.BoardResponse> br = api.board(t.getKey());
                ApiModels.BoardResponse tb = br.ok() ? br.body : null;
                String kw = tb != null && tb.event != null ? tb.event.keyword : null;
                List<String> names = new ArrayList<>();
                for (String tileId : tilesByTeam.getOrDefault(t.getKey(), java.util.Collections.emptyList()))
                {
                    String title = null;
                    if (tb != null && tb.tiles != null) for (ApiModels.Tile x : tb.tiles) if (x != null && tileId.equals(x.id)) { title = TileInfo.shownTitle(x); break; }
                    if (title == null) title = tileTitles.get(tileId);
                    if (title != null && !title.isEmpty() && !names.contains(title)) names.add(title);
                }
                String tiles = names.isEmpty() ? null : names.size() <= 2 ? String.join(", ", names) : names.get(0) + ", " + names.get(1) + " +" + (names.size() - 2);
                for (int n = 0; n < shots.size(); n++)
                {
                    int i = shots.get(n);
                    // several frames of one drop say which they are, on the picture and in the file's name
                    String part = shots.size() > 1 ? (n + 1) + " of " + shots.size() : null;
                    String stampTile = part == null ? tiles : tiles == null ? part : tiles + " (" + part + ")";
                    String label = labels.get(t.getKey());
                    if (part != null) label = (label == null || label.isEmpty() ? "proof" : label) + " " + part;
                    BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(s.raw[i]));
                    if (img == null) continue;
                    byte[] stamped = ScreenshotEncode.jpeg(ScreenshotStamp.stamp(img, kw, s.taken[i], s.player, stampTile), ApiClient.MAX_SCREENSHOT_BYTES);
                    if (stamped == null) continue;
                    String capturedAt = s.taken[i].withZoneSameInstant(java.time.ZoneOffset.UTC).toInstant().toString();
                    ApiResult<ApiModels.ProofResponse> r = api.uploadProof(t.getKey(), stamped, t.getValue(), kw, capturedAt, label);
                    if (!r.ok())
                    {
                        notice("Screenshot upload failed: " + r.describe(), true);
                        break;
                    }
                }
            }
            catch (java.io.IOException ex)
            {
                log.debug("screenshot stamp failed", ex);
            }
        }
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
            if (result != null && result.isMatched() && !result.duplicate)
            {
                // tiles that were already finished before this drop: it still counts, but no second celebration
                // what was already finished before this drop (no second celebration), and task names for the messages
                java.util.Set<String> wasDone = new java.util.HashSet<>();
                java.util.Set<String> taskWasDone = new java.util.HashSet<>();
                Map<String, String> taskNames = new java.util.HashMap<>();
                ApiModels.BoardResponse before = currentBoard();
                if (before != null && before.tiles != null)
                {
                    for (ApiModels.Tile t : before.tiles)
                    {
                        if (t == null) continue;
                        if (t.completed && t.id != null) wasDone.add(t.id);
                        if (t.tasks != null) for (ApiModels.Task k : t.tasks)
                        {
                            if (k == null || k.id == null) continue;
                            if (k.completed) taskWasDone.add(k.id);
                            taskNames.put(k.id, k.description);
                        }
                    }
                }
                // a match's "completed" is its TASK; the tile is finished when matchedTiles says so
                java.util.Set<String> tileNowDone = new java.util.HashSet<>();
                if (result.matchedTiles != null)
                {
                    for (ApiModels.MatchedTile mt : result.matchedTiles) if (mt != null && mt.completed && mt.tileId != null) tileNowDone.add(mt.tileId);
                }
                java.util.Set<String> announced = new java.util.HashSet<>();
                for (ApiModels.Match m : result.matched)
                {
                    String title = m.tileId == null ? null : tileTitles.get(m.tileId);
                    String tileName = title == null ? "a tile" : title;
                    String state = "approved".equals(m.status) ? ""
                        : Boolean.FALSE.equals(m.verified) ? " - waiting for review (your name isn't verified yet)" : " - waiting for review";
                    if (m.tileId != null && wasDone.contains(m.tileId))
                    {
                        notice("Extra drop logged for " + tileName + " (already done)" + state, config.chatNotices());
                        continue;
                    }
                    String progress = m.requiredQuantity > 0 ? " (" + Math.min(m.currentQuantity, m.requiredQuantity) + "/" + m.requiredQuantity + ")" : "";
                    if (m.tileId != null && tileNowDone.contains(m.tileId))
                    {
                        if (announced.add("tile:" + m.tileId)) notice("Tile complete: " + tileName + state, config.chatNotices());
                    }
                    else if (m.completed && m.taskId != null && !taskWasDone.contains(m.taskId))
                    {
                        String task = taskNames.get(m.taskId);
                        String what = task == null || task.isEmpty() ? tileName : task + " (" + tileName + ")";
                        if (announced.add("task:" + m.taskId))
                        {
                            notice("Task complete: " + what + state, config.chatNotices());
                            if (config.effects()) effects.push(EffectsOverlay.Kind.TASK, task == null || task.isEmpty() ? tileName : task);
                        }
                    }
                    else
                    {
                        notice("Counted: " + tileName + progress + state, config.chatNotices());
                    }
                }
                maybeUploadScreenshot(event, result);
                for (String tileId : tileNowDone)
                {
                    if (!wasDone.contains(tileId)) celebrateTile(config.activeBoardId(), tileId, tileTitles.get(tileId));
                }
                boardVersions.clear();
                executor.execute(ForgeBingoPlugin.this::pollBoardSafely);
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
            notice("Dropped " + event.sourceType + " event (" + event.label() + "): " + reason, true);
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
