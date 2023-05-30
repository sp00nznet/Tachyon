package xyz.znix.xftl.crew

import xyz.znix.xftl.Animations
import xyz.znix.xftl.layout.Room

class CrewZoltan(blueprint: CrewBlueprint, animations: Animations, room: Room, mode: SlotType) :
    LivingCrew(blueprint, animations, room, mode) {

    override val maxHealth: Float get() = 70f

    // TODO on-death animation

    // TODO implement power
}
