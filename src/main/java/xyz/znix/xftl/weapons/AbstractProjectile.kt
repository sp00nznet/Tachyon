package xyz.znix.xftl.weapons

import org.newdawn.slick.Graphics
import xyz.znix.xftl.Constants.ROOM_SIZE
import xyz.znix.xftl.Ship
import xyz.znix.xftl.f
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.math.Point
import kotlin.math.cos
import kotlin.math.sin

abstract class AbstractProjectile(val type: AbstractWeaponBlueprint, val target: Room, val travelTime: Float) {
    // The angle we are approaching the target at, in radians
    var angle: Float = (Math.random() * Math.PI * 2).toFloat()

    // The angle the projectile is heading in, in radians
    val projectileAngle: Float
        get() {
            val shift = angle - Math.PI
            return (if (shift < 0) shift + Math.PI * 2 else shift).toFloat()
        }

    val ship: Ship get() = target.ship

    var timeInFlight: Float = 0f

    val distance: Float get() = 1000f * (1 - timeInFlight / travelTime)

    private var missed: Boolean? = null

    private var passedShields: Boolean = false

    // Helper for maths
    val Float.squared get() = this * this

    private val mutablePosition = Point(0, 0)

    /**
     * The position of this projectile on the screen, relative to the target ship
     */
    val position: IPoint get() = mutablePosition

    fun update(dt: Float) {
        timeInFlight += dt

        calculatePositionFor(distance, mutablePosition)

        if (!passedShields && !type.shieldPiercing) {
            // Check if we're inside the target ships shields
            val s = ship

            val shieldOrigin = Point(s.hullImage.width, s.hullImage.height)
            shieldOrigin.divide(2)
            if (s.isPlayerShip)
                shieldOrigin += s.shieldOffset

            val rel = Point(position)
            rel -= shieldOrigin

            val shieldSize = s.shieldHalfSize

            if (rel.x.f.squared / shieldSize.x.f.squared + rel.y.f.squared / shieldSize.y.f.squared < 1)
                crossedShieldLine()
        }

        if (distance <= 0) {
            resolveMissed()

            if (missed == true)
                return

            // TODO shields

            ship.damage(target, type)

            ship.inboundProjectiles.remove(this)
        }
    }

    /**
     * Is this projectile 'dead' and can be safely removed?
     */
    fun isDead(): Boolean {
        // If we missed and ran off the screen, get removed
        // TODO use the actual screen size for this - for now just assume that 10 000 px is when the user won't see them
        if (distance < -5_000) return true

        return false
    }

    private fun resolveMissed() {
        missed = missed ?: (Math.random() * 100 < target.ship.evasion)
    }

    private fun crossedShieldLine() {
        // We're inside the shield!
        passedShields = true

        val shields = ship.shields

        if (shields == null || shields.activeShields == 0)
            return

        resolveMissed()

        if (missed == true)
            return

        shields.activeShields--

        ship.inboundProjectiles.remove(this)
        ship.playDamageEffect(type, position)
    }

    abstract fun render(g: Graphics, x: Float, y: Float, rotation: Float)

    private fun calculatePositionFor(dist: Float, output: Point) {
        val offX = cos(angle.toDouble()) * dist
        val offY = sin(angle.toDouble()) * dist
        output.x = offX.toInt() + target.offsetX + target.width * ROOM_SIZE / 2
        output.y = offY.toInt() + target.offsetY + target.height * ROOM_SIZE / 2
    }
}
