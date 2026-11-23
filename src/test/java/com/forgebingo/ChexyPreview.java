package com.forgebingo;

import com.forgebingo.core.Chexy;
import com.forgebingo.core.ChexySpells;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Writes Chexy's preview: art/chexy-frames.png (eight frames; top row the real pixels blown up, bottom row
 * drawn big) and art/chexy-big.png (one frame, real pixels on the left, drawn big on the right), and prints what
 * one frame costs to draw. Not a test.
 */
public final class ChexyPreview
{
    private ChexyPreview()
    {
    }

    private static final int CELL = 56;
    private static final int FRAMES = 8;
    private static final float STEP = 0.18f;
    private static final int[] BACKS = {
        PanelTheme.CLASSIC.palette[1], PanelTheme.BLOOD.palette[0], PanelTheme.XERIC.palette[1], PanelTheme.AMASCUT.palette[0],
        PanelTheme.CLASSIC.palette[0], PanelTheme.INFERNO.palette[1], PanelTheme.SKILLING.palette[0], PanelTheme.BARROWS.palette[1],
    };

    public static void main(String[] args) throws Exception
    {
        File dir = new File(args.length > 0 ? args[0] : "art");
        dir.mkdirs();
        boolean calm = args.length > 1 && "calm".equals(args[1]);
        int up = 3;
        BufferedImage strip = new BufferedImage(CELL * up * FRAMES, CELL * up * 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = strip.createGraphics();
        for (int f = 0; f < FRAMES; f++)
        {
            float t = 3f + f * STEP;
            Color back = new Color(BACKS[f % BACKS.length]);
            BufferedImage small = frame(t, back, 1, calm);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(small, f * CELL * up, 0, CELL * up, CELL * up, null);
            g.drawImage(frame(t, back, up, calm), f * CELL * up, CELL * up, null);
        }
        g.dispose();
        ImageIO.write(strip, "png", new File(dir, calm ? "chexy-frames-calm.png" : "chexy-frames.png"));

        int big = 8;
        BufferedImage one = new BufferedImage(CELL * big * 2, CELL * big, BufferedImage.TYPE_INT_RGB);
        g = one.createGraphics();
        Color back = new Color(PanelTheme.CLASSIC.palette[1]);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(frame(3.5f, back, 1, calm), 0, 0, CELL * big, CELL * big, null);
        g.drawImage(frame(3.5f, back, big, calm), CELL * big, 0, null);
        g.dispose();
        ImageIO.write(one, "png", new File(dir, calm ? "chexy-big-calm.png" : "chexy-big.png"));

        if (!calm) gif(new File(dir, "chexy.gif"));
        forms(new File(dir, calm ? "chexy-forms-calm.png" : "chexy-forms.png"), calm);
        if (!calm) powerup(new File(dir, "chexy-powerup.gif"));
        spellsSheet(new File(dir, calm ? "chexy-spells-calm.png" : "chexy-spells.png"), calm);
        if (!calm) spellsGif(new File(dir, "chexy-spells.gif"));

        // what one frame costs, drawn straight onto a panel-sized picture the way the panel's layer does
        BufferedImage panel = new BufferedImage(242, 560, BufferedImage.TYPE_INT_RGB);
        Graphics2D pg = panel.createGraphics();
        pg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        String[] names = {"base", "charge", "gold", "blue", "flower spell", "barrier", "beam volley"};
        float[] ages = {3f, Chexy.CHARGE_AT + 1f, 25f, 50f, ChexySpells.FLOWER_AT + 1f, ChexySpells.BARRIER_AT + 1.2f,
            ChexySpells.VOLLEYS[0] + 1.9f};
        for (int f = 0; f < names.length; f++)
        {
            int n = 3000;
            final int which = f;
            for (int pass = 0; pass < 2; pass++)
            {
                long start = System.nanoTime();
                for (int i = 0; i < n; i++)
                {
                    float age = ages[which] + (i % 10) * 0.01f;
                    float t = i * 0.033f;
                    Chexy.paint(pg, 120, 200, t, t, Chexy.SIZE, calm, which < 4 ? age : -1f, which < 4 ? -1f : age);
                    if (which == 4) paintFlowers(pg, 120, 200, 2.5f + (i % 10) * 0.01f, t, calm);
                    if (which == 6)
                    {
                        ChexySpells.paintVolley(pg, 120, 200, -1, age - ChexySpells.VOLLEYS[0], t, Chexy.GOLD, calm,
                            new java.awt.Rectangle(0, 0, 242, 560));
                    }
                }
                long took = System.nanoTime() - start;
                if (pass == 1) System.out.printf("Chexy %s: %.3f ms per frame%n", names[f], took / 1e6 / n);
            }
        }
        pg.dispose();
        System.out.println("Wrote previews to " + dir.getAbsolutePath());
    }

    // ------------------------------------------------------------------ the spells

    private static final int SW = 150, SH = 96, SUP = 3;

    /** the flowers' layout round Chexy at (cx, cy), as the panel lays them out: on the card just below, and lower down */
    private static final float[][] FLOWERS = layout(false);
    private static final float[][] FLOWERS_CALM = layout(true);

    private static float[][] layout(boolean calm)
    {
        java.util.List<float[]> out = new java.util.ArrayList<>();
        java.util.Random r = new java.util.Random(7);
        float[] near = calm ? new float[] {-6, 7, 18} : new float[] {-6, 7, -17, 18, -29, 30};
        float[] low = calm ? new float[] {-40, 10, 60} : new float[] {-46, -28, -9, 9, 27, 45, 64, 82};
        int index = 0;
        for (float dx : near) out.add(new float[] {dx, 26, r.nextInt(4), 0.12f + Math.abs(dx) / 110f + 26 / 260f + r.nextFloat() * 0.12f, index++});
        for (float dx : low) out.add(new float[] {dx, 52, r.nextInt(4), 0.12f + Math.abs(dx) / 110f + 52 / 260f + r.nextFloat() * 0.12f, index++});
        return out.toArray(new float[0][]);
    }

    /** the flowers, sinceSpawn seconds after they were placed */
    private static void paintFlowers(Graphics2D g, float cx, float cy, float sinceSpawn, float clock, boolean calm)
    {
        if (sinceSpawn >= 0 && sinceSpawn < 1.5f) ChexySpells.paintWave(g, cx, cy + 22, sinceSpawn, calm);
        for (float[] fl : calm ? FLOWERS_CALM : FLOWERS)
        {
            int x = Math.round(cx + fl[0]);
            int ground = Math.round(cy + fl[1]);
            float since = sinceSpawn - fl[3];
            float life = ChexySpells.FLOWER_LIFE - fl[3] * 0.5f;
            ChexySpells.paintFlower(g, x, ground, (int) fl[2], since, life, clock);
            ChexySpells.paintPetals(g, x, ground, (int) fl[2], since, (int) fl[4], calm);
        }
    }

    /** a dark panel with two cards, as the flowers would stand on them */
    private static Graphics2D scene(BufferedImage img, float cy, boolean cards)
    {
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(PanelTheme.CLASSIC.palette[0]));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.scale(SUP, SUP);
        if (cards)
        {
            int[] tops = {Math.round(cy + 26), Math.round(cy + 52)};
            int[] rights = {108, 146};
            for (int i = 0; i < 2; i++)
            {
                g.setColor(new Color(PanelTheme.CLASSIC.palette[2]));
                g.fillRect(4, tops[i], rights[i] - 4, i == 0 ? 20 : SH);
                g.setColor(new Color(PanelTheme.CLASSIC.palette[3]));
                g.drawRect(4, tops[i], rights[i] - 4, i == 0 ? 20 : SH);
            }
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        return g;
    }

    /** One frame of a spell at visit age; t is Chexy's clock. which: 0 flowers, 1 barrier, 2 volley. */
    private static BufferedImage spellFrame(int which, float age, float t, boolean calm)
    {
        BufferedImage img = new BufferedImage(SW * SUP, SH * SUP, BufferedImage.TYPE_INT_RGB);
        float cx = which == 0 ? 58 : which == 1 ? 75 : 34;
        float cy = which == 0 ? 30 : which == 1 ? 46 : 50;
        Graphics2D g = scene(img, cy, which == 0);
        float x = cx + Chexy.bobX(t), y = cy + Chexy.bobY(t);
        if (which == 0)
        {
            float spawn = ChexySpells.FLOWER_AT + ChexySpells.FLOWER_SPAWN;
            paintFlowers(g, cx, cy, age - spawn, age, calm);
        }
        float[] kick = new float[2];
        float vp = ChexySpells.volleyPhase(age);
        if (which == 2 && vp >= 0) ChexySpells.recoil(vp, 1, calm, kick);
        Chexy.paint(g, x + kick[0], y + kick[1], t, t, Chexy.SIZE, calm, -1f, age);
        if (which == 2 && vp >= 0)
        {
            ChexySpells.paintVolley(g, x, y, 1, vp, age, Chexy.BASE, calm, new java.awt.Rectangle(0, 0, SW, SH));
        }
        g.dispose();
        return img;
    }

    /** art/chexy-spells.png: key frames, a row per spell (flowers, barrier, beam volley). */
    private static void spellsSheet(File file, boolean calm) throws Exception
    {
        float spawn = ChexySpells.FLOWER_AT + ChexySpells.FLOWER_SPAWN;
        float[][] ages = {
            {spawn + 0.2f, spawn + 0.75f, spawn + 1.3f, spawn + 3.6f},
            {ChexySpells.BARRIER_AT + 0.35f, ChexySpells.BARRIER_AT + 1.15f, ChexySpells.BARRIER_AT + 2.05f, ChexySpells.BARRIER_AT + 3.75f},
            {ChexySpells.VOLLEYS[0] + 0.85f, ChexySpells.VOLLEYS[0] + 1.06f, ChexySpells.VOLLEYS[0] + 1.95f, ChexySpells.VOLLEYS[0] + 2.75f},
        };
        int w = SW * SUP, h = SH * SUP;
        BufferedImage sheet = new BufferedImage(w * 4, h * 3, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        for (int row = 0; row < 3; row++)
        {
            for (int col = 0; col < 4; col++)
            {
                float age = ages[row][col];
                g.drawImage(spellFrame(row, age, 3f + age, calm), col * w, row * h, null);
            }
        }
        g.dispose();
        ImageIO.write(sheet, "png", file);
    }

    /** art/chexy-spells.gif: the three spells playing one after another, looping (long lingers cut short). */
    private static void spellsGif(File file) throws Exception
    {
        float dt = 0.08f;
        float spawn = ChexySpells.FLOWER_AT + ChexySpells.FLOWER_SPAWN;
        float[][] parts = {
            {0, ChexySpells.FLOWER_AT - 0.3f, spawn + 4.4f},
            {0, spawn + ChexySpells.FLOWER_LIFE - 2.1f, spawn + ChexySpells.FLOWER_LIFE + 0.1f},
            {1, ChexySpells.BARRIER_AT - 0.3f, ChexySpells.BARRIER_AT + ChexySpells.BARRIER_TIME + 0.2f},
            {2, ChexySpells.VOLLEYS[0] - 0.3f, ChexySpells.VOLLEYS[0] + ChexySpells.VOLLEY_TIME + 0.2f},
        };
        java.util.List<BufferedImage> frames = new java.util.ArrayList<>();
        for (float[] part : parts)
        {
            float t = 3f + part[1]; // a spell visit never spins up: its clock runs at the usual speed
            for (float age = part[1]; age < part[2]; age += dt)
            {
                frames.add(spellFrame((int) part[0], age, t, false));
                t += dt;
            }
        }
        BufferedImage[] all = frames.toArray(new BufferedImage[0]);
        int w = SW * SUP, h = SH * SUP;
        int cols = 8;
        int shown = (all.length + 3) / 4;
        BufferedImage sheet = new BufferedImage(w * cols / 2, h * ((shown + cols - 1) / cols) / 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        for (int i = 0; i < all.length; i += 4)
        {
            int n = i / 4;
            g.drawImage(all[i], n % cols * w / 2, n / cols * h / 2, w / 2, h / 2, null);
        }
        g.dispose();
        new File("build/preview").mkdirs();
        ImageIO.write(sheet, "png", new File("build/preview/chexy-spells-sheet.png"));
        writeGif(file, all, Math.round(dt * 100));
        System.out.println("Spells GIF: " + all.length + " frames");
    }

    private static final int PCELL = 80;

    /** Chexy's own clock at a visit age, run the way the panel runs it (sped up by Chexy.spin). */
    private static float clockAt(float age)
    {
        float t = 3f;
        for (float a = 0; a < age; a += 0.01f) t += 0.01f * Chexy.spin(a);
        return t;
    }

    /** One frame of the power-up at 3x, centred, floating, on the dark panel colour; scale for the pop. */
    private static BufferedImage powerFrame(float t, float age, float pop, boolean calm)
    {
        int up = 3;
        BufferedImage img = new BufferedImage(PCELL * up, PCELL * up, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(PanelTheme.CLASSIC.palette[0]));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.scale(up, up);
        g.translate(PCELL / 2f + Chexy.bobX(t), PCELL / 2f + 6 + Chexy.bobY(t));
        if (pop < 1f) g.scale(Math.max(0.02f, pop), Math.max(0.02f, pop));
        Chexy.paint(g, 0, 0, t, t, Chexy.SIZE, calm, age);
        g.dispose();
        return img;
    }

    /** art/chexy-forms.png: frames across the whole power-up (base, charge, flash, gold, charge, flash, blue). */
    private static void forms(File file, boolean calm) throws Exception
    {
        float[] ages = {3f, Chexy.CHARGE_AT + 0.8f, Chexy.GOLD_AT - 0.15f, Chexy.GOLD_AT + 0.05f, Chexy.GOLD_AT + 0.4f, Chexy.GOLD_AT + 9f,
            Chexy.CHARGE2_AT + 1.2f, Chexy.BLUE_AT + 0.05f, Chexy.BLUE_AT + 0.6f, Chexy.BLUE_AT + 10f};
        int w = PCELL * 3;
        BufferedImage strip = new BufferedImage(w * ages.length, w, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = strip.createGraphics();
        for (int i = 0; i < ages.length; i++) g.drawImage(powerFrame(clockAt(ages[i]), ages[i], 1f, calm), i * w, 0, null);
        g.dispose();
        ImageIO.write(strip, "png", file);
    }

    /**
     * art/chexy-powerup.gif: the whole transformation, looping. It pops in, stays itself a moment, charges, flashes
     * gold, holds, charges again, flashes blue, holds, and pops out. The long holds are cut short.
     */
    private static void powerup(File file) throws Exception
    {
        float dt = 0.07f;
        // stretches of visit age to show: (from, to); between them the steady holds are skipped
        float[][] parts = {{Chexy.CHARGE_AT - 1.8f, Chexy.GOLD_AT + 3.2f}, {Chexy.CHARGE2_AT - 0.8f, Chexy.BLUE_AT + 3.4f}};
        java.util.List<BufferedImage> frames = new java.util.ArrayList<>();
        float t = clockAt(parts[0][0]);
        float pop = 0.45f;
        int total = 0;
        for (float[] part : parts) total += (int) ((part[1] - part[0]) / dt);
        int f = 0;
        for (float[] part : parts)
        {
            for (float age = part[0]; age < part[1]; age += dt, f++)
            {
                // the pop in at the start and the pop out at the end, as on the panel
                float sc = 1f;
                float in = f * dt / pop;
                float out = (total - 1 - f) * dt / pop;
                if (in < 1) sc = (float) (1 + 0.25 * Math.sin(in * Math.PI)) * Math.min(1f, in * 1.6f);
                else if (out < 1) sc = 1 - (1 - out) * (1 - out);
                frames.add(powerFrame(t, age, sc, false));
                t += dt * Chexy.spin(age);
            }
        }
        BufferedImage[] all = frames.toArray(new BufferedImage[0]);
        // a contact sheet (every sixth frame) to look at it still
        int w = PCELL * 3;
        int cols = (all.length + 5) / 6;
        BufferedImage sheet = new BufferedImage(w * Math.min(cols, 12), w * ((cols + 11) / 12), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        for (int i = 0; i < all.length; i += 6) g.drawImage(all[i], (i / 6) % 12 * w, (i / 6) / 12 * w, null);
        g.dispose();
        new File("build/preview").mkdirs();
        ImageIO.write(sheet, "png", new File("build/preview/chexy-powerup-sheet.png"));
        writeGif(file, all, Math.round(dt * 100));
        System.out.println("Power-up GIF: " + all.length + " frames");
    }

    private static final int GIF_FRAMES = 36;
    /**
     * A looping GIF of one comet lap at 3x on the dark panel colour: the real pixels blown up on the left, drawn at
     * 3x on the right. The comets, twinkles and float repeat every lap. The rings' slow turn does not, so here its
     * clock sways to and fro around the start (at the same top speed as in the panel) and the loop is seamless.
     */
    private static void gif(File file) throws Exception
    {
        int up = 3;
        int cell = 64;
        Color back = new Color(PanelTheme.CLASSIC.palette[0]);
        float dt = Chexy.LAP / GIF_FRAMES;
        float t0 = 3f;
        BufferedImage[] frames = new BufferedImage[GIF_FRAMES];
        for (int f = 0; f < GIF_FRAMES; f++)
        {
            frames[f] = gifFrame(t0 + f * dt, back, cell, up);
        }
        // a contact sheet of the GIF (every third frame, real pixels on top, drawn big below) to look at it still
        int fw = cell * up;
        BufferedImage sheet = new BufferedImage(fw * GIF_FRAMES / 3, fw * 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D sg = sheet.createGraphics();
        for (int f = 0; f < GIF_FRAMES; f += 3)
        {
            sg.drawImage(frames[f].getSubimage(0, 0, fw, fw), f / 3 * fw, 0, null);
            sg.drawImage(frames[f].getSubimage(fw, 0, fw, fw), f / 3 * fw, fw, null);
        }
        sg.dispose();
        new File("build/preview").mkdirs();
        ImageIO.write(sheet, "png", new File("build/preview/chexy-gif-sheet.png"));

        writeGif(file, frames, Math.round(dt * 100));
    }

    /** Writes a looping GIF, every frame on one shared, dithered palette. */
    private static void writeGif(File file, BufferedImage[] frames, int delayCs) throws Exception
    {
        frames = indexed(frames);
        javax.imageio.ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (javax.imageio.stream.ImageOutputStream out = ImageIO.createImageOutputStream(file))
        {
            writer.setOutput(out);
            writer.prepareWriteSequence(null);
            for (int f = 0; f < frames.length; f++)
            {
                javax.imageio.ImageTypeSpecifier type = javax.imageio.ImageTypeSpecifier.createFromRenderedImage(frames[f]);
                javax.imageio.metadata.IIOMetadata meta = writer.getDefaultImageMetadata(type, null);
                String fmt = meta.getNativeMetadataFormatName();
                javax.imageio.metadata.IIOMetadataNode root = (javax.imageio.metadata.IIOMetadataNode) meta.getAsTree(fmt);
                javax.imageio.metadata.IIOMetadataNode gce = child(root, "GraphicControlExtension");
                gce.setAttribute("disposalMethod", "none");
                gce.setAttribute("userInputFlag", "FALSE");
                gce.setAttribute("transparentColorFlag", "FALSE");
                gce.setAttribute("delayTime", Integer.toString(delayCs));
                gce.setAttribute("transparentColorIndex", "0");
                // the shared palette, written out with every frame (the writer leaves it out otherwise)
                java.awt.image.IndexColorModel cm = (java.awt.image.IndexColorModel) frames[f].getColorModel();
                javax.imageio.metadata.IIOMetadataNode table = child(root, "LocalColorTable");
                table.setAttribute("sizeOfLocalColorTable", "256");
                table.setAttribute("sortFlag", "FALSE");
                while (table.getLength() > 0) table.removeChild(table.getFirstChild());
                for (int i = 0; i < 256; i++)
                {
                    int rgb = i < cm.getMapSize() ? cm.getRGB(i) : 0;
                    javax.imageio.metadata.IIOMetadataNode e = new javax.imageio.metadata.IIOMetadataNode("ColorTableEntry");
                    e.setAttribute("index", Integer.toString(i));
                    e.setAttribute("red", Integer.toString((rgb >> 16) & 255));
                    e.setAttribute("green", Integer.toString((rgb >> 8) & 255));
                    e.setAttribute("blue", Integer.toString(rgb & 255));
                    table.appendChild(e);
                }
                if (f == 0)
                {
                    javax.imageio.metadata.IIOMetadataNode apps = child(root, "ApplicationExtensions");
                    javax.imageio.metadata.IIOMetadataNode app = new javax.imageio.metadata.IIOMetadataNode("ApplicationExtension");
                    app.setAttribute("applicationID", "NETSCAPE");
                    app.setAttribute("authenticationCode", "2.0");
                    app.setUserObject(new byte[] {1, 0, 0});
                    apps.appendChild(app);
                }
                meta.setFromTree(fmt, root);
                writer.writeToSequence(new javax.imageio.IIOImage(frames[f], null, meta), null);
            }
            writer.endWriteSequence();
        }
        writer.dispose();
    }

    /**
     * One shared 256-colour palette for every frame (median cut over all of them), with error-diffusion dithering,
     * so the soft glow and the nebula survive the GIF instead of banding away.
     */
    private static BufferedImage[] indexed(BufferedImage[] frames)
    {
        int[] hist = new int[1 << 15];
        for (BufferedImage f : frames)
        {
            for (int y = 0; y < f.getHeight(); y++)
            {
                for (int x = 0; x < f.getWidth(); x++) hist[key(f.getRGB(x, y))]++;
            }
        }
        java.util.List<int[]> boxes = new java.util.ArrayList<>();
        int n = 0;
        for (int i = 0; i < hist.length; i++) if (hist[i] > 0) n++;
        int[] all = new int[n];
        n = 0;
        for (int i = 0; i < hist.length; i++) if (hist[i] > 0) all[n++] = i;
        boxes.add(all);
        while (boxes.size() < 256)
        {
            // split the box spread widest, at its weighted middle along its widest channel
            int best = -1, bestRange = 0, bestCh = 0;
            for (int b = 0; b < boxes.size(); b++)
            {
                int[] box = boxes.get(b);
                if (box.length < 2) continue;
                for (int ch = 0; ch < 3; ch++)
                {
                    int lo = 31, hi = 0;
                    for (int c : box)
                    {
                        int v = (c >> (10 - ch * 5)) & 31;
                        lo = Math.min(lo, v);
                        hi = Math.max(hi, v);
                    }
                    if (hi - lo > bestRange)
                    {
                        bestRange = hi - lo;
                        best = b;
                        bestCh = ch;
                    }
                }
            }
            if (best < 0) break;
            int shift = 10 - bestCh * 5;
            Integer[] box = java.util.Arrays.stream(boxes.get(best)).boxed().toArray(Integer[]::new);
            java.util.Arrays.sort(box, java.util.Comparator.comparingInt(c -> (c >> shift) & 31));
            long total = 0;
            for (int c : box) total += hist[c];
            long acc = 0;
            int cut = 1;
            for (int i = 0; i < box.length - 1; i++)
            {
                acc += hist[box[i]];
                cut = i + 1;
                if (acc * 2 >= total) break;
            }
            int[] a = new int[cut];
            int[] b = new int[box.length - cut];
            for (int i = 0; i < box.length; i++)
            {
                if (i < cut) a[i] = box[i];
                else b[i - cut] = box[i];
            }
            boxes.set(best, a);
            boxes.add(b);
        }
        int size = boxes.size();
        byte[] rs = new byte[size], gs = new byte[size], bs = new byte[size];
        int[][] pal = new int[size][3];
        for (int b = 0; b < size; b++)
        {
            long r = 0, g = 0, bl = 0, w = 0;
            for (int c : boxes.get(b))
            {
                int k = hist[c];
                r += (long) (((c >> 10) & 31) * 8 + 4) * k;
                g += (long) (((c >> 5) & 31) * 8 + 4) * k;
                bl += (long) ((c & 31) * 8 + 4) * k;
                w += k;
            }
            pal[b][0] = (int) (r / w);
            pal[b][1] = (int) (g / w);
            pal[b][2] = (int) (bl / w);
            rs[b] = (byte) pal[b][0];
            gs[b] = (byte) pal[b][1];
            bs[b] = (byte) pal[b][2];
        }
        java.awt.image.IndexColorModel cm = new java.awt.image.IndexColorModel(8, size, rs, gs, bs);
        int[] near = new int[1 << 15];
        java.util.Arrays.fill(near, -1);
        BufferedImage[] out = new BufferedImage[frames.length];
        for (int fi = 0; fi < frames.length; fi++)
        {
            BufferedImage f = frames[fi];
            int w = f.getWidth(), h = f.getHeight();
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_INDEXED, cm);
            java.awt.image.WritableRaster ras = img.getRaster();
            float[][] err = new float[2][(w + 2) * 3];
            for (int y = 0; y < h; y++)
            {
                float[] cur = err[y & 1];
                float[] next = err[(y + 1) & 1];
                java.util.Arrays.fill(next, 0f);
                for (int x = 0; x < w; x++)
                {
                    int p = f.getRGB(x, y);
                    int[] want = new int[3];
                    for (int ch = 0; ch < 3; ch++)
                    {
                        int v = (p >> (16 - ch * 8)) & 255;
                        want[ch] = Math.max(0, Math.min(255, Math.round(v + cur[(x + 1) * 3 + ch])));
                    }
                    int key = (want[0] >> 3) << 10 | (want[1] >> 3) << 5 | (want[2] >> 3);
                    if (near[key] < 0)
                    {
                        int bi = 0;
                        long bd = Long.MAX_VALUE;
                        for (int i = 0; i < size; i++)
                        {
                            long dr = pal[i][0] - ((want[0] & 0xf8) + 4);
                            long dg = pal[i][1] - ((want[1] & 0xf8) + 4);
                            long db = pal[i][2] - ((want[2] & 0xf8) + 4);
                            long d = dr * dr * 3 + dg * dg * 4 + db * db * 2;
                            if (d < bd)
                            {
                                bd = d;
                                bi = i;
                            }
                        }
                        near[key] = bi;
                    }
                    int idx = near[key];
                    ras.setSample(x, y, 0, idx);
                    for (int ch = 0; ch < 3; ch++)
                    {
                        float e = want[ch] - pal[idx][ch];
                        cur[(x + 2) * 3 + ch] += e * 7 / 16f;
                        next[x * 3 + ch] += e * 3 / 16f;
                        next[(x + 1) * 3 + ch] += e * 5 / 16f;
                        next[(x + 2) * 3 + ch] += e / 16f;
                    }
                }
            }
            out[fi] = img;
        }
        return out;
    }

    private static int key(int rgb)
    {
        return ((rgb >> 19) & 31) << 10 | ((rgb >> 11) & 31) << 5 | ((rgb >> 3) & 31);
    }

    private static javax.imageio.metadata.IIOMetadataNode child(javax.imageio.metadata.IIOMetadataNode root, String name)
    {
        for (int i = 0; i < root.getLength(); i++)
        {
            if (root.item(i).getNodeName().equals(name)) return (javax.imageio.metadata.IIOMetadataNode) root.item(i);
        }
        javax.imageio.metadata.IIOMetadataNode n = new javax.imageio.metadata.IIOMetadataNode(name);
        root.appendChild(n);
        return n;
    }

    private static BufferedImage gifFrame(float t, Color back, int cell, int up)
    {
        BufferedImage img = new BufferedImage(cell * up * 2, cell * up, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        BufferedImage small = new BufferedImage(cell, cell, BufferedImage.TYPE_INT_RGB);
        Graphics2D sg = small.createGraphics();
        sg.setColor(back);
        sg.fillRect(0, 0, cell, cell);
        // the slow clock sways round the start once per lap (top speed 1, as in the panel), so the loop closes
        double lap = Chexy.LAP;
        float drift = 3f + (float) (lap / (2 * Math.PI) * Math.sin(2 * Math.PI * (t - 3f) / lap));
        Chexy.paint(sg, cell / 2f + Chexy.bobX(t), cell / 2f + Chexy.bobY(t), t, drift, Chexy.SIZE, false, -1f);
        sg.dispose();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(small, 0, 0, cell * up, cell * up, null);
        g.setColor(back);
        g.fillRect(cell * up, 0, cell * up, cell * up);
        g.translate(cell * up, 0);
        g.scale(up, up);
        Chexy.paint(g, cell / 2f + Chexy.bobX(t), cell / 2f + Chexy.bobY(t), t, drift, Chexy.SIZE, false, -1f);
        g.dispose();
        return img;
    }

    private static BufferedImage frame(float t, Color back, int scale, boolean calm)
    {
        BufferedImage img = new BufferedImage(CELL * scale, CELL * scale, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(back);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.scale(scale, scale);
        Chexy.paint(g, CELL / 2f, CELL / 2f, t, Chexy.SIZE, calm);
        g.dispose();
        return img;
    }
}
