package xyz.znix.xftl.crew

import xyz.znix.xftl.Animations
import xyz.znix.xftl.Ship
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.layout.Room
import kotlin.random.Random

/**
 * Represents a crew member that can be hired by the player.
 */
abstract class LivingCrew(blueprint: CrewBlueprint, anims: Animations, room: Room, mode: SlotType) :
    AbstractCrew(blueprint, anims, room, mode) {

    /**
     * The player facing name of this crew, eg 'Slocknog' or some
     * other name that's selected by the player, by an event or
     * from the default list of names.
     */
    var selectedName: String

    /**
     * The ship that 'owns' this crewmember, or null if they were
     * spawned in by an intruder event.
     */
    var ownerShip: Ship? = room.ship

    init {
        // TODO make this a bit cleaner
        selectedName = room.ship.sys.nameManager.getForGender(null, "en", Random.Default)
    }

    override fun onMidTeleport() {
        super.onMidTeleport()

        // Apply the reconstructive teleport effect now, since this
        // is both the mid-point of our animation, and we can't take
        // any more damage on the enemy ship.
        if (currentAction != Action.DYING && hasAugment(AugmentBlueprint.RECONSTRUCTIVE_TELEPORT)) {
            health = maxHealth
        }
    }

    protected fun hasAugment(name: String): Boolean {
        return ownerShip?.hasAugment(name) == true
    }
}
