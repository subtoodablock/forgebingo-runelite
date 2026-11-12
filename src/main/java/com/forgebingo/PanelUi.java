package com.forgebingo;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.io.InputStream;
import java.util.Collections;
import java.util.function.DoubleConsumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import net.runelite.client.ui.ColorScheme;

/**
 * The side panel's look: the website's stone-and-gold palette, its Cinzel heading font, and the small painted
 * parts (cards, buttons, bars, board squares) with their gentle animations. Everything here is Swing-only and
 * runs on the EDT. Animations are short and stop by themselves; {@link #animations} turns them all off.
 */
final class PanelUi
{
    private PanelUi()
    {
    }

    // ---- palette (warm stone and soft gold, like the website's panels; easy on the eyes next to the game)
    static final Color BG = ColorScheme.DARK_GRAY_COLOR;
    static final Color CARD_TOP = new Color(0x363029);
    static final Color CARD_BOTTOM = new Color(0x2b2722);
    static final Color CARD_EDGE = new Color(0x4d4437);
    static final Color INSET = new Color(0x1f1c18);
    static final Color INSET_EDGE = new Color(0x3d362c);
    static final Color GOLD = new Color(0xffbf1f);
    static final Color GOLD_SOFT = new Color(0xe9b84e);
    static final Color GOLD_DARK = new Color(0xa07222);
    static final Color ORANGE = new Color(0xff981f);
    static final Color TEXT = new Color(0xe6dfd3);
    static final Color MUTED = new Color(0xa69d8f);
    static final Color GREEN = new Color(0x66d966);
    static final Color GREEN_DARK = new Color(0x2f6a33);
    static final Color AMBER = new Color(0xf2bb45);
    static final Color RED = new Color(0xff7b70);

    /** false = no motion at all: values jump, nothing pulses (the "Side panel animations" setting) */
    static volatile boolean animations = true;

    // ---- fonts
    private static final Font CINZEL = loadCinzel();
    private static final String SANS = pickSans();
    static final Font CLEAN_SMALL = new Font(SANS, Font.PLAIN, 11);
    static final Font CLEAN_BODY = new Font(SANS, Font.PLAIN, 12);
    static final Font CLEAN_BOLD = new Font(SANS, Font.BOLD, 13);
    /** headings: Cinzel, the website's display font (falls back to the bold body font if it cannot load) */
    static final Font CLEAN_HEAD = CINZEL == null ? CLEAN_BOLD : CINZEL.deriveFont(13f);
    static final Font CLEAN_TITLE = CINZEL == null ? CLEAN_BOLD.deriveFont(15f) : CINZEL.deriveFont(16f);
    static final Font CLEAN_CAPS = (CINZEL == null ? CLEAN_SMALL.deriveFont(Font.BOLD) : CINZEL.deriveFont(10.5f))
        .deriveFont(Collections.singletonMap(TextAttribute.TRACKING, 0.08f));

    private static Font loadCinzel()
    {
        try (InputStream in = PanelUi.class.getResourceAsStream("fonts/Cinzel-Bold.ttf"))
        {
            if (in == null) return null;
            Font f = Font.createFont(Font.TRUETYPE_FONT, in);
            // labels that wrap their text (HTML) look fonts up by family name, so the font has to be registered
            java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(f);
            return f;
        }
        catch (Exception | LinkageError e)
        {
            return null;
        }
    }

    /** A smooth system sans-serif where there is one; Java's own otherwise. */
    private static String pickSans()
    {
        try
        {
            java.util.Set<String> have = new java.util.HashSet<>(java.util.Arrays.asList(
                java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
            for (String f : new String[] {"Segoe UI", "Inter", "SF Pro Text", "Helvetica Neue", "Noto Sans", "DejaVu Sans"})
            {
                if (have.contains(f)) return f;
            }
        }
        catch (RuntimeException | LinkageError e)
        {
            // fall through
        }
        return Font.SANS_SERIF;
    }

    /** The heading font when it has every character of the text (it is Latin only); the fallback otherwise. */
    static Font headingFor(String text, Font heading, Font fallback)
    {
        return text == null || heading.canDisplayUpTo(text) < 0 ? heading : fallback;
    }

    static Graphics2D smooth(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g2;
    }

    static Color mix(Color a, Color b, float t)
    {
        float k = Math.max(0f, Math.min(1f, t));
        return new Color(
            Math.round(a.getRed() + (b.getRed() - a.getRed()) * k),
            Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * k),
            Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * k),
            Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * k));
    }

    static Color alpha(Color c, int a)
    {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }

    /** A slow breath between 0 and 1 (about 2.6 s), shared so everything that glows glows together. */
    static float pulse()
    {
        if (!animations) return 1f;
        return 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 2600.0 * Math.PI * 2);
    }

    /** Runs f with an eased 0..1 over ms milliseconds (or just once with 1 when animations are off). */
    static void tween(int ms, DoubleConsumer f)
    {
        if (!animations)
        {
            f.accept(1);
            return;
        }
        long start = System.nanoTime();
        Timer t = new Timer(16, null);
        t.addActionListener(e -> {
            double k = Math.min(1.0, (System.nanoTime() - start) / 1e6 / ms);
            f.accept(1 - Math.pow(1 - k, 3));
            if (k >= 1) t.stop();
        });
        f.accept(0);
        t.start();
    }

    /** A value that glides between 0 and 1 (hover highlights). */
    static final class Fader
    {
        private final JComponent owner;
        private final int ms;
        private final Timer timer = new Timer(16, e -> step());
        private float value;
        private float target;
        private long last;

        Fader(JComponent owner, int ms)
        {
            this.owner = owner;
            this.ms = ms;
        }

        void set(boolean on)
        {
            target = on ? 1f : 0f;
            if (!animations)
            {
                value = target;
                owner.repaint();
                return;
            }
            if (!timer.isRunning())
            {
                last = System.nanoTime();
                timer.start();
            }
        }

        float get()
        {
            return value;
        }

        private void step()
        {
            long now = System.nanoTime();
            float d = (now - last) / 1e6f / ms;
            last = now;
            value = target > value ? Math.min(target, value + d) : Math.max(target, value - d);
            if (value == target || !owner.isDisplayable()) timer.stop();
            owner.repaint();
        }
    }

    // ------------------------------------------------------------------ card

    /** A rounded stone card: soft gradient, thin edge, optional coloured stripe down the left. */
    static class Card extends JPanel
    {
        private static final int ARC = 8;
        private Color accent;
        private Color edge = CARD_EDGE;
        private boolean inset;
        private Fader hover;
        private float alpha = 1f;
        private float lift;

        Card(Color accent)
        {
            this.accent = accent;
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(false);
            setAlignmentX(LEFT_ALIGNMENT);
            pad(8, 8, 8, 8);
            putClientProperty(PanelImps.PLATFORM, Boolean.TRUE);
        }

        /** Padding inside the card; the stripe's width is added on the left. */
        final Card pad(int top, int left, int bottom, int right)
        {
            setBorder(BorderFactory.createEmptyBorder(top, left + (accent == null ? 0 : 3), bottom, right));
            return this;
        }

        /** The darker, sunken look (a task inside a tile card). */
        Card inset(Color edgeColour)
        {
            inset = true;
            edge = edgeColour == null ? INSET_EDGE : edgeColour;
            return this;
        }

        /** Brightens a little under the mouse. */
        Card hoverable()
        {
            hover = new Fader(this, 130);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseEntered(MouseEvent e)
                {
                    hover.set(true);
                }

                @Override
                public void mouseExited(MouseEvent e)
                {
                    hover.set(false);
                }
            });
            return this;
        }

        /** Fades in and settles up a few pixels (a tile being opened, team news arriving). */
        void fadeIn()
        {
            tween(240, k -> {
                alpha = (float) k;
                lift = (float) (1 - k) * 5f;
                repaint();
            });
        }

        @Override
        public void paint(Graphics g)
        {
            if (alpha >= 1f && lift <= 0f)
            {
                super.paint(g);
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            try
            {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, alpha))));
                g2.translate(0, Math.round(lift));
                super.paint(g2);
            }
            finally
            {
                g2.dispose();
            }
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            int w = getWidth();
            int h = getHeight();
            Graphics2D g2 = smooth(g);
            try
            {
                float hv = hover == null ? 0f : hover.get();
                Shape body = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, ARC, ARC);
                if (inset)
                {
                    g2.setColor(INSET);
                }
                else
                {
                    Color top = mix(CARD_TOP, new Color(0x4a4135), hv);
                    Color bottom = mix(CARD_BOTTOM, new Color(0x3a332a), hv);
                    g2.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
                }
                g2.fill(body);
                if (accent != null)
                {
                    Shape old = g2.getClip();
                    g2.clip(body);
                    g2.setColor(accent);
                    g2.fillRect(0, 0, 3, h);
                    g2.setClip(old);
                }
                if (!inset)
                {
                    // a faint light line under the top edge, like the bevel on the website's panels
                    g2.setColor(new Color(255, 240, 210, 16));
                    g2.drawLine(ARC / 2 + 1, 1, w - ARC / 2 - 2, 1);
                }
                g2.setColor(mix(edge, GOLD_DARK, hv * 0.7f));
                g2.draw(body);
            }
            finally
            {
                g2.dispose();
            }
        }
    }

    // ------------------------------------------------------------------ button

    /** A stone button with a gold edge that warms up under the mouse. */
    static final class StoneButton extends JButton
    {
        private final Fader hover = new Fader(this, 120);

        StoneButton(String text)
        {
            super(text);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setFocusable(false);
            setOpaque(false);
            setRolloverEnabled(false);
            setForeground(TEXT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
            putClientProperty(PanelImps.PLATFORM, Boolean.TRUE);
            addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseEntered(MouseEvent e)
                {
                    hover.set(true);
                }

                @Override
                public void mouseExited(MouseEvent e)
                {
                    hover.set(false);
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            int w = getWidth();
            int h = getHeight();
            Graphics2D g2 = smooth(g);
            try
            {
                boolean down = getModel().isArmed() && getModel().isPressed();
                float hv = isEnabled() ? hover.get() : 0f;
                Shape body = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, 7, 7);
                Color top = down ? new Color(0x2a251f) : mix(new Color(0x453c30), new Color(0x54483a), hv);
                Color bottom = down ? new Color(0x332d25) : mix(new Color(0x322c25), new Color(0x3d352c), hv);
                g2.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
                g2.fill(body);
                if (!down)
                {
                    g2.setColor(new Color(255, 240, 210, 20));
                    g2.drawLine(4, 1, w - 5, 1);
                }
                g2.setColor(mix(new Color(0x6a5e50), GOLD, hv * 0.75f));
                g2.draw(body);

                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                String text = getText() == null ? "" : getText();
                int x = (w - fm.stringWidth(text)) / 2;
                int y = (h - fm.getHeight()) / 2 + fm.getAscent() + (down ? 1 : 0);
                g2.setColor(new Color(0, 0, 0, 150));
                g2.drawString(text, x + 1, y + 1);
                Color fg = isEnabled() ? getForeground() : MUTED;
                g2.setColor(mix(fg, new Color(0xffd83d), hv * 0.6f));
                g2.drawString(text, x, y);
            }
            finally
            {
                g2.dispose();
            }
        }
    }

    // ------------------------------------------------------------------ progress bar

    /** A rounded gold bar whose fill glides to its new value with one soft sheen. */
    static final class GoldBar extends JComponent
    {
        private int max = 1;
        private int value;
        private double shown;
        private double sheen = -1;
        private String text;
        private boolean first = true;
        private int run;

        GoldBar()
        {
            setOpaque(false);
            setForeground(GOLD);
            putClientProperty(PanelImps.PLATFORM, Boolean.TRUE);
        }

        void setMaximum(int m)
        {
            max = Math.max(1, m);
        }

        void setString(String s)
        {
            text = s;
            repaint();
        }

        /** Jumps there (a redraw of something already on screen). */
        void setValueNow(int v)
        {
            run++;
            value = Math.max(0, Math.min(max, v));
            shown = value;
            sheen = -1;
            first = false;
            repaint();
        }

        /** Glides there from where the bar is now (from empty the first time). */
        void setValue(int v)
        {
            int to = Math.max(0, Math.min(max, v));
            if (to == value && !first) return;
            first = false;
            value = to;
            double from = Math.min(shown, max);
            int mine = ++run;
            tween(520, k -> {
                if (mine != run) return;
                shown = from + (to - from) * k;
                sheen = k < 1 ? k : -1;
                repaint();
            });
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            int w = getWidth();
            int h = getHeight();
            Graphics2D g2 = smooth(g);
            try
            {
                float arc = Math.min(h, 8);
                Shape track = new RoundRectangle2D.Float(0, 0, w, h, arc, arc);
                g2.setColor(new Color(0x14110e));
                g2.fill(track);
                int fw = (int) Math.round(w * Math.max(0, Math.min(1, shown / max)));
                if (fw > 0)
                {
                    Shape old = g2.getClip();
                    g2.clip(track);
                    Color c = getForeground();
                    g2.setPaint(new GradientPaint(0, 0, mix(c, Color.WHITE, 0.22f), 0, h, mix(c, Color.BLACK, 0.28f)));
                    g2.fillRect(0, 0, fw, h);
                    if (sheen >= 0)
                    {
                        float sx = (float) (-20 + (fw + 40) * sheen);
                        int a = (int) (70 * Math.sin(Math.PI * sheen));
                        g2.clipRect(0, 0, fw, h);
                        g2.setPaint(new LinearGradientPaint(sx - 14, 0, sx + 14, 0, new float[] {0f, 0.5f, 1f},
                            new Color[] {new Color(255, 255, 255, 0), new Color(255, 255, 255, Math.max(0, a)), new Color(255, 255, 255, 0)}));
                        g2.fillRect((int) sx - 14, 0, 28, h);
                    }
                    g2.setClip(old);
                }
                g2.setColor(new Color(0, 0, 0, 110));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, arc, arc));
                if (text != null && !text.isEmpty() && h >= 12)
                {
                    g2.setFont(getFont());
                    FontMetrics fm = g2.getFontMetrics();
                    int x = (w - fm.stringWidth(text)) / 2;
                    int y = (h - fm.getHeight()) / 2 + fm.getAscent();
                    g2.setColor(new Color(0, 0, 0, 190));
                    g2.drawString(text, x + 1, y + 1);
                    g2.setColor(new Color(0xfff6e2));
                    g2.drawString(text, x, y);
                }
            }
            finally
            {
                g2.dispose();
            }
        }
    }

    // ------------------------------------------------------------------ board square

    /** One square of the mini board: rounded, lit from above, gold ring when open, a soft lift under the mouse. */
    static final class TileCell extends JLabel
    {
        private final Fader hover = new Fader(this, 110);
        private boolean selected;
        private boolean interactive;

        TileCell()
        {
            setOpaque(false);
            setHorizontalAlignment(CENTER);
        }

        void makeInteractive()
        {
            interactive = true;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseEntered(MouseEvent e)
                {
                    hover.set(true);
                }

                @Override
                public void mouseExited(MouseEvent e)
                {
                    hover.set(false);
                }
            });
        }

        void setSelected(boolean on)
        {
            if (selected == on) return;
            selected = on;
            repaint();
        }

        boolean isSelected()
        {
            return selected;
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            int w = getWidth();
            int h = getHeight();
            Graphics2D g2 = smooth(g);
            try
            {
                float arc = Math.max(3f, Math.min(6f, w / 5f));
                Shape body = new RoundRectangle2D.Float(1f, 1f, w - 2f, h - 2f, arc, arc);
                Color bg = getBackground();
                float hv = interactive ? hover.get() : 0f;
                g2.setPaint(new GradientPaint(0, 0, mix(bg, Color.WHITE, 0.10f + 0.10f * hv), 0, h, mix(bg, Color.BLACK, 0.12f)));
                g2.fill(body);
                g2.setColor(new Color(0, 0, 0, 120));
                g2.draw(new RoundRectangle2D.Float(1.5f, 1.5f, w - 3f, h - 3f, arc, arc));
                if (selected)
                {
                    float p = pulse();
                    g2.setStroke(new BasicStroke(3f));
                    g2.setColor(alpha(GOLD, Math.round(40 + 55 * p)));
                    g2.draw(new RoundRectangle2D.Float(1.5f, 1.5f, w - 3f, h - 3f, arc, arc));
                    g2.setStroke(new BasicStroke(1.4f));
                    g2.setColor(GOLD);
                    g2.draw(new RoundRectangle2D.Float(1f, 1f, w - 2f, h - 2f, arc, arc));
                }
                else if (hv > 0f)
                {
                    g2.setStroke(new BasicStroke(1.2f));
                    g2.setColor(alpha(GOLD_SOFT, Math.round(190 * hv)));
                    g2.draw(new RoundRectangle2D.Float(1f, 1f, w - 2f, h - 2f, arc, arc));
                }
            }
            finally
            {
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }

    // ------------------------------------------------------------------ small parts

    /** "ForgeBingo" in the website's gold gradient with a hard dark edge. */
    static final class GoldTitle extends JComponent
    {
        private final String text;

        GoldTitle(String text)
        {
            this.text = text;
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredSize()
        {
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(fm.stringWidth(text) + 2, fm.getHeight() + 1);
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            Graphics2D g2 = smooth(g);
            try
            {
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                g2.setColor(new Color(0, 0, 0, 200));
                g2.drawString(text, 1, y + 1);
                g2.setPaint(new LinearGradientPaint(0, y - fm.getAscent(), 0, y + 2, new float[] {0f, 0.55f, 1f},
                    new Color[] {new Color(0xffe27a), GOLD, new Color(0xc98f24)}));
                g2.drawString(text, 0, y);
            }
            finally
            {
                g2.dispose();
            }
        }
    }

    /** A section name with a gold line that fades out to the right. */
    static final class SectionHeader extends JComponent
    {
        private final String text;

        SectionHeader(String text)
        {
            this.text = text.toUpperCase();
            setOpaque(false);
            putClientProperty(PanelImps.PLATFORM, Boolean.TRUE);
        }

        @Override
        public Dimension getPreferredSize()
        {
            return new Dimension(40, getFontMetrics(getFont()).getHeight() + 8);
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            Graphics2D g2 = smooth(g);
            try
            {
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                g2.setColor(new Color(0, 0, 0, 170));
                g2.drawString(text, 1, y + 1);
                g2.setColor(ORANGE);
                g2.drawString(text, 0, y);
                int x0 = fm.stringWidth(text) + 8;
                int ly = getHeight() / 2;
                if (getWidth() - x0 > 12)
                {
                    g2.setPaint(new GradientPaint(x0, 0, alpha(GOLD, 150), getWidth(), 0, alpha(GOLD, 0)));
                    g2.fillRect(x0, ly, getWidth() - x0, 1);
                }
            }
            finally
            {
                g2.dispose();
            }
        }
    }

    /** The connection light: a small dot with a slow, soft halo. */
    static final class StatusDot extends JComponent
    {
        private Color colour = MUTED;

        StatusDot()
        {
            setOpaque(false);
            Dimension d = new Dimension(12, 14);
            setPreferredSize(d);
            setMinimumSize(d);
        }

        void setColour(Color c)
        {
            colour = c;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            Graphics2D g2 = smooth(g);
            try
            {
                float cx = 5f;
                float cy = 8f;
                float p = pulse();
                float r = 3.6f + 1.6f * p;
                g2.setColor(alpha(colour, Math.round(30 + 45 * p)));
                g2.fill(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
                g2.setColor(colour);
                g2.fill(new Ellipse2D.Float(cx - 2.6f, cy - 2.6f, 5.2f, 5.2f));
                g2.setColor(new Color(255, 255, 255, 90));
                g2.fill(new Ellipse2D.Float(cx - 1.6f, cy - 1.8f, 1.8f, 1.8f));
            }
            finally
            {
                g2.dispose();
            }
        }
    }
}
