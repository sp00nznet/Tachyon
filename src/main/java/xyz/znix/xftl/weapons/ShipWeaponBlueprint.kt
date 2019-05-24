package xyz.znix.xftl.weapons

import org.jdom2.Element
import xyz.znix.xftl.Ship

abstract class ShipWeaponBlueprint(xml: Element) : AbstractWeaponBlueprint(xml) {
    val chargeTime: Float = xml.getChild("cooldown").textTrim.toFloat()

    abstract fun buildInstance(ship: Ship): AbstractWeaponInstance?
}
