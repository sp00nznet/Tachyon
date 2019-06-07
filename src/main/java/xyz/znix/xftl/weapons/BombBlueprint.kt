package xyz.znix.xftl.weapons

import org.jdom2.Element
import xyz.znix.xftl.Ship

class BombBlueprint(xml: Element) : ShipWeaponBlueprint(xml) {
    override val explosion: String = super.explosion ?: "explosion_random"

    override fun buildInstance(ship: Ship): AbstractWeaponInstance {
        return BombInstance(ship)
    }

    inner class BombInstance(ship: Ship) : AbstractWeaponInstance(this, ship)
}
