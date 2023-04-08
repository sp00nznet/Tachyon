package xyz.znix.xftl.drones

import org.newdawn.slick.Graphics
import xyz.znix.xftl.Constants.ROOM_SIZE
import xyz.znix.xftl.Ship
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.draw
import xyz.znix.xftl.imageSize
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.Point
import xyz.znix.xftl.weapons.DroneBlueprint
import xyz.znix.xftl.weapons.IProjectile
import kotlin.math.cos
import kotlin.math.sin

class BoardingDrone(type: DroneBlueprint) : AbstractIndoorsDrone(type) {
    // TODO support the Ion Intruder drone, which I can't find anything other
    //  than it's name to tell it apart?

    override val occupancySlotType get() = AbstractCrew.SlotType.INTRUDER

    // It looks like anti-personnel and boarding drones use the same images?
    override val pawnCodename: String get() = "battle"

    private var projectile: FlyingDrone? = null

    // Same as 'ship', but works when the drone is flying.
    // Used to destroy the drone if we jump away.
    private lateinit var enemyShip: Ship

    override fun init(ownerShip: Ship) {
        super.init(ownerShip)

        // TODO support enemy-to-player boarding
        enemyShip = ownerShip.sys.enemy
        val target = enemyShip.rooms.random()
        projectile = FlyingDrone(target)
        enemyShip.inboundProjectiles += projectile!!
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

    private fun roomContainsHostileCrew(room: Room): Boolean {
        for (slot in room.reservedPlayerSlots) {
            if (slot == null)
                continue

            // Skip crewmembers on their way to the room
            if (slot.room != room)
                continue

            return true
        }

        return false
    }

    private fun roomHasWorkingSystem(room: Room): Boolean {
        val system = room.system ?: return false

        // Can we damage it further?
        return system.damagedEnergyLevels != system.energyLevels
    }

    override fun onEnemyShipUpdated() {
        super.onEnemyShipUpdated()

        // Destroy the drone when we jump away, and when the enemy ship is killed.
        if (ownerShip.sys.enemy != enemyShip) {
            destroy()

            // If a flying drone was sent out, get rid of it in case we jump back.
            projectile?.destroyFlying()
        }
    }

    override fun drawPawn() {
        // TODO draw the green glow when turned on
    }

    override fun drawBackground(g: Graphics) {
        super.drawBackground(g)

        // Draw the drone flying away towards the enemy ship
        val proj = this.projectile ?: return

        // Start in the centre of the shields, for lack of a better place.
        val startingPoint = ownerShip.shieldOffset + ownerShip.shieldHalfSize
        val dist = proj.initialDistance - proj.distance

        proj.render(g, startingPoint.x + dist, startingPoint.y.toFloat(), 0f)
    }

    /**
     * The projectile that represents the drone flying through space towards
     * the target ship.
     */
    inner class FlyingDrone(val target: Room) : IProjectile {
        // The angle we are approaching the target at, in radians
        var angle: Float = (Math.random() * Math.PI * 2).toFloat()

        // The angle the projectile is heading in, in radians
        // Copied from AbstractProjectile
        override val projectileAngle: Float
            get() {
                val shift = angle - Math.PI
                return (if (shift < 0) shift + Math.PI * 2 else shift).toFloat()
            }

        override val position = Point(0, 0)

        // The portrait frame of the robot, which is shown on top
        // of the thruster sprite
        val portrait = ship.sys.animations["battle_portrait"].spriteAt(0)

        val thruster = ship.sys.getImg("img/ship/drones/boarder_engine.png")

        override fun isDead(): Boolean {
            return false
        }

        override fun update(dt: Float) {
            // Copied from AbstractProjectile.calculatePositionFor
            val offX = cos(angle.toDouble()) * distance
            val offY = sin(angle.toDouble()) * distance
            position.x = offX.toInt() + target.offsetX + target.width * ROOM_SIZE / 2
            position.y = offY.toInt() + target.offsetY + target.height * ROOM_SIZE / 2

            timeInFlight += dt

            // Have we hit our destination room?
            if (timeInFlight >= travelTime) {
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
        }

        fun destroyFlying() {
            ship.inboundProjectiles.remove(this)
            projectile = null
        }

        override fun render(g: Graphics, x: Float, y: Float, rotation: Float) {
            // Centre the drone on the supplied x,y coordinates.
            val size = portrait.imageSize
            val offset = ConstPoint(-size.x / 2, -size.y / 2)

            // 'up' in the sprite is forwards, rotate it so that is the case.
            val angleOffset = 90 // In degrees because that's what Slick uses.

            g.pushTransform()
            g.translate(x, y)
            g.rotate(0f, 0f, rotation + angleOffset)

            portrait.draw(offset)
            thruster.draw(offset)

            g.popTransform()
        }

        val travelTime = 4f

        // Mostly copied from AbstractProjectile
        val ship: Ship get() = target.ship
        var timeInFlight: Float = 0f
        val initialDistance: Float get() = 1000f
        val distance: Float get() = initialDistance * (1 - timeInFlight / travelTime)
    }
}
