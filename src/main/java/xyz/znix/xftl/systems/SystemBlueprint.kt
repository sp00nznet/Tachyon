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

    val onIconPath = "img/icons/s_${type}_green1.png"
    val roomIconPath: String = "img/icons/s_${type}_overlay.png"

    // No idea what the 'locked' flag does.

    fun createInstance(): AbstractSystem? {
        return when (type) {
            Doors.NAME -> Doors(this)
            Engines.NAME -> Engines(this)
            Medbay.NAME -> Medbay(this)
            Oxygen.NAME -> Oxygen(this)
            Piloting.NAME -> Piloting(this)
            Sensors.NAME -> Sensors(this)
            Shields.NAME -> Shields(this)
            Cloaking.NAME -> Cloaking(this)
            Weapons.NAME -> Weapons(this)
            Drones.NAME -> Drones(this)
            Teleporter.NAME -> Teleporter(this)
            Artillery.NAME -> Artillery(this)

            // AE-only
            MindControl.NAME -> MindControl(this)
            Hacking.NAME -> Hacking(this)
            Clonebay.NAME -> Clonebay(this)
            BackupBattery.NAME -> BackupBattery(this)

            else -> {
                // Must be a modded system.
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
