package xyz.znix.xftl.systems

import org.jdom2.Element
import org.newdawn.slick.Graphics
import org.newdawn.slick.Image
import xyz.znix.xftl.AbstractSystem
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.Point

class Weapons(elem: Element) : AbstractSystem("weapons", elem) {
    override fun update(dt: Float) {
        for (hp in ship.hardpoints)
            hp.weapon?.update(dt)
    }

    override fun drawBackground(g: Graphics) {
        for (hp in ship.hardpoints) {
            val blueprint = hp.weapon ?: continue

            val weaponAnimations = ship.sys.animations.weaponAnimations

            run {
                val animPnt = Point(hp.x, hp.y)

                val projectile = ship.sys.animations[blueprint.type.projectile]
                projectile.start().draw(animPnt.x.toFloat(), animPnt.y.toFloat())
            }

            g.pushTransform()

            run {
                val anim = weaponAnimations.getValue(blueprint.type.launcher)
                val start = anim.start()

                start.setCurrentFrame((anim.chargedFrame * blueprint.chargeProgress).toInt())

                var launcher: Image = start.currentFrame

                // We start in ship space - x and y are relative to the hull

                g.translate(hp.x.toFloat(), hp.y.toFloat())

                // We are now in hardpoint-xy space - x and y are relative to the hardpoint's xy,
                // but rotation is independant

                if (hp.rotate)
                    g.rotate(0f, 0f, 90f)

                // We are now properly in hardpoint space, y+ is always forward and y- is always backwards

                if (hp.mirror) {
                    g.scale(-1f, 1f)
                }

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
}