package xyz.znix.xftl.game

import org.newdawn.slick.Font
import org.newdawn.slick.Graphics
import org.newdawn.slick.Image
import org.newdawn.slick.Input
import xyz.znix.xftl.*
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint

abstract class Button(pos: IPoint, size: IPoint) {
    val basePos = pos.const
    var windowOffset: IPoint = ConstPoint.ZERO
        set(value) {
            field = value
            positionUpdated()
        }

    var pos = basePos
        private set
    val size = size.const

    var hovered: Boolean = false
        private set

    abstract fun draw(g: Graphics)

    /**
     * Alert the button that the mouse has been clicked at a specified location.
     *
     * @return true if this button absorbed the click, false otherwise
     */
    open fun mouseDown(button: Int, x: Int, y: Int): Boolean {
        if (!contains(x, y)) return false
        click(button)
        return true
    }

    protected abstract fun click(button: Int)

    fun contains(point: IPoint) = contains(point.x, point.y)

    open fun contains(x: Int, y: Int): Boolean {
        return pos.x <= x && x < pos.x + size.x && pos.y <= y && y < pos.y + size.y
    }

    fun update(x: Int, y: Int) {
        hovered = contains(x, y)
    }

    protected open fun positionUpdated() {
        pos = basePos + windowOffset
    }
}

class SimpleButton(
    pos: IPoint, size: IPoint, imgOffset: IPoint, val normal: Image, val hover: Image?,
    private val callback: (Int) -> Unit
) : Button(pos, size) {
    val imgOffset = imgOffset.const
    private var imagePos = pos - imgOffset

    override fun draw(g: Graphics) {
        var image = normal
        if (hovered && hover != null)
            image = hover
        image.draw(imagePos)
    }

    override fun positionUpdated() {
        super.positionUpdated()
        imagePos = pos - imgOffset
    }

    override fun click(button: Int) = callback(button)

    companion object {
        // Create a new button by specifying the clickable region within it,
        // rather than using pos and imgOffset to achive it.
        fun byRegion(
            pos: IPoint, buttonOffset: IPoint, buttonSize: IPoint,
            normal: Image, hover: Image?,
            callback: (Int) -> Unit
        ): SimpleButton {
            return SimpleButton(pos + buttonOffset, buttonSize, buttonOffset, normal, hover, callback)
        }
    }
}

object Buttons {
    fun drawRounded(g: Graphics, x: Int, y: Int, width: Int, height: Int, radius: Int) {
        // Note: this loop's range is inclusive, and will run for both 0 and radius.
        for (i in 0..radius) {
            // Draw a bunch of overlapping rectangles, making a diagonal corner.
            g.fillRect(x.f + i, y.f + radius - i, width.f - i * 2, height.f - (radius - i) * 2)
        }
    }

    class JumpButton(pos: IPoint, val ship: Ship, private val game: SlickGame, private val callback: () -> Unit) :
        Button(pos, ConstPoint(74, 29)) {

        private val font = game.getFont("HL2", 2f)

        override fun draw(g: Graphics) {
            val ftlX = pos.x + 6
            val ftlY = pos.y + 4

            game.getImg("img/buttons/FTL/FTL_base.png").draw(pos.x - 7, pos.y - 7)

            val engineOn = ship.engines!!.powerSelected > 0
            if (ship.isFtlCharged) {
                g.color = if (engineOn) Constants.JUMP_READY else Constants.JUMP_DISABLED
                drawRounded(g, pos.x + 5, pos.y + 6, size.x, size.y, 3)

                val textColour = when {
                    !engineOn -> Constants.JUMP_DISABLED_TEXT
                    hovered -> Constants.JUMP_READY_TEXT_HOVER
                    else -> Constants.JUMP_READY_TEXT
                }
                font.drawStringLegacy(ftlX + 8f, ftlY + 18f, "JUMP", textColour)
            } else {
                val suffix = if (engineOn) "" else "_off"
                val width = (ship.ftlChargeProgress * 74).toInt().coerceAtMost(74)
                game.getImg("img/buttons/FTL/FTL_loadingbars$suffix.png").drawSection(ftlX - 1, ftlY + 2, width, 29)
            }
        }

        override fun click(button: Int) {
            if (button != Input.MOUSE_LEFT_BUTTON) return
            if (!ship.isFtlReady) return

            callback()
        }

        // Apply an offset to make the hoverable area the big yellow centre region - by default the hoverable
        // section starts at the button's 0,0, and the main region is offset. Thus translate the mouse coordinates
        // back so 0,0 becomes the origin of the yellow area.
        override fun contains(x: Int, y: Int) = super.contains(x - 5, y - 7)
    }

    class BasicButton(
        pos: IPoint, size: IPoint, val label: String, game: SlickGame,
        private val radius: Int, private val font: Font, private val yOffset: Int,
        private val cb: () -> Unit
    ) : Button(pos, size) {

        override fun draw(g: Graphics) {
            // TODO mouseover highlight
            g.color = Constants.SECTOR_CUTOUT_TEXT
            drawRounded(g, pos.x, pos.y, size.x, size.y, radius)

            val x = (size.x - font.getWidth(label)) / 2f
            font.drawString(pos.x + x, pos.y + yOffset.f, label, Constants.JUMP_DISABLED_TEXT)
        }

        override fun click(button: Int) {
            if (button == Input.MOUSE_LEFT_BUTTON)
                cb()
        }
    }

    class ShipButton(pos: IPoint, val game: SlickGame, private val cb: () -> Unit) : Button(pos, ConstPoint(60, 41)) {
        private val imgPos = pos - ConstPoint(7, 7)

        private val imgOff = game.getImg("img/statusUI/top_ship_off.png")
        private val imgOn = game.getImg("img/statusUI/top_ship_on.png")
        private val imgHighlight = game.getImg("img/statusUI/top_ship_select2.png")

        override fun draw(g: Graphics) {
            val img = when {
                game.isInDanger -> imgOff
                hovered -> imgHighlight
                else -> imgOn
            }

            img.draw(imgPos)
        }

        override fun click(button: Int) {
            if (button == Input.MOUSE_LEFT_BUTTON)
                cb()
        }
    }

    class StoreButton(pos: IPoint, val game: SlickGame, private val callback: () -> Unit) :
        Button(pos, ConstPoint(88, 41)) {

        private val imgPos = pos - ConstPoint(7, 7)

        private val imgBase = game.getImg("img/statusUI/top_store_base.png")

        private val font = game.getFont("HL2", 2f)

        override fun draw(g: Graphics) {
            imgBase.draw(imgPos)
            font.drawString(pos.x + 11f, pos.y + 26f, "STORE", Constants.JUMP_DISABLED_TEXT)
        }

        override fun click(button: Int) {
            if (button == Input.MOUSE_LEFT_BUTTON)
                callback()
        }
    }
}
