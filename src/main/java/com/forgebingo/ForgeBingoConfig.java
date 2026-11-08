package com.forgebingo;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(ForgeBingoConfig.GROUP)
public interface ForgeBingoConfig extends Config
{
    String GROUP = "forgebingo";
    /**
     * The live ForgeBingo plugin API (later the front domain, e.g.
     * https://api.forgebingo.com/v1 base, once it exists). Players never need to change it.
     */
    String DEFAULT_BASE_URL = "https://bkdhejyrltialyqjjnko.supabase.co/functions/v1/plugin-api";

    @ConfigSection(name = "Connection", description = "Server and key", position = 0)
    String connection = "connection";

    @ConfigSection(name = "Events to send", description = "Which in-game events are reported", position = 1)
    String events = "events";

    @ConfigItem(
        keyName = "apiBaseUrl",
        name = "API base URL",
        description = "ForgeBingo plugin API address. Must be https (http only for localhost). Leave as default unless your organizer told you otherwise.",
        warning = "This sends your API key, RuneScape name, account hash, drops, collection log, pets, kill counts and levels to the server you enter. Only use an address you trust.",
        section = connection,
        position = 0
    )
    default String apiBaseUrl()
    {
        return DEFAULT_BASE_URL;
    }

    @ConfigItem(
        keyName = "apiKey",
        name = "API key",
        description = "Create a key on the ForgeBingo website (Account settings).",
        secret = true,
        section = connection,
        position = 1
    )
    default String apiKey()
    {
        return "";
    }

    @ConfigItem(
        keyName = "sendNpcLoot", name = "NPC drops", description = "Report monster drops",
        section = events, position = 0)
    default boolean sendNpcLoot()
    {
        return true;
    }

    @ConfigItem(
        keyName = "sendChestLoot", name = "Raids, chests and clues",
        description = "Report raid/Barrows/minigame rewards and clue caskets. Needs the core Loot Tracker plugin enabled.",
        section = events, position = 1)
    default boolean sendChestLoot()
    {
        return true;
    }

    @ConfigItem(
        keyName = "sendCollectionLog", name = "Collection log slots",
        description = "Report new collection log entries. Needs the in-game 'Collection log - New addition notification' setting on.",
        section = events, position = 2)
    default boolean sendCollectionLog()
    {
        return true;
    }

    @ConfigItem(keyName = "sendPets", name = "Pets", description = "Report pet drops",
        section = events, position = 3)
    default boolean sendPets()
    {
        return true;
    }

    @ConfigItem(keyName = "sendKc", name = "Kill counts", description = "Report kill/completion counts shown in chat",
        section = events, position = 4)
    default boolean sendKc()
    {
        return true;
    }

    @ConfigItem(keyName = "sendLevels", name = "Level-ups", description = "Report skill level-ups",
        section = events, position = 5)
    default boolean sendLevels()
    {
        return true;
    }

    @ConfigItem(
        keyName = "screenshotOnMatch",
        name = "Screenshot on matched drop",
        description = "After the server confirms a drop counts for your team, upload a game-window screenshot as proof. On by default.",
        warning = "Screenshots of your game window (which may show chat) are sent to the ForgeBingo server, only after a drop is confirmed as counting.",
        position = 2
    )
    default boolean screenshotOnMatch()
    {
        return true;
    }

    @ConfigItem(
        keyName = "screenshotSidebar",
        name = "Include sidebar in screenshots",
        description = "Proof screenshots show the whole RuneLite window while the ForgeBingo panel is open in the sidebar (other plugin panels are never captured). Off by default.",
        warning = "Your whole RuneLite window (game, sidebar and the ForgeBingo panel) will be in your proof screenshots. Organizers and reviewers of your event can see them. Turn this on?",
        position = 3
    )
    default boolean screenshotSidebar()
    {
        return false;
    }

    /** A server address other than the ForgeBingo one that the player approved in the warning dialog. */
    @ConfigItem(keyName = "trustedApiHost", name = "", description = "", hidden = true)
    default String trustedApiHost()
    {
        return "";
    }

    @ConfigItem(keyName = "effects", name = "Celebrations", description = "Fireworks and banners over the game when a tile, a line or the whole board is finished (same as the website)",
        position = 4)
    default boolean effects()
    {
        return true;
    }

    @ConfigItem(keyName = "chatNotices", name = "Chat notices", description = "Show a game message when an event counts or is dropped",
        position = 5)
    default boolean chatNotices()
    {
        return true;
    }

    @ConfigSection(name = "Board overlay", description = "The bingo board drawn over the game", position = 6)
    String boardOverlay = "boardOverlay";

    @ConfigItem(keyName = "boardOverlayKey", name = "Show board on screen",
        description = "Opens or closes the bingo board over the game. Click a tile to see it bigger; click off the board or press Esc to close.",
        section = boardOverlay, position = 0)
    default Keybind boardOverlayKey()
    {
        return new Keybind(KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK);
    }

    @ConfigItem(keyName = "boardOverlayCloseOnHit", name = "Close when attacked",
        description = "Close the board overlay as soon as you take damage",
        section = boardOverlay, position = 1)
    default boolean boardOverlayCloseOnHit()
    {
        return true;
    }

    @ConfigItem(keyName = "boardOverlayAnimations", name = "Overlay animations",
        description = "Fade the board in and out and pop tiles open. Turn off to reduce motion.",
        section = boardOverlay, position = 2)
    default boolean boardOverlayAnimations()
    {
        return true;
    }

    @ConfigItem(keyName = "activeBoardId", name = "", description = "", hidden = true)
    default String activeBoardId()
    {
        return "";
    }
}
