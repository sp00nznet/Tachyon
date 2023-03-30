package xyz.znix.xftl.weapons

import org.jdom2.Element
import xyz.znix.xftl.Blueprint
import xyz.znix.xftl.game.SlickGame
import xyz.znix.xftl.math.IPoint

class DroneBlueprint(xml: Element) : Blueprint(xml) {
    val type: DroneType = DroneType.valueOf(xml.getChildTextTrim("type"))
    val tip: String? = xml.getChildTextTrim("tip") // Tooltip
    override val cost: Int? = xml.getChildTextTrim("cost")?.toInt()
    val power: Int = xml.getChildTextTrim("power").toInt()
    val droneImage: String? = xml.getChildTextTrim("droneImage")
    val iconImage: String? = xml.getChildTextTrim("iconImage")

    fun drawIconUI(game: SlickGame, pos: IPoint) {
        val base = game.getImg("img/ship/drones/drone_${iconImage!!}_charged.png")
        base.draw(pos.x - base.width / 2f, pos.y - base.height / 2f)

        // TODO laser for defense drones
    }

    enum class DroneType {
        COMBAT,
        SHIP_REPAIR,
        DEFENSE,
        REPAIR,
        BATTLE,
        BOARDER,
        HACKING,
        SHIELD,
    }
}
