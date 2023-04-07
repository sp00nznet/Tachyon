package xyz.znix.xftl.weapons

import org.jdom2.Element
import xyz.znix.xftl.Blueprint
import xyz.znix.xftl.game.SlickGame
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint

class DroneBlueprint(xml: Element) : Blueprint(xml) {
    val type: DroneType = DroneType.valueOf(xml.getChildTextTrim("type"))
    val tip: String? = xml.getChildTextTrim("tip") // Tooltip
    override val cost: Int? = xml.getChildTextTrim("cost")?.toInt()
    val power: Int = xml.getChildTextTrim("power").toInt()
    val droneImage: String? = xml.getChildTextTrim("droneImage")
    val iconImage: String? = xml.getChildTextTrim("iconImage")

    // The size of the icon, as it should appear in the UI. This is for roughly
    // positioning them, and obviously different drones are different sizes.
    // These are mostly guessed, I couldn't find anywhere to measure them and
    // disassembling the UI code to look for this seems a bit excessive.
    val iconSize: ConstPoint = when (type) {
        // TODO find some logic behind this - is it the laser arm
        //  in defense drones that make them taller?
        // DEFENSE_1 drones use 46 pixels of Y in the event option UI, while COMBAT_1 uses 38.
        DroneType.DEFENSE -> ConstPoint(29, 37)
        DroneType.COMBAT -> ConstPoint(29, 29)

        // TODO fill in all the drones properly
        else -> ConstPoint(25, 25)
    }

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
