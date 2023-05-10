package xyz.znix.xftl.drones

import org.newdawn.slick.Color
import org.newdawn.slick.Graphics
import org.newdawn.slick.Image
import xyz.znix.xftl.Ship
import xyz.znix.xftl.f
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.math.Point
import xyz.znix.xftl.weapons.AbstractProjectile
import xyz.znix.xftl.weapons.AbstractWeaponProjectile
import xyz.znix.xftl.weapons.DroneBlueprint
import xyz.znix.xftl.weapons.LaserBlueprint
import kotlin.math.*

class DefenceDrone(type: DroneBlueprint) : AbstractExternalDrone(type, false) {
    override val flightController = OrbitFlightController(this)

    private lateinit var offImage: Image
    private lateinit var onImage: Image
    private lateinit var engine: Image

    private lateinit var gunOffImage: Image
    private lateinit var gunOnImage: Image
    private lateinit var gunChargedImage: Image

    private val typeCooldown = type.cooldown!! / 1000f

    // Note: missiles and asteroids count as the same thing
    private val shootsAtMissiles: Boolean = type.defenceTarget != DRONES_TARGET
    private val shootsAtLasers: Boolean = type.defenceTarget == LASERS_TARGET
    private val shootsAtDrones: Boolean = type.defenceTarget == DRONES_TARGET

    private lateinit var weapon: LaserBlueprint
    private var weaponSpeed: Int = 0

    private var aimAngle: Float = 0f
    private var cooldown: Float = 0f

    // Remember the last projectile we shot at, to avoid shooting
    // at the same one twice.
    private var lastShotAtTarget: AbstractProjectile? = null

    override fun onInit() {
        offImage = game.getImg("img/ship/drones/${type.droneImage}_base.png")
        onImage = game.getImg("img/ship/drones/${type.droneImage}_charged.png")
        engine = game.getImg("img/ship/drones/drone_engine.png")

        gunOffImage = game.getImg("img/ship/drones/${type.droneImage}_gun.png")
        gunOnImage = game.getImg("img/ship/drones/${type.droneImage}_gun_on.png")
        gunChargedImage = game.getImg("img/ship/drones/${type.droneImage}_gun_charged.png")

        // Just assume we're using a laser, it could get a bit silly otherwise.
        weapon = type.weaponBlueprint!! as LaserBlueprint

        // On that note, assume the speed defaults to 60 which is the case for lasers.
        // (x16 is to convert the speed into pixels-per-second)
        weaponSpeed = (weapon.speed ?: 60) * 16
    }

    override fun onRender(g: Graphics) {
        val baseImage = when {
            isPowered -> onImage
            else -> offImage
        }

        // Try to keep everything as an integer to make the sprites sharper.
        drawCentred(baseImage)

        // Draw the little thruster flame.
        if (isPowered) {
            val movementAngle = atan2(flightController.speedY, flightController.speedX)
            g.pushTransform()
            g.rotate(0f, 0f, Math.toDegrees(movementAngle.toDouble()).toFloat() + 90)
            drawCentred(engine)
            g.popTransform()
        }

        // Draw the gun.
        // It points up with no rotation, so we need to rotate it clockwise
        // to make 0 match up with sin/cos space.
        val gunImage = when {
            !isPowered -> gunOffImage
            else -> gunOnImage
        }

        g.pushTransform()
        g.rotate(0f, 0f, Math.toDegrees(aimAngle.toDouble()).toFloat())

        // Draw the base image
        drawCentred(gunImage)

        // And draw the charged image with an increasing opacity as we charge up
        if (isPowered) {
            val timeCharging = typeCooldown - cooldown
            val chargeProgress = max(0f, timeCharging / typeCooldown)
            val colour = Color(1f, 1f, 1f, chargeProgress)
            drawCentred(gunChargedImage, colour)
        }

        g.popTransform()
    }

    private fun drawCentred(image: Image, filter: Color = Color.white) {
        // Try to keep everything as an integer to make the sprites sharper.
        image.draw(-(image.width / 2).f, -(image.height / 2).f, filter)
    }

    override fun update(dt: Float) {
        super.update(dt)

        if (!isPowered) {
            cooldown = typeCooldown
            return
        }

        cooldown = max(0f, cooldown - dt)

        val bestTarget: InterceptResult? = if (shootsAtDrones) {
            pickAndShootAtDrones()
        } else {
            pickBestProjectileTarget()
        }

        // Can we shoot at something?
        if (cooldown == 0f && bestTarget != null) {
            shootAt(bestTarget)
        }

        // Point the gun at whatever we're planning to shoot
        // when it gets in range (or have just shot at).
        if (bestTarget != null) {
            aimAngle = DroneFlightController.getAngleFrom(flightController.position, bestTarget.point)
        }
    }

    private fun pickAndShootAtDrones(): InterceptResult? {
        // TODO implement, this is for the anti-drone
        return null
    }

    private fun pickBestProjectileTarget(): InterceptResult? {
        var best: InterceptResult? = null

        projectileLoop@ for (projectile in ownerShip.projectiles) {
            if (projectile !is AbstractProjectile)
                continue

            // Skip targets we've already shot at
            if (projectile == lastShotAtTarget)
                continue

            // Ignore departing shots, or those shooting
            // at something other than the ship.
            if (projectile.targetShip != ownerShip)
                continue

            if (projectile is AbstractWeaponProjectile) {
                // Ignore shots that were fired by another drone, since
                // that'll distract this drone a lot, and it doesn't *seem*
                // to happen in vanilla.
                if (projectile.firedByDrone)
                    continue
            }

            // Skip projectiles we're not allowed to shoot at.
            when {
                projectile.isLaserForDD && shootsAtLasers -> Unit
                projectile.isMissileForDD && shootsAtMissiles -> Unit
                else -> continue@projectileLoop
            }

            // Check if we can intercept this target (IP=intercept point)
            val result = calculateIntercept(projectile, weaponSpeed.f) ?: continue

            // Check if the projectile will reach the target prior to the interception
            val distanceToTarget = projectile.targetPos.distToSq(projectile.position)
            val timeToIntercept = sqrt(distanceToTarget.f) / projectile.speed

            if (timeToIntercept < result.time)
                continue

            // TODO prioritise targets

            best = result
        }

        return best
    }

    private fun calculateIntercept(projectile: AbstractProjectile, ourSpeed: Float): InterceptResult? {
        // Centre the projectile at the origin by translating the drone

        val dronePos = Point(flightController.position)
        dronePos -= projectile.position

        val distanceToDrone = dronePos.distToSq(ConstPoint.ZERO).f

        val projVelX = cos(projectile.rotation) * projectile.speed.f
        val projVelY = sin(projectile.rotation) * projectile.speed.f

        // This is largely based on the following article. Paste it's equations into Lyx to read them.
        // https://www.codeproject.com/Articles/990452/Interception-of-Two-Moving-Objects-in-D-Space

        val a = ourSpeed.pow(2) - projectile.speed.f.pow(2)
        val b = 2 * (dronePos.x * projVelX + dronePos.y * projVelY)
        val c = -distanceToDrone

        // If we're shooting at the exact same speed as the incoming projectile, we get
        // a degenerate case that would otherwise cause a divide-by-zero.
        if (a < 0.001f) {
            val interceptTime = distanceToDrone / b

            val interceptPos = Point(projectile.position)
            interceptPos.x += (projVelX * interceptTime).roundToInt()
            interceptPos.y += (projVelY * interceptTime).roundToInt()
            return InterceptResult(projectile, interceptPos, interceptTime)
        }

        val discriminant = b.pow(2) - 4 * a * c

        // No time at which an interception would work?
        if (discriminant <= 0) {
            return null
        }

        val interceptTime1 = (-b + sqrt(discriminant)) / (2 * a)
        val interceptTime2 = (-b - sqrt(discriminant)) / (2 * a)

        val bestInterceptTime = when {
            // Both intercepts are in the past?
            interceptTime1 <= 0f && interceptTime2 <= 0f -> return null

            // If one time is in the past, use the other
            interceptTime1 <= 0f -> interceptTime2
            interceptTime2 <= 0f -> interceptTime1

            // If there are two valid times, pick the first one.
            else -> min(interceptTime1, interceptTime2)
        }

        // From the intercept time, we can trivially solve the intercept point.
        val interceptPos = Point(projectile.position)
        interceptPos.x += (projVelX * bestInterceptTime).roundToInt()
        interceptPos.y += (projVelY * bestInterceptTime).roundToInt()

        return InterceptResult(projectile, interceptPos, bestInterceptTime)
    }

    private fun shootAt(interception: InterceptResult) {
        val point = interception.point

        // Require that all projectiles are within -150 to 450
        // in both the X and Y coordinates. This is what prevents
        // the defence drone from defending the right-hand side
        // of some larger ships.
        // (Note these should be exclusive ranges, but who cares).
        // See doc/defence-drones for the source of these numbers.
        // The reason we don't include this when searching for
        // targets is:
        // a) So that the gun barrel aiming code works for out-of-range
        //    targets, and:
        // b) So that if we prioritise one target over another, we
        //    wait until the more important one is in range.
        if (point.x !in -150..450)
            return
        if (point.y !in -150..450)
            return

        val shot = InterceptorLaser()
        targetShip.projectiles += shot

        shot.setInitialPath(flightController.position, point)

        weapon.launchSounds?.get()?.play()

        cooldown = typeCooldown
        lastShotAtTarget = interception.target
    }

    private inner class InterceptorLaser : AbstractProjectile(null) {

        private val animation = ownerShip.sys.animations[weapon.projectile!!]

        private val hitAnimation = ownerShip.sys.animations[weapon.explosion]

        override val speed: Int get() = weaponSpeed

        override fun renderPreTranslated(g: Graphics) {
            val spr = animation.spriteAt(0)

            spr.draw(-spr.width.f, -spr.height.f / 2)
        }

        override fun reachedTarget() {
            // Do nothing and fly off into space.
        }

        // Since we don't override crossedShieldLine, we completely ignore shields.

        override fun hitOtherProjectile(currentSpace: Ship) {
            currentSpace.animations += Ship.FloatingAnimation.centered(hitAnimation.start(), position)
        }
    }

    private class InterceptResult(val target: AbstractProjectile, val point: IPoint, val time: Float)

    companion object {
        // The target type used by defence 2 drones, this also shoots
        // at asteroids/missiles.
        private const val LASERS_TARGET = "LASERS"

        // Used by anti-drones, this only fires at drones.
        private const val DRONES_TARGET = "DRONES"
    }
}
