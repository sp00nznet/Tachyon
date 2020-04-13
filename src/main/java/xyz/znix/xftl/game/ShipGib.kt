package xyz.znix.xftl.game

import org.jdom2.Element
import org.newdawn.slick.Graphics
import org.newdawn.slick.Image
import xyz.znix.xftl.Ship
import xyz.znix.xftl.f
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.math.Point
import kotlin.math.cos
import kotlin.math.sin

class ShipGib(game: SlickGame, ship: Ship, node: Element) {
    val img: Image

    val velocity = pickRange(node.getChild("velocity"))
    val direction = -Math.toRadians(pickRange(node.getChild("direction")).toDouble())
    val angular = pickRange(node.getChild("angular"))
    val offset = ConstPoint(node.getChildTextTrim("x").toInt(), node.getChildTextTrim("y").toInt())

    private var time = 0f

    val isFinished: Boolean get() = time >= GIB_DURATION

    init {
        val imgBase = "img/" + if (ship.isPlayerShip) "ship" else "ships_glow"
        img = game.getImg("$imgBase/${ship.imageName}_${node.name}.png")
    }

    fun draw(g: Graphics, basePoint: IPoint) {
        val pos = Point(basePoint)
        pos += offset

        val dist = velocity * time * 25
        val progress = time / GIB_DURATION
        pos += ConstPoint((cos(direction) * dist).toInt(), (sin(direction) * dist).toInt())

        val rotation = progress * angular * Math.PI * 2
        g.pushTransform()
        g.translate(pos.x.f, pos.y.f)
        g.rotate(img.width.f / 2, img.height.f / 2, rotation.toFloat())
        img.draw(0f, 0f)
        g.popTransform()
    }

    fun update(dt: Float) {
        time += dt
    }

    // Currently just used for testing
    fun reset() {
        time = 0f
    }

    companion object {
        // Number of seconds the gibs play for
        val GIB_DURATION = 2f

        fun pickRange(elem: Element): Float {
            val min = elem.getAttributeValue("min").toFloat()
            val max = elem.getAttributeValue("max").toFloat()
            val range = max - min
            return min + range * Math.random().toFloat()
        }
    }
}
