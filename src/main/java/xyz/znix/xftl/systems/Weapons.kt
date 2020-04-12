package xyz.znix.xftl.systems

import org.jdom2.Element
import org.newdawn.slick.Graphics
import xyz.znix.xftl.Ship
import xyz.znix.xftl.f
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.weapons.AbstractProjectile
import xyz.znix.xftl.weapons.AbstractWeaponInstance
import kotlin.math.roundToInt

class Weapons(elem: Element) : MainSystem("weapons", elem) {
    override val sortingType: SortingType get() = SortingType.WEAPONS

    override fun update(dt: Float) {
        for (hp in ship.hardpoints) {
            val weapon = hp.weapon ?: continue
            weapon.update(dt)

            // Update the weapon slide
            val slideSpeed = dt * 2
            if (weapon.isPowered) {
                weapon.slide = (weapon.slide + slideSpeed).coerceAtMost(1f)
            } else {
                weapon.slide = (weapon.slide - slideSpeed).coerceAtLeast(0f)
            }
        }
    }

    private val departing: MutableList<DepartingShot> = ArrayList()

    override val powerSelected: Int
        get() {
            var power = 0
            for (hp in ship.hardpoints) {
                val weapon = hp.weapon ?: continue
                if (weapon.isPowered)
                    power += weapon.type.power
            }
            return power
        }

    override fun drawBackground(g: Graphics) {
        // Draw the departing projectiles
        for (shot in departing) {
            val dist = shot.initialDistance - shot.projectile.distance
            g.pushTransform()
            translateHardpoint(g, shot.hardpoint)
            g.translate(shot.offset.x.f, shot.offset.y.f)
            shot.projectile.render(g, 0f, -dist, -90f)
            g.popTransform()
        }

        // Discard these once they hit or miss their target. We could certainly
        // remove them sooner, but there is little reason to.
        departing.removeIf { p -> !p.projectile.target.ship.inboundProjectiles.contains(p.projectile) }

        for (hp in ship.hardpoints) {
            val weapon = hp.weapon ?: continue

            g.pushTransform()

            run {
                val anim = weapon.animation

                // Apply the slide
                if (hp.slide != null) {
                    val dist = (12 * (weapon.slide - 1)).roundToInt()
                    g.translate(hp.slide.x * dist.f, hp.slide.y * dist.f)
                }

                // We start in ship space - x and y are relative to the hull

                translateHardpoint(g, hp)

                // We are now in image space - any given XY value here will line up to the pixel
                // with the same XY on the launcher image.

                g.translate(-anim.mountPoint.x.f, -anim.mountPoint.y.f)

                // TODO how much are the weapons retracted by?

                weapon.render(g)

                // Draw the charging glow, if present
                if (weapon.isCharged) return@run
                val glow = anim.chargeImage ?: return@run

                glow.alpha = weapon.chargeProgress
                glow.draw(0f, 0f)
            }

            g.popTransform()
        }
    }

    fun findHardpoint(weapon: AbstractWeaponInstance): Ship.Hardpoint {
        for (hp in ship.hardpoints) {
            if (hp.weapon == weapon)
                return hp
        }

        throw IllegalArgumentException("No matching hardpoint for weapon $weapon")
    }

    fun launchProjectile(hp: Ship.Hardpoint, projectile: AbstractProjectile) {
        val weaponAnimations = ship.sys.animations.weaponAnimations
        val anim = hp.weapon!!.animation

        departing += DepartingShot(hp, anim.firePoint - anim.mountPoint, projectile)
        projectile.target.ship.inboundProjectiles += projectile
    }

    private fun translateHardpoint(g: Graphics, hp: Ship.Hardpoint) {
        g.translate(hp.x.f, hp.y.f)

        // We are now in hardpoint-xy space - x and y are relative to the hardpoint's xy,
        // but rotation is independant

        if (hp.rotate)
            g.rotate(0f, 0f, 90f)

        // We are now properly in hardpoint space, y+ is always forward and y- is always backwards

        if (hp.mirror) {
            g.scale(-1f, 1f)
        }
    }

    override fun powerStateChanged() {
        // The weapons are arranged in order of priority, so turn the last ones off if possible.
        for (hp in ship.hardpoints.asReversed()) {
            if (powerAvailable >= powerSelected)
                break

            hp.weapon?.isPowered = false
        }
    }

    override fun increasePower() {
        for (hp in ship.hardpoints) {
            val weapon = hp.weapon ?: continue

            if (weapon.isPowered)
                continue

            if (weapon.type.power > powerUnused)
                continue

            weapon.isPowered = true
            powerStateChanged()
            return
        }
    }

    override fun decreasePower() {
        for (hp in ship.hardpoints.asReversed()) {
            val weapon = hp.weapon ?: continue

            if (!weapon.isPowered)
                continue

            weapon.isPowered = false
            powerStateChanged()
            return
        }
    }

    class DepartingShot(val hardpoint: Ship.Hardpoint, val offset: ConstPoint, val projectile: AbstractProjectile) {
        val initialDistance: Float = projectile.distance
    }
}
