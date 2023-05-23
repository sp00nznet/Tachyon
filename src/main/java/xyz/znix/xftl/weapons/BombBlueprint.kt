package xyz.znix.xftl.weapons

import org.jdom2.Element
import org.newdawn.slick.Animation
import org.newdawn.slick.Graphics
import xyz.znix.xftl.Constants
import xyz.znix.xftl.Ship
import xyz.znix.xftl.drones.CombatDrone
import xyz.znix.xftl.f
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.systems.Weapons
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

class BombBlueprint(xml: Element) : AbstractWeaponBlueprint(xml) {
    override val explosion: String = super.explosion ?: "explosion_random"

    override fun buildInstance(ship: Ship): AbstractWeaponInstance {
        return BombInstance(ship)
    }

    inner class BombInstance(ship: Ship) : AbstractWeaponInstance(this, ship), IRoomTargetingWeapon {
        private var firingAnimation: Animation? = null
        private var target: Room? = null
        private var hasFired = false

        override fun render(g: Graphics) {
            val fa = firingAnimation
            if (fa != null) {
                fa.draw()
            } else {
                super.render(g)
            }
        }

        override fun update(dt: Float, canCharge: Boolean, isHacked: Boolean) {
            super.update(dt, canCharge, isHacked)

            val firingAnimation = firingAnimation ?: return

            timeCharged = 0f
            firingAnimation.update((1000 * dt).toLong())

            if (firingAnimation.frame >= animation.chargedFrame && !hasFired) {
                val target = target ?: error("Ended teleport animation without target set - what happened?")
                doBombFire(target)

                hasFired = true
                this.target = null
            }

            if (firingAnimation.isStopped) {
                this.firingAnimation = null
                hasFired = false
            }
        }

        private fun doBombFire(target: Room) {
            val animation = target.ship.sys.animations[projectile!!].start(2f, true)
            animation.setLooping(false)
            val fb = FiredBomb(this@BombBlueprint, target, animation)
            target.ship.inboundBombs += fb
        }

        override fun fire(weapons: Weapons, target: Room) {
            fire()
            this.target = target
            val fa = this.animation.shoot()
            firingAnimation = fa
            fa.setLooping(false)

            type.launchSounds?.get()?.play()
        }

        override fun fireFromDrone(drone: CombatDrone, target: Room) {
            // Should we be subtracting a bomb when used on a drone?
            fire()

            doBombFire(target)
        }
    }

    class FiredBomb(val type: BombBlueprint, val target: Room, val animation: Animation) {
        val missed = Math.random() * 100 < target.ship.evasion
        val hitSuperShield = target.ship.superShield > 0 && !missed
        val position: ConstPoint

        init {
            val ship = target.ship
            position = when {
                missed -> {
                    // Currently just pick anywhere in a rectangle around their shield
                    // TODO implement this in some better way, and find out how FTL does it
                    val halfSize = ship.shieldHalfSize
                    val size = halfSize * 2
                    val rand = ConstPoint((Math.random() * size.x).toInt(), (Math.random() * size.y).toInt())
                    val shipCentre = ship.hullImage.let { ConstPoint(it.width / 2, it.height / 2) }
                    shipCentre + rand - halfSize
                }

                hitSuperShield -> {
                    // Pick a random point on the ship's shield line
                    val halfSize = ship.shieldHalfSize
                    val angle = Random.nextFloat() * PI.toFloat() * 2
                    ship.shieldOrigin + ConstPoint(
                        (halfSize.x * cos(angle)).roundToInt(),
                        (halfSize.y * sin(angle)).roundToInt()
                    )
                }

                else -> {
                    val centreX = target.offsetX + target.width * Constants.ROOM_SIZE / 2
                    val centreY = target.offsetY + target.height * Constants.ROOM_SIZE / 2
                    ConstPoint(centreX, centreY)
                }
            }
        }

        init {
            animation.setLooping(false)
        }

        fun update(dt: Float) {
            animation.update((dt * 1000).toLong())

            if (animation.isStopped) {
                target.ship.inboundBombs.remove(this)

                if (missed) {
                    target.ship.playDamageEffect(type, position)
                } else if (hitSuperShield) {
                    // TODO support ships without a shields system
                    target.ship.shields?.popShieldLayer(type)
                    target.ship.playDamageEffect(type, position)
                } else {
                    target.ship.damage(target, type)
                }
            }
        }

        fun render() {
            animation.draw(position.x.f - animation.width / 2, position.y.f - animation.height / 2)
        }
    }
}
