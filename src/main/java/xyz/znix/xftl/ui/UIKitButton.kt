package xyz.znix.xftl.ui

import org.jdom2.Element
import xyz.znix.xftl.Constants
import xyz.znix.xftl.game.Button
import xyz.znix.xftl.game.Buttons
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.game.Window
import xyz.znix.xftl.math.Point
import xyz.znix.xftl.rendering.Color
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.sys.Input
import kotlin.math.max

class UIKitButton(
    provider: UIProvider,
    val normalColour: Color,
    val highlightColour: Color,
    val disabledColour: Color
) : Widget(provider) {
    override val size: Point = Point(0, 0)

    /**
     * If we're running in-game (as opposed to the UI editor), an invisible
     * in-game button is created, which handles stuff like hover detection
     * and audio cues.
     *
     * We still render it as part of the widget though, to get the z-order
     * correct with other components.
     */
    private var gameButton: InGameButton? = null

    /**
     * The button on-click listener.
     *
     * This is only used while in-game.
     */
    var clickListener: (() -> Unit)? = null

    override fun draw(g: Graphics) {
        g.colour = gameButton?.getColour() ?: normalColour
        Buttons.drawRounded(g, position.x, position.y, size.x, size.y, 4)

        postDraw(g)
    }

    override fun attemptStretch(availableWidth: Int, availableHeight: Int) {
        size.x = max(size.x, availableWidth)
        size.y = max(size.y, availableHeight)
    }

    override fun updateSizes() {
        // Find the child sizes
        super.updateSizes()

        // And make sure they all fit inside this image
        stretchToFitChildren()
    }

    override fun createGameButtons(game: InGameState, window: Window): List<Button> {
        val btn = InGameButton(game)
        gameButton = btn
        return listOf(btn)
    }

    companion object {
        fun fromXML(provider: UIProvider, elem: Element): UIKitButton {
            val colour = SpecDeserialiser.parseColour(elem, "colour", Constants.SECTOR_CUTOUT_TEXT)
            val highlight = SpecDeserialiser.parseColour(elem, "colour", Constants.UI_BUTTON_HOVER)
            val disabled = SpecDeserialiser.parseColour(elem, "colour", Constants.JUMP_DISABLED)

            val view = UIKitButton(provider, colour, highlight, disabled)
            view.loadXML(elem)
            return view
        }
    }

    private inner class InGameButton(game: InGameState) : Button(game, position, size) {
        fun getColour(): Color {
            return when {
                disabled -> Constants.JUMP_DISABLED
                hovered -> Constants.UI_BUTTON_HOVER
                else -> Constants.SECTOR_CUTOUT_TEXT
            }
        }

        override fun draw(g: Graphics) {
            // Do nothing, the widget does all the drawing.
        }

        override fun click(button: Int) {
            if (button != Input.MOUSE_LEFT_BUTTON)
                return

            clickListener?.let { it() }
        }
    }
}
