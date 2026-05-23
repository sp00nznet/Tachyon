# Tachyon

A fork of [Project Wormhole](https://gitlab.com/znixian/xftl) — Campbell Suter
(**ZNix**)'s open-source re-implementation of the *FTL: Faster Than Light* engine —
that adds two-player co-op multiplayer, an in-game developer menu, and an
end-of-run score screen. All the hard engine work is upstream's; Tachyon is a UI
and multiplayer layer on top of those shoulders.

> **You must own FTL: Faster Than Light to use this.** Tachyon ships none of
> FTL's original assets — it reads art, audio and ship data straight from your
> existing copy of `ftl.dat` (Steam, GOG, Epic or Humble).

## What this fork adds

- **Co-op multiplayer** — host-authoritative, two players sharing one ship. Every
  player action becomes a `Command` that runs on the host; the client renders
  snapshots streamed back. Doors, crew, system power, weapons, events, upgrades,
  beacon jumps, next-sector jumps and equipment swap all sync.
- **Developer menu** — an always-visible top bar with cheats, ship stats, the
  outfitter, world-generation tuning, a multi-slot save manager, the Slipstream
  mods panel, audio/graphics options and an event picker.
- **End-of-run score screen** — upstream left this as a placeholder; Tachyon
  scores `scrap + ships×15 + beacons×4 + sectors×25`, doubled on a win.

Full details: **[`doc/FEATURES.md`](doc/FEATURES.md)**.

Everything else — the engine, asset loading, AI, ship systems, sectors, events,
the endgame — is upstream's. None of that would exist without ZNix's work; see
[Attribution](#attribution).

## Run it

Need **JDK 17+** and a copy of FTL.

```sh
./gradlew run
```

On first launch Tachyon auto-detects FTL through the Steam library folders. If
it can't find `ftl.dat`, pick it in the in-game selection screen — or for the
full reference (fat jar, env vars, OS-specific save paths) see
[`doc/BUILDING.md`](doc/BUILDING.md).

## Play co-op

One player hosts, the other joins by IP (TCP `7777` — forward it for internet
play, or use a LAN address from the in-game **Multiplayer** menu).

```sh
# Host
./gradlew run --args='--mp-host'

# Join
./gradlew run --args='--mp-join 192.168.1.42'
```

Or skip the flags entirely and use **Multiplayer → Host a Game** / **Join** from
the in-game menu bar. The host's LAN address is shown right there.

## Attribution

The engine is **upstream's work**. Tachyon adds UI/multiplayer; the rest exists
because of:

- **[Project Wormhole](https://gitlab.com/znixian/xftl)** — Campbell Suter (ZNix)
  and contributors. The clean-sheet FTL engine, asset pipeline, AI, save format —
  effectively everything that runs FTL — is theirs.
- **[Slipstream Mod Manager](https://github.com/blizzarchon/Slipstream-Mod-Manager)**
  — bundled as a library for on-the-fly mod patching.
- **FTL: Faster Than Light** is © Subset Games. None of FTL's assets ship in this
  repository.

To pull future upstream changes:

```sh
git fetch upstream && git merge upstream/master
```

## License

**GNU General Public License v2.0 or later** (GPL-2.0-or-later), matching
upstream. See [`LICENCE.txt`](LICENCE.txt). The bundled Roboto font is Apache 2.0
(`src/main/resources/roboto-font/LICENSE.txt`).
