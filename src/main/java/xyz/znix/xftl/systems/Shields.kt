package xyz.znix.xftl.systems

import org.jdom2.Element

class Shields(elem: Element) : MainSystem("shields", elem) {
    override val sortingType: SortingType get() = SortingType.SHIELD

    var selectedShieldBars: Int = 0

    override val selectedEnergyLevel: Int get() = selectedShieldBars * 2

    override fun powerStateChanged() {
        if (powerAvailable < selectedEnergyLevel)
            selectedShieldBars = Math.floorDiv(powerAvailable, 2)
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
