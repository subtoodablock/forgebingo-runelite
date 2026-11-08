package com.forgebingo;

import com.forgebingo.core.ApiModels;
import com.forgebingo.core.BoardLayout;
import com.forgebingo.core.TileInfo;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The bingo board drawn over the game (owner request, 2026-11-07). Toggled by the hotkey or the panel button; a
 * clicked tile pops open into a card; clicking off the board, Esc or taking a hit closes it, and the open tile is
 * remembered for next time. Drawn on the client thread; opened, closed and clicked from the AWT / Swing threads, so
 * all shared state is volatile and the board is the plugin's immutable-by-convention snapshot (never fetched here).
 */
public class BoardOverlay extends Overlay
{
    static final long FADE_MS = 150;
    static final long POP_MS = 180;

    private static final Color GOLD = new Color(0xffbf1f);
    private static final Color GOLD_DIM = new Color(0x8a6a1a);
    private static final Color STONE_TOP = new Color(0x2b2724);
    private static final Color STONE_BOTTOM = new Color(0x1a1714);
    private static final Color CARD_BG = new Color(0x221e1b);
    private static final Color DONE_TICK = new Color(0x7dff7d);
    private static final Color BAR_BG = new Color(0, 0, 0, 150);
    private static final Color BAR_FILL = new Color(0xff981f);
    private static final Color BAR_DONE = new Color(0x5cc85c);

    private final Client client;
    private final ItemManager itemManager;
    private final ForgeBingoConfig config;
    private final ForgeBingoPlugin plugin;
    /** item icons already asked of ItemManager (it loads them asynchronously; the image fills in when ready) */
    private final Map<Integer, BufferedImage> icons = new ConcurrentHashMap<>();

    private volatile boolean open;
    /** when the overlay last opened or closed, for the fade */
    private volatile long fadeAt;
    /** the tile whose card is open (kept when the overlay closes, so reopening returns to it) */
    private volatile String selectedTileId;
    /** when the card started popping open; 0 = show it fully open straight away */
    private volatile long cardAt;
    /** index (row * size + col) of the cell under the mouse, or -1 */
    private volatile int hover = -1;
    private volatile BoardLayout layout;

    @Inject
    BoardOverlay(Client client, ItemManager itemManager, ForgeBingoConfig config, ForgeBingoPlugin plugin)
    {
        this.client = client;
        this.itemManager = itemManager;
        this.config = config;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(Overlay.PRIORITY_HIGH);
    }

    // ---------- state (any thread) ----------

    boolean isOpen()
    {
        return open;
    }

    void toggle()
    {
        if (open) close(); else open();
    }

    void open()
    {
        if (open) return;
        hover = -1;
        // the remembered tile comes back without its pop (the board fade is enough)
        cardAt = 0;
        fadeAt = System.currentTimeMillis();
        open = true;
    }

    void close()
    {
        if (!open) return;
        fadeAt = System.currentTimeMillis();
        open = false;
        hover = -1;
    }

    /** Plugin shutdown: forget everything. */
    void reset()
    {
        open = false;
        fadeAt = 0;
        selectedTileId = null;
        hover = -1;
        icons.clear();
    }

    void hover(int x, int y)
    {
        BoardLayout l = layout();
        hover = l == null ? -1 : l.cellAt(x, y);
    }

    boolean cardOpen()
    {
        return selectedTile(plugin.currentBoard()) != null;
    }

    void closeCard()
    {
        selectedTileId = null;
    }

    /**
     * One click while the overlay is open (the caller swallows it either way): the card closes on any click, the
     * close button and anything off the board close the overlay, a tile opens its card, the rest of the board does
     * nothing.
     */
    void click(int x, int y)
    {
        ApiModels.BoardResponse board = plugin.currentBoard();
        BoardLayout l = layout();
        if (board == null || l == null)
        {
            Rectangle m = BoardLayout.message(client.getCanvasWidth(), client.getCanvasHeight());
            if (!m.contains(x, y)) close();
            return;
        }
        if (selectedTile(board) != null)
        {
            // on the card, on the board or off it: the card closes (the overlay stays); the X still closes everything
            if (!l.insideCard(x, y) && l.onCloseButton(x, y)) close();
            else closeCard();
            return;
        }
        if (l.onCloseButton(x, y))
        {
            close();
            return;
        }
        int idx = l.cellAt(x, y);
        if (idx >= 0)
        {
            ApiModels.Tile t = tileAt(board, idx / l.size, idx % l.size);
            if (t != null && t.id != null)
            {
                selectedTileId = t.id;
                cardAt = config.boardOverlayAnimations() ? System.currentTimeMillis() : 0;
            }
            return;
        }
        if (!l.insideBoard(x, y)) close();
    }

    /** The layout for the current canvas and board, shared with the mouse listener. */
    BoardLayout layout()
    {
        ApiModels.BoardResponse board = plugin.currentBoard();
        if (board == null) return null;
        int w = client.getCanvasWidth();
        int h = client.getCanvasHeight();
        int n = Math.max(1, board.size());
        BoardLayout l = layout;
        if (l == null || l.viewW != Math.max(1, w) || l.viewH != Math.max(1, h) || l.size != Math.min(20, n))
        {
            l = BoardLayout.compute(w, h, n);
            layout = l;
        }
        return l;
    }

    private ApiModels.Tile selectedTile(ApiModels.BoardResponse board)
    {
        String id = selectedTileId;
        if (id == null || board == null || board.tiles == null) return null;
        for (ApiModels.Tile t : board.tiles) if (t != null && id.equals(t.id)) return t;
        return null;
    }

    private static ApiModels.Tile tileAt(ApiModels.BoardResponse board, int row, int col)
    {
        if (board.tiles == null) return null;
        for (ApiModels.Tile t : board.tiles) if (t != null && t.row == row && t.col == col) return t;
        return null;
    }

    // ---------- drawing (client thread) ----------

    @Override
    public Dimension render(Graphics2D g)
    {
        long now = System.currentTimeMillis();
        boolean anim = config.boardOverlayAnimations();
        boolean isOpen = open;
        float alpha;
        if (isOpen)
        {
            alpha = anim ? (float) BoardLayout.easeOut((now - fadeAt) / (double) FADE_MS) : 1f;
        }
        else
        {
            if (!anim || now - fadeAt >= FADE_MS) return null;
            alpha = (float) (1 - BoardLayout.easeOut((now - fadeAt) / (double) FADE_MS));
        }
        if (alpha <= 0.01f) return null;

        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        int w = client.getCanvasWidth();
        int h = client.getCanvasHeight();
        Graphics2D g2 = (Graphics2D) g.create();
        try
        {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            // dim the game a little so the board reads, without hiding what is going on
            g2.setColor(new Color(0, 0, 0, 90));
            g2.fillRect(0, 0, w, h);

            ApiModels.BoardResponse board = plugin.currentBoard();
            BoardLayout l = layout();
            if (board == null || l == null)
            {
                drawNoBoard(g2, w, h);
                return null;
            }
            ApiModels.Tile selected = selectedTile(board);
            drawBoard(g2, board, l, selected == null && isOpen ? hover : -1, selected);
            if (selected != null)
            {
                double t = cardAt == 0 || !anim ? 1 : (now - cardAt) / (double) POP_MS;
                drawCard(g2, l, selected, BoardLayout.easeOut(t), alpha);
            }
        }
        finally
        {
            g2.dispose();
        }
        return null;
    }

    private void drawNoBoard(Graphics2D g, int w, int h)
    {
        Rectangle m = BoardLayout.message(w, h);
        stonePanel(g, m);
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        FontMetrics fm = g.getFontMetrics();
        String a = "No bingo board yet:";
        String b = "connect in the ForgeBingo panel";
        g.setColor(GOLD);
        g.drawString(a, m.x + (m.width - fm.stringWidth(a)) / 2, m.y + m.height / 2 - 4);
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        fm = g.getFontMetrics();
        g.setColor(Color.LIGHT_GRAY);
        g.drawString(b, m.x + (m.width - fm.stringWidth(b)) / 2, m.y + m.height / 2 + fm.getAscent());
    }

    private static void stonePanel(Graphics2D g, Rectangle r)
    {
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRoundRect(r.x + 4, r.y + 5, r.width, r.height, 16, 16);
        g.setPaint(new GradientPaint(r.x, r.y, STONE_TOP, r.x, r.y + r.height, STONE_BOTTOM));
        g.fillRoundRect(r.x, r.y, r.width, r.height, 16, 16);
        g.setStroke(new BasicStroke(2f));
        g.setColor(GOLD_DIM);
        g.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1, 16, 16);
        g.setColor(new Color(255, 255, 255, 18));
        g.drawRoundRect(r.x + 3, r.y + 3, r.width - 7, r.height - 7, 12, 12);
    }

    private void drawBoard(Graphics2D g, ApiModels.BoardResponse board, BoardLayout l, int hoverIdx, ApiModels.Tile selected)
    {
        Rectangle b = l.board();
        stonePanel(g, b);

        // header: "<team> - <bingo name>" and "X of Y tiles done"
        String name = board.event != null && board.event.name != null ? board.event.name : board.board != null && board.board.title != null ? board.board.title : "Bingo";
        String title = board.teamName == null ? name : board.teamName + " - " + name;
        int total = board.tiles == null ? 0 : board.tiles.size();
        int done = 0;
        if (board.tiles != null) for (ApiModels.Tile t : board.tiles) if (t != null && t.completed) done++;
        String sub = done + " of " + total + " tiles done" + (done == total && total > 0 ? "  -  BLACKOUT!" : "");
        int textW = b.width - 2 * BoardLayout.PAD - BoardLayout.CLOSE - 6;
        g.setFont(new Font("Serif", Font.BOLD, 17));
        String shownTitle = ellipsize(g.getFontMetrics(), title, textW);
        g.setColor(new Color(0, 0, 0, 200));
        g.drawString(shownTitle, b.x + BoardLayout.PAD + 1, b.y + BoardLayout.PAD + 15);
        g.setColor(GOLD);
        g.drawString(shownTitle, b.x + BoardLayout.PAD, b.y + BoardLayout.PAD + 14);
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.setColor(done == total && total > 0 ? DONE_TICK : Color.LIGHT_GRAY);
        g.drawString(ellipsize(g.getFontMetrics(), sub, textW), b.x + BoardLayout.PAD, b.y + BoardLayout.PAD + 30);

        // close button
        Rectangle c = l.closeButton();
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRoundRect(c.x, c.y, c.width, c.height, 6, 6);
        g.setColor(GOLD_DIM);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(c.x, c.y, c.width - 1, c.height - 1, 6, 6);
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(2f));
        g.drawLine(c.x + 6, c.y + 6, c.x + c.width - 7, c.y + c.height - 7);
        g.drawLine(c.x + c.width - 7, c.y + 6, c.x + 6, c.y + c.height - 7);

        ApiModels.Tile[][] cells = new ApiModels.Tile[l.size][l.size];
        if (board.tiles != null)
        {
            for (ApiModels.Tile t : board.tiles)
            {
                if (t != null && t.row >= 0 && t.col >= 0 && t.row < l.size && t.col < l.size) cells[t.row][t.col] = t;
            }
        }
        for (int r = 0; r < l.size; r++)
        {
            for (int col = 0; col < l.size; col++)
            {
                ApiModels.Tile t = cells[r][col];
                boolean hot = hoverIdx == r * l.size + col && t != null;
                drawCell(g, l.cell(r, col), t, hot, t != null && selected != null && t == selected);
            }
        }
    }

    private void drawCell(Graphics2D g, Rectangle r, ApiModels.Tile t, boolean hot, boolean selected)
    {
        int arc = Math.max(4, r.width / 5);
        TileInfo.State st = TileInfo.state(t);
        Color base = ForgeBingoPanel.stateColour(st);
        if (hot) base = base.brighter();
        g.setPaint(new GradientPaint(r.x, r.y, lighten(base, 18), r.x, r.y + r.height, base));
        g.fillRoundRect(r.x, r.y, r.width, r.height, arc, arc);
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(0, 0, 0, 140));
        g.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1, arc, arc);
        if (t == null) return;

        if (st == TileInfo.State.SECRET)
        {
            centredText(g, r, "?", new Font("Serif", Font.BOLD, Math.max(10, (int) (r.height * 0.62))), new Color(255, 215, 90));
        }
        else
        {
            BufferedImage img = t.iconItemId != null ? icon(t.iconItemId) : null;
            if (img != null && img.getWidth() > 1)
            {
                // keep the item sprite's shape, sized to ~72% of the cell
                double s = r.width * 0.72 / Math.max(img.getWidth(), img.getHeight());
                int iw = Math.max(1, (int) (img.getWidth() * s));
                int ih = Math.max(1, (int) (img.getHeight() * s));
                g.drawImage(img, r.x + (r.width - iw) / 2, r.y + (r.height - ih) / 2, iw, ih, null);
            }
            else
            {
                centredText(g, r, initials(t.title), new Font("SansSerif", Font.BOLD, Math.max(8, (int) (r.height * 0.32))), new Color(235, 225, 205));
            }
        }

        if (st == TileInfo.State.DONE) drawTick(g, r);
        else if (st != TileInfo.State.SECRET)
        {
            double p = TileInfo.progress(t);
            if (p > 0 && r.height >= 14)
            {
                int bh = Math.max(3, r.height / 10);
                int bx = r.x + 3;
                int bw = r.width - 6;
                int by = r.y + r.height - bh - 3;
                g.setColor(BAR_BG);
                g.fillRect(bx, by, bw, bh);
                g.setColor(BAR_FILL);
                g.fillRect(bx, by, (int) Math.round(bw * Math.min(1, p)), bh);
            }
        }

        if (hot || selected)
        {
            g.setStroke(new BasicStroke(2f));
            g.setColor(GOLD);
            g.drawRoundRect(r.x + 1, r.y + 1, r.width - 3, r.height - 3, arc, arc);
        }
    }

    private static void drawTick(Graphics2D g, Rectangle r)
    {
        int s = Math.max(8, r.width * 2 / 5);
        int x = r.x + r.width - s - 2;
        int y = r.y + 2;
        g.setColor(new Color(0, 0, 0, 150));
        g.fillOval(x, y, s, s);
        g.setColor(DONE_TICK);
        g.setStroke(new BasicStroke(Math.max(1.5f, s / 7f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawPolyline(new int[] {x + s / 4, x + s * 5 / 12, x + s * 3 / 4}, new int[] {y + s / 2, y + s * 2 / 3, y + s / 3}, 3);
    }

    private void drawCard(Graphics2D g0, BoardLayout l, ApiModels.Tile t, double ease, float boardAlpha)
    {
        Rectangle c = l.card();
        Graphics2D g = (Graphics2D) g0.create();
        try
        {
            // pop: scale 85% -> 100% about the card's centre while fading in
            double scale = 0.85 + 0.15 * ease;
            g.translate(c.getCenterX(), c.getCenterY());
            g.scale(scale, scale);
            g.translate(-c.getCenterX(), -c.getCenterY());
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, Math.min(1, ease * boardAlpha))));

            g.setColor(new Color(0, 0, 0, 140));
            g.fillRoundRect(c.x + 5, c.y + 6, c.width, c.height, 18, 18);
            g.setColor(CARD_BG);
            g.fillRoundRect(c.x, c.y, c.width, c.height, 18, 18);
            g.setStroke(new BasicStroke(2f));
            g.setColor(GOLD);
            g.drawRoundRect(c.x, c.y, c.width - 1, c.height - 1, 18, 18);
            // a strip in the tile's state colour along the top
            g.setColor(ForgeBingoPanel.stateColour(TileInfo.state(t)));
            g.fillRoundRect(c.x + 8, c.y + 8, c.width - 16, 6, 6, 6);

            int pad = 14;
            int x = c.x + pad;
            int maxW = c.width - 2 * pad;
            int bottom = c.y + c.height - 22;
            int y = c.y + 22;
            boolean secret = TileInfo.isSecret(t);

            Font titleFont = new Font("Serif", Font.BOLD, 18);
            g.setFont(titleFont);
            FontMetrics tm = g.getFontMetrics();
            g.setColor(GOLD);
            for (String line : wrap(tm, TileInfo.shownTitle(t), maxW, 2))
            {
                y += tm.getAscent();
                g.drawString(line, x, y);
                y += tm.getDescent();
            }

            Font body = new Font("SansSerif", Font.PLAIN, 12);
            Font small = new Font("SansSerif", Font.PLAIN, 11);
            g.setFont(body);
            FontMetrics bm = g.getFontMetrics();
            if (!secret)
            {
                y += 2 + bm.getAscent();
                g.setColor(Color.WHITE);
                g.drawString(TileInfo.worth(t) + " pts", x, y);
                y += bm.getDescent();
            }
            String state = ForgeBingoPanel.stateText(t);
            if (t.tasks != null && !t.completed && "any".equals(t.completionMode) && t.tasks.size() > 1) state += " (any one finishes it)";
            g.setColor(ForgeBingoPanel.stateTextColour(t));
            y = drawLines(g, wrap(bm, state, maxW, 3), x, y + 2);
            if (t.description != null && !t.description.isEmpty())
            {
                g.setFont(small);
                g.setColor(Color.GRAY);
                y = drawLines(g, wrap(g.getFontMetrics(), t.description, maxW, 2), x, y + 2);
            }
            y += 6;

            List<ApiModels.Task> tasks = t.tasks == null ? new ArrayList<>() : t.tasks;
            int shown = 0;
            for (ApiModels.Task k : tasks)
            {
                int need = bm.getHeight() * 2 + (k.requiredQty > 1 ? 14 : 0);
                if (y + need > bottom) break;
                g.setFont(body);
                g.setColor(k.completed ? DONE_TICK : Color.WHITE);
                y = drawLines(g, wrap(bm, (k.completed ? "[x] " : "[ ] ") + (k.description == null ? "" : k.description), maxW, 2), x, y);
                if (k.requiredQty > 1)
                {
                    y += 2;
                    int barH = 11;
                    g.setColor(BAR_BG);
                    g.fillRoundRect(x, y, maxW, barH, 4, 4);
                    double p = Math.min(1, Math.max(0, k.approvedQty / (double) k.requiredQty));
                    g.setColor(k.completed ? BAR_DONE : BAR_FILL);
                    g.fillRoundRect(x, y, (int) Math.round(maxW * p), barH, 4, 4);
                    g.setFont(new Font("SansSerif", Font.BOLD, 10));
                    FontMetrics pm = g.getFontMetrics();
                    String txt = k.approvedQty + " / " + k.requiredQty + (k.pendingQty > 0 ? "  (+" + k.pendingQty + " pending)" : "");
                    g.setColor(Color.WHITE);
                    g.drawString(txt, x + (maxW - pm.stringWidth(txt)) / 2, y + barH - 2);
                    y += barH;
                }
                else if (k.pendingQty > 0 && !k.completed)
                {
                    g.setFont(small);
                    g.setColor(new Color(0xffd83d));
                    y = drawLines(g, java.util.Collections.singletonList("Sent, waiting for a reviewer"), x, y);
                }
                g.setFont(small);
                g.setColor(Color.GRAY);
                y = drawLines(g, wrap(g.getFontMetrics(), ForgeBingoPanel.trackingText(k), maxW, 1), x, y + 1);
                y += 6;
                shown++;
            }
            g.setFont(small);
            FontMetrics sm = g.getFontMetrics();
            if (shown < tasks.size())
            {
                g.setColor(Color.LIGHT_GRAY);
                g.drawString(ellipsize(sm, "+" + (tasks.size() - shown) + " more: see the ForgeBingo panel", maxW), x, bottom + 2);
            }
            String hint = "Click to close";
            g.setColor(new Color(150, 140, 120));
            g.drawString(hint, c.x + c.width - pad - sm.stringWidth(hint), c.y + c.height - 8);
        }
        finally
        {
            g.dispose();
        }
    }

    private BufferedImage icon(int itemId)
    {
        BufferedImage img = icons.get(itemId);
        if (img == null && icons.size() < 400)
        {
            img = itemManager.getImage(itemId);
            if (img != null) icons.put(itemId, img);
        }
        return img;
    }

    // ---------- small helpers ----------

    private static int drawLines(Graphics2D g, List<String> lines, int x, int y)
    {
        FontMetrics fm = g.getFontMetrics();
        for (String line : lines)
        {
            y += fm.getAscent();
            g.drawString(line, x, y);
            y += fm.getDescent();
        }
        return y;
    }

    private static void centredText(Graphics2D g, Rectangle r, String s, Font f, Color c)
    {
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        int tx = r.x + (r.width - fm.stringWidth(s)) / 2;
        int ty = r.y + (r.height - fm.getHeight()) / 2 + fm.getAscent();
        g.setColor(new Color(0, 0, 0, 180));
        g.drawString(s, tx + 1, ty + 1);
        g.setColor(c);
        g.drawString(s, tx, ty);
    }

    /** "Dragon warhammer" -> "DW"; one word -> its first two letters. */
    static String initials(String title)
    {
        if (title == null) return "";
        String[] words = title.trim().split("[^A-Za-z0-9]+");
        StringBuilder sb = new StringBuilder();
        for (String w : words)
        {
            if (!w.isEmpty() && sb.length() < 2) sb.append(Character.toUpperCase(w.charAt(0)));
        }
        if (sb.length() == 1)
        {
            for (String w : words)
            {
                if (w.length() > 1)
                {
                    sb.append(Character.toLowerCase(w.charAt(1)));
                    break;
                }
            }
        }
        return sb.toString();
    }

    private static Color lighten(Color c, int by)
    {
        return new Color(Math.min(255, c.getRed() + by), Math.min(255, c.getGreen() + by), Math.min(255, c.getBlue() + by), c.getAlpha());
    }

    private static String ellipsize(FontMetrics fm, String s, int maxW)
    {
        if (s == null) return "";
        if (fm.stringWidth(s) <= maxW) return s;
        String dots = "...";
        int end = s.length();
        while (end > 0 && fm.stringWidth(s.substring(0, end) + dots) > maxW) end--;
        return s.substring(0, end) + dots;
    }

    /** Word-wraps to at most maxLines lines, the last one cut with "..." when the text runs on. */
    private static List<String> wrap(FontMetrics fm, String s, int maxW, int maxLines)
    {
        List<String> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;
        String[] words = s.split(" ");
        StringBuilder line = new StringBuilder();
        int i = 0;
        while (i < words.length)
        {
            String candidate = line.length() == 0 ? words[i] : line + " " + words[i];
            if (fm.stringWidth(candidate) <= maxW || line.length() == 0)
            {
                line.setLength(0);
                line.append(candidate);
                i++;
            }
            else
            {
                if (out.size() == maxLines - 1) break;
                out.add(line.toString());
                line.setLength(0);
            }
        }
        if (line.length() > 0)
        {
            StringBuilder rest = new StringBuilder(line);
            for (int j = i; j < words.length; j++) rest.append(' ').append(words[j]);
            out.add(ellipsize(fm, rest.toString(), maxW));
        }
        return out;
    }
}
