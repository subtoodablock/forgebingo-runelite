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
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The bingo board drawn over the game (owner request, 2026-11-07; redrawn 2026-11-13 to stay clean at any window
 * size). Toggled by the hotkey or the panel button; a clicked tile pops open into a card sized to its content;
 * clicking off the board, Esc or taking a hit closes it, and the open tile is remembered for next time. Hovering a
 * tile fades in a highlight and a name label.
 *
 * <p>How it stays sharp and cheap: every size comes from {@link BoardLayout#scale}, straight edges sit on whole
 * pixels, curves and text are anti-aliased (the side panel's Cinzel headings and clean body font), and item sprites
 * are only ever enlarged by whole numbers without smoothing. The stone panel, the header, each tile face and the open
 * card are painted once into images and reused until the board, the layout or an icon changes, so a settled frame is
 * a handful of image copies. Tiles in flight are those same images drawn through a sub-pixel transform with a short
 * motion-blur trail, and snap to the pixel grid when they land.
 *
 * <p>Drawn on the client thread; opened, closed and clicked from the AWT / Swing threads, so all shared state is
 * volatile and the board is the plugin's immutable-by-convention snapshot (never fetched here). The image caches are
 * only touched on the client thread.
 */
public class BoardOverlay extends Overlay
{
    /** the whole overlay fading and easing in or out */
    static final long FADE_MS = 200;
    /** the tile card popping open */
    static final long POP_MS = 240;
    /** hover highlight fade */
    static final long HOVER_MS = 130;
    /** the whole opening deal, first tile leaving the deck to the last one landing */
    static final long DEAL_MS = 3000;
    /** one tile's flight from the deck to its slot */
    static final long FLIGHT_MS = 460;
    /** the last tile lands by here, leaving room for the rarity effects inside DEAL_MS */
    static final long LAND_MS = 2550;
    /** sparkles / glow after a tile lands (Rare and up) */
    static final long LANDING_FX_MS = 420;
    /** the soft flash every tile gives as it settles into its slot */
    static final long SETTLE_MS = 220;
    /** how far back in time a flying tile's motion-blur trail reaches */
    static final long BLUR_MS = 34;
    /** the most blur samples drawn behind one flying tile (fast tiles get more, so the trail stays continuous) */
    private static final int BLUR_MAX = 10;

    private static final Color GOLD = PanelUi.GOLD;
    private static final Color GOLD_SOFT = PanelUi.GOLD_SOFT;
    private static final Color GOLD_DIM = new Color(0x7a5c22);
    private static final Color GOLD_EDGE = new Color(0x5a4420);
    private static final int STONE_TOP = 0x322c27;
    private static final int STONE_BOTTOM = 0x1d1a17;
    private static final int CARD_TOP = 0x2f2924;
    private static final int CARD_BOTTOM = 0x211d19;
    private static final int RIM_TOP = 0x94713a;
    private static final int RIM_BOTTOM = 0x5a4420;
    private static final Color WELL = new Color(0x151210);
    private static final Color TEXT = PanelUi.TEXT;
    private static final Color MUTED = PanelUi.MUTED;
    private static final Color FAINT = new Color(0x776e62);
    private static final Color SHADOW = new Color(0, 0, 0, 170);
    private static final Color DONE_TICK = new Color(0x7fe57f);
    private static final Color BAR_BG = new Color(0, 0, 0, 170);
    private static final Color BAR_FILL = new Color(0xff981f);
    private static final Color BAR_DONE = new Color(0x4fbf4f);
    private static final Color PENDING = new Color(0xffd83d);
    private static final Color DIM = new Color(0, 0, 0, 105);
    private static final Color SECRET_MARK = new Color(0xffd75a);

    private final Client client;
    private final ItemManager itemManager;
    private final ForgeBingoConfig config;
    private final ForgeBingoPlugin plugin;
    /** item icons already asked of the client (it loads them asynchronously; the image fills in when ready) */
    private final Map<Integer, BufferedImage> icons = new ConcurrentHashMap<>();
    /** the animation clock (the preview harness swaps it to draw frames at exact times) */
    LongSupplier clock = System::currentTimeMillis;

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
    /** the mouse is over the close button (it lights up) */
    private volatile boolean hoverClose;
    private volatile BoardLayout layout;
    /** bumped to throw the image caches away (plugin shutdown) */
    private volatile int cacheEpoch;

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
        hoverClose = false;
        // the remembered tile comes back without its pop (the board fade is enough)
        cardAt = 0;
        cascade = selectedTileId == null;
        fadeAt = clock.getAsLong();
        // a new opening animation every time, rolled by rarity (see OpeningAnim)
        openingSeed = fadeAt;
        opening = OpeningAnim.roll(new java.util.Random(openingSeed));
        openingRanks = null;
        open = true;
    }

    /** The shared reveal when a bingo starts: the board opens by itself with a Legendary animation. Any thread. */
    void openReveal()
    {
        if (open) close();
        selectedTileId = null;
        open();
        opening = OpeningAnim.rollOf(OpeningAnim.Rarity.LEGENDARY, new java.util.Random(openingSeed));
        openingRanks = null;
    }

    /** this opening's animation, its random seed and the arrival order (built on first draw for the board's size) */
    volatile OpeningAnim.Spec opening = OpeningAnim.ALL.get(0);
    private volatile long openingSeed;
    private volatile int[] openingRanks;

    void close()
    {
        if (!open) return;
        fadeAt = clock.getAsLong();
        open = false;
        hover = -1;
        hoverPrev = -1;
        hoverClose = false;
    }

    /** Plugin shutdown: forget everything. */
    void reset()
    {
        open = false;
        fadeAt = 0;
        selectedTileId = null;
        hover = -1;
        hoverPrev = -1;
        hoverClose = false;
        icons.clear();
        cacheEpoch++;
    }

    void hover(int x, int y)
    {
        BoardLayout l = layout();
        int next = l == null ? -1 : l.cellAt(x, y);
        hoverClose = l != null && l.onCloseButton(x, y);
        if (next != hover)
        {
            hoverPrev = hover;
            hoverAt = clock.getAsLong();
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
                cardAt = config.boardOverlayAnimations() ? clock.getAsLong() : 0;
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

    // ---------- what is cached between frames (client thread only) ----------

    /** One tile's finished face (background, icon, badges, bar) with a clear margin around it for smooth edges in flight. */
    private static final class Face
    {
        BufferedImage img;
        ApiModels.Tile tile;
        BufferedImage pic;
        /** the icon had not loaded when this was painted (the client fills icons in later): look again soon */
        boolean blank;
        long checkedAt;
    }

    private int seenEpoch;
    /** current scale and the line weight / fonts that go with it */
    private double s = 1;
    private int u = 1;
    private double fontScale = -1;
    private Font fTeam, fCount, fName, fTip, fTipSub, fCardTitle, fBody, fSmall, fSmallBold;

    private BufferedImage backdrop;
    private BoardLayout backdropFor;
    private BufferedImage headerImg;
    private BoardLayout headerLayout;
    private ApiModels.BoardResponse headerBoard;
    private Face[] faces;
    private BoardLayout facesFor;
    private ApiModels.Tile[] cells;
    private ApiModels.BoardResponse cellsBoard;
    private BoardLayout cellsLayout;
    private BufferedImage cardImg;
    private ApiModels.Tile cardTile;
    private BoardLayout cardLayout;
    private Rectangle cardRect;
    private BufferedImage messageImg;

    // scratch shapes, reused every frame
    private final AffineTransform xf = new AffineTransform();
    private final RoundRectangle2D.Double rr = new RoundRectangle2D.Double();
    private final Line2D.Double line = new Line2D.Double();
    private final Path2D.Double path = new Path2D.Double();
    private final Ellipse2D.Double oval = new Ellipse2D.Double();

    private void dropCaches()
    {
        backdrop = null;
        backdropFor = null;
        headerImg = null;
        headerLayout = null;
        headerBoard = null;
        faces = null;
        facesFor = null;
        cells = null;
        cellsBoard = null;
        cellsLayout = null;
        cardImg = null;
        cardTile = null;
        cardLayout = null;
        messageImg = null;
    }

    private int px(double n)
    {
        return (int) Math.round(n * s);
    }

    private void useScale(double scale)
    {
        s = scale;
        u = Math.max(1, (int) Math.round(scale * 0.8));
        if (scale == fontScale) return;
        fontScale = scale;
        Font head = PanelUi.CLEAN_HEAD;
        Font body = PanelUi.CLEAN_BODY;
        Font bold = PanelUi.CLEAN_BOLD;
        // whole-pixel sizes: the letter shapes are hinted to the grid, so small text stays even
        fTeam = head.deriveFont((float) Math.round(15 * scale));
        fCount = bold.deriveFont((float) Math.round(13 * scale));
        fName = body.deriveFont((float) Math.round(11 * scale));
        fTip = head.deriveFont((float) Math.round(12.5 * scale));
        fTipSub = body.deriveFont((float) Math.round(11 * scale));
        fCardTitle = head.deriveFont((float) Math.round(15 * scale));
        fBody = body.deriveFont((float) Math.round(12 * scale));
        fSmall = body.deriveFont((float) Math.round(11 * scale));
        fSmallBold = bold.deriveFont((float) Math.round(11 * scale));
    }

    /** Smooth shapes and text; images are copied 1:1 unless something is moving. */
    private static void quality(Graphics2D g)
    {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        // tiles in flight sit between pixels: blend them. Anything on the pixel grid is unaffected by this hint.
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        // TrueType text at the board's own size, smoothed (greyscale: the game image behind it is not an LCD surface)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    // ---------- drawing (client thread) ----------

    @Override
    public Dimension render(Graphics2D g)
    {
        long now = clock.getAsLong();
        boolean anim = config.boardOverlayAnimations();
        boolean isOpen = open;
        // 0 = hidden, 1 = fully there; eased both ways
        double vis;
        if (isOpen)
        {
            vis = anim ? BoardLayout.easeOut((now - fadeAt) / (double) FADE_MS) : 1;
        }
        else
        {
            if (!anim || now - fadeAt >= FADE_MS) return null;
            vis = 1 - BoardLayout.easeInOut((now - fadeAt) / (double) FADE_MS);
        }
        float alpha = (float) vis;
        if (alpha <= 0.01f) return null;
        if (seenEpoch != cacheEpoch)
        {
            seenEpoch = cacheEpoch;
            dropCaches();
        }

        int w = client.getCanvasWidth();
        int h = client.getCanvasHeight();
        Graphics2D g2 = (Graphics2D) g.create();
        try
        {
            quality(g2);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            // dim the game a little so the board reads, without hiding what is going on
            g2.setColor(DIM);
            g2.fillRect(0, 0, w, h);

            ApiModels.BoardResponse board = plugin.currentBoard();
            BoardLayout l = layout();
            if (board == null || l == null)
            {
                drawNoBoard(g2, w, h);
                return null;
            }
            useScale(l.scale);
            if (anim && vis < 0.999)
            {
                // the board eases up to size as it fades in (and back down as it leaves)
                double grow = 0.965 + 0.035 * vis;
                Rectangle b = l.board();
                g2.translate(b.getCenterX(), b.getCenterY());
                g2.scale(grow, grow);
                g2.translate(-b.getCenterX(), -b.getCenterY());
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
        useScale(1);
        int sm = 22;
        if (messageImg == null || messageImg.getWidth() != m.width + 2 * sm || messageImg.getHeight() != m.height + 2 * sm)
        {
            BufferedImage img = new BufferedImage(m.width + 2 * sm, m.height + 2 * sm, BufferedImage.TYPE_INT_ARGB);
            paintPanel(img, sm, sm, m.width, m.height, 9, 1, STONE_TOP, STONE_BOTTOM, RIM_TOP, RIM_BOTTOM, sm - 6, 6, 150);
            Graphics2D gi = img.createGraphics();
            quality(gi);
            int cx = sm + m.width / 2;
            int cy = sm + m.height / 2;
            text(gi, fTeam, "No bingo board yet", GOLD, cx, cy - 3, 1, 1);
            text(gi, fName, "Connect in the ForgeBingo side panel", MUTED, cx, cy + 15, 1, 1);
            gi.dispose();
            messageImg = img;
        }
        g.drawImage(messageImg, m.x - sm, m.y - sm, null);
    }

    /**
     * Rasterises a rounded panel straight into an image: a soft drop shadow, a black outline, a gold rim (brighter at
     * the top), then stone that darkens downwards with a faint grain. Done per pixel from the distance to the rounded
     * edge, so the corners are smooth at any size, the long gradient cannot band and the shadow has no steps.
     *
     * @param ox,oy,w,h the panel inside the image
     * @param lw        outline and rim thickness, px
     * @param shadowR   how far the shadow reaches; shadowDy how far it drops; shadowA its darkest alpha (0..255)
     */
    static void paintPanel(BufferedImage img, int ox, int oy, int w, int h, double radius, int lw,
        int top, int bottom, int rimTop, int rimBottom, int shadowR, int shadowDy, int shadowA)
    {
        int iw = img.getWidth();
        int ih = img.getHeight();
        int[] out = new int[iw * ih];
        double cx = ox + w / 2.0;
        double cy = oy + h / 2.0;
        double hw = w / 2.0 - radius;
        double hh = h / 2.0 - radius;
        for (int y = 0; y < ih; y++)
        {
            double fy = Math.max(0, Math.min(1, (y - oy) / (double) Math.max(1, h - 1)));
            double sr = lerp(top >> 16 & 255, bottom >> 16 & 255, fy);
            double sg = lerp(top >> 8 & 255, bottom >> 8 & 255, fy);
            double sb = lerp(top & 255, bottom & 255, fy);
            double rimR = lerp(rimTop >> 16 & 255, rimBottom >> 16 & 255, fy);
            double rimG = lerp(rimTop >> 8 & 255, rimBottom >> 8 & 255, fy);
            double rimB = lerp(rimTop & 255, rimBottom & 255, fy);
            // the inner highlight is strongest along the top edge and gone by the middle
            double shine = 20 * Math.max(0, 1 - fy * 2.2);
            double py = y + 0.5 - cy;
            for (int x = 0; x < iw; x++)
            {
                double pxl = x + 0.5 - cx;
                double d = roundBox(pxl, py, hw, hh, radius);
                int n = (x * 73856093) ^ (y * 19349663);
                n = (n ^ (n >>> 13)) * 1274126177;
                n ^= n >>> 16;
                double r1 = (n & 0xffff) / 65536.0;
                double r2 = (n >>> 16 & 0xffff) / 65536.0;
                double a = 0;
                if (d > -1 && shadowA > 0)
                {
                    double ds = roundBox(pxl, py - shadowDy, hw, hh, radius);
                    double t = Math.max(0, Math.min(1, 1 - ds / shadowR));
                    a = shadowA / 255.0 * t * t * (3 - 2 * t) * t;
                }
                if (d >= 0.5)
                {
                    int ai = (int) (a * 255 + r1);
                    out[y * iw + x] = Math.min(255, ai) << 24;
                    continue;
                }
                double c0 = cover(d);
                double c1 = cover(d + lw);
                double c2 = cover(d + 2 * lw);
                double c3 = cover(d + 3 * lw);
                a = a + (1 - a) * c0;
                // grain (the same on every channel, so it reads as stone, not colour noise) + dither for the gradient
                double grain = (r2 - 0.5) * 3.2;
                double lift = shine * (c2 - c3);
                double r = lerp(lerp(0, rimR, c1), sr + grain + lift, c2);
                double gg = lerp(lerp(0, rimG, c1), sg + grain + lift, c2);
                double b = lerp(lerp(0, rimB, c1), sb + grain + lift, c2);
                int ri = clamp255((int) Math.floor(r + r1));
                int gi = clamp255((int) Math.floor(gg + r1));
                int bi = clamp255((int) Math.floor(b + r1));
                out[y * iw + x] = clamp255((int) Math.round(a * 255)) << 24 | ri << 16 | gi << 8 | bi;
            }
        }
        img.setRGB(0, 0, iw, ih, out, 0, iw);
    }

    /** Distance from a point to a rounded box centred on the origin (negative inside). hw/hh: half sizes less the radius. */
    private static double roundBox(double x, double y, double hw, double hh, double radius)
    {
        double qx = Math.abs(x) - hw;
        double qy = Math.abs(y) - hh;
        double ax = Math.max(qx, 0);
        double ay = Math.max(qy, 0);
        return Math.sqrt(ax * ax + ay * ay) + Math.min(Math.max(qx, qy), 0) - radius;
    }

    /** How much of a pixel whose centre is d from an edge lies inside it. */
    private static double cover(double d)
    {
        return Math.max(0, Math.min(1, 0.5 - d));
    }

    private static double lerp(double a, double b, double t)
    {
        return a + (b - a) * t;
    }

    private static int clamp255(int v)
    {
        return Math.max(0, Math.min(255, v));
    }

    private int shadowMargin()
    {
        return px(26);
    }

    private static int cellArc(int c)
    {
        return Math.max(3, Math.min(20, (int) Math.round(c * 0.14)));
    }

    /** The panel with its shadow and the empty slots the tiles sit in; painted once per layout. */
    private BufferedImage backdrop(BoardLayout l)
    {
        if (backdrop != null && backdropFor == l) return backdrop;
        Rectangle b = l.board();
        int sm = shadowMargin();
        int drop = px(6);
        BufferedImage img = new BufferedImage(b.width + 2 * sm, b.height + 2 * sm, BufferedImage.TYPE_INT_ARGB);
        paintPanel(img, sm, sm, b.width, b.height, px(9), u, STONE_TOP, STONE_BOTTOM, RIM_TOP, RIM_BOTTOM, sm - drop, drop, 165);
        Graphics2D g = img.createGraphics();
        quality(g);
        g.translate(sm - b.x, sm - b.y);
        Rectangle grid = l.grid();
        int c = l.cell;
        int arc = cellArc(c);
        int lip = c >= 12 ? u : 0;
        for (int r = 0; r < l.size; r++)
        {
            for (int col = 0; col < l.size; col++)
            {
                int x = grid.x + col * (c + l.gap);
                int y = grid.y + r * (c + l.gap);
                // a slot pressed into the stone: light catches its bottom lip, its top edge is in shade
                g.setColor(new Color(255, 255, 255, 16));
                g.fillRoundRect(x, y + lip, c, c, arc, arc);
                g.setColor(WELL);
                g.fillRoundRect(x, y, c, c, arc, arc);
                g.setPaint(new GradientPaint(0, y, new Color(0, 0, 0, 120), 0, y + Math.max(2, c * 0.22f), new Color(0, 0, 0, 0)));
                g.fillRoundRect(x, y, c, c, arc, arc);
            }
        }
        g.dispose();
        backdrop = img;
        backdropFor = l;
        return img;
    }

    /** Team, bingo name, count and the progress bar; painted once per board snapshot. Drawn at the board's corner. */
    private BufferedImage header(ApiModels.BoardResponse board, BoardLayout l)
    {
        if (headerImg != null && headerLayout == l && headerBoard == board) return headerImg;
        Rectangle b = l.board();
        int hh = l.pad + l.header;
        BufferedImage img = headerImg != null && headerImg.getWidth() == b.width && headerImg.getHeight() == hh
            ? headerImg : new BufferedImage(b.width, hh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, b.width, hh);
        g.setComposite(AlphaComposite.SrcOver);
        quality(g);

        String name = board.event != null && board.event.name != null ? board.event.name : board.board != null && board.board.title != null ? board.board.title : "Bingo";
        if (board.event != null && !board.event.active) name += " (not running)";
        int total = board.tiles == null ? 0 : board.tiles.size();
        int done = 0;
        if (board.tiles != null) for (ApiModels.Tile t : board.tiles) if (t != null && t.completed) done++;
        boolean blackout = done == total && total > 0;

        int x = l.pad;
        int right = b.width - l.pad - l.close - px(12);
        int row1 = l.pad + px(16);
        int row2 = l.pad + px(31);
        String team = board.teamName == null ? name : board.teamName;
        Font teamFont = PanelUi.headingFor(team, fTeam, fCount.deriveFont(fTeam.getSize2D()));

        // the count: done in gold (green on a blackout), the total quieter beside it
        String doneText = blackout ? "BLACKOUT" : Integer.toString(done);
        String totalText = blackout ? "" : " / " + total;
        FontMetrics cm = g.getFontMetrics(fCount);
        int countW = cm.stringWidth(doneText) + cm.stringWidth(totalText);
        boolean showCount = countW + px(40) < right - x;
        if (showCount)
        {
            text(g, fCount, doneText, blackout ? DONE_TICK : GOLD, right - countW, row1, 0, u);
            text(g, fCount, totalText, MUTED, right - cm.stringWidth(totalText), row1, 0, u);
        }
        int teamRoom = right - x - (showCount ? countW + px(12) : 0);
        text(g, teamFont, ellipsize(g.getFontMetrics(teamFont), team, teamRoom), GOLD, x, row1, 0, u);
        if (board.teamName != null)
        {
            text(g, fName, ellipsize(g.getFontMetrics(fName), name, right - x), MUTED, x, row2, 0, u);
        }

        // progress bar across the foot of the header
        int barH = Math.max(3, px(4));
        int barY = hh - px(6) - barH;
        int barW = b.width - 2 * l.pad;
        g.setColor(new Color(255, 255, 255, 14));
        g.fillRoundRect(x, barY + 1, barW, barH, barH, barH);
        g.setColor(new Color(0x100e0c));
        g.fillRoundRect(x, barY, barW, barH, barH, barH);
        double p = total == 0 ? 0 : done / (double) total;
        int fillW = (int) Math.round(barW * p);
        if (fillW > 0)
        {
            fillW = Math.max(barH, fillW);
            g.setPaint(new GradientPaint(0, barY, blackout ? BAR_DONE.brighter() : new Color(0xffcf6a), 0, barY + barH, blackout ? BAR_DONE : BAR_FILL));
            g.fillRoundRect(x, barY, fillW, barH, barH, barH);
        }
        g.dispose();
        headerImg = img;
        headerLayout = l;
        headerBoard = board;
        return img;
    }

    private ApiModels.Tile[] cells(ApiModels.BoardResponse board, BoardLayout l)
    {
        if (cells != null && cellsBoard == board && cellsLayout == l) return cells;
        ApiModels.Tile[] out = new ApiModels.Tile[l.size * l.size];
        if (board.tiles != null)
        {
            for (ApiModels.Tile t : board.tiles)
            {
                if (t != null && t.row >= 0 && t.col >= 0 && t.row < l.size && t.col < l.size) out[t.row * l.size + t.col] = t;
            }
        }
        cells = out;
        cellsBoard = board;
        cellsLayout = l;
        return out;
    }

    private void drawBoard(Graphics2D g, ApiModels.BoardResponse board, BoardLayout l, boolean hoverable, ApiModels.Tile selected,
        long now, boolean anim, long sinceOpen, float boardAlpha)
    {
        Rectangle b = l.board();
        int sm = shadowMargin();
        g.drawImage(backdrop(l), b.x - sm, b.y - sm, null);
        boolean legendaryDeal = sinceOpen != Long.MAX_VALUE && opening.rarity == OpeningAnim.Rarity.LEGENDARY;
        if (legendaryDeal) drawVortex(g, b, sinceOpen, boardAlpha);
        g.drawImage(header(board, l), b.x, b.y, null);
        drawCloseButton(g, l.closeButton(), hoverable && hoverClose);

        ApiModels.Tile[] tiles = cells(board, l);
        if (faces == null || facesFor != l || faces.length != tiles.length)
        {
            faces = new Face[tiles.length];
            facesFor = l;
        }
        int c = l.cell;
        int step = c + l.gap;
        Rectangle grid = l.grid();
        int m = faceMargin();
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
        double stepMs = n > 1 ? (LAND_MS - FLIGHT_MS) / (double) (n - 1) : 0;
        double deckX = b.getCenterX();
        double deckY = b.y + b.height - l.pad - c / 2.0;
        int undealt = 0;
        boolean anyFlying = false;
        for (int idx = 0; idx < n; idx++)
        {
            ApiModels.Tile t = tiles[idx];
            int x = grid.x + idx % l.size * step;
            int y = grid.y + idx / l.size * step;
            long sinceLand = Long.MAX_VALUE;
            if (dealing)
            {
                double dealt = (sinceOpen - ranks[idx] * stepMs) / FLIGHT_MS;
                if (dealt <= 0) { undealt++; continue; }
                if (dealt < 1) { anyFlying = true; continue; }
                sinceLand = sinceOpen - (long) (ranks[idx] * stepMs + FLIGHT_MS);
            }
            if (t == null) continue;
            // landed: an exact copy on the pixel grid
            g.drawImage(face(idx, t, c, now).img, x - m, y - m, null);
            if (sinceLand < SETTLE_MS)
            {
                double f = 1 - sinceLand / (double) SETTLE_MS;
                g.setColor(new Color(255, 244, 214, (int) (52 * f * f)));
                g.fillRoundRect(x, y, c, c, cellArc(c), cellArc(c));
            }
            double glow = t == selected ? 1 : idx == hov ? hoverIn : idx == prev ? 1 - hoverIn : 0;
            if (glow > 0.01) drawHighlight(g, x, y, c, glow, t == selected, boardAlpha, base);
            if (sinceLand < LANDING_FX_MS && rarity != OpeningAnim.Rarity.COMMON) drawLanding(g, x, y, c, idx, sinceLand, rarity, boardAlpha);
        }
        if (dealing)
        {
            // the deck (Deal entrance only): a small stack that shrinks as the tiles leave it
            if (undealt > 0 && spec.entrance == OpeningAnim.Entrance.DEAL) drawDeck(g, deckX, deckY, c, undealt);
            if (anyFlying)
            {
                // tiles on their way in, on top, between pixels; Epic and Legendary also leave a coloured trail
                boolean trails = rarity == OpeningAnim.Rarity.EPIC || rarity == OpeningAnim.Rarity.LEGENDARY;
                AffineTransform home = g.getTransform();
                for (int idx = 0; idx < n; idx++)
                {
                    double fp = (sinceOpen - ranks[idx] * stepMs) / FLIGHT_MS;
                    if (fp <= 0 || fp >= 1 || tiles[idx] == null) continue;
                    Face f = face(idx, tiles[idx], c, now);
                    double tx = grid.x + idx % l.size * step + c / 2.0;
                    double ty = grid.y + idx / l.size * step + c / 2.0;
                    if (trails)
                    {
                        for (int k = 2; k >= 1; k--)
                        {
                            drawPose(g, home, f, c, m, spec, idx, fp - 0.11 * k, tx, ty, b, deckX, deckY, boardAlpha * (k == 1 ? 0.24f : 0.11f), rarity.colour);
                        }
                    }
                    // motion blur: a few fainter copies where the tile was a moment ago, stronger the faster it moves
                    double span = BLUR_MS / (double) FLIGHT_MS;
                    double[] here = OpeningAnim.pose(spec.entrance, fp, idx, tx, ty, c, b.x, b.y, b.width, b.height, deckX, deckY);
                    double[] was = OpeningAnim.pose(spec.entrance, fp - span, idx, tx, ty, c, b.x, b.y, b.width, b.height, deckX, deckY);
                    double moved = Math.hypot(here[0] - was[0], here[1] - was[1]) + c * 0.5 * Math.abs(here[4] - was[4])
                        + c * 0.5 * Math.max(Math.abs(here[2] - was[2]), Math.abs(here[3] - was[3]));
                    double strength = Math.max(0, Math.min(1, (moved - 1.5) / (c * 0.22)));
                    if (strength > 0.03)
                    {
                        // close enough together to read as one smear, fading out towards where the tile came from
                        int samples = (int) Math.max(3, Math.min(BLUR_MAX, Math.ceil(moved / Math.max(2.0, c * 0.07))));
                        double each = 0.9 * strength * Math.min(1, 3.0 / samples);
                        for (int k = samples; k >= 1; k--)
                        {
                            double fall = 1 - k / (samples + 1.0);
                            drawPose(g, home, f, c, m, spec, idx, fp - span * k / samples, tx, ty, b, deckX, deckY,
                                (float) (boardAlpha * each * fall * fall), null);
                        }
                    }
                    drawPose(g, home, f, c, m, spec, idx, fp, tx, ty, b, deckX, deckY, boardAlpha, null);
                }
                g.setTransform(home);
                g.setComposite(base);
            }
            if (rarity == OpeningAnim.Rarity.LEGENDARY) drawBurst(g, b, sinceOpen, boardAlpha);
            g.setComposite(base);
            if (sinceOpen > DEAL_MS) cascade = false;
        }

        // name label over the hovered tile
        if (hov >= 0 && hov < n && selected == null && tiles[hov] != null)
        {
            drawHoverLabel(g, l, grid.x + hov % l.size * step, grid.y + hov / l.size * step, tiles[hov], hoverIn, boardAlpha);
            g.setComposite(base);
        }
    }

    /**
     * One tile (or, with ghost set, its coloured trail) somewhere along its flight, drawn through a transform with
     * fractional positions so it glides instead of stepping from pixel to pixel.
     */
    private void drawPose(Graphics2D g, AffineTransform home, Face f, int c, int m, OpeningAnim.Spec spec, int idx, double fp,
        double tx, double ty, Rectangle b, double deckX, double deckY, float alpha, Color ghost)
    {
        if (fp <= 0) return;
        double[] p = OpeningAnim.pose(spec.entrance, fp, idx, tx, ty, c, b.x, b.y, b.width, b.height, deckX, deckY);
        float a = (float) Math.max(0, Math.min(1, alpha * p[5]));
        if (a < 0.004f) return;
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
        xf.setTransform(home);
        xf.translate(p[0], p[1]);
        xf.rotate(p[4]);
        xf.scale(p[2], p[3]);
        if (ghost == null)
        {
            xf.translate(-c / 2.0 - m, -c / 2.0 - m);
            g.setTransform(xf);
            g.drawImage(f.img, 0, 0, null);
        }
        else
        {
            g.setTransform(xf);
            g.setColor(ghost);
            rr.setRoundRect(-c / 2.0, -c / 2.0, c, c, cellArc(c), cellArc(c));
            g.fill(rr);
        }
    }

    /** Hover / selected: the face brightens a touch and a gold ring with a faint outer glow fades in. */
    private void drawHighlight(Graphics2D g, int x, int y, int c, double glow, boolean selected, float boardAlpha, Composite base)
    {
        float a = (float) Math.max(0, Math.min(1, glow));
        int arc = cellArc(c);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a * boardAlpha));
        if (!selected)
        {
            g.setColor(new Color(255, 240, 210, 26));
            g.fillRoundRect(x + u, y + u, c - 2 * u, c - 2 * u, Math.max(2, arc - 2 * u), Math.max(2, arc - 2 * u));
        }
        double w = Math.max(1.5, 1.6 * s);
        if (c >= 12)
        {
            // glow just outside the tile, in the gap
            g.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), 70));
            g.setStroke(new BasicStroke((float) (w + 2 * u)));
            rr.setRoundRect(x - u * 0.5, y - u * 0.5, c + u, c + u, arc + u, arc + u);
            g.draw(rr);
        }
        g.setColor(GOLD);
        g.setStroke(new BasicStroke((float) w));
        rr.setRoundRect(x + w / 2, y + w / 2, c - w, c - w, Math.max(2, arc - w), Math.max(2, arc - w));
        g.draw(rr);
        g.setComposite(base);
    }

    /** Rare and up: a few sparkles burst from a tile as it lands; Epic and up also flash a glow in the rarity colour. */
    private void drawLanding(Graphics2D g, int x, int y, int c, int idx, long sinceLand, OpeningAnim.Rarity rarity, float alpha)
    {
        double t = sinceLand / (double) LANDING_FX_MS;
        double fade = 1 - BoardLayout.easeInOut(t);
        double out = OpeningAnim.easeOut(t);
        Color col = rarity.colour;
        double cx = x + c / 2.0;
        double cy = y + c / 2.0;
        if (rarity != OpeningAnim.Rarity.RARE)
        {
            double grow = out * c * 0.35;
            int arc = cellArc(c);
            g.setStroke(new BasicStroke((float) Math.max(1.5, 2 * s * (1 - 0.5 * t))));
            g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), (int) (200 * fade * alpha)));
            rr.setRoundRect(x - grow / 2, y - grow / 2, c + grow, c + grow, arc + grow, arc + grow);
            g.draw(rr);
        }
        int count = rarity == OpeningAnim.Rarity.RARE ? 5 : rarity == OpeningAnim.Rarity.EPIC ? 7 : 10;
        for (int k = 0; k < count; k++)
        {
            long h = OpeningAnim.hash(idx * 31L + k);
            double ang = (h % 628) / 100.0;
            double dist = c * (0.35 + 0.5 * out) * (0.7 + (h >>> 10) % 30 / 100.0);
            double size = Math.max(1.5, c * 0.085 * fade + 1);
            double sx = cx + Math.cos(ang) * dist;
            double sy = cy + Math.sin(ang) * dist;
            Color sc = k % 3 == 0 ? Color.WHITE : rarity == OpeningAnim.Rarity.LEGENDARY ? GOLD : col;
            g.setColor(new Color(sc.getRed(), sc.getGreen(), sc.getBlue(), (int) (230 * fade * alpha)));
            // a four-point star with pinched sides
            double pinch = size * 0.28;
            path.reset();
            path.moveTo(sx, sy - size);
            path.lineTo(sx + pinch, sy - pinch);
            path.lineTo(sx + size, sy);
            path.lineTo(sx + pinch, sy + pinch);
            path.lineTo(sx, sy + size);
            path.lineTo(sx - pinch, sy + pinch);
            path.lineTo(sx - size, sy);
            path.lineTo(sx - pinch, sy - pinch);
            path.closePath();
            g.fill(path);
        }
    }

    /** Legendary: slow rotating rays of light behind the board while the tiles arrive. */
    private void drawVortex(Graphics2D g, Rectangle b, long since, float alpha)
    {
        double life = BoardLayout.easeInOut(since / 400.0) * BoardLayout.easeInOut((DEAL_MS - since) / 500.0);
        if (life <= 0) return;
        Graphics2D gc = (Graphics2D) g.create();
        try
        {
            int in = 2 * u;
            gc.clip(new RoundRectangle2D.Double(b.x + in, b.y + in, b.width - 2 * in, b.height - 2 * in, px(14), px(14)));
            double cx = b.getCenterX(), cy = b.getCenterY(), r = Math.hypot(b.width, b.height);
            gc.translate(cx, cy);
            gc.rotate(since / 900.0);
            Arc2D.Double ray = new Arc2D.Double(-r, -r, 2 * r, 2 * r, -6, 12, Arc2D.PIE);
            gc.setPaint(new GradientPaint(0, 0, new Color(255, 200, 90, (int) (80 * life * alpha)), (float) r * 0.6f, 0, new Color(255, 160, 40, 0)));
            for (int k = 0; k < 12; k++)
            {
                gc.rotate(Math.PI / 6);
                gc.fill(ray);
            }
        }
        finally
        {
            gc.dispose();
        }
    }

    /** Legendary: a golden ring and spray of sparks from the middle as the last tiles land. */
    private void drawBurst(Graphics2D g, Rectangle b, long since, float alpha)
    {
        double t = (since - (LAND_MS - 150)) / (double) (DEAL_MS - LAND_MS + 150);
        if (t <= 0 || t >= 1) return;
        double cx = b.getCenterX(), cy = b.getCenterY(), reach = Math.max(b.width, b.height) * 0.6 * OpeningAnim.easeOut(t);
        int a = (int) (230 * (1 - BoardLayout.easeInOut(t)) * alpha);
        g.setStroke(new BasicStroke((float) Math.max(2, 3 * s * (1 - 0.6 * t))));
        g.setColor(new Color(255, 214, 102, a));
        oval.setFrame(cx - reach, cy - reach, reach * 2, reach * 2);
        g.draw(oval);
        for (int k = 0; k < 36; k++)
        {
            long h = OpeningAnim.hash(9000 + k);
            double ang = k * Math.PI * 2 / 36 + (h % 100) / 400.0, d = reach * (0.6 + (h >>> 8) % 40 / 100.0);
            double size = (3 + h % 3) * s * 0.8;
            g.setColor(k % 4 == 0 ? new Color(255, 255, 255, a) : new Color(255, 184, 63, a));
            oval.setFrame(cx + Math.cos(ang) * d - size / 2.0, cy + Math.sin(ang) * d - size / 2.0, size, size);
            g.fill(oval);
        }
    }

    /** The deck the opening deal comes from: a few offset card backs, thinner as fewer tiles are left. */
    private void drawDeck(Graphics2D g, double atX, double atY, int cell, int left)
    {
        int w = Math.max(10, (int) (cell * 0.8));
        int layers = Math.max(1, Math.min(4, (left + 9) / 10));
        int arc = cellArc(w);
        int off = Math.max(2, px(2));
        for (int i = layers - 1; i >= 0; i--)
        {
            int x = (int) Math.round(atX) - w / 2 + i * off;
            int y = (int) Math.round(atY) - w / 2 - i * off;
            g.setColor(new Color(0, 0, 0, 90));
            g.fillRoundRect(x, y + u, w, w, arc, arc);
            g.setColor(GOLD_DIM);
            g.fillRoundRect(x, y, w, w, arc, arc);
            g.setPaint(new GradientPaint(0, y, new Color(0x3d3124), 0, y + w, new Color(0x241d16)));
            g.fillRoundRect(x + u, y + u, w - 2 * u, w - 2 * u, Math.max(2, arc - 2 * u), Math.max(2, arc - 2 * u));
        }
        // a gold diamond on the top card's back
        double mx = Math.round(atX), my = Math.round(atY) - (layers - 1) * off, d = Math.max(3, w * 0.17);
        path.reset();
        path.moveTo(mx, my - d);
        path.lineTo(mx + d, my);
        path.lineTo(mx, my + d);
        path.lineTo(mx - d, my);
        path.closePath();
        g.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), 170));
        g.fill(path);
    }

    private void drawCloseButton(Graphics2D g, Rectangle c, boolean hot)
    {
        int arc = Math.max(4, px(7));
        g.setColor(hot ? GOLD : GOLD_EDGE);
        g.fillRoundRect(c.x, c.y, c.width, c.height, arc, arc);
        g.setColor(hot ? new Color(0x3a2c16) : new Color(0x1a1613));
        g.fillRoundRect(c.x + u, c.y + u, c.width - 2 * u, c.height - 2 * u, Math.max(2, arc - 2 * u), Math.max(2, arc - 2 * u));
        g.setColor(hot ? Color.WHITE : GOLD_SOFT);
        g.setStroke(new BasicStroke((float) Math.max(1.5, 1.7 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double in = c.width * 0.33;
        line.setLine(c.x + in, c.y + in, c.x + c.width - in, c.y + c.height - in);
        g.draw(line);
        line.setLine(c.x + c.width - in, c.y + in, c.x + in, c.y + c.height - in);
        g.draw(line);
    }

    // ---------- tile faces ----------

    /** clear pixels kept around a tile face: room for its contact shadow and for smooth edges while it flies */
    private int faceMargin()
    {
        return u + 2;
    }

    /** {top, bottom, edge, top highlight} for a tile in this state: the side panel's state colours, warmed to sit on stone. */
    private static Color[] faceColours(TileInfo.State st)
    {
        int top, bottom;
        switch (st)
        {
            case DONE: top = 0x2f7636; bottom = 0x1d4f24; break;
            case WAITING: top = 0x7d6522; bottom = 0x574516; break;
            case NEEDS_CHANGES: top = 0x7e302b; bottom = 0x561f1c; break;
            case SECRET: top = 0x4c2f6c; bottom = 0x2f1b46; break;
            case IN_PROGRESS: top = 0x414b56; bottom = 0x2b333c; break;
            default: top = 0x3d362f; bottom = 0x2a2520; break;
        }
        Color t = new Color(top);
        Color b = new Color(bottom);
        return new Color[] {t, b, mix(b, Color.BLACK, 0.72), lighten(t, 30)};
    }

    private Face face(int idx, ApiModels.Tile t, int c, long now)
    {
        Face f = faces[idx];
        BufferedImage pic = TileInfo.isSecret(t) && !t.completed ? null : tilePicture(t);
        int side = c + 2 * faceMargin();
        boolean stale = f == null || f.tile != t || f.pic != pic || f.img.getWidth() != side;
        if (!stale && f.blank && now - f.checkedAt > 250)
        {
            // the client fills an item's icon in a moment after it is first asked for
            f.checkedAt = now;
            stale = !blank(pic);
        }
        if (!stale) return f;
        if (f == null) faces[idx] = f = new Face();
        if (f.img == null || f.img.getWidth() != side) f.img = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        f.tile = t;
        f.pic = pic;
        f.blank = blank(pic);
        f.checkedAt = now;
        paintFace(f.img, t, f.blank ? null : pic, c);
        return f;
    }

    /** An icon that has nothing in it (yet). Only small sprites are checked: those are the ones that load late. */
    private static boolean blank(BufferedImage pic)
    {
        if (pic == null) return false;
        int w = pic.getWidth();
        int h = pic.getHeight();
        if (w <= 1 || h <= 1) return true;
        if (w > 64 || h > 64) return false;
        for (int y = 0; y < h; y++)
        {
            for (int x = 0; x < w; x++)
            {
                if ((pic.getRGB(x, y) >>> 24) != 0) return false;
            }
        }
        return true;
    }

    private void paintFace(BufferedImage img, ApiModels.Tile t, BufferedImage pic, int c)
    {
        int m = faceMargin();
        Graphics2D g = img.createGraphics();
        try
        {
            g.setComposite(AlphaComposite.Clear);
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            g.setComposite(AlphaComposite.SrcOver);
            quality(g);
            g.translate(m, m);
            TileInfo.State st = TileInfo.state(t);
            Color[] col = faceColours(st);
            int arc = cellArc(c);
            int e = c >= 12 ? u : 0;
            int arcIn = Math.max(2, arc - 2 * e);
            // contact shadow, dark edge, a light top lip, then the face itself
            g.setColor(new Color(0, 0, 0, 120));
            g.fillRoundRect(0, e, c, c, arc, arc);
            g.setColor(col[2]);
            g.fillRoundRect(0, 0, c, c, arc, arc);
            g.setColor(col[3]);
            g.fillRoundRect(e, e, c - 2 * e, c - 2 * e, arcIn, arcIn);
            g.setPaint(new GradientPaint(0, e, col[0], 0, c - e, col[1]));
            g.fillRoundRect(e, 2 * e, c - 2 * e, c - 3 * e, arcIn, arcIn);
            // a soft sheen over the top half
            g.setPaint(new GradientPaint(0, 2 * e, new Color(255, 255, 255, 20), 0, c * 0.55f, new Color(255, 255, 255, 0)));
            g.fillRoundRect(e, 2 * e, c - 2 * e, c - 3 * e, arcIn, arcIn);

            if (st == TileInfo.State.SECRET)
            {
                centredGlyphs(g, PanelUi.CLEAN_HEAD.deriveFont(c * 0.5f), "?", SECRET_MARK, c / 2.0, c / 2.0, c);
            }
            else if (pic != null && pic.getWidth() > 1)
            {
                // a tile with a progress bar carries its picture a touch higher
                boolean barred = st != TileInfo.State.DONE && TileInfo.progress(t) > 0 && c >= 14;
                paintIcon(g, pic, c, barred ? (int) Math.round(c * 0.035) : 0);
            }
            else
            {
                String in = initials(t.title);
                Font f = PanelUi.headingFor(in, PanelUi.CLEAN_HEAD, PanelUi.CLEAN_BOLD).deriveFont(c * 0.32f);
                centredGlyphs(g, f, in, new Color(0xe6dcc8), c / 2.0, c / 2.0, c);
            }
            if (c < 14) return;
            // on very small tiles the colour alone tells the state: a badge would hide the picture
            boolean badges = c >= 22;

            int inset = Math.max(2, (int) Math.round(c * 0.06));
            int d = Math.max(8, Math.min(46, (int) Math.round(c * 0.26)));
            int bx = c - inset - d;
            if (!badges)
            {
                // nothing in the corner
            }
            else if (st == TileInfo.State.DONE)
            {
                badge(g, bx, inset, d, new Color(0x49c452), new Color(0x2c9a38), new Color(0x0d2a10));
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(Math.max(1.4f, d * 0.13f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                path.reset();
                path.moveTo(bx + d * 0.27, inset + d * 0.53);
                path.lineTo(bx + d * 0.43, inset + d * 0.69);
                path.lineTo(bx + d * 0.74, inset + d * 0.34);
                g.draw(path);
            }
            else if (st == TileInfo.State.WAITING)
            {
                // a little clock: sent, waiting for a reviewer
                badge(g, bx, inset, d, new Color(0xffd65a), new Color(0xe0a52a), new Color(0x33270a));
                g.setColor(new Color(0x33270a));
                g.setStroke(new BasicStroke(Math.max(1.2f, d * 0.11f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                path.reset();
                path.moveTo(bx + d * 0.5, inset + d * 0.26);
                path.lineTo(bx + d * 0.5, inset + d * 0.52);
                path.lineTo(bx + d * 0.69, inset + d * 0.62);
                g.draw(path);
            }
            else if (st == TileInfo.State.NEEDS_CHANGES)
            {
                badge(g, bx, inset, d, new Color(0xff7b6e), new Color(0xd9483d), new Color(0x3a0f0c));
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(Math.max(1.4f, d * 0.14f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                line.setLine(bx + d * 0.5, inset + d * 0.25, bx + d * 0.5, inset + d * 0.54);
                g.draw(line);
                line.setLine(bx + d * 0.5, inset + d * 0.75, bx + d * 0.5, inset + d * 0.75);
                g.draw(line);
            }

            if (st != TileInfo.State.DONE && st != TileInfo.State.SECRET)
            {
                double p = TileInfo.progress(t);
                if (p > 0)
                {
                    int bh = Math.max(3, (int) Math.round(c * 0.06));
                    int ins = Math.max(3, (int) Math.round(c * 0.09));
                    int bw = c - 2 * ins;
                    int by = c - ins - bh;
                    g.setColor(BAR_BG);
                    g.fillRoundRect(ins, by, bw, bh, bh, bh);
                    int fw = Math.max(bh, (int) Math.round(bw * Math.min(1, p)));
                    g.setPaint(new GradientPaint(0, by, new Color(0xffc257), 0, by + bh, BAR_FILL));
                    g.fillRoundRect(ins, by, fw, bh, bh, bh);
                }
            }

            // someone on the team is going for this tile: a small orange flag in the top-left corner
            if (badges && st != TileInfo.State.DONE && t.workingOn != null && !t.workingOn.isEmpty())
            {
                int fs = Math.max(5, Math.min(22, (int) Math.round(c * 0.13)));
                badge(g, inset, inset, fs, new Color(0xffb04a), BAR_FILL, new Color(0x2e1a05));
            }
        }
        finally
        {
            g.dispose();
        }
    }

    /** A round badge: dark ring, then a disc shaded top to bottom. */
    private void badge(Graphics2D g, int x, int y, int d, Color top, Color bottom, Color ring)
    {
        int e = Math.max(1, Math.min(u, d / 6));
        g.setColor(new Color(0, 0, 0, 90));
        g.fillOval(x - e, y - e + e, d + 2 * e, d + 2 * e);
        g.setColor(ring);
        g.fillOval(x - e, y - e, d + 2 * e, d + 2 * e);
        g.setPaint(new GradientPaint(0, y, top, 0, y + d, bottom));
        g.fillOval(x, y, d, d);
    }

    /**
     * The tile's picture, centred. Item sprites are pixel art: they are enlarged by whole numbers only, with no
     * smoothing, so every game pixel stays a sharp square. Anything else (an uploaded icon) is resampled smoothly.
     */
    private static void paintIcon(Graphics2D g, BufferedImage pic, int c, int raise)
    {
        int sw = pic.getWidth();
        int sh = pic.getHeight();
        double fit = c * 0.82 / Math.max(sw, sh);
        if (fit >= 1 && Math.max(sw, sh) <= 48)
        {
            int k = (int) fit;
            Object was = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(pic, (c - sw * k) / 2, (c - sh * k) / 2 - raise, sw * k, sh * k, null);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, was);
            return;
        }
        int tw = Math.max(1, (int) Math.round(sw * fit));
        int th = Math.max(1, (int) Math.round(sh * fit));
        g.drawImage(resample(pic, tw, th), (c - tw) / 2, (c - th) / 2 - raise, null);
    }

    /** Smooth resize: halves at a time while shrinking a lot (so nothing is skipped), then one bicubic step. */
    private static BufferedImage resample(BufferedImage src, int tw, int th)
    {
        BufferedImage cur = src;
        int w = src.getWidth();
        int h = src.getHeight();
        while (w / 2 >= tw && h / 2 >= th)
        {
            w /= 2;
            h /= 2;
            cur = scaled(cur, w, h, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        return w == tw && h == th ? cur : scaled(cur, tw, th, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    private static BufferedImage scaled(BufferedImage src, int w, int h, Object hint)
    {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    /** Text centred on a point by its inked shape (not its line box), with a soft shadow; shrunk if wider than maxW. */
    private void centredGlyphs(Graphics2D g, Font f, String str, Color colour, double cx, double cy, int maxW)
    {
        if (str == null || str.isEmpty()) return;
        java.awt.font.GlyphVector gv = f.createGlyphVector(g.getFontRenderContext(), str);
        Rectangle2D box = gv.getVisualBounds();
        if (box.getWidth() > maxW * 0.8 && box.getWidth() > 0)
        {
            f = f.deriveFont((float) (f.getSize2D() * maxW * 0.8 / box.getWidth()));
            gv = f.createGlyphVector(g.getFontRenderContext(), str);
            box = gv.getVisualBounds();
        }
        float x = (float) (cx - box.getCenterX());
        float y = (float) (cy - box.getCenterY());
        float drop = Math.max(1f, f.getSize2D() / 14f);
        g.setColor(SHADOW);
        g.drawGlyphVector(gv, x, y + drop);
        g.setColor(colour);
        g.drawGlyphVector(gv, x, y);
    }

    // ---------- hover label ----------

    /** A small name plate above the hovered tile (below it on the top row), kept inside the board. */
    private void drawHoverLabel(Graphics2D g, BoardLayout l, int cellX, int cellY, ApiModels.Tile t, double in, float boardAlpha)
    {
        if (in <= 0.02) return;
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.min(1, in) * boardAlpha));
        String shown = TileInfo.shownTitle(t);
        Font tf = PanelUi.headingFor(shown, fTip, fSmallBold.deriveFont(fTip.getSize2D()));
        FontMetrics tm = g.getFontMetrics(tf);
        FontMetrics sm = g.getFontMetrics(fTipSub);
        Rectangle b = l.board();
        int padX = px(10);
        int padY = px(7);
        int maxW = Math.min(px(270), b.width - px(16));
        String title = ellipsize(tm, shown, maxW - 2 * padX);
        String sub = TileInfo.isSecret(t) ? "Secret tile" : ForgeBingoPanel.stateText(t);
        String pts = TileInfo.isSecret(t) ? "" : points(TileInfo.worth(t));
        int ptsW = pts.isEmpty() ? 0 : sm.stringWidth(pts) + px(12);
        sub = ellipsize(sm, sub, maxW - 2 * padX - ptsW);
        int w = Math.max(tm.stringWidth(title), sm.stringWidth(sub) + ptsW) + 2 * padX;
        int titleBase = padY + (int) Math.round(tf.getSize2D() * 0.74);
        int subBase = titleBase + px(5) + (int) Math.round(fTipSub.getSize2D() * 0.78);
        int h = subBase + (int) Math.round(fTipSub.getSize2D() * 0.28) + padY;
        int c = l.cell;
        int x = Math.max(b.x + px(6), Math.min(b.x + b.width - px(6) - w, cellX + c / 2 - w / 2));
        int lift = (int) Math.round((1 - in) * px(5));
        int y = cellY - h - px(7) + lift;
        if (y < b.y + px(4)) y = cellY + c + px(7) - lift;
        int arc = px(8);
        // layered shadow, gold edge, dark plate
        for (int i = 3; i >= 1; i--)
        {
            g.setColor(new Color(0, 0, 0, 34));
            g.fillRoundRect(x - i * u, y - i * u + 2 * u, w + 2 * i * u, h + 2 * i * u, arc + 2 * i * u, arc + 2 * i * u);
        }
        g.setColor(GOLD_DIM);
        g.fillRoundRect(x, y, w, h, arc, arc);
        g.setPaint(new GradientPaint(0, y, new Color(0x332c26), 0, y + h, new Color(0x1f1b18)));
        g.fillRoundRect(x + u, y + u, w - 2 * u, h - 2 * u, Math.max(2, arc - 2 * u), Math.max(2, arc - 2 * u));
        text(g, tf, title, GOLD, x + padX, y + titleBase, 0, u);
        text(g, fTipSub, sub, ForgeBingoPanel.stateTextColour(t), x + padX, y + subBase, 0, u);
        if (!pts.isEmpty()) text(g, fTipSub, pts, MUTED, x + w - padX, y + subBase, 2, u);
    }

    // ---------- the tile card ----------

    private static final int CARD_PAD = 14;

    /** The tile card: sized to what it shows, centred on the board, popping in with a slight overshoot. */
    private void drawCard(Graphics2D g, BoardLayout l, ApiModels.Tile t, double progress, float boardAlpha)
    {
        Rectangle b = l.board();
        if (cardImg == null || cardTile != t || cardLayout != l) buildCard(g, l, t);
        Rectangle c = cardRect;
        int cm = px(24);
        double p = Math.max(0, Math.min(1, progress));
        double fade = BoardLayout.easeOut(p * 1.6);
        Composite base = g.getComposite();
        // the board behind dims while a card is open
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (fade * boardAlpha)));
        g.setColor(new Color(0, 0, 0, 130));
        int in = u;
        g.fillRoundRect(b.x + in, b.y + in, b.width - 2 * in, b.height - 2 * in, px(18) - 2 * in, px(18) - 2 * in);
        if (p >= 1)
        {
            g.drawImage(cardImg, c.x - cm, c.y - cm, null);
        }
        else
        {
            double scale = 0.9 + 0.1 * easeOutBack(p);
            AffineTransform home = g.getTransform();
            xf.setTransform(home);
            xf.translate(c.getCenterX(), c.getCenterY());
            xf.scale(scale, scale);
            xf.translate(c.x - cm - c.getCenterX(), c.y - cm - c.getCenterY());
            g.setTransform(xf);
            Object was = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(cardImg, 0, 0, null);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, was);
            g.setTransform(home);
        }
        g.setComposite(base);
    }

    /** Lays the card out and paints it (panel, shadow and text) into one image, reused until the tile or layout changes. */
    private void buildCard(Graphics2D ref, BoardLayout l, ApiModels.Tile t)
    {
        // the card may use the whole board (less a margin) so every task fits; it is only as tall as its content
        Rectangle b = l.board();
        int edge = px(10);
        int pad = px(CARD_PAD);
        Rectangle area = new Rectangle(b.x + edge, b.y + edge, b.width - 2 * edge, b.height - 2 * edge);
        int w = Math.min(area.width, px(320));
        int room = area.height - 2 * pad;
        boolean compact = cardContent(ref, t, 0, 0, w - 2 * pad, Integer.MAX_VALUE / 4, true, false) > room;
        int contentH = cardContent(ref, t, 0, 0, w - 2 * pad, room, true, compact);
        int h = Math.min(area.height, contentH + 2 * pad);
        int cx = b.x + b.width / 2;
        int cy = b.y + b.height / 2;
        Rectangle c = new Rectangle(cx - w / 2, cy - h / 2, w, h);
        c.y = Math.max(area.y, Math.min(area.y + area.height - h, c.y));

        int cm = px(24);
        int drop = px(7);
        BufferedImage img = new BufferedImage(w + 2 * cm, h + 2 * cm, BufferedImage.TYPE_INT_ARGB);
        paintPanel(img, cm, cm, w, h, px(9), u, CARD_TOP, CARD_BOTTOM, RIM_TOP, RIM_BOTTOM, cm - drop, drop, 190);
        Graphics2D g = img.createGraphics();
        quality(g);
        g.translate(cm, cm);
        // the tile's state colour as a slim band across the top
        Color[] col = faceColours(TileInfo.state(t));
        int bandH = Math.max(2, px(3));
        g.setPaint(new GradientPaint(0, 0, lighten(col[0], 40), w, 0, lighten(col[0], 10)));
        g.fillRoundRect(px(10), px(6), w - 2 * px(10), bandH, bandH, bandH);
        cardContent(g, t, pad, pad, w - 2 * pad, h - 2 * pad, false, compact);
        g.dispose();
        cardImg = img;
        cardTile = t;
        cardLayout = l;
        cardRect = c;
    }

    /**
     * Lays out (dry = true: only measures) the card's text: title, points + state, description, then each task with
     * its checkbox, progress bar and how it's tracked. compact = leave out the "how it's tracked" lines and keep each
     * task to one line, so a tile with many tasks still shows all of them. Returns the height used. All sizes are in
     * pixels at the board's scale.
     */
    private int cardContent(Graphics2D g, ApiModels.Tile t, int x, int top, int maxW, int maxH, boolean dry, boolean compact)
    {
        String shown = TileInfo.shownTitle(t);
        Font title = PanelUi.headingFor(shown, fCardTitle, fCount.deriveFont(fCardTitle.getSize2D()));
        FontMetrics bm = g.getFontMetrics(title);
        FontMetrics tm = g.getFontMetrics(fBody);
        FontMetrics sm = g.getFontMetrics(fSmall);
        int bottom = top + maxH;
        int y = top + px(4);
        boolean secret = TileInfo.isSecret(t);
        String pts = secret ? "" : points(TileInfo.worth(t));
        int pillW = secret ? 0 : g.getFontMetrics(fSmallBold).stringWidth(pts) + px(14);
        int pillH = px(17);

        int titleLine = (int) Math.round(title.getSize2D() * 1.22);
        int titleAscent = (int) Math.round(title.getSize2D() * 0.80);
        for (String ln : wrap(bm, shown, maxW - (secret ? 0 : pillW + px(8)), 2))
        {
            if (!dry) text(g, title, ln, GOLD, x, y + titleAscent, 0, u);
            y += titleLine;
        }
        if (!secret && !dry)
        {
            // points as a small pill on the right of the first title line
            int pxl = x + maxW - pillW;
            int pyl = top + px(4) + (titleLine - pillH) / 2 - px(1);
            g.setColor(GOLD_EDGE);
            g.fillRoundRect(pxl, pyl, pillW, pillH, pillH, pillH);
            g.setColor(new Color(0x17130f));
            g.fillRoundRect(pxl + u, pyl + u, pillW - 2 * u, pillH - 2 * u, pillH - 2 * u, pillH - 2 * u);
            FontMetrics pm = g.getFontMetrics(fSmallBold);
            text(g, fSmallBold, pts, GOLD_SOFT, pxl + pillW / 2, pyl + (pillH + pm.getAscent() - pm.getDescent()) / 2, 1, 0);
        }

        String state = ForgeBingoPanel.stateText(t);
        if (t.tasks != null && !t.completed && "any".equals(t.completionMode) && t.tasks.size() > 1) state += " (any one finishes it)";
        y = lines(g, fSmall, wrap(sm, state, maxW, 2), ForgeBingoPanel.stateTextColour(t), x, y + px(1), dry);
        if (t.description != null && !t.description.isEmpty())
        {
            y = lines(g, fSmall, wrap(sm, t.description, maxW, 3), MUTED, x, y + px(4), dry);
        }
        if (!t.completed && t.workingOn != null && !t.workingOn.isEmpty())
        {
            List<String> who = new ArrayList<>();
            for (ApiModels.WorkingOn w : t.workingOn) if (w.me) who.add(0, "You"); else who.add(w.name);
            y = lines(g, fSmall, wrap(sm, "Working on it: " + String.join(", ", who), maxW, 2), BAR_FILL, x, y + px(4), dry);
        }

        List<ApiModels.Task> tasks = t.tasks == null ? new ArrayList<>() : t.tasks;
        if (!tasks.isEmpty())
        {
            y += px(8);
            if (!dry)
            {
                g.setColor(new Color(0, 0, 0, 120));
                g.fillRect(x, y, maxW, u);
                g.setColor(new Color(255, 255, 255, 18));
                g.fillRect(x, y + u, maxW, u);
            }
            y += 2 * u + px(7);
        }
        int shownTasks = 0;
        int box = px(11);
        int indent = box + px(7);
        for (ApiModels.Task k : tasks)
        {
            boolean kDone = k.completed || t.completed;
            List<String> desc = wrap(tm, k.description == null ? "" : k.description, maxW - indent, compact ? 1 : 2);
            List<String> how = kDone || compact ? new ArrayList<>() : wrap(sm, ForgeBingoPanel.trackingText(k), maxW - indent, 2);
            // exactly what the drawing below uses (lines() advances ascent + descent per line)
            int tLine = tm.getAscent() + tm.getDescent();
            int sLine = sm.getAscent() + sm.getDescent();
            // "one of each" tasks list every item with its own tick instead of one bar
            List<com.forgebingo.core.ItemSlots.Slot> slots = kDone ? java.util.Collections.emptyList() : com.forgebingo.core.ItemSlots.of(k);
            boolean bar = slots.isEmpty() && k.requiredQty > 1;
            boolean pendingLine = slots.isEmpty() && k.requiredQty <= 1 && k.pendingQty > 0 && !kDone;
            int barH = px(13);
            int need = desc.size() * tLine + (bar ? px(4) + barH + px(2) : 0) + slots.size() * (sLine + px(2)) + (pendingLine ? sLine : 0)
                + px(1) + how.size() * sLine + px(compact ? 4 : 8);
            // keep room for the closing hint, plus the "+N more" line only when another task would follow this one
            boolean last = shownTasks == tasks.size() - 1;
            int reserve = sm.getHeight() + px(4) + (last ? 0 : sm.getHeight() + px(2));
            if (y + need > bottom - reserve && shownTasks > 0) break;
            if (!dry) drawCheckbox(g, x, y + (tLine - box) / 2, box, kDone);
            int tx = x + indent;
            y = lines(g, fBody, desc, kDone ? DONE_TICK : TEXT, tx, y, dry);
            if (!slots.isEmpty())
            {
                int sub = px(9);
                for (com.forgebingo.core.ItemSlots.Slot sl : slots)
                {
                    y += px(2);
                    String name = sl.itemIds.size() == 1 ? plugin.itemName(sl.itemIds.get(0)) : "Any one of " + sl.itemIds.size() + " items";
                    String count = sl.need > 1 ? sl.approved + "/" + sl.need : "";
                    Color c = sl.done() ? DONE_TICK : sl.pending > 0 ? PENDING : TEXT;
                    if (!dry)
                    {
                        drawCheckbox(g, tx + px(2), y + (sLine - sub) / 2, sub, sl.done());
                        int cw = count.isEmpty() ? 0 : sm.stringWidth(count) + px(6);
                        text(g, fSmall, ellipsize(sm, name + (sl.pending > 0 && !sl.done() ? " (waiting)" : ""), maxW - indent - sub - px(6) - cw), c,
                            tx + sub + px(8), y + sm.getAscent(), 0, u);
                        if (cw > 0) text(g, fSmall, count, MUTED, x + maxW, y + sm.getAscent(), 2, u);
                    }
                    y += sLine;
                }
            }
            else if (bar)
            {
                y += px(4);
                if (!dry) drawBar(g, tx, y, maxW - indent, barH, k);
                y += barH + px(2);
            }
            else if (pendingLine)
            {
                y = lines(g, fSmall, java.util.Collections.singletonList("Sent - waiting for a reviewer"), PENDING, tx, y, dry);
            }
            y = lines(g, fSmall, how, MUTED, tx, y + px(1), dry);
            y += px(compact ? 4 : 8);
            shownTasks++;
        }
        if (shownTasks < tasks.size())
        {
            y += px(2);
            if (!dry) text(g, fSmall, ellipsize(sm, "+" + (tasks.size() - shownTasks) + " more in the ForgeBingo side panel", maxW), TEXT, x, y + sm.getAscent(), 0, u);
            y += sm.getHeight();
        }
        y += px(4);
        String hint = "Click anywhere to close";
        if (!dry) text(g, fSmall, hint, FAINT, x + maxW, y + sm.getAscent(), 2, 0);
        y += sm.getHeight();
        return y - top;
    }

    private void drawCheckbox(Graphics2D g, int x, int y, int size, boolean done)
    {
        int arc = Math.max(2, px(3));
        g.setColor(done ? new Color(0x3fae48) : GOLD_DIM);
        g.fillRoundRect(x, y, size, size, arc, arc);
        g.setColor(done ? new Color(0x163d1a) : new Color(0x15120f));
        g.fillRoundRect(x + u, y + u, size - 2 * u, size - 2 * u, Math.max(1, arc - u), Math.max(1, arc - u));
        if (done)
        {
            g.setColor(DONE_TICK);
            g.setStroke(new BasicStroke(Math.max(1.4f, size * 0.15f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            path.reset();
            path.moveTo(x + size * 0.24, y + size * 0.52);
            path.lineTo(x + size * 0.42, y + size * 0.70);
            path.lineTo(x + size * 0.76, y + size * 0.30);
            g.draw(path);
        }
    }

    private void drawBar(Graphics2D g, int x, int y, int w, int h, ApiModels.Task k)
    {
        g.setColor(new Color(255, 255, 255, 14));
        g.fillRoundRect(x, y + u, w, h, h, h);
        g.setColor(new Color(0x120f0d));
        g.fillRoundRect(x, y, w, h, h, h);
        double p = Math.min(1, Math.max(0, k.approvedQty / (double) Math.max(1, k.requiredQty)));
        int fw = (int) Math.round(w * p);
        if (fw > 0)
        {
            Color c = k.completed ? BAR_DONE : BAR_FILL;
            g.setPaint(new GradientPaint(0, y, lighten(c, 36), 0, y + h, darken(c, 20)));
            g.fillRoundRect(x, y, Math.max(h, fw), h, h, h);
        }
        String txt = k.approvedQty + " / " + k.requiredQty + (k.pendingQty > 0 ? "  (+" + k.pendingQty + " pending)" : "");
        Font f = fSmallBold.deriveFont(fSmallBold.getSize2D() * 0.9f);
        FontMetrics fm = g.getFontMetrics(f);
        text(g, f, txt, Color.WHITE, x + w / 2, y + (h + fm.getAscent() - fm.getDescent()) / 2, 1, u);
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
            img = plugin.itemImage(itemId);
            if (img != null) icons.put(itemId, img);
        }
        return img;
    }

    // ---------- small helpers ----------

    /**
     * Smoothed text with a soft shadow under it. align: 0 = x is the left edge, 1 = the centre, 2 = the right edge.
     * drop = how far down the shadow sits (0 = none).
     */
    private static void text(Graphics2D g, Font f, String str, Color c, int x, int y, int align, int drop)
    {
        if (str == null || str.isEmpty()) return;
        g.setFont(f);
        int tx = align == 0 ? x : align == 1 ? x - g.getFontMetrics().stringWidth(str) / 2 : x - g.getFontMetrics().stringWidth(str);
        if (drop > 0)
        {
            g.setColor(SHADOW);
            g.drawString(str, tx, y + drop);
        }
        g.setColor(c);
        g.drawString(str, tx, y);
    }

    /** Draws (or, when dry, only measures) lines starting with their tops at y; returns the y under the last one. */
    private int lines(Graphics2D g, Font f, List<String> lines, Color c, int x, int y, boolean dry)
    {
        FontMetrics fm = g.getFontMetrics(f);
        for (String ln : lines)
        {
            y += fm.getAscent();
            if (!dry) text(g, f, ln, c, x, y, 0, u);
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
        double v = t - 1;
        return 1 + c3 * v * v * v + c1 * v * v;
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
        double v = Math.max(0, Math.min(1, t));
        return new Color((int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * v),
            (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * v),
            (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * v), a.getAlpha());
    }

    private static String ellipsize(FontMetrics fm, String str, int maxW)
    {
        if (str == null) return "";
        if (fm.stringWidth(str) <= maxW) return str;
        String dots = "...";
        int end = str.length();
        while (end > 0 && fm.stringWidth(str.substring(0, end) + dots) > maxW) end--;
        return str.substring(0, end).trim() + dots;
    }

    /** Word-wraps to at most maxLines lines, the last one cut with "..." when the text runs on. */
    private static List<String> wrap(FontMetrics fm, String str, int maxW, int maxLines)
    {
        List<String> out = new ArrayList<>();
        if (str == null || str.isEmpty()) return out;
        String[] words = str.split(" ");
        StringBuilder ln = new StringBuilder();
        int i = 0;
        while (i < words.length)
        {
            String candidate = ln.length() == 0 ? words[i] : ln + " " + words[i];
            if (fm.stringWidth(candidate) <= maxW || ln.length() == 0)
            {
                ln.setLength(0);
                ln.append(candidate);
                i++;
            }
            else
            {
                if (out.size() == maxLines - 1) break;
                out.add(ln.toString());
                ln.setLength(0);
            }
        }
        if (ln.length() > 0)
        {
            StringBuilder rest = new StringBuilder(ln);
            for (int j = i; j < words.length; j++) rest.append(' ').append(words[j]);
            out.add(ellipsize(fm, rest.toString(), maxW));
        }
        return out;
    }
}
