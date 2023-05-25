package xyz.znix.xftl.systems

import org.jdom2.Element
import xyz.znix.xftl.AbstractSystem
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil
import kotlin.math.max
import kotlin.math.min

abstract class MainSystem(blueprint: SystemBlueprint) : AbstractSystem(blueprint) {
    private var simpleSelectedEnergyLevel: Int = 1

    open val powerSelected: Int get() = simpleSelectedEnergyLevel

    val powerAvailable: Int get() = min(undamagedEnergy, ship.powerAvailable + powerSelected)

    val powerUnused: Int get() = min(undamagedEnergy - powerSelected, ship.powerAvailable)

    open val isPowerLocked: Boolean get() = isIonised || isHackActive

    abstract val sortingType: SortingType

    override fun powerStateChanged() {
        if (powerSelected > powerAvailable)
            simpleSelectedEnergyLevel = powerAvailable
    }

    open fun increasePower() {
        if (isPowerLocked)
            return

        simpleSelectedEnergyLevel++
        powerStateChanged()
    }

    open fun decreasePower() {
        if (isPowerLocked)
            return

        simpleSelectedEnergyLevel = max(0, simpleSelectedEnergyLevel - 1)
        powerStateChanged()
    }

    override fun saveToXML(elem: Element, refs: ObjectRefs) {
        super.saveToXML(elem, refs)

        SaveUtil.addTagInt(elem, "simpleSelectedEnergyLevel", simpleSelectedEnergyLevel)
    }

    override fun loadFromXML(elem: Element, refs: RefLoader) {
        simpleSelectedEnergyLevel = SaveUtil.getTagInt(elem, "simpleSelectedEnergyLevel")

        // Load our stuff before calling the super-method, so that when
        // the system loading code runs it has the correct power level.
        super.loadFromXML(elem, refs)
    }

    // List of the default systems, for sorting purposes
    // TODO handle modded systems
    enum class SortingType {
        SHIELD,
        ENGINES,
        MEDBAY,
        OXYGEN,
        TELEPORTER,
        CLOAKING,
        MIND_CONTROL,
        HACKING,
        WEAPONS,
        DRONES;
    }
}
