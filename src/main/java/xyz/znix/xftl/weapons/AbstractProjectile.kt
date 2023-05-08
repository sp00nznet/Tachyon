package xyz.znix.xftl.weapons

import xyz.znix.xftl.Constants.ROOM_SIZE
import xyz.znix.xftl.Ship
import xyz.znix.xftl.f
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.math.Point
import kotlin.math.cos
import kotlin.math.sin

abstract class AbstractProjectile(val target: Room, val travelTime: Float) : IProjectile {

    // The angle we are approaching the target at, in radians
    var angle: Float = (Math.random() * Math.PI * 2).toFloat()

    // The angle the projectile is heading in, in radians
    override val projectileAngle: Float
        get() {
            val shift = angle - Math.PI
            return (if (shift < 0) shift + Math.PI * 2 else shift).toFloat()
        }

    val ship: Ship get() = target.ship

    var timeInFlight: Float = 0f

    val distance: Float get() = 1000f * (1 - timeInFlight / travelTime)

    private var hasReachedTarget: Boolean = false
    private var hasPassedShields: Boolean = false

    // Helper for maths
    val Float.squared get() = this * this

    private val mutablePosition = Point(0, 0)

    override val position: IPoint get() = mutablePosition

    override fun update(dt: Float) {
        timeInFlight += dt

        calculatePositionFor(distance, mutablePosition)

        if (!hasPassedShields) {
            // Check if we're inside the target ships shields
            val s = ship

            val rel = Point(position)
            rel -= s.shieldOrigin

            val shieldSize = s.shieldHalfSize

            if (rel.x.f.squared / shieldSize.x.f.squared + rel.y.f.squared / shieldSize.y.f.squared < 1) {
                hasPassedShields = true
                crossedShieldLine()
            }
        }

        if (distance <= 0 && !hasReachedTarget) {
            hasReachedTarget = true
            reachedTarget()
        }
    }

    /**
     * Is this projectile 'dead' and can be safely removed?
     */
    override fun isDead(): Boolean {
        // If we missed and ran off the screen, get removed
        // TODO use the actual screen size for this - for now just assume that 10 000 px is when the user won't see them
        if (distance < -5_000) return true

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

    protected open fun calculatePositionFor(dist: Float, output: Point) {
        val offX = cos(angle.toDouble()) * dist
        val offY = sin(angle.toDouble()) * dist
        output.x = offX.toInt() + target.offsetX + target.width * ROOM_SIZE / 2
        output.y = offY.toInt() + target.offsetY + target.height * ROOM_SIZE / 2
    }
}

abstract class AbstractWeaponProjectile(val type: AbstractWeaponBlueprint, target: Room, travelTime: Float) :
    AbstractProjectile(target, travelTime) {

    private val defaultMissSound = target.ship.sys.sounds.getSample("miss")

    private var missed: Boolean? = null

    override fun reachedTarget() {
        resolveMissed()

        if (missed == true)
            return

        hitHull()

        ship.inboundProjectiles.remove(this)
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
        if (type.shieldPiercing >= activeShields)
            return

        hitShields()

        ship.inboundProjectiles.remove(this)
    }

    protected open fun hitShields() {
        if (type.ionDamage > 0) {
            ship.shields!!.dealDamage(0, type.ionDamage)
        } else {
            ship.shields!!.popShieldLayer()
        }

        ship.playDamageEffect(type, position)
        type.hitShieldSounds?.get()?.play()
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
