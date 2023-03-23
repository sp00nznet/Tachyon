package xyz.znix.xftl.systems

import org.jdom2.Element
import xyz.znix.xftl.Blueprint

class SystemBlueprint(xml: Element) : Blueprint(xml) {
    val type: String = xml.getChildTextTrim("type")

    val startPower: Int = xml.getChildTextTrim("startPower").toInt()
    val maxPower: Int = xml.getChildTextTrim("maxPower").toInt()
    val cost: Int = xml.getChildTextTrim("cost").toInt()

    val upgradeCost: List<Int> = xml.getChild("upgradeCost").getChildren("level").map { it.text.toInt() }

    // No idea what the 'locked' flag does.
}
