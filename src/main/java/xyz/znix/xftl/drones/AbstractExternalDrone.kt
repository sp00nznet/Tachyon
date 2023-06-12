package xyz.znix.xftl.drones

import org.jdom2.Element
import org.newdawn.slick.Color
import xyz.znix.xftl.FTLAnimation
import xyz.znix.xftl.Ship
import xyz.znix.xftl.f
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.math.Point
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.rendering.Image
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil
import xyz.znix.xftl.weapons.DroneBlueprint
import xyz.znix.xftl.weapons.IProjectile
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
     * If true, the drone isn't automatically rotated by hacking.
     *
     * The drone must then apply [stunRotationAnimation] itself.
     */
    protected open val useCustomStunRotation: Boolean get() = false

    /**
     * The ship this drone is flying around.
     */
    lateinit var targetShip: Ship
        private set

    /**
     * The amount of time this drone has been active (powered on and
     * not stunned) for.
     *
     * This is used by the anti-drone to avoid shooting at drones
     * that have been turned on very shortly prior and will likely
     * change direction.
     */
    var timeActive: Float = 0f
        private set

    /**
     * The radius in pixels of the circular 'hitbox' around this drone.
     *
     * This is used for drone/projectile collisions.
     */
    open val hitboxRadius: Int get() = 8

    override val isStunned: Boolean get() = super.isStunned || ionStunTimer > 0f
    private var ionStunTimer: Float = 0f

    protected var stunRotationAnimation: Float = 0f
    private var stunSparksAnimation: FTLAnimation? = null
    private var stunSparksMirror: Boolean = false

    protected val game: InGameState get() = ownerShip.sys

    override fun init(ownerShip: Ship) {
        super.init(ownerShip)

        // If we're being deserialised, targetShip will already be set.
        // This is required since we don't know what the player and enemy
        // ship are during deserialisation.
        if (!this::targetShip.isInitialized) {
            targetShip = when {
                onEnemy -> ownerShip.sys.getEnemyOf(ownerShip)
                    ?: error("Cannot deploy drone '${type.name}' with no enemy present!")

                else -> ownerShip
            }
        }

        targetShip.externalDrones.add(this)

        flightController.init()

        onInit()
    }

    protected abstract fun onInit()

    override fun update(dt: Float) {
        super.update(dt)

        if (isRunning) {
            timeActive += dt
        } else {
            timeActive = 0f
        }

        ionStunTimer = max(0f, ionStunTimer - dt)

        if (isStunned) {
            stunRotationAnimation += Math.toRadians(480.0).toFloat() * dt
            stunSparksAnimation?.update(dt)
            return
        }
        stunRotationAnimation = 0f

        flightController.update(dt)
    }

    override fun destroy() {
        super.destroy()

        targetShip.animations += Ship.FloatingAnimation.centred(explodeAnimation, flightController.position)
        explodeSound.play()
    }

    override fun removeInstance() {
        super.removeInstance()

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

        var x = flightController.posX
        var y = flightController.posY

        if (flightController.pixelAligned) {
            x = x.roundToInt().f
            y = y.roundToInt().f
        }

        var rotation = flightController.rotation
        if (!useCustomStunRotation)
            rotation += stunRotationAnimation
        g.translate(x, y)
        g.rotate(0f, 0f, rotation / TWO_PI * 360f)

        onRender(g)

        g.popTransform()

        // If we're ion-stunned or being hacked, draw sparks on top
        renderStunSparks()
    }

    private fun renderStunSparks() {
        if (!isStunned) {
            stunSparksAnimation = null
            return
        }

        if (stunSparksAnimation == null || stunSparksAnimation?.isStopped == true) {
            stunSparksAnimation = game.animations["stun_spark_big"].startSingle()

            // See doc/hacking for details about this
            stunSparksMirror = Random.nextBoolean()
        }

        val sparks = stunSparksAnimation!!.currentFrame
        val pos = flightController.position

        // Use the top-right corner rather than the top-left
        // as FTL appears to, since this looks much better.
        var x1 = sparks.width - 32
        var x2 = sparks.width - 0

        if (stunSparksMirror) {
            val tmp = x1
            x1 = x2
            x2 = tmp
        }

        sparks.draw(
            pos.x - 16f, pos.y - 16f, pos.x + 16f, pos.y + 16f,
            x1.f, 0f, x2.f, 32f
        )
    }

    protected abstract fun onRender(g: Graphics)

    protected fun drawCentred(image: Image, filter: Color = Color.white) {
        // Copying FTL, use nearest filtering to avoid ugly edges due to the
        // wrong transparency colour.
        image.drawNearest(-image.width / 2.f, -image.height / 2f, filter)
    }

    open fun canCollideWith(proj: IProjectile): Boolean {
        // This probably isn't the nicest place to put it, but check
        // if we miss this projectile by chance.
        if (isPowered && Random.nextInt(10) < (type.dodge ?: 0))
            return false

        // Check if we're exempt from this projectile due to being
        // owned by the same ship.
        if (proj.antiDroneExemption == ownerShip)
            return false

        // The projectile must have a weapon blueprint to represent
        // the damage it can do to us.
        return proj.antiDroneBP != null
    }

    open fun hitProjectile(other: IProjectile) {
        // We checked this is non-null in canCollideWith.
        val type = other.antiDroneBP
        requireNotNull(type) { "Can't hit projectile without an anti-drone blueprint!" }

        // To match vanilla, check for ion damage first.
        // This means a modded weapon (there aren't any in vanilla)
        // that does both ion and hull damage will only stun
        // a drone, rather than kill it.
        // Note that ion damage doesn't affect unpowered drones
        // for whatever reason, leading to an exploit to defeat
        // enemy anti-drones (turn it off just afterr they shoot).
        if (type.ionDamage > 0 || isPowered) {
            // Five seconds per level of ion damage, same as systems.
            ionStunTimer = 5f * type.ionDamage
            return
        }

        // It looks like FTL ignores system-damage-only projectiles.
        if (type.damage > 0) {
            destroy()
            return
        }
    }

    override fun saveToXML(elem: Element, refs: ObjectRefs) {
        super.saveToXML(elem, refs)

        SaveUtil.addAttrRef(elem, "targetShip", refs, targetShip)

        val elemFC = Element("flightControl")
        flightController.saveToXML(elemFC, refs)
        elem.addContent(elemFC)
    }

    override fun loadFromXML(elem: Element, refs: RefLoader, containingShip: Ship) {
        // We're always saved as part of the target ship.
        // This is to deal with cases like an enemy drone after that enemy was destroyed.
        targetShip = containingShip

        super.loadFromXML(elem, refs, containingShip)

        // Don't deserialise the flight controller until after init() is called.
        // Thus we have to delay it until reference resolution time.
        refs.addOnResolveFunction {
            flightController.loadFromXML(elem.getChild("flightControl"), refs)
        }
    }
}

abstract class DroneFlightController(val drone: AbstractExternalDrone) {
    protected val ship: Ship get() = drone.targetShip

    /**
     * If true, the drone is always snapped to a rounded position.
     *
     * This should be used when there's no rotation, as it makes
     * the sprite sharper.
     */
    abstract val pixelAligned: Boolean

    var posX: Float = 0f
        set(value) {
            field = value
            mutablePosition.x = value.roundToInt()
        }
    var posY: Float = 0f
        set(value) {
            field = value
            mutablePosition.y = value.roundToInt()
        }
    var rotation: Float = 0f

    // Speeds in pixels per second are used by the anti-drone
    abstract val speedX: Float
    abstract val speedY: Float

    private val mutablePosition = Point(0, 0)
    val position: IPoint get() = mutablePosition

    abstract fun update(dt: Float)

    /**
     * Called when the owner drone has finished seting itself up.
     */
    open fun init() {
    }

    open fun saveToXML(elem: Element, refs: ObjectRefs) {
        SaveUtil.addAttrFloat(elem, "x", posX)
        SaveUtil.addAttrFloat(elem, "y", posY)
        SaveUtil.addAttrFloat(elem, "rotation", rotation)
    }

    open fun loadFromXML(elem: Element, refs: RefLoader) {
        posX = SaveUtil.getAttrFloat(elem, "x")
        posY = SaveUtil.getAttrFloat(elem, "y")
        rotation = SaveUtil.getAttrFloat(elem, "rotation")
    }

    companion object {
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
}

/**
 * A flight controller that constantly flies around the ship's
 * shields line. Used on defence and shield overcharger drones.
 */
class OrbitFlightController(drone: AbstractExternalDrone) : DroneFlightController(drone) {
    override val pixelAligned: Boolean get() = true

    private lateinit var shieldBounds: IPoint

    private var typeSpeed: Float = 0f

    // The angle about the origin we're so stubbonly using.
    var theta = 0f

    private val Float.sq get() = this * this

    override var speedX: Float = 0f
        private set
    override var speedY: Float = 0f
        private set

    private var poweredLastFrame = false

    override fun init() {
        super.init()

        shieldBounds = drone.targetShip.shieldHalfSize

        val shieldSemiMajor = max(shieldBounds.x, shieldBounds.y)
        typeSpeed = shieldSemiMajor * drone.type.speed!! / 21.875f

        // Initialise our position
        update(0f)
    }

    override fun update(dt: Float) {
        val wasStartedUp = !poweredLastFrame && drone.isPowered
        poweredLastFrame = drone.isPowered

        if (!drone.isPowered) {
            // Copied from CombatFlightController
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

        // When we're turned on, snap back to the closest point on the shields
        if (wasStartedUp) {
            val circleX = (posX - ship.shieldOrigin.x.f) / shieldBounds.x
            val circleY = (posY - ship.shieldOrigin.y.f) / shieldBounds.y
            theta = atan2(circleY, circleX)
        }

        updateMovement(dt)
    }

    @Suppress("LocalVariableName")
    private fun updateMovement(dt: Float) {
        // Consider the shields an ellipse where x^2/a^2 + y^2/b^2 = 1
        val a = shieldBounds.x.f
        val b = shieldBounds.y.f

        // Find the direction we're moving in.
        val tangentX = -a * sin(theta)
        val tangentY = b * cos(theta)
        val tangentLength = sqrt(tangentX.sq + tangentY.sq)

        // These are effectively dx/dL or dy/dL, where L is the
        // distance we move this step.
        val unitX = tangentX / tangentLength
        val unitY = tangentY / tangentLength

        // Use this to set our speed, which is used for
        // coasting when we're powered off.
        speedX = unitX * typeSpeed
        speedY = unitY * typeSpeed

        // Calculate our position to stay locked to the ellipse
        // while moving at a constant rate.
        // For some reason I solved this analytically, so here's
        // the equations. There's two of them, both of which
        // give you the same answer but you'll run into precision
        // errors at different points:
        // dθ/dx = -1/(a*sqrt(1 - x^2/a^2))
        // dθ/dy =  1/(b*sqrt(1 - y^2/b^2))
        // These were taken by simply rearranging and deriving
        // the circle point equations, x=a*cos(θ) and y=b*sin(θ).
        // We actually take the absolute values of these, since
        // we only want to move forwards and these assume the
        // angle is in the top-right corner.

        val x = shieldBounds.x * cos(theta)
        val y = shieldBounds.y * sin(theta)

        val dθdL: Float = if (abs(x) > abs(y)) {
            val dθdy = 1 / (b * sqrt(1 - y.sq / b.sq))
            abs(dθdy * unitY)
        } else {
            val dθdx = 1 / (a * sqrt(1 - x.sq / a.sq))
            abs(dθdx * unitX)
        }

        // Now we can finally update our angle.
        // For completeness, typeSpeed is effectively dL/dt, since
        // it's the movement per unit time.
        theta += dθdL * typeSpeed * dt

        theta = theta.rem(TWO_PI)

        rotation = 0f
        posX = ship.shieldOrigin.x.f + (a * cos(theta))
        posY = ship.shieldOrigin.y.f + (b * sin(theta))
    }

    override fun saveToXML(elem: Element, refs: ObjectRefs) {
        super.saveToXML(elem, refs)

        SaveUtil.addAttrFloat(elem, "angleAboutShip", theta)
        SaveUtil.addAttrFloat(elem, "speedX", speedX)
        SaveUtil.addAttrFloat(elem, "speedY", speedY)
        SaveUtil.addAttrBool(elem, "poweredLastFrame", poweredLastFrame)
    }

    override fun loadFromXML(elem: Element, refs: RefLoader) {
        super.loadFromXML(elem, refs)

        theta = SaveUtil.getAttrFloat(elem, "angleAboutShip")
        speedX = SaveUtil.getAttrFloat(elem, "speedX")
        speedY = SaveUtil.getAttrFloat(elem, "speedY")
        poweredLastFrame = SaveUtil.getAttrBool(elem, "poweredLastFrame")
    }
}

/**
 * A flight controller that flies to different parts
 * of the enemy ship, and lines behind (but pointing towards)
 * the shield.
 */
class CombatFlightController(drone: AbstractExternalDrone) : DroneFlightController(drone) {
    override val pixelAligned: Boolean get() = false

    private var nextDestination: IPoint = ConstPoint.ZERO
    private var currentDestAngle: Float = 0f // In radians

    // We'll use this quite a bit
    private lateinit var shieldSize: IPoint

    // The speed in pixels per second
    private var typeSpeed: Float = 0f

    // The drone's current speed
    override var speedX: Float = 0f
        private set
    override var speedY: Float = 0f
        private set

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

    override fun init() {
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
        movementDistance = sqrt(nextDestination.distToSq(position).f)

        // Pick the new rotation, and save the old one so we can
        // interpolate between them.
        lastDestRotation = nextDestRotation
        nextDestRotation = getAngleFrom(nextDestination, nextStopTarget)
    }

    override fun saveToXML(elem: Element, refs: ObjectRefs) {
        super.saveToXML(elem, refs)

        SaveUtil.addPoint(elem, "nextDestination", nextDestination)
        SaveUtil.addPoint(elem, "nextStopTarget", nextStopTarget)
        SaveUtil.addAttrFloat(elem, "currentDestAngle", currentDestAngle)
        SaveUtil.addAttrFloat(elem, "speedX", speedX)
        SaveUtil.addAttrFloat(elem, "speedY", speedY)
        SaveUtil.addAttrFloat(elem, "pauseTimer", pauseTimer)
        SaveUtil.addAttrFloat(elem, "lastDestRotation", lastDestRotation)
        SaveUtil.addAttrFloat(elem, "nextDestRotation", nextDestRotation)
        SaveUtil.addAttrFloat(elem, "movementDistance", movementDistance)
        SaveUtil.addAttrBool(elem, "paused", paused)
        SaveUtil.addAttrFloat(elem, "pauseStopTime", pauseStopTime)
    }

    override fun loadFromXML(elem: Element, refs: RefLoader) {
        super.loadFromXML(elem, refs)

        nextDestination = SaveUtil.getPoint(elem, "nextDestination")
        nextStopTarget = SaveUtil.getPoint(elem, "nextStopTarget")
        currentDestAngle = SaveUtil.getAttrFloat(elem, "currentDestAngle")
        speedX = SaveUtil.getAttrFloat(elem, "speedX")
        speedY = SaveUtil.getAttrFloat(elem, "speedY")
        pauseTimer = SaveUtil.getAttrFloat(elem, "pauseTimer")
        lastDestRotation = SaveUtil.getAttrFloat(elem, "lastDestRotation")
        nextDestRotation = SaveUtil.getAttrFloat(elem, "nextDestRotation")
        movementDistance = SaveUtil.getAttrFloat(elem, "movementDistance")
        paused = SaveUtil.getAttrBool(elem, "paused")
        pauseStopTime = SaveUtil.getAttrFloat(elem, "pauseStopTime")
    }
}

private const val TWO_PI = 2 * PI.toFloat()
