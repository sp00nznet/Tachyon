package xyz.znix.xftl.game

import org.newdawn.slick.Graphics
import org.newdawn.slick.Image
import org.newdawn.slick.Input
import xyz.znix.xftl.*
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint

abstract class Button(pos: IPoint, size: IPoint) {
    val pos = pos.const
    val size = size.const

    var hovered: Boolean = false
        private set

    abstract fun draw(g: Graphics)

    open fun mouseDown(button: Int, x: Int, y: Int) {
        if (!contains(x, y)) return
        click(button)
    }

    protected abstract fun click(button: Int)

    fun contains(point: IPoint) = contains(point.x, point.y)

    fun contains(x: Int, y: Int): Boolean {
        return pos.x <= x && x < pos.x + size.x && pos.y <= y && y < pos.y + size.y
    }

    fun update(x: Int, y: Int) {
        hovered = contains(x, y)
    }
}

class SimpleButton(pos: IPoint, size: IPoint, imgOffset: IPoint, val normal: Image, val hover: Image?,
                   private val callback: (Int) -> Unit) : Button(pos, size) {
    val imgOffset = imgOffset.const
    private val imagePos = pos - imgOffset

    override fun draw(g: Graphics) {
        normal.draw(imagePos)
    }

    override fun click(button: Int) = callback(button)
}

object Buttons {
    fun drawRounded(g: Graphics, x: Int, y: Int, width: Int, height: Int) {
        g.fillRect(x + 3f, y + 0f, width - 6f, 1f)
        g.fillRect(x + 2f, y + 1f, width - 4f, 1f)
        g.fillRect(x + 1f, y + 2f, width - 2f, 1f)
        g.fillRect(x.f, y + 3f, width.f, height - 6f)
        g.fillRect(x + 1f, y + height - 3f, width - 2f, 1f)
        g.fillRect(x + 2f, y + height - 2f, width - 4f, 1f)
        g.fillRect(x + 3f, y + height - 1f, width - 6f, 1f)
    }

    class JumpButton(pos: IPoint, val ship: Ship, private val game: SlickGame)
        : Button(pos, ConstPoint(74, 29)) {

        private val font = game.getFont("HL2", 2f)

        override fun draw(g: Graphics) {
            val ftlX = pos.x + 6
            val ftlY = pos.y + 4

            game.getImg("img/buttons/FTL/FTL_base.png").draw(pos.x - 7, pos.y - 7)

            val engineOn = ship.engines!!.powerSelected > 0
            if (ship.isFtlCharged) {
                g.color = if (engineOn) Constants.JUMP_READY else Constants.JUMP_DISABLED
                drawRounded(g, pos.x + 5, pos.y + 6, size.x, size.y)

                val textColour = when {
                    !engineOn -> Constants.JUMP_DISABLED_TEXT
                    hovered -> Constants.JUMP_READY_TEXT_HOVER
                    else -> Constants.JUMP_READY_TEXT
                }
                font.drawString(ftlX + 8f, ftlY + 18f, "JUMP", textColour)
            } else {
                val suffix = if (engineOn) "" else "_off"
                val width = (ship.ftlChargeProgress * 74).toInt().coerceAtMost(74)
                game.getImg("img/buttons/FTL/FTL_loadingbars$suffix.png").drawSection(ftlX - 1, ftlY + 2, width, 29)
            }
        }

        override fun click(button: Int) {
            if (button != Input.MOUSE_LEFT_BUTTON) return
            if (!ship.isFtlReady) return

            println("Clicked $this")
        }
    }
}
