package com.forgebingo;

import com.forgebingo.core.ApiClient;
import com.forgebingo.core.ApiModels;
import com.forgebingo.core.ConnectionStatus;
import com.forgebingo.core.ItemSlots;
import com.forgebingo.core.TileIcons;
import com.forgebingo.core.TileInfo;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
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
    private static final Color CARD = ColorScheme.DARKER_GRAY_COLOR;
    private static final Color CARD_HOVER = ColorScheme.DARKER_GRAY_HOVER_COLOR;
    private static final Color MUTED = new Color(150, 150, 150);
    private static final Color TEXT = new Color(220, 220, 220);
    private static final Color ERROR = new Color(255, 112, 112);
    private static final Color CLEAR = new Color(0, 0, 0, 0);

    /** square size for the current board (shrinks for big boards) */
    private int cell = ICON + 6;

    private final ForgeBingoPlugin plugin;
    private final ApiClient api;
    private final JLabel status = new JLabel("Not connected");
    private final JLabel scope = new JLabel(" ");
    /** Verified RSN / name mismatch / revoked key, one card per line (from POST /v1/hello). */
    private final JPanel identity = column();
    private final JLabel queueLabel = new JLabel(" ");
    private final JLabel teamLabel = new JLabel(" ");
    private final JLabel eventLabel = new JLabel(" ");
    private final JProgressBar tilesBar = new JProgressBar();
    private final JPanel boardCard = column();
    private final JComboBox<ApiModels.BoardSummary> boardBox = new JComboBox<>();
    private final JPanel grid = new JPanel();
    private final JPanel gridWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
    /** one tile, opened by clicking it on the grid: title, state, every task with its progress and how it is tracked */
    private final JPanel detail = column();
    /** the 3 open tiles closest to done, like the website's "What can I do next?" */
    private final JPanel nextUp = column();
    private String selectedTileId;
    private final Map<String, JLabel> tileLabels = new ConcurrentHashMap<>();
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
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        Column content = new Column();
        content.setBorder(BorderFactory.createEmptyBorder(PAD, PAD, PAD, PAD));
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);
        content.setOpaque(true);

        // --- header card: name, connection, account, send queue
        JPanel head = card(null);
        JLabel title = new JLabel("ForgeBingo");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(ColorScheme.BRAND_ORANGE);
        head.add(left(title));
        head.add(Box.createVerticalStrut(2));
        status.setFont(FontManager.getRunescapeSmallFont());
        head.add(left(status));
        scope.setFont(FontManager.getRunescapeSmallFont());
        scope.setForeground(MUTED);
        head.add(left(scope));
        queueLabel.setFont(FontManager.getRunescapeSmallFont());
        queueLabel.setForeground(MUTED);
        head.add(left(queueLabel));
        add(content, head);

        identity.add(message("Log in to the game so we can check which character you're playing.", ConnectionStatus.Level.INFO));
        add(content, gap(6));
        add(content, identity);
        teamNews.setVisible(false);
        add(content, teamNews);

        // --- board picker and actions
        add(content, gap(4));
        add(content, section("Board"));
        boardBox.setFocusable(false);
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
        teamLabel.setFont(FontManager.getRunescapeBoldFont());
        teamLabel.setForeground(Color.WHITE);
        eventLabel.setFont(FontManager.getRunescapeSmallFont());
        eventLabel.setForeground(MUTED);
        tilesBar.setStringPainted(true);
        tilesBar.setFont(FontManager.getRunescapeSmallFont());
        tilesBar.setForeground(ColorScheme.BRAND_ORANGE);
        tilesBar.setBackground(ColorScheme.DARK_GRAY_COLOR);
        tilesBar.setBorder(BorderFactory.createEmptyBorder());
        boardCard.setBackground(CARD);
        boardCard.setOpaque(true);
        boardCard.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        boardCard.add(left(teamLabel));
        boardCard.add(left(eventLabel));
        boardCard.add(Box.createVerticalStrut(6));
        boardCard.add(fullWidth(tilesBar, 16));
        boardCard.setVisible(false);
        add(content, gap(10));
        add(content, boardCard);

        // --- grid, tile detail, next up
        gridWrap.setOpaque(false);
        grid.setOpaque(false);
        gridWrap.add(grid);
        add(content, gap(8));
        add(content, gridWrap);
        detail.setOpaque(true);
        detail.setBackground(CARD);
        detail.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 3, 0, 0, ColorScheme.BRAND_ORANGE),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        detail.setVisible(false);
        add(content, gap(6));
        add(content, detail);
        add(content, nextUp);
        // no leaderboard or notices list here (owner, 2026-11-10): drops are announced in chat instead

        JScrollPane scroll = new JScrollPane(content,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(BAR, 0));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        add(scroll, BorderLayout.CENTER);
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

    // ------------------------------------------------------------------ public API (called by the plugin)

    void setStatus(boolean ok, String text)
    {
        status.setText(wrapHtml(text, INNER - 16));
        status.setForeground(ok ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_ERROR_COLOR);
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

    void setQueueSize(int size, String lastError)
    {
        queueLabel.setText(wrapHtml(size == 0 ? "Nothing waiting to send"
            : size + " waiting to send" + (lastError == null ? "" : " (" + lastError + ")"), INNER - 16));
        queueLabel.setForeground(size == 0 ? MUTED : ColorScheme.PROGRESS_INPROGRESS_COLOR);
    }

    void setBoards(List<ApiModels.BoardSummary> boards, String activeId)
    {
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
        JPanel card = card(ColorScheme.PROGRESS_COMPLETE_COLOR);
        card.add(fullWidth(fw, Fireworks.HEIGHT));
        card.add(left(cardText("Your team finished " + (tileTitle == null || tileTitle.isEmpty() ? "a tile" : tileTitle) + "!", Color.WHITE)));
        teamNews.add(gap(6));
        teamNews.add(card);
        teamNews.setVisible(true);
        teamNews.revalidate();
        teamNews.repaint();
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
        teamLabel.setText(wrapHtml(board.teamName == null ? "Your team" : board.teamName, CARD_TEXT));
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
        tilesBar.setForeground(blackout ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.BRAND_ORANGE);
        boardCard.setVisible(true);

        tileLabels.clear();
        grid.removeAll();
        grid.setLayout(new GridLayout(size, size, 2, 2));
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
        if (previous != null && tileLabels.get(previous) != null) tileLabels.get(previous).setBorder(BorderFactory.createLineBorder(CLEAR, 2));
        if (t.id != null && tileLabels.get(t.id) != null) tileLabels.get(t.id).setBorder(BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE, 2));
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
        JLabel title = new JLabel(wrapHtml(TileInfo.shownTitle(t), CARD_TEXT - 22));
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(ColorScheme.BRAND_ORANGE);
        head.add(title, BorderLayout.CENTER);
        JLabel close = new JLabel("x");
        close.setFont(FontManager.getRunescapeBoldFont());
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
            JLabel pts = new JLabel(BoardOverlay.points(TileInfo.worth(t)));
            pts.setFont(FontManager.getRunescapeSmallFont());
            pts.setForeground(ColorScheme.BRAND_ORANGE);
            pts.setVerticalAlignment(SwingConstants.TOP);
            stateRow.add(pts, BorderLayout.EAST);
        }
        detail.add(fullWidth(stateRow, -1));
        if (t.description != null && !t.description.isEmpty()) detail.add(left(cardText(t.description, MUTED)));
        if (!t.completed && t.workingOn != null && !t.workingOn.isEmpty())
        {
            detail.add(left(cardText("Working on it: " + workingNames(t), ColorScheme.BRAND_ORANGE)));
        }
        detail.add(divider());

        for (ApiModels.Task k : t.tasks)
        {
            boolean kDone = k.completed || t.completed;
            detail.add(checkRow(k.description, kDone, kDone ? ColorScheme.PROGRESS_COMPLETE_COLOR : Color.WHITE, false));
            List<ItemSlots.Slot> slots = ItemSlots.of(k);
            if (!slots.isEmpty() && !kDone)
            {
                // "one of each": every item is its own line with its own tick, like the website
                for (ItemSlots.Slot s : slots)
                {
                    String name = s.itemIds.size() == 1 ? plugin.itemName(s.itemIds.get(0)) : "Any one: " + anyOf(s.itemIds);
                    String count = s.need > 1 ? "  " + s.approved + "/" + s.need : "";
                    String pending = s.pending > 0 && !s.done() ? " (waiting)" : "";
                    Color c = s.done() ? ColorScheme.PROGRESS_COMPLETE_COLOR : s.pending > 0 ? ColorScheme.PROGRESS_INPROGRESS_COLOR : Color.LIGHT_GRAY;
                    detail.add(checkRow(name + count + pending, s.done(), c, true));
                }
            }
            else if (k.requiredQty > 1 && !kDone)
            {
                detail.add(gap(3));
                detail.add(progressBar(Math.min(k.requiredQty, k.approvedQty), k.requiredQty,
                    k.approvedQty + " / " + k.requiredQty + (k.pendingQty > 0 ? "   +" + k.pendingQty + " waiting" : ""), false));
            }
            else if (k.pendingQty > 0 && !kDone)
            {
                detail.add(left(cardText("Sent, waiting for a reviewer", ColorScheme.PROGRESS_INPROGRESS_COLOR)));
            }
            if (!kDone)
            {
                detail.add(gap(2));
                detail.add(left(cardText(trackingText(k), MUTED)));
            }
            if (!kDone && !secret && k.id != null)
            {
                // RuneLite sends its own proof: a game screenshot straight to this task, waiting for review
                JButton shot = button("Send screenshot", "Takes a screenshot of the game now and sends it as proof for this task");
                String what = k.description == null || k.description.isEmpty() ? TileInfo.shownTitle(t) : k.description;
                shot.addActionListener(e -> plugin.sendScreenshotProof(k.id, what));
                detail.add(gap(4));
                detail.add(fullWidth(shot, 22));
            }
            detail.add(gap(8));
        }
        if (!t.completed && t.id != null)
        {
            boolean on = plugin.isWorkingOn(t);
            JButton pin = button(on ? "Stop working on it" : "I'm working on this",
                on ? "Take this tile off your team's Working on list" : "Show your team you're going for this tile");
            if (on) pin.setForeground(ColorScheme.BRAND_ORANGE);
            pin.addActionListener(e -> {
                plugin.setWorkingOn(t.id, !on);
                if (lastBoard != null) setBoard(lastBoard, null, lastItemManager);
            });
            detail.add(fullWidth(pin, 24));
        }
        detail.setVisible(true);
        detail.revalidate();
        detail.repaint();
    }

    void closeTile()
    {
        if (selectedTileId != null && tileLabels.get(selectedTileId) != null) tileLabels.get(selectedTileId).setBorder(BorderFactory.createLineBorder(CLEAR, 2));
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
        return TileInfo.isSecret(t) && !t.completed ? "Secret tile: revealed later" : t.completed ? "Done" : reason != null ? "Needs changes: " + reason
            : pending ? "Waiting for review" : t.tasks.isEmpty() ? "No tasks yet" : doneTasks + " of " + t.tasks.size() + " tasks done";
    }

    static Color stateTextColour(ApiModels.Tile t)
    {
        return t.completed ? ColorScheme.PROGRESS_COMPLETE_COLOR : TileInfo.needsChangesReason(t) != null ? ERROR
            : TileInfo.waiting(t) ? ColorScheme.PROGRESS_INPROGRESS_COLOR : Color.LIGHT_GRAY;
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
            default: return "Needs a screenshot or video: add proof on the website";
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
        JLabel l = new JLabel();
        l.setHorizontalAlignment(JLabel.CENTER);
        l.setOpaque(true);
        l.setPreferredSize(new Dimension(cell, cell));
        if (t == null)
        {
            l.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            return l;
        }
        TileInfo.State st = TileInfo.state(t);
        l.setBackground(stateColour(st));
        if (st == TileInfo.State.SECRET)
        {
            l.setText("?");
            l.setForeground(new Color(255, 215, 90));
            l.setFont(l.getFont().deriveFont(java.awt.Font.BOLD, Math.max(10f, cell * 0.55f)));
        }
        StringBuilder tip = new StringBuilder("<html><b>").append(esc(st == TileInfo.State.SECRET ? "Secret tile" : t.title)).append("</b>");
        if (st == TileInfo.State.SECRET) tip.append("<br>").append(esc(t.description == null || t.description.isEmpty() ? "Revealed later" : t.description));
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
        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        l.setBorder(BorderFactory.createLineBorder(t.id != null && t.id.equals(selectedTileId) ? ColorScheme.BRAND_ORANGE : CLEAR, 2));
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
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(CARD);
        row.setBorder(pinned
            ? BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, ColorScheme.BRAND_ORANGE), BorderFactory.createEmptyBorder(5, 5, 5, 8))
            : BorderFactory.createEmptyBorder(5, 8, 5, 8));
        String count = TileInfo.countText(t);
        JLabel countLabel = new JLabel(count);
        countLabel.setFont(FontManager.getRunescapeSmallFont());
        countLabel.setForeground(ColorScheme.BRAND_ORANGE);
        int textW = CARD_TEXT - (count.isEmpty() ? 0 : countLabel.getPreferredSize().width + 6);
        String who = pinned ? workingNames(t) : "";
        JLabel name = new JLabel(who.isEmpty() ? wrapHtml(t.title, Math.max(60, textW))
            : "<html><body style='width:" + Math.max(60, textW) + "px'>" + esc(t.title)
              + "<br><font color='#969696'>" + esc(who) + "</font></body></html>");
        name.setFont(FontManager.getRunescapeSmallFont());
        name.setForeground(TEXT);
        row.add(name, BorderLayout.CENTER);
        if (!count.isEmpty()) row.add(countLabel, BorderLayout.EAST);
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        row.setToolTipText("Open this tile");
        hover(row, CARD, CARD_HOVER);
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
        Color edge = level == ConnectionStatus.Level.OK ? ColorScheme.PROGRESS_COMPLETE_COLOR
            : level == ConnectionStatus.Level.ERROR ? ColorScheme.PROGRESS_ERROR_COLOR
            : level == ConnectionStatus.Level.INFO ? ColorScheme.MEDIUM_GRAY_COLOR : ColorScheme.PROGRESS_INPROGRESS_COLOR;
        Color fg = level == ConnectionStatus.Level.ERROR ? ERROR : TEXT;
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(level == ConnectionStatus.Level.ERROR ? new Color(58, 34, 34) : CARD);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 3, 0, 0, edge),
            BorderFactory.createEmptyBorder(5, 7, 5, 7)));
        JLabel l = new JLabel(wrapHtml(text, CARD_TEXT));
        l.setFont(FontManager.getRunescapeSmallFont());
        l.setForeground(fg);
        p.add(l, BorderLayout.CENTER);
        return fullWidth(p, -1);
    }

    private static JPanel card(Color edge)
    {
        JPanel p = column();
        p.setOpaque(true);
        p.setBackground(CARD);
        Border pad = BorderFactory.createEmptyBorder(8, 8, 8, 8);
        p.setBorder(edge == null ? pad : BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, edge), pad));
        return p;
    }

    private static JComponent section(String text)
    {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(FontManager.getRunescapeSmallFont());
        l.setForeground(ColorScheme.BRAND_ORANGE);
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(4, 0, 4, 0),
            BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
                BorderFactory.createEmptyBorder(0, 0, 3, 0))));
        p.add(l, BorderLayout.WEST);
        return fullWidth(p, -1);
    }

    private static JButton button(String text, String tip)
    {
        JButton b = new JButton(text);
        b.setFocusable(false);
        b.setFont(FontManager.getRunescapeSmallFont());
        b.setToolTipText(tip);
        return b;
    }

    private static JLabel muted(String text)
    {
        JLabel l = new JLabel(wrapHtml(text, INNER - 4));
        l.setFont(FontManager.getRunescapeSmallFont());
        l.setForeground(MUTED);
        return l;
    }

    private static JLabel cardText(String text, Color color, int width)
    {
        JLabel l = new JLabel(wrapHtml(text, width));
        l.setFont(FontManager.getRunescapeSmallFont());
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
        JLabel box = new JLabel(indent ? (done ? SMALL_ON : SMALL_OFF) : (done ? BOX_ON : BOX_OFF));
        box.setVerticalAlignment(SwingConstants.TOP);
        box.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));
        row.add(box, BorderLayout.WEST);
        JLabel l = new JLabel(wrapHtml(text == null ? "" : text, CARD_TEXT - (indent ? 34 : 18)));
        l.setFont(indent ? FontManager.getRunescapeSmallFont() : FontManager.getRunescapeFont());
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
        bar.setFont(FontManager.getRunescapeSmallFont());
        bar.setString(text);
        bar.setForeground(done ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.BRAND_ORANGE);
        bar.setBackground(ColorScheme.DARK_GRAY_COLOR);
        bar.setBorder(BorderFactory.createEmptyBorder());
        return fullWidth(bar, 16);
    }

    private static JLabel cardText(String text, Color color)
    {
        JLabel l = new JLabel(wrapHtml(text, CARD_TEXT));
        l.setFont(FontManager.getRunescapeSmallFont());
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
            label.setFont(cell >= 26 ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeSmallFont());
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
                g.setFont(FontManager.getRunescapeBoldFont());
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
