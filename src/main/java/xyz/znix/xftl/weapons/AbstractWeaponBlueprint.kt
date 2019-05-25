package xyz.znix.xftl.weapons

import org.jdom2.Element

abstract class AbstractWeaponBlueprint(xml: Element) {
    val name: String = xml.getAttributeValue("name")!!
    val launcher: String
    val projectile: String
    open val explosion: String? = xml.getChildTextTrim("explosion")
    val shots = xml.getChildTextTrim("shots")?.toInt() ?: 1

    init {
        launcher = tag(xml, "weaponArt")
        projectile = tag(xml, "image")
    }

    private fun tag(elem: Element, name: String): String {
        val tags = elem.getChildren(name)
        check(tags.size == 1)
        return tags[0].textTrim
    }
}
