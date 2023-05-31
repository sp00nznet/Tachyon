package xyz.znix.xftl.weapons

import org.jdom2.Element
import org.newdawn.slick.Graphics
import xyz.znix.xftl.Animations
import xyz.znix.xftl.FTLAnimation
import xyz.znix.xftl.Ship
import xyz.znix.xftl.drones.CombatDrone
import xyz.znix.xftl.f
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil
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

    private fun makeBomb(target: Room): FiredBomb {
        val animation = target.ship.sys.animations[projectile!!].startSingle(0.5f, true)
        return FiredBomb(this@BombBlueprint, target, animation)
    }

    inner class BombInstance(ship: Ship) : AbstractWeaponInstance(this, ship), IRoomTargetingWeapon {
        private var firingAnimationTimer: Float = 0f
        private var target: Room? = null
        private var hasFired = false

        private val fireAnimationFrame: Int
            get() {
                require(target != null)
                return animation.fireIndex(firingAnimationTimer, Animations.BOMB_FIRE_TIME)
            }

        override fun render(g: Graphics) {
            if (target != null) {
                val frame = animation.spriteAt(fireAnimationFrame)
                frame.draw()
            } else {
                super.render(g)
            }
        }

        override fun update(dt: Float, canCharge: Boolean, isHacked: Boolean) {
            super.update(dt, canCharge, isHacked)

            val target = this.target ?: return

            firingAnimationTimer += dt

            if (!hasFired && fireAnimationFrame >= animation.fireFrame) {
                doBombFire(target)
                hasFired = true
            }

            if (firingAnimationTimer >= Animations.BOMB_FIRE_TIME) {
                this.target = null
                hasFired = false
                firingAnimationTimer = 0f
            }
        }

        private fun doBombFire(target: Room) {
            target.ship.projectiles += makeBomb(target)
        }

        override fun fire(target: Room) {
            fire()
            this.target = target

            type.launchSounds?.get()?.play()
        }

        override fun fireFromDrone(drone: CombatDrone, target: Room) {
            // Should we be subtracting a bomb when used on a drone?
            fire()

            doBombFire(target)
        }

        override fun saveToXML(elem: Element, refs: ObjectRefs) {
            super.saveToXML(elem, refs)

            SaveUtil.addTagFloat(elem, "firingAnimationTimer", firingAnimationTimer, 0f)
            SaveUtil.addTagBoolIfTrue(elem, "hasFired", hasFired)
            if (target != null) {
                SaveUtil.addRoomRef(elem, "target", refs, target!!)
            }
        }

        override fun loadFromXML(elem: Element, refs: RefLoader) {
            super.loadFromXML(elem, refs)

            firingAnimationTimer = SaveUtil.getOptionalTagFloat(elem, "firingAnimationTimer") ?: 0f
            hasFired = SaveUtil.getOptionalTagBool(elem, "hasFired") ?: false

            if (elem.getChild("target") != null) {
                SaveUtil.getRoomRef(elem, "target", refs) { target = it }
            }
        }
    }

    class FiredBomb(val type: BombBlueprint, val target: Room, val animation: FTLAnimation) : IProjectile {
        private var missed = Math.random() * 100 < target.ship.evasion
        private var hitSuperShield = target.ship.superShield > 0 && !missed

        override var position: ConstPoint = ConstPoint.ZERO
            private set

        override val serialisationType: String get() = SERIALISATION_TYPE

        // Can't collide with drones or other projectiles
        override val collisionsEnabled: Boolean get() = false
        override val antiDroneBP: AbstractWeaponBlueprint? get() = null
        override val antiDroneExemption: Ship? get() = null

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

                else -> target.pixelCentre
            }
        }

        override fun update(dt: Float, currentSpace: Ship) {
            animation.update(dt)

            if (!animation.isStopped)
                return

            currentSpace.projectiles.remove(this)

            if (missed) {
                currentSpace.playDamageEffect(type, position)
            } else if (hitSuperShield) {
                // TODO support ships without a shields system
                currentSpace.shields?.popShieldLayer(type)
                currentSpace.playDamageEffect(type, position)
            } else {
                currentSpace.damage(target, type)
            }
        }

        override fun render(g: Graphics, currentSpace: Ship) {
            animation.draw(position.x.f - animation.width / 2, position.y.f - animation.height / 2)
        }

        override fun hitOtherProjectile(currentSpace: Ship) = error("Bombs have collision disabled")

        override fun saveToXML(elem: Element, refs: ObjectRefs) {
            SaveUtil.addAttr(elem, "type", type.name)

            SaveUtil.addAttr(elem, "targetShip", refs[target.ship])
            SaveUtil.addAttrInt(elem, "targetRoomId", target.id)
            SaveUtil.addAttrFloat(elem, "animationTimer", animation.timer)

            SaveUtil.addTagBoolIfTrue(elem, "missed", missed)
            SaveUtil.addTagBoolIfTrue(elem, "hitSuperShield", hitSuperShield)

            // The position will always be the same if we hit, but if we miss
            // or hit a zoltan shield then it's randomised.
            SaveUtil.addPoint(elem, "position", position)
        }

        fun loadFromXML(elem: Element) {
            missed = SaveUtil.getOptionalTagBool(elem, "missed") ?: false
            hitSuperShield = SaveUtil.getOptionalTagBool(elem, "hitSuperShield") ?: false

            position = SaveUtil.getPoint(elem, "position")

            animation.timer = SaveUtil.getAttrFloat(elem, "animationTimer")
        }
    }

    companion object {
        fun loadProjectileFromXML(
            game: InGameState,
            elem: Element, refs: RefLoader,
            callback: ProjectileLoadCallback
        ) {
            val typeName = SaveUtil.getAttr(elem, "type")
            val type = game.blueprintManager[typeName] as BombBlueprint

            val targetShip = SaveUtil.getAttr(elem, "targetShip")
            val targetRoomId = SaveUtil.getAttrInt(elem, "targetRoomId")

            refs.asyncResolve(Ship::class.java, targetShip) {
                val room = it!!.rooms[targetRoomId]
                val bomb = type.makeBomb(room)
                bomb.loadFromXML(elem)
                callback(bomb)
            }
        }

        const val SERIALISATION_TYPE = "firedBomb"
    }
}
