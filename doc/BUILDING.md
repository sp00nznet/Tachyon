# Building and running Tachyon

Everything you need to compile, run and hack on the project. The short-form
"just run it" instructions live in the top-level [`README.md`](../README.md);
this doc is the full reference.

## Requirements

- **JDK 17 or newer** — tested on Temurin 17 and 21. Kotlin 1.9 emits Java 17
  bytecode.
- **A copy of FTL: Faster Than Light** (Advanced Edition recommended) on
  Steam, GOG, Epic or Humble. Tachyon reads `ftl.dat` from that install.
- About 1 GB of free RAM and an OpenGL-capable GPU.

## Building

```sh
# Compile, run tests, produce regular jars
./gradlew build            # gradlew.bat on Windows

# Produce a single runnable fat jar with all dependencies + native libs bundled
./gradlew fatJar
# -> build/libs/XFTL-complete.jar
```

The build is cross-platform — Linux, Windows and macOS hosts all produce the
same jars. The fat jar bundles every platform's LWJGL natives, so it can be
copied to another OS and run there with just a JDK installed.

### Standalone Windows .exe

For users without a JDK installed, package the game as a self-contained
Windows application with `jpackage` (which ships in JDK 14+):

```sh
./gradlew packageWindows
# -> build/dist/Tachyon/Tachyon.exe  (plus a runtime/ folder, ~180 MB total)
```

Zip up `build/dist/Tachyon/` and ship it — the user extracts, double-clicks
`Tachyon.exe`, and runs the game without ever installing Java. (jpackage
can only target the host OS, so produce the Windows build on Windows.)

## Running

```sh
# Easiest: run through Gradle
./gradlew run

# Or run the fat jar directly
java -jar build/libs/XFTL-complete.jar
```

### Pointing Tachyon at `ftl.dat`

On first launch the engine tries to auto-detect FTL via Steam library folders
(`libraryfolders.vdf`) and the standard Epic install location. If it can't
find your install you get a picker screen — auto-detected paths plus a
**Browse…** button that opens a native file dialog so you can point at
`ftl.dat` directly.

You can also tell the engine where to look up front:

```sh
# Environment variable
XFTL_DATAFILE="/path/to/FTL Faster Than Light/ftl.dat" java -jar build/libs/XFTL-complete.jar

# or JVM system property
java -Dxftl.datafile-path="/path/to/ftl.dat" -jar build/libs/XFTL-complete.jar
```

You can also pick the file in the in-game selection screen, or edit
`ftl-path.txt` inside the save directory:

| OS | Save directory |
|----|----------------|
| Windows | `%USERPROFILE%\Saved Games\Project Wormhole\` |
| Linux | `$XDG_DATA_HOME/ProjectWormhole/` (defaults to `~/.local/share/ProjectWormhole/`) |
| macOS | the save-path glue is a stub upstream — patches welcome |

## Useful command-line flags

| Flag | Effect |
|------|--------|
| `--new-game <SHIP_ID>` | Jump straight into a new game with the given ship blueprint |
| `--load-debug-save <name>` | Load a debug save from the debug-saves directory |
| `--mp-host` | Start hosting a co-op session on launch |
| `--mp-join <address>` | Join a co-op session at the given address on launch |
| `--mp-test` | While spectating, periodically fire test commands at the host (co-op test harness) |

Pass them through Gradle's `run` task with `./gradlew run --args='--mp-host'`,
or straight to the fat jar:

```sh
java -jar build/libs/XFTL-complete.jar --new-game PLAYER_SHIP_HARD --mp-host
```

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
  net/                    Co-op netcode (Multiplayer.kt, Command.kt)   ← Tachyon
  devmenu/                Developer menu                               ← Tachyon
doc/                      Reverse-engineering notes on FTL mechanics
```

The two `← Tachyon` packages are this fork's additions. Everything else is
upstream.

## Tech stack

- **Kotlin** 1.9 / Java 17 bytecode
- **LWJGL 3.3** — GLFW, OpenGL, OpenAL
- **Slick2D** — image/audio decoders only (LWJGL 2 parts excluded)
- **JDOM2** — XML parsing
- **Slipstream Mod Manager** — on-the-fly mod patching
- **Gradle** with the Kotlin DSL

## Continuous integration

[`.gitlab-ci.yml`](../.gitlab-ci.yml) drives a four-stage pipeline —
*build → test → publish:fatjar → deploy:windows*. The publish stage uploads
`build/libs/XFTL-complete.jar` as a `tachyon-<short-sha>` artifact; the
optional deploy stage SMB-pushes it to
`\\$SMB_HOST\$SMB_SHARE\Tachyon\<branch-or-tag>\` on master, tags and manual
web triggers. The pipeline expects a Windows shell runner tagged
`windows`, `win10`, `shell` with JDK 17 installed machine-wide. Drop the
`deploy:windows` job and you can run the rest on any JDK 17 Linux image.

## Pulling upstream changes

```sh
git remote add upstream https://gitlab.com/znixian/xftl.git
git fetch upstream
git merge upstream/master
```
