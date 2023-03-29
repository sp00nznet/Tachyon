package xyz.znix.xftl

import org.jdom2.Element
import org.newdawn.slick.Graphics
import xyz.znix.xftl.Constants.ROOM_SIZE
import xyz.znix.xftl.game.SlickGame
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.systems.MainSystem
import xyz.znix.xftl.systems.SystemBlueprint

abstract class AbstractSystem(val blueprint: SystemBlueprint, elem: Element) {
    val codename: String get() = blueprint.name

    // TODO handle this properly
    var room: Room? = null

    protected val ship: Ship get() = room!!.ship

    var energyLevels: Int = 1
    var damagedEnergyLevels: Int = 0
    val damaged: Boolean get() = damagedEnergyLevels > 0

    // Used for calculations by the ship generator.
    val aiMaxPower: Int? = elem.getAttributeValue("max")?.toInt()

    /**
     * The number of intact energy bars in the system
     */
    open val undamagedEnergy get() = energyLevels - damagedEnergyLevels

    var repairProgress: Float = 0f
        private set(value) {
            field = if (damaged) value else 0f
        }

    open fun update(dt: Float) {
        if (!damaged || room?.reservedPlayerSlots?.all { it == null } == true)
            repairProgress = 0f
    }

    open fun drawBackground(g: Graphics) {
    }

    open fun drawForeground(g: Graphics) {
    }

    open fun initialise(ship: Ship) {
    }

    fun drawRoom(g: Graphics) {
        // Draw the system icon
        val room = room!!
        val img = room.ship.sys.getImg(icon)

        val colour = when {
            damagedEnergyLevels == energyLevels -> Constants.SYSTEM_BROKEN
            damagedEnergyLevels > 0 -> Constants.SYSTEM_DAMAGED
            else -> Constants.SYSTEM_NORMAL
        }

        g.drawImage(
            img,
            (room.offsetX + (room.width * ROOM_SIZE / 2f - img.width / 2f).toInt()).f,
            (room.offsetY + (room.height * ROOM_SIZE / 2f - img.height / 2f).toInt()).f,
            colour
        )
    }

    open fun dealDamage(damage: Int) {
        // Add the specified amount of damage, but avoid having more damage than we have power (which
        // would come to a negative amount of available power)
        damagedEnergyLevels = (damagedEnergyLevels + damage).coerceAtMost(energyLevels)
        powerStateChanged()
    }

    // Something - anything - happened to the system's power level.
    // Systems should generally override this rather than dealDamage, to include stuff like ionisation or
    // a Zoltan leaving the room.
    protected open fun powerStateChanged() {
    }

    fun repair(progress: Float) {
        repairProgress += progress

        if (repairProgress < 1f)
            return

        repairProgress = 0f

        damagedEnergyLevels--
    }

    open val icon: String = "img/icons/s_${codename}_overlay.png"

    val img: String? = elem.getAttributeValue("img")?.let { i -> "img/ship/interior/$i.png" }

    open val iconColourName: String
        get() = when {
            damagedEnergyLevels == energyLevels -> "red"
            damagedEnergyLevels > 0 -> "orange"
            this is MainSystem && powerSelected == 0 -> "grey"
            else -> "green"
        }

    open fun drawIconAndPower(game: SlickGame, g: Graphics, x: Int, baseY: Int) {
        game.getImg("img/icons/s_" + codename + "_" + iconColourName + "1.png").draw(x.f, baseY.f)

        for (i in 0 until energyLevels) {
            val y = baseY + 8 - i * 8

            when {
                i >= energyLevels - damagedEnergyLevels -> {
                    // System damaged/broken
                    g.color = Constants.SYS_ENERGY_BROKEN
                    g.drawRect((x + 24).f, y.f, (16 - 1).f, (6 - 1).f)
                    g.drawLine((x + 24).f, (y + 6).f, (x + 24 + 16).f, y.f)
                }

                this is MainSystem && i >= powerSelected -> {
                    // System depowered
                    g.color = Constants.SYS_ENERGY_DEPOWERED
                    g.drawRect((x + 24).f, y.f, (16 - 1).f, (6 - 1).f)
                }

                else -> {
                    // System powered, or a subsystem that doesn't need powering
                    g.color = Constants.SYS_ENERGY_ACTIVE
                    g.fillRect((x + 24).f, y.f, 16f, 6f)
                }
            }

            if (i == energyLevels - damagedEnergyLevels) {
                g.color = Constants.SYS_ENERGY_REPAIR
                val width = (16 * repairProgress).toInt()
                g.fillRect((x + 24 + 16 - width).f, y.f, width.f, 6f)
            }
        }
    }
}
