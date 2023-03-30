package xyz.znix.xftl.systems

import org.jdom2.Element
import xyz.znix.xftl.Ship
import xyz.znix.xftl.weapons.DroneBlueprint

class Drones(blueprint: SystemBlueprint, xml: Element) : MainSystem(blueprint, xml) {
    override val sortingType: SortingType get() = SortingType.DRONES

    val blueprints = ArrayList<DroneBlueprint?>()

    override fun initialise(ship: Ship) {
        // Resize the blueprints list to match the ship
        // On some enemy ships (eg REBEL_FAT) there's a drone system but
        // no number of drone slots - in that case assume it's three, though
        // maybe there's no limit on it? TODO find out.
        val droneSlots = ship.droneSlots ?: 3
        while (blueprints.size < droneSlots)
            blueprints.add(null)
    }
}
