package xyz.znix.xftl.devutil

import org.newdawn.slick.*
import xyz.znix.xftl.Datafile
import xyz.znix.xftl.SILFontLoader
import java.io.File

object FontPalette {
    @JvmStatic
    fun main(args: Array<String>) {
        val df = Datafile(File("/home/znix/Static/Games/FTL-linux/data/ftl.dat"))

        // TODO automatic extraction
        if (System.getProperty("org.lwjgl.librarypath") == null) System.setProperty(
            "org.lwjgl.librarypath",
            File("natives").absolutePath
        )

        // Enable stenciling support
        GameContainer.enableStencil()

        val gc = AppGameContainer(GameImpl(df))
        gc.setTargetFrameRate(120)
        gc.setDisplayMode(1024, 768, false)
        gc.setShowFPS(false)
        gc.start()
    }

    val FONT_NAMES = listOf(
        "c&c",
        "c&cnew",
        "HL1",
        "HL2",
        "JustinFont8",
        "JustinFont10",
        "JustinFont11Bold",
        "JustinFont12Bold",
        "num_font"
    )

    val DEFAULT_TEXT = "The quick brown fox jumps over the lazy dog"
    val HELP_MSG = "Press F1 to clear the string, F2 for sample, F3 and arrow keys to view rawfonts, F4 for" +
            " baseline, F5 for legacy mode, type to edit"

    val BASELINE_COLOUR = Color(255, 0, 0, 128)

    private class GameImpl(val df: Datafile) : BasicGame("Font Palette") {
        private val fonts = HashMap<String, SILFontLoader>()
        private val pictures = HashMap<String, Image>()
        private var text = DEFAULT_TEXT

        private var rawFontMode = false
        private var rawFontIdx: Int = 0

        private var baseline = false
        private var legacyMode = false

        override fun update(container: GameContainer, delta: Int) {}

        override fun render(container: GameContainer, g: Graphics) {
            g.background = Color.white
            g.clear()

            val utilFont = fonts["JustinFont8"]!!

            utilFont.drawString(50f, 20f, HELP_MSG, Color.black)

            if (rawFontMode) {
                val name = FONT_NAMES[rawFontIdx]
                utilFont.drawString(50f, 35f, "Selected font: (idx $rawFontIdx) $name", Color.black)
                pictures[name]!!.draw(50f, 50f, Color.black)
                return
            }

            utilFont.drawString(50f, 35f, "Legacy: $legacyMode", Color.black)

            val drawStr = when {
                text.endsWith(' ') || text.startsWith(' ') -> "'$text'"
                else -> text
            }

            for ((i, name) in FONT_NAMES.withIndex()) {
                val fnt = fonts[name]!!
                val y = 50f + 25 * i
                utilFont.drawString(20f, y, name, Color.black)

                @Suppress("DEPRECATION")
                val drawFunc = if (legacyMode) fnt::drawStringLegacy else fnt::drawString
                drawFunc(150f, y, drawStr, Color.black)

                if (baseline) {
                    g.color = BASELINE_COLOUR
                    g.drawLine(150f, y, 150f + fnt.getWidth(drawStr), y)
                }
            }
        }

        override fun init(container: GameContainer) {
            // Hack here so we don't have to make it public, won't be used anywhere else
            val field = SILFontLoader::class.java.getDeclaredField("picture")
            field.isAccessible = true

            for (name in FONT_NAMES) {
                val font = SILFontLoader(df, df["fonts/$name.font"])
                fonts[name] = font
                pictures[name] = field.get(font) as Image
            }
        }

        override fun keyPressed(key: Int, c: Char) {
            var handled = true
            when (key) {
                Input.KEY_BACK -> {
                    if (text.isNotEmpty())
                        text = text.substring(0, text.length - 1)
                }

                Input.KEY_F1 -> {
                    text = ""
                    rawFontMode = false
                }

                Input.KEY_F2 -> {
                    text = DEFAULT_TEXT
                    rawFontMode = false
                }

                Input.KEY_F3 -> rawFontMode = !rawFontMode
                Input.KEY_F4 -> baseline = !baseline
                Input.KEY_F5 -> legacyMode = !legacyMode
                else -> handled = false
            }

            if (handled) return

            if (rawFontMode) {
                when (key) {
                    Input.KEY_LEFT -> rawFontIdx -= 1
                    Input.KEY_RIGHT -> rawFontIdx += 1
                }
                rawFontIdx = rawFontIdx.coerceAtLeast(0)
                rawFontIdx = rawFontIdx.coerceAtMost(fonts.size - 1)
                return
            }

            val isLatinChar = c in 'a'..'z' || c in 'A'..'Z'
            val isNum = c in '0'..'9'
            val isSimplePunc = ", .?!".contains(c)

            if (isLatinChar || isNum || isSimplePunc) {
                text += c
            }
        }
    }
}
