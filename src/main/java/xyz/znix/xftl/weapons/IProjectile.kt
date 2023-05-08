package xyz.znix.xftl.weapons

import org.newdawn.slick.Graphics
import xyz.znix.xftl.Ship
import xyz.znix.xftl.math.IPoint

interface IProjectile {
    /**
     * The position of this projectile on the screen, relative
     * to the ship in whose projectiles list it's in.
     */
    val position: IPoint

    /**
     * If true, this projectile should be drawn underneath the
     * ship whose space it occupies.
     *
     * This is intended for departing shots.
     */
    val drawUnderShip: Boolean get() = false

    /**
     * Called to update this projectile.
     *
     * [currentSpace] is the ship in whose space this projectile
     * is currently residing.
     */
    fun update(dt: Float, currentSpace: Ship)

    fun render(g: Graphics)
}
