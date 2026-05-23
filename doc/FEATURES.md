# What Tachyon adds

Tachyon is a UI/multiplayer layer sitting on top of [Project
Wormhole](https://gitlab.com/znixian/xftl). The engine, asset loading, AI,
sectors and endgame all come from upstream — this document covers only the
pieces Tachyon adds.

## Developer menu

An always-visible menu bar across the top of the window, drawn with the engine's
own renderer (no extra dependencies). The game is rendered below the bar, so the
menu never covers it.

| Menu | Contents |
|------|----------|
| **File** | New Game, Save Game, Saved Games (save manager), Mods, Quit |
| **Graphics** | V-Sync toggle, FPS counter, window-size presets (720p up to 4K) plus Fill Screen, borderless fullscreen |
| **Audio** | Sound-effect and music volume sliders |
| **Debug** | Cheats, Outfitter, Ship Stats, Tuning, Spawn Enemy Ship, Load Event, plus End Run (Victory/Defeat) |
| **Multiplayer** | Host or join a co-op session — see [Co-op multiplayer](#co-op-multiplayer) |
| **About** | Credits, license and repository links |

The windows the dev menu opens:

- **Cheats** — a You/Enemy grid for Ship Invincible, Crew Invincible, Fast
  Weapons, Infinite Missiles and Infinite Drones (each side toggles
  independently), plus Reveal Map / Jump Anywhere / No Enemy Weapons and one-shot
  actions (repair, heal, max resources, upgrade systems, max crew skills, destroy
  enemy).
- **Ship Stats** — a live view of the player ship (hull, scrap, fuel, missiles,
  drone parts, per-crew health) with steppers to edit each value on the fly.
- **Outfitter** — a tabbed picker that drops crew, weapons, drones, augments and
  systems straight onto the ship, so you never have to find a store.
- **Tuning** — a Game Speed slider, plus world-generation sliders (nebula-sector
  frequency, beacon density, hazard-beacon chance, extra-store chance) that take
  effect on newly generated sectors.
- **Spawn Enemy Ship** / **Load Event** — pick any enemy ship or any event and
  apply it at the current beacon.
- **Saved Games** — a multi-slot save manager (save, load, delete).
- **Mods** — enable, disable and reorder Slipstream mods, written to
  `order.txt`.

Cheat toggles bind to the engine's existing `DebugFlagManager`; the actions and
pickers mirror the debug console's commands. Saves are written in the debug
console's XML format, so dev-menu saves and console saves are interchangeable.

Implementation lives in
[`src/main/java/xyz/znix/xftl/devmenu/`](../src/main/java/xyz/znix/xftl/devmenu/):
`DevUI` (a small immediate-mode toolkit), `DevMenu` (the bar), `DevWindows`,
`DevActions`.

## Co-op multiplayer

Host-authoritative co-op: two players sharing one ship over the network. The
host runs the only real simulation and streams game-state snapshots to the
client; the client sends commands back.

### Connection
One player hosts, the other joins by IP address. The handshake exchanges a
magic string and protocol version over a single TCP connection. For internet
play the host forwards TCP port `7777`; on a LAN the **Multiplayer** menu shows
the host's address.

### State streaming
The host serialises its game about five times a second; the client rebuilds and
renders each snapshot instead of simulating, so it always shows the host's
authoritative state. The snapshot is gzip-compressed on the wire (an XML game
state compresses heavily), keeping the stream light enough for internet play.

### Commands
Player input is funnelled into `Command` objects rather than mutating the game
directly. On the host (and in single-player) a command is applied at once; on
the client it is sent to the host, which applies it and streams the result
back. The following actions all go through this path, so a client is a full
co-pilot:

- doors, crew movement, system power
- weapon arming, room-targeting and beam-aim
- event dialogue choices
- pause / unpause
- system and reactor upgrades, with right-click undo and "undo all"
- crew dismiss and rename
- equipment swap (drag-drop, including sell-for-scrap)
- beacon jumps within a sector and next-sector jumps

### Client UI state
The client keeps its own crew selection, resource counters and any open local
windows (jump map, ship overview, options) across snapshots, so its menus don't
vanish when the game is rebuilt from the host's stream.

### Shared cursor
Each player's mouse position is sent to the other and drawn as a small amber
crosshair, so co-op players can point things out to each other.

### Unfocused rendering
While a co-op session is connected the game keeps rendering even when its
window is not focused, so both players' windows stay live on a shared screen
(useful for same-machine testing, screenshare play, or just glancing at the
ship while doing something else).

Netcode lives in
[`src/main/java/xyz/znix/xftl/net/`](../src/main/java/xyz/znix/xftl/net/) —
`Multiplayer.kt` (connection, streaming, message protocol) and `Command.kt`
(the shared input path). It adds no new dependencies, just Java sockets.

## End-of-run score screen

Upstream left the score screen as a placeholder. Tachyon implements it.

A per-run `GameStats` tracks scrap collected, ships destroyed and beacons
explored (saved with the game); the Game Over window shows a real score and a
**Stats** button that toggles a full breakdown. Score is
`scrap + ships×15 + beacons×4 + sectors×25`, doubled on a win.
