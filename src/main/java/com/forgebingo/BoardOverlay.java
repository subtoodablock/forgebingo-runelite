package com.forgebingo;

import com.forgebingo.core.ApiModels;
import com.forgebingo.core.BoardLayout;
import com.forgebingo.core.OpeningAnim;
import com.forgebingo.core.TileInfo;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The bingo board drawn over the game (owner request, 2026-11-07; polished 2026-11-10). Toggled by the hotkey or the
 * panel button; a clicked tile pops open into a card sized to its content; clicking off the board, Esc or taking a hit
 * closes it, and the open tile is remembered for next time. Hovering a tile fades in a highlight and a name label.
 * Text uses RuneLite's own pixel fonts with antialiasing off, so it stays sharp like the game's interfaces.
 * Drawn on the client thread; opened, closed and clicked from the AWT / Swing threads, so all shared state is
 * volatile and the board is the plugin's immutable-by-convention snapshot (never fetched here).
 */
public class BoardOverlay extends Overlay
{
    static final long FADE_MS = 160;
    static final long POP_MS = 200;
    /** hover highlight fade */
    static final long HOVER_MS = 110;
    /** opening cascade: each diagonal of cells starts this much after the previous one */
    /** the whole opening deal, first tile leaving the deck to the last one landing */
    static final long DEAL_MS = 3000;
    /** one tile's flight from the deck to its slot */
    static final long FLIGHT_MS = 420;
    /** the last tile lands by here, leaving room for the rarity effects inside DEAL_MS */
    static final long LAND_MS = 2550;
    /** sparkles / glow after a tile lands (Rare and up) */
    static final long LANDING_FX_MS = 420;

    private static final Color GOLD = new Color(0xffb83f);
    private static final Color GOLD_DIM = new Color(0x7a5c22);
    private static final Color GOLD_EDGE = new Color(0x5a4420);
    private static final Color STONE_TOP = new Color(0x2e2925);
    private static final Color STONE_BOTTOM = new Color(0x1b1815);
    private static final Color CARD_TOP = new Color(0x2a2521);
    private static final Color CARD_BOTTOM = new Color(0x1e1a17);
    private static final Color TEXT = new Color(0xe8e0d0);
    private static final Color MUTED = new Color(0x9a9083);
    private static final Color SHADOW = new Color(0, 0, 0, 220);
    private static final Color DONE_TICK = new Color(0x6ee06e);
    private static final Color BAR_BG = new Color(0, 0, 0, 160);
    private static final Color BAR_FILL = new Color(0xff981f);
    private static final Color BAR_DONE = new Color(0x4fbf4f);
    private static final Color PENDING = new Color(0xffd83d);

    private final Client client;
    private final ItemManager itemManager;
    private final ForgeBingoConfig config;
    private final ForgeBingoPlugin plugin;
    /** item icons already asked of ItemManager (it loads them asynchronously; the image fills in when ready) */
    private final Map<Integer, BufferedImage> icons = new ConcurrentHashMap<>();

    private volatile boolean open;
    /** when the overlay last opened or closed, for the fade */
    private volatile long fadeAt;
    /** play the tile cascade for this opening (not when reopening onto a remembered card) */
    private volatile boolean cascade;
    /** the tile whose card is open (kept when the overlay closes, so reopening returns to it) */
    private volatile String selectedTileId;
    /** when the card started popping open; 0 = show it fully open straight away */
    private volatile long cardAt;
    /** index (row * size + col) of the cell under the mouse, or -1 */
    private volatile int hover = -1;
    /** the cell the mouse just left (its highlight fades out) and when the hover last changed */
    private volatile int hoverPrev = -1;
    private volatile long hoverAt;
    private volatile BoardLayout layout;

    @Inject
    BoardOverlay(Client client, ItemManager itemManager, ForgeBingoConfig config, ForgeBingoPlugin plugin)
    {
        this.client = client;
        this.itemManager = itemManager;
        this.config = config;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        // above every other plugin's overlays and info boxes (owner, 2026-11-10)
        setLayer(OverlayLayer.ALWAYS_ON_TOP);
        setPriority(Overlay.PRIORITY_HIGHEST);
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
        hoverPrev = -1;
        // the remembered tile comes back without its pop (the board fade is enough)
        cardAt = 0;
        cascade = selectedTileId == null;
        fadeAt = System.currentTimeMillis();
        // a new opening animation every time, rolled by rarity (see OpeningAnim)
        openingSeed = fadeAt;
        opening = OpeningAnim.roll(new java.util.Random(openingSeed));
        openingRanks = null;
        open = true;
    }

    /** this opening's animation, its random seed and the arrival order (built on first draw for the board's size) */
    private volatile OpeningAnim.Spec opening = OpeningAnim.ALL.get(0);
    private volatile long openingSeed;
    private volatile int[] openingRanks;

    void close()
    {
        if (!open) return;
        fadeAt = System.currentTimeMillis();
        open = false;
        hover = -1;
        hoverPrev = -1;
    }

    /** Plugin shutdown: forget everything. */
    void reset()
    {
        open = false;
        fadeAt = 0;
        selectedTileId = null;
        hover = -1;
        hoverPrev = -1;
        icons.clear();
    }

    void hover(int x, int y)
    {
        BoardLayout l = layout();
        int next = l == null ? -1 : l.cellAt(x, y);
        if (next != hover)
        {
            hoverPrev = hover;
            hoverAt = System.currentTimeMillis();
            hover = next;
        }
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

        int w = client.getCanvasWidth();
        int h = client.getCanvasHeight();
        Graphics2D g2 = (Graphics2D) g.create();
        try
        {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            // pixel fonts: no smoothing, so every letter lands on whole pixels like the game's own text
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            // dim the game a little so the board reads, without hiding what is going on
            g2.setColor(new Color(0, 0, 0, 100));
            g2.fillRect(0, 0, w, h);

            ApiModels.BoardResponse board = plugin.currentBoard();
            BoardLayout l = layout();
            if (board == null || l == null)
            {
                drawNoBoard(g2, w, h);
                return null;
            }
            ApiModels.Tile selected = selectedTile(board);
            long sinceOpen = isOpen && anim && cascade ? now - fadeAt : Long.MAX_VALUE;
            drawBoard(g2, board, l, selected == null && isOpen, selected, now, anim, sinceOpen, alpha);
            if (selected != null)
            {
                double t = cardAt == 0 || !anim ? 1 : (now - cardAt) / (double) POP_MS;
                drawCard(g2, l, selected, t, alpha);
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
        panel(g, m, 14);
        text(g, FontManager.getRunescapeBoldFont(), "No bingo board yet", GOLD, m.x + m.width / 2, m.y + m.height / 2 - 2, true);
        text(g, FontManager.getRunescapeSmallFont(), "Connect in the ForgeBingo side panel", MUTED, m.x + m.width / 2, m.y + m.height / 2 + 14, true);
    }

    /** Dark stone panel with a soft drop shadow, a thin gold rim and a faint inner bevel. */
    private static void panel(Graphics2D g, Rectangle r, int arc)
    {
        for (int i = 3; i >= 1; i--)
        {
            g.setColor(new Color(0, 0, 0, 40));
            g.fillRoundRect(r.x - i + 2, r.y - i + 4, r.width + 2 * i, r.height + 2 * i, arc + 2 * i, arc + 2 * i);
        }
        g.setPaint(new GradientPaint(r.x, r.y, STONE_TOP, r.x, r.y + r.height, STONE_BOTTOM));
        g.fillRoundRect(r.x, r.y, r.width, r.height, arc, arc);
        g.setStroke(new BasicStroke(1f));
        g.setColor(Color.BLACK);
        g.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1, arc, arc);
        g.setColor(GOLD_DIM);
        g.drawRoundRect(r.x + 1, r.y + 1, r.width - 3, r.height - 3, arc - 2, arc - 2);
        g.setColor(new Color(255, 255, 255, 14));
        g.drawLine(r.x + arc / 2, r.y + 2, r.x + r.width - arc / 2, r.y + 2);
    }

    private void drawBoard(Graphics2D g, ApiModels.BoardResponse board, BoardLayout l, boolean hoverable, ApiModels.Tile selected,
        long now, boolean anim, long sinceOpen, float boardAlpha)
    {
        Rectangle b = l.board();
        panel(g, b, 12);

        // header: team (gold) + bingo name (muted), the count on the right, a thin progress line underneath
        String name = board.event != null && board.event.name != null ? board.event.name : board.board != null && board.board.title != null ? board.board.title : "Bingo";
        if (board.event != null && !board.event.active) name += " (not running)";
        int total = board.tiles == null ? 0 : board.tiles.size();
        int done = 0;
        if (board.tiles != null) for (ApiModels.Tile t : board.tiles) if (t != null && t.completed) done++;
        boolean blackout = done == total && total > 0;

        int x = b.x + BoardLayout.PAD;
        int right = l.closeButton().x - 8;
        Font bold = FontManager.getRunescapeBoldFont();
        Font small = FontManager.getRunescapeSmallFont();
        String count = blackout ? "BLACKOUT!" : done + " / " + total;
        g.setFont(bold);
        int countW = g.getFontMetrics().stringWidth(count);
        String team = board.teamName == null ? name : board.teamName;
        text(g, bold, ellipsize(g.getFontMetrics(), team, right - x - countW - 10), GOLD, x, b.y + BoardLayout.PAD + 12, false);
        text(g, bold, count, blackout ? DONE_TICK : TEXT, right - countW, b.y + BoardLayout.PAD + 12, false);
        if (board.teamName != null)
        {
            g.setFont(small);
            text(g, small, ellipsize(g.getFontMetrics(), name, right - x), MUTED, x, b.y + BoardLayout.PAD + 25, false);
        }
        // progress line across the header
        int lineY = b.y + BoardLayout.HEADER + BoardLayout.PAD - 9;
        int lineW = b.width - 2 * BoardLayout.PAD;
        g.setColor(BAR_BG);
        g.fillRoundRect(x, lineY, lineW, 4, 4, 4);
        double p = total == 0 ? 0 : done / (double) total;
        int fillW = (int) Math.round(lineW * p);
        if (fillW > 0)
        {
            g.setPaint(new GradientPaint(x, lineY, blackout ? BAR_DONE.brighter() : GOLD, x, lineY + 4, blackout ? BAR_DONE : BAR_FILL));
            g.fillRoundRect(x, lineY, Math.max(4, fillW), 4, 4, 4);
        }

        drawCloseButton(g, l.closeButton(), false);

        ApiModels.Tile[][] cells = new ApiModels.Tile[l.size][l.size];
        if (board.tiles != null)
        {
            for (ApiModels.Tile t : board.tiles)
            {
                if (t != null && t.row >= 0 && t.col >= 0 && t.row < l.size && t.col < l.size) cells[t.row][t.col] = t;
            }
        }
        int hov = hoverable ? hover : -1;
        int prev = hoverable ? hoverPrev : -1;
        double hoverIn = anim ? BoardLayout.easeOut((now - hoverAt) / (double) HOVER_MS) : 1;
        Composite base = g.getComposite();
        // Opening animation (owner, 2026-11-12): one of 109, rolled by rarity on every open (OpeningAnim). Tiles
        // arrive in the animation's order and way; the last one lands by LAND_MS and the rarity effects finish by
        // DEAL_MS (3 s). Rare: landing sparkles. Epic: + trails and glow flashes. Legendary: + light vortex and burst.
        int n = l.size * l.size;
        boolean dealing = sinceOpen != Long.MAX_VALUE;
        OpeningAnim.Spec spec = opening;
        OpeningAnim.Rarity rarity = spec.rarity;
        int[] ranks = openingRanks;
        if (dealing && (ranks == null || ranks.length != n)) openingRanks = ranks = OpeningAnim.ranks(spec.order, l.size, openingSeed);
        double step = n > 1 ? (LAND_MS - FLIGHT_MS) / (double) (n - 1) : 0;
        Point2D deck = new Point2D.Double(b.getCenterX(), b.y + b.height - BoardLayout.PAD - l.cell / 2.0);
        if (dealing && rarity == OpeningAnim.Rarity.LEGENDARY) drawVortex(g, b, sinceOpen, boardAlpha);
        java.util.List<int[]> flying = new java.util.ArrayList<>();
        int undealt = 0;
        for (int r = 0; r < l.size; r++)
        {
            for (int col = 0; col < l.size; col++)
            {
                ApiModels.Tile t = cells[r][col];
                int idx = r * l.size + col;
                double glow = t == null ? 0 : idx == hov ? hoverIn : idx == prev ? 1 - hoverIn : 0;
                if (dealing)
                {
                    double dealt = (sinceOpen - ranks[idx] * step) / FLIGHT_MS;
                    if (dealt <= 0) { undealt++; continue; }
                    if (dealt < 1) { flying.add(new int[] {r, col}); continue; }
                }
                drawCell(g, l.cell(r, col), t, glow, t != null && selected != null && t == selected);
                if (dealing && rarity != OpeningAnim.Rarity.COMMON)
                {
                    long sinceLand = sinceOpen - (long) (ranks[idx] * step + FLIGHT_MS);
                    if (sinceLand >= 0 && sinceLand < LANDING_FX_MS) drawLanding(g, l.cell(r, col), idx, sinceLand, rarity, boardAlpha);
                }
            }
        }
        if (dealing)
        {
            // the deck (Deal entrance only): a small stack that shrinks as the tiles leave it
            if (undealt > 0 && spec.entrance == OpeningAnim.Entrance.DEAL) drawDeck(g, deck, l.cell, undealt);
            // tiles on their way in, on top; Epic and Legendary leave a trail of fading copies behind them
            boolean trails = rarity == OpeningAnim.Rarity.EPIC || rarity == OpeningAnim.Rarity.LEGENDARY;
            for (int[] rc : flying)
            {
                int idx = rc[0] * l.size + rc[1];
                double fp = Math.min(1, (sinceOpen - ranks[idx] * step) / FLIGHT_MS);
                if (trails)
                {
                    for (int k = 2; k >= 1; k--) drawFlying(g, l, b, deck, cells, rc, idx, spec, fp - 0.11 * k, boardAlpha * (k == 1 ? 0.3f : 0.14f), rarity);
                }
                drawFlying(g, l, b, deck, cells, rc, idx, spec, fp, boardAlpha, null);
            }
            g.setComposite(base);
            if (rarity == OpeningAnim.Rarity.LEGENDARY) drawBurst(g, b, sinceOpen, boardAlpha);
            g.setComposite(base);
            if (sinceOpen > DEAL_MS) cascade = false;
        }

        // name label over the hovered tile
        if (hov >= 0 && selected == null)
        {
            ApiModels.Tile t = cells[hov / l.size][hov % l.size];
            if (t != null) drawHoverLabel(g, l, l.cell(hov / l.size, hov % l.size), t, hoverIn, boardAlpha);
        }
    }

    /** One tile on its way in, posed by the opening animation. tint != null draws it as a coloured trail ghost. */
    private void drawFlying(Graphics2D g, BoardLayout l, Rectangle b, Point2D deck, ApiModels.Tile[][] cells, int[] rc, int idx,
                            OpeningAnim.Spec spec, double fp, float alpha, OpeningAnim.Rarity tint)
    {
        if (fp <= 0) return;
        Rectangle target = l.cell(rc[0], rc[1]);
        double[] p = OpeningAnim.pose(spec.entrance, fp, idx, target.getCenterX(), target.getCenterY(), l.cell,
            b.x, b.y, b.width, b.height, deck.getX(), deck.getY());
        Graphics2D gc = (Graphics2D) g.create();
        try
        {
            gc.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, Math.min(1, alpha * p[5]))));
            gc.translate(p[0], p[1]);
            gc.rotate(p[4]);
            gc.scale(p[2], p[3]);
            gc.translate(-target.getCenterX(), -target.getCenterY());
            if (tint == null) drawCell(gc, target, cells[rc[0]][rc[1]], 0, false);
            else
            {
                int arc = Math.max(4, target.width / 5);
                gc.setColor(tint.colour);
                gc.fillRoundRect(target.x, target.y, target.width, target.height, arc, arc);
            }
        }
        finally
        {
            gc.dispose();
        }
    }

    /** Rare and up: a few sparkles burst from a tile as it lands; Epic and up also flash a glow in the rarity colour. */
    private static void drawLanding(Graphics2D g, Rectangle cell, int idx, long sinceLand, OpeningAnim.Rarity rarity, float alpha)
    {
        double t = sinceLand / (double) LANDING_FX_MS, fade = 1 - t;
        Graphics2D gc = (Graphics2D) g.create();
        try
        {
            gc.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color c = rarity.colour;
            if (rarity != OpeningAnim.Rarity.RARE)
            {
                int grow = (int) (t * cell.width * 0.35);
                gc.setStroke(new BasicStroke(2f));
                gc.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (200 * fade * alpha)));
                int arc = Math.max(4, cell.width / 5) + grow;
                gc.drawRoundRect(cell.x - grow / 2, cell.y - grow / 2, cell.width + grow, cell.height + grow, arc, arc);
            }
            int count = rarity == OpeningAnim.Rarity.RARE ? 5 : rarity == OpeningAnim.Rarity.EPIC ? 7 : 10;
            for (int k = 0; k < count; k++)
            {
                long h = OpeningAnim.hash(idx * 31L + k);
                double ang = (h % 628) / 100.0, dist = cell.width * (0.35 + 0.5 * t) * (0.7 + (h >>> 10) % 30 / 100.0);
                int s = Math.max(2, (int) (cell.width * 0.09 * fade)) + 1;
                double x = cell.getCenterX() + Math.cos(ang) * dist, y = cell.getCenterY() + Math.sin(ang) * dist;
                Color sc = k % 3 == 0 ? Color.WHITE : rarity == OpeningAnim.Rarity.LEGENDARY ? GOLD : c;
                gc.setColor(new Color(sc.getRed(), sc.getGreen(), sc.getBlue(), (int) (230 * fade * alpha)));
                gc.fillPolygon(new int[] {(int) x, (int) x + s / 2, (int) x, (int) x - s / 2}, new int[] {(int) y - s, (int) y, (int) y + s, (int) y}, 4);
            }
        }
        finally
        {
            gc.dispose();
        }
    }

    /** Legendary: slow rotating rays of light behind the board while the tiles arrive. */
    private static void drawVortex(Graphics2D g, Rectangle b, long since, float alpha)
    {
        double life = Math.min(1, since / 400.0) * Math.min(1, Math.max(0, (DEAL_MS - since) / 500.0));
        if (life <= 0) return;
        Graphics2D gc = (Graphics2D) g.create();
        try
        {
            gc.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            gc.clip(new java.awt.geom.RoundRectangle2D.Double(b.x, b.y, b.width, b.height, 14, 14));
            double cx = b.getCenterX(), cy = b.getCenterY(), r = Math.hypot(b.width, b.height);
            gc.translate(cx, cy);
            gc.rotate(since / 900.0);
            for (int k = 0; k < 12; k++)
            {
                gc.rotate(Math.PI / 6);
                gc.setPaint(new GradientPaint(0, 0, new Color(255, 200, 90, (int) (90 * life * alpha)), (float) r * 0.6f, 0, new Color(255, 160, 40, 0)));
                gc.fill(new java.awt.geom.Arc2D.Double(-r, -r, 2 * r, 2 * r, -6, 12, java.awt.geom.Arc2D.PIE));
            }
        }
        finally
        {
            gc.dispose();
        }
    }

    /** Legendary: a golden ring and spray of sparks from the middle as the last tiles land. */
    private static void drawBurst(Graphics2D g, Rectangle b, long since, float alpha)
    {
        double t = (since - (LAND_MS - 150)) / (double) (DEAL_MS - LAND_MS + 150);
        if (t <= 0 || t >= 1) return;
        Graphics2D gc = (Graphics2D) g.create();
        try
        {
            gc.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double cx = b.getCenterX(), cy = b.getCenterY(), reach = Math.max(b.width, b.height) * 0.6 * OpeningAnim.easeOut(t);
            int a = (int) (230 * (1 - t) * alpha);
            gc.setStroke(new BasicStroke(3f));
            gc.setColor(new Color(255, 214, 102, a));
            gc.draw(new java.awt.geom.Ellipse2D.Double(cx - reach, cy - reach, reach * 2, reach * 2));
            for (int k = 0; k < 36; k++)
            {
                long h = OpeningAnim.hash(9000 + k);
                double ang = k * Math.PI * 2 / 36 + (h % 100) / 400.0, d = reach * (0.6 + (h >>> 8) % 40 / 100.0);
                int s = 3 + (int) (h % 3);
                gc.setColor(k % 4 == 0 ? new Color(255, 255, 255, a) : new Color(255, 184, 63, a));
                gc.fill(new java.awt.geom.Ellipse2D.Double(cx + Math.cos(ang) * d - s / 2.0, cy + Math.sin(ang) * d - s / 2.0, s, s));
            }
        }
        finally
        {
            gc.dispose();
        }
    }

    /** The deck the opening deal comes from: a few offset card backs, thinner as fewer tiles are left. */
    private static void drawDeck(Graphics2D g, Point2D at, int cell, int left)
    {
        int w = Math.max(10, (int) (cell * 0.8));
        int layers = Math.max(1, Math.min(4, (left + 9) / 10));
        int arc = Math.max(4, w / 5);
        for (int i = layers - 1; i >= 0; i--)
        {
            int x = (int) at.getX() - w / 2 + i * 2;
            int y = (int) at.getY() - w / 2 - i * 2;
            g.setPaint(new GradientPaint(x, y, new Color(0x3a2f22), x, y + w, new Color(0x231c15)));
            g.fillRoundRect(x, y, w, w, arc, arc);
            g.setStroke(new BasicStroke(1f));
            g.setColor(GOLD_DIM);
            g.drawRoundRect(x, y, w - 1, w - 1, arc, arc);
        }
        g.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), 160));
        int s = Math.max(4, w / 4);
        g.fillOval((int) at.getX() - s / 2, (int) at.getY() - s / 2 - (layers - 1) * 2, s, s);
    }

    private static void drawCloseButton(Graphics2D g, Rectangle c, boolean hot)
    {
        g.setColor(new Color(0, 0, 0, 130));
        g.fillRoundRect(c.x, c.y, c.width, c.height, 6, 6);
        g.setStroke(new BasicStroke(1f));
        g.setColor(GOLD_DIM);
        g.drawRoundRect(c.x, c.y, c.width - 1, c.height - 1, 6, 6);
        g.setColor(hot ? Color.WHITE : GOLD);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int in = Math.max(5, c.width / 3);
        g.drawLine(c.x + in, c.y + in, c.x + c.width - in - 1, c.y + c.height - in - 1);
        g.drawLine(c.x + c.width - in - 1, c.y + in, c.x + in, c.y + c.height - in - 1);
    }

    private void drawCell(Graphics2D g, Rectangle r, ApiModels.Tile t, double glow, boolean selected)
    {
        int arc = Math.max(4, Math.min(10, r.width / 5));
        TileInfo.State st = TileInfo.state(t);
        Color base = ForgeBingoPanel.stateColour(st);
        if (glow > 0) base = mix(base, lighten(base, 34), glow);
        g.setPaint(new GradientPaint(r.x, r.y, lighten(base, 16), r.x, r.y + r.height, darken(base, 6)));
        g.fillRoundRect(r.x, r.y, r.width, r.height, arc, arc);
        // crisp edge: dark outline + a 1px top highlight
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(0, 0, 0, 170));
        g.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1, arc, arc);
        g.setColor(new Color(255, 255, 255, 22));
        g.drawLine(r.x + arc / 2, r.y + 1, r.x + r.width - arc / 2 - 1, r.y + 1);
        if (t == null) return;

        if (st == TileInfo.State.SECRET)
        {
            Font f = r.height >= 26 ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeSmallFont();
            text(g, f, "?", new Color(255, 215, 90), r.x + r.width / 2, r.y + r.height / 2 + g.getFontMetrics(f).getAscent() / 2 - 1, true);
        }
        else
        {
            BufferedImage img = tilePicture(t);
            if (img != null && img.getWidth() > 1)
            {
                // keep the item sprite's shape; draw at whole-pixel steps when it fits, so it stays sharp
                double fit = r.width * 0.74 / Math.max(img.getWidth(), img.getHeight());
                double s = fit >= 1 ? Math.floor(fit * 2) / 2 : fit;
                int iw = Math.max(1, (int) Math.round(img.getWidth() * s));
                int ih = Math.max(1, (int) Math.round(img.getHeight() * s));
                Object interp = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
                if (s >= 1) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g.drawImage(img, r.x + (r.width - iw) / 2, r.y + (r.height - ih) / 2, iw, ih, null);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interp);
            }
            else
            {
                Font f = r.height >= 26 ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeSmallFont();
                FontMetrics fm = g.getFontMetrics(f);
                text(g, f, initials(t.title), new Color(235, 225, 205), r.x + r.width / 2, r.y + (r.height + fm.getAscent()) / 2 - 2, true);
            }
        }

        if (st == TileInfo.State.DONE) drawTick(g, r);
        else if (st != TileInfo.State.SECRET)
        {
            double p = TileInfo.progress(t);
            if (p > 0 && r.height >= 14)
            {
                int bh = Math.max(3, r.height / 11);
                int bx = r.x + 4;
                int bw = r.width - 8;
                int by = r.y + r.height - bh - 4;
                g.setColor(BAR_BG);
                g.fillRoundRect(bx, by, bw, bh, bh, bh);
                g.setColor(BAR_FILL);
                g.fillRoundRect(bx, by, Math.max(bh, (int) Math.round(bw * Math.min(1, p))), bh, bh, bh);
            }
        }

        // someone on the team is going for this tile: a small orange flag in the top-left corner
        if (st != TileInfo.State.DONE && t.workingOn != null && !t.workingOn.isEmpty() && r.width >= 14)
        {
            int s = Math.max(5, Math.min(9, r.width / 5));
            int fx = r.x + 3;
            int fy = r.y + 3;
            g.setColor(new Color(0, 0, 0, 160));
            g.fillOval(fx - 1, fy - 1, s + 2, s + 2);
            g.setColor(BAR_FILL);
            g.fillOval(fx, fy, s, s);
        }

        if (glow > 0 || selected)
        {
            float a = selected ? 1f : (float) glow;
            g.setStroke(new BasicStroke(1.5f));
            g.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), Math.round(255 * a)));
            g.drawRoundRect(r.x + 1, r.y + 1, r.width - 3, r.height - 3, arc, arc);
        }
    }

    private static void drawTick(Graphics2D g, Rectangle r)
    {
        int s = Math.max(9, Math.min(16, r.width * 2 / 5));
        int x = r.x + r.width - s - 2;
        int y = r.y + 2;
        g.setColor(new Color(0x1d4a1d));
        g.fillOval(x, y, s, s);
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(0, 0, 0, 160));
        g.drawOval(x, y, s - 1, s - 1);
        g.setColor(DONE_TICK);
        g.setStroke(new BasicStroke(Math.max(1.5f, s / 7f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawPolyline(new int[] {x + s * 3 / 11, x + s * 5 / 11, x + s * 8 / 11}, new int[] {y + s / 2, y + s * 7 / 10, y + s * 3 / 10}, 3);
    }

    /** A small name plate above the hovered tile (below it on the top row), kept inside the board. */
    private void drawHoverLabel(Graphics2D g0, BoardLayout l, Rectangle cell, ApiModels.Tile t, double in, float boardAlpha)
    {
        if (in <= 0.02) return;
        Graphics2D g = (Graphics2D) g0.create();
        try
        {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.min(1, in) * boardAlpha));
            Font bold = FontManager.getRunescapeBoldFont();
            Font small = FontManager.getRunescapeSmallFont();
            FontMetrics bm = g.getFontMetrics(bold);
            FontMetrics sm = g.getFontMetrics(small);
            Rectangle b = l.board();
            int maxW = Math.min(260, b.width - 16);
            String title = ellipsize(bm, TileInfo.shownTitle(t), maxW - 16);
            String sub = TileInfo.isSecret(t) ? "Secret tile" : ForgeBingoPanel.stateText(t) + "  -  " + points(TileInfo.worth(t));
            sub = ellipsize(sm, sub, maxW - 16);
            int w = Math.max(bm.stringWidth(title), sm.stringWidth(sub)) + 16;
            int h = 36;
            int x = Math.max(b.x + 6, Math.min(b.x + b.width - 6 - w, cell.x + cell.width / 2 - w / 2));
            int lift = (int) Math.round((1 - in) * 4);
            int y = cell.y - h - 6 + lift;
            if (y < b.y + 4) y = cell.y + cell.height + 6 - lift;
            g.setColor(new Color(0, 0, 0, 110));
            g.fillRoundRect(x + 1, y + 2, w, h, 8, 8);
            g.setPaint(new GradientPaint(x, y, CARD_TOP, x, y + h, CARD_BOTTOM));
            g.fillRoundRect(x, y, w, h, 8, 8);
            g.setStroke(new BasicStroke(1f));
            g.setColor(GOLD_EDGE);
            g.drawRoundRect(x, y, w - 1, h - 1, 8, 8);
            text(g, bold, title, GOLD, x + 8, y + 15, false);
            text(g, small, sub, ForgeBingoPanel.stateTextColour(t), x + 8, y + 29, false);
        }
        finally
        {
            g.dispose();
        }
    }

    /** The tile card: sized to what it shows, centred on the board, popping in with a slight overshoot. */
    private void drawCard(Graphics2D g0, BoardLayout l, ApiModels.Tile t, double progress, float boardAlpha)
    {
        // the card may use the whole board (less a margin) so every task fits; it is only as tall as its content
        Rectangle b = l.board();
        Rectangle area = new Rectangle(b.x + 10, b.y + 10, b.width - 20, b.height - 20);
        int w = Math.min(area.width, 320);
        int room = area.height - 2 * CARD_PAD;
        boolean compact = cardContent(g0, t, 0, 0, w - 2 * CARD_PAD, Integer.MAX_VALUE / 4, true, false) > room;
        int contentH = cardContent(g0, t, 0, 0, w - 2 * CARD_PAD, room, true, compact);
        int h = Math.min(area.height, contentH + 2 * CARD_PAD);
        int cx = b.x + b.width / 2;
        int cy = b.y + b.height / 2;
        Rectangle c = new Rectangle(cx - w / 2, cy - h / 2, w, h);
        c.y = Math.max(area.y, Math.min(area.y + area.height - h, c.y));

        double ease = easeOutBack(Math.max(0, Math.min(1, progress)));
        double fade = BoardLayout.easeOut(progress);
        Graphics2D g = (Graphics2D) g0.create();
        try
        {
            // the board behind dims while a card is open
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (fade * boardAlpha)));
            g.setColor(new Color(0, 0, 0, 120));
            g.fillRoundRect(b.x, b.y, b.width, b.height, 12, 12);

            double scale = 0.9 + 0.1 * ease;
            g.translate(c.getCenterX(), c.getCenterY());
            g.scale(scale, scale);
            g.translate(-c.getCenterX(), -c.getCenterY());

            for (int i = 4; i >= 1; i--)
            {
                g.setColor(new Color(0, 0, 0, 35));
                g.fillRoundRect(c.x - i + 2, c.y - i + 5, c.width + 2 * i, c.height + 2 * i, 16 + 2 * i, 16 + 2 * i);
            }
            g.setPaint(new GradientPaint(c.x, c.y, CARD_TOP, c.x, c.y + c.height, CARD_BOTTOM));
            g.fillRoundRect(c.x, c.y, c.width, c.height, 14, 14);
            g.setStroke(new BasicStroke(1f));
            g.setColor(Color.BLACK);
            g.drawRoundRect(c.x, c.y, c.width - 1, c.height - 1, 14, 14);
            g.setColor(GOLD_DIM);
            g.drawRoundRect(c.x + 1, c.y + 1, c.width - 3, c.height - 3, 12, 12);
            // the tile's state colour as a slim band across the top
            Color band = ForgeBingoPanel.stateColour(TileInfo.state(t));
            g.setColor(lighten(band, 30));
            g.fillRoundRect(c.x + 10, c.y + 7, c.width - 20, 3, 3, 3);

            cardContent(g, t, c.x + CARD_PAD, c.y + CARD_PAD, c.width - 2 * CARD_PAD, c.height - 2 * CARD_PAD, false, compact);
        }
        finally
        {
            g.dispose();
        }
    }

    private static final int CARD_PAD = 14;

    /**
     * Lays out (dry = true: only measures) the card's text: title, points + state, description, then each task with
     * its checkbox, progress bar and how it's tracked. compact = leave out the "how it's tracked" lines and keep each
     * task to one line, so a tile with many tasks still shows all of them. Returns the height used.
     */
    private int cardContent(Graphics2D g, ApiModels.Tile t, int x, int top, int maxW, int maxH, boolean dry, boolean compact)
    {
        Font bold = FontManager.getRunescapeBoldFont();
        Font body = FontManager.getRunescapeFont();
        Font small = FontManager.getRunescapeSmallFont();
        FontMetrics bm = g.getFontMetrics(bold);
        FontMetrics tm = g.getFontMetrics(body);
        FontMetrics sm = g.getFontMetrics(small);
        int bottom = top + maxH;
        int y = top + 2;
        boolean secret = TileInfo.isSecret(t);

        for (String line : wrap(bm, TileInfo.shownTitle(t), maxW - (secret ? 0 : 50), 2))
        {
            y += bm.getAscent();
            if (!dry) text(g, bold, line, GOLD, x, y, false);
            y += bm.getDescent() + 1;
        }
        if (!secret && !dry)
        {
            // points as a small pill on the right of the first title line
            String pts = points(TileInfo.worth(t));
            int pw = sm.stringWidth(pts) + 10;
            int px = x + maxW - pw;
            int py = top + 2;
            g.setColor(new Color(0, 0, 0, 120));
            g.fillRoundRect(px, py, pw, 15, 8, 8);
            g.setStroke(new BasicStroke(1f));
            g.setColor(GOLD_EDGE);
            g.drawRoundRect(px, py, pw - 1, 14, 8, 8);
            text(g, small, pts, GOLD, px + 5, py + 11, false);
        }

        String state = ForgeBingoPanel.stateText(t);
        if (t.tasks != null && !t.completed && "any".equals(t.completionMode) && t.tasks.size() > 1) state += " (any one finishes it)";
        y = lines(g, small, wrap(sm, state, maxW, 2), ForgeBingoPanel.stateTextColour(t), x, y + 2, dry);
        if (t.description != null && !t.description.isEmpty())
        {
            y = lines(g, small, wrap(sm, t.description, maxW, 3), MUTED, x, y + 3, dry);
        }
        if (!t.completed && t.workingOn != null && !t.workingOn.isEmpty())
        {
            List<String> who = new ArrayList<>();
            for (ApiModels.WorkingOn w : t.workingOn) if (w.me) who.add(0, "You"); else who.add(w.name);
            y = lines(g, small, wrap(sm, "Working on it: " + String.join(", ", who), maxW, 2), BAR_FILL, x, y + 3, dry);
        }

        List<ApiModels.Task> tasks = t.tasks == null ? new ArrayList<>() : t.tasks;
        if (!tasks.isEmpty())
        {
            y += 7;
            if (!dry)
            {
                g.setColor(new Color(255, 255, 255, 18));
                g.drawLine(x, y, x + maxW, y);
                g.setColor(new Color(0, 0, 0, 120));
                g.drawLine(x, y + 1, x + maxW, y + 1);
            }
            y += 6;
        }
        int shown = 0;
        int box = 10;
        for (ApiModels.Task k : tasks)
        {
            boolean kDone = k.completed || t.completed;
            List<String> desc = wrap(tm, k.description == null ? "" : k.description, maxW - box - 6, compact ? 1 : 2);
            List<String> how = kDone || compact ? new ArrayList<>() : wrap(sm, ForgeBingoPanel.trackingText(k), maxW - box - 6, 2);
            // exactly what the drawing below uses (lines() advances ascent + descent per line)
            int tLine = tm.getAscent() + tm.getDescent();
            int sLine = sm.getAscent() + sm.getDescent();
            // "one of each" tasks list every item with its own tick instead of one bar
            List<com.forgebingo.core.ItemSlots.Slot> slots = kDone ? java.util.Collections.emptyList() : com.forgebingo.core.ItemSlots.of(k);
            boolean bar = slots.isEmpty() && k.requiredQty > 1;
            boolean pendingLine = slots.isEmpty() && k.requiredQty <= 1 && k.pendingQty > 0 && !kDone;
            int need = desc.size() * tLine + (bar ? 15 : 0) + slots.size() * (sLine + 2) + (pendingLine ? sLine : 0)
                + 1 + how.size() * sLine + (compact ? 3 : 6);
            // keep room for the closing hint, plus the "+N more" line only when another task would follow this one
            boolean last = shown == tasks.size() - 1;
            int reserve = sm.getHeight() + 4 + (last ? 0 : sm.getHeight() + 2);
            if (y + need > bottom - reserve && shown > 0) break;
            if (!dry) drawCheckbox(g, x, y + 2, box, kDone);
            int tx = x + box + 6;
            y = lines(g, body, desc, kDone ? DONE_TICK : TEXT, tx, y, dry);
            if (!slots.isEmpty())
            {
                int sub = 8;
                for (com.forgebingo.core.ItemSlots.Slot s : slots)
                {
                    y += 2;
                    String name = s.itemIds.size() == 1 ? plugin.itemName(s.itemIds.get(0)) : "Any one of " + s.itemIds.size() + " items";
                    String count = s.need > 1 ? s.approved + "/" + s.need : "";
                    Color c = s.done() ? DONE_TICK : s.pending > 0 ? PENDING : TEXT;
                    if (!dry)
                    {
                        drawCheckbox(g, tx + 2, y + 2, sub, s.done());
                        int cw = count.isEmpty() ? 0 : sm.stringWidth(count) + 6;
                        text(g, small, ellipsize(sm, name + (s.pending > 0 && !s.done() ? " (waiting)" : ""), maxW - box - 6 - sub - 6 - cw), c, tx + sub + 8, y + sm.getAscent(), false);
                        if (cw > 0) text(g, small, count, MUTED, x + maxW - cw + 6, y + sm.getAscent(), false);
                    }
                    y += sLine;
                }
            }
            else if (bar)
            {
                y += 3;
                if (!dry) drawBar(g, sm, tx, y, maxW - box - 6, k);
                y += 12;
            }
            else if (pendingLine)
            {
                y = lines(g, small, java.util.Collections.singletonList("Sent - waiting for a reviewer"), PENDING, tx, y, dry);
            }
            y = lines(g, small, how, MUTED, tx, y + 1, dry);
            y += compact ? 3 : 6;
            shown++;
        }
        if (shown < tasks.size())
        {
            y += 2;
            if (!dry) text(g, small, ellipsize(sm, "+" + (tasks.size() - shown) + " more in the ForgeBingo side panel", maxW), TEXT, x, y + sm.getAscent(), false);
            y += sm.getHeight();
        }
        y += 4;
        String hint = "Click anywhere to close";
        if (!dry) text(g, small, hint, new Color(0x6f675c), x + maxW - sm.stringWidth(hint), y + sm.getAscent(), false);
        y += sm.getHeight();
        return y - top;
    }

    private static void drawCheckbox(Graphics2D g, int x, int y, int s, boolean done)
    {
        g.setColor(done ? new Color(0x1d4a1d) : new Color(0, 0, 0, 140));
        g.fillRoundRect(x, y, s, s, 3, 3);
        g.setStroke(new BasicStroke(1f));
        g.setColor(done ? DONE_TICK : GOLD_DIM);
        g.drawRoundRect(x, y, s - 1, s - 1, 3, 3);
        if (done)
        {
            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawPolyline(new int[] {x + 2, x + 4, x + s - 2}, new int[] {y + s / 2, y + s - 3, y + 2}, 3);
        }
    }

    private static void drawBar(Graphics2D g, FontMetrics sm, int x, int y, int w, ApiModels.Task k)
    {
        int h = 11;
        g.setColor(BAR_BG);
        g.fillRoundRect(x, y, w, h, 6, 6);
        double p = Math.min(1, Math.max(0, k.approvedQty / (double) Math.max(1, k.requiredQty)));
        int fw = (int) Math.round(w * p);
        if (fw > 0)
        {
            Color c = k.completed ? BAR_DONE : BAR_FILL;
            g.setPaint(new GradientPaint(x, y, lighten(c, 30), x, y + h, c));
            g.fillRoundRect(x, y, Math.max(6, fw), h, 6, 6);
        }
        String txt = k.approvedQty + " / " + k.requiredQty + (k.pendingQty > 0 ? "  (+" + k.pendingQty + " pending)" : "");
        text(g, sm.getFont(), txt, Color.WHITE, x + w / 2, y + h - 1, true);
    }

    /** Same order as the side panel: item icon, uploaded icon, an item picked by TileIcons; null = initials. */
    private BufferedImage tilePicture(ApiModels.Tile t)
    {
        if (t.iconItemId != null) return icon(t.iconItemId);
        if (t.hasIcon && t.iconUrl != null && t.iconEmoji == null)
        {
            BufferedImage up = plugin.tileImage(t.iconUrl);
            if (up != null || !plugin.tileImageMissing(t.iconUrl)) return up;
        }
        Integer item = com.forgebingo.core.TileIcons.fallbackItem(t);
        return item == null ? null : icon(item);
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

    /** Game-style text: 1px black shadow under it. centred = x is the centre. */
    private static void text(Graphics2D g, Font f, String s, Color c, int x, int y, boolean centred)
    {
        if (s == null || s.isEmpty()) return;
        g.setFont(f);
        int tx = centred ? x - g.getFontMetrics().stringWidth(s) / 2 : x;
        g.setColor(SHADOW);
        g.drawString(s, tx + 1, y + 1);
        g.setColor(c);
        g.drawString(s, tx, y);
    }

    /** Draws (or, when dry, only measures) lines starting with their tops at y; returns the y under the last one. */
    private static int lines(Graphics2D g, Font f, List<String> lines, Color c, int x, int y, boolean dry)
    {
        FontMetrics fm = g.getFontMetrics(f);
        for (String line : lines)
        {
            y += fm.getAscent();
            if (!dry) text(g, f, line, c, x, y, false);
            y += fm.getDescent();
        }
        return y;
    }

    static String points(int n)
    {
        return n + (n == 1 ? " pt" : " pts");
    }

    /** Slight overshoot then settle, for the card pop. */
    static double easeOutBack(double t)
    {
        double c1 = 1.4;
        double c3 = c1 + 1;
        double u = t - 1;
        return 1 + c3 * u * u * u + c1 * u * u;
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

    private static Color darken(Color c, int by)
    {
        return new Color(Math.max(0, c.getRed() - by), Math.max(0, c.getGreen() - by), Math.max(0, c.getBlue() - by), c.getAlpha());
    }

    private static Color mix(Color a, Color b, double t)
    {
        double u = Math.max(0, Math.min(1, t));
        return new Color((int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * u),
            (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * u),
            (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * u), a.getAlpha());
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
