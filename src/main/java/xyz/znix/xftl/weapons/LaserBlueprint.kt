package xyz.znix.xftl.weapons

import org.jdom2.Element
import xyz.znix.xftl.Ship

class LaserBlueprint(xml: Element) : ShipWeaponBlueprint(xml) {
    override fun buildInstance(ship: Ship): AbstractWeaponInstance? {
        return LaserInstance(ship)
    }

    inner class LaserInstance(ship: Ship) : AbstractWeaponInstance(this, ship) {
    }
}
