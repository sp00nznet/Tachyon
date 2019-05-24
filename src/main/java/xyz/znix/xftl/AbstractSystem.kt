package xyz.znix.xftl

import org.jdom2.Element
import org.newdawn.slick.Graphics
import xyz.znix.xftl.Constants.ROOM_SIZE
import xyz.znix.xftl.layout.Room

abstract class AbstractSystem(val codename: String, elem: Element) {
    // TODO handle this properly
    var room: Room? = null

    protected val ship: Ship get() = room!!.ship

    var energyLevels: Int = 1
    var damagedEnergyLevels: Int = 0

    open fun update(dt: Float) {
    }

    open fun drawBackground(g: Graphics) {
    }

    open fun drawForeground(g: Graphics) {
    }

    fun drawRoom(g: Graphics) {
        // Draw the system icon
        val room = room!!
        val img = room.ship.sys.getImg(icon)

        val colour = when {
            damagedEnergyLevels == energyLevels -> Constants.SYSTEM_BROKEN
            damagedEnergyLevels > 0 -> Constants.SYSTEM_DAMAGED
            else -> Constants.SYSTEM_NORMAL
        }

        g.drawImage(img,
                (room.offsetX + (room.width * ROOM_SIZE / 2f - img.width / 2f).toInt()).toFloat(),
                (room.offsetY + (room.height * ROOM_SIZE / 2f - img.height / 2f).toInt()).toFloat(),
                colour
        )
    }

    open val icon: String = "img/icons/s_${codename}_overlay.png"

    val img: String? = elem.getAttributeValue("img")?.let { i -> "img/ship/interior/$i.png" }

}