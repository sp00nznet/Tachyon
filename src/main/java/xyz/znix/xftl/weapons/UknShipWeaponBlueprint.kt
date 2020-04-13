package xyz.znix.xftl.weapons

import org.jdom2.Element
import xyz.znix.xftl.Ship

class UknShipWeaponBlueprint(xml: Element) : ShipWeaponBlueprint(xml) {
    init {
        val type = xml.getChildTextTrim("type")
        println("Unsupported ship weapon blueprint name '$name' of type '$type'")
    }

    override fun buildInstance(ship: Ship): AbstractWeaponInstance {
        return UknInstance(ship)
    }

    inner class UknInstance(ship: Ship) : AbstractWeaponInstance(this, ship) {
    }
}
