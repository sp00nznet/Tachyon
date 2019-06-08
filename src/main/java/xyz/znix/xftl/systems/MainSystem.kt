package xyz.znix.xftl.systems

import org.jdom2.Element
import xyz.znix.xftl.AbstractSystem

abstract class MainSystem(codename: String, elem: Element) : AbstractSystem(codename, elem) {
    open val selectedEnergyLevel: Int = 1

    abstract val sortingType: SortingType

    // List of the default systems, for sorting purposes
    // TODO handle modded systems
    enum class SortingType {
        SHIELD,
        ENGINES,
        MEDBAY,
        OXYGEN,
        WEAPONS;
        // TODO support drones
    }
}
