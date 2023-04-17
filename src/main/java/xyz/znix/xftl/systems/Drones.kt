package xyz.znix.xftl.systems

import org.jdom2.Element
import org.newdawn.slick.Graphics
import xyz.znix.xftl.Ship
import xyz.znix.xftl.drones.AbstractDrone
import xyz.znix.xftl.weapons.DroneBlueprint

class Drones(blueprint: SystemBlueprint, xml: Element) : MainSystem(blueprint, xml) {
    override val sortingType: SortingType get() = SortingType.DRONES

    val drones = ArrayList<DroneInfo?>()

    override fun initialise(ship: Ship) {
        // Resize the blueprints list to match the ship
        // On some enemy ships (eg REBEL_FAT) there's a drone system but
        // no number of drone slots - in that case assume it's three, though
        // maybe there's no limit on it? TODO find out.
        val droneSlots = ship.droneSlots ?: 3
        while (drones.size < droneSlots)
            drones.add(null)
    }

    override fun drawBackground(g: Graphics) {
        // Draw the departing boarding drones
        for (info in drones) {
            info?.instance?.drawBackground(g)
        }
    }


    // Custom power-management (right-clicking turning drones on and off)

    override val powerSelected: Int
        get() {
            return drones.filter { it?.instance?.isPowered == true }.sumBy { it!!.type.power }
        }

    override fun powerStateChanged() {
        decrease@ while (powerSelected > powerAvailable) {
            for (i in drones.size - 1 downTo 0) {
                val drone = drones[i]?.instance ?: continue

                // If the drone is powered, turn it off and check again if
                // we need to shed more power.
                if (drone.isPowered) {
                    drone.isPowered = false
                    continue@decrease
                }
            }

            // We've turned off all our drones, and there's still too much
            // power use? This should never happen!
            throw IllegalStateException("Drones cannot meet provided power, selected $powerSelected available $powerAvailable")
        }
    }

    override fun increasePower() {
        if (isPowerLocked)
            return

        // Find the first drone, and power it up
        for ((i, drone) in drones.withIndex()) {
            if (drone == null)
                continue

            // Skip already-powered drones
            if (drone.instance?.isPowered == true)
                continue

            // We've found a suitable drone, turn it on.
            setDronePower(i, true)
            return
        }

        // No more drones to power up.
    }

    override fun decreasePower() {
        if (isPowerLocked)
            return

        // Find the last drone, and power it down
        for (i in drones.size - 1 downTo 0) {
            val drone = drones[i] ?: continue
            val instance = drone.instance ?: continue

            if (instance.isPowered) {
                setDronePower(i, false)
                return
            }
        }

        // No drones are currently powered.
    }

    override fun update(dt: Float) {
        super.update(dt)

        for (info in drones) {
            info?.instance?.update(dt)
        }
    }

    /**
     * Attempt to set the power status of the drone in the nth slot,
     * considering the system and reactor power limits.
     *
     * @return True if the given change was made.
     */
    fun setDronePower(slot: Int, power: Boolean): Boolean {
        val info = drones[slot] ?: return false

        // If the drone is already powered appropriately, nothing needs to be done.
        // This includes 'off' when the drone hasn't been spawned.
        if (info.instance?.isPowered == power || (info.instance == null && !power))
            return true

        // If we're turning the drone on, check there's enough power for it.
        if (power) {
            val consumedPower = powerSelected + info.type.power
            if (consumedPower > powerAvailable) {
                // TODO show a 'not enough system power' or 'not enough reactor power' warning
                return false
            }

            // If this drone attacks hostile ships but there isn't
            // one present, we can't turn it on.
            val hostileShip = ship.sys.enemy
            if (info.type.type.needsHostileShip && hostileShip == null) {
                return false
            }

            // Create the drone instance if it's not already.
            if (info.instance == null) {
                info.instance = info.type.makeInstance()
                info.instance!!.init(ship)
            }
        }

        info.instance!!.isPowered = power
        return true
    }

    fun enemyShipUpdated() {
        for (drone in drones) {
            drone?.instance?.onEnemyShipUpdated()
        }
    }

    class DroneInfo(val type: DroneBlueprint, var instance: AbstractDrone? = null)
}
