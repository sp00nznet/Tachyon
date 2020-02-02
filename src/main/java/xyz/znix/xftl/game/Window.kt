package xyz.znix.xftl.game

import org.newdawn.slick.Graphics
import org.newdawn.slick.Image
import xyz.znix.xftl.Constants
import xyz.znix.xftl.draw
import xyz.znix.xftl.drawSection
import xyz.znix.xftl.f
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.Direction
import xyz.znix.xftl.math.IPoint

abstract class Window(position: IPoint) {
    val position = position.const
    abstract val size: IPoint
    abstract val outlineImage: Image

    abstract fun draw(g: Graphics)

    open fun mouseClick(button: Int, x: Int, y: Int) {
    }

    protected fun drawCorner(edge: Direction) {
        val x = position.x + edge.x.coerceAtLeast(0) * (size.x - 33)
        val y = position.y + edge.y.coerceAtLeast(0) * (size.y - 36)
        val tx = edge.x.coerceAtLeast(0) * outlineImage.width / 2
        val ty = edge.y.coerceAtLeast(0) * outlineImage.height / 2

        outlineImage.drawSection(x, y, 33, 36, tx, ty)
    }

    protected fun drawSide(edge: Direction, start: Int? = null, stop: Int? = null) {
        val xb = position.x + edge.x.coerceAtLeast(0) * (size.x - 33)
        val yb = position.y + edge.y.coerceAtLeast(0) * (size.y - 36)

        val texPos = when (edge) {
            Direction.UP -> ConstPoint(33, 0)
            Direction.LEFT -> ConstPoint(0, 36)
            Direction.DOWN, Direction.RIGHT -> ConstPoint(33, 36)
            else -> error("Invalid side edge $edge")
        }

        val texSize = when (edge) {
            Direction.UP, Direction.DOWN -> ConstPoint(1, 36)
            Direction.LEFT, Direction.RIGHT -> ConstPoint(33, 1)
            else -> error("Invalid side edge $edge")
        }

        val x: Int
        val y: Int

        val drawSize: ConstPoint
        when (edge) {
            Direction.UP, Direction.DOWN -> {
                x = xb + (start ?: 33)
                y = yb
                drawSize = ConstPoint((stop ?: size.x - 33) - (start ?: 33), 36)
            }
            Direction.LEFT, Direction.RIGHT -> {
                x = xb
                y = yb + (start ?: 36)
                drawSize = ConstPoint(33, (stop ?: size.y - 36) - (start ?: 36))
            }
            else -> error("Invalid edge $edge")
        }

        outlineImage.draw(x.f, y.f, x.f + drawSize.x, y.f + drawSize.y, texPos.x.f, texPos.y.f, texPos.x.f + texSize.x, texPos.y.f + texSize.y)
    }
}

class Windows {
    // Note that the actual window appears at 340, if we want to be resizable we'll have to fix
    // that (and the height). Currently we run much smaller than FTL so their size doesn't fit
    // for us atm.
    class JumpWindow(game: SlickGame) : Window(ConstPoint(0 /* 340 */, 83)) {
        override val size = ConstPoint(752, 580)
        override val outlineImage = game.getImg("img/window_outline.png")

        private val sectorInfoTab = game.getImg("img/map/side_sector.png")
        private val titleTab = game.getImg("img/map/side_beaconmap.png")
        private val font = game.getFont("HL2", 3f)
        private val cancelButton = game.getImg("img/main_menus/button_cancel_base.png")

        override fun draw(g: Graphics) {
            // Draw the top-left map label tab
            val tab = "BEACON MAP"
            val tabWidth = UIUtils.drawTab(font, tab, titleTab, position.x.f, position.y.f, 20f, 38f)
            font.drawString(position.x + 21f, position.y + 26f, tab, Constants.JUMP_DISABLED_TEXT)

            // Draw the rest of the top, going clockwise to the right side
            drawSide(Direction.UP, tabWidth.toInt())
            drawCorner(Direction.UP_RIGHT)
            drawSide(Direction.RIGHT)

            // Draw the bottom line connecting
            // Note these have to be drawn before the cancel button - see below
            drawSide(Direction.DOWN, sectorInfoTab.width)
            drawCorner(Direction.RIGHT_DOWN)

            // Cancel button
            // This is done in a slightly weird way - the bottom line is drawn exactly how it usually is, but
            // the cancel button is drawn just below it, cutting off part of it's glow. The glow at the edges
            // of the cancel button image are modified specially to fit the glow of the line, so it looks
            // seamless. Note that this must be done after the lines are drawn, otherwise their glow would overlap
            // the cancel button frame.
            // TODO use a real button object
            cancelButton.draw(position.x + size.x - cancelButton.width - 14, position.y + size.y - 7)
            g.color = Constants.SECTOR_CUTOUT_TEXT
            Buttons.drawRounded(g, position.x + size.x - cancelButton.width + 10, position.y + size.y + 1, 124, 30)
            font.drawString(position.x + size.x - cancelButton.width + 16f, position.y + size.y + 19f, "CANCEL", Constants.JUMP_DISABLED_TEXT)

            // Draw the sector info
            sectorInfoTab.draw(position.x, position.y + size.y - 27)
            // TODO put all the information in

            // The top and bottom tabs are slightly different sizes, this compensates for them
            drawSide(Direction.LEFT, 45, size.y - 27)

            // TODO draw the background and stars
        }
    }
}
