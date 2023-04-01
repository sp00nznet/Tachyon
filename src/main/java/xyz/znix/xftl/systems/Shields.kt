package xyz.znix.xftl.systems

import org.jdom2.Element
import kotlin.math.min

class Shields(blueprint: SystemBlueprint, elem: Element) : MainSystem(blueprint, elem) {
    override val sortingType: SortingType get() = SortingType.SHIELD

    var selectedShieldBars: Int = 0
        private set

    var activeShields: Int = 0
        set(value) {
            field = min(selectedShieldBars, value)
        }

    override val powerSelected: Int get() = selectedShieldBars * 2

    var rechargeTimer: Float = 0f
        private set

    val rechargeDelay: Float get() = 2f

    override fun update(dt: Float) {
        super.update(dt)

        if (activeShields == selectedShieldBars)
            return

        rechargeTimer += dt
        if (rechargeTimer < rechargeDelay)
            return

        rechargeTimer = 0f
        activeShields++
    }

    override fun powerStateChanged() {
        if (powerAvailable < powerSelected)
            selectedShieldBars = Math.floorDiv(powerAvailable, 2)

        if (activeShields > selectedShieldBars)
            activeShields = selectedShieldBars
    }

    override fun increasePower() {
        if (isPowerLocked)
            return

        selectedShieldBars++
        powerStateChanged()
    }

    override fun decreasePower() {
        if (isPowerLocked)
            return

        if (selectedShieldBars > 0)
            selectedShieldBars--

        powerStateChanged()
    }
}
