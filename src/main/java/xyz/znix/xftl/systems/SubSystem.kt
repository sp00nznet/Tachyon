package xyz.znix.xftl.systems

import org.jdom2.Element
import xyz.znix.xftl.AbstractSystem

abstract class SubSystem(blueprint: SystemBlueprint, elem: Element) : AbstractSystem(blueprint, elem) {
    abstract val sortingType: SortingType

    // List of the default systems, for sorting purposes
    // TODO handle modded systems
    enum class SortingType {
        PILOTING,
        SENSORS,
        DOORS,
        BATTERY,
    }
}
