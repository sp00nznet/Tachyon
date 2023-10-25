package xyz.znix.xftl.sys

import xyz.znix.xftl.SILFontLoader
import xyz.znix.xftl.Translator
import xyz.znix.xftl.f
import xyz.znix.xftl.game.MainGame
import xyz.znix.xftl.rendering.Colour
import xyz.znix.xftl.rendering.Graphics
import java.nio.file.Files
import java.nio.file.Path

class DatafileSelectState(private val game: MainGame) : MainGame.GameState() {
    private val context = ResourceContext()

    private lateinit var font: SILFontLoader
    private lateinit var translator: Translator

    private val options = ArrayList<Path>()

    private val optionX = 60
    private var optionBaseY = 0
    private var optionWidth = 0
    private val optionHeight = 25

    private var hoveredOption: Int? = null

    private lateinit var descriptionLines: List<String>

    override fun init(container: GameContainer) {
        // Use a badly-converted copy of the Roboto font, since we can't access
        // FTL fonts until we select ftl.dat.
        // Note the font is 1px too high above the baseline, but that doesn't matter.
        val fontData = javaClass.classLoader.getResourceAsStream("baked/roboto.font").readAllBytes()
        font = SILFontLoader(context, null, fontData)

        translator = Translator("assets/lang/en.xml")

        options.addAll(FTLFinder.findInstallations())

        val description = translator["xftl_select_datafile_desc"]
            .replace("\\1", PlatformSpecific.INSTANCE.ftlDatPathFile.toString())
        descriptionLines = description.split('\n').flatMap { font.wrapString(it, 500) }
    }

    override fun update(container: GameContainer, delta: Float) {
    }

    override fun render(container: GameContainer, g: Graphics) {
        g.clear(Colour.lightGray)
        val input = container.input

        val title = translator["xftl_select_datafile_title"]
        val notFound = translator["xftl_select_datafile_not_found"]

        var y = 30
        font.drawString(30f, y.f, title, Colour.black)
        y += 50

        for (line in descriptionLines) {
            font.drawString(70f, y.f, line, Colour.black)
            y += 20
        }

        y += 30

        g.colour = Colour.black
        g.drawLine(50, y, 530, y)
        y += 40

        if (options.isEmpty()) {
            font.drawString(70f, y.f, notFound, Colour.black)
            return
        }

        hoveredOption = null
        optionBaseY = y
        optionWidth = container.width - optionX - 50
        for ((index, opt) in options.withIndex()) {
            val optY = getOptionY(index)

            val hover = input.mouseX in optionX..optionX + optionWidth
                    && input.mouseY in optY..optY + optionHeight
            if (hover) {
                hoveredOption = index
            }

            g.colour = when (hover) {
                true -> Colour.white
                false -> Colour.lightGray
            }
            g.fillRect(optionX, optY, optionWidth, optionHeight)
            g.colour = Colour.black
            g.drawRect(optionX, optY, optionWidth - 1, optionHeight - 1)

            font.drawString(optionX + 5f, optY + 18f, opt.toString(), Colour.black)
        }
    }

    override fun mouseClicked(button: Int, x: Int, y: Int, clickCount: Int) {
        if (button != Input.MOUSE_LEFT_BUTTON)
            return
        if (clickCount != 1)
            return

        val hovered = hoveredOption ?: return
        val selected = options[hovered].toString()

        Files.writeString(PlatformSpecific.INSTANCE.ftlDatPathFile, selected)

        game.switchToShipSelect()
    }

    private fun getOptionY(index: Int): Int {
        return optionBaseY + 25 * index
    }

    override fun shutdown() {
        context.freeAll()
    }
}
