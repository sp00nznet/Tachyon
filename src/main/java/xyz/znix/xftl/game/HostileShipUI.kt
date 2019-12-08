package xyz.znix.xftl.game

import org.newdawn.slick.GameContainer
import org.newdawn.slick.Graphics
import xyz.znix.xftl.Ship
import xyz.znix.xftl.Utils
import xyz.znix.xftl.draw
import xyz.znix.xftl.f
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.math.Point

class HostileShipUI(private val game: SlickGame, private val enemy: Ship) {
    private val mutableShipPos = Point(0, 0)
    val shipPos: IPoint get() = mutableShipPos

    fun render(gc: GameContainer, g: Graphics, hoveredRoom: Room?) {
        val box = game.getImg("img/combatUI/box_hostiles2.png")

        val boxX = gc.width - (box.width - 20) - 18
        val boxY = 54 - 9

        // It's not quite the same as FTL, but works well enough for now
        mutableShipPos.x = boxX + (box.width - enemy.hullImage.width) / 2
        mutableShipPos.y = boxY + (box.height - enemy.hullImage.height) / 2

        box.draw(boxX, boxY)

        Utils.drawStenciled(Utils.StencilMode.MASKING, {
            game.getImg("img/combatUI/box_hostiles_mask.png").draw(boxX, boxY)
        }) {
            g.translate(shipPos.x.f, shipPos.y.f)
            enemy.render(g, hoveredRoom)
            g.resetTransform()
        }
    }
}
