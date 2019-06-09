package xyz.znix.xftl.weapons

import org.newdawn.slick.Graphics
import xyz.znix.xftl.Constants.ROOM_SIZE
import xyz.znix.xftl.Ship
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.math.Point

abstract class AbstractProjectile(val type: AbstractWeaponBlueprint, val target: Room, val speed: Float) {
    // The angle we are approaching the target at, in radians
    var angle: Float = (Math.random() * Math.PI * 2).toFloat()

    // The angle the projectile is heading in, in radians
    val projectileAngle: Float
        get() {
            val shift = angle - Math.PI
            return (if (shift < 0) shift + Math.PI * 2 else shift).toFloat()
        }

    val ship: Ship get() = target.ship

    var distance: Float = 1500f

    private var missed: Boolean? = null

    private var passedShields: Boolean = false

    // Helper for maths
    val Int.f get() = toFloat()
    val Float.squared get() = this * this

    /**
     * The position of this projectile on the screen, relative to the target ship
     */
    val position: IPoint
        get() {
            val offX = Math.cos(angle.toDouble()) * distance
            val offY = Math.sin(angle.toDouble()) * distance
            return ConstPoint(
                    offX.toInt() + target.offsetX + target.width * ROOM_SIZE / 2,
                    offY.toInt() + target.offsetY + target.height * ROOM_SIZE / 2
            )
        }

    fun update(dt: Float) {
        distance -= speed * dt

        if (!passedShields) {
            // Check if we're inside the target ships shields
            val s = ship
            val p = position

            val shieldOrigin = Point(s.hullImage.width, s.hullImage.height)
            shieldOrigin.divide(2)
            if (s.isPlayerShip)
                shieldOrigin += s.shieldOffset

            val rel = Point(p)
            rel -= shieldOrigin

            val shieldSize = s.shieldHalfSize

            if (rel.x.f.squared / shieldSize.x.f.squared + rel.y.f.squared / shieldSize.y.f.squared < 1)
                crossedShieldLine()
        }

        if (distance <= 0) {
            if (missed == true)
                return

            resolveMissed()

            distance = 0f

            // TODO shields

            target.system?.dealDamage(type.sysDamage)

            ship.inboundProjectiles.remove(this)

            renderHit()
        }
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
    }

    protected open fun renderHit() {
        val animation = ship.sys.animations[type.explosion ?: throw IllegalStateException("Default explosion not set")]
        ship.animations += Ship.FloatingAnimation.centered(animation.start(), position)
    }

    abstract fun render(g: Graphics, x: Float, y: Float, rotation: Float)
}
