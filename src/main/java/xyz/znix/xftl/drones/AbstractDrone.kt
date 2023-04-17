package xyz.znix.xftl.drones

import org.newdawn.slick.Graphics
import xyz.znix.xftl.Ship
import xyz.znix.xftl.weapons.DroneBlueprint

abstract class AbstractDrone(val type: DroneBlueprint) {
    var isPowered: Boolean = false
        set(value) {
            val changed = value != field
            field = value
            if (changed)
                onPowerChanged()
        }

    lateinit var ownerShip: Ship
        private set

    protected var initialised: Boolean = false
        private set

    open fun init(ownerShip: Ship) {
        if (initialised)
            error("Cannot re-initialise drone ${type.name}")

        this.ownerShip = ownerShip
        initialised = true
    }

    /**
     * Remove this drone instance, setting a cooldown before
     * it can be re-deployed.
     *
     * This should be called when a drone is destroyed by something,
     * for example a repair drone destroyed by boarders.
     */
    open fun destroy() {
        // Drones that have been moved to cargo won't have an associated
        // info any more, so we have to use firstOrNull.
        val drones = ownerShip.drones!!
        val info = drones.drones.firstOrNull { it?.instance == this }
        info?.instance = null

        // TODO cooldown
    }

    open fun update(dt: Float) {
    }

    protected open fun onPowerChanged() {
    }

    /**
     * Called whenever something potentially important happens to the enemy
     * ship, such as it being destroyed or changing as we jump away.
     */
    open fun onEnemyShipUpdated() {
    }

    /**
     * Draw something on the owning ship, in the background.
     *
     * This is intended for departing boarding drones.
     */
    open fun drawBackground(g: Graphics) {
    }
}
