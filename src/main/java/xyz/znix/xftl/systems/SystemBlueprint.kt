package xyz.znix.xftl.systems

import org.jdom2.Element
import xyz.znix.xftl.Blueprint

class SystemBlueprint(xml: Element) : Blueprint(xml) {
    val type: String = xml.getChildTextTrim("type")

    val startPower: Int = xml.getChildTextTrim("startPower").toInt()
    val maxPower: Int = xml.getChildTextTrim("maxPower").toInt()
    val cost: Int = xml.getChildTextTrim("cost").toInt()

    val upgradeCost: List<Int> = xml.getChild("upgradeCost").getChildren("level").map { it.text.toInt() }

    val onIconPath = "img/icons/s_${name}_green1.png"

    // No idea what the 'locked' flag does.

    companion object {
        /**
         * Padding inside the icon image file, which contains the glow
         */
        const val ICON_GLOW = 19
    }
}
