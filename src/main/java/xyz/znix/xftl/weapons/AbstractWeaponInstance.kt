package xyz.znix.xftl.weapons

import org.newdawn.slick.Graphics
import xyz.znix.xftl.Ship

abstract class AbstractWeaponInstance(val type: ShipWeaponBlueprint, val ship: Ship) {
    // The time spent charging thus far
    var timeCharged: Float = 0f

    // Is this weapon selected as powered
    var isPowered: Boolean = false

    val isCharged: Boolean get() = timeCharged >= type.chargeTime
    val chargeProgress: Float get() = timeCharged / type.chargeTime

    val animation = ship.sys.animations.weaponAnimations.getValue(type.launcher)

    open fun update(dt: Float) {
        if (isPowered)
            timeCharged += dt
        if (timeCharged > type.chargeTime)
            timeCharged = type.chargeTime
    }

    protected fun fire() {
        timeCharged = 0f
    }

    open fun render(g: Graphics) {
        val launcher = animation.spriteAt((animation.chargedFrame * chargeProgress).toInt())
        launcher.draw(0f, 0f)
    }
}
