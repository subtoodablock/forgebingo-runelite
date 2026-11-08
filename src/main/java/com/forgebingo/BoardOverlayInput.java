package com.forgebingo;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.util.HotkeyListener;

/**
 * Mouse and keys for the board overlay. Runs on the AWT thread. While the overlay is closed nothing is consumed; while
 * it is open every click is swallowed (press, release and click) so the player never walks or attacks by accident,
 * and Esc closes the card, then the overlay. Mouse coordinates are already in game canvas space: RuneLite's stretched
 * mode registers its translating listener first (index 0), and overlays draw in that same space.
 */
class BoardOverlayInput implements MouseListener, KeyListener
{
    private final Client client;
    private final BoardOverlay overlay;
    private final HotkeyListener hotkey;
    /** the press was ours: swallow its release and click too */
    private volatile boolean swallowRelease;
    private volatile boolean swallowClick;

    BoardOverlayInput(Client client, BoardOverlay overlay, ForgeBingoConfig config)
    {
        this.client = client;
        this.overlay = overlay;
        this.hotkey = new HotkeyListener(config::boardOverlayKey)
        {
            @Override
            public void hotkeyPressed()
            {
                if (client.getGameState() == GameState.LOGGED_IN) overlay.toggle();
            }
        };
    }

    void register(MouseManager mouseManager, KeyManager keyManager)
    {
        mouseManager.registerMouseListener(this);
        keyManager.registerKeyListener(this);
        keyManager.registerKeyListener(hotkey);
    }

    void unregister(MouseManager mouseManager, KeyManager keyManager)
    {
        mouseManager.unregisterMouseListener(this);
        keyManager.unregisterKeyListener(this);
        keyManager.unregisterKeyListener(hotkey);
        swallowRelease = false;
        swallowClick = false;
    }

    private boolean active()
    {
        return overlay.isOpen() && client.getGameState() == GameState.LOGGED_IN;
    }

    // ---------- mouse ----------

    @Override
    public MouseEvent mousePressed(MouseEvent e)
    {
        swallowRelease = false;
        swallowClick = false;
        if (!active()) return e;
        overlay.click(e.getX(), e.getY());
        swallowRelease = true;
        swallowClick = true;
        e.consume();
        return e;
    }

    @Override
    public MouseEvent mouseReleased(MouseEvent e)
    {
        if (swallowRelease)
        {
            swallowRelease = false;
            e.consume();
        }
        return e;
    }

    @Override
    public MouseEvent mouseClicked(MouseEvent e)
    {
        if (swallowClick)
        {
            swallowClick = false;
            e.consume();
        }
        return e;
    }

    @Override
    public MouseEvent mouseMoved(MouseEvent e)
    {
        // hover only; never consumed so the game keeps tracking the mouse
        if (active()) overlay.hover(e.getX(), e.getY());
        return e;
    }

    @Override
    public MouseEvent mouseDragged(MouseEvent e)
    {
        return e;
    }

    @Override
    public MouseEvent mouseEntered(MouseEvent e)
    {
        return e;
    }

    @Override
    public MouseEvent mouseExited(MouseEvent e)
    {
        if (overlay.isOpen()) overlay.hover(-1, -1);
        return e;
    }

    // ---------- keys ----------

    @Override
    public void keyPressed(KeyEvent e)
    {
        if (e.getKeyCode() != KeyEvent.VK_ESCAPE || !active()) return;
        if (overlay.cardOpen()) overlay.closeCard(); else overlay.close();
        e.consume();
    }

    @Override
    public void keyReleased(KeyEvent e)
    {
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE && active()) e.consume();
    }

    @Override
    public void keyTyped(KeyEvent e)
    {
        if (e.getKeyChar() == KeyEvent.VK_ESCAPE && active()) e.consume();
    }
}
