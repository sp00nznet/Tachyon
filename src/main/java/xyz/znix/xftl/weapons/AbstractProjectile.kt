package xyz.znix.xftl.weapons

import org.newdawn.slick.Graphics
import xyz.znix.xftl.Constants.ROOM_SIZE
import xyz.znix.xftl.Ship
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint

abstract class AbstractProjectile(val target: Room, val speed: Float) {
    // The angle we are approaching the target at, in radians
    val angle: Float = (Math.random() * Math.PI * 2).toFloat()

    // The angle the projectile is heading in, in radians
    val projectileAngle: Float
        get() {
            val shift = angle - Math.PI
            return (if (shift < 0) shift + Math.PI * 2 else shift).toFloat()
        }

    val ship: Ship get() = target.ship

    var distance: Float = 100f

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

        if (distance <= 0) {
            distance = 0f

            // TODO shields
            // TODO missing (dodge chance, etc)

            val system = target.system
            if (system != null) {
                if (system.damagedEnergyLevels < system.energyLevels)
                    system.damagedEnergyLevels++
            }

            ship.inboundProjectiles.remove(this)
        }
    }

    abstract fun render(g: Graphics, x: Float, y: Float, rotation: Float)
}
