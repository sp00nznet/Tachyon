package xyz.znix.xftl.weapons

import org.jdom2.Element
import xyz.znix.xftl.Ship

class BeamBlueprint(xml: Element) : ShipWeaponBlueprint(xml) {
    override fun buildInstance(ship: Ship): AbstractWeaponInstance {
        return BeamInstance(ship)
    }

    inner class BeamInstance(ship: Ship) : AbstractWeaponInstance(this, ship) {
    }
}
