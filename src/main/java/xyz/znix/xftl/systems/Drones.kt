package xyz.znix.xftl.systems

import org.jdom2.Element
import xyz.znix.xftl.Ship
import xyz.znix.xftl.weapons.DroneBlueprint

class Drones(blueprint: SystemBlueprint, xml: Element) : MainSystem(blueprint, xml) {
    override val sortingType: SortingType get() = SortingType.DRONES

    val blueprints = ArrayList<DroneBlueprint?>()

    override fun initialise(ship: Ship) {
        // Resize the blueprints list to match the ship
        while (blueprints.size < ship.droneSlots!!)
            blueprints.add(null)
    }
}
