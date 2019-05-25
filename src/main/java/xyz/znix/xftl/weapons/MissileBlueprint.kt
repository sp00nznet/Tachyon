package xyz.znix.xftl.weapons

import org.jdom2.Element
import xyz.znix.xftl.Ship

class MissileBlueprint(xml: Element) : ShipWeaponBlueprint(xml) {
    override val explosion: String = super.explosion ?: "explosion_random"

    override fun buildInstance(ship: Ship): AbstractWeaponInstance? {
        return MissileInstance(ship)
    }

    inner class MissileInstance(ship: Ship) : AbstractWeaponInstance(this, ship) {
    }
}
