package xyz.znix.xftl.weapons

import org.jdom2.Element
import org.newdawn.slick.Graphics
import xyz.znix.xftl.Ship
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.systems.Hacking

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
     * Does this projectile count as a missile to a defence drone?
     */
    val isMissileForDD: Boolean get() = false

    /**
     * Does this projectile count as a laser to a defence drone?
     */
    val isLaserForDD: Boolean get() = false

    /**
     * If true, collisions are enabled for this projectile.
     */
    val collisionsEnabled: Boolean get() = true

    /**
     * The radius of this projectile's circular hitbox.
     *
     * This is used for checking for collisions between projectiles
     * and other projectiles or drones.
     */
    val hitboxRadius: Int get() = 3

    /**
     * If this projectile can collide with drones, this is the blueprint
     * representing the weapon it applies the effect of.
     *
     * Null means it can't collide with drones.
     */
    val antiDroneBP: AbstractWeaponBlueprint?

    /**
     * If this projectile can collide with drones, this is the ship
     * whose drones are exempt from collisions.
     *
     * This is used to avoid ships shooting down their own drones.
     *
     * Null means it will collide with all drones.
     */
    val antiDroneExemption: Ship?

    /**
     * The ID used to describe what object must deserialise this projectile
     * once it's saved to XML.
     */
    val serialisationType: String

    /**
     * Called to update this projectile.
     *
     * [currentSpace] is the ship in whose space this projectile
     * is currently residing.
     */
    fun update(dt: Float, currentSpace: Ship)

    fun render(g: Graphics, currentSpace: Ship)

    /**
     * Called when this projectile hit another projectile, immediately
     * before it's destroyed.
     */
    fun hitOtherProjectile(currentSpace: Ship)

    /**
     * Serialise this projectile to XML.
     */
    fun saveToXML(elem: Element, refs: ObjectRefs)

    companion object {
        fun loadFromXML(
            game: InGameState,
            elem: Element, refs: RefLoader,
            serialisationType: String,
            callback: ProjectileLoadCallback
        ) {
            when (serialisationType) {
                AbstractWeaponProjectile.SERIALISATION_TYPE -> AbstractWeaponProjectile.loadFromXML(
                    game, elem, refs, callback
                )

                BombBlueprint.SERIALISATION_TYPE -> BombBlueprint.loadProjectileFromXML(game, elem, refs, callback)

                Hacking.PROBE_SERIALISATION_TYPE -> Hacking.loadProjectileFromXML(game, elem, refs, callback)

                else -> {
                    error("Invalid serialised projectile with serialisation type '$serialisationType'")
                }
            }
        }
    }
}

typealias ProjectileLoadCallback = (IProjectile) -> Unit
