package com.forgebingo;

import com.forgebingo.core.ApiClient;
import com.forgebingo.core.ApiModels;
import com.forgebingo.core.ConnectionStatus;
import com.forgebingo.core.TileInfo;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Cursor;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
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
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;

/** Sidebar. All methods must be called on the EDT (the plugin wraps calls in invokeLater). */
class ForgeBingoPanel extends PluginPanel
{
    private static final int MAX_NOTICES = 8;
    private static final int ICON = 32;
    /** square size for the current board (shrinks for big boards) */
    private int cell = ICON + 6;

    private final ForgeBingoPlugin plugin;
    private final ApiClient api;
    private final JLabel status = new JLabel("Not connected");
    private final JLabel scope = new JLabel(" ");
    /** Verified RSN / name mismatch / revoked key, one line per event (from POST /v1/hello). */
    private final JPanel identity = new JPanel();
    private final JLabel queueLabel = new JLabel(" ");
    private final JLabel header = new JLabel(" ");
    private final JComboBox<ApiModels.BoardSummary> boardBox = new JComboBox<>();
    private final JPanel grid = new JPanel();
    private final JPanel standings = new JPanel();
    private final JPanel notices = new JPanel();
    /** one tile, opened by clicking it on the grid: title, state, every task with its progress and how it is tracked */
    private final JPanel detail = new JPanel();
    private final JLabel progress = new JLabel(" ");
    /** the 3 open tiles closest to done, like the website's "What can I do next?" */
    private final JPanel nextUp = new JPanel();
    private String selectedTileId;
    private final Map<String, JLabel> tileLabels = new ConcurrentHashMap<>();
    private final Deque<String> noticeLog = new ArrayDeque<>();
    private final Map<String, ImageIcon> proxyIcons = new ConcurrentHashMap<>();
    private boolean updatingBox;

    ForgeBingoPanel(ForgeBingoPlugin plugin, ApiClient api)
    {
        super(false);
        this.plugin = plugin;
        this.api = api;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setOpaque(false);
        top.add(status);
        scope.setForeground(Color.GRAY);
        top.add(scope);
        identity.setLayout(new BoxLayout(identity, BoxLayout.Y_AXIS));
        identity.setOpaque(false);
        identity.setAlignmentX(LEFT_ALIGNMENT);
        identity.add(wrapLabel("Log in to the game to check your RSN.", Color.GRAY));
        top.add(identity);
        top.add(queueLabel);
        top.add(Box.createVerticalStrut(6));
        top.add(boardBox);
        top.add(Box.createVerticalStrut(6));
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> plugin.refreshNow());
        top.add(refresh);
        top.add(Box.createVerticalStrut(4));
        JButton showBoard = new JButton("Show board on screen");
        showBoard.setToolTipText("Open the board over the game (click off it, press Esc or the hotkey to close)");
        showBoard.addActionListener(e -> plugin.toggleBoardOverlay());
        top.add(showBoard);
        top.add(Box.createVerticalStrut(6));
        top.add(header);
        progress.setForeground(Color.LIGHT_GRAY);
        top.add(progress);
        add(top, BorderLayout.NORTH);

        boardBox.addActionListener(e -> {
            if (updatingBox) return;
            Object sel = boardBox.getSelectedItem();
            if (sel instanceof ApiModels.BoardSummary) plugin.selectBoard(((ApiModels.BoardSummary) sel).teamBoardId);
        });

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        body.add(grid);
        body.add(Box.createVerticalStrut(6));
        detail.setLayout(new BoxLayout(detail, BoxLayout.Y_AXIS));
        detail.setOpaque(true);
        detail.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        detail.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        detail.setVisible(false);
        body.add(detail);
        nextUp.setLayout(new BoxLayout(nextUp, BoxLayout.Y_AXIS));
        nextUp.setOpaque(false);
        body.add(Box.createVerticalStrut(6));
        body.add(nextUp);
        body.add(Box.createVerticalStrut(8));
        body.add(section("Leaderboard"));
        standings.setLayout(new BoxLayout(standings, BoxLayout.Y_AXIS));
        standings.setOpaque(false);
        body.add(standings);
        body.add(Box.createVerticalStrut(8));
        body.add(section("Notices"));
        notices.setLayout(new BoxLayout(notices, BoxLayout.Y_AXIS));
        notices.setOpaque(false);
        body.add(notices);
        add(body, BorderLayout.CENTER);
    }

    private static JLabel section(String text)
    {
        JLabel l = new JLabel(text);
        l.setForeground(ColorScheme.BRAND_ORANGE);
        return l;
    }

    void setStatus(boolean ok, String text)
    {
        status.setText(text);
        status.setForeground(ok ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_ERROR_COLOR);
    }

    void setScope(String text)
    {
        scope.setText(text == null || text.isEmpty() ? " " : text);
    }

    void setIdentity(List<ConnectionStatus.Line> lines)
    {
        identity.removeAll();
        for (ConnectionStatus.Line l : lines)
        {
            Color c = l.level == ConnectionStatus.Level.OK ? ColorScheme.PROGRESS_COMPLETE_COLOR
                : l.level == ConnectionStatus.Level.ERROR ? ColorScheme.PROGRESS_ERROR_COLOR : ColorScheme.PROGRESS_INPROGRESS_COLOR;
            identity.add(wrapLabel(l.text, c));
        }
        identity.revalidate();
        identity.repaint();
    }

    void setQueueSize(int size, String lastError)
    {
        queueLabel.setText(size == 0 ? "Nothing waiting to send"
            : size + " waiting to send" + (lastError == null ? "" : " (" + lastError + ")"));
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

    void addNotice(String text)
    {
        noticeLog.addFirst(text);
        while (noticeLog.size() > MAX_NOTICES) noticeLog.removeLast();
        notices.removeAll();
        for (String n : noticeLog)
        {
            notices.add(wrapLabel(n, Color.LIGHT_GRAY));
        }
        notices.revalidate();
        notices.repaint();
    }

    void setBoard(ApiModels.BoardResponse board, ApiModels.LeaderboardResponse leaderboard, ItemManager itemManager)
    {
        StringBuilder h = new StringBuilder("<html>");
        if (board.teamName != null) h.append(esc(board.teamName));
        if (board.event != null && board.event.name != null) h.append(" - ").append(esc(board.event.name));
        if (board.event != null && !board.event.active) h.append(" (not running)");
        header.setText(h.append("</html>").toString());

        int size = board.size();
        // squares shrink to fit the sidebar on big boards (a 10x10 board would otherwise cut its icons off)
        cell = Math.max(14, Math.min(ICON + 6, (PluginPanel.PANEL_WIDTH - 16 - 2 * (size - 1)) / Math.max(1, size)));
        int done = 0;
        for (ApiModels.Tile t : board.tiles) if (t.completed) done++;
        progress.setText(done + " of " + board.tiles.size() + " tiles done" + (done == board.tiles.size() && !board.tiles.isEmpty() ? "  -  BLACKOUT!" : ""));
        progress.setForeground(done == board.tiles.size() && !board.tiles.isEmpty() ? ColorScheme.PROGRESS_COMPLETE_COLOR : Color.LIGHT_GRAY);
        tileLabels.clear();
        grid.removeAll();
        grid.setOpaque(false);
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
        List<ApiModels.Tile> next = TileInfo.nextUp(board.tiles, 3);
        if (!next.isEmpty()) nextUp.add(wrapLabel("Closest to done:", ColorScheme.BRAND_ORANGE));
        for (ApiModels.Tile t : next)
        {
            String count = TileInfo.countText(t);
            JLabel l = wrapLabel("- " + t.title + (count.isEmpty() ? "" : " (" + count + ")"), Color.LIGHT_GRAY);
            l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            l.addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseClicked(MouseEvent e)
                {
                    showTile(t);
                }
            });
            nextUp.add(l);
        }
        nextUp.revalidate();
        nextUp.repaint();
        // keep the open tile in step with live updates
        if (selectedTileId != null)
        {
            ApiModels.Tile still = null;
            for (ApiModels.Tile t : board.tiles) if (selectedTileId.equals(t.id)) still = t;
            if (still != null) showTile(still); else closeTile();
        }

        standings.removeAll();
        if (leaderboard != null)
        {
            for (ApiModels.LeaderboardTeam t : leaderboard.teams)
            {
                String line = "#" + t.rank + " " + (t.teamName == null ? "?" : t.teamName) + " - " + t.score + " pts, "
                    + t.tilesCompleted + " tiles";
                standings.add(wrapLabel(line, t.isMyTeam ? ColorScheme.BRAND_ORANGE : Color.LIGHT_GRAY));
            }
        }
        standings.revalidate();
        standings.repaint();
    }

    private JLabel tileButton(ApiModels.Tile t, ItemManager itemManager)
    {
        JLabel l = new JLabel();
        l.setHorizontalAlignment(JLabel.CENTER);
        l.setOpaque(true);
        l.setPreferredSize(new Dimension(cell, cell));
        if (t == null)
        {
            l.setBackground(ColorScheme.DARK_GRAY_COLOR);
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
        else tip.append(" (").append(TileInfo.worth(t)).append(" pts)");
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
        l.setBorder(BorderFactory.createLineBorder(t.id != null && t.id.equals(selectedTileId) ? ColorScheme.BRAND_ORANGE : new Color(0, 0, 0, 0), 2));
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

    /** Opens one tile under the grid: what it is, where it stands, each task with its progress and how it is tracked. */
    void showTile(ApiModels.Tile t)
    {
        String previous = selectedTileId;
        selectedTileId = t.id;
        if (previous != null && tileLabels.get(previous) != null) tileLabels.get(previous).setBorder(BorderFactory.createLineBorder(new Color(0, 0, 0, 0), 2));
        if (t.id != null && tileLabels.get(t.id) != null) tileLabels.get(t.id).setBorder(BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE, 2));
        detail.removeAll();
        JLabel title = new JLabel("<html><body style='width:170px'><b>" + esc(t.title) + "</b></body></html>");
        title.setForeground(ColorScheme.BRAND_ORANGE);
        detail.add(title);
        boolean secret = TileInfo.isSecret(t);
        String state = stateText(t);
        if (!secret) state += "  -  " + TileInfo.worth(t) + " pts";
        if (!t.completed && "any".equals(t.completionMode) && t.tasks.size() > 1) state += " (any one finishes it)";
        detail.add(wrapLabel(state, stateTextColour(t)));
        if (t.description != null && !t.description.isEmpty()) detail.add(wrapLabel(t.description, Color.GRAY));
        detail.add(Box.createVerticalStrut(4));
        for (ApiModels.Task k : t.tasks)
        {
            detail.add(wrapLabel((k.completed ? "[x] " : "[ ] ") + k.description, k.completed ? ColorScheme.PROGRESS_COMPLETE_COLOR : Color.WHITE));
            if (k.requiredQty > 1)
            {
                JProgressBar bar = new JProgressBar(0, k.requiredQty);
                bar.setValue(Math.min(k.requiredQty, k.approvedQty));
                bar.setStringPainted(true);
                bar.setString(k.approvedQty + " / " + k.requiredQty + (k.pendingQty > 0 ? "  (+" + k.pendingQty + " pending)" : ""));
                bar.setForeground(k.completed ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.BRAND_ORANGE);
                bar.setAlignmentX(LEFT_ALIGNMENT);
                detail.add(bar);
            }
            else if (k.pendingQty > 0)
            {
                detail.add(wrapLabel("Sent, waiting for a reviewer", ColorScheme.PROGRESS_INPROGRESS_COLOR));
            }
            detail.add(wrapLabel(trackingText(k), Color.GRAY));
            detail.add(Box.createVerticalStrut(4));
        }
        JButton back = new JButton("Close");
        back.addActionListener(e -> closeTile());
        detail.add(back);
        detail.setVisible(true);
        detail.revalidate();
        detail.repaint();
    }

    void closeTile()
    {
        if (selectedTileId != null && tileLabels.get(selectedTileId) != null) tileLabels.get(selectedTileId).setBorder(BorderFactory.createLineBorder(new Color(0, 0, 0, 0), 2));
        selectedTileId = null;
        detail.setVisible(false);
        detail.removeAll();
        detail.revalidate();
        detail.repaint();
    }

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
        return t.completed ? ColorScheme.PROGRESS_COMPLETE_COLOR : TileInfo.needsChangesReason(t) != null ? new Color(255, 112, 112)
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

    private void loadIcon(JLabel label, ApiModels.Tile tile, ItemManager itemManager)
    {
        if (!tile.hasIcon) return;
        if (tile.iconItemId != null)
        {
            AsyncBufferedImage img = itemManager.getImage(tile.iconItemId);
            final int px = Math.max(10, cell - 6);
            Runnable set = () -> label.setIcon(new ImageIcon(img.getScaledInstance(px, px, java.awt.Image.SCALE_SMOOTH)));
            set.run();
            img.onLoaded(() -> javax.swing.SwingUtilities.invokeLater(set));
        }
        else if (tile.iconUrl != null) // already validated as /v1/icons/... by Sanitizer; fetched from the API host only
        {
            String path = tile.iconUrl;
            ImageIcon cached = proxyIcons.get(path);
            if (cached != null)
            {
                label.setIcon(cached);
                return;
            }
            plugin.fetchIcon(path, bytes -> {
                try
                {
                    BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
                    if (img == null || img.getWidth() > 256 || img.getHeight() > 256) return;
                    int px = Math.max(10, cell - 6);
                    ImageIcon ic = new ImageIcon(img.getScaledInstance(px, px, java.awt.Image.SCALE_SMOOTH));
                    proxyIcons.put(path, ic);
                    label.setIcon(ic);
                }
                catch (java.io.IOException ignored)
                {
                    // leave blank
                }
            });
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

    private static JLabel wrapLabel(String text, Color color)
    {
        JLabel l = new JLabel("<html><body style='width:170px'>" + esc(text) + "</body></html>");
        l.setForeground(color);
        return l;
    }

    private static String esc(String s)
    {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
