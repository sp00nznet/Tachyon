package xyz.znix.xftl.systems

import xyz.znix.xftl.AbstractSystem

abstract class SubSystem(blueprint: SystemBlueprint) : AbstractSystem(blueprint) {
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
