package xyz.znix.xftl.systems

import org.jdom2.Element
import xyz.znix.xftl.AbstractSystem
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil
import kotlin.math.min

abstract class MainSystem(blueprint: SystemBlueprint) : AbstractSystem(blueprint) {
    var powerSelected: Int = 1
        private set

    val powerAvailable: Int get() = min(undamagedEnergy, ship.powerAvailable + powerSelected)

    val powerUnused: Int get() = min(undamagedEnergy - powerSelected, ship.powerAvailable)

    open val isPowerLocked: Boolean get() = isIonised || isHackActive

    abstract val sortingType: SortingType

    /**
     * Use a 54-pixel gap in the power bar, to allow a button (eg cloaking)
     * to fit in next to the power icon.
     */
    open val insertButtonSpace: Boolean get() = false


    override fun powerStateChanged() {
        if (powerSelected > powerAvailable)
            powerSelected = powerAvailable.coerceAtLeast(0)
    }

    open fun increasePower() {
        if (isPowerLocked)
            return

        setSystemPower(powerSelected + 1)
    }

    open fun decreasePower() {
        if (isPowerLocked)
            return

        setSystemPower(powerSelected - 1)
    }

    /**
     * Attempt to set the power to a given level.
     *
     * When increasing the power, this is atomic - the power will either be
     * increased to [level], or it will be left the same - it won't be partially
     * increased if it's not possible to increase it all the way.
     *
     * This does NOT check [isPowerLocked] - that must be checked by the caller
     * if appropriate.
     *
     * Returns true if the change was made, or false if that was not possible.
     */
    protected fun setSystemPower(level: Int): Boolean {
        // Increasing power is atomic, eg for shields we need to increase or
        // decrease it by 2, or do nothing.
        val available = powerAvailable
        if (level > available && level > powerSelected)
            return false

        powerSelected = level
        powerStateChanged()

        return true
    }

    override fun saveToXML(elem: Element, refs: ObjectRefs) {
        super.saveToXML(elem, refs)

        SaveUtil.addAttrInt(elem, "power", powerSelected)
    }

    override fun loadFromXML(elem: Element, refs: RefLoader) {
        powerSelected = SaveUtil.getAttrInt(elem, "power")

        // Load our stuff before calling the super-method, so that when
        // the system loading code runs it has the correct power level.
        super.loadFromXML(elem, refs)
    }

    // List of the default systems, for sorting purposes
    // Modded systems can just use one of the vanilla systems, since if two
    // systems share the same sorting type they'll use the room ID as a tie-breaker.
    enum class SortingType {
        SHIELD,
        ENGINES,
        MEDBAY,
        CLONEBAY,
        OXYGEN,
        TELEPORTER,
        CLOAKING,
        ARTILLERY,
        MIND_CONTROL,
        HACKING,
        WEAPONS,
        DRONES;
    }
}
