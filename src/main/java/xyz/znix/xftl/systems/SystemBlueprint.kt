package xyz.znix.xftl.systems

import org.jdom2.Element
import xyz.znix.xftl.AbstractSystem
import xyz.znix.xftl.Blueprint

class SystemBlueprint(xml: Element) : Blueprint(xml) {
    val type: String = xml.getChildTextTrim("type")

    val startPower: Int = xml.getChildTextTrim("startPower").toInt()
    val maxPower: Int = xml.getChildTextTrim("maxPower").toInt()
    override val cost: Int = xml.getChildTextTrim("cost").toInt()

    val upgradeCost: List<Int> = xml.getChild("upgradeCost").getChildren("level").map { it.text.toInt() }

    val onIconPath = "img/icons/s_${name}_green1.png"

    // No idea what the 'locked' flag does.

    fun createInstance(node: Element): AbstractSystem? {
        return when (type) {
            Doors.NAME -> Doors(this, node)
            Engines.NAME -> Engines(this, node)
            Medbay.NAME -> Medbay(this, node)
            Oxygen.NAME -> Oxygen(this, node)
            Piloting.NAME -> Piloting(this, node)
            Sensors.NAME -> Sensors(this, node)
            Shields.NAME -> Shields(this, node)
            Cloaking.NAME -> Cloaking(this, node)
            Weapons.NAME -> Weapons(this, node)
            Drones.NAME -> Drones(this, node)
            Teleporter.NAME -> Teleporter(this, node)

            // AE-only
            MindControl.NAME -> MindControl(this, node)
            Hacking.NAME -> Hacking(this, node)
            BackupBattery.NAME -> BackupBattery(this, node)

            else -> {
                // TODO throw exception when all systems are implemented
                println("Warning: unimplemented system $type")
                null
            }
        }
    }

    companion object {
        /**
         * Padding inside the icon image file, which contains the glow
         */
        const val ICON_GLOW = 19
    }
}
