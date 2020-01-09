package xyz.znix.xftl.game

import org.newdawn.slick.Graphics
import org.newdawn.slick.Image
import xyz.znix.xftl.*
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint

abstract class Button(pos: IPoint, size: IPoint) {
    val pos = pos.const
    val size = size.const

    abstract fun draw(g: Graphics)
}

class SimpleButton(pos: IPoint, size: IPoint, imgOffset: IPoint, val normal: Image, val hover: Image?) : Button(pos, size) {
    val imgOffset = imgOffset.const
    private val imagePos = pos - imgOffset

    override fun draw(g: Graphics) {
        normal.draw(imagePos)
    }
}

object Buttons {
    class JumpButton(pos: IPoint, val ship: Ship, private val game: SlickGame, private val font: SILFontLoader)
        : Button(pos, ConstPoint(74, 29)) {
        override fun draw(g: Graphics) {
            val ftlX = pos.x + 6
            val ftlY = pos.y + 4

            game.getImg("img/buttons/FTL/FTL_base.png").draw(pos.x - 7, pos.y - 7)

            val engineOn = ship.engines!!.powerSelected > 0
            if (ship.isFtlReady) {
                g.color = if (engineOn) Constants.JUMP_READY else Constants.JUMP_DISABLED
                g.fillRect(ftlX + 2f, ftlY + 2f, 68f, 1f)
                g.fillRect(ftlX + 1f, ftlY + 3f, 70f, 1f)
                g.fillRect(ftlX + 0f, ftlY + 4f, 72f, 1f)
                g.fillRect(ftlX - 1f, ftlY + 5f, 74f, 23f)
                g.fillRect(ftlX + 0f, ftlY + 5f + 23 + 0, 72f, 1f)
                g.fillRect(ftlX + 1f, ftlY + 5f + 23 + 1, 70f, 1f)
                g.fillRect(ftlX + 2f, ftlY + 5f + 23 + 2, 68f, 1f)

                val textColour = if (engineOn) Constants.JUMP_READY_TEXT else Constants.JUMP_DISABLED_TEXT
                font.drawString(ftlX + 8f, ftlY + 18f, "JUMP", textColour)
            } else {
                val suffix = if (engineOn) "" else "_off"
                val width = (ship.ftlChargeProgress * 74).toInt().coerceAtMost(74)
                game.getImg("img/buttons/FTL/FTL_loadingbars$suffix.png").drawSection(ftlX - 1, ftlY + 2, width, 29)
            }
        }
    }
}
