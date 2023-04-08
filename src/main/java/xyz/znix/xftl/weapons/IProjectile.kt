package xyz.znix.xftl.weapons

import org.newdawn.slick.Graphics
import xyz.znix.xftl.math.IPoint

interface IProjectile {
    /**
     * The angle the projectile is heading in, in radians
     */
    val projectileAngle: Float

    /**
     * The position of this projectile on the screen, relative to the target ship
     */
    val position: IPoint

    /**
     * Is this projectile 'dead' and can be safely removed?
     *
     * This happens when the projectile has missed it's target and is now offscreen.
     */
    fun isDead(): Boolean

    fun update(dt: Float)

    fun render(g: Graphics, x: Float, y: Float, rotation: Float)
}
