package xyz.znix.xftl.weapons

import org.jdom2.Element
import org.newdawn.slick.Graphics
import xyz.znix.xftl.Animations
import xyz.znix.xftl.Ship
import xyz.znix.xftl.drones.CombatDrone
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil
import xyz.znix.xftl.systems.Artillery
import xyz.znix.xftl.systems.Weapons

abstract class AbstractProjectileWeaponInstance(type: AbstractWeaponBlueprint, ship: Ship) :
    AbstractWeaponInstance(type, ship), IRoomTargetingWeapon {

    protected var target: Room? = null
    private val isFiring: Boolean get() = target != null

    private var firingAnimationTimer: Float = 0f
    private var waitingToFire: Boolean = false

    private val fireAnimationFrame: Int
        get() {
            require(isFiring)
            return animation.fireIndex(firingAnimationTimer, Animations.PROJECTILE_WEAPON_FIRE_TIME)
        }

    protected var shotsRemaining: Int = 0
    protected var entryAngle: Float = 0f

    // Doesn't need to be serialised, as it's set by the weapons or artillery system.
    protected lateinit var projectileSpawnPos: IPoint

    override fun update(dt: Float, canCharge: Boolean, isHacked: Boolean) {
        super.update(dt, canCharge, isHacked)

        if (!isFiring) {
            return
        }

        firingAnimationTimer += dt

        // Surprisingly, weapons charge while firing

        if (waitingToFire && fireAnimationFrame >= animation.fireFrame) {
            waitingToFire = false
            fireFrameHit()
        }

        if (firingAnimationTimer >= Animations.PROJECTILE_WEAPON_FIRE_TIME) {
            if (shotsRemaining <= 0) {
                // This stops us firing
                target = null

                // Save space in the savefile, as these aren't written if they're zero.
                entryAngle = 0f
                firingAnimationTimer = 0f
            } else {
                primeShot()
            }
        }
    }

    override fun render(g: Graphics) {
        if (isFiring) {
            val frame = animation.spriteAt(fireAnimationFrame)
            frame.draw(0f, 0f)
        } else {
            super.render(g)
        }
    }

    override fun bindToWeaponsSystem(weapons: Weapons) {
        super.bindToWeaponsSystem(weapons)

        if (!this::projectileSpawnPos.isInitialized) {
            this.projectileSpawnPos = weapons.getProjectileSpawnPos(this)
        }
    }

    override fun bindToArtillery(artillery: Artillery) {
        super.bindToArtillery(artillery)

        if (!this::projectileSpawnPos.isInitialized) {
            this.projectileSpawnPos = artillery.weaponFirePoint
        }
    }

    protected open fun fireFrameHit() {
        val projectile = buildProjectile(target!!)
        projectile.entryAngle = entryAngle
        launchProjectile(projectile)

        type.launchSounds?.get()?.play()
    }

    protected fun launchProjectile(projectile: AbstractProjectile) {
        // Depending on whether we're the player or enemy ship, we need
        // to fly in different directions as they're angled differently.
        val endPos = projectileSpawnPos + ship.weaponFireDirection * 5000

        projectile.setInitialPath(projectileSpawnPos, endPos)

        ship.projectiles += projectile
    }

    override fun fire(target: Room) {
        check(!isFiring) { "Cannot file while already firing!" }

        this.target = target
        shotsRemaining = type.shots
        entryAngle = (Math.random() * Math.PI * 2).toFloat()
        fire()
        primeShot()
    }

    override fun fireFromDrone(drone: CombatDrone, target: Room) {
        // If this is a multi-shot weapon, only fire a single shot.
        // We don't have any way of doing more than that.

        val projectile = buildProjectile(target)
        target.ship.projectiles += projectile

        if (projectile is AbstractWeaponProjectile) {
            // Draw the projectile on top of the ship. By default it's
            // set to draw under the ship, as it expects to be launched
            // from one ship area to another, at which point it switches this.
            projectile.drawUnderShip = false

            // Prevent defence drones from firing on this shot.
            projectile.firedByDrone = true
        }

        projectile.setInitialPath(drone.flightController.position, projectile.calculateTargetPosition())

        type.launchSounds?.get()?.play()
    }

    open fun fireFromArtillery(possibleTargets: List<Room>, origin: IPoint) {
        // FIXME non-flak weapons should target separate rooms for each shot!
        target = possibleTargets.random()
        shotsRemaining = type.shots

        entryAngle = (Math.random() * Math.PI * 2).toFloat()
        primeShot()
    }

    override fun saveToXML(elem: Element, refs: ObjectRefs) {
        super.saveToXML(elem, refs)

        SaveUtil.addTagBoolIfTrue(elem, "waitingToFire", waitingToFire)
        SaveUtil.addTagInt(elem, "shotsRemaining", shotsRemaining, 0)
        SaveUtil.addTagFloat(elem, "entryAngle", entryAngle, 0f)
        SaveUtil.addTagFloat(elem, "fireAnimationTimer", firingAnimationTimer, 0f)

        if (target != null) {
            val targetElem = Element("target")
            SaveUtil.addAttr(targetElem, "ship", refs[target!!.ship])
            SaveUtil.addAttrInt(targetElem, "roomId", target!!.id)
            elem.addContent(targetElem)
        }
    }

    override fun loadFromXML(elem: Element, refs: RefLoader) {
        super.loadFromXML(elem, refs)

        waitingToFire = SaveUtil.getOptionalTagBool(elem, "waitingToFire") ?: false
        shotsRemaining = SaveUtil.getOptionalTagInt(elem, "shotsRemaining") ?: 0
        entryAngle = SaveUtil.getOptionalTagFloat(elem, "entryAngle") ?: 0f
        firingAnimationTimer = SaveUtil.getOptionalTagFloat(elem, "fireAnimationTimer") ?: 0f

        val targetElem = elem.getChild("target")
        if (targetElem != null) {
            val roomId = SaveUtil.getAttrInt(targetElem, "roomId")
            val shipRef = SaveUtil.getAttr(targetElem, "ship")
            refs.asyncResolve(Ship::class.java, shipRef) { target = it!!.rooms[roomId] }
        }
    }

    private fun primeShot() {
        shotsRemaining--
        waitingToFire = true
        firingAnimationTimer = 0f
    }

    protected abstract fun buildProjectile(target: Room): AbstractProjectile
}
