package xyz.znix.xftl.weapons

import org.newdawn.slick.Animation
import org.newdawn.slick.Graphics
import xyz.znix.xftl.Ship
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.systems.Weapons

abstract class AbstractProjectileWeaponInstance(type: ShipWeaponBlueprint, ship: Ship) : AbstractWeaponInstance(type, ship) {
    val isFiring: Boolean get() = !(firingAnimation?.isStopped ?: true)

    var firingAnimation: Animation? = null
    var hasFired: Boolean = false

    private var hp: Ship.Hardpoint? = null
    private var weapons: Weapons? = null
    private var target: Room? = null

    private var shotsFired: Int = 0

    override fun update(dt: Float) {
        super.update(dt)

        val fa = firingAnimation ?: return

        // Don't charge while firing
        timeCharged = 0f

        if (fa.frame == animation.fireFrame - animation.chargedFrame && !hasFired) {
            hasFired = true
            fireFrameHit()
        }

        if (fa.isStopped) {
            if (shotsFired >= type.shots)
                firingAnimation = null
            else
                primeShot()
        }
    }

    override fun render(g: Graphics) {
        if (isFiring)
            firingAnimation!!.draw(0f, 0f)
        else
            super.render(g)
    }

    private fun fireFrameHit() {
        weapons!!.launchProjectile(hp!!, buildProjectile(target!!))
    }

    fun fire(hp: Ship.Hardpoint, weapons: Weapons, target: Room) {
        if (isFiring)
            throw IllegalStateException("Cannot file while already firing!")

        this.hp = hp
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
