package xyz.znix.xftl.weapons

import org.newdawn.slick.Animation
import org.newdawn.slick.Graphics
import xyz.znix.xftl.Ship
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.systems.Weapons

abstract class AbstractProjectileWeaponInstance(type: ShipWeaponBlueprint, ship: Ship) :
    AbstractWeaponInstance(type, ship), IRoomTargetingWeapon {
    val isFiring: Boolean get() = !(firingAnimation?.isStopped ?: true)

    var firingAnimation: Animation? = null
    var hasFired: Boolean = false

    protected var weapons: Weapons? = null
    protected var target: Room? = null

    protected var shotsFired: Int = 0
    protected var lastProjectile: AbstractProjectile? = null

    override fun update(dt: Float, canCharge: Boolean) {
        super.update(dt, canCharge)

        val fa = firingAnimation ?: return

        fa.update((dt * 1000).toLong())

        // Don't charge while firing
        timeCharged = 0f

        if (fa.frame >= animation.fireFrame - animation.chargedFrame && !hasFired) {
            hasFired = true
            fireFrameHit()
        }

        if (fa.isStopped) {
            if (shotsFired >= type.shots) {
                firingAnimation = null
                lastProjectile = null
            } else {
                primeShot()
            }
        }
    }

    override fun render(g: Graphics) {
        if (isFiring)
            firingAnimation!!.draw(0f, 0f)
        else
            super.render(g)
    }

    protected open fun fireFrameHit() {
        val projectile = buildProjectile(target!!)
        val hp = weapons!!.findHardpoint(this)
        lastProjectile?.run { projectile.angle = angle }
        weapons!!.launchProjectile(hp, projectile)
        lastProjectile = projectile
    }

    override fun fire(weapons: Weapons, target: Room) {
        check(!isFiring) { "Cannot file while already firing!" }

        this.weapons = weapons
        this.target = target
        shotsFired = 0
        fire()
        val anim = animation.shoot()
        anim.setLooping(false)
        firingAnimation = anim
        primeShot()
    }

    private fun primeShot() {
        shotsFired++
        hasFired = false
        firingAnimation!!.restart()
    }

    protected abstract fun buildProjectile(target: Room): AbstractProjectile
}
