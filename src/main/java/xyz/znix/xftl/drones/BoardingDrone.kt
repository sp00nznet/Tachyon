package xyz.znix.xftl.drones

import org.newdawn.slick.Graphics
import xyz.znix.xftl.*
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.weapons.AbstractProjectile
import xyz.znix.xftl.weapons.DroneBlueprint

class BoardingDrone(type: DroneBlueprint) : AbstractIndoorsDrone(type) {
    // TODO support the Ion Intruder drone, which I can't find anything other
    //  than it's name to tell it apart?

    override val occupancySlotType get() = AbstractCrew.SlotType.INTRUDER

    // It looks like anti-personnel and boarding drones use the same images?
    override val pawnCodename: String get() = "battle"

    private var projectile: FlyingDrone? = null

    override fun init(ownerShip: Ship) {
        super.init(ownerShip)

        val enemyShip = ownerShip.sys.getEnemyOf(ownerShip)!!
        val target = enemyShip.rooms.random()
        projectile = FlyingDrone(target)

        // Start in the centre of the shields, for lack of a better place.
        val startingPoint = ownerShip.shieldOrigin

        // The direction the drone flies in depends on whether this is
        // a player ship or an enemy ship, as it's supposed to go
        // forwards out of both of them.
        val endPoint = startingPoint + if (ownerShip.isPlayerShip) {
            // Fly right
            ConstPoint(1000, 0)
        } else {
            // Fly upwards
            ConstPoint(0, -1000)
        }

        projectile!!.setInitialPath(startingPoint, endPoint)

        ownerShip.projectiles += projectile!!
    }

    override fun updatePawn(dt: Float) {
        val pawn = this.pawn!!

        // Are we moving somewhere
        if (pawn.pathingTarget != null)
            return

        // Do we have unfinished business here?
        if (roomHasWorkingSystem(pawn.room))
            return
        if (roomContainsHostileCrew(pawn.room))
            return

        // Pick a random room to go and punch up the contents of.
        // It can either have a non-broken system or it can have friendly (to
        // the ship, not to the drone) crew.
        val candidates = ship.rooms.filter { roomContainsHostileCrew(it) || roomHasWorkingSystem(it) }

        // Nothing more to break and kill :(
        if (candidates.isEmpty())
            return

        // If the room is full this won't do anything, but it doesn't matter
        // as we'll just try again next update.
        pawn.setTargetRoom(candidates.random())
    }

    override fun update(dt: Float) {
        super.update(dt)

        // Check if the player ship jumped away or shot down the projectile
        if (pawn != null)
            return

        val projectile = this.projectile
        if (projectile == null) {
            // No projectile and no pawn? This probably shouldn't happen.
            destroy()
            return
        }

        // Check if the enemy ship changed, for example if it was killed.
        val enemyShip = ownerShip.sys.getEnemyOf(ownerShip)
        if (enemyShip == null || enemyShip != projectile.targetShip) {
            destroy()
            return
        }

        if (!ownerShip.projectiles.contains(projectile) && !enemyShip.projectiles.contains(projectile)) {
            // The projectile has vanished, for example if it was
            // greeted by a defence drone.
            destroy()
        }
    }

    private fun roomContainsHostileCrew(room: Room): Boolean {
        return room.crew.any { it.mode == AbstractCrew.SlotType.CREW }
    }

    private fun roomHasWorkingSystem(room: Room): Boolean {
        val system = room.system ?: return false

        // Can we damage it further?
        return system.damagedEnergyLevels != system.energyLevels
    }

    override fun destroy() {
        super.destroy()

        // If a flying drone was sent out, get rid of it.
        projectile?.destroyFlying()
    }

    override fun drawPawn() {
        // TODO draw the green glow when turned on
    }

    override fun makePawn(room: Room): Pawn = BoardingPawn(room)

    /**
     * The projectile that represents the drone flying through space towards
     * the target ship.
     */
    inner class FlyingDrone(val target: Room) : AbstractProjectile(target.ship) {
        // The portrait frame of the robot, which is shown on top
        // of the thruster sprite
        val portrait = ownerShip.sys.animations["battle_portrait"].spriteAt(0)

        val thruster = ownerShip.sys.getImg("img/ship/drones/boarder_engine.png")

        // Fished out with x32dbg as I couldn't be bothered to find
        // it via static analysis, and it's not guaranteed to be correct
        // if I got some addresses swapped.
        // Note it has a 16x multiplier, to convert it from pixels per
        // SpeedFactor to pixels per second. See doc/reveng-general.md.
        override val speed: Int get() = 18 * 16

        override var drawUnderShip: Boolean = true

        override val isMissileForDD: Boolean get() = true

        override fun reachedTarget() {
            // We've hit our target room.

            destroyFlying()

            // Spawn the drone pawn in the target room
            spawn(target)

            // If the drone ended up in a different room due to
            // the target being full, move it back but leave it
            // pathing to somewhere else.
            if (pawn!!.room != target) {
                pawn!!.jumpTo(target, ConstPoint.ZERO)
            }
        }

        fun destroyFlying() {
            dead = true
            projectile = null
        }

        override fun renderPreTranslated(g: Graphics) {
            // Centre the drone on the supplied x,y coordinates.
            val size = portrait.imageSize
            val offset = ConstPoint(-size.x / 2, -size.y / 2)

            // 'up' in the sprite is forwards, rotate it so that is the case.
            val angleOffset = 90 // In degrees because that's what Slick uses.

            g.rotate(0f, 0f, angleOffset.f)

            portrait.draw(offset)
            thruster.draw(offset)
        }

        override fun onSwitchedToTarget() {
            super.onSwitchedToTarget()

            drawUnderShip = false
        }

        override fun calculateTargetPosition(): IPoint {
            // Copied from AbstractWeaponProjectile.

            // Aim for the centre of the target room.
            return ConstPoint(
                target.offsetX + target.width * Constants.ROOM_SIZE / 2,
                target.offsetY + target.height * Constants.ROOM_SIZE / 2
            )
        }

        override fun hitOtherProjectile(currentSpace: Ship) {
            currentSpace.animations += Ship.FloatingAnimation.centered(explodeAnimation.start(), position)
        }
    }

    private inner class BoardingPawn(room: Room) : AbstractIndoorsDrone.Pawn(room) {
        // Always fight by shooting a laser
        override val canPunch: Boolean get() = false
        override val canFight: Boolean get() = true
        override val attackDamageMult: Float get() = 1.2f

        override val maxHealth: Float get() = 150f
    }
}
