package com.forgebingo;

import com.forgebingo.core.Critter;
import com.forgebingo.core.CritterSprite;
import com.forgebingo.core.ImpSprite;
import com.forgebingo.core.Chexy;
import com.forgebingo.core.Pet;
import com.forgebingo.core.PetSprites;
import java.awt.AWTEvent;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Composite;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntFunction;
import net.runelite.api.gameval.ItemID;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.swing.JComponent;
import javax.swing.JLayer;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.plaf.LayerUI;

/**
 * The website's little imps, wandering over the side panel: they walk along the tops of the cards, hop between
 * them, climb the panel's edges and stop for a chat (a port of apps/web/src/features/fun/Imps.tsx).
 * Painted over the scrolling column by a JLayer, in the column's own coordinates, so they stay on the card they
 * stand on when you scroll. They never take clicks away from the panel. The timer only runs while the panel is
 * on screen and imps are switched on.
 */
final class PanelImps extends LayerUI<JComponent>
{
    /** client property: this component's top edge is somewhere an imp can stand */
    static final String PLATFORM = "forgebingo.impPlatform";

    private static final float SPEED = 26f;
    private static final float GRAVITY = 1400f;
    /** body size the physics uses (the sprite itself is 42 x 34 with room for hats and staffs) */
    private static final int BODY_W = 22;
    private static final int EDGE = 9;

    private enum Mode { WALK, IDLE, AIR, CHAT, CLIMB }

    private static final class Imp
    {
        float x, y, vx, vy;
        int dir = 1;
        Mode mode = Mode.AIR;
        float t, life, chatCd, squash, px, seekT, spark, sparkAge = 9f, bubbleT;
        int seek, side = 1, cdir = -1;
        String bubble = "";
        ImpSprite.Hat hat = ImpSprite.Hat.NONE;
        ImpSprite.Item item = ImpSprite.Item.NONE;
        boolean rare;
        /** which creature this is (the theme's cast), and which of its looks */
        Critter kind = Critter.IMP;
        int variant;
        /** where a flier is heading */
        float tx, ty;
        /** a rare visitor, and how long it still stays */
        boolean guest;
        float guestT;
        /** popping out (-1) or in (1), how far along (0..1), and who it becomes when it has popped out */
        int popDir;
        float pop;
        boolean nextGuest;
        float sparkX, sparkY;
        /** Chexy's own clock (its rings and comets), slowed for Calm */
        float wisp;
        Rectangle last;
    }

    /** The famous drops that rain down now and then (real item icons, asked of the client by id). */
    private static final int[] RAIN_ITEMS = {
        ItemID.TWISTED_BOW, ItemID.SOULREAPER, ItemID.INQUISITORS_MACE, ItemID.INQUISITORS_HELM,
        ItemID.ZARYTE_XBOW, ItemID.ACB, ItemID.DRAGONHUNTER_XBOW, ItemID.SCYTHE_OF_VITUR,
        ItemID.TUMEKENS_SHADOW, ItemID.OSMUMTENS_FANG, ItemID.ELDER_MAUL, ItemID.KODAI_WAND,
        ItemID.MASORI_MASK, ItemID.MASORI_BODY, ItemID.MASORI_CHAPS,
        ItemID.ANCESTRAL_HAT, ItemID.ANCESTRAL_ROBE_TOP, ItemID.ANCESTRAL_ROBE_BOTTOM,
        ItemID.TORVA_HELM, ItemID.OATHPLATE_HELM, ItemID.VOIDWAKER, ItemID.DRAGON_WARHAMMER,
        ItemID.DRAGON_CLAWS, ItemID.ABYSSAL_BLUDGEON, ItemID.NOXIOUS_HALBERD, ItemID.EYE_OF_AYAK,
        ItemID.SANGUINESTI_STAFF, ItemID.TOXIC_BLOWPIPE, ItemID.ASCENSION_CROSSBOWS, 34717 /* Obligator */,
    };
    private static final float RAIN_GRAVITY = 240f;
    private static final float RAIN_MAX_FALL = 170f;
    private static final int RAIN_MAX = 7;

    /** One falling item: centre x/y in the column's coordinates. */
    private static final class Drop
    {
        float x, y, vx, vy, rot, vrot, age;
        /** drawn bigger than its pixels (our own small pixel-art bits; item icons stay 1) */
        float scale = 1f;
        BufferedImage img;
        Rectangle last;
    }

    private final Random rnd = new Random();
    private final List<Drop> drops = new ArrayList<>();
    private final Map<Integer, BufferedImage> itemCache = new HashMap<>();
    private IntFunction<BufferedImage> itemImages;
    private boolean rain = true;
    /** the look in use: who walks here and what falls */
    private PanelTheme theme = PanelUi.theme;
    /** the theme's own moving touches: sand piles, tiny volcanoes */
    private final ThemeFx fx = new ThemeFx();
    private int lastMouseX;

    /** seconds until the theme next rolls for a rare visitor (the previews shorten it) */
    float rollT = PanelTheme.ROLL_SECONDS;
    /** the odds a roll brings a visitor; below 0 = the usual PanelTheme.ROLL_CHANCE (the previews set 1) */
    float rollChance = -1f;
    /** the odds a visitor is Chexy (the previews set 1) */
    float chexyChance = PanelTheme.CHEXY_CHANCE;
    private static final float POP_TIME = 0.45f;
    /** Calm: Chexy turns slower and sparkles less */
    private boolean calm;

    private boolean fxOn()
    {
        return ThemeFx.moving(theme) && PanelUi.animations && PanelUi.effects == ForgeBingoConfig.PanelEffects.FULL;
    }

    /** The "Theme" setting changed: everyone leaves and the new cast drops in. */
    void setTheme(PanelTheme t)
    {
        theme = t == null ? PanelTheme.CLASSIC : t;
        imps.clear();
        clearDrops();
        fx.reset();
        rollT = PanelTheme.ROLL_SECONDS;
        nextDrop = 2f;
        sync();
        if (layer != null) layer.repaint();
    }
    private float nextDrop = 5f;
    /** the item the mouse is carrying, and where on it the mouse took hold */
    private Drop held;
    private float holdDx, holdDy;
    private long holdNanos;
    private final MouseAdapter grab = new MouseAdapter()
    {
        @Override
        public void mousePressed(MouseEvent e)
        {
            Drop d = dropAt(e.getX(), e.getY());
            if (d == null)
            {
                syncGlass(false);
                return;
            }
            held = d;
            holdDx = d.x - e.getX();
            holdDy = d.y - e.getY();
            holdNanos = System.nanoTime();
            d.vx = 0;
            d.vy = 0;
        }

        @Override
        public void mouseDragged(MouseEvent e)
        {
            Drop d = held;
            if (d == null) return;
            long now = System.nanoTime();
            float dt = Math.max(0.004f, (now - holdNanos) / 1e9f);
            holdNanos = now;
            float nx = e.getX() + holdDx;
            float ny = e.getY() + holdDy;
            // remember how fast it is being carried, so letting go throws it
            d.vx += ((nx - d.x) / dt - d.vx) * 0.5f;
            d.vy += ((ny - d.y) / dt - d.vy) * 0.5f;
            d.x = nx;
            d.y = ny;
        }

        @Override
        public void mouseReleased(MouseEvent e)
        {
            Drop d = held;
            held = null;
            if (d != null)
            {
                if (System.nanoTime() - holdNanos > 120_000_000L)
                {
                    d.vx = 0;
                    d.vy = 0;
                }
                d.vx = Math.max(-420, Math.min(420, d.vx));
                d.vy = Math.max(-520, Math.min(420, d.vy));
                d.vrot = d.vx / 90f + rnd(-1.2f, 1.2f);
            }
            syncGlass(dropAt(e.getX(), e.getY()) != null);
        }

        @Override
        public void mouseExited(MouseEvent e)
        {
            if (held == null) syncGlass(false);
        }
    };
    private final List<Imp> imps = new ArrayList<>();
    private final List<int[]> plats = new ArrayList<>(); // left, right, y
    private final Timer timer = new Timer(33, e -> frame());
    private JLayer<? extends JComponent> layer;
    private boolean enabled = true;
    private long lastNanos;
    private float sincePlats = 99f;
    private float clock;

    private final HierarchyListener showing = e -> {
        if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) sync();
    };

    @Override
    @SuppressWarnings("unchecked")
    public void installUI(JComponent c)
    {
        super.installUI(c);
        layer = (JLayer<? extends JComponent>) c;
        layer.setLayerEventMask(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
        layer.addHierarchyListener(showing);
        // the layer's glass pane is only shown while the mouse is on a falling item, so catching one never
        // clicks the button under it and the rest of the panel keeps its clicks
        layer.getGlassPane().addMouseListener(grab);
        layer.getGlassPane().addMouseMotionListener(grab);
        layer.getGlassPane().setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        sync();
    }

    @Override
    public void uninstallUI(JComponent c)
    {
        timer.stop();
        if (layer != null)
        {
            layer.setLayerEventMask(0);
            layer.removeHierarchyListener(showing);
            layer.getGlassPane().removeMouseListener(grab);
            layer.getGlassPane().removeMouseMotionListener(grab);
            layer.getGlassPane().setVisible(false);
        }
        held = null;
        layer = null;
        super.uninstallUI(c);
    }

    /** Calm draws at half speed (about 15 frames a second); Full at about 30. */
    void setCalm(boolean calm)
    {
        this.calm = calm;
        timer.setDelay(calm ? 66 : 33);
    }

    void setEnabled(boolean on)
    {
        enabled = on;
        if (!on) imps.clear();
        sync();
        if (layer != null) layer.repaint();
    }

    /** Where the falling items' icons come from (the client's item sprites); without it nothing rains. */
    void setItemImages(IntFunction<BufferedImage> source)
    {
        itemImages = source;
    }

    /** Switches the item rain on or off (the "Side panel item rain" setting). */
    void setRain(boolean on)
    {
        rain = on;
        if (!on) clearDrops();
        sync();
        if (layer != null) layer.repaint();
    }

    private void clearDrops()
    {
        drops.clear();
        held = null;
        syncGlass(false);
    }

    /** Stops everything for good (plugin shut down). */
    void dispose()
    {
        enabled = false;
        rain = false;
        timer.stop();
        imps.clear();
        clearDrops();
        itemCache.clear();
    }

    private void sync()
    {
        boolean run = (enabled || rain || fxOn()) && layer != null && layer.isShowing();
        if (run && !timer.isRunning())
        {
            lastNanos = 0;
            timer.start();
        }
        else if (!run && timer.isRunning())
        {
            timer.stop();
        }
    }

    // ------------------------------------------------------------------ world

    private float rnd(float a, float b)
    {
        return a + rnd.nextFloat() * (b - a);
    }

    private int width()
    {
        return Math.max(60, layer.getWidth());
    }

    private int height()
    {
        return Math.max(60, layer.getHeight());
    }

    /** Most imps are plain; a few wear hats and the rarest carry famous items (same odds as the website). */
    private void rollLook(Imp m)
    {
        m.hat = ImpSprite.Hat.NONE;
        m.item = ImpSprite.Item.NONE;
        float r = rnd.nextFloat();
        if (r < 1 / 50f) m.hat = ImpSprite.Hat.CHEF;
        else if (r < 2 / 50f) m.hat = ImpSprite.Hat.WIZARD;
        else if (r < 2 / 50f + 1 / 400f) m.item = ImpSprite.Item.FIRE_STAFF;
        else if (r < 2 / 50f + 2 / 400f) m.item = ImpSprite.Item.AYAK;
        else if (r < 2 / 50f + 3 / 400f) m.item = ImpSprite.Item.SOULREAPER;
        else if (r < 2 / 50f + 3 / 400f + 1 / 200f) m.hat = ImpSprite.Hat.SANTA;
        else if (r < 2 / 50f + 3 / 400f + 2 / 200f) m.hat = ImpSprite.Hat.BUNNY;
        m.rare = m.hat != ImpSprite.Hat.NONE || m.item != ImpSprite.Item.NONE;
    }

    /** Drops a fresh imp in from above the part of the panel you can see. */
    private void spawn(Imp m)
    {
        Rectangle view = layer.getVisibleRect();
        m.x = rnd(20, Math.max(40, width() - 20));
        m.y = view.y - rnd(30, 110);
        m.vx = 0;
        m.vy = 0;
        m.dir = rnd.nextBoolean() ? 1 : -1;
        m.mode = Mode.AIR;
        m.t = rnd(1, 3);
        m.seek = 0;
        m.seekT = 0;
        m.chatCd = rnd(2, 6);
        m.squash = 0;
        m.life = rnd(150, 400);
        m.spark = rnd(2, 6);
        m.bubble = "";
        m.guest = false;
        m.guestT = 0;
        m.popDir = 0;
        m.pop = 0;
        dress(m, false);
    }

    /**
     * Who this walker is. A regular is one of the theme's everyday cast (the one there are fewest of right now);
     * a visitor is one of its rare ones. Returns false when the theme has no visitor to offer.
     */
    private boolean dress(Imp m, boolean visitor)
    {
        m.hat = ImpSprite.Hat.NONE;
        m.item = ImpSprite.Item.NONE;
        m.rare = false;
        Critter[] cast = theme.cast;
        Critter pick = cast[0];
        if (visitor && rnd.nextFloat() < chexyChance)
        {
            // Chexy can visit any theme
            m.kind = Critter.CHEXY;
            m.variant = 0;
            m.wisp = rnd(0, 20);
            return true;
        }
        if (visitor && theme == PanelTheme.BARROWS)
        {
            // the six brothers take turns: one that is not out right now
            List<Critter> away = new ArrayList<>();
            for (Critter c : cast)
            {
                boolean here = false;
                for (Imp o : imps) if (o.kind == c) here = true;
                if (!here) away.add(c);
            }
            if (away.isEmpty()) return false;
            pick = away.get(rnd.nextInt(away.size()));
        }
        else if (visitor && theme.rare().length > 0)
        {
            Critter[] rare = theme.rare();
            pick = rare[rnd.nextInt(rare.length)];
        }
        else
        {
            int fewest = Integer.MAX_VALUE;
            int start = rnd.nextInt(cast.length);
            for (int i = 0; i < cast.length; i++)
            {
                Critter c = cast[(start + i) % cast.length];
                int n = 0;
                for (Imp o : imps) if (o != m && o.kind == c) n++;
                if (n < fewest) { fewest = n; pick = c; }
            }
        }
        if (pick == Critter.PET)
        {
            // everyday pets are the common and uncommon ones; a visitor is a rare, epic or legendary one
            java.util.function.Predicate<Pet> tier = p -> PetSprites.drawn(p)
                && (p.rarity.ordinal() >= Pet.Rarity.RARE.ordinal()) == visitor;
            Pet pet = null;
            for (int tries = 0; tries < 6; tries++)
            {
                pet = Pet.pick(rnd.nextDouble(), tier);
                if (pet == null) break;
                boolean here = false;
                for (Imp o : imps) if (o != m && o.kind == Critter.PET && o.variant == pet.ordinal()) here = true;
                if (!here) break;
            }
            if (pet == null && visitor) return false;
            m.kind = pick;
            m.variant = pet == null ? 0 : pet.ordinal();
            return true;
        }
        m.kind = pick;
        m.variant = rnd.nextInt(pick.variants);
        if (pick == Critter.IMP && visitor)
        {
            // the Classic visitor: an imp in a hat or carrying something famous
            for (int tries = 0; tries < 400 && !m.rare; tries++) rollLook(m);
            if (!m.rare)
            {
                m.hat = ImpSprite.Hat.WIZARD;
                m.rare = true;
            }
        }
        return true;
    }

    /** The five-minute roll: maybe one regular pops out and a rare visitor pops in for a little while. */
    private void roll()
    {
        for (Imp o : imps) if (o.guest || o.popDir != 0) return; // one visitor at a time
        float chance = theme == PanelTheme.BARROWS ? 1f : rollChance >= 0 ? rollChance : PanelTheme.ROLL_CHANCE;
        if (rnd.nextFloat() >= chance) return;
        List<Imp> ok = new ArrayList<>();
        for (Imp o : imps) if (o.mode != Mode.AIR || o.kind.flies()) ok.add(o);
        if (ok.isEmpty()) return;
        Imp m = ok.get(rnd.nextInt(ok.size()));
        m.nextGuest = true;
        m.popDir = -1;
        m.pop = 0;
    }

    /** Pops play out; a visitor whose time is up pops out again and the regular comes back. */
    private void stepVisitors(float dt)
    {
        rollT -= dt;
        if (rollT <= 0)
        {
            rollT = PanelTheme.ROLL_SECONDS;
            roll();
        }
        for (Imp m : imps)
        {
            if (m.popDir != 0)
            {
                m.pop += dt / POP_TIME;
                if (m.pop < 1) continue;
                if (m.popDir == 1)
                {
                    m.popDir = 0;
                    continue;
                }
                // out of sight: change who it is, then pop back in
                boolean came = dress(m, m.nextGuest);
                if (!came) dress(m, false);
                // Barrows brothers just take turns; everywhere else the visitor leaves again
                // (Chexy is a visitor there too, and leaves again)
                m.guest = came && m.nextGuest && (theme != PanelTheme.BARROWS || m.kind == Critter.CHEXY);
                m.guestT = m.guest ? rnd(PanelTheme.VISIT_MIN, PanelTheme.VISIT_MAX) : 0;
                m.life = Math.max(m.life, PanelTheme.VISIT_MAX + 30f);
                m.popDir = 1;
                m.pop = 0;
            }
            else if (m.guest)
            {
                m.guestT -= dt;
                if (m.guestT <= 0)
                {
                    m.guest = false;
                    m.nextGuest = false;
                    m.popDir = -1;
                    m.pop = 0;
                }
            }
        }
    }

    private void refreshPlatforms()
    {
        plats.clear();
        JComponent view = layer.getView();
        int vw = width();
        if (view != null) collect(view, view, vw, 0);
        plats.add(new int[] {6, vw - 6, height() - 2});
    }

    private void collect(Container root, Container parent, int vw, int depth)
    {
        if (depth > 6 || plats.size() > 200) return;
        for (Component c : parent.getComponents())
        {
            if (!c.isVisible() || c.getWidth() <= 0 || c.getHeight() <= 0) continue;
            if (c instanceof JComponent && Boolean.TRUE.equals(((JComponent) c).getClientProperty(PLATFORM)))
            {
                Rectangle r = SwingUtilities.convertRectangle(parent, c.getBounds(), root);
                int l = Math.max(r.x, 6);
                int rr = Math.min(r.x + r.width, vw - 6);
                // the fourth number says whether a still prop may stand here: only on a card, and not right under
                // buttons or a drop-down, whose text it would cover
                int still = c instanceof PanelUi.Card && !controlAbove(parent, c) ? 1 : 0;
                if (rr - l >= 30 && r.height >= 8 && r.y >= 14) plats.add(new int[] {l, rr, r.y, still});
            }
            if (c instanceof Container) collect(root, (Container) c, vw, depth + 1);
        }
    }

    /** Is the thing sitting just above this component (in the same column) a button or drop-down, or holding one? */
    private static boolean controlAbove(Container parent, Component c)
    {
        Component above = null;
        for (Component k : parent.getComponents())
        {
            if (k == c || !k.isVisible() || k.getHeight() < 12) continue;
            int bottom = k.getY() + k.getHeight();
            if (bottom <= c.getY() + 2 && (above == null || bottom > above.getY() + above.getHeight())) above = k;
        }
        // first in its own group: look at what is above the group instead
        if (above == null) return parent.getParent() != null && c.getY() < 30 && controlAbove(parent.getParent(), parent);
        return c.getY() - (above.getY() + above.getHeight()) < 30 && holdsControl(above, 0);
    }

    private static boolean holdsControl(Component c, int depth)
    {
        if (c instanceof javax.swing.AbstractButton || c instanceof javax.swing.JComboBox) return true;
        if (depth > 4 || !(c instanceof Container)) return false;
        for (Component k : ((Container) c).getComponents()) if (k.isVisible() && holdsControl(k, depth + 1)) return true;
        return false;
    }

    private int[] support(float x, float y)
    {
        for (int[] p : plats) if (x >= p[0] - 3 && x <= p[1] + 3 && Math.abs(p[2] - y) <= 8) return p;
        return null;
    }

    private void launch(Imp m, float vx, float vy)
    {
        m.mode = Mode.AIR;
        m.vx = vx;
        m.vy = vy;
        m.squash = 1;
    }

    private boolean jumpToNearby(Imp m)
    {
        List<int[]> cands = new ArrayList<>();
        for (int[] p : plats)
        {
            float cx = Math.min(Math.max(m.x, p[0]), p[1]);
            float dy = m.y - p[2];
            if (Math.abs(cx - m.x) < 200 && dy < 130 && dy > -190 && Math.abs(dy) > 3) cands.add(p);
        }
        if (cands.isEmpty()) return false;
        int[] p = cands.get(rnd.nextInt(cands.size()));
        float tx = rnd(p[0] + 4, Math.max(p[0] + 5, p[1] - 4));
        float up = m.y - p[2];
        float vy0 = -(float) Math.sqrt(2 * GRAVITY * (Math.max(0, up) + 24));
        float t = (float) ((-vy0 + Math.sqrt(vy0 * vy0 - 2 * GRAVITY * up)) / GRAVITY);
        float vx = Math.max(-280, Math.min(280, (tx - m.x) / Math.max(0.05f, t)));
        m.dir = vx < 0 ? -1 : 1;
        launch(m, vx, vy0);
        return true;
    }

    private void startClimb(Imp m, int side)
    {
        Rectangle view = layer.getVisibleRect();
        m.mode = Mode.CLIMB;
        m.side = side;
        m.cdir = m.y - view.y > view.height * 0.5f ? -1 : 1;
        m.t = rnd(2.5f, 5);
        m.seek = 0;
    }

    /** A flier (Xarpus, Vespula) never lands: it drifts from spot to spot over the part of the panel you can see. */
    private void fly(Imp m, float dt, int vw)
    {
        Rectangle view = layer.getVisibleRect();
        if (m.life <= 0 && m.y < view.y - 50)
        {
            spawn(m);
            return;
        }
        m.t -= dt;
        boolean lost = m.ty < view.y - 80 || m.ty > view.y + view.height + 40;
        if (m.mode != Mode.WALK || m.t <= 0 || (lost && m.life > 0))
        {
            // a new spot to head for; when its time is up it leaves over the top
            m.mode = Mode.WALK;
            m.t = rnd(2.5f, 6f);
            m.tx = rnd(26, Math.max(40, vw - 26));
            m.ty = m.life <= 0 ? view.y - 90 : view.y + rnd(36, Math.max(60, view.height - 40));
        }
        float dx = m.tx - m.x;
        float dy = m.ty - m.y;
        float d = (float) Math.max(1.0, Math.sqrt(dx * dx + dy * dy));
        if (m.kind == Critter.CHEXY)
        {
            // the wisp glides: it eases into speed and out again, and swings through gentle curves on the way
            m.wisp += dt * (calm ? 0.55f : 1f);
            float speed = 40f * Math.min(1f, d / 46f) + 6f;
            float swirl = (float) Math.sin(m.wisp * 0.9f) * 0.55f;
            float wx = dx / d * (float) Math.cos(swirl) - dy / d * (float) Math.sin(swirl);
            float wy = dx / d * (float) Math.sin(swirl) + dy / d * (float) Math.cos(swirl);
            m.vx += (wx * speed - m.vx) * Math.min(1, dt * 1.1f);
            m.vy += (wy * speed - m.vy) * Math.min(1, dt * 1.1f);
            m.x += m.vx * dt;
            m.y += m.vy * dt;
            // stays clear of the side edges (and the scroll bar), its glow included
            m.x = Math.max(28, Math.min(vw - 30, m.x));
            return;
        }
        float speed = d < 20 ? 14f : 46f;
        m.vx += (dx / d * speed - m.vx) * Math.min(1, dt * 1.6f);
        m.vy += (dy / d * speed - m.vy) * Math.min(1, dt * 1.6f);
        m.x += m.vx * dt;
        m.y += m.vy * dt + (float) Math.sin(clock * 4.2f + m.tx) * 7f * dt;
        if (Math.abs(m.vx) > 4) m.dir = m.vx > 0 ? 1 : -1;
        // the wings beat about three times a second
        m.px += dt * 6.5f;
    }

    private void step(Imp m, float dt)
    {
        int vw = width();
        int docH = height();
        m.squash += (0 - m.squash) * Math.min(1, dt * 9);
        m.life -= dt;
        m.chatCd -= dt;
        m.sparkAge += dt;
        if (m.bubbleT > 0)
        {
            m.bubbleT -= dt;
            if (m.bubbleT <= 0 && m.mode != Mode.CHAT) m.bubble = "";
        }
        if (m.seekT > 0)
        {
            m.seekT -= dt;
            if (m.seekT <= 0) m.seek = 0;
        }
        if (m.kind.flies())
        {
            fly(m, dt, vw);
            return;
        }
        if (m.mode == Mode.WALK || m.mode == Mode.IDLE)
        {
            int[] p = support(m.x, m.y);
            if (p == null)
            {
                m.mode = Mode.AIR;
                m.vx = 0;
                m.vy = 0;
                return;
            }
            m.y = p[2];
            m.t -= dt;
            if (m.mode == Mode.IDLE)
            {
                if (m.t <= 0)
                {
                    m.mode = Mode.WALK;
                    m.t = rnd(1.5f, 4);
                }
                return;
            }
            m.px += dt * 8;
            float nx = m.x + m.dir * SPEED * dt;
            if (nx > p[1] - 3 || nx < p[0] + 3)
            {
                boolean atEdge = nx > p[1] - 3 ? p[1] >= vw - 6.5f : p[0] <= 6.5f;
                if (m.seek == m.dir && atEdge && m.kind != Critter.ZUK)
                {
                    startClimb(m, m.dir);
                    return;
                }
                if (m.seek == m.dir)
                {
                    launch(m, m.dir * 110, -220);
                    return;
                }
                if (rnd.nextFloat() < 0.45f && jumpToNearby(m)) return;
                m.dir = -m.dir;
                return;
            }
            m.x = nx;
            if (m.t <= 0)
            {
                float r = rnd.nextFloat();
                m.t = rnd(1.5f, 4);
                if (r < 0.2f) launch(m, m.dir * 30, -260);
                else if (r < 0.42f)
                {
                    if (!jumpToNearby(m)) m.dir = -m.dir;
                }
                else if (r < 0.52f)
                {
                    m.seek = rnd.nextBoolean() ? -1 : 1;
                    m.dir = m.seek;
                    m.seekT = 14;
                }
                else if (r < 0.66f) m.dir = -m.dir;
                else
                {
                    m.mode = Mode.IDLE;
                    m.t = rnd(0.8f, 2.6f);
                }
            }
            if (m.life <= 0) spawn(m);
        }
        else if (m.mode == Mode.AIR)
        {
            m.vy += GRAVITY * dt;
            float py = m.y;
            m.x += m.vx * dt;
            m.y += m.vy * dt;
            if (m.x <= EDGE || m.x >= vw - EDGE)
            {
                m.x = Math.min(Math.max(m.x, EDGE), vw - EDGE);
                if (m.seek != 0 && m.y > 40 && m.y < docH - 14 && m.kind != Critter.ZUK)
                {
                    startClimb(m, m.x < vw / 2f ? -1 : 1);
                    return;
                }
                m.vx = -m.vx * 0.5f;
            }
            if (m.vy > 0)
            {
                for (int[] p : plats)
                {
                    if (m.x >= p[0] - 2 && m.x <= p[1] + 2 && py <= p[2] + 1 && m.y >= p[2])
                    {
                        m.y = p[2];
                        m.vx = 0;
                        m.vy = 0;
                        m.mode = Mode.WALK;
                        m.t = rnd(1, 3);
                        m.squash = -1;
                        break;
                    }
                }
            }
            if (m.y > docH + 30)
            {
                m.y = docH - 2;
                m.mode = Mode.WALK;
                m.vx = 0;
                m.vy = 0;
            }
        }
        else if (m.mode == Mode.CLIMB)
        {
            m.x = m.side == -1 ? EDGE : vw - EDGE;
            m.y += m.cdir * 22 * dt;
            m.px += dt * 8;
            m.t -= dt;
            if (m.y < 40) m.cdir = 1;
            if (m.y > docH - 4)
            {
                m.y = docH - 2;
                m.t = 0;
            }
            if (m.t <= 0)
            {
                m.dir = -m.side;
                launch(m, -m.side * 100, -300);
                m.t = rnd(1, 3);
            }
        }
        else
        {
            m.t -= dt;
            if (m.t <= 0)
            {
                m.mode = Mode.WALK;
                m.t = rnd(1, 3);
                m.chatCd = rnd(6, 12);
                m.dir = rnd.nextBoolean() ? 1 : -1;
                m.bubble = "";
            }
        }
    }

    private void frame()
    {
        if (layer == null || !layer.isShowing())
        {
            sync();
            return;
        }
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.033f : Math.min((now - lastNanos) / 1e9f, 0.05f);
        lastNanos = now;
        advance(dt);
    }

    /** One step of the imps' world (the timer calls this; the offline preview calls it directly). */
    void advance(float dt)
    {
        boolean fxNow = fxOn();
        if (layer == null || !(enabled || rain || fxNow)) return;
        clock += dt;
        if (rain) stepRain(dt);
        if (!enabled && !fxNow) return;
        sincePlats += dt;
        if (sincePlats > 1.2f)
        {
            sincePlats = 0;
            refreshPlatforms();
        }
        if (fxNow)
        {
            Rectangle view = layer.getVisibleRect();
            fx.step(theme, dt, plats, view, width(), rnd);
            layer.repaint(view);
        }
        else if (!fx.isEmpty())
        {
            fx.reset();
            layer.repaint();
        }
        if (!enabled) return;
        while (imps.size() > theme.count) imps.remove(imps.size() - 1);
        while (imps.size() < theme.count)
        {
            Imp m = new Imp();
            spawn(m);
            imps.add(m);
        }
        for (Imp m : imps) step(m, dt);
        stepVisitors(dt);
        // hanging out: two imps that meet stop for a chat
        for (int i = 0; i < imps.size(); i++)
        {
            Imp a = imps.get(i);
            if ((a.mode != Mode.WALK && a.mode != Mode.IDLE) || a.chatCd > 0 || a.kind.flies()) continue;
            for (int j = i + 1; j < imps.size(); j++)
            {
                Imp b = imps.get(j);
                if ((b.mode != Mode.WALK && b.mode != Mode.IDLE) || b.chatCd > 0 || b.kind.flies()) continue;
                if (Math.abs(a.x - b.x) < 26 && Math.abs(a.y - b.y) < 4 && rnd.nextFloat() < dt * 1.5f)
                {
                    float d = rnd(1.2f, 3);
                    a.mode = Mode.CHAT;
                    b.mode = Mode.CHAT;
                    a.t = d;
                    b.t = d;
                    a.dir = a.x <= b.x ? 1 : -1;
                    b.dir = -a.dir;
                    a.bubble = rnd.nextBoolean() ? "heart" : "...";
                    b.bubble = rnd.nextBoolean() ? "note" : "...";
                    break;
                }
            }
        }
        for (Imp m : imps)
        {
            if (m.rare)
            {
                m.spark -= dt;
                if (m.spark <= 0)
                {
                    m.spark = rnd(2.5f, 5);
                    m.sparkAge = 0;
                    m.sparkX = rnd(-12, 10);
                    m.sparkY = rnd(-26, -6);
                }
            }
            Rectangle r = new Rectangle(Math.round(m.x) - 30, Math.round(m.y) - 50, 60, 58);
            layer.repaint(m.last == null ? r : r.union(m.last));
            m.last = r;
        }
    }

    // ------------------------------------------------------------------ item rain

    private BufferedImage itemImage(int id)
    {
        BufferedImage img = itemCache.get(id);
        if (img == null && itemImages != null)
        {
            try
            {
                img = itemImages.apply(id);
            }
            catch (RuntimeException | AssertionError e)
            {
                img = null; // the client is not ready to hand out item sprites yet
            }
            if (img != null) itemCache.put(id, img);
        }
        return img;
    }

    private Drop newDrop(float x, float y, float vx, float vy)
    {
        if (drops.size() >= RAIN_MAX) return null;
        BufferedImage img = null;
        float scale = 1f;
        // a themed look mixes its own small falling bits (crystals, embers, ...) in with the famous items
        Critter.Fall[] falls = theme.falls;
        if (falls.length > 0 && rnd.nextFloat() < (theme == PanelTheme.BARROWS || theme == PanelTheme.INFERNO ? 0.4f : 0.6f))
        {
            img = CritterSprite.fall(falls[rnd.nextInt(falls.length)], rnd.nextInt(2));
            scale = 1.5f;
        }
        if (img == null)
        {
            int[] items = theme.rainItems != null ? theme.rainItems : RAIN_ITEMS;
            img = itemImage(items[rnd.nextInt(items.length)]);
            scale = 1f;
        }
        if (img == null) return null;
        Drop d = new Drop();
        d.img = img;
        d.scale = scale;
        d.x = x;
        d.y = y;
        d.vx = vx;
        d.vy = vy;
        d.rot = rnd(-0.5f, 0.5f);
        d.vrot = rnd(-1.6f, 1.6f);
        drops.add(d);
        return d;
    }

    /** Now and then: one item drifting down, a small shower, or one lobbed across from the side. */
    private void rainSome()
    {
        Rectangle view = layer.getVisibleRect();
        int vw = width();
        float r = rnd.nextFloat();
        if (r < 0.14f)
        {
            int n = 3 + rnd.nextInt(3);
            for (int i = 0; i < n; i++) newDrop(rnd(22, Math.max(30, vw - 22)), view.y - rnd(24, 190), rnd(-12, 12), rnd(20, 70));
        }
        else if (r < 0.36f)
        {
            boolean fromLeft = rnd.nextBoolean();
            float y = view.y + rnd(0.15f, 0.6f) * view.height;
            Drop d = newDrop(fromLeft ? -22 : vw + 22, y, (fromLeft ? 1 : -1) * rnd(110, 190), -rnd(120, 230));
            if (d != null) d.vrot = (fromLeft ? 1 : -1) * rnd(3, 6);
        }
        else
        {
            newDrop(rnd(22, Math.max(30, vw - 22)), view.y - rnd(24, 60), rnd(-14, 14), rnd(20, 60));
        }
    }

    private void stepRain(float dt)
    {
        nextDrop -= dt;
        if (nextDrop <= 0)
        {
            nextDrop = theme.falls.length > 0 ? rnd(4, 11) : rnd(7, 18);
            rainSome();
        }
        if (drops.isEmpty()) return;
        Rectangle view = layer.getVisibleRect();
        int vw = width();
        for (int i = drops.size() - 1; i >= 0; i--)
        {
            Drop d = drops.get(i);
            d.age += dt;
            if (d != held)
            {
                d.vy = Math.min(d.vy + RAIN_GRAVITY * dt, RAIN_MAX_FALL);
                d.vx -= d.vx * Math.min(1, dt * 0.5f);
                d.x += d.vx * dt;
                d.y += d.vy * dt;
                d.rot += d.vrot * dt;
            }
            Rectangle r = new Rectangle(Math.round(d.x) - 30, Math.round(d.y) - 30, 60, 60);
            layer.repaint(d.last == null ? r : r.union(d.last));
            d.last = r;
            // gone once it has fallen out of the bottom of what you can see, or flown off a side
            boolean out = d.y > view.y + view.height + 34 || d.x < -40 || d.x > vw + 40 || d.y < view.y - 400;
            if (out && d != held) drops.remove(i);
        }
    }

    private Drop dropAt(float px, float py)
    {
        for (int i = drops.size() - 1; i >= 0; i--)
        {
            Drop d = drops.get(i);
            if (Math.abs(px - d.x) <= 17 && Math.abs(py - d.y) <= 16) return d;
        }
        return null;
    }

    private void syncGlass(boolean want)
    {
        if (layer == null) return;
        Component glass = layer.getGlassPane();
        boolean show = rain && (want || held != null);
        if (glass.isVisible() != show) glass.setVisible(show);
    }

    @Override
    protected void processMouseMotionEvent(MouseEvent e, JLayer<? extends JComponent> l)
    {
        if (e.getID() == MouseEvent.MOUSE_MOVED && fxOn())
        {
            // brushing a sand pile with the mouse knocks it away
            Point at = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), l);
            fx.mouseAt(at.x, at.y, at.x - lastMouseX, rnd);
            lastMouseX = at.x;
        }
        if (!rain || held != null || e.getID() != MouseEvent.MOUSE_MOVED) return;
        if (drops.isEmpty())
        {
            syncGlass(false);
            return;
        }
        Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), l);
        syncGlass(dropAt(p.x, p.y) != null);
    }

    private void paintDrop(Graphics2D g, Drop d)
    {
        AffineTransform old = g.getTransform();
        Composite oldC = g.getComposite();
        float a = Math.min(1f, d.age / 0.35f);
        if (a < 1f) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
        g.translate(d.x, d.y);
        g.rotate(d.rot);
        if (d == held) g.scale(1.15, 1.15);
        if (d.scale != 1f) g.scale(d.scale, d.scale);
        g.drawImage(d.img, -d.img.getWidth() / 2, -d.img.getHeight() / 2, null);
        g.setTransform(old);
        g.setComposite(oldC);
    }

    // ------------------------------------------------------------------ input (a poke makes an imp jump)

    @Override
    protected void processMouseEvent(MouseEvent e, JLayer<? extends JComponent> l)
    {
        if (e.getID() != MouseEvent.MOUSE_PRESSED || imps.isEmpty()) return;
        if (e.getComponent() == l.getGlassPane()) return; // that press caught a falling item
        Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), l);
        for (Imp m : imps)
        {
            if (m.mode == Mode.AIR) continue;
            if (Math.abs(p.x - m.x) <= BODY_W / 2 + 2 && p.y <= m.y + 1 && p.y >= m.y - 22)
            {
                m.bubble = "!";
                m.bubbleT = 0.9f;
                m.dir = p.x < m.x ? 1 : -1;
                if (m.kind.variants > 1)
                {
                    // a poke changes its colour (the crab's crystal), with a small hop
                    m.variant = (m.variant + 1) % m.kind.variants;
                    launch(m, m.dir * rnd(10, 30), -rnd(150, 200));
                    return;
                }
                launch(m, m.dir * rnd(40, 90), -rnd(280, 360));
                return;
            }
        }
    }

    // ------------------------------------------------------------------ painting

    @Override
    public void paint(Graphics g, JComponent c)
    {
        super.paint(g, c);
        boolean showImps = enabled && !imps.isEmpty();
        boolean showRain = rain && !drops.isEmpty();
        boolean showFx = fxOn() && !fx.isEmpty();
        if (!showImps && !showRain && !showFx) return;
        Graphics2D g2 = (Graphics2D) g.create();
        try
        {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (showFx) fx.paint(g2, clock);
            if (showRain)
            {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                for (Drop d : drops) paintDrop(g2, d);
            }
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            if (showImps) for (Imp m : imps) paintImp(g2, m);
        }
        finally
        {
            g2.dispose();
        }
    }

    private void paintImp(Graphics2D g, Imp m)
    {
        boolean moving = m.mode == Mode.WALK || m.mode == Mode.CLIMB;
        boolean stepUp = moving && ((int) Math.floor(m.px)) % 2 == 0;
        int frame = stepUp || m.mode == Mode.AIR ? 1 : 0;
        BufferedImage img = m.kind == Critter.IMP ? ImpSprite.image(m.hat, m.item, frame)
            : m.kind == Critter.PET ? PetSprites.image(Pet.of(m.variant), frame) : CritterSprite.image(m.kind, m.variant, frame);
        if (img == null) img = ImpSprite.image(ImpSprite.Hat.NONE, ImpSprite.Item.NONE, frame);
        float x = Math.round(m.x);
        float y = Math.round(m.y);

        if (m.mode != Mode.AIR && m.mode != Mode.CLIMB && !m.kind.flies())
        {
            g.setColor(new Color(0, 0, 0, 70));
            g.fill(new Ellipse2D.Float(x - 8, y - 2, 16, 3.5f));
        }

        if (m.popDir != 0)
        {
            // a puff where it pops: a ring that opens and a few sparkles
            float k = Math.max(0f, Math.min(1f, m.pop));
            float open = m.popDir == -1 ? k : 1 - k;
            int a = Math.round(200 * (1 - Math.abs(open - 0.5f) * 1.6f));
            if (a > 0)
            {
                float r = 5 + open * 13;
                g.setColor(new Color(255, 236, 170, Math.min(255, a)));
                g.setStroke(new java.awt.BasicStroke(1.6f));
                g.draw(new Ellipse2D.Float(x - r, y - 13 - r, r * 2, r * 2));
                for (int i = 0; i < 6; i++)
                {
                    double ang = i * Math.PI / 3 + 0.4;
                    float sx = x + (float) Math.cos(ang) * (r + 4);
                    float sy = y - 13 + (float) Math.sin(ang) * (r + 4);
                    g.fillRect(Math.round(sx) - 1, Math.round(sy), 3, 1);
                    g.fillRect(Math.round(sx), Math.round(sy) - 1, 1, 3);
                }
            }
        }
        AffineTransform old = g.getTransform();
        g.translate(x, y);
        if (m.popDir != 0)
        {
            // shrinks to nothing going out; springs in a little too big and settles coming in
            float k = Math.max(0f, Math.min(1f, m.pop));
            float sc = m.popDir == -1 ? 1 - k * k : (float) (1 + 0.25 * Math.sin(k * Math.PI)) * Math.min(1f, k * 1.6f);
            g.translate(0, -12 * (1 - sc));
            g.scale(Math.max(0.02f, sc), Math.max(0.02f, sc));
        }
        if (m.kind == Critter.CHEXY)
        {
            // drawn live, centred where a creature's body would be: a slow float and bob on top of its flight,
            // leaning a touch into the way it is going
            float w = m.wisp;
            g.translate(Chexy.bobX(w), -20 + Chexy.bobY(w));
            g.rotate(Math.max(-0.25f, Math.min(0.25f, m.vx * 0.006f)));
            Chexy.paint(g, 0, 0, w, Chexy.SIZE, calm);
            g.setTransform(old);
            return;
        }
        if (m.mode == Mode.CLIMB)
        {
            // feet against the panel's edge, head pointing inward
            g.rotate(m.side == -1 ? Math.PI / 2 : -Math.PI / 2);
            g.translate(stepUp ? 1 : 0, 0);
        }
        else
        {
            float bounce = m.mode == Mode.CHAT ? -(float) Math.abs(Math.sin(clock * 9)) * 3 : (stepUp ? -1 : 0);
            float s = m.squash;
            g.translate(0, bounce);
            // the sprite faces left; flip it when heading right
            g.scale((1 - s * 0.22f) * (m.dir == 1 ? -1 : 1), 1 + s * 0.28f);
        }
        // feet on the origin: the sprite's body is centred 21px in and stands on its bottom row
        g.drawImage(img, -ImpSprite.WIDTH / 2, -ImpSprite.HEIGHT + 1, null);
        g.setTransform(old);

        if (m.rare && m.sparkAge < 0.9f)
        {
            float k = m.sparkAge / 0.9f;
            float a = (float) Math.sin(Math.PI * k);
            float r = 1.5f + 3f * a;
            float sx = x + m.sparkX;
            float sy = y + m.sparkY;
            g.setColor(new Color(255, 240, 150, Math.round(230 * a)));
            java.awt.geom.Path2D.Float star = new java.awt.geom.Path2D.Float();
            star.moveTo(sx, sy - r);
            star.lineTo(sx + r * 0.3f, sy - r * 0.3f);
            star.lineTo(sx + r, sy);
            star.lineTo(sx + r * 0.3f, sy + r * 0.3f);
            star.lineTo(sx, sy + r);
            star.lineTo(sx - r * 0.3f, sy + r * 0.3f);
            star.lineTo(sx - r, sy);
            star.lineTo(sx - r * 0.3f, sy - r * 0.3f);
            star.closePath();
            g.fill(star);
        }
        if (!m.bubble.isEmpty()) paintBubble(g, m.bubble, Math.round(x), Math.round(y) - 26);
    }

    private static final String[] HEART = {".x.x.", "xxxxx", "xxxxx", ".xxx.", "..x.."};
    private static final String[] NOTE = {"..xx.", "..x.x", "..x..", "xxx..", "xxx.."};
    private static final String[] DOTS = {".....", ".....", ".....", ".....", "x.x.x"};
    private static final String[] BANG = {"..x..", "..x..", "..x..", ".....", "..x.."};

    /** A tiny pixel symbol over an imp's head, with a dark edge so it reads on any card. */
    private static void paintBubble(Graphics2D g, String what, int cx, int top)
    {
        String[] rows = "heart".equals(what) ? HEART : "note".equals(what) ? NOTE : "!".equals(what) ? BANG : DOTS;
        Color c = "heart".equals(what) ? new Color(0xff7f9d) : new Color(0xffe9a8);
        for (int pass = 0; pass < 2; pass++)
        {
            g.setColor(pass == 0 ? new Color(0, 0, 0, 200) : c);
            for (int j = 0; j < rows.length; j++)
            {
                for (int i = 0; i < rows[j].length(); i++)
                {
                    if (rows[j].charAt(i) != 'x') continue;
                    int px = cx - 5 + i * 2;
                    int py = top + j * 2;
                    if (pass == 0) g.fillRect(px - 1, py - 1, 4, 4);
                    else g.fillRect(px, py, 2, 2);
                }
            }
        }
    }
}
