package xyz.znix.xftl.drones

import xyz.znix.xftl.Ship
import xyz.znix.xftl.weapons.DroneBlueprint

abstract class AbstractDrone(val type: DroneBlueprint) {
    var isPowered: Boolean = false

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
}
