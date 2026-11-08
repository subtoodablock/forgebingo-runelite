# ForgeBingo

Clan bingo tracking for [ForgeBingo](https://forgebingo.vercel.app) events. Your drops, raid and clue loot, pets, kill
counts and level-ups count for your team automatically, so you don't have to screenshot everything. Your team's board
shows in a sidebar panel and, with **Ctrl+B**, on top of the game.

## Getting started

1. Join your team on the website with the invite link your organizer sent you.
2. On your team board, click **ForgeBingo plugin: make your API key** and copy the key (shown once).
   One key covers every bingo you play with a ForgeBingo account.
3. In RuneLite, open the ForgeBingo settings and paste the key into **API key**. Leave **API base URL** as it is.
4. Open the ForgeBingo panel and click **Refresh**. Your bingo, team and board appear.

## What it tracks

| In game | How |
| --- | --- |
| Monster drops | RuneLite's loot events (the drop counts when it lands, you don't have to pick it up) |
| Raid, Barrows, minigame and clue rewards | RuneLite's loot tracker events |
| Pickpocketing | only when the loot holds an item on your board |
| Collection log slots, pets | the game's chat messages |
| Kill and completion counts, clue completions | the game's chat messages |
| Level-ups | skill level changes |
| Reward-shop buys and opened containers (activity uniques) | new items in your backpack, except trades, bank withdrawals, Grand Exchange collects, ground pickups and anything in the Wilderness or on PvP worlds |

The ForgeBingo server decides what counts for your board. Items picked up off the ground and loot from other players
never count.

## Data sent to ForgeBingo

This plugin sends data to the ForgeBingo server (a third-party service, not run by RuneLite or Jagex):

- your API key, RuneScape name and account hash (to link you to your team and stop name changes from being abused)
- the drops, loot, collection log slots, pets, kill counts and levels listed above, with the time they happened
- a screenshot of the game window as proof, only after the server confirms a drop counts for your team
  (**Screenshot on matched drop**, on by default; turn it off to send none). The screenshot may show your chat box.
  Screenshots are never taken while the bank PIN keypad is open or while you are logged out, and the sidebar is only
  included if you turn on **Include sidebar in screenshots**

It never sees your password, bank PIN or authenticator, and it never reads private messages or what you type.
Turn off any event type under **Events to send**. Revoke your key on the website at any time.
Full details: [ForgeBingo privacy page](https://forgebingo.vercel.app/privacy).

## Board on screen

Press **Ctrl+B** (or **Show board on screen** in the panel) to open your board over the game. Click a tile for its
details. Click anywhere off the board or press Esc to close it. While it is open it takes your mouse clicks, so
you don't walk where you click; it closes by itself when a monster attacks you (**Close when attacked**).

## Settings

- **Connection:** API base URL (leave as default), API key
- **Events to send:** NPC drops; raids, chests and clues; collection log slots; pets; kill counts; level-ups
- **Screenshots:** screenshot on matched drop (on), include sidebar in screenshots (off)
- **Celebrations** and **chat notices**
- **Board overlay:** hotkey, close when attacked, animations

## Building

```bash
./gradlew build
./gradlew run   # RuneLite in developer mode with the plugin loaded
```

## License

BSD 2-Clause, see [LICENSE](LICENSE).
