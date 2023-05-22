package xyz.znix.xftl.drones

import xyz.znix.xftl.AnimationSpec
import xyz.znix.xftl.Ship
import xyz.znix.xftl.game.FTLSound
import xyz.znix.xftl.rollChance
import xyz.znix.xftl.weapons.DroneBlueprint
import kotlin.random.Random

abstract class AbstractDrone(val type: DroneBlueprint) {
    var isPowered: Boolean = false
        set(value) {
            val changed = value != field
            field = value
            if (changed)
                onPowerChanged()
        }

    /**
     * Like [isPowered], but also false if the drone is stunned.
     */
    val isRunning: Boolean get() = isPowered && !isStunned

    lateinit var ownerShip: Ship
        private set

    protected var initialised: Boolean = false
        private set

    protected lateinit var explodeAnimation: AnimationSpec
    protected lateinit var explodeSound: FTLSound

    open val isStunned: Boolean get() = ownerShip.drones!!.isHackActive

    private var stunTotalTimer: Float = 0f
    private var stunDestroyTimer: Float = 0f

    open fun init(ownerShip: Ship) {
        if (initialised)
            error("Cannot re-initialise drone ${type.name}")

        explodeAnimation = ownerShip.sys.animations["explosion_random"]
        explodeSound = ownerShip.sys.sounds.getSample("smallExplosion")

        this.ownerShip = ownerShip
        initialised = true
    }

    /**
     * Remove this drone instance, setting a cooldown before
     * it can be re-deployed.
     *
     * This should be called when a drone is destroyed by something,
     * for example a repair drone destroyed by boarders.
     */
    open fun destroy() {
        removeInstance()

        // TODO cooldown
    }

    /**
     * Remove this instance of this drone.
     *
     * This does less than [destroy]: it doesn't set a cooldown or show
     * an explosion animation or anything like that, it only removes
     * the drone instance.
     */
    open fun removeInstance() {
        // Drones that have been moved to cargo won't have an associated
        // info any more, so we have to use firstOrNull.
        val drones = ownerShip.drones!!
        val info = drones.drones.firstOrNull { it?.instance == this }
        info?.instance = null

        // If we're an orphaned drone (blueprint was swapped out), clear
        // ourselves from that list too.
        ownerShip.orphanedDrones.remove(this)
    }

    open fun update(dt: Float) {
        if (isStunned) {
            stunTotalTimer += dt

            // After one second, there's a 15% chance to destroy
            // the drone once per second.
            // It appears this is how both hacking and the anti-drone
            // can destroy drones.
            if (stunTotalTimer >= 1f) {
                // Add a small margin so level 1 hacking can get three
                // attempts at it.
                val waitPeriod = 0.99f
                stunDestroyTimer += dt
                if (stunDestroyTimer >= waitPeriod) {
                    stunDestroyTimer -= waitPeriod

                    if (Random.rollChance(15)) {
                        destroy()
                    }
                }
            }

            return
        }
        stunTotalTimer = 0f
        stunDestroyTimer = 0f
    }

    protected open fun onPowerChanged() {
    }

    /**
     * Called whenever something potentially important happens to the enemy
     * ship, such as it being destroyed or changing as we jump away.
     */
    open fun onEnemyShipUpdated() {
    }
}
