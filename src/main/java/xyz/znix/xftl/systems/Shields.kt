package xyz.znix.xftl.systems

import org.jdom2.Element

class Shields(elem: Element) : MainSystem("shields", elem) {
    override val sortingType: SortingType get() = SortingType.SHIELD

    var selectedShieldBars: Int = 0

    var activeShields: Int = 0
        set(value) {
            field = Math.min(selectedShieldBars, value)
        }

    override val powerSelected: Int get() = selectedShieldBars * 2

    var rechargeTimer: Float = 0f
        private set(value) {
            field = value
        }

    val rechargeDelay: Float get() = 2f

    override fun update(dt: Float) {
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
        selectedShieldBars++
        powerStateChanged()
    }

    override fun decreasePower() {
        if (selectedShieldBars > 0)
            selectedShieldBars--

        powerStateChanged()
    }
}
