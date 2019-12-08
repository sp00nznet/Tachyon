package xyz.znix.xftl.game

import org.newdawn.slick.GameContainer
import org.newdawn.slick.Graphics
import xyz.znix.xftl.Ship
import xyz.znix.xftl.Utils
import xyz.znix.xftl.draw
import xyz.znix.xftl.f
import xyz.znix.xftl.layout.Room

class HostileShipUI(private val game: SlickGame, private val enemy: Ship) {
    fun render(gc: GameContainer, g: Graphics, hoveredRoom: Room?) {
        val box = game.getImg("img/combatUI/box_hostiles2.png")

        val boxX = gc.width - (box.width - 20) - 18
        val boxY = 54 - 9

        box.draw(boxX, boxY)

        Utils.drawStenciled(Utils.StencilMode.MASKING, {
            game.getImg("img/combatUI/box_hostiles_mask.png").draw(boxX, boxY)
        }) {
            g.translate(gc.width - enemy.hullImage.width.f, 0f)
            enemy.render(g, hoveredRoom)
            g.resetTransform()
        }
    }
}
