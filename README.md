# Tachyon

**Tachyon** is a private fork of [Project Wormhole](https://gitlab.com/znixian/xftl) — a
clean-sheet, open-source re-implementation of the *FTL: Faster Than Light* game engine.
It contains no original FTL game content: it loads the art, audio, text and ship data
straight out of a legitimate copy of the game (`ftl.dat`).

> **You must own FTL: Faster Than Light to use this.** Tachyon is an engine only.
> It reads assets from your existing Steam/GOG/Epic/Humble installation.

---

## What this is

FTL's own engine is closed-source. Project Wormhole (upstream) re-builds that engine
from scratch in Kotlin, driving it with the data files shipped in the retail game. The
goal is a faithful, moddable, cross-platform reimplementation.

This repository is a fork. The engine itself is upstream's work; what Tachyon adds
on top is a **[developer menu](#developer-menu)** — an in-game menu bar for saving,
loading, graphics/audio settings, cheats and live game inspection. Upstream credit
goes entirely to **Campbell Suter (ZNix)** and contributors — see [Attribution](#attribution).

## Status — assessed 2026-05-19

The project is **substantially complete and playable**. Built and run against a retail
Steam copy of FTL: Advanced Edition; it loads all assets, boots to the ship-select /
ship-editor screen, and renders FTL's original sprites correctly.

| Area | State |
|------|-------|
| Build | ✅ Clean build on JDK 17–21 (Gradle, Kotlin 1.9) |
| Asset loading | ✅ Reads `ftl.dat`, including Slipstream-patched mods |
| Ship systems | ✅ All 19 (shields, weapons, engines, oxygen, medbay, clonebay, teleporter, cloaking, hacking, mind control, drones, artillery, doors, piloting, sensors, backup battery, etc.) |
| Weapons | ✅ Lasers, missiles, beams, bombs, flak/burst, drones |
| Crew & AI | ✅ Crew AI, combat AI, ship AI, boarding |
| Sectors | ✅ Sector map, events, stores, environments (asteroids, nebula, pulsar, sun) |
| Endgame | ✅ Rebel flagship / boss logic |
| Save / load | ✅ Save game + debug saves |
| Modding | ✅ Slipstream mod manager bundled as a library |
| Developer menu | ✅ Tachyon's own addition — see below |
| Rough edges | ⚠️ macOS save-path is a stub; some augments load without gameplay logic; no tagged releases |

Scale: ~50,700 lines of Kotlin across ~200 files. Last upstream commit: 2024-06-30.

## Developer menu

Tachyon adds an always-visible menu bar across the top of the window, drawn with the
engine's own renderer (no extra dependencies). The game is rendered below the bar, so
the menu never covers it.

| Menu | Contents |
|------|----------|
| **File** | New Game, Save Game, Saved Games (save manager), Mods, Quit |
| **Graphics** | V-Sync toggle, FPS counter, window-size presets (720p up to 4K) plus Fill Screen, borderless fullscreen |
| **Audio** | Sound-effect and music volume sliders |
| **Debug** | Opens the Cheats, Outfitter, Game Inspector, Tuning, Spawn Enemy Ship and Load Event windows, plus End Run (Victory/Defeat) |
| **Multiplayer** | Host or join a co-op session — see [Co-op multiplayer](#co-op-multiplayer) below |
| **About** | Credits, license and repository links |

The windows the dev menu opens:

- **Cheats** — a You/Enemy grid for Ship Invincible, Crew Invincible, Fast Weapons,
  Infinite Missiles and Infinite Drones (each side toggles independently), plus Reveal
  Map / Jump Anywhere / No Enemy Weapons and one-shot actions (repair, heal, max
  resources, upgrade systems, max crew skills, destroy enemy).
- **Game Inspector** — a live view of the player ship (hull, scrap, fuel, missiles,
  drone parts, per-crew health) with steppers to edit each value on the fly.
- **Outfitter** — a tabbed picker that drops crew, weapons, drones, augments and
  systems straight onto the ship, so you never have to find a store.
- **Tuning** — a Game Speed slider, plus world-generation sliders (nebula-sector
  frequency, beacon density, hazard-beacon chance, extra-store chance) that take
  effect on newly generated sectors.
- **Spawn Enemy Ship** / **Load Event** — pick any enemy ship or any event and
  apply it at the current beacon.
- **Saved Games** — a multi-slot save manager (save, load, delete).
- **Mods** — enable, disable and reorder Slipstream mods, written to `order.txt`.

Cheat toggles bind to the engine's existing `DebugFlagManager`; the actions and pickers
mirror the debug console's commands. Saves are written in the debug console's XML
format, so dev-menu saves and console saves are interchangeable.

## Co-op multiplayer

Tachyon adds **host-authoritative co-op** — two players sharing one ship over the
network. It is being built as a vertical slice; the current state:

- **Connection** — one player hosts, the other joins by IP address. The handshake
  exchanges a magic string and protocol version over a single TCP connection. For
  internet play the host forwards TCP port `7777`; on a LAN the **Multiplayer** menu
  shows the host's address.
- **State streaming** — the host runs the only real simulation and serialises its
  game about five times a second; the client rebuilds and renders each snapshot
  instead of simulating, so it always shows the host's authoritative state.
- **Commands** — player input is funnelled into `Command` objects rather than
  mutating the game directly. On the host (and in single-player) a command is applied
  at once; on the client it is sent to the host, which applies it and streams the
  result back. Opening doors and moving crew are wired through this path; the client
  keeps its own crew selection across snapshots. The remaining actions (system power,
  weapons) are being moved onto it action by action.

The netcode is in [`src/main/java/xyz/znix/xftl/net/`](src/main/java/xyz/znix/xftl/net/)
— `Multiplayer.kt` (connection, streaming, message protocol) and `Command.kt` (the
shared input path). It adds no new dependencies, just Java sockets.

## End-of-run score screen

Tachyon implements the game-over **score screen**, which upstream left as a placeholder.
A per-run `GameStats` tracks scrap collected, ships destroyed and beacons explored (saved
with the game); the Game Over window shows a real score and a **Stats** button that
toggles a full breakdown. Score is `scrap + ships×15 + beacons×4 + sectors×25`, doubled
on a win.

Implementation lives in [`src/main/java/xyz/znix/xftl/devmenu/`](src/main/java/xyz/znix/xftl/devmenu/)
(`DevUI` — a small immediate-mode toolkit, `DevMenu` — the bar, `DevWindows`, `DevActions`).

## Requirements

- **JDK 17 or newer** (tested on Temurin 21)
- **A copy of FTL: Faster Than Light** (Advanced Edition recommended) — Steam, GOG, Epic or Humble
- ~1 GB RAM free; an OpenGL-capable GPU

## Building

```sh
# Compile + run tests + produce jars
./gradlew build            # gradlew.bat on Windows

# Produce a single runnable fat jar (all dependencies + native libs bundled)
./gradlew fatJar
# -> build/libs/XFTL-complete.jar
```

## Running

```sh
# Easiest: run via Gradle
./gradlew run

# Or run the fat jar directly
java -jar build/libs/XFTL-complete.jar
```

On first launch the engine tries to auto-detect FTL (Steam library folders, Epic).
If your `ftl.dat` lives somewhere it can't find it (e.g. directly in the FTL folder
rather than a `data/` subfolder), point it there explicitly:

```sh
# Environment variable
XFTL_DATAFILE="/path/to/FTL Faster Than Light/ftl.dat" java -jar build/libs/XFTL-complete.jar

# or JVM system property
java -Dxftl.datafile-path="/path/to/ftl.dat" -jar build/libs/XFTL-complete.jar
```

You can also pick the file in the in-game selection screen, or edit `ftl-path.txt`
in the save directory:

- **Windows:** `%USERPROFILE%\Saved Games\Project Wormhole\`
- **Linux:** `$XDG_DATA_HOME/ProjectWormhole/` (or `~/.local/share/ProjectWormhole/`)

### Useful command-line flags

| Flag | Effect |
|------|--------|
| `--new-game <SHIP_ID>` | Jump straight into a new game with the given ship blueprint |
| `--load-debug-save <name>` | Load a debug save from the debug-saves directory |
| `--mp-host` | Start hosting a co-op session on launch |
| `--mp-join <address>` | Join a co-op session at the given address on launch |
| `--mp-test` | While spectating, periodically fire a door-toggle command (co-op test harness) |

## Project layout

```
build.gradle.kts          Gradle build (Kotlin DSL)
settings.gradle.kts       Multi-project setup (root + :slipstream)
slipstream/               Slipstream Mod Manager, vendored as a library
src/main/java/xyz/znix/xftl/
  game/                   Main game loop, in-game state, UI windows
  systems/                Ship systems (shields, weapons, oxygen, ...)
  weapons/                Weapon & projectile blueprints and instances
  crew/                   Crew entities and behaviour
  ai/                     Combat / ship / crew AI
  drones/                 Drone types
  environment/            Hazards: asteroids, nebula, pulsar, sun
  sector/                 Sector map, events, generation
  shipgen/                Procedural enemy ship generation
  rendering/              OpenGL/LWJGL rendering layer
  modding/                Slipstream integration
  savegame/               Save/load
  sys/                    Platform glue, FTL install detection
  devmenu/                Developer menu (Tachyon's addition)
doc/                      Reverse-engineering notes on FTL mechanics
```

## Tech stack

- **Kotlin** 1.9 / Java 17 bytecode
- **LWJGL 3.3** — GLFW, OpenGL, OpenAL
- **Slick2D** — image/audio decoders only (LWJGL 2 parts excluded)
- **JDOM2** — XML parsing
- **Slipstream Mod Manager** — on-the-fly mod patching
- **Gradle** with the Kotlin DSL

## Attribution

Tachyon is a fork. All engine code is the work of the upstream project:

- **Upstream:** Project Wormhole — <https://gitlab.com/znixian/xftl>
- **Author:** Campbell Suter (ZNix) and contributors
- Bundled: [Slipstream Mod Manager](https://github.com/blizzarchon/Slipstream-Mod-Manager)
- *FTL: Faster Than Light* is © Subset Games. This project ships **none** of its assets.

To pull future upstream changes:

```sh
git remote add upstream https://gitlab.com/znixian/xftl.git
git fetch upstream
git merge upstream/master
```

## License

Licensed under the **GNU General Public License v2.0 or later** (GPL-2.0-or-later),
matching upstream. See [`LICENCE.txt`](LICENCE.txt). The bundled Roboto font is under
the Apache License 2.0 (`src/main/resources/roboto-font/LICENSE.txt`).
