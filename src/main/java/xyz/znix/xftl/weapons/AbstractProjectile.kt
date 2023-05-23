package xyz.znix.xftl.weapons

import org.newdawn.slick.Color
import org.newdawn.slick.Graphics
import xyz.znix.xftl.Ship
import xyz.znix.xftl.f
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.math.Point
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

abstract class AbstractProjectile(
    /**
     * If non-null, the ship this projectile is aimed at.
     *
     * If this is set and the projectile leaves one ship's
     * space, it jumps to this ship's space.
     */
    val targetShip: Ship?
) : IProjectile {

    /**
     * The projectile's speed, in pixels per second.
     */
    abstract val speed: Int

    // The angle we are approaching the target at, in radians.
    // This is only used if and when we exit the space of the
    // ship that fired us, and enter the target's space.
    var entryAngle: Float = (Math.random() * Math.PI * 2).toFloat()

    private var hasReachedTarget: Boolean = false
    private var hasPassedShields: Boolean = false

    /**
     * If true, this projectile will be removed on the next update.
     */
    protected var dead: Boolean = false

    // Helper for maths
    val Float.squared get() = this * this

    // The position of this projectile, whether it's inside
    // the player or enemy ship space.
    private var posX: Float = 0f
        set(value) {
            field = value
            mutablePosition.x = posX.toInt()
        }
    private var posY: Float = 0f
        set(value) {
            field = value
            mutablePosition.y = posY.toInt()
        }

    // The position this projectile is flying towards.
    var targetPos: IPoint = ConstPoint.ZERO

    // The rotation of this projectile.
    // In radians, where 0 is pointing right.
    // Note this can't be trivially computed from the current
    // and target positions - if we miss, for example, that
    // would point in the wrong direction.
    var rotation: Float = 0f
        private set

    // The position point. This isn't authoritative, and is
    // updated from the floating-point x,y values.
    private val mutablePosition = Point(0, 0)

    override val position: IPoint get() = mutablePosition

    override fun update(dt: Float, currentSpace: Ship) {
        if (dead) {
            currentSpace.projectiles.remove(this)
            return
        }

        val isDeparting = targetShip != null && targetShip != currentSpace

        // If we're in the target ship's space, check if we're now inside it's shields.
        if (!hasPassedShields && !isDeparting) {
            val rel = Point(position)
            rel -= currentSpace.shieldOrigin

            val shieldSize = currentSpace.shieldHalfSize

            if (rel.x.f.squared / shieldSize.x.f.squared + rel.y.f.squared / shieldSize.y.f.squared < 1) {
                hasPassedShields = true
                crossedShieldLine()
            }
        }

        val hitRoom = updateMovement(dt)

        if (hitRoom) {
            reachedTarget()
        }

        // Check if we're out-of-bounds. If so, either switch to the target
        // ship (if we're a departing projectile), or destroy ourselves.
        // See doc/projectiles
        if (posX < -800 || posX > 800 || posY < -800 || posY > 800) {
            currentSpace.projectiles.remove(this)

            // Are we a departing projectile? If so, switch ourselves to
            // the ship we're aimed at.
            if (targetShip != null && currentSpace != targetShip) {
                switchToTarget()
            }
        }
    }

    override fun render(g: Graphics, currentSpace: Ship) {
        g.pushTransform()
        g.translate(position.x.f, position.y.f)
        g.rotate(0f, 0f, Math.toDegrees(rotation.toDouble()).toFloat())

        renderPreTranslated(g)

        if (currentSpace.sys.debugFlags.showProjectileHitboxes.set) {
            val r = hitboxRadius.f
            g.color = Color.red
            g.drawOval(-r, -r, r * 2, r * 2)
        }

        g.popTransform()
    }

    protected abstract fun renderPreTranslated(g: Graphics)

    /**
     * Update the position, and return true if we hit
     * the target room (this isn't set if missed=true).
     */
    private fun updateMovement(dt: Float): Boolean {
        val movementThisFrame = speed * dt

        // Update our position. If we've missed, use the rotation to
        // find the correct path. Otherwise use the difference in
        // position to make sure we're always properly aligned.
        if (hasReachedTarget) {
            posX += cos(rotation) * movementThisFrame
            posY += sin(rotation) * movementThisFrame
            return false
        }

        val deltaX = targetPos.x - posX
        val deltaY = targetPos.y - posY

        val distance = sqrt(deltaX * deltaX + deltaY * deltaY)

        // Would we overshoot the target position?
        if (distance < movementThisFrame) {
            posX = targetPos.x.f
            posY = targetPos.y.f
            hasReachedTarget = true
            return true
        }

        // Find the unit-vector of the direction we have to travel in.
        val unitX = deltaX / distance
        val unitY = deltaY / distance

        posX += unitX * movementThisFrame
        posY += unitY * movementThisFrame

        return false
    }

    /**
     * Called once when the projectile reaches the target room.
     */
    protected abstract fun reachedTarget()

    /**
     * Called once when the projectile crosses the enemy shields,
     * regardless of whether they're up (or even turned on) or not.
     */
    protected open fun crossedShieldLine() {
    }

    /**
     * Called when this projectile exits the space of one ship
     * and enters that of its target.
     */
    protected open fun onSwitchedToTarget() {
    }

    /**
     * Calculate the position on the enemy ship this projectile
     * should fly towards.
     *
     * This is called after the projectile has swapped from
     * the shooter's ship to the target's ship.
     */
    open fun calculateTargetPosition(): IPoint {
        error("Cannot cross ship spaces without implementing calculateTargetPosition")
    }

    private fun switchToTarget() {
        targetShip!!.projectiles.add(this)

        // Jump into the outside of the enemy ship space.
        // See doc/projectiles for this logic.

        val radius = (800 * 0.75).toInt()

        posX = 200 + cos(entryAngle) * radius
        posY = 200 + sin(entryAngle) * radius

        targetPos = calculateTargetPosition()

        setRotationFromTarget()

        onSwitchedToTarget()
    }

    fun setInitialPath(initialPos: IPoint, targetPos: IPoint) {
        posX = initialPos.x.f
        posY = initialPos.y.f

        this.targetPos = targetPos

        setRotationFromTarget()
    }

    private fun setRotationFromTarget() {
        rotation = atan2(targetPos.y - posY, targetPos.x - posX)
    }
}

abstract class AbstractWeaponProjectile(val type: AbstractWeaponBlueprint, val target: Room) :
    AbstractProjectile(target.ship) {

    /**
     * The default speed for this projectile, if it's not set in the blueprint.
     *
     * Note this speed is given in pixels-per-second, divided by 16.
     * We have to multiply it by 16, since it's based on the SpeedFactor thing.
     * See doc/reveng-general.md for notes on this.
     */
    abstract val defaultSpeed: Int

    @Suppress("LeakingThis")
    override val speed = (type.speed ?: defaultSpeed) * 16

    // When we're leaving the ship that fired this weapon, draw underneath it.
    override var drawUnderShip: Boolean = true

    override val antiDroneBP: AbstractWeaponBlueprint get() = type
    override val antiDroneExemption: Ship? get() = ship.sys.getEnemyOf(ship)

    private val defaultMissSound = target.ship.sys.sounds.getSample("miss")

    private var missed: Boolean? = null

    // Used by defence drones.
    var firedByDrone: Boolean = false

    val ship: Ship get() = target.ship

    override fun reachedTarget() {
        resolveMissed()

        if (missed == true)
            return

        hitHull()

        dead = true
    }

    override fun crossedShieldLine() {
        // We're inside the shield!

        val activeShields = ship.shields?.activeShields ?: 0
        if (activeShields == 0)
            return

        resolveMissed()

        // Check for shield piercing, which seems to work the same
        // way across all weapons. Missiles for example just have
        // a very high shieldPiercing of 5.
        // This doesn't apply when the player has a super-shield
        // active, which blocks everything.
        if (type.shieldPiercing >= activeShields && ship.superShield == 0)
            return

        hitShields()

        dead = true
    }

    override fun onSwitchedToTarget() {
        super.onSwitchedToTarget()

        // Now we're in the target ship's space, draw on top of the ship.
        drawUnderShip = false
    }

    override fun calculateTargetPosition(): IPoint {
        // Aim for the centre of the target room.
        return ConstPoint(
            target.offsetX + target.pixelWidth / 2,
            target.offsetY + target.pixelHeight / 2
        )
    }

    protected open fun hitShields() {
        if (type.ionDamage > 0 && ship.superShield == 0) {
            ship.shields!!.dealDamage(0, type.ionDamage)
        } else {
            ship.shields!!.popShieldLayer(type)
        }

        ship.playDamageEffect(type, position)
        type.hitShieldSounds?.get()?.play()
    }

    override fun hitOtherProjectile(currentSpace: Ship) {
        currentSpace.playDamageEffect(type, position)
    }

    protected open fun hitHull() {
        ship.damage(target, type)
    }

    private fun resolveMissed() {
        if (missed == null)
            return

        missed = Math.random() * 100 < target.ship.evasion

        if (missed == true) {
            val missSound = type.missSounds?.get() ?: defaultMissSound
            missSound.play()
            return
        }
    }
}
