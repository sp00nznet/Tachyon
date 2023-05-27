package xyz.znix.xftl.systems

import org.jdom2.Element
import org.newdawn.slick.Color
import org.newdawn.slick.Graphics
import org.newdawn.slick.Input
import org.newdawn.slick.Renderable
import xyz.znix.xftl.AbstractSystem
import xyz.znix.xftl.Constants.ROOM_SIZE
import xyz.znix.xftl.FTLAnimation
import xyz.znix.xftl.Ship
import xyz.znix.xftl.f
import xyz.znix.xftl.game.Button
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.game.SoundInstance
import xyz.znix.xftl.game.SystemPowerButton
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.Direction
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.math.Point
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.weapons.AbstractProjectile
import xyz.znix.xftl.weapons.AbstractWeaponBlueprint
import xyz.znix.xftl.weapons.DroneBlueprint
import kotlin.math.max
import kotlin.math.min

class Hacking(blueprint: SystemBlueprint) : MainSystem(blueprint) {
    override val sortingType: SortingType get() = SortingType.HACKING

    /**
     * Is the hacking module powered up, regardless
     * of whether it's actively hacking?
     *
     * This controls passive effects like locking doors
     * and disabling a room's console.
     */
    val isPoweredUp: Boolean get() = powerSelected != 0

    /**
     * If true, the main hacking pulse is currently active.
     */
    val active: Boolean get() = timeRemaining != null

    val droneLaunched: Boolean get() = projectile != null
    val droneLanded: Boolean get() = projectile?.hasLanded == true
    val droneInFlight: Boolean get() = projectile?.hasLanded == false

    private var buttonHeight: Int = 1

    private var timeRemaining: Float? = null

    private val duration get() = 1f + powerSelected.coerceAtLeast(1) * 3f

    // If the player selects a target, the drone isn't deployed
    // until the next update, so you can cancel it.
    private var selectedTarget: Room? = null

    private var projectile: HackingDroneProjectile? = null
        set(value) {
            field = value

            // The power button size changes when the drone lands or is killed
            updateButton()
        }

    private val launchSound by onInit { it.sounds.getSample("droneLaunch") }
    private val landSound by onInit { it.sounds.getSample("hackLand") }
    private val startSound by onInit { it.sounds.getSample("hackStart") }
    private val loopSound by onInit { it.sounds.getLoop("hackLoop") }

    private var loopSoundInstance: SoundInstance? = null

    // TODO block drone launches when a super-shield is up

    override fun powerStateChanged() {
        super.powerStateChanged()
        updateButton()
    }

    private fun updateButton() {
        // If the probe hasn't landed, only show one power.
        // Otherwise use the selected power, clamped to at least one.
        val height = when (projectile) {
            null -> 1
            else -> powerSelected.coerceAtLeast(1)
        }

        // Only bother to update the UI if this is a) on the player
        // ship, and b) the power really did change.
        if (ship == ship.sys.player && buttonHeight != height) {
            buttonHeight = height
            ship.sys.shipUI.updateButtons()
        }
    }

    override fun makeExtraButtons(powerPos: IPoint): List<Button> {
        return listOf(HackButton(buttonHeight, powerPos))
    }

    override fun update(dt: Float) {
        super.update(dt)

        if (timeRemaining != null) {
            loopSoundInstance?.continueLoop()

            // If the drone was destroyed, immediately go into cooldown.
            // This generally shouldn't happen as the drone shouldn't
            // be killable after it lands, but do it just in case.
            if (projectile == null)
                timeRemaining = 0f

            timeRemaining = timeRemaining!! - dt

            // If the system is damaged, that caps the amount hacking time.
            // TODO this was copied from cloaking, check if it's the same here
            timeRemaining = min(timeRemaining!!, duration)

            // We have to check for powerSelected here, since the above
            // won't work if the system is fully broken since duration is
            // never limited to zero.
            if (timeRemaining!! <= 0 || powerSelected == 0) {
                timeRemaining = null
                loopSoundInstance = null

                // Don't apply ion damage - this means we hold onto
                // the power until the cooldown is finished (or
                // the system is damaged)
                ionTimer += Cloaking.COOLDOWN
            }
        }

        val projectile = this.projectile
        if (projectile != null) {
            // Is the projectile gone?
            if (!ship.projectiles.contains(projectile) && !projectile.target.ship.projectiles.contains(projectile)) {
                this.projectile = null
            }
            if (!ship.sys.isShipPresent(projectile.target.ship)) {
                this.projectile = null
            }
        }

        // If the player/AI commanded a launch, perform it.
        selectedTarget?.let {
            deployProbe(it)
            selectedTarget = null
        }
    }

    /**
     * Called when the user clicks on a room to launch the hacking drone,
     * or the AI has picked a room to target.
     */
    fun selectTarget(target: Room) {
        selectedTarget = target

        // deployProbe will be called when next unpaused.
    }

    private fun deployProbe(target: Room) {
        if (projectile != null)
            return

        // Block hacking while we're turned off. It's not possible
        // to select this via the player UI, unless hacking is hit
        // while you're selecting a room.
        if (powerSelected == 0)
            return

        // Block hacking empty rooms
        if (target.system == null)
            return

        val roomCentre = Point(room!!.offsetX, room!!.offsetY)
        roomCentre.x += room!!.width * ROOM_SIZE / 2
        roomCentre.y += room!!.height * ROOM_SIZE / 2

        // The direction the probe flies in depends on whether this is
        // a player ship or an enemy ship, as it's supposed to go
        // forwards out of both of them.
        // Note we need to use a very large value, so it's always
        // outside of the -800 to 800 bounds - otherwise it'll think
        // it reached the enemy ship.
        val endPoint = roomCentre + if (ship.isPlayerShip) {
            // Fly right
            ConstPoint(5000, 0)
        } else {
            // Fly upwards
            ConstPoint(0, -5000)
        }

        projectile = HackingDroneProjectile(target).also {
            it.setInitialPath(roomCentre, endPoint)
            ship.projectiles += it
        }

        launchSound.play()
    }

    // Called by AbstractSystem to check if we're still attacking a given room.
    // This is to make the game robust to avoid a room remaining
    // hacked after a ship jumps away or something similar.
    fun checkStillAttacking(system: AbstractSystem): Boolean {
        val projectile = this.projectile ?: return false

        if (!projectile.hasLanded)
            return false

        // Check this ship hasn't been destroyed
        if (!ship.sys.isShipPresent(ship)) {
            return false
        }

        // This would normally be caught by update, but check it here
        // in case we're on a ship that has jumped away.
        if (!projectile.target.ship.projectiles.contains(projectile)) {
            this.projectile = null
            return false
        }

        return projectile.target == system.room
    }

    fun removeProbe() {
        projectile = null
    }

    fun startHackingPulse() {
        if (isPowerLocked || powerSelected == 0)
            return

        if (!droneLanded)
            return

        if (active)
            return

        // Start the hacking pulse
        this@Hacking.timeRemaining = duration

        startSound.play()
        loopSoundInstance = loopSound.play()
    }

    override fun saveSystem(elem: Element, refs: ObjectRefs) {
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun loadSystem(elem: Element, refs: RefLoader) {
        throw UnsupportedOperationException("Not yet implemented")
    }

    // Copied from Cloaking.CloakButton
    private inner class HackButton(power: Int, powerPos: IPoint) : SystemPowerButton(ship.sys, power, powerPos) {

        override val timeRemaining: Float? get() = this@Hacking.timeRemaining
        override val duration: Float get() = this@Hacking.duration
        override val isOff: Boolean get() = powerSelected == 0 || isPowerLocked || droneInFlight

        override val forceHighlight: Boolean
            get() {
                if (ship != game.player)
                    return false

                return game.shipUI.isSelectingHackingTarget
            }

        override fun click(button: Int) {
            if (button != Input.MOUSE_LEFT_BUTTON)
                return

            // Stop hacking from being activated when it's on cooldown
            if (isPowerLocked)
                return

            if (powerSelected == 0)
                return

            if (projectile == null) {
                // If the player previously selected a target but hasn't
                // unpaused, clear it out.
                selectedTarget = null

                ship.sys.shipUI.hackSelected()
                return
            }

            startHackingPulse()
        }
    }

    private inner class HackingDroneProjectile(val target: Room) : AbstractProjectile(target.ship) {
        val game: InGameState = target.ship.sys

        val blueprint = game.blueprintManager["DRONE_HACKING"] as DroneBlueprint

        val offImage = game.getImg("img/ship/drones/drone_hack_base.png")
        val onImage = game.getImg("img/ship/drones/drone_hack_on.png")
        val lightOverlay = game.getImg("img/ship/drones/drone_hack_light1.png")

        val flyAnimation = game.animations["drone_hack_fly"].startLooping()
        val landAnimation = game.animations["drone_hack_land"].startSingle()
        val extendAnimation = game.animations["drone_hack_extend"].startSingle()

        // Pick the direction the drone is coming from - for example,
        // a drone flying up from the bottom of the screen would be DOWN.
        val fromDirection = Direction.CARDINALS.random()

        var hasLanded: Boolean = false

        // Set until we switch spaces
        override var drawUnderShip: Boolean = true
        override val collisionsEnabled: Boolean get() = !hasLanded

        // We can never collide with drones, as we're pretending to be one.
        override val antiDroneBP: AbstractWeaponBlueprint? get() = null
        override val antiDroneExemption: Ship? get() = null

        override val serialisationType: String get() = throw UnsupportedOperationException("TODO")

        override val isMissileForDD: Boolean get() = !hasLanded

        private var lightFlashTimer: Float = 0f
        override val speed: Int
            get() {
                // Stop once we've reached our destination
                if (hasLanded)
                    return 0

                // Stop the drone when the enemy is cloaking.
                if (target.ship.isCloakActive)
                    return 0

                // And when the system power is turned off - this is important as
                // it's how the defence drone bypass exploit works.
                // TODO add an option to disable this.
                if (powerSelected == 0)
                    return 0

                // Standard 16x multiplier for FTL time units to seconds.
                return blueprint.speed!! * 16
            }

        private val currentImage: Renderable
            get() = when {
                // The flying animation (or single frame, as it may be)
                !hasLanded -> flyAnimation

                // The animations once we land
                !landAnimation.isStopped -> landAnimation
                !extendAnimation.isStopped -> extendAnimation

                // Once we've landed, use the powered-up image if that's true
                powerSelected == 0 -> offImage
                else -> onImage
            }

        override fun renderPreTranslated(g: Graphics) {
            // 'up' in the sprite is forwards, rotate it so that is the case.
            g.rotate(0f, 0f, 90f)

            // Make sure the top of the image lines up with our true position, so
            // it doesn't clip into rooms.
            currentImage.draw(-(offImage.width / 2).f, -6f)

            // Draw on the flashing antenna lights, if appropriate
            if (currentImage == onImage) {
                // Flash with a period of one second
                var alpha = lightFlashTimer * 2
                if (alpha > 1)
                    alpha = 2 - alpha

                val colour = Color(1f, 1f, 1f, alpha)
                lightOverlay.draw(-(offImage.width / 2).f, -6f, colour)
            }
        }

        override fun update(dt: Float, currentSpace: Ship) {
            // Has something odd happened?
            if (this != this@Hacking.projectile) {
                dead = true
            }

            // Go away if the target is dying or the sender is dead.
            // TODO or if the target is playing the jump animation
            if (target.ship.isDead || !target.ship.sys.isShipPresent(this@Hacking.ship)) {
                dead = true
            }

            super.update(dt, currentSpace)

            // Update the currently-playing animation
            val image = currentImage
            if (image is FTLAnimation) {
                image.update(dt)
            }

            // Animate the flashing antenna lights
            if (image == onImage) {
                lightFlashTimer += dt
            } else {
                lightFlashTimer = 0f
            }
            if (lightFlashTimer > 1f)
                lightFlashTimer -= 1f
        }

        override fun reachedTarget() {
            // Turn into the permanent graphic, disabling collisions.
            hasLanded = true

            // Tell the system it's being hacked
            target.system?.hackedBy = this@Hacking

            // Update the start button size
            updateButton()

            // Play the landing sound
            landSound.play()
        }

        override fun hitOtherProjectile(currentSpace: Ship) {
            val explodeAnimation = currentSpace.sys.animations["explosion_random"]
            currentSpace.animations += Ship.FloatingAnimation.centred(explodeAnimation, position)
        }

        override fun onSwitchedToTarget() {
            super.onSwitchedToTarget()

            // Figure out where we'll be starting from
            val startPos = Point(
                (fromDirection.x * 1000).coerceIn(-400..800),
                (fromDirection.y * 1000).coerceIn(-400..800)
            )

            if (fromDirection.isVertical) {
                startPos.x = targetPos.x
            } else {
                startPos.y = targetPos.y
            }

            setInitialPath(startPos, targetPos)

            // Render above the target ship, otherwise you couldn't see
            // it once it lands.
            drawUnderShip = false
        }

        override fun calculateTargetPosition(): IPoint {
            val rooms = target.ship.rooms

            // See doc/hacking for the logic here.

            // This is the direction we'll be flying in
            val approachDirection = fromDirection.opposite

            val maxRoomX = rooms.map { it.x + it.width }.max()!!
            val maxRoomY = rooms.map { it.y + it.height }.max()!!
            val bounds = ConstPoint(maxRoomX, maxRoomY)

            // TODO move this into Room
            val targetCentre = ConstPoint(
                target.offsetX + ROOM_SIZE * target.width / 2,
                target.offsetY + ROOM_SIZE * target.height / 2
            )

            // Keep track of the best (closest to the target room)
            // position we've found.
            var bestDist = Int.MAX_VALUE
            var bestDestPos: ConstPoint? = null

            val destPixelPos = Point(0, 0)

            // This must be in bounds of maxRoomX/maxRoomY for
            // findLandingPoint to work. Note this is in room
            // coordinates, not pixel coordinates.
            val startPos = Point(0, 0)

            // Note that when we're moving in a vertical direction we scan
            // horizontally across the ship to find our landing position,
            // and vice versa.

            val iterBound: Int
            if (fromDirection.isVertical) {
                // We're coming in vertically, scan along the X axis
                startPos.y = if (fromDirection.y > 0) maxRoomY else 0
                iterBound = maxRoomX
            } else {
                startPos.x = if (fromDirection.x > 0) maxRoomX else 0
                iterBound = maxRoomY
            }

            for (i in 0..iterBound) {
                if (fromDirection.isVertical) {
                    startPos.x = i
                } else {
                    startPos.y = i
                }

                val (landingPos, room) = findLandingPoint(startPos, approachDirection, bounds) ?: continue

                // Find the top-left corner of the cell of the room we hit
                destPixelPos.x = ROOM_SIZE * (landingPos.x + target.ship.offset.x)
                destPixelPos.y = ROOM_SIZE * (landingPos.y + target.ship.offset.y)

                // Adjust to the centre of the position we hit it from - if
                // we were moving upwards, we'd hit it from below.
                if (fromDirection.isVertical) {
                    destPixelPos.x += ROOM_SIZE / 2
                    destPixelPos.y += ROOM_SIZE * max(0, fromDirection.y)
                } else {
                    destPixelPos.x += ROOM_SIZE * max(0, fromDirection.x)
                    destPixelPos.y += ROOM_SIZE / 2
                }

                // Immediately stop if we find the room directly
                if (room == target) {
                    return destPixelPos
                }

                // Note that we use the first available position if there's a tie.
                val dist = destPixelPos.distToSq(targetCentre)
                if (dist < bestDist) {
                    // Duplicate the position as we're changing it
                    bestDestPos = ConstPoint(destPixelPos)
                    bestDist = dist
                }
            }

            requireNotNull(bestDestPos) { "Couldn't find anywhere to land the drone on the target ship!" }
            return bestDestPos
        }

        private fun findLandingPoint(from: IPoint, movementDirection: Direction, bounds: IPoint): Pair<IPoint, Room>? {
            val rooms = target.ship.rooms

            val iter = Point(from)

            // Assume rooms can't have negative positions
            while (iter.x in 0..bounds.x && iter.y in 0..bounds.y) {
                val room = rooms.firstOrNull { it.containsAbsolute(iter) }

                if (room != null) {
                    return Pair(iter, room)
                }

                iter += movementDirection
            }

            return null
        }
    }

    companion object {
        const val NAME = "hacking"
    }
}
