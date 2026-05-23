# Tachyon — attribution and license

Tachyon is a fork of [Project Wormhole](https://gitlab.com/znixian/xftl), a
clean-sheet open-source re-implementation of the *FTL: Faster Than Light*
engine. Tachyon adds two-player co-op multiplayer, an in-game developer
menu, and an end-of-run score screen on top of upstream's engine.

## Source code

The complete corresponding source code for any binary build of Tachyon is
available at:

- **Tachyon:** https://github.com/sp00nznet/Tachyon
- **Upstream Project Wormhole:** https://gitlab.com/znixian/xftl

This satisfies GPL-2.0 §3 for binary distributions.

## License

Tachyon is licensed under the **GNU General Public License v2.0 or later**
(GPL-2.0-or-later), matching upstream. See [`LICENCE.txt`](LICENCE.txt) for
the full license text.

## Third-party components

Binary builds of Tachyon bundle the following components; each retains its
own license, included with the binary (in `META-INF/` inside the jar, and in
`runtime/legal/` for the bundled JRE).

| Component | License | Source |
|-----------|---------|--------|
| LWJGL 3 | BSD-3-Clause | https://www.lwjgl.org/ |
| Slick2D (image/audio decoders only) | BSD-3-Clause | https://slick.ninjacave.com/ |
| JDOM2 | Apache-style with attribution | https://github.com/hunterhacker/jdom |
| JOrbis | LGPL-2.1-or-later | http://www.jcraft.com/jorbis/ |
| Slipstream Mod Manager | LGPL-2.1-or-later | https://github.com/blizzarchon/Slipstream-Mod-Manager |
| Kotlin Standard Library | Apache-2.0 | https://kotlinlang.org/ |
| Roboto font | Apache-2.0 | https://fonts.google.com/specimen/Roboto |
| Bundled JRE (Windows builds only) | GPL-2.0 with Classpath Exception | https://adoptium.net/ |

## Subset Games' FTL assets

*FTL: Faster Than Light* is © Subset Games. **No FTL assets are included in
this repository or in any binary build.** Tachyon reads FTL's art, audio,
text and ship data from your own copy of `ftl.dat` at runtime; you must
own a copy of FTL to use Tachyon.

## Co-Authored work

Several commits in this fork are co-authored with Claude (Anthropic) under
the `Co-Authored-By: Claude Opus 4.7 ...` trailer — they remain GPL-2.0
licensed under this project alongside every other commit.
