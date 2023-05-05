package xyz.znix.xftl.augments

import org.jdom2.Element
import xyz.znix.xftl.Blueprint
import xyz.znix.xftl.Ship
import xyz.znix.xftl.crew.LivingCrew
import kotlin.math.min

/**
 * The base augment class, which is suitable for augments which are
 * implemented as part of another system (for example, reconstructive
 * teleport is implemented by the teleporter).
 *
 * In general, it's probably a good idea to implement augments here
 * to show likely pain points for modding.
 */
open class AugmentBlueprint(elem: Element) : Blueprint(elem) {
    override val cost: Int = elem.getChildTextTrim("cost").toInt()
    val stackable: Boolean = elem.getChildTextTrim("stackable")!!.toBoolean()

    open fun update(ship: Ship, dt: Float) {}

    open fun onJump(ship: Ship) {}

    companion object {
        const val LONG_RANGE_SCANNERS: String = "ADV_SCANNERS"
        const val RECONSTRUCTIVE_TELEPORT: String = "TELEPORT_HEAL"
        const val OXYGEN_MASKS: String = "O2_MASKS"
    }
}

class AugEngiMedbots(elem: Element) : AugmentBlueprint(elem) {
    override fun update(ship: Ship, dt: Float) {
        super.update(ship, dt)

        val medbay = ship.medbay ?: return
        if (medbay.powerSelected == 0)
            return

        val healing = dt * 1.6f

        for (crew in ship.friendlyCrew) {
            if (crew !is LivingCrew)
                continue

            // Medbots don't add extra healing in the medbay
            if (crew.room == medbay.room)
                continue

            crew.health = min(crew.health + healing, crew.maxHealth)
        }
    }

    companion object {
        const val NAME = "NANO_MEDBAY"
    }
}

class AugPreigniter(elem: Element) : AugmentBlueprint(elem) {
    override fun onJump(ship: Ship) {
        super.onJump(ship)

        for (hp in ship.hardpoints) {
            val weapon = hp.weapon ?: continue

            // We don't charge up unpowered weapons
            if (!weapon.isPowered)
                continue

            weapon.timeCharged = weapon.type.chargeTime
        }
    }

    companion object {
        const val NAME: String = "WEAPON_PREIGNITE"
    }
}
