package xyz.znix.xftl.weapons

import org.jdom2.Element
import xyz.znix.xftl.Blueprint

abstract class AbstractWeaponBlueprint(xml: Element) : Blueprint(xml) {
    val launcher: String = xml.getChildTextTrim("weaponArt")
    val projectile: String? = xml.getChildTextTrim("image")
    open val explosion: String? = xml.getChildTextTrim("explosion")
    val shots = xml.getChildTextTrim("shots")?.toInt() ?: 1
    val damage = xml.getChildTextTrim("damage").toInt()
    val sysDamage = xml.getChildTextTrim("sysDamage")?.toInt() ?: damage
    open val shieldPiercing: Boolean get() = false

    val power = xml.getChildTextTrim("power").toInt()
}
