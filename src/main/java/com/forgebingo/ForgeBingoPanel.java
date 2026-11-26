package com.forgebingo;

import com.forgebingo.core.ApiClient;
import com.forgebingo.core.ApiModels;
import com.forgebingo.core.ConnectionStatus;
import com.forgebingo.core.ItemSlots;
import com.forgebingo.core.RejectedNotices;
import com.forgebingo.core.TileIcons;
import com.forgebingo.core.TileInfo;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * Sidebar. All methods must be called on the EDT (the plugin wraps calls in invokeLater).
 * Layout: one scrolling column of full-width, left-aligned cards. Every child of a BoxLayout column is
 * left-aligned on purpose: mixing Swing's default alignments (labels left, buttons/combos centred) shifts
 * the labels half a panel to the right and cuts their text off.
 */
class ForgeBingoPanel extends PluginPanel
{
    private static final int ICON = 32;
    private static final int PAD = 8;
    /** room kept for the vertical scrollbar */
    private static final int BAR = 8;
    /** usable width inside the column */
    /** RuneLite's sidebar shows a little less than PANEL_WIDTH (its own border), so keep a safety margin for text. */
    private static final int INNER = PluginPanel.PANEL_WIDTH - 2 * PAD - BAR - 14;
    /** wrap width for text inside a card (card padding + accent stripe) */
    private static final int CARD_TEXT = INNER - 22;
    // the look (palette, fonts, painted cards and bars) lives in PanelUi
    // MUTED, TEXT and GOLD follow the theme (see setTheme)
    private static Color MUTED = PanelUi.MUTED;
    private static Color TEXT = PanelUi.TEXT;
    private static final Color ERROR = PanelUi.RED;
    private static Color GOLD = PanelUi.GOLD_SOFT;
    private static final Color GOOD = PanelUi.GREEN;
    private static final Color WAIT = PanelUi.AMBER;

    /** Sets the palette before any panel exists (plugin start-up, previews). */
    static void useTheme(PanelTheme theme)
    {
        PanelUi.applyTheme(theme);
        MUTED = PanelUi.MUTED;
        TEXT = PanelUi.TEXT;
        GOLD = PanelUi.GOLD_SOFT;
    }

    /**
     * Switches the look of everything already on screen (the "Theme" setting): the palette, who wanders over the
     * panel and what falls down it. Cards built later pick the new colours up anyway.
     */
    void setTheme(PanelTheme theme)
    {
        if (theme == null || theme == PanelUi.theme) return;
        Map<Color, Color> map = PanelUi.applyTheme(theme);
        MUTED = PanelUi.MUTED;
        TEXT = PanelUi.TEXT;
        GOLD = PanelUi.GOLD_SOFT;
        recolour(this, map);
        imps.setTheme(theme);
        revalidate();
        repaint();
    }

    private static void recolour(Component c, Map<Color, Color> map)
    {
        if (c.isForegroundSet() && map.containsKey(c.getForeground())) c.setForeground(map.get(c.getForeground()));
        if (c.isBackgroundSet() && map.containsKey(c.getBackground())) c.setBackground(map.get(c.getBackground()));
        if (c instanceof PanelUi.Card) ((PanelUi.Card) c).remap(map);
        if (c instanceof java.awt.Container) for (Component k : ((java.awt.Container) c).getComponents()) recolour(k, map);
    }

    // ---- fonts: "Clean" (the website's Cinzel headings over a smooth sans-serif, the default) or the game's own
    // pixel fonts; chosen in the plugin settings
    private static volatile boolean pixelFonts;

    // one font per role, in the same order in both sets: small, body, bold, heading, title, small capitals.
    // The pixel set reuses three game fonts for six roles, so each role gets its own copy: a component's font can
    // then be matched by identity when the setting changes, and swapped for the same role in the other set.
    private static final Font[] CLEAN_FONTS = {PanelUi.CLEAN_SMALL, PanelUi.CLEAN_BODY, PanelUi.CLEAN_BOLD, PanelUi.CLEAN_HEAD, PanelUi.CLEAN_TITLE, PanelUi.CLEAN_CAPS};
    private static Font[] pixelSet;

    private static synchronized Font[] pixelFontSet()
    {
        if (pixelSet == null)
        {
            Font s = FontManager.getRunescapeSmallFont();
            Font r = FontManager.getRunescapeFont();
            Font b = FontManager.getRunescapeBoldFont();
            pixelSet = new Font[] {s, r, b, b.deriveFont(b.getSize2D()), b.deriveFont(b.getSize2D() + 0.001f), s.deriveFont(s.getSize2D())};
        }
        return pixelSet;
    }

    private static Font font(int role) { return (pixelFonts ? pixelFontSet() : CLEAN_FONTS)[role]; }

    static Font smallFont() { return font(0); }
    static Font bodyFont() { return font(1); }
    static Font boldFont() { return font(2); }
    /** card and tile titles */
    static Font headFont() { return font(3); }
    /** the "ForgeBingo" name */
    static Font titleFont() { return font(4); }
    /** small capitals: section names, "REQUIREMENTS" */
    static Font capsFont() { return font(5); }

    /** A title in the heading font, or the plain bold one when the name has characters the heading font lacks. */
    private static Font headFontFor(String text)
    {
        return PanelUi.headingFor(text, headFont(), boldFont());
    }

    /** Switch fonts and restyle everything already on screen (cards built later pick the new fonts up anyway). */
    void setPixelFonts(boolean pixel)
    {
        if (pixel == pixelFonts) return;
        Font[] from = pixelFonts ? pixelFontSet() : CLEAN_FONTS;
        pixelFonts = pixel;
        Font[] to = pixelFonts ? pixelFontSet() : CLEAN_FONTS;
        restyle(this, from, to);
        revalidate();
        repaint();
    }

    /** Turns every animation in the panel on or off (the "Side panel animations" setting). */
    void setAnimations(boolean on)
    {
        PanelUi.animations = on;
        syncPulse();
        repaint();
    }

    /** Shows or hides the wandering imps (the "Side panel imps" setting). */
    void setImps(boolean on)
    {
        imps.setEnabled(on);
    }

    /** Switches the falling items on or off (the "Side panel item rain" setting). */
    void setRain(boolean on)
    {
        imps.setRain(on);
    }

    /** Plugin shut down: stop every timer the panel owns. */
    void dispose()
    {
        imps.dispose();
        pulseTimer.stop();
        upcomingTick.stop();
        if (fireworks != null) fireworks.stop();
    }

    static void usePixelFonts(boolean pixel)
    {
        pixelFonts = pixel;
    }

    private static void restyle(Component c, Font[] from, Font[] to)
    {
        Font f = c.getFont();
        for (int i = 0; i < from.length; i++) if (from[i] == f) { c.setFont(to[i]); break; }
        if (c instanceof java.awt.Container) for (Component k : ((java.awt.Container) c).getComponents()) restyle(k, from, to);
    }

    /** square size for the current board (shrinks for big boards) */
    private int cell = ICON + 6;

    private final ForgeBingoPlugin plugin;
    private final ApiClient api;
    private final JLabel status = new PanelUi.CrispLabel("Not connected");
    private final JLabel scope = new PanelUi.CrispLabel(" ");
    /** Verified RSN / name mismatch / revoked key, one card per line (from POST /v1/hello). */
    private final JPanel identity = column();
    private final JLabel queueLabel = new PanelUi.CrispLabel(" ");
    private final JLabel teamLabel = new PanelUi.CrispLabel(" ");
    private final JLabel eventLabel = new PanelUi.CrispLabel(" ");
    private final PanelUi.GoldBar tilesBar = new PanelUi.GoldBar();
    private final PanelUi.Card boardCard = new PanelUi.Card(null);
    private final PanelUi.StatusDot statusDot = new PanelUi.StatusDot();
    /** the website's imps, wandering over the cards */
    final PanelImps imps = new PanelImps();
    /** keeps the connection light and the open square's gold ring breathing; only runs while the panel is on screen */
    private final javax.swing.Timer pulseTimer = new javax.swing.Timer(70, e -> pulse());
    private final JComboBox<ApiModels.BoardSummary> boardBox = new JComboBox<>();
    private final JPanel grid = new JPanel();
    private final JPanel gridWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
    /** one tile, opened by clicking it on the grid: title, state, every task with its progress and how it is tracked */
    private final PanelUi.Card detail = new PanelUi.Card(PanelUi.GOLD);
    /** the 3 open tiles closest to done, like the website's "What can I do next?" */
    private final JPanel nextUp = column();
    private String selectedTileId;
    private final Map<String, PanelUi.TileCell> tileLabels = new ConcurrentHashMap<>();
    /** the last board shown, so icon arrivals and "working on" changes can redraw without a server round trip */
    private ApiModels.BoardResponse lastBoard;
    private ItemManager lastItemManager;
    private javax.swing.Timer iconRedraw;
    /** "your team finished X" card with fireworks (teammates' tiles) */
    private final JPanel teamNews = column();
    /** the fireworks playing on an open finished tile */
    private Fireworks fireworks;
    private boolean updatingBox;

    ForgeBingoPanel(ForgeBingoPlugin plugin, ApiClient api)
    {
        super(false);
        this.plugin = plugin;
        this.api = api;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder());
        setBackground(PanelUi.BG);

        Column content = new Column();
        content.setBorder(BorderFactory.createEmptyBorder(PAD, PAD, PAD, PAD));
        content.setBackground(PanelUi.BG);
        content.setOpaque(true);

        // --- header card: name, connection, account, send queue
        // collapsible (owner, 2026-11-11): the title row (and the connection line) always shows; tap it to hide or
        // show the account, queue and "Tracking drops as ..." details. Remembered between sessions.
        // proof a reviewer sent back, first thing in the panel (filled by setBoard)
        rejected.setVisible(false);
        add(content, rejected);

        JPanel head = card(null);
        JPanel titleRow = new JPanel(new BorderLayout(6, 0));
        titleRow.setOpaque(false);
        PanelUi.GoldTitle title = new PanelUi.GoldTitle("ForgeBingo");
        title.setFont(titleFont());
        titleRow.add(title, BorderLayout.CENTER);
        headToggle.setFont(smallFont());
        headToggle.setForeground(MUTED);
        titleRow.add(headToggle, BorderLayout.EAST);
        titleRow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        titleRow.setToolTipText("Show or hide connection details");
        titleRow.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                setHeaderCollapsed(!headerCollapsed);
                plugin.setPanelHeaderCollapsed(headerCollapsed);
            }
        });
        head.add(fullWidth(titleRow, -1));
        head.add(Box.createVerticalStrut(3));
        status.setFont(smallFont());
        status.setForeground(MUTED);
        JPanel statusRow = new JPanel(new BorderLayout(4, 0));
        statusRow.setOpaque(false);
        JPanel dotWrap = new JPanel(new BorderLayout());
        dotWrap.setOpaque(false);
        dotWrap.add(statusDot, BorderLayout.NORTH);
        statusRow.add(dotWrap, BorderLayout.WEST);
        statusRow.add(status, BorderLayout.CENTER);
        head.add(left(statusRow));
        scope.setFont(smallFont());
        scope.setForeground(MUTED);
        head.add(left(scope));
        queueLabel.setFont(smallFont());
        queueLabel.setForeground(MUTED);
        head.add(left(queueLabel));
        // the look, picked right here instead of deep in RuneLite's settings (owner, 2026-11-14)
        themeBox.setFont(smallFont());
        effectsBox.setFont(smallFont());
        themeBox.setToolTipText("Theme: the plugin's colours, creatures and effects");
        effectsBox.setToolTipText("Full: everything. Calm: lighter on a slow PC. Off: colours only.");
        themeBox.setSelectedItem(PanelUi.theme);
        effectsBox.setSelectedItem(PanelUi.effects);
        themeBox.addActionListener(e -> {
            if (!updatingLook && themeBox.getSelectedItem() instanceof PanelTheme) plugin.choosePanelTheme((PanelTheme) themeBox.getSelectedItem());
        });
        effectsBox.addActionListener(e -> {
            if (!updatingLook && effectsBox.getSelectedItem() instanceof ForgeBingoConfig.PanelEffects)
            {
                plugin.choosePanelEffects((ForgeBingoConfig.PanelEffects) effectsBox.getSelectedItem());
            }
        });
        lookRow.setOpaque(false);
        lookRow.add(themeBox);
        lookRow.add(effectsBox);
        head.add(lookGap);
        head.add(fullWidth(lookRow, 52));
        add(content, head);

        identity.add(message("Log in to the game so we can check which character you're playing.", ConnectionStatus.Level.INFO));
        add(content, identityGap);
        add(content, identity);
        // bingos whose board hasn't opened yet (shared reveal): a countdown to their start
        upcoming.setVisible(false);
        add(content, upcoming);
        setHeaderCollapsed(plugin.panelHeaderCollapsed());
        teamNews.setVisible(false);
        add(content, teamNews);

        // --- board picker and actions
        add(content, gap(4));
        add(content, section("Board"));
        boardBox.setFocusable(false);
        boardBox.setFont(smallFont());
        boardBox.putClientProperty(PanelImps.PLATFORM, Boolean.TRUE);
        boardBox.setToolTipText("Pick which team board to show and send drops to");
        add(content, fullWidth(boardBox, 26));
        add(content, gap(6));
        JPanel actions = new JPanel(new GridLayout(1, 2, 6, 0));
        actions.setOpaque(false);
        JButton refresh = button("Refresh", "Reload the board and leaderboard now");
        refresh.addActionListener(e -> plugin.refreshNow());
        JButton showBoard = button("Show board", "Open the board over the game (click off it, press Esc or the hotkey to close)");
        showBoard.addActionListener(e -> plugin.toggleBoardOverlay());
        actions.add(refresh);
        actions.add(showBoard);
        add(content, fullWidth(actions, 26));

        boardBox.addActionListener(e -> {
            if (updatingBox) return;
            Object sel = boardBox.getSelectedItem();
            if (sel instanceof ApiModels.BoardSummary) plugin.selectBoard(((ApiModels.BoardSummary) sel).teamBoardId);
        });

        // --- team + progress card (filled by setBoard)
        teamLabel.setFont(headFont());
        teamLabel.setForeground(new Color(0xfff3da));
        eventLabel.setFont(smallFont());
        eventLabel.setForeground(MUTED);
        tilesBar.setFont(smallFont());
        tilesBar.setForeground(PanelUi.GOLD_DARK);
        boardCard.add(left(teamLabel));
        boardCard.add(left(eventLabel));
        activityLabel.setFont(smallFont());
        activityLabel.setForeground(GOOD);
        activityLabel.setVisible(false);
        boardCard.add(left(activityLabel));
        reviewLabel.setFont(smallFont());
        reviewLabel.setForeground(WAIT);
        reviewLabel.setVisible(false);
        boardCard.add(left(reviewLabel));
        boardCard.add(Box.createVerticalStrut(7));
        boardCard.add(fullWidth(tilesBar, 17));
        boardCard.setVisible(false);
        add(content, gap(10));
        add(content, boardCard);

        // --- grid, tile detail, next up
        gridWrap.setOpaque(false);
        grid.setOpaque(false);
        gridWrap.add(grid);
        add(content, gap(8));
        add(content, gridWrap);
        detail.setVisible(false);
        add(content, gap(6));
        add(content, detail);
        add(content, nextUp);
        // no leaderboard or notices list here (owner, 2026-11-10): drops are announced in chat instead

        imps.setItemImages(plugin::itemImage);
        // no creatures, falling items or moving touches until a board is linked (setBoard switches them on)
        imps.setLinked(false);
        // the imps are painted over the column by a layer, in the column's own coordinates (they scroll with it)
        JScrollPane scroll = new JScrollPane(new javax.swing.JLayer<JComponent>(content, imps),
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(BAR, 0));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getViewport().setBackground(PanelUi.BG);
        // Swing's default scroll copies the pixels already on screen up or down, painted imps and falling items
        // included, and only repaints the strip that came into view: each copy stayed behind as a stack of ghost
        // sprites (owner, 2026-11-25). Repaint the whole (small) column on scroll instead.
        scroll.getViewport().setScrollMode(javax.swing.JViewport.SIMPLE_SCROLL_MODE);
        add(scroll, BorderLayout.CENTER);

        pulseTimer.setCoalesce(true);
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0) syncPulse();
        });
    }

    private void syncPulse()
    {
        boolean run = PanelUi.animations && isShowing();
        if (run && !pulseTimer.isRunning()) pulseTimer.start();
        else if (!run && pulseTimer.isRunning()) pulseTimer.stop();
    }

    /** Redraws the two things that breathe: the connection light and the gold ring on the open square. */
    private void pulse()
    {
        if (!isShowing())
        {
            pulseTimer.stop();
            return;
        }
        statusDot.repaint();
        PanelUi.TileCell open = selectedTileId == null ? null : tileLabels.get(selectedTileId);
        if (open != null) open.repaint();
    }

    /**
     * Always exactly the sidebar's width. Without this, a long message made the panel ask for more room and
     * RuneLite resized the game area to make space (the game looked like it zoomed after each drop).
     */
    @Override
    public Dimension getPreferredSize()
    {
        return new Dimension(PluginPanel.PANEL_WIDTH, super.getPreferredSize().height);
    }

    private final JLabel headToggle = new PanelUi.CrispLabel("Hide");
    private final JComboBox<PanelTheme> themeBox = new JComboBox<>(PanelTheme.values());
    private final JComboBox<ForgeBingoConfig.PanelEffects> effectsBox = new JComboBox<>(ForgeBingoConfig.PanelEffects.values());
    private final JPanel lookRow = new JPanel(new java.awt.GridLayout(2, 1, 0, 4));
    private final Component lookGap = Box.createVerticalStrut(6);
    private boolean updatingLook;
    /** the newest thing that counted, as one quiet line under the team's name; the rest is in its tooltip */
    private final JLabel activityLabel = new PanelUi.CrispLabel(" ");
    /** how much of the team's proof is waiting for a reviewer (player request, 2026-11-14) */
    private final JLabel reviewLabel = new PanelUi.CrispLabel(" ");

    /** The Theme or Effects setting changed somewhere else: show it in the pickers without firing them. */
    void showLook(PanelTheme theme, ForgeBingoConfig.PanelEffects effects)
    {
        updatingLook = true;
        try
        {
            themeBox.setSelectedItem(theme);
            effectsBox.setSelectedItem(effects);
        }
        finally
        {
            updatingLook = false;
        }
    }

    /** How much of the theme moves (the "Theme effects" setting). */
    void setEffects(ForgeBingoConfig.PanelEffects level)
    {
        PanelUi.effects = level == null ? ForgeBingoConfig.PanelEffects.FULL : level;
        imps.setCalm(PanelUi.effects == ForgeBingoConfig.PanelEffects.CALM);
        repaint();
    }

    /**
     * The newest thing that counted (null hides the line) and the last few as the tooltip's lines. One line only,
     * so the panel stays tidy.
     */
    void setActivity(String newest, List<String> recent)
    {
        if (newest == null || newest.isEmpty())
        {
            activityLabel.setVisible(false);
            return;
        }
        activityLabel.setText(wrapHtml(newest, CARD_TEXT));
        StringBuilder tip = new StringBuilder("<html><b>Recently counted</b>");
        if (recent != null) for (String line : recent) tip.append("<br>").append(esc(line));
        activityLabel.setToolTipText(tip.append("</html>").toString());
        activityLabel.setVisible(true);
        revalidate();
        repaint();
    }

    private final Component identityGap = gap(6);
    private boolean headerCollapsed;

    /** Collapsed: only "ForgeBingo" and the connection line; the account, queue and tracking cards are hidden. */
    private void setHeaderCollapsed(boolean collapsed)
    {
        headerCollapsed = collapsed;
        headToggle.setText(collapsed ? "Show" : "Hide");
        scope.setVisible(!collapsed);
        queueLabel.setVisible(!collapsed);
        lookRow.setVisible(!collapsed);
        lookGap.setVisible(!collapsed);
        identity.setVisible(!collapsed);
        identityGap.setVisible(!collapsed);
        revalidate();
        repaint();
    }

    // ------------------------------------------------------------------ public API (called by the plugin)

    void setStatus(boolean ok, String text)
    {
        status.setText(wrapHtml(text, INNER - 32));
        status.setForeground(ok ? GOOD : ERROR);
        statusDot.setColour(ok ? GOOD : ERROR);
    }

    void setScope(String text)
    {
        scope.setText(text == null || text.isEmpty() ? " " : wrapHtml(text, INNER - 16));
    }

    void setIdentity(List<ConnectionStatus.Line> lines)
    {
        identity.removeAll();
        boolean first = true;
        for (ConnectionStatus.Line l : lines)
        {
            if (!first) identity.add(gap(4));
            identity.add(message(l.text, l.level));
            first = false;
        }
        identity.revalidate();
        identity.repaint();
    }

    // ---- shared reveal countdown
    private final JPanel upcoming = column();
    private java.util.List<ApiModels.Membership> upcomingList = new java.util.ArrayList<>();
    private final javax.swing.Timer upcomingTick = new javax.swing.Timer(1000, e -> renderUpcoming());

    /** Bingos the player is on whose board hasn't opened yet; shown with a live countdown to the start. */
    void setUpcoming(List<ApiModels.Membership> list)
    {
        upcomingList = list == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(list);
        renderUpcoming();
        if (upcomingList.isEmpty()) upcomingTick.stop();
        else if (!upcomingTick.isRunning()) upcomingTick.start();
    }

    /** That bingo's board just opened: drop its countdown. */
    void removeUpcoming(String eventId)
    {
        if (eventId == null) return;
        upcomingList.removeIf(m -> eventId.equals(m.eventId));
        setUpcoming(upcomingList);
    }

    private void renderUpcoming()
    {
        upcoming.removeAll();
        for (ApiModels.Membership m : upcomingList)
        {
            String name = (m.eventName == null ? "Your bingo" : m.eventName) + (m.teamName == null ? "" : " - " + m.teamName);
            upcoming.add(gap(4));
            upcoming.add(message(name + ": " + countdown(m.startsAt), ConnectionStatus.Level.INFO));
        }
        upcoming.setVisible(!upcomingList.isEmpty());
        upcoming.revalidate();
        upcoming.repaint();
    }

    /** "board opens in 2h 13m 05s" (or "when the bingo goes live" when there's no start time yet). */
    static String countdown(String startsAt)
    {
        long start;
        try
        {
            start = startsAt == null ? -1 : java.time.OffsetDateTime.parse(startsAt).toInstant().toEpochMilli();
        }
        catch (RuntimeException ex)
        {
            start = -1;
        }
        if (start < 0) return "board opens when the bingo goes live";
        long s = (start - System.currentTimeMillis()) / 1000;
        if (s <= 0) return "board opening...";
        long d = s / 86400, h = (s % 86400) / 3600, mi = (s % 3600) / 60, se = s % 60;
        return "board opens in " + (d > 0 ? d + "d " + h + "h " + mi + "m" : h > 0 ? h + "h " + String.format("%02dm %02ds", mi, se) : mi + "m " + String.format("%02ds", se));
    }

    void setQueueSize(int size, String lastError)
    {
        queueLabel.setText(wrapHtml(size == 0 ? "Nothing waiting to send"
            : size + " waiting to send" + (lastError == null ? "" : " (" + lastError + ")"), INNER - 16));
        queueLabel.setForeground(size == 0 ? MUTED : WAIT);
    }

    /** what the board list shows now (see setBoards) */
    private String boardsShown;

    void setBoards(List<ApiModels.BoardSummary> boards, String activeId)
    {
        // called on every 10 s poll: rebuilding an unchanged list closed the dropdown under the player's mouse
        StringBuilder sig = new StringBuilder(String.valueOf(activeId));
        for (ApiModels.BoardSummary b : boards == null ? Collections.<ApiModels.BoardSummary>emptyList() : boards)
        {
            sig.append('\n').append(b.teamBoardId).append('|').append(b).append('|').append(b.status).append('|').append(b.active)
                .append('|').append(b.completedCount).append('/').append(b.totalTiles).append('|').append(b.startsAt).append('|').append(b.endsAt);
        }
        String s = sig.toString();
        if (s.equals(boardsShown)) return;
        boardsShown = s;
        updatingBox = true;
        boardBox.removeAllItems();
        ApiModels.BoardSummary selected = null;
        for (ApiModels.BoardSummary b : boards == null ? Collections.<ApiModels.BoardSummary>emptyList() : boards)
        {
            boardBox.addItem(b);
            if (b.teamBoardId.equals(activeId)) selected = b;
        }
        if (selected != null) boardBox.setSelectedItem(selected);
        updatingBox = false;
    }

    /**
     * A teammate finished a tile: a fireworks card under the header for a few seconds (instead of a banner over the
     * game, which is only for tiles you finish yourself).
     */
    void teamFinished(String tileTitle)
    {
        teamNews.removeAll();
        Fireworks fw = new Fireworks("TEAM TILE DONE!");
        PanelUi.Card card = card(GOOD);
        card.add(fullWidth(fw, Fireworks.HEIGHT));
        card.add(left(cardText("Your team finished " + (tileTitle == null || tileTitle.isEmpty() ? "a tile" : tileTitle) + "!", Color.WHITE)));
        teamNews.add(gap(6));
        teamNews.add(card);
        teamNews.setVisible(true);
        teamNews.revalidate();
        teamNews.repaint();
        card.fadeIn();
        fw.start();
        javax.swing.Timer hide = new javax.swing.Timer(9000, e -> {
            if (teamNews.getComponentCount() > 1 && teamNews.getComponent(1) == card)
            {
                fw.stop();
                teamNews.removeAll();
                teamNews.setVisible(false);
                teamNews.revalidate();
                teamNews.repaint();
            }
        });
        hide.setRepeats(false);
        hide.start();
    }

    // ------------------------------------------------------------------ proof sent back

    /** "Rejected: tile - reason" lines at the very top of the panel (see RejectedNotices for what counts as recent) */
    private final JPanel rejected = column();
    private final RejectedNotices.Log rejectedLog = new RejectedNotices.Log();
    /** the team board the log above belongs to (each board keeps its own in the player's settings) */
    private String rejectedBoardId;

    private void renderRejected(ApiModels.BoardResponse board)
    {
        String id = board.teamBoardId;
        long now = System.currentTimeMillis();
        List<RejectedNotices.Notice> show = Collections.emptyList();
        if (id != null && !id.isEmpty())
        {
            if (!id.equals(rejectedBoardId))
            {
                rejectedLog.load(plugin.rejectedMemory(id));
                rejectedBoardId = id;
            }
            if (rejectedLog.update(RejectedNotices.find(board.tiles), now)) plugin.setRejectedMemory(id, rejectedLog.save());
            show = rejectedLog.visible(now);
        }
        rejected.removeAll();
        for (int i = 0; i < show.size() && i < RejectedNotices.MAX_SHOWN; i++)
        {
            rejected.add(rejectedRow(board, show.get(i)));
            rejected.add(gap(4));
        }
        String more = RejectedNotices.moreText(show.size());
        if (!more.isEmpty())
        {
            JLabel l = muted(more + " sent back. Close one to see the next.");
            rejected.add(left(l));
            rejected.add(gap(4));
        }
        if (!show.isEmpty()) rejected.add(gap(2));
        rejected.setVisible(!show.isEmpty());
        rejected.revalidate();
        rejected.repaint();
    }

    /** One rejected tile: a red-edged card like the other messages, the text opens the tile, the cross closes the line. */
    private JPanel rejectedRow(ApiModels.BoardResponse board, RejectedNotices.Notice n)
    {
        PanelUi.Card row = new PanelUi.Card(ERROR).pad(6, 7, 6, 7);
        row.setLayout(new BorderLayout(6, 0));
        JLabel text = new JLabel("<html><body style='width:" + (CARD_TEXT - 40) + "px'><font color='#ff7070'>Rejected:</font> " + esc(n.title)
            + (n.reason == null ? "" : "<br><font color='#a69d8f'>" + esc(n.reason) + "</font>") + "</body></html>");
        text.setFont(smallFont());
        text.setForeground(TEXT);
        text.setToolTipText("Open this tile");
        text.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        text.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                for (ApiModels.Tile t : board.tiles)
                {
                    if (t != null && n.tileId.equals(t.id))
                    {
                        showTile(t);
                        return;
                    }
                }
            }
        });
        row.add(text, BorderLayout.CENTER);
        JLabel close = new JLabel("x");
        close.setFont(boldFont());
        close.setForeground(MUTED);
        close.setVerticalAlignment(SwingConstants.TOP);
        close.setToolTipText("Dismiss. It comes back only if this tile's proof is sent back again.");
        close.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        close.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                dismissRejected(n.key);
            }

            @Override
            public void mouseEntered(MouseEvent e)
            {
                close.setForeground(Color.WHITE);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                close.setForeground(MUTED);
            }
        });
        row.add(close, BorderLayout.EAST);
        return fullWidth(row, -1);
    }

    /** Closes one "Rejected" line and remembers it, so it stays away on the next poll and after a restart. */
    void dismissRejected(String key)
    {
        if (!rejectedLog.dismiss(key)) return;
        if (rejectedBoardId != null) plugin.setRejectedMemory(rejectedBoardId, rejectedLog.save());
        if (lastBoard != null) renderRejected(lastBoard);
    }

    /** Notices are shown in the game chat only (the sidebar list was removed); kept so callers stay simple. */
    void addNotice(String text)
    {
    }

    /** Server icons arrived: redraw the grid once they settle (many arrive together on the first load). */
    void iconsChanged()
    {
        if (lastBoard == null) return;
        if (iconRedraw == null)
        {
            iconRedraw = new javax.swing.Timer(250, e -> {
                if (lastBoard != null) setBoard(lastBoard, null, lastItemManager);
            });
            iconRedraw.setRepeats(false);
        }
        iconRedraw.restart();
    }

    void setBoard(ApiModels.BoardResponse board, ApiModels.LeaderboardResponse leaderboard, ItemManager itemManager)
    {
        lastBoard = board;
        lastItemManager = itemManager;
        imps.setLinked(true);
        String teamName = board.teamName == null ? "Your team" : board.teamName;
        teamLabel.setFont(headFontFor(teamName));
        teamLabel.setText(wrapHtml(teamName, CARD_TEXT));
        String ev = board.event != null && board.event.name != null ? board.event.name : "";
        if (board.event != null && !board.event.active) ev += (ev.isEmpty() ? "" : " ") + "(not running)";
        eventLabel.setText(ev.isEmpty() ? " " : wrapHtml(ev, CARD_TEXT));

        int size = board.size();
        // squares shrink to fit the sidebar on big boards (a 10x10 board would otherwise cut its icons off)
        cell = Math.max(14, Math.min(ICON + 6, (INNER - 2 * (size - 1)) / Math.max(1, size)));
        int done = 0;
        for (ApiModels.Tile t : board.tiles) if (t.completed) done++;
        int total = board.tiles.size();
        boolean blackout = done == total && total > 0;
        tilesBar.setMaximum(Math.max(1, total));
        tilesBar.setValue(done);
        tilesBar.setString(blackout ? "BLACKOUT! " + done + " / " + total : done + " / " + total + " tiles done");
        tilesBar.setForeground(blackout ? PanelUi.GREEN_DARK : PanelUi.GOLD_DARK);
        // what is waiting for a reviewer, across the whole board: a screenshot just sent shows up here
        int waitingTasks = 0;
        for (ApiModels.Tile t : board.tiles)
        {
            if (t == null || t.tasks == null) continue;
            for (ApiModels.Task k : t.tasks) if (k != null && k.pendingQty > 0) waitingTasks++;
        }
        reviewLabel.setVisible(waitingTasks > 0);
        if (waitingTasks > 0)
        {
            reviewLabel.setText(wrapHtml("Being reviewed: " + waitingTasks + (waitingTasks == 1 ? " task" : " tasks") + " waiting", CARD_TEXT));
            reviewLabel.setToolTipText("Proof for " + (waitingTasks == 1 ? "this task" : "these tasks") + " is with the reviewers. It counts once they approve it.");
        }
        boardCard.setVisible(true);
        renderRejected(board);

        tileLabels.clear();
        grid.removeAll();
        grid.setLayout(new GridLayout(size, size, size > 6 ? 1 : 2, size > 6 ? 1 : 2));
        ApiModels.Tile[][] cells = new ApiModels.Tile[size][size];
        for (ApiModels.Tile t : board.tiles)
        {
            if (t.row < size && t.col < size) cells[t.row][t.col] = t;
        }
        for (int r = 0; r < size; r++)
        {
            for (int c = 0; c < size; c++)
            {
                grid.add(tileButton(cells[r][c], itemManager));
            }
        }
        grid.revalidate();
        grid.repaint();

        nextUp.removeAll();
        // "Working on": open tiles anyone on the team marked (from the server), or that you marked (older servers)
        List<ApiModels.Tile> working = new java.util.ArrayList<>();
        for (ApiModels.Tile t : board.tiles)
        {
            if (t.id == null || t.completed) continue;
            boolean teamMarked = t.workingOn != null && !t.workingOn.isEmpty();
            if (teamMarked || plugin.isWorkingOn(t)) working.add(t);
        }
        if (!working.isEmpty())
        {
            nextUp.add(gap(6));
            nextUp.add(left(section("Team is working on")));
            for (int i = 0; i < working.size(); i++)
            {
                if (i > 0) nextUp.add(gap(4));
                nextUp.add(nextUpRow(working.get(i), true));
            }
        }
        List<ApiModels.Tile> next = TileInfo.nextUp(board.tiles, 3);
        next.removeIf(working::contains);
        if (!next.isEmpty())
        {
            nextUp.add(gap(6));
            nextUp.add(left(section("Closest to done")));
        }
        boolean firstNext = true;
        for (ApiModels.Tile t : next)
        {
            if (!firstNext) nextUp.add(gap(4));
            nextUp.add(nextUpRow(t, false));
            firstNext = false;
        }
        nextUp.revalidate();
        nextUp.repaint();
        // keep the open tile in step with live updates
        if (selectedTileId != null)
        {
            ApiModels.Tile still = null;
            for (ApiModels.Tile t : board.tiles) if (selectedTileId.equals(t.id)) still = t;
            if (still != null) showTile(still, false); else closeTile();
        }
    }

    /** Opens one tile under the grid: what it is, where it stands, each task with its progress and how it is tracked. */
    void showTile(ApiModels.Tile t)
    {
        showTile(t, true);
    }

    /** @param opened true when the player just opened it (plays the fireworks on a finished tile), false on a refresh */
    void showTile(ApiModels.Tile t, boolean opened)
    {
        String previous = selectedTileId;
        selectedTileId = t.id;
        if (previous != null && tileLabels.get(previous) != null) tileLabels.get(previous).setSelected(false);
        if (t.id != null && tileLabels.get(t.id) != null) tileLabels.get(t.id).setSelected(true);
        if (fireworks != null && (opened || !t.completed))
        {
            fireworks.stop();
            fireworks = null;
        }
        Fireworks keep = t.completed && !opened ? fireworks : null;
        detail.removeAll();
        if (t.completed)
        {
            Fireworks fw = keep != null ? keep : new Fireworks();
            detail.add(fullWidth(fw, Fireworks.HEIGHT));
            detail.add(gap(4));
            if (keep == null)
            {
                fireworks = fw;
                fw.start();
            }
        }
        // title row: name (gold) with a small close cross on the right
        JPanel head = new JPanel(new BorderLayout(6, 0));
        head.setOpaque(false);
        JLabel title = new PanelUi.CrispLabel(wrapHtml(TileInfo.shownTitle(t), CARD_TEXT - 22));
        title.setFont(headFontFor(TileInfo.shownTitle(t)));
        title.setForeground(GOLD);
        head.add(title, BorderLayout.CENTER);
        JLabel close = new PanelUi.CrispLabel("x");
        close.setFont(boldFont());
        close.setForeground(MUTED);
        close.setVerticalAlignment(SwingConstants.TOP);
        close.setToolTipText("Close this tile");
        close.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        close.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                closeTile();
            }

            @Override
            public void mouseEntered(MouseEvent e)
            {
                close.setForeground(Color.WHITE);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                close.setForeground(MUTED);
            }
        });
        head.add(close, BorderLayout.EAST);
        detail.add(fullWidth(head, -1));

        // state + points on one line
        boolean secret = TileInfo.isSecret(t);
        String state = stateText(t);
        if (!t.completed && "any".equals(t.completionMode) && t.tasks.size() > 1) state += " (any one finishes it)";
        JPanel stateRow = new JPanel(new BorderLayout(6, 0));
        stateRow.setOpaque(false);
        stateRow.add(cardText(state, stateTextColour(t), CARD_TEXT - 40), BorderLayout.CENTER);
        if (!secret)
        {
            // part-way through, show what the finished tasks already scored (each task scores as soon as it is done;
            // a task with "OR" options scores only the option the team finished)
            double got = TileInfo.earned(t);
            double max = TileInfo.worth(t);
            String gotText = com.forgebingo.core.Points.text(got);
            JLabel pts = new PanelUi.CrispLabel(t.completed ? BoardOverlay.points(got > 0 ? got : max) : got > 0 ? gotText + " / " + BoardOverlay.points(max) : BoardOverlay.points(max));
            if (!t.completed && got > 0) pts.setToolTipText(gotText + " scored so far: each finished task counts now; an OR task scores only the option you did");
            pts.setFont(smallFont());
            pts.setForeground(GOLD);
            pts.setVerticalAlignment(SwingConstants.TOP);
            stateRow.add(pts, BorderLayout.EAST);
        }
        detail.add(fullWidth(stateRow, -1));
        if (t.description != null && !t.description.isEmpty()) detail.add(left(cardText(t.description, MUTED)));
        if (!t.completed && t.workingOn != null && !t.workingOn.isEmpty())
        {
            detail.add(left(cardText("Working on it: " + workingNames(t), GOLD)));
        }
        // FINISHED BY (owner, 2026-11-23: the finished card was mostly empty): who earned the tile and how much of
        // theirs counted, most first, then when it was finished
        if (t.completed && !secret)
        {
            if (t.contributors != null && !t.contributors.isEmpty())
            {
                detail.add(gap(8));
                detail.add(left(smallCaps("Finished by")));
                detail.add(gap(4));
                detail.add(finishedByBox(t));
            }
            String when = TileInfo.finishedAgo(t.completedAt, System.currentTimeMillis());
            if (when != null && !TileInfo.isFree(t))
            {
                detail.add(gap(4));
                detail.add(left(cardText(when, MUTED)));
            }
        }
        // REQUIREMENTS: one box per task, laid out like the website's tile card (owner, 2026-11-11)
        if (!t.tasks.isEmpty())
        {
            detail.add(gap(8));
            detail.add(left(smallCaps("Requirements")));
            detail.add(gap(4));
        }
        // a hidden secret tile has no tasks to show, but it takes proof too (owner, 2026-11-17): one Send screenshot
        // for the whole tile, waiting for an organizer like any other proof
        if (secret && !t.completed && t.proofTaskId != null && !t.proofTaskId.isEmpty())
        {
            JButton shot = button("Send screenshot", "Takes a screenshot of the game now and sends it as proof for this secret tile");
            String boardId = lastBoard == null ? null : lastBoard.teamBoardId;
            String tileName = TileInfo.shownTitle(t);
            String proofTask = t.proofTaskId;
            shot.addActionListener(e -> plugin.sendScreenshotProof(boardId, proofTask, tileName, tileName));
            detail.add(gap(8));
            detail.add(left(cardText("Think you worked it out? Send a screenshot. An organizer checks it.", MUTED)));
            detail.add(gap(5));
            detail.add(fullWidth(shot, 24));
        }
        int counted = 0;
        int waiting = 0;
        // a tile that completes with any one of its tasks shows them as Option 1 / OR / Option 2 (owner, 2026-11-15),
        // each with its own Send screenshot, so every proof lands on the option it is for
        boolean options = "any".equals(t.completionMode) && t.tasks.size() > 1;
        // an "all" tile can hold tasks with up to 3 "OR" options (owner, 2026-11-16): Task 1, option OR option;
        // only the option the team finishes scores
        java.util.List<java.util.List<ApiModels.Task>> groups = TileInfo.taskGroups(t);
        boolean grouped = !options && groups.size() < t.tasks.size();
        int n = 0;
        int gi = 0;
        for (java.util.List<ApiModels.Task> g : groups)
        {
            gi++;
            if (grouped)
            {
                detail.add(left(smallCaps("Task " + gi + (g.size() > 1 ? ": any one option" : ""))));
                detail.add(gap(2));
            }
            int oi = 0;
            for (ApiModels.Task k : g)
            {
                counted += k.approvedQty;
                waiting += k.pendingQty;
                n++;
                oi++;
                if (options)
                {
                    if (n > 1) detail.add(left(cardText("OR", PanelUi.GOLD)));
                    detail.add(left(smallCaps("Option " + n)));
                    detail.add(gap(2));
                }
                else if (grouped && oi > 1)
                {
                    detail.add(left(cardText("OR", PanelUi.GOLD)));
                }
                detail.add(taskBox(t, k, secret, opened));
                detail.add(gap(6));
            }
        }

        // PROOF: what this team has sent for the tile
        detail.add(gap(2));
        detail.add(left(smallCaps("Proof")));
        detail.add(gap(3));
        String proof = TileInfo.isFree(t) ? "Free space: no proof needed."
            : t.completed ? "Done - proof accepted."
            : counted == 0 && waiting == 0 ? "No proof sent yet."
            : (counted > 0 ? counted + " counted" : "") + (counted > 0 && waiting > 0 ? ", " : "") + (waiting > 0 ? waiting + " waiting for review" : "") + ".";
        detail.add(left(cardText(proof, t.completed ? GOOD : waiting > 0 ? WAIT : TEXT)));
        detail.add(gap(8));
        if (!t.completed && t.id != null)
        {
            boolean on = plugin.isWorkingOn(t);
            JButton pin = button(on ? "Stop working on it" : "I'm working on this",
                on ? "Take this tile off your team's Working on list" : "Show your team you're going for this tile");
            if (on) pin.setForeground(GOLD);
            pin.addActionListener(e -> {
                plugin.setWorkingOn(t.id, !on);
                if (lastBoard != null) setBoard(lastBoard, null, lastItemManager);
            });
            detail.add(fullWidth(pin, 26));
        }
        detail.setVisible(true);
        detail.revalidate();
        detail.repaint();
        // a different tile was opened: the card settles in (not on live refreshes of the tile already open)
        if (opened && (previous == null || !previous.equals(t.id))) detail.fadeIn();
    }

    void closeTile()
    {
        if (selectedTileId != null && tileLabels.get(selectedTileId) != null) tileLabels.get(selectedTileId).setSelected(false);
        selectedTileId = null;
        if (fireworks != null)
        {
            fireworks.stop();
            fireworks = null;
        }
        detail.setVisible(false);
        detail.removeAll();
        detail.revalidate();
        detail.repaint();
    }

    // ------------------------------------------------------------------ shared with the board overlay

    /** Where a tile stands, in the player's words (shared with the board overlay's tile card). */
    static String stateText(ApiModels.Tile t)
    {
        boolean pending = TileInfo.waiting(t);
        int doneTasks = 0;
        for (ApiModels.Task k : t.tasks) if (k.completed) doneTasks++;
        String reason = TileInfo.needsChangesReason(t);
        return TileInfo.isSecret(t) && !t.completed ? "Secret tile" : t.completed ? "Done" : reason != null ? "Needs changes: " + reason
            : pending ? "Waiting for review" : t.tasks.isEmpty() ? "No tasks yet" : doneTasks + " of " + t.tasks.size() + " tasks done";
    }

    static Color stateTextColour(ApiModels.Tile t)
    {
        return t.completed ? GOOD : TileInfo.needsChangesReason(t) != null ? ERROR
            : TileInfo.waiting(t) ? WAIT : TEXT;
    }

    /** How a task gets ticked off, in the player's words. */
    static String trackingText(ApiModels.Task k)
    {
        String st = k.sourceType == null ? "manual" : k.sourceType;
        switch (st)
        {
            case "npc": return "Counts by itself when RuneLite sees the drop";
            case "raid_chest": return "Counts by itself when RuneLite sees the chest or raid loot";
            case "clue": return "Counts by itself when RuneLite sees the casket";
            case "collection_log": return "Counts by itself from your collection log";
            case "pet": return "Counts by itself when the pet drops";
            case "kc": return "Counts by itself from your kill count message";
            case "level": return "Counts by itself when you level up";
            case "wom_gain": return "Tracked by Wise Old Man (team total)";
            default: return "Needs a screenshot: use Send screenshot below, or add proof on the website";
        }
    }

    /** Square colours, matching the website: green done, yellow waiting, red needs changes, purple secret. */
    static Color stateColour(TileInfo.State st)
    {
        switch (st)
        {
            case DONE: return new Color(30, 90, 30);
            case WAITING: return new Color(110, 90, 20);
            case NEEDS_CHANGES: return new Color(110, 35, 35);
            case SECRET: return new Color(60, 35, 85);
            case IN_PROGRESS: return new Color(45, 52, 60);
            case EMPTY: return ColorScheme.DARK_GRAY_COLOR;
            default: return ColorScheme.DARKER_GRAY_COLOR;
        }
    }

    // ------------------------------------------------------------------ rows and cards

    private JLabel tileButton(ApiModels.Tile t, ItemManager itemManager)
    {
        PanelUi.TileCell l = new PanelUi.TileCell();
        l.setPreferredSize(new Dimension(cell, cell));
        if (t == null)
        {
            l.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            return l;
        }
        l.makeInteractive();
        TileInfo.State st = TileInfo.state(t);
        l.setBackground(stateColour(st));
        if (st == TileInfo.State.SECRET)
        {
            l.setText("?");
            l.setForeground(new Color(255, 215, 90));
            l.setFont(l.getFont().deriveFont(java.awt.Font.BOLD, Math.max(10f, cell * 0.55f)));
        }
        StringBuilder tip = new StringBuilder("<html><b>").append(esc(st == TileInfo.State.SECRET ? "Secret tile" : t.title)).append("</b>");
        if (st == TileInfo.State.SECRET)
        {
            // secret tiles never reveal their tasks: only the organizer's own message, when there is one
            if (t.description != null && !t.description.isEmpty()) tip.append("<br>").append(esc(t.description));
        }
        else tip.append(" (").append(BoardOverlay.points(TileInfo.worth(t))).append(")");
        String reason = TileInfo.needsChangesReason(t);
        if (reason != null) tip.append("<br><font color='#ff7070'>Needs changes: ").append(esc(reason)).append("</font>");
        for (ApiModels.Task k : t.tasks)
        {
            tip.append("<br>").append(k.completed ? "[x] " : "[ ] ").append(esc(k.description));
            if (k.requiredQty > 1) tip.append(" ").append(k.approvedQty).append("/").append(k.requiredQty);
            if (k.pendingQty > 0) tip.append(" (+").append(k.pendingQty).append(" pending)");
        }
        l.setToolTipText(tip.append("</html>").toString());
        l.setSelected(t.id != null && t.id.equals(selectedTileId));
        if (t.id != null) tileLabels.put(t.id, l);
        l.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (t.id != null && t.id.equals(selectedTileId)) closeTile(); else showTile(t);
            }
        });
        loadIcon(l, t, itemManager);
        return l;
    }

    /** how many teammates the finished card lists before "+N more" */
    private static final int FINISHED_BY_SHOWN = 6;

    /** The finished card's "Finished by": one row per teammate, name on the left, what counted on the right. */
    private JPanel finishedByBox(ApiModels.Tile t)
    {
        PanelUi.Card box = new PanelUi.Card(null).inset(PanelUi.GREEN_DARK).pad(6, 7, 6, 7);
        int shown = Math.min(FINISHED_BY_SHOWN, t.contributors.size());
        for (int i = 0; i < shown; i++)
        {
            ApiModels.Contributor c = t.contributors.get(i);
            JPanel row = new JPanel(new BorderLayout(6, 0));
            row.setOpaque(false);
            row.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
            JLabel name = new PanelUi.CrispLabel(wrapHtml(c.name + (c.me ? " (you)" : ""), CARD_TEXT - 90));
            name.setFont(bodyFont());
            // the teammate who did the most leads the list in gold
            name.setForeground(i == 0 && t.contributors.size() > 1 ? GOLD : c.me ? new Color(0xf4eee2) : TEXT);
            row.add(name, BorderLayout.CENTER);
            JLabel qty = new PanelUi.CrispLabel(TileInfo.contributed(t, c.qty));
            qty.setFont(smallFont());
            qty.setForeground(GOOD);
            qty.setVerticalAlignment(SwingConstants.TOP);
            row.add(qty, BorderLayout.EAST);
            box.add(fullWidth(row, -1));
        }
        if (t.contributors.size() > shown) box.add(left(cardText("+" + (t.contributors.size() - shown) + " more", MUTED)));
        return fullWidth(box, -1);
    }

    /** "REQUIREMENTS" / "PROOF": small gold capitals, like the website's section labels. */
    private static JLabel smallCaps(String text)
    {
        JLabel l = new PanelUi.CrispLabel(text.toUpperCase());
        l.setFont(capsFont());
        l.setForeground(new Color(0xc8a35a));
        return l;
    }

    /** An item's icon at px size (fills in when the sprite loads); blank when there is no item manager yet. */
    private JLabel itemIcon(int itemId, int px)
    {
        JLabel l = new PanelUi.CrispLabel();
        l.setPreferredSize(new Dimension(px, px));
        l.setMinimumSize(new Dimension(px, px));
        if (lastItemManager == null) return l;
        AsyncBufferedImage img = lastItemManager.getImage(itemId);
        Runnable set = () -> l.setIcon(new ImageIcon(fit(img, px)));
        set.run();
        img.onLoaded(() -> javax.swing.SwingUtilities.invokeLater(set));
        return l;
    }

    /** icons per row in an "Any one of these" grid, and the most shown before "+N" */
    static final int GRID_ICON = 22;
    static final int GRID_GAP = 3;
    static final int GRID_MAX = 18;

    /** Item icons in rows that fit the card, each naming its item on hover; past GRID_MAX a "+N" cell. */
    private JPanel itemGrid(List<Integer> items)
    {
        int cols = Math.max(1, (CARD_TEXT - 4 + GRID_GAP) / (GRID_ICON + GRID_GAP));
        boolean more = items.size() > GRID_MAX;
        int shown = more ? GRID_MAX - 1 : items.size();
        int cells = shown + (more ? 1 : 0);
        JPanel grid = new JPanel(new java.awt.GridLayout(0, cols, GRID_GAP, GRID_GAP));
        grid.setOpaque(false);
        grid.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
        for (int i = 0; i < shown; i++)
        {
            JLabel icon = itemIcon(items.get(i), GRID_ICON);
            icon.setHorizontalAlignment(SwingConstants.CENTER);
            icon.setToolTipText(plugin.itemName(items.get(i)));
            grid.add(icon);
        }
        if (more)
        {
            JLabel rest = new PanelUi.CrispLabel("+" + (items.size() - shown));
            rest.setFont(smallFont());
            rest.setForeground(MUTED);
            rest.setHorizontalAlignment(SwingConstants.CENTER);
            StringBuilder names = new StringBuilder("<html>");
            for (int i = shown; i < Math.min(items.size(), shown + 30); i++)
            {
                if (i > shown) names.append("<br>");
                names.append(esc(plugin.itemName(items.get(i))));
            }
            if (items.size() > shown + 30) names.append("<br>...");
            rest.setToolTipText(names.append("</html>").toString());
            grid.add(rest);
        }
        // fill the last row so GridLayout keeps every cell the same size and the icons stay left-aligned
        while (cells % cols != 0 && cells > cols) { grid.add(new JLabel()); cells++; }
        int rows = (cells + cols - 1) / cols;
        int w = Math.min(cells, cols) * (GRID_ICON + GRID_GAP) - GRID_GAP + 4;
        Dimension d = new Dimension(w, rows * (GRID_ICON + GRID_GAP) - GRID_GAP);
        grid.setPreferredSize(d);
        grid.setMaximumSize(d);
        return grid;
    }

    /**
     * One requirement, website style: icon box, name and count, a thin bar, then either each item ("x1") for
     * one-of-each tasks or "Any of" with the items it accepts; how it is tracked and "Send screenshot" underneath.
     */
    private JPanel taskBox(ApiModels.Tile t, ApiModels.Task k, boolean secret, boolean animate)
    {
        boolean done = k.completed || t.completed;
        PanelUi.Card box = new PanelUi.Card(null).inset(done ? PanelUi.GREEN_DARK : null).pad(7, 7, 7, 7);
        List<Integer> items = k.itemIds == null ? java.util.Collections.<Integer>emptyList() : k.itemIds;

        // icon + name + count
        JPanel top = new JPanel(new BorderLayout(6, 0));
        top.setOpaque(false);
        // no box behind the icon: the item sits straight on the card, like the website
        JPanel iconBox = new JPanel(new BorderLayout());
        iconBox.setOpaque(false);
        iconBox.setBorder(BorderFactory.createEmptyBorder());
        iconBox.setPreferredSize(new Dimension(28, 28));
        // task picture: its item / skill / named boss, else the tile's uploaded picture, else an item from the tile
        Integer taskPic = secret ? null : TileIcons.taskItem(k, t);
        BufferedImage tilePic = taskPic == null && !secret && t.hasIcon && t.iconUrl != null && t.iconEmoji == null ? plugin.tileImage(t.iconUrl) : null;
        if (taskPic == null && tilePic == null && !secret) taskPic = TileIcons.taskFallback(t);
        if (taskPic != null) iconBox.add(itemIcon(taskPic, 22), BorderLayout.CENTER);
        else if (tilePic != null)
        {
            JLabel pic = new PanelUi.CrispLabel(new ImageIcon(fit(tilePic, 22)));
            pic.setHorizontalAlignment(SwingConstants.CENTER);
            iconBox.add(pic, BorderLayout.CENTER);
        }
        else
        {
            JLabel tick = new PanelUi.CrispLabel(done ? BOX_ON : BOX_OFF);
            tick.setHorizontalAlignment(SwingConstants.CENTER);
            iconBox.add(tick, BorderLayout.CENTER);
        }
        top.add(iconBox, BorderLayout.WEST);
        // a finished task shows no count: its green name and full bar say "done", and the name gets the whole width
        // (the "Done" beside it used to cut the name off, owner 2026-11-23)
        JLabel name = new PanelUi.CrispLabel(wrapHtml(k.description == null || k.description.isEmpty() ? "Task" : k.description, CARD_TEXT - (done ? 66 : 80)));
        name.setFont(bodyFont());
        name.setForeground(done ? GOOD : new Color(0xf4eee2));
        top.add(name, BorderLayout.CENTER);
        int need = Math.max(1, k.requiredQty);
        if (!done)
        {
            JLabel count = new PanelUi.CrispLabel(compact(Math.min(need, k.approvedQty)) + "/" + compact(need));
            if (need >= 100_000) count.setToolTipText(String.format("%,d / %,d", Math.min(need, k.approvedQty), need));
            count.setFont(smallFont());
            count.setForeground(MUTED);
            count.setVerticalAlignment(SwingConstants.TOP);
            top.add(count, BorderLayout.EAST);
        }
        box.add(fullWidth(top, -1));

        // thin bar: fills up when the tile is opened, sits still on live refreshes
        box.add(gap(5));
        PanelUi.GoldBar bar = new PanelUi.GoldBar();
        bar.putClientProperty(PanelImps.PLATFORM, null);
        bar.setMaximum(need);
        bar.setForeground(done ? PanelUi.GREEN : PanelUi.GOLD);
        int have = done ? need : Math.min(need, k.approvedQty);
        if (animate) bar.setValue(have); else bar.setValueNow(have);
        box.add(fullWidth(bar, 6));

        List<ItemSlots.Slot> slots = ItemSlots.of(k);
        if (!slots.isEmpty())
        {
            // one of each: every item (or group) on its own row with "x1", ticked green when done
            box.add(gap(4));
            for (ItemSlots.Slot s : slots)
            {
                boolean sDone = done || s.done();
                JPanel row = new JPanel(new BorderLayout(6, 0));
                row.setOpaque(false);
                row.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 0));
                row.add(itemIcon(s.itemIds.get(0), 20), BorderLayout.WEST);
                String label = s.itemIds.size() == 1 ? plugin.itemName(s.itemIds.get(0)) : "Any one: " + anyOf(s.itemIds);
                JLabel n = new PanelUi.CrispLabel(wrapHtml(label + (s.pending > 0 && !sDone ? " (waiting)" : ""), CARD_TEXT - 70));
                n.setFont(smallFont());
                n.setForeground(sDone ? GOOD : s.pending > 0 ? WAIT : TEXT);
                row.add(n, BorderLayout.CENTER);
                JLabel x = new PanelUi.CrispLabel(sDone ? "done" : s.need > 1 ? s.approved + "/" + s.need : "x" + s.need);
                x.setFont(smallFont());
                x.setForeground(sDone ? GOOD : MUTED);
                row.add(x, BorderLayout.EAST);
                box.add(fullWidth(row, -1));
            }
        }
        else if (items.size() > 1 && !done)
        {
            // any of these items counts: a compact grid of their icons, the name on hover (owner, 2026-11-25: a name
            // per row made tiles with several options far too long)
            box.add(gap(4));
            box.add(left(cardText("Any one of these:", MUTED)));
            box.add(gap(2));
            box.add(left(itemGrid(items)));
        }

        if (!done)
        {
            if (k.pendingQty > 0) box.add(left(cardText(k.pendingQty + " waiting for review", WAIT)));
            box.add(gap(3));
            // automatic tasks need no explanation (owner, 2026-11-12); only manual ones say how to prove them
            if (k.sourceType == null || "manual".equals(k.sourceType)) box.add(left(cardText(trackingText(k), MUTED, CARD_TEXT - 44)));
        }
        // the button stays on a finished task too (owner, 2026-11-14): proof can still be sent after it is complete
        {
            // a free space has nothing to prove: no button there
            if (!secret && k.id != null && !TileInfo.isFree(t))
            {
                // RuneLite sends its own proof: a game screenshot straight to this task, waiting for review
                JButton shot = button("Send screenshot", "Takes a screenshot of the game now and sends it as proof for this task");
                String what = k.description == null || k.description.isEmpty() ? TileInfo.shownTitle(t) : k.description;
                // sent to the board this card is from (not whatever is selected when it's clicked), stamped with its tile
                String boardId = lastBoard == null ? null : lastBoard.teamBoardId;
                // a tile with several tasks or "or" options stamps which one the picture is for (owner, 2026-11-22)
                String tileName = t.tasks != null && t.tasks.size() > 1 && !what.equals(TileInfo.shownTitle(t))
                    ? TileInfo.shownTitle(t) + ": " + what : TileInfo.shownTitle(t);
                shot.addActionListener(e -> plugin.sendScreenshotProof(boardId, k.id, what, tileName));
                box.add(gap(5));
                box.add(fullWidth(shot, 24));
            }
        }
        return fullWidth(box, -1);
    }

    /** Big counts stay short so the task's name keeps its room: 3100000 -> "3.1M", 250000 -> "250K". */
    static String compact(int n)
    {
        if (n >= 1_000_000) return trimZero(String.format(java.util.Locale.ROOT, "%.1f", n / 1_000_000.0)) + "M";
        if (n >= 100_000) return (n / 1000) + "K";
        return String.valueOf(n);
    }

    private static String trimZero(String s)
    {
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    /** "Hydra's eye, Hydra's fang or Hydra's heart" */
    private String anyOf(List<Integer> ids)
    {
        List<String> n = new java.util.ArrayList<>();
        for (Integer id : ids) n.add(plugin.itemName(id));
        if (n.size() <= 1) return n.isEmpty() ? "" : n.get(0);
        return String.join(", ", n.subList(0, n.size() - 1)) + " or " + n.get(n.size() - 1);
    }

    /** "You, Nexussy": who on the team is going for this tile (you first); "You" alone on older servers. */
    private String workingNames(ApiModels.Tile t)
    {
        List<String> names = new java.util.ArrayList<>();
        if (t.workingOn != null)
        {
            for (ApiModels.WorkingOn w : t.workingOn) if (w.me) names.add(0, "You"); else names.add(w.name);
        }
        else if (plugin.isWorkingOn(t))
        {
            names.add("You");
        }
        if (names.size() > 4) return String.join(", ", names.subList(0, 4)) + " +" + (names.size() - 4);
        return String.join(", ", names);
    }

    /** One "working on" / "closest to done" tile: a clickable card with the count on the right. */
    private JPanel nextUpRow(ApiModels.Tile t, boolean pinned)
    {
        PanelUi.Card row = new PanelUi.Card(pinned ? PanelUi.GOLD : null).pad(6, pinned ? 6 : 8, 6, 8).hoverable();
        row.setLayout(new BorderLayout(6, 0));
        String count = TileInfo.countText(t);
        JLabel countLabel = new PanelUi.CrispLabel(count);
        countLabel.setFont(smallFont());
        countLabel.setForeground(GOLD);
        int textW = CARD_TEXT - (count.isEmpty() ? 0 : countLabel.getPreferredSize().width + 6);
        String who = pinned ? workingNames(t) : "";
        JLabel name = new PanelUi.CrispLabel(who.isEmpty() ? wrapHtml(t.title, Math.max(60, textW))
            : "<html><body style='width:" + Math.max(60, textW) + "px'>" + esc(t.title)
              + "<br><font color='#a69d8f'>" + esc(who) + "</font></body></html>");
        name.setFont(smallFont());
        name.setForeground(TEXT);
        row.add(name, BorderLayout.CENTER);
        if (!count.isEmpty()) row.add(countLabel, BorderLayout.EAST);
        row.setToolTipText("Open this tile");
        row.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                showTile(t);
            }
        });
        return fullWidth(row, -1);
    }

    /** A status message card with a coloured left edge (green ok, amber warning/info, red error). */
    private static JPanel message(String text, ConnectionStatus.Level level)
    {
        Color edge = level == ConnectionStatus.Level.OK ? GOOD
            : level == ConnectionStatus.Level.ERROR ? ERROR
            : level == ConnectionStatus.Level.INFO ? new Color(0x7d7262) : WAIT;
        Color fg = level == ConnectionStatus.Level.ERROR ? ERROR : TEXT;
        PanelUi.Card p = new PanelUi.Card(edge).pad(6, 7, 6, 7);
        p.setLayout(new BorderLayout());
        JLabel l = new PanelUi.CrispLabel(wrapHtml(text, CARD_TEXT));
        l.setFont(smallFont());
        l.setForeground(fg);
        p.add(l, BorderLayout.CENTER);
        return fullWidth(p, -1);
    }

    private static PanelUi.Card card(Color edge)
    {
        return new PanelUi.Card(edge);
    }

    private static JComponent section(String text)
    {
        PanelUi.SectionHeader h = new PanelUi.SectionHeader(text);
        h.setFont(capsFont());
        return fullWidth(h, -1);
    }

    private static JButton button(String text, String tip)
    {
        JButton b = new PanelUi.StoneButton(text);
        b.setFont(smallFont());
        b.setToolTipText(tip);
        return b;
    }

    private static JLabel muted(String text)
    {
        JLabel l = new PanelUi.CrispLabel(wrapHtml(text, INNER - 4));
        l.setFont(smallFont());
        l.setForeground(MUTED);
        return l;
    }

    private static JLabel cardText(String text, Color color, int width)
    {
        JLabel l = new PanelUi.CrispLabel(wrapHtml(text, width));
        l.setFont(smallFont());
        l.setForeground(color);
        l.setVerticalAlignment(SwingConstants.TOP);
        return l;
    }

    /** A drawn tick box (crisp at any scale, unlike "[x]" text). */
    private static ImageIcon checkIcon(boolean done, int s)
    {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        try
        {
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(done ? new Color(0x1d4a1d) : new Color(0, 0, 0, 120));
            g.fillRoundRect(0, 0, s - 1, s - 1, 3, 3);
            g.setColor(done ? ColorScheme.PROGRESS_COMPLETE_COLOR : new Color(0x7a5c22));
            g.drawRoundRect(0, 0, s - 1, s - 1, 3, 3);
            if (done)
            {
                g.setStroke(new java.awt.BasicStroke(1.6f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                g.drawPolyline(new int[] {2, s / 2 - 1, s - 3}, new int[] {s / 2, s - 3, 2}, 3);
            }
        }
        finally
        {
            g.dispose();
        }
        return new ImageIcon(img);
    }

    private static final ImageIcon BOX_ON = checkIcon(true, 11);
    private static final ImageIcon BOX_OFF = checkIcon(false, 11);
    private static final ImageIcon SMALL_ON = checkIcon(true, 9);
    private static final ImageIcon SMALL_OFF = checkIcon(false, 9);

    /** Tick box + wrapped text; indent = a sub-item (smaller box, pushed in). */
    private static JPanel checkRow(String text, boolean done, Color color, boolean indent)
    {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(indent ? 2 : 0, indent ? 17 : 0, 0, 0));
        JLabel box = new PanelUi.CrispLabel(indent ? (done ? SMALL_ON : SMALL_OFF) : (done ? BOX_ON : BOX_OFF));
        box.setVerticalAlignment(SwingConstants.TOP);
        box.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));
        row.add(box, BorderLayout.WEST);
        JLabel l = new PanelUi.CrispLabel(wrapHtml(text == null ? "" : text, CARD_TEXT - (indent ? 34 : 18)));
        l.setFont(indent ? smallFont() : bodyFont());
        l.setForeground(color);
        row.add(l, BorderLayout.CENTER);
        return fullWidth(row, -1);
    }

    /** A thin line between a card's header and its tasks. */
    private static JComponent divider()
    {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0),
            BorderFactory.createMatteBorder(1, 0, 0, 0, ColorScheme.MEDIUM_GRAY_COLOR)));
        return fullWidth(p, 13);
    }

    /** Progress bar tall enough that its numbers never overlap the fill edge. */
    private static JProgressBar progressBar(int value, int max, String text, boolean done)
    {
        JProgressBar bar = new JProgressBar(0, Math.max(1, max));
        bar.setValue(value);
        bar.setStringPainted(true);
        bar.setFont(smallFont());
        bar.setString(text);
        bar.setForeground(done ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.BRAND_ORANGE);
        bar.setBackground(PanelUi.BG);
        bar.setBorder(BorderFactory.createEmptyBorder());
        return fullWidth(bar, 16);
    }

    private static JLabel cardText(String text, Color color)
    {
        JLabel l = new PanelUi.CrispLabel(wrapHtml(text, CARD_TEXT));
        l.setFont(smallFont());
        l.setForeground(color);
        return l;
    }

    private static void hover(JPanel p, Color normal, Color over)
    {
        p.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e)
            {
                p.setBackground(over);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                p.setBackground(normal);
            }
        });
    }

    // ------------------------------------------------------------------ layout helpers

    private static JPanel column()
    {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);
        return p;
    }

    private static Component gap(int h)
    {
        Component c = Box.createVerticalStrut(h);
        ((JComponent) c).setAlignmentX(LEFT_ALIGNMENT);
        return c;
    }

    private static <T extends JComponent> T left(T c)
    {
        c.setAlignmentX(LEFT_ALIGNMENT);
        return c;
    }

    /** Stretch to the column's width; height fixed (h > 0) or the component's own (h = -1). */
    private static <T extends JComponent> T fullWidth(T c, int h)
    {
        c.setAlignmentX(LEFT_ALIGNMENT);
        int height = h > 0 ? h : c.getPreferredSize().height;
        if (h > 0) c.setPreferredSize(new Dimension(INNER, h));
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        return c;
    }

    private static void add(JPanel column, JComponent c)
    {
        c.setAlignmentX(LEFT_ALIGNMENT);
        column.add(c);
    }

    private static void add(JPanel column, Component c)
    {
        if (c instanceof JComponent) ((JComponent) c).setAlignmentX(LEFT_ALIGNMENT);
        column.add(c);
    }

    private static String wrapHtml(String text, int width)
    {
        return "<html><body style='width:" + width + "px'>" + esc(text).replace("\n", "<br>") + "</body></html>";
    }

    private static String esc(String s)
    {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * The tile's picture: its item, else its uploaded icon (fetched once, shared with the overlay), else an item
     * picked from its emoji / title / tasks (TileIcons), else its initials. Secret tiles keep their "?".
     */
    private void loadIcon(JLabel label, ApiModels.Tile tile, ItemManager itemManager)
    {
        if (TileInfo.isSecret(tile)) return;
        Integer item = tile.iconItemId;
        if (item == null && tile.hasIcon && tile.iconUrl != null && tile.iconEmoji == null)
        {
            BufferedImage up = plugin.tileImage(tile.iconUrl);
            if (up != null)
            {
                label.setIcon(new ImageIcon(fit(up, Math.max(10, cell - 6))));
                return;
            }
            if (!plugin.tileImageMissing(tile.iconUrl)) return; // still loading: iconsChanged() redraws
        }
        if (item == null) item = TileIcons.fallbackItem(tile);
        if (item == null || itemManager == null)
        {
            label.setText(BoardOverlay.initials(tile.title));
            label.setForeground(new Color(235, 225, 205));
            label.setFont(cell >= 26 ? boldFont() : smallFont());
            return;
        }
        AsyncBufferedImage img = itemManager.getImage(item);
        final int px = Math.max(10, cell - 6);
        Runnable set = () -> label.setIcon(new ImageIcon(fit(img, px)));
        set.run();
        img.onLoaded(() -> javax.swing.SwingUtilities.invokeLater(set));
    }

    /** Scales a picture to fit a px square, keeping its shape. */
    private static java.awt.Image fit(BufferedImage img, int px)
    {
        int w = Math.max(1, img.getWidth());
        int h = Math.max(1, img.getHeight());
        double s = px / (double) Math.max(w, h);
        return img.getScaledInstance(Math.max(1, (int) Math.round(w * s)), Math.max(1, (int) Math.round(h * s)), java.awt.Image.SCALE_SMOOTH);
    }

    /** A short burst of fireworks across the top of a finished tile's card (about 1.8 s, then it rests). */
    static final class Fireworks extends JComponent
    {
        static final int HEIGHT = 58;
        private static final Color[] COLOURS = {
            new Color(0xffb83f), new Color(0x6ee06e), new Color(0x5ec8ff), new Color(0xff6f91), new Color(0xfff27a)};
        private static final long RUN_MS = 1800;
        private final java.util.List<float[]> sparks = new java.util.ArrayList<>(); // x, y, vx, vy, life, colour
        private final java.util.Random rnd = new java.util.Random();
        private final javax.swing.Timer timer = new javax.swing.Timer(16, e -> step());
        private long started;
        private int bursts;

        private final String message;

        Fireworks()
        {
            this("TILE COMPLETE!");
        }

        Fireworks(String message)
        {
            this.message = message;
            setOpaque(false);
        }

        void start()
        {
            started = System.currentTimeMillis();
            bursts = 0;
            sparks.clear();
            timer.start();
        }

        void stop()
        {
            timer.stop();
        }

        private void step()
        {
            long t = System.currentTimeMillis() - started;
            int w = Math.max(40, getWidth());
            // a new burst every ~280 ms for the first second
            if (t < 1100 && t / 280 >= bursts)
            {
                bursts++;
                float cx = 20 + rnd.nextFloat() * (w - 40);
                float cy = 14 + rnd.nextFloat() * 18;
                int colour = rnd.nextInt(COLOURS.length);
                for (int i = 0; i < 26; i++)
                {
                    double a = Math.PI * 2 * i / 26 + rnd.nextDouble() * 0.2;
                    float sp = 0.9f + rnd.nextFloat() * 1.3f;
                    sparks.add(new float[] {cx, cy, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp, 1f, colour});
                }
            }
            for (java.util.Iterator<float[]> it = sparks.iterator(); it.hasNext(); )
            {
                float[] s = it.next();
                s[0] += s[2];
                s[1] += s[3];
                s[2] *= 0.96f;
                s[3] = s[3] * 0.96f + 0.035f; // a little gravity
                s[4] -= 0.018f;
                if (s[4] <= 0) it.remove();
            }
            if (t > RUN_MS && sparks.isEmpty()) timer.stop();
            repaint();
        }

        @Override
        protected void paintComponent(java.awt.Graphics g0)
        {
            java.awt.Graphics2D g = (java.awt.Graphics2D) g0.create();
            try
            {
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                for (float[] s : sparks)
                {
                    Color c = COLOURS[(int) s[5]];
                    int a = Math.max(0, Math.min(255, Math.round(255 * s[4])));
                    g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), a));
                    float r = 1.2f + 1.3f * s[4];
                    g.fill(new java.awt.geom.Ellipse2D.Float(s[0] - r, s[1] - r, r * 2, r * 2));
                }
                // the banner settles in as the sparks fade
                long t = System.currentTimeMillis() - started;
                float k = Math.min(1f, Math.max(0f, (t - 250) / 500f));
                String msg = message;
                g.setFont(boldFont());
                java.awt.FontMetrics fm = g.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(msg)) / 2;
                int y = getHeight() - 8 + Math.round((1 - k) * 6);
                g.setColor(new Color(0, 0, 0, Math.round(200 * k)));
                g.drawString(msg, x + 1, y + 1);
                g.setColor(new Color(110, 224, 110, Math.round(255 * k)));
                g.drawString(msg, x, y);
            }
            finally
            {
                g.dispose();
            }
        }
    }

    /** The scrolling column: always as wide as the sidebar, so text wraps instead of running off the edge. */
    private static final class Column extends JPanel implements Scrollable
    {
        Column()
        {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        }

        @Override
        protected void paintComponent(java.awt.Graphics g)
        {
            super.paintComponent(g);
            if (PanelUi.theme == PanelTheme.INFERNO && PanelUi.decorations())
            {
                // the Inferno's rock is cracked with lava behind the cards
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                try
                {
                    ThemeFx.lavaGround(g2, getWidth(), getHeight(), g.getClipBounds(), PanelUi.animations ? PanelUi.pulse() : 0.5f);
                }
                finally
                {
                    g2.dispose();
                }
            }
        }

        @Override
        public Dimension getPreferredScrollableViewportSize()
        {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
        {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
        {
            return Math.max(16, visibleRect.height - 32);
        }

        @Override
        public boolean getScrollableTracksViewportWidth()
        {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight()
        {
            return false;
        }
    }
}
