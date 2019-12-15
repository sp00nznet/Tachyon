package xyz.znix.xftl.game

import org.newdawn.slick.Color
import org.newdawn.slick.GameContainer
import org.newdawn.slick.Graphics
import org.newdawn.slick.Image
import xyz.znix.xftl.*
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.math.Point

class HostileShipUI(private val game: SlickGame, df: Datafile, private val enemy: Ship) {
    private val mutableShipPos = Point(0, 0)
    val shipPos: IPoint get() = mutableShipPos

    private val font = SILFontLoader(df, df["fonts/HL2.font"])

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

        val textX = boxX + 12

        // Draw the hull level
        val hullY = boxY + 29 + 4
        renderSmallbar(textX, hullY, "HULL")
        // TODO hull bar

        // Draw the shield bubbles
        val shieldsY = hullY + 27
        renderSmallbar(textX, shieldsY, "SHIELDS")
        // TODO render shield bubbles
    }

    private fun renderSmallbar(x: Int, y: Int, text: String) {
        // TODO colours

        val textX = 2
        val textWidth = font.getWidth(text)

        val left = game.getImg("img/combatUI/box_hostiles_smallbar_left.png")
        left.draw(x, y)
        val middle = game.getImg("img/combatUI/box_hostiles_smallbar_middle.png")
        middle.filter = Image.FILTER_NEAREST
        val midWidth = textWidth + textX - left.width
        middle.draw(x.f + left.width, y.f, midWidth.f, middle.height.f)

        game.getImg("img/combatUI/box_hostiles_smallbar_right.png").draw(x + left.width + midWidth, y)

        font.drawString(x + 2f, y + 7f, text, Color.black)
    }
}
