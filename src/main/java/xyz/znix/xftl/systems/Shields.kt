package xyz.znix.xftl.systems

import org.jdom2.Element
import kotlin.math.max

class Shields(blueprint: SystemBlueprint, elem: Element) : MainSystem(blueprint, elem) {
    override val sortingType: SortingType get() = SortingType.SHIELD

    var selectedShieldBars: Int = 0
        private set

    var activeShields: Int = 0
        set(value) {
            val old = field
            field = value.coerceIn(0..selectedShieldBars)

            if (value < old) {
                shieldsDownSound.play()
            }
        }

    override val powerSelected: Int get() = selectedShieldBars * 2

    var rechargeTimer: Float = 0f
        private set

    // Used for hacking - the charge bar runs backwards
    // and then drops a shield level when it hits zero.
    var discharging: Boolean = false
        private set

    val rechargeDelay: Float get() = 2f

    val hackDrainTime: Float get() = 2f

    private val shieldsUpSound by onInit { it.sounds.getSample("shieldsUp") }
    private val shieldsDownSound by onInit { it.sounds.getSample("shieldsDown") }

    override fun update(dt: Float) {
        super.update(dt)

        if (isHackActive) {
            updateHacked(dt)
            return
        }

        if (activeShields == selectedShieldBars) {
            rechargeTimer = 0f
            return
        }

        // Leave discharge mode immediately without
        // resetting the progress or anything like that.
        if (discharging) {
            discharging = false

            // If we were very, very close to discharging
            // this shield, assume it was an off-by-one-frame
            // error. Without this, a level-1 hack can't
            // take down two shields.
            if (rechargeTimer < dt * 2 + 0.02) {
                rechargeTimer = 0f
                activeShields--
            }
        }

        rechargeTimer += dt
        if (rechargeTimer < rechargeDelay)
            return

        if (activeShields == 0) {
            shieldsUpSound.play()
        }

        rechargeTimer = 0f
        activeShields++
    }

    private fun updateHacked(dt: Float) {
        // Note that if we were recharging a shield layer,
        // then we have to undo that progress before we can
        // get started on the first bubble.
        // This happens (which is intended) because rechargeTimer
        // starts at a non-zero value in that case, and we have
        // to drain it before we switch to discharge mode.

        if (activeShields == 0) {
            rechargeTimer = 0f
            return
        }

        rechargeTimer = max(rechargeTimer - dt, 0f)
        if (rechargeTimer <= 0f && activeShields > 0) {
            rechargeTimer = hackDrainTime

            // This implements the above-mentioned system,
            // and is what sets rechargeTimer when we're
            // hacking fully-charged shields.
            if (!discharging) {
                discharging = true
                return
            }

            activeShields--

            // Without this, rechargeTimer would jump back to 2s for
            // one frame, which is very visible on the shield charge bar.
            if (activeShields == 0) {
                rechargeTimer = 0f
            }
        }
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
