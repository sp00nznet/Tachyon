package xyz.znix.xftl.systems

import org.jdom2.Element
import org.newdawn.slick.Graphics
import xyz.znix.xftl.Ship
import xyz.znix.xftl.f
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.math.Point
import xyz.znix.xftl.weapons.AbstractProjectile
import xyz.znix.xftl.weapons.AbstractWeaponInstance
import xyz.znix.xftl.weapons.BeamBlueprint
import xyz.znix.xftl.weapons.IRoomTargetingWeapon
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class Weapons(blueprint: SystemBlueprint, elem: Element) : MainSystem(blueprint, elem) {
    override val sortingType: SortingType get() = SortingType.WEAPONS

    val selectedTargets = TargetList()

    /**
     * Set by SlickGame, this is true if the opponent's cloak is active.
     *
     * This prevents the weapons from charging.
     */
    var opponentCloakActive: Boolean = false

    override fun update(dt: Float) {
        super.update(dt)

        for (hp in ship.hardpoints) {
            val weapon = hp.weapon ?: continue
            weapon.update(dt, !opponentCloakActive, isHackActive)

            // Update the weapon slide
            val slideSpeed = dt * 2
            if (weapon.isPowered) {
                weapon.slide = (weapon.slide + slideSpeed).coerceAtMost(1f)
            } else {
                weapon.slide = (weapon.slide - slideSpeed).coerceAtLeast(0f)
            }
        }

        selectedTargets.update()
    }

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
        val anim = hp.weapon!!.animation

        // Fly off-screen, so it jumps over to the target ship.

        // Find the exact position to start at.
        // Take the difference between the fire and mounting positions
        // on the animation. The latter, when converted into ship-space,
        // is equal to hp.position.
        val startPos = Point(0, 0)
        startPos += anim.firePoint
        startPos -= anim.mountPoint

        // Convert from hardpoint-space to ship-space.
        // This does everything that translateHardpoint does, but backwards
        // since matrix transforms (which the Slick operations in
        // translateHardpoint are) apply the last operation first.
        if (hp.mirror) {
            startPos.x *= -1
        }
        if (hp.rotate) {
            // To rotate counter-clockwise, set:
            // Y to X (so a right-hand line points up)
            // X to -Y (so an upwards line points left)
            val tmp = startPos.x
            startPos.x = -startPos.y
            startPos.y = tmp
        }
        startPos += hp.position

        // Depending on whether we're the player or enemy ship, we need
        // to fly in different directions as they're angled differently.
        val endPos = startPos + if (ship.isPlayerShip) {
            // Fly right
            ConstPoint(1000, 0)
        } else {
            // Fly upwards
            ConstPoint(0, -1000)
        }

        projectile.setInitialPath(startPos, endPos)

        ship.projectiles += projectile
    }

    private fun translateHardpoint(g: Graphics, hp: Ship.Hardpoint) {
        g.translate(hp.position.x.f, hp.position.y.f)

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

            // Force-turn-off the weapon, even if we have ion damage.
            // This is required since otherwise we could end up powering
            // more weapons than we're allowed to, for example if
            // we took damage while ion-locked.
            hp.weapon?.forceSetPowered(false)
        }
    }

    override fun increasePower() {
        for (hp in ship.hardpoints) {
            val weapon = hp.weapon ?: continue

            if (weapon.isPowered)
                continue

            if (weapon.type.power > powerUnused)
                continue

            if (!weapon.hasEnoughMissiles)
                continue

            hp.weapon?.let { setWeaponPower(it, true) }
            return
        }
    }

    override fun decreasePower() {
        for (hp in ship.hardpoints.asReversed()) {
            val weapon = hp.weapon ?: continue

            if (!weapon.isPowered)
                continue

            setWeaponPower(weapon, false)
            return
        }
    }

    /**
     * Turns a weapon on or off.
     *
     * Returns true if successful.
     */
    fun setWeaponPower(weapon: AbstractWeaponInstance, newPower: Boolean): Boolean {
        if (weapon.isPowered == newPower)
            return true

        // Can't power weapons on or off with ion damage.
        if (isPowerLocked)
            return false

        if (newPower) {
            if (weapon.type.power > powerUnused)
                return false

            if (!weapon.hasEnoughMissiles)
                return false
        }

        weapon.forceSetPowered(newPower)
        powerStateChanged()
        return true
    }

    inner class TargetList {
        /**
         * For a weapon that's aimed at a specific room (notably not beams),
         * target it onto a given room.
         */
        fun targetRoom(weaponId: Int, room: Room) {
            val weapon = ship.hardpoints[weaponId].weapon!!
            check(weapon is IRoomTargetingWeapon)

            targets[weaponId] = SelectedTarget.RoomAim(room, weapon, weaponId)
        }

        fun targetBeam(weaponId: Int, beam: SelectedTarget.BeamAim) {
            val weapon = ship.hardpoints[weaponId].weapon!!
            require(weapon == beam.weapon)

            targets[weaponId] = beam
        }

        fun unTarget(weaponId: Int) {
            targets.remove(weaponId)
        }

        operator fun iterator(): MutableIterator<SelectedTarget> {
            return targets.values.iterator()
        }

        fun getTarget(weaponId: Int): SelectedTarget? {
            return targets[weaponId]
        }

        fun update() {
            // Un-target all unpowered weapons
            selectedTargets.targets.values.removeIf { !it.weapon.isPowered }
        }

        private val targets = HashMap<Int, SelectedTarget>()

        // If the user is currently aiming a beam, this marks it so the ship being targeted
        // can draw it. It's a bit ugly, but keeps all the target-drawing stuff in one place.
        var beamAiming: SelectedTarget.BeamAim? = null
    }

    companion object {
        const val NAME = "weapons"
    }
}

sealed class SelectedTarget(val weapon: AbstractWeaponInstance, val weaponNumber: Int) {
    class RoomAim(val room: Room, weapon: IRoomTargetingWeapon, weaponNumber: Int) :
        SelectedTarget(weapon.asWeaponInstance(), weaponNumber) {

        val roomTargetingWeapon get() = weapon as IRoomTargetingWeapon
    }

    class BeamAim(
        weapon: BeamBlueprint.BeamInstance,
        weaponId: Int,
        val targetShip: Ship,
        val startMousePoint: IPoint,
        val startShipPoint: IPoint
    ) : SelectedTarget(weapon, weaponId) {
        val hitRooms = ArrayList<Room>()
        var angle: Float = 0f
        var visible: Boolean = false

        val beamWeapon get() = weapon as BeamBlueprint.BeamInstance

        /**
         * Update the rooms the beam will hit, based on [angle].
         */
        fun updateHitRooms() {
            visible = true

            val length = (weapon.type as BeamBlueprint).length

            // Loop over the pixels, marking whenever we cross one of the lines of the
            // grid all the enemy rooms are placed on.
            val lastPoint = Point(-100, -100)
            var lastRoom: Room? = null
            val tmpPoint = Point(ConstPoint.ZERO)
            for (i in 0 until length) {
                tmpPoint.x = startShipPoint.x + (i * cos(angle)).roundToInt()
                tmpPoint.y = startShipPoint.y + (i * sin(angle)).roundToInt()

                // This (among other things) divides the position by the size
                // of a room, so whenever the result changes we might
                // be in a new room.
                targetShip.screenPosToShipPos(tmpPoint)

                if (tmpPoint == lastPoint)
                    continue
                lastPoint.set(tmpPoint)

                val roomPoint = targetShip.shipToRoomPos(tmpPoint) ?: continue

                // We might still be inside the same room, however.
                if (roomPoint.room == lastRoom)
                    continue
                lastRoom = roomPoint.room

                hitRooms.add(roomPoint.room)
            }
        }
    }
}
