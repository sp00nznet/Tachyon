package xyz.znix.xftl.drones

import org.newdawn.slick.Animation
import org.newdawn.slick.Graphics
import xyz.znix.xftl.AnimationSpec
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

    protected lateinit var explodeAnimation: AnimationSpec

    override fun init(ownerShip: Ship) {
        super.init(ownerShip)
        explodeAnimation = ownerShip.sys.animations["explosion_random"]
    }

    /**
     * Spawn in [targetRoom], or one of the nearby rooms if that's full.
     */
    protected fun spawn(targetRoom: Room) {
        ship = targetRoom.ship

        val emptySpace = ship.findSpaceForCrew(targetRoom, occupancySlotType)

        pawn = makePawn(emptySpace.room)
        ship.crew += pawn!!

        // Move the drone to it's starting position
        pawn!!.jumpTo(emptySpace)
    }

    protected open fun makePawn(room: Room): Pawn {
        return Pawn(room)
    }

    override fun removeInstance() {
        super.removeInstance()

        // If the pawn was never spawned, there's nothing to clean up.
        val pawn = this.pawn ?: return
        this.pawn = null

        // Destroy the pawn
        pawn.removeFromShip()
    }

    override fun update(dt: Float) {
        super.update(dt)

        // If something weird happens and our pawn disappears
        // from the enemy ship, assume it was destroyed.
        val pawn = this.pawn
        if (pawn != null && !pawn.room.ship.crew.contains(pawn)) {
            destroy()
        }
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
        override val canFight: Boolean get() = false
        override val hasDyingAnimation: Boolean get() = false
        override val suffocationMultiplier: Float get() = 0f
        override val playerControllable: Boolean get() = false

        override fun update(dt: Float) {
            // If the ship powering this drone has jumped away, destroy it.
            // Also self-destruct if the pawn field no longer points to this
            // pawn for whatever reason. It shouldn't happen, but this could
            // avoid a drone getting stuck on the player ship.
            if (!ship.sys.isShipPresent(ownerShip) || pawn != this) {
                removeFromShip()
                return
            }

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

            // Play the explosion animation whenever a drone is killed.
            ship.animations += Ship.FloatingAnimation.centered(explodeAnimation.start(), getPixelPositionCentre())
        }
    }
}
