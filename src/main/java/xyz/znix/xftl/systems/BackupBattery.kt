package xyz.znix.xftl.systems

import org.jdom2.Element
import org.newdawn.slick.Input
import xyz.znix.xftl.AbstractSystem
import xyz.znix.xftl.SystemInfo
import xyz.znix.xftl.Translator
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.game.Button
import xyz.znix.xftl.game.SystemPowerButton
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil

class BackupBattery(blueprint: SystemBlueprint) : SubSystem(blueprint) {
    override val sortingType: SortingType get() = SortingType.BATTERY

    private var timeRemaining: Float? = null

    /**
     * The amount of power this system contributes to the ship's reactor.
     */
    val contributedPower: Int
        get() = when {
            isHackActive -> -2
            timeRemaining == null -> 0
            else -> undamagedEnergy * 2
        }

    // TODO draw the white power padlock around the system's power bars while it's active.

    override fun update(dt: Float) {
        super.update(dt)

        if (timeRemaining != null) {
            if (broken || isHackActive) {
                timeRemaining = 0f
            }

            val newTime = timeRemaining!! - dt
            timeRemaining = newTime.coerceAtLeast(0f)

            if (newTime <= 0f) {
                timeRemaining = null
                enterCooldown()
            }
        }
    }

    private fun enterCooldown() {
        var duration = 20f

        if (ship.hasAugment(AugmentBlueprint.BATTERY_CHARGER)) {
            duration /= 2f
        }

        ionTimer += duration

        // Update all the systems, to pull power from them as required.
        // TODO treat and show battery power as a separate type of power.
        // TODO do this when we're hacked.
        for (system in ship.systems) {
            system.powerStateChanged()
        }
    }

    override fun makeExtraButtons(powerPos: IPoint): List<Button> {
        return listOf(BatteryButton(powerPos))
    }

    override fun saveSystem(elem: Element, refs: ObjectRefs) {
        SaveUtil.addAttrFloat(elem, "timeRemaining", timeRemaining)
    }

    override fun loadSystem(elem: Element, refs: RefLoader) {
        timeRemaining = SaveUtil.getAttrFloatOrNull(elem, "timeRemaining")
    }

    private inner class BatteryButton(powerPos: IPoint) : SystemPowerButton(ship.sys, 3, powerPos) {

        // The battery base image is slightly different to properly fit the square subsystem icon.
        override val base = game.getImg("img/systemUI/button_battery3_base.png")

        override val timeRemaining: Float? get() = this@BackupBattery.timeRemaining
        override val duration: Float get() = DURATION
        override val isOff: Boolean get() = ionTimer != 0f || isHackActive

        override fun click(button: Int) {
            if (button != Input.MOUSE_LEFT_BUTTON)
                return

            if (disabled)
                return

            this@BackupBattery.timeRemaining = DURATION
        }
    }

    companion object {
        val INFO: SystemInfo = BatteryInfo

        const val DURATION: Float = 30f
    }
}

private object BatteryInfo : SystemInfo("battery") {
    override val canBeManned: Boolean get() = false

    override fun create(blueprint: SystemBlueprint): AbstractSystem = BackupBattery(blueprint)

    override fun getLevelName(level: Int, translator: Translator): String {
        val power = 2 * (level + 1)
        return translator["battery_power"].replace("\\1", power.toString())
    }
}
