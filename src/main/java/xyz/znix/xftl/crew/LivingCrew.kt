package xyz.znix.xftl.crew

import xyz.znix.xftl.Animations
import xyz.znix.xftl.layout.Room
import kotlin.random.Random

/**
 * Represents a crew member that can be hired by the player.
 */
abstract class LivingCrew(codename: String, anims: Animations, room: Room, mode: SlotType) :
    AbstractCrew(codename, anims, room, mode) {

    /**
     * The player facing name of this crew, eg 'Slocknog' or some
     * other name that's selected by the player, by an event or
     * from the default list of names.
     */
    var selectedName: String

    init {
        // TODO make this a bit cleaner
        selectedName = room.ship.sys.nameManager.getForGender(null, "en", Random.Default)
    }
}
