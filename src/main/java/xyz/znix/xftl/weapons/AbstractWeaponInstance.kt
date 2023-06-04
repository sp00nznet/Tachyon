package xyz.znix.xftl.weapons

import org.jdom2.Element
import org.newdawn.slick.Graphics
import xyz.znix.xftl.Ship
import xyz.znix.xftl.drones.CombatDrone
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil
import xyz.znix.xftl.systems.Artillery
import xyz.znix.xftl.systems.Weapons
import kotlin.math.max

abstract class AbstractWeaponInstance(val type: AbstractWeaponBlueprint, val ship: Ship) {
    // The time spent charging thus far
    var timeCharged: Float = 0f

    // Is this weapon selected as powered
    // To set this, use forceSetPowered and read it's JavaDoc.
    var isPowered: Boolean = false
        private set

    // Turn missile weapons off once their ship runs out of missiles
    val hasEnoughMissiles: Boolean get() = ship.missilesCount >= type.missilesUsed

    // How far out is this weapon, on a scale of 0-1. Used for making weapons slide in and out when
    // powered up and down.
    var slide: Float = 0f

    val isCharged: Boolean get() = timeCharged >= type.chargeTime
    val chargeProgress: Float get() = timeCharged / type.chargeTime

    val animation = type.getLauncher(ship.sys)

    open fun update(dt: Float, canCharge: Boolean, isHacked: Boolean) {
        var chargeMult = when (ship.sys.debugFlags.fastWeaponCharge.set) {
            true -> 10f
            false -> 1f
        }

        // When hacked, the weapons charge backwards at the same
        // speed as they charge normally.
        if (isHacked) {
            chargeMult *= -1
        }

        if (isPowered) {
            if (!hasEnoughMissiles)
                isPowered = false

            if (canCharge)
                timeCharged += dt * chargeMult
        } else {
            timeCharged -= dt * 10
        }

        if (timeCharged > type.chargeTime)
            timeCharged = type.chargeTime
        if (timeCharged < 0f)
            timeCharged = 0f
    }

    protected fun fire() {
        timeCharged = 0f

        // Deduct a missile (or multiple), if this weapon uses them
        // This really shouldn't be going negative here, but guard
        // it just in case.
        if (!ship.sys.debugFlags.infiniteMissiles.set) {
            ship.missilesCount = max(0, ship.missilesCount - type.missilesUsed)
        }
    }

    open fun render(g: Graphics) {
        val launcher = animation.spriteAt((animation.chargedFrame * chargeProgress).toInt())
        launcher.draw(0f, 0f)

        // Draw the charging glow, if present
        if (isCharged)
            return
        val glow = animation.chargeImage ?: return

        glow.alpha = chargeProgress
        glow.draw(0f, 0f)
    }

    fun asWeaponInstance(): AbstractWeaponInstance = this

    /**
     * Power this weapon on or off.
     *
     * This should rarely be used - you should have a very
     * good reason not to use [Weapons.setWeaponPower], since
     * this doesn't check if the weapons system is ion-locked.
     */
    fun forceSetPowered(newPowerState: Boolean) {
        isPowered = newPowerState
    }

    open fun bindToWeaponsSystem(weapons: Weapons) {
    }

    open fun bindToArtillery(artillery: Artillery) {
    }

    open fun saveToXML(elem: Element, refs: ObjectRefs) {
        SaveUtil.addAttr(elem, "type", type.name)
        SaveUtil.addAttrBool(elem, "powered", isPowered)
        SaveUtil.addAttrFloat(elem, "chargeTime", timeCharged)

        val expectedSlide = if (isPowered) 1f else 0f
        SaveUtil.addTagFloat(elem, "slideAnimation", slide, expectedSlide)
    }

    open fun loadFromXML(elem: Element, refs: RefLoader) {
        require(type.name == SaveUtil.getAttr(elem, "type"))

        isPowered = SaveUtil.getAttrBool(elem, "powered")
        timeCharged = SaveUtil.getAttrFloat(elem, "chargeTime")

        val expectedSlide = if (isPowered) 1f else 0f
        slide = SaveUtil.getOptionalTagFloat(elem, "slideAnimation") ?: expectedSlide
    }
}

/**
 * Represents a weapon that can be fired at a single room. Includes basically everything but beams.
 */
interface IRoomTargetingWeapon {
    fun fire(target: Room)

    fun fireFromDrone(drone: CombatDrone, target: Room)

    fun asWeaponInstance(): AbstractWeaponInstance
}
