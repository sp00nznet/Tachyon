package xyz.znix.xftl.systems

import org.jdom2.Element
import xyz.znix.xftl.AbstractSystem
import xyz.znix.xftl.Constants
import xyz.znix.xftl.game.EnergySource
import xyz.znix.xftl.game.ReactorEnergySource
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil
import kotlin.math.max
import kotlin.math.min

abstract class MainSystem(blueprint: SystemBlueprint) : AbstractSystem(blueprint) {
    /**
     * The amount of power this system is drawing from the reactor, and other sources.
     *
     * It's the amount 'selected' because it's the value you change by
     * adjusting the system's power level.
     *
     * This is simply the sum of all the power in [selectedPowerSources].
     */
    // Note: this is cached since it's used *everywhere*
    var powerSelected: Int = 1
        private set

    /**
     * The power this system is supplied with, broken down by where it's coming from.
     */
    private val selectedPowerSources = HashMap<EnergySource, Int>()

    val powerAvailable: Int get() = min(undamagedEnergy, ship.powerAvailable + powerSelected)

    val powerUnused: Int get() = min(undamagedEnergy - powerSelected, ship.powerAvailable)

    open val isPowerLocked: Boolean get() = isIonised || isHackActive

    abstract val sortingType: SortingType

    /**
     * Use a 54-pixel gap in the power bar, to allow a button (eg cloaking)
     * to fit in next to the power icon.
     */
    open val insertButtonSpace: Boolean get() = false


    override fun drawPowerBars(g: Graphics, x: Int, yOfBottom: Int): Int {
        // The top of the bottom bar
        val baseY = yOfBottom - 6

        // Need to grab this as a local so the compiler knows it won't change
        val scriptedPowerLimit = this.scriptedPowerLimit

        var nextLevel = 0

        // Draw the power sources in priority order - the highest-priority
        // sources go at the bottom, lower priorities go higher up.
        for (type in EnergySource.TYPES) {
            val amount = selectedPowerSources[type] ?: 0
            if (amount == 0)
                continue

            for (i in 0 until amount) {
                val y = baseY - nextLevel++ * 8

                // Use the generic power bar visuals
                type.drawSystemPowerBar(g, this, x, y, 16, 6)
            }
        }

        // Draw the remaining, non-powered bars
        while (nextLevel < energyLevels) {
            val level = nextLevel++
            val y = baseY - level * 8

            when {
                level >= energyLevels - damagedEnergyLevels -> {
                    // System damaged/broken
                    g.colour = Constants.SYS_ENERGY_BROKEN
                    g.drawRect(x, y, 16 - 1, 6 - 1)
                    g.drawLine(x, y + 6, x + 16, y)
                }

                scriptedPowerLimit != null && level >= scriptedPowerLimit -> {
                    // System power limited by a scripted event
                    g.colour = Constants.SYS_ENERGY_EVENT_LOCKED
                    g.drawRect(x, y, 16 - 1, 6 - 1)
                    g.drawLine(x, y + 6, x + 16, y)
                }

                else -> {
                    // System depowered
                    g.colour = Constants.SYS_ENERGY_DEPOWERED
                    g.drawRect(x, y, 16 - 1, 6 - 1)
                }
            }
        }

        val topBarY = baseY - (nextLevel - 1) * 8

        // The repair bar
        val repairY = topBarY + (damagedEnergyLevels - 1) * 8
        g.colour = Constants.SYS_ENERGY_REPAIR
        val repairWidth = (16 * repairProgress).toInt()
        g.fillRect(x + 16 - repairWidth, repairY, repairWidth, 6)

        // The sabotage bar
        val sabotageY = topBarY + damagedEnergyLevels * 8
        g.colour = Constants.SYS_ENERGY_SABOTAGE
        val sabotageWidth = (16 * damageProgress).toInt()
        g.fillRect(x, sabotageY, sabotageWidth, 6)

        return topBarY
    }

    override fun powerStateChanged() {
        // This ultimately calls consumePower, which will reduce our selected
        // power if there isn't enough.
        ship.updateAvailablePower()
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

        if (level < 0 || level > undamagedEnergy)
            return false

        powerSelected = level
        powerStateChanged()

        return true
    }

    /**
     * Deduct this system's power usage from the ship's available power, and reduce
     * this system's power usage if there's not enough left.
     *
     * This function must not take more power than the system was previously using
     * from any source, as that may deprive other systems of power.
     *
     * This should only be called by Ship.updateAvailablePower.
     */
    fun consumePowerFirst(powerAvailable: HashMap<EnergySource, Int>) {
        val previousSources = HashMap(selectedPowerSources)
        selectedPowerSources.clear()

        var totalRemaining = powerSelected.coerceIn(0, undamagedEnergy)

        // TYPES is in order of priority, so we'll use stuff like Zoltan power
        // before reactor or battery power.
        for (type in EnergySource.TYPES) {
            var remaining = previousSources[type] ?: 0
            var newPower = 0

            // If we've got some new power from one source (eg a Zoltan), that
            // should reduce the amount of power we pull from the
            // lowest-priority source.
            remaining = min(remaining, totalRemaining)

            // If this source is supplying power to this specific system,
            // separately from the ship's power (ie, Zoltans), then use
            // that first.
            val bonusPower = min(type.getSystemPower(this), undamagedEnergy)
            remaining = max(remaining - bonusPower, 0)
            newPower += bonusPower

            // If that's not enough, pull this type of power from the ship.
            val available = powerAvailable[type] ?: continue
            val toDeduct = min(available, remaining)
            powerAvailable[type] = available - toDeduct

            remaining -= toDeduct
            newPower += toDeduct

            selectedPowerSources[type] = newPower
            totalRemaining -= newPower

            // These shouldn't be negative
            require(toDeduct >= 0)
            require(remaining >= 0)
            require(available >= 0)
            require(totalRemaining >= 0)
        }
    }

    /**
     * Like [consumePowerFirst], but this function can grab extra power since
     * it won't steal it from other systems.
     *
     * This should only be called by Ship.updateAvailablePower.
     */
    fun consumePowerSecond(powerAvailable: HashMap<EnergySource, Int>) {
        // Separately track how much power to add, so we can switch between
        // sources should one run out.
        // This comes from powerSelected, so that setSystemPower can use that
        // to indicate how much power we actually want.
        val previousDemand = powerSelected.coerceIn(0, undamagedEnergy)
        val currentAmount = selectedPowerSources.values.sum()
        var totalRemaining = previousDemand - currentAmount

        // powerSelected matches how much power we have
        if (totalRemaining == 0) {
            return
        }

        // One or more power sources couldn't supply as much as they used to,
        // or setSystemPower is demanding a bit more.
        // In either case, grab as much of it as we can in order of priority.
        for (type in EnergySource.TYPES) {
            val prevAmount = selectedPowerSources[type] ?: 0

            val available = powerAvailable[type] ?: continue
            val toDeduct = min(available, totalRemaining)
            powerAvailable[type] = available - toDeduct

            totalRemaining -= toDeduct
            selectedPowerSources[type] = prevAmount + toDeduct
        }

        powerSelected = selectedPowerSources.values.sum()
    }

    override fun saveToXML(elem: Element, refs: ObjectRefs) {
        super.saveToXML(elem, refs)

        // Put the reactor power in an attribute, since it's by far the most
        // common, and it lets most of the system elements collapse.
        val reactorPower = selectedPowerSources[ReactorEnergySource] ?: 0
        SaveUtil.addAttrInt(elem, "power", reactorPower)

        for (type in EnergySource.TYPES) {
            // Don't add reactor power in both an attribute and element
            if (type == ReactorEnergySource)
                continue

            val amount = selectedPowerSources[type] ?: 0
            if (amount == 0)
                continue

            val powerElem = Element("powerSource")
            SaveUtil.addAttrInt(powerElem, "amount", amount)
            SaveUtil.addAttr(powerElem, "type", type.serialisationId)
            elem.addContent(powerElem)
        }
    }

    override fun loadFromXML(elem: Element, refs: RefLoader) {
        // Load the power sources, split into their different types.
        selectedPowerSources.clear()
        selectedPowerSources[ReactorEnergySource] = SaveUtil.getAttrInt(elem, "power")

        for (powerElem in elem.getChildren("powerSource")) {
            val amount = SaveUtil.getAttrInt(powerElem, "amount")
            val typeId = SaveUtil.getAttr(powerElem, "type")

            val type = EnergySource.TYPES.first { it.serialisationId == typeId }
            selectedPowerSources[type] = amount
        }

        powerSelected = selectedPowerSources.values.sum()

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
