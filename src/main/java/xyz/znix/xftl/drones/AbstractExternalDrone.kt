package xyz.znix.xftl.drones

import org.newdawn.slick.Graphics
import xyz.znix.xftl.Ship
import xyz.znix.xftl.f
import xyz.znix.xftl.game.SlickGame
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.weapons.DroneBlueprint
import kotlin.math.*
import kotlin.random.Random

abstract class AbstractExternalDrone(
    type: DroneBlueprint,

    /**
     * True if this drone should fly around the enemy ship, rather than
     * it's owner's ship.
     */
    val onEnemy: Boolean

) : AbstractDrone(type) {

    /**
     * The [DroneFlightController] that defines this drone's flight path.
     *
     * This is used to orbit the shield bubble (like defence drones) or
     * pick positions around but behind it (like combat drones).
     */
    abstract val flightController: DroneFlightController

    /**
     * The ship this drone is flying around.
     */
    lateinit var targetShip: Ship
        private set

    protected val game: SlickGame get() = ownerShip.sys

    override fun init(ownerShip: Ship) {
        super.init(ownerShip)

        targetShip = when {
            onEnemy -> ownerShip.sys.getEnemyOf(ownerShip)
                ?: error("Cannot deploy drone '${type.name}' with no enemy present!")

            else -> ownerShip
        }

        targetShip.externalDrones.add(this)

        flightController.init(this)

        onInit()
    }

    protected abstract fun onInit()

    override fun update(dt: Float) {
        super.update(dt)

        flightController.update(dt)
    }

    override fun destroy() {
        super.destroy()

        targetShip.externalDrones.remove(this)
    }

    override fun onEnemyShipUpdated() {
        super.onEnemyShipUpdated()

        if (!onEnemy)
            return

        // If we're deployed to an enemy ship, check if it's gone.
        if (ownerShip.sys.getEnemyOf(ownerShip) == targetShip)
            return

        removeInstance()
        targetShip.externalDrones.remove(this)
    }

    /**
     * Draw this ship onto the target.
     *
     * This is called by the ship this drone is flying around.
     */
    open fun renderExternal(g: Graphics) {
        g.pushTransform()
        g.translate(flightController.posX, flightController.posY)
        g.rotate(0f, 0f, flightController.rotation / TWO_PI * 360f)

        onRender(g)

        g.popTransform()
    }

    protected abstract fun onRender(g: Graphics)
}

abstract class DroneFlightController {
    protected lateinit var drone: AbstractExternalDrone
        private set

    protected val ship: Ship get() = drone.targetShip

    var posX: Float = 0f
    var posY: Float = 0f
    var rotation: Float = 0f

    fun init(drone: AbstractExternalDrone) {
        if (this::drone.isInitialized) {
            throw IllegalStateException("Cannot re-initialise drone flight controller!")
        }

        this.drone = drone

        setup()
    }

    abstract fun update(dt: Float)

    protected abstract fun setup()
}

/**
 * A flight controller that flies to different parts
 * of the enemy ship, and lines behind (but pointing towards)
 * the shield.
 */
class CombatFlightController : DroneFlightController() {
    private var nextDestination: IPoint = ConstPoint.ZERO
    private var currentDestAngle: Float = 0f // In radians

    // We'll use this quite a bit
    private lateinit var shieldSize: IPoint

    // The speed in pixels per second
    private var typeSpeed: Float = 0f

    // The drone's current speed
    private var speedX: Float = 0f
    private var speedY: Float = 0f

    // Times how long it's been since we started slowing down to fire
    private var pauseTimer: Float = 0f

    // The angle the drone needs to point in at the last and next
    // destination, which we then interpolate between.
    private var lastDestRotation: Float = 0f
    private var nextDestRotation: Float = 0f

    // The distance between where we were when we selected our next
    // destination, and the next destination. This can be used to
    // interpolate the drone's rotation.
    private var movementDistance: Float = 1f

    /**
     * Set to true when the drone should slow down and stop, ready
     * to take a shot.
     */
    var paused: Boolean = false

    /**
     * How long it should take the drone to stop once paused.
     */
    var pauseStopTime: Float = 0f

    /**
     * A callback that's run when the drone reaches a destination point.
     */
    var onReachedDestination: (() -> Unit)? = null

    /**
     * The point the drone will face when it next stops.
     */
    var nextStopTarget: IPoint = ConstPoint.ZERO

    override fun setup() {
        shieldSize = ship.shieldHalfSize

        // See doc/combat-drone for the speed information.
        val shieldSemiMajor = max(shieldSize.x, shieldSize.y)
        typeSpeed = shieldSemiMajor * drone.type.speed!! / 21.875f

        // Face in the direction of the centre of the ship, since
        // we don't know our actual target yet.
        nextStopTarget = ship.shieldOrigin

        // Pick a fully random initial destination, and jump to it
        currentDestAngle = Random.nextFloat() * TWO_PI
        pickNextDestination()

        posX = nextDestination.x.f
        posY = nextDestination.y.f

        // Pick another destination to fly to, as the first 'real' point.
        // This will avoid us firing a shot on the first update.
        pickNextDestination()
    }

    override fun update(dt: Float) {
        if (!drone.isPowered) {
            posX += speedX * dt
            posY += speedY * dt

            val slowdownFactor = 0.1f * dt * 16
            speedX *= 1 - slowdownFactor
            speedY *= 1 - slowdownFactor

            val totalSpeed = sqrt(speedX * speedX + speedY * speedY)
            if (totalSpeed < 16) {
                speedX = 0f
                speedY = 0f
            }

            return
        }

        if (paused) {
            pauseTimer += dt

            // If we're still slowing down, apply that
            val remaining = pauseStopTime - pauseTimer
            if (remaining > 0f) {
                val newSpeedMult = remaining / 1.5f

                posX += speedX * dt * newSpeedMult
                posY += speedY * dt * newSpeedMult
            }

            return
        }
        pauseTimer = 0f

        val inPosition = updateMovement(dt)
        if (inPosition) {
            onReachedDestination?.let { it() }

            pickNextDestination()
        }
    }

    // Moves, and returns true if we're in position.
    private fun updateMovement(dt: Float): Boolean {
        val deltaX = nextDestination.x - posX
        val deltaY = nextDestination.y - posY
        val distanceToDest = sqrt(deltaX * deltaX + deltaY * deltaY)

        // Update our rotation, to face towards the new target.
        val finalRotation = getAngleFrom(nextDestination, nextStopTarget)

        // If we'd move past the target on this update, snap to it.
        if (distanceToDest <= dt * typeSpeed) {
            posX = nextDestination.x.f
            posY = nextDestination.y.f
            rotation = finalRotation
            return true
        }

        // Figure out the direction we have to rotate in, since we might
        // need to wrap around the 2pi/0 transition.
        val deltaRotation: Float = when {
            // No wrap-around
            abs(finalRotation - rotation) < PI -> finalRotation - rotation

            // Wrap-around, rotating in the positive direction
            finalRotation < rotation -> finalRotation + (TWO_PI - rotation)

            // Wrap-around, rotating in the negative direction
            else -> -(rotation + (TWO_PI - finalRotation))
        }

        // Figure out the rate of rotation required to get a smooth
        // rotation over the whole movement.
        val rotationRate = deltaRotation / distanceToDest

        // Apply an amount of rotation consistent with that, based
        // on the distance we're about to travel.
        rotation += rotationRate * (typeSpeed * dt)

        // Update our position to move closer towards the target
        val unitX = deltaX / distanceToDest
        val unitY = deltaY / distanceToDest

        speedX = unitX * typeSpeed
        speedY = unitY * typeSpeed

        posX += speedX * dt
        posY += speedY * dt

        return false
    }

    private fun pickNextDestination() {
        // Pick an angle at least 90° away from our current one.
        // See doc/combat-drone.
        var newAngle: Float
        do {
            newAngle = Random.nextFloat() * TWO_PI

            // Calculate the difference in angle, accounting for wrap-around by adding 2pi to the current angle.
            val difference = min(
                abs(newAngle - currentDestAngle),
                abs(newAngle - (currentDestAngle + TWO_PI))
            )
        } while (difference < PI.toFloat() / 2)

        // Save the new angle for next time
        currentDestAngle = newAngle

        // Calculate the new destination, relative to the centre of the shields
        val baseX = shieldSize.x * cos(newAngle) * 1.15
        val baseY = shieldSize.y * sin(newAngle) * 1.15

        nextDestination = ConstPoint(
            ship.shieldOrigin.x + baseX.toInt(),
            ship.shieldOrigin.y + baseY.toInt()
        )

        // Calculate the distance between this point and the next one.
        // This is used to figure out how far along our flight we are,
        // to correctly set the rotation.
        movementDistance = sqrt(nextDestination.distToSq(ConstPoint(posX.toInt(), posY.toInt())).f)

        // Pick the new rotation, and save the old one so we can
        // interpolate between them.
        lastDestRotation = nextDestRotation
        nextDestRotation = getAngleFrom(nextDestination, nextStopTarget)
    }

    /**
     * Get the angle the drone needs to face in to point at a given
     * location when it's floating at the set coordinates.
     */
    fun getAngleFrom(at: IPoint, to: IPoint): Float {
        val base = atan2(to.y.f - at.y.f, to.x.f - at.x.f)

        // With atan2 we get an angle where zero means 'to the right'.
        // However our drones are facing up in the images, so we need
        // to rotate 90° to correct for that.
        var corrected = base + TWO_PI / 4

        // Fix the value up to be in the 0..2pi range.
        if (corrected < 0)
            corrected += TWO_PI
        if (corrected > TWO_PI)
            corrected -= TWO_PI

        return corrected
    }
}

private const val TWO_PI = 2 * PI.toFloat()
