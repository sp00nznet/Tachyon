package xyz.znix.xftl.game

import org.newdawn.slick.Color
import org.newdawn.slick.Graphics
import xyz.znix.xftl.Constants
import xyz.znix.xftl.f
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.Direction
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.sector.Event

class DialogueWindow(val game: SlickGame, startingEvent: Event, val close: () -> Unit) : Window(ConstPoint(100, 100)) {
    override val size: IPoint get() = ConstPoint(602, 377)
    override val outlineImage = game.getImg("img/window_base.png")

    private val font = game.getFont("JustinFont11Bold")

    private var currentEvent: Event = startingEvent
    private lateinit var currentEventText: String
    private lateinit var optionsText: List<String>

    init {
        loadEvent(currentEvent)
    }

    private fun loadEvent(event: Event) {
        currentEvent = event
        currentEventText = event.text?.resolve() ?: error("Textless event!")
        optionsText = event.choices.map { it.text.resolve() }
    }

    override fun draw(g: Graphics) {
        // Draw the frame
        // TODO fix up the stretched bits
        for (dir in Direction.values()) {
            if (dir.isDiagonal) {
                drawCorner(dir)
            } else {
                drawSide(dir)
            }
        }

        outlineImage.draw(position.x + 33f, position.y + 36f, position.x + size.x - 33f, position.y + size.y - 36f,
                33f, 36f, 34f, 37f)

        // Event text
        //val ev = "The space station here has a traveling merchant who shows you his wares."

        var textY = position.y + 42
        textY = drawText(textY, currentEventText)

        textY += 60

        for ((i, choice) in currentEvent.choices.withIndex()) {
            val colour = when {
                choice.blue -> Constants.TEXT_OPTION_BLUE
                else -> Color.white
            }
            val prefix = "${i + 1}. "
            textY = drawText(textY, prefix + optionsText[i], colour)
            textY += 32
        }

        if (currentEvent.choices.isEmpty()) {
            drawText(textY, "1. " + game.translator["continue"])
        }
    }

    private fun drawText(y: Int, msg: String, colour: Color = Color.white): Int {
        val textX = position.x + 25f
        val textFarX = position.x + size.x - 25f

        var lineText = ""
        var textY = y
        for (word in msg.split(" ")) {
            if (font.getWidth(lineText + word) + textX > textFarX) {
                font.drawString(textX, textY.f, lineText, colour)
                lineText = ""
                textY += 20
            }

            lineText += "$word "
        }
        font.drawString(textX, textY.f, lineText, colour)

        return textY
    }

    override fun mouseClick(button: Int, x: Int, y: Int) {
        close()
    }
}
