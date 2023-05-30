package xyz.znix.xftl.crew

import xyz.znix.xftl.Animations
import xyz.znix.xftl.layout.Room

class CrewRock(blueprint: CrewBlueprint, animations: Animations, room: Room, mode: SlotType) :
    LivingCrew(blueprint, animations, room, mode) {

    override val maxHealth: Float get() = 150f
    override val movementSpeed: Float get() = BASE_MOVEMENT_SPEED * 0.5f
    // TODO no fire damage
    // TODO fight fires faster
}
