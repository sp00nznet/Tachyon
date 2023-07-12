package xyz.znix.xftl.crew

import xyz.znix.xftl.Animations
import xyz.znix.xftl.f
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.rendering.Graphics

class CrewZoltan(blueprint: CrewBlueprint, animations: Animations, room: Room, mode: SlotType) :
    LivingCrew(blueprint, animations, room, mode) {

    override val maxHealth: Float get() = 70f

    override fun draw(g: Graphics) {
        // The explosion animation is in it's own image, so we have to draw
        // that without using any of the usual layer stuff.
        if (currentAction != Action.DYING) {
            super.draw(g)
            return
        }

        // The explosion is also larger than usual, so centre it
        val centre = getPixelPositionCentre()
        icon.draw(
            (centre.x - icon.width / 2).f,
            (centre.y - icon.height / 2).f
        )
    }

    override fun onStartedDying() {
        super.onStartedDying()

        for (crew in room.crew) {
            crew.dealDamage(ZoltanDeathDamage(15f, this))
        }
    }

    // TODO implement power
}
