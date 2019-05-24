package xyz.znix.xftl.systems

import org.jdom2.Element
import org.newdawn.slick.Graphics
import org.newdawn.slick.Image
import xyz.znix.xftl.AbstractSystem
import xyz.znix.xftl.math.Point

class Weapons(elem: Element) : AbstractSystem("weapons", elem) {
    override fun update(dt: Float) {
        for (hp in ship.hardpoints)
            hp.weapon?.update(dt)
    }

    // override fun drawBackground(g: Graphics) {
    override fun drawForeground(g: Graphics) {
        for (hp in ship.hardpoints) {
            val blueprint = hp.weapon ?: continue

            val weaponAnimations = ship.sys.animations.weaponAnimations

            run {
                val animPnt = Point(hp.x, hp.y)

                val projectile = ship.sys.animations[blueprint.type.projectile]
                projectile.start().draw(animPnt.x.toFloat(), animPnt.y.toFloat())
            }

            run {
                val anim = weaponAnimations.getValue(blueprint.type.launcher)
                val start = anim.start()

                start.setCurrentFrame((anim.chargedFrame * blueprint.chargeProgress).toInt())

                var launcher: Image = start.currentFrame
                val pnt = Point(hp.x, hp.y)

                if (hp.mirror) {
                    val margin = launcher.height - anim.mountPoint.y
                    launcher = launcher.getFlippedCopy(true, false)
                    pnt.y -= margin
                }

                pnt -= anim.mountPoint

                launcher.setCenterOfRotation(anim.mountPoint.x.toFloat(), anim.mountPoint.y.toFloat())

                // launcher.rotation = ((System.currentTimeMillis() / 4) % 360).toFloat()
                launcher.rotation = if (hp.rotate) 90f else 0f

                // TODO how much are the weapons retracted by?

                launcher.draw(pnt.x.toFloat(), pnt.y.toFloat(), launcher.width.toFloat(), launcher.height.toFloat())

                // Draw the charging glow, if present
                if (blueprint.isCharged) return@run
                var glow = anim.chargeImage ?: return@run

                if (hp.mirror)
                    glow = glow.getFlippedCopy(true, false)

                glow.setCenterOfRotation(launcher.centerOfRotationX, launcher.centerOfRotationY)
                glow.rotation = launcher.rotation
                glow.alpha = blueprint.chargeProgress
                glow.draw(pnt.x.toFloat(), pnt.y.toFloat())
            }
        }
    }
}