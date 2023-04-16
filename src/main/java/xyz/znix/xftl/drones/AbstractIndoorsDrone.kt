package xyz.znix.xftl.drones

import org.newdawn.slick.Animation
import org.newdawn.slick.Graphics
import xyz.znix.xftl.Ship
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.crew.CrewBlueprint
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.weapons.DroneBlueprint

/**
 * A drone that operates on the inside of a ship, like a repair or boarding drone.
 */
abstract class AbstractIndoorsDrone(type: DroneBlueprint) : AbstractDrone(type) {
    /**
     * The type of slot that represents whether this drone counts as a crewmember
     * or an enemy for the purposes of occupying a cell in a room (this is where
     * you can have an enemy and a crewmember standing in the same cell, but not
     * two crewmembers in the same cell).
     */
    abstract val occupancySlotType: AbstractCrew.SlotType

    /**
     * The name of the blueprint for the fake crew race that's used for drawing the drone.
     *
     * This matches up with the spritesheets in img/ship/drones, and with
     * the crewBlueprint in the XML.
     */
    abstract val pawnCodename: String

    /**
     * The ship this drone is aboard. This may be different to [ownerShip],
     * in the case of boarding drones.
     */
    lateinit var ship: Ship
        private set

    var pawn: Pawn? = null
        private set

    /**
     * Spawn in [targetRoom], or one of the nearby rooms if that's full.
     */
    protected fun spawn(targetRoom: Room) {
        ship = targetRoom.ship

        // Try spawning in the preferred room
        if (trySpawn(targetRoom))
            return

        // Otherwise try the neighbouring rooms
        for (room in targetRoom.doors.mapNotNull { it.other(targetRoom) }) {
            if (trySpawn(room))
                return
        }

        // This is getting a bit silly, just pick some arbitrary room in the ship.
        for (room in ship.rooms) {
            if (trySpawn(room))
                return
        }

        error("Couldn't find any free space in ship ${ship.name} to deploy indoors drone ${type.name}")
    }

    protected open fun makePawn(room: Room): Pawn {
        return Pawn(room)
    }

    private fun trySpawn(targetRoom: Room): Boolean {
        // Pick a different room to create the drone in, so we can set
        // its target room properly.
        val otherRoom = ship.rooms.first { it != targetRoom }

        // While we create the pawn now, if there's no space
        // in this room we'll throw it away.
        val tmpPawn = makePawn(otherRoom)

        // Check if there's space in this room. If there is, this
        // pawn is added to that room's occupied slot array. Thus
        // we're committed to using this instance if this call
        // is successful.
        if (!tmpPawn.setTargetRoom(targetRoom)) {
            return false
        }

        // Move the drone to it's starting position
        tmpPawn.jumpTo(targetRoom, tmpPawn.pathingTarget!!)

        // TODO fix the drone driving up and down when spawned

        pawn = tmpPawn
        ship.crew += tmpPawn
        return true
    }

    override fun destroy() {
        super.destroy()

        // If the pawn was never spawned, there's nothing to clean up.
        val pawn = this.pawn ?: return
        this.pawn = null

        // Destroy the pawn
        pawn.removeFromShip()

        // TODO play the explosion animation
    }

    protected abstract fun updatePawn(dt: Float)

    protected abstract fun drawPawn()

    override fun onPowerChanged() {
        super.onPowerChanged()

        pawn?.onPowerChanged()
    }

    private fun getPawnBlueprint(): CrewBlueprint {
        return ownerShip.sys.blueprintManager[pawnCodename] as CrewBlueprint
    }

    /**
     * An indoors drone comprises two objects: the drone object itself, and
     * a crewmember 'pawn'. This is to allow drones like boarding drones that
     * first cross the screen and can be shot down, or otherwise don't
     * immediately spawn. Also, [AbstractCrew] needs the initial room in its
     * constructor, which is inconvenient for us.
     */
    open inner class Pawn(room: Room) :
        AbstractCrew(getPawnBlueprint(), room.ship.sys.animations, room, occupancySlotType) {

        var powerUpDuration: Float = 0f
        var onLastUpdate: Boolean = false
        var newPowerAnimation: Animation? = null

        override val canManSystem: Boolean get() = false
        override val hasDyingAnimation: Boolean get() = false
        override val canSuffocate: Boolean get() = false
        override val playerControllable: Boolean get() = false

        override fun update(dt: Float) {
            // If a new animation has been selected while paused, apply that now.
            // This avoids stuff changing while paused, which makes paused not
            // feel properly paused, for lack of a better term.
            newPowerAnimation?.let { icon = it }
            newPowerAnimation = null

            // Only progress on movement, fighting, repairs, etc if we're powered,
            // and the power-up/power-down animation isn't playing.
            val on = isPowered && powerUpDuration == 0f
            if (on) {
                super.update(dt)

                // Note the pawn can be null here if it was killed
                // during the update cycle.
                if (pawn != null)
                    updatePawn(dt)
            } else {
                icon.update((dt * 1000).toLong())

                if (powerUpDuration != 0f && isPowered) {
                    powerUpDuration -= dt
                    if (powerUpDuration < 0f) {
                        powerUpDuration = 0f
                        updateAnimation()
                    }
                }
            }

            // Keep track of whether the drone was turned off then on again
            // while paused, as the turning-on animation won't have to play.
            onLastUpdate = on
        }

        override fun draw(g: Graphics) {
            super.draw(g)
            drawPawn()
        }

        fun onPowerChanged() {
            val powerDir = when (isPowered) {
                true -> "power_up"
                false -> "power_down"
            }

            val animation = ship.sys.animations["${codename}_$powerDir"]
            newPowerAnimation = animation.start().apply {
                setLooping(false)
            }

            if (!isPowered) {
                powerUpDuration = animation.totalTime
            } else if (onLastUpdate) {
                powerUpDuration = 0f
            }
        }

        override fun removeFromShip() {
            super.removeFromShip()

            // Cleanly kill the drone
            this@AbstractIndoorsDrone.pawn = null
            destroy()
        }
    }
}
