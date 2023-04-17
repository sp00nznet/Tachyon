package xyz.znix.xftl.systems

import org.jdom2.Element
import kotlin.math.min

class Shields(blueprint: SystemBlueprint, elem: Element) : MainSystem(blueprint, elem) {
    override val sortingType: SortingType get() = SortingType.SHIELD

    var selectedShieldBars: Int = 0
        private set

    var activeShields: Int = 0
        set(value) {
            val old = field
            field = min(selectedShieldBars, value)

            if (value < old) {
                shieldsDownSound.play()
            }
        }

    override val powerSelected: Int get() = selectedShieldBars * 2

    var rechargeTimer: Float = 0f
        private set

    val rechargeDelay: Float get() = 2f

    private val shieldsUpSound by onInit { it.sounds.getSample("shieldsUp") }
    private val shieldsDownSound by onInit { it.sounds.getSample("shieldsDown") }

    override fun update(dt: Float) {
        super.update(dt)

        if (activeShields == selectedShieldBars)
            return

        rechargeTimer += dt
        if (rechargeTimer < rechargeDelay)
            return

        if (activeShields == 0) {
            shieldsUpSound.play()
        }

        rechargeTimer = 0f
        activeShields++
    }

    fun popShieldLayer() {
        if (activeShields == 0)
            return

        activeShields--
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
