package xyz.znix.xftl.weapons

import org.jdom2.Element
import xyz.znix.xftl.Ship
import xyz.znix.xftl.drones.CombatDrone
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.rendering.Colour
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil
import xyz.znix.xftl.systems.Artillery
import xyz.znix.xftl.systems.Weapons
import kotlin.math.max

abstract class AbstractWeaponInstance(val type: AbstractWeaponBlueprint, val ship: Ship) {
    // The time spent charging thus far
    var timeCharged: Float = 0f

    // For charger weapons, this is how many shots are stored up, in
    // addition to the one represented by timeCharged.
    var extraCharges: Int = 0

    // For chain weapons, this is the number of shots we've fired since
    // the weapon was powered off. This is limited to the maximum number
    // of shots that have an effect.
    var chainCount: Int = 0

    // The number of charges this weapon is ready to fire.
    val totalReadyCharges: Int get() = extraCharges + if (isCurrentCharged) 1 else 0

    // Is this weapon selected as powered
    // To set this, use forceSetPowered and read it's JavaDoc.
    var isPowered: Boolean = false
        private set

    abstract val isFiring: Boolean

    // Turn missile weapons off once their ship runs out of missiles
    val hasEnoughMissiles: Boolean get() = ship.missilesCount >= type.missilesUsed

    // How far out is this weapon, on a scale of 0-1. Used for making weapons slide in and out when
    // powered up and down.
    var slide: Float = 0f

    /**
     * True if the weapon is ready to fire. For charge weapons this is true
     * even when [isCurrentCharged] is false, as long as they've stored away an extra shot.
     */
    val isCharged: Boolean get() = isCurrentCharged || extraCharges > 0

    /**
     * True if the weapon's current charge has finished charging. This is different
     * to [isCharged] for charge weapons, as this is only true when the currently
     * charging charge is done.
     */
    val isCurrentCharged: Boolean get() = timeCharged >= chargeTime
    val chargeProgress: Float get() = timeCharged / chargeTime

    val maxTotalCharges get() = type.chargeLevels ?: 1
    val maxExtraCharges get() = maxTotalCharges - 1

    val animation = type.getLauncher(ship.sys)

    private val boostAnim = animation.boostAnim?.startSingle(ship.sys)

    /**
     * True if the player can fire this weapon at their own ship, eg for bombs.
     */
    open val canTargetOwnShip: Boolean get() = false

    val chargeTime: Float
        get() {
            if (type.boost?.type != AbstractWeaponBlueprint.BoostType.COOLDOWN)
                return type.chargeTime

            return type.chargeTime - chainCount * type.boost.perShot
        }

    open fun update(dt: Float, chargeTime: Float, canCharge: Boolean) {
        if (isPowered) {
            if (canCharge)
                timeCharged += chargeTime
        } else {
            timeCharged -= dt * 10

            if (timeCharged <= 0) {
                extraCharges = 0
                chainCount = 0
            }
        }

        if (timeCharged > this.chargeTime)
            timeCharged = this.chargeTime
        if (timeCharged < 0f)
            timeCharged = 0f

        if (isCurrentCharged && extraCharges < maxExtraCharges) {
            timeCharged = 0f
            extraCharges++
        }
    }

    protected fun fire() {
        timeCharged = 0f
        extraCharges = 0

        // Count up
        if (type.boost != null) {
            chainCount = (chainCount + 1).coerceIn(0..type.boost.maxCount)
        }

        // Deduct a missile (or multiple), if this weapon uses them
        // This really shouldn't be going negative here, but guard
        // it just in case.
        if (!ship.sys.debugFlags.infiniteMissiles.set) {
            ship.missilesCount = max(0, ship.missilesCount - type.missilesUsed)
        }
    }

    open fun render(g: Graphics) {
        val launcher = animation.spriteAt(ship.sys, (animation.chargedFrame * chargeProgress).toInt())
        launcher.draw(0f, 0f)

        renderChainChargeLights()

        // Draw the charging glow, if present
        if (isCurrentCharged)
            return
        val glowPath = animation.chargeImage ?: return
        val glow = ship.sys.getImg(glowPath)

        val filter = Colour(1f, 1f, 1f, chargeProgress)
        glow.draw(0f, 0f, filter)
    }

    fun renderChainChargeLights() {
        // For charge weapons, draw on the indicator lights
        if (boostAnim != null && maxTotalCharges > 1 && totalReadyCharges > 0) {
            // 0 indicates one extra charge, so we have to -1.
            boostAnim.spriteAt(totalReadyCharges - 1).draw()
        }

        // For chain weapons, draw the progress
        if (boostAnim != null && type.boost != null && chainCount > 0) {
            // 0 indicates one shot already fired, so -1.
            boostAnim.spriteAt(chainCount - 1).draw()
        }
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
        SaveUtil.addTagInt(elem, "extraCharges", extraCharges, 0)
        SaveUtil.addTagInt(elem, "chainCount", chainCount, 0)

        val expectedSlide = if (isPowered) 1f else 0f
        SaveUtil.addTagFloat(elem, "slideAnimation", slide, expectedSlide)
    }

    open fun loadFromXML(elem: Element, refs: RefLoader) {
        require(type.name == SaveUtil.getAttr(elem, "type"))

        isPowered = SaveUtil.getAttrBool(elem, "powered")
        timeCharged = SaveUtil.getAttrFloat(elem, "chargeTime")
        extraCharges = SaveUtil.getOptionalTagInt(elem, "extraCharges") ?: 0
        chainCount = SaveUtil.getOptionalTagInt(elem, "chainCount") ?: 0

        val expectedSlide = if (isPowered) 1f else 0f
        slide = SaveUtil.getOptionalTagFloat(elem, "slideAnimation") ?: expectedSlide
    }
}

/**
 * Represents a weapon that can be fired at individual rooms. Includes basically everything but beams.
 */
interface IRoomTargetingWeapon {
    fun fire(targetSource: () -> Room)

    fun fireFromDrone(drone: CombatDrone, target: Room)

    fun asWeaponInstance(): AbstractWeaponInstance
}
