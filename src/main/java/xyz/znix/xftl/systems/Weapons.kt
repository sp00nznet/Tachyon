package xyz.znix.xftl.systems

import org.jdom2.Element
import org.newdawn.slick.Graphics
import xyz.znix.xftl.Ship
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.weapons.AbstractProjectile

class Weapons(elem: Element) : MainSystem("weapons", elem) {
    override val sortingType: SortingType get() = SortingType.WEAPONS

    override fun update(dt: Float) {
        for (hp in ship.hardpoints)
            hp.weapon?.update(dt)
    }

    private val departing: MutableList<DepartingShot> = ArrayList()

    override fun drawBackground(g: Graphics) {
        // Draw the departing projectiles
        for (shot in departing) {
            val dist = shot.initialDistance - shot.projectile.distance
            g.pushTransform()
            translateHardpoint(g, shot.hardpoint)
            g.translate(shot.offset.x.toFloat(), shot.offset.y.toFloat())
            shot.projectile.render(g, 0f, -dist, -90f)
            g.popTransform()
        }

        // Discard these once they hit or miss their target. We could certainly
        // remove them sooner, but there is little reason to.
        departing.removeIf { p -> !p.projectile.target.ship.inboundProjectiles.contains(p.projectile) }

        for (hp in ship.hardpoints) {
            val blueprint = hp.weapon ?: continue

            val weaponAnimations = ship.sys.animations.weaponAnimations

            g.pushTransform()

            run {
                val anim = blueprint.animation

                // We start in ship space - x and y are relative to the hull

                translateHardpoint(g, hp)

                // We are now in image space - any given XY value here will line up to the pixel
                // with the same XY on the launcher image.

                g.translate(-anim.mountPoint.x.toFloat(), -anim.mountPoint.y.toFloat())

                // TODO how much are the weapons retracted by?

                blueprint.render(g)

                // Draw the charging glow, if present
                if (blueprint.isCharged) return@run
                val glow = anim.chargeImage ?: return@run

                glow.alpha = blueprint.chargeProgress
                glow.draw(0f, 0f)
            }

            g.popTransform()
        }
    }

    fun launchProjectile(hp: Ship.Hardpoint, projectile: AbstractProjectile) {
        val weaponAnimations = ship.sys.animations.weaponAnimations
        val anim = hp.weapon!!.animation

        departing += DepartingShot(hp, anim.firePoint - anim.mountPoint, projectile)
        projectile.target.ship.inboundProjectiles += projectile
    }

    private fun translateHardpoint(g: Graphics, hp: Ship.Hardpoint) {
        g.translate(hp.x.toFloat(), hp.y.toFloat())

        // We are now in hardpoint-xy space - x and y are relative to the hardpoint's xy,
        // but rotation is independant

        if (hp.rotate)
            g.rotate(0f, 0f, 90f)

        // We are now properly in hardpoint space, y+ is always forward and y- is always backwards

        if (hp.mirror) {
            g.scale(-1f, 1f)
        }
    }

    class DepartingShot(val hardpoint: Ship.Hardpoint, val offset: ConstPoint, val projectile: AbstractProjectile) {
        val initialDistance: Float = projectile.distance
    }
}
