package xyz.znix.xftl.systems

import org.jdom2.Element
import xyz.znix.xftl.AbstractSystem

abstract class MainSystem(blueprint: SystemBlueprint, elem: Element) : AbstractSystem(blueprint, elem) {
    private var simpleSelectedEnergyLevel: Int = 1
    open val powerSelected: Int get() = simpleSelectedEnergyLevel

    val powerAvailableSys: Int get() = energyLevels - damagedEnergyLevels

    val powerAvailable: Int get() = Math.min(powerAvailableSys, ship.powerAvailable + powerSelected)

    val powerUnused: Int get() = Math.min(powerAvailableSys - powerSelected, ship.powerAvailable)

    abstract val sortingType: SortingType

    override fun powerStateChanged() {
        if (powerSelected > powerAvailable)
            simpleSelectedEnergyLevel = powerAvailable
    }

    open fun increasePower() {
        simpleSelectedEnergyLevel++
        powerStateChanged()
    }

    open fun decreasePower() {
        simpleSelectedEnergyLevel = Math.max(0, simpleSelectedEnergyLevel - 1)
        powerStateChanged()
    }

    // List of the default systems, for sorting purposes
    // TODO handle modded systems
    enum class SortingType {
        SHIELD,
        ENGINES,
        MEDBAY,
        OXYGEN,
        WEAPONS,
        DRONES;
    }
}
