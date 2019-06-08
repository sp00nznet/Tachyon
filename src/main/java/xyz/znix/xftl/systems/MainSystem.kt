package xyz.znix.xftl.systems

import org.jdom2.Element
import xyz.znix.xftl.AbstractSystem

abstract class MainSystem(codename: String, elem: Element) : AbstractSystem(codename, elem) {
    private var simpleSelectedEnergyLevel: Int = 1
    open val selectedEnergyLevel: Int get() = simpleSelectedEnergyLevel

    val powerAvailable: Int get() = energyLevels - damagedEnergyLevels

    abstract val sortingType: SortingType

    override fun powerStateChanged() {
        if (selectedEnergyLevel > powerAvailable)
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
        WEAPONS;
        // TODO support drones
    }
}
