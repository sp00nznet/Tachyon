package xyz.znix.xftl.weapons

import org.jdom2.Element
import org.newdawn.slick.Animation
import org.newdawn.slick.Graphics
import org.newdawn.slick.Image
import xyz.znix.xftl.Constants
import xyz.znix.xftl.Ship
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.math.Point
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// Implementation for flak-style weapons, including the swarm missiles.
class FlakBlueprint(xml: Element) : ShipWeaponBlueprint(xml) {
    override val explosion: String = super.explosion ?: "explosion_missile1"
    val radius: Int = xml.getChildTextTrim("radius").toInt()

    private val projectileSpecs: List<ProjectileSpec>

    init {
        projectileSpecs = ArrayList()
        xml.getChild("projectiles")?.getChildren("projectile")?.forEach { proj ->
            projectileSpecs += ProjectileSpec(
                proj.getAttributeValue("count")!!.toInt(),
                proj.textTrim,
                proj.getAttributeValue("fake")!!.toBoolean()
            )
        }
    }

    override fun buildInstance(ship: Ship): AbstractWeaponInstance = FlakInstance(ship)

    inner class FlakInstance(ship: Ship) : AbstractProjectileWeaponInstance(this, ship) {
        override fun buildProjectile(target: Room) = error("Building a single flak projectile isn't supported")

        override fun fireFrameHit() {
            // Make sure all the projectiles come in from the same angle
            val angle: Float = (Math.random() * Math.PI * 2).toFloat()

            val hp = weapons!!.findHardpoint(this)

            for (spec in projectileSpecs) {
                for (i in 0 until spec.count) {
                    val projectile = FlakProjectile(target!!, spec)
                    projectile.angle = angle

                    if (spec.fake)
                        continue

                    weapons!!.launchProjectile(hp, projectile)
                }
            }
        }
    }

    private inner class FlakProjectile(room: Room, val spec: ProjectileSpec) : AbstractProjectile(this, room, 3f) {
        private val animation = room.ship.sys.animations[spec.animation].start()

        private val destinationOffset: IPoint
        private val targetPoint: IPoint

        // TODO make all the bits arrive lined up with each other - currently, they all
        //  hit their targets at the same point in time, which doesn't look as good.

        init {
            // Pick where in the circle this bit of flack will land
            // Put the random radius value in a square root so the
            // points don't clump up around the centre.
            val angle = 2 * Math.PI * Random.nextFloat()
            val randomRadius = radius * sqrt(Random.nextFloat())
            destinationOffset = ConstPoint(
                (randomRadius * cos(angle)).toInt(),
                (randomRadius * sin(angle)).toInt()
            )

            // Find the centre of the target room
            val centreX = target.offsetX + target.width * Constants.ROOM_SIZE / 2
            val centreY = target.offsetY + target.height * Constants.ROOM_SIZE / 2

            // Use this to find where on the ship we're headed
            targetPoint = ConstPoint(centreX, centreY) + destinationOffset
        }

        override fun update(dt: Float) {
            super.update(dt)
            animation.update((dt * 1000).toLong())
        }

        override fun render(g: Graphics, x: Float, y: Float, rotation: Float) {
            g.pushTransform()
            g.translate(x, y)
            g.rotate(0f, 0f, rotation)

            animation.draw(-animation.width / 2f, -animation.height / 2f)

            g.popTransform()
        }

        override fun hitShields() {
            if (!spec.fake) {
                ship.shields!!.popShieldLayer()
            }
            playAnimation()
        }

        override fun hitHull() {
            // Fake projectiles are there for the visuals only
            // For real projectiles, we have to turn the ship's automatic
            // explosion animation off because we're drawing it ourselves.
            if (!spec.fake) {
                ship.damage(target, type, false)
            }

            // We always have to use our custom animation, so the explosion
            // animation isn't in the centre of the room.
            playAnimation()
        }

        private fun playAnimation() {
            val baseAnimation = target.ship.sys.animations[explosion].start()

            val animation: Animation
            if (spec.fake) {
                // Fake explosions are scaled down by about 5 times, though
                // this isn't an exact measurement (though I have confirmed
                // they're the same animation).
                animation = Animation()
                for (i in 0 until baseAnimation.frameCount) {
                    val image = ScaledImage(baseAnimation.getImage(i), 0.2f)
                    animation.addFrame(image, baseAnimation.getDuration(i))
                }
            } else {
                animation = baseAnimation
            }

            target.ship.animations += Ship.FloatingAnimation.centered(animation, position)
        }

        override fun calculatePositionFor(dist: Float, output: Point) {
            output.x = (cos(angle.toDouble()) * dist).toInt()
            output.y = (sin(angle.toDouble()) * dist).toInt()
            output += targetPoint
        }
    }

    // Corresponds to a <projectile> tag in the XML
    private class ProjectileSpec(val count: Int, val animation: String, val fake: Boolean)

    // For some reason Slick doesn't seem to have a good way to scale an image?
    // There's getScaledCopy, but that changes the region
    // of the image that is drawn.
    private class ScaledImage(copyOf: Image, scale: Float) : Image(copyOf) {
        init {
            width = (width * scale).toInt()
            height = (height * scale).toInt()
            centerX *= scale
            centerY *= scale
        }
    }
}
