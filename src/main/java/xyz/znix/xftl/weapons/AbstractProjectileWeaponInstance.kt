package xyz.znix.xftl.weapons

import org.newdawn.slick.Graphics
import xyz.znix.xftl.Animations
import xyz.znix.xftl.Ship
import xyz.znix.xftl.drones.CombatDrone
import xyz.znix.xftl.layout.Room

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

    override fun update(dt: Float, canCharge: Boolean, isHacked: Boolean) {
        super.update(dt, canCharge, isHacked)

        if (!isFiring) {
            return
        }

        firingAnimationTimer += dt

        // Don't charge while firing
        timeCharged = 0f

        if (waitingToFire && fireAnimationFrame >= animation.fireFrame) {
            waitingToFire = false
            fireFrameHit()
        }

        if (firingAnimationTimer >= Animations.PROJECTILE_WEAPON_FIRE_TIME) {
            if (shotsRemaining <= 0) {
                // This stops us firing
                target = null

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

    protected open fun fireFrameHit() {
        val projectile = buildProjectile(target!!)
        val hp = weapons.findHardpoint(this)
        projectile.entryAngle = entryAngle
        weapons.launchProjectile(hp, projectile)

        type.launchSounds?.get()?.play()
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

    private fun primeShot() {
        shotsRemaining--
        waitingToFire = true
        firingAnimationTimer = 0f
    }

    protected abstract fun buildProjectile(target: Room): AbstractProjectile
}
