package xyz.znix.xftl.systems

import org.jdom2.Element
import org.newdawn.slick.Graphics
import xyz.znix.xftl.AbstractSystem
import xyz.znix.xftl.Ship
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.weapons.AbstractProjectile

class Weapons(elem: Element) : AbstractSystem("weapons", elem) {
    override fun update(dt: Float) {
        for (hp in ship.hardpoints)
            hp.weapon?.update(dt)
    }

    private val departing: MutableList<DepartingShot> = ArrayList()

    override fun drawBackground(g: Graphics) {
        var missing: DepartingShot? = null
        for (shot in departing) {
            val dist = shot.initialDistance - shot.projectile.distance
            g.pushTransform()
            translateHardpoint(g, shot.hardpoint)
            g.translate(shot.offset.x.toFloat(), shot.offset.y.toFloat())
            shot.projectile.render(g, 0f, -dist, -90f)
            g.popTransform()

            if (!shot.projectile.target.ship.inboundProjectiles.contains(shot.projectile))
                missing = shot
        }

        if (missing != null)
            departing.remove(missing)

        for (hp in ship.hardpoints) {
            val blueprint = hp.weapon ?: continue

            val weaponAnimations = ship.sys.animations.weaponAnimations

            g.pushTransform()

            run {
                val anim = weaponAnimations.getValue(blueprint.type.launcher)
                val launcher = anim.spriteAt((anim.chargedFrame * blueprint.chargeProgress).toInt())

                // We start in ship space - x and y are relative to the hull

                translateHardpoint(g, hp)

                // We are now in image space - any given XY value here will line up to the pixel
                // with the same XY on the launcher image.

                g.translate(-anim.mountPoint.x.toFloat(), -anim.mountPoint.y.toFloat())

                // TODO how much are the weapons retracted by?

                launcher.draw(0f, 0f)

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
        val anim = weaponAnimations.getValue(hp.weapon!!.type.launcher)

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
