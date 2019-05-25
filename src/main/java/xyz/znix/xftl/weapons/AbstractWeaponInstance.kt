package xyz.znix.xftl.weapons

import xyz.znix.xftl.Ship

abstract class AbstractWeaponInstance(val type: ShipWeaponBlueprint, val ship: Ship) {
    // The time spent charging thus far
    var timeCharged: Float = 0f

    val isCharged: Boolean get() = timeCharged >= type.chargeTime
    val chargeProgress: Float get() = timeCharged / type.chargeTime

    open fun update(dt: Float) {
        timeCharged += dt
        if (timeCharged > type.chargeTime)
            timeCharged = type.chargeTime
    }

    protected fun fire() {
        timeCharged = 0f
    }
}
