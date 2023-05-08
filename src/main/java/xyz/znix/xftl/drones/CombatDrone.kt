package xyz.znix.xftl.drones

import org.newdawn.slick.Graphics
import org.newdawn.slick.Image
import xyz.znix.xftl.Constants
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.weapons.AbstractWeaponInstance
import xyz.znix.xftl.weapons.BeamBlueprint
import xyz.znix.xftl.weapons.DroneBlueprint
import xyz.znix.xftl.weapons.IRoomTargetingWeapon

class CombatDrone(type: DroneBlueprint) : AbstractExternalDrone(type, true) {
    override val flightController = CombatFlightController()

    private lateinit var offImage: Image
    private lateinit var onImage: Image
    private lateinit var chargedImage: Image

    private lateinit var weapon: AbstractWeaponInstance

    private var fireTimer: Float = 0f

    private lateinit var target: Room

    init {
        requireNotNull(type.weaponBlueprint) { "Missing weapon blueprint for combat drone '${type.name}'" }
    }

    override fun onInit() {
        offImage = game.getImg("img/ship/drones/${type.droneImage}_base.png")
        onImage = game.getImg("img/ship/drones/${type.droneImage}_on.png")
        chargedImage = game.getImg("img/ship/drones/${type.droneImage}_charged.png")

        weapon = type.weaponBlueprint!!.buildInstance(ownerShip)

        flightController.onReachedDestination = this::onReachedDestination
        flightController.pauseStopTime = 0.5f

        pickNewTarget()
    }

    override fun onRender(g: Graphics) {
        val image = when {
            !isPowered -> offImage
            flightController.paused -> chargedImage
            else -> onImage
        }

        image.draw(-image.width / 2f, -image.height / 2f)
    }

    override fun update(dt: Float) {
        super.update(dt)

        if (flightController.paused) {
            fireTimer -= dt
            if (fireTimer <= 0f) {
                fireTimer = 0f
                flightController.paused = false

                fire()
                pickNewTarget()
            }
        }
    }

    private fun onReachedDestination() {
        fireTimer = 0.5f
        flightController.paused = true
    }

    private fun fire() {
        // Required for Kotlin smart-casting since 'weapon' is mutable
        val weapon = weapon

        // TODO fire the weapon
        when (weapon) {
            is IRoomTargetingWeapon -> weapon.fireFromDrone(this, target)

            is BeamBlueprint.BeamInstance -> {
                // TODO implement
            }

            else -> error("Unsupported weapon type for drone: ${weapon.type}")
        }
    }

    private fun pickNewTarget() {
        // For now, always pick a random room. TODO Is that right?
        target = targetShip.rooms.random()

        // Tell the drone where it is, so it can rotate accordingly
        flightController.nextStopTarget = ConstPoint(
            target.offsetX + target.width * Constants.ROOM_SIZE / 2,
            target.offsetY + target.height * Constants.ROOM_SIZE / 2
        )
    }
}
