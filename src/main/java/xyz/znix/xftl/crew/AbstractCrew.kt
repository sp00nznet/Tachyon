package xyz.znix.xftl.crew

import org.jdom2.Element
import org.newdawn.slick.Color
import org.newdawn.slick.Graphics
import org.newdawn.slick.Image
import org.newdawn.slick.SpriteSheet
import xyz.znix.xftl.*
import xyz.znix.xftl.Constants.*
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.*
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil
import xyz.znix.xftl.systems.Oxygen
import java.util.*
import kotlin.math.*
import kotlin.random.Random

abstract class AbstractCrew(
    val blueprint: CrewBlueprint,
    private val anims: Animations,
    initialRoom: Room,
    mode: SlotType
) {
    val codename: String get() = blueprint.name

    var icon: FTLAnimation
    val backImg: SpriteSheet? = anims["${codename}_portrait"].sheet.secondary
    val portraitAnim: AnimationSpec

    // This is used to avoid restarting the walking animation unless necessary.
    private var walkDirection: Direction? = null

    // When you set the room, be sure to call positionChanged!
    var room: Room = initialRoom
        private set

    // The position of the top-left corner of the 35x35 sprite relative
    // to the ship's 0,0 screen position (NOT relative to the room).
    // These are measured in pixels, NOT in units of 35x35 cells.
    private var pixelSpaceX: Float = room.offsetX.f
        set(value) {
            field = value
            positionChanged()
        }
    private var pixelSpaceY: Float = room.offsetY.f
        set(value) {
            field = value
            positionChanged()
        }
    private val pixelPosition = Point(0, 0)
    private val pixelPositionCentre = Point(0, 0)

    fun getPixelPositionCentre(): IPoint = pixelPositionCentre

    /**
     * If this crewmember is exactly aligned with a cell, this is non-null.
     *
     * Note that this being null does NOT mean the unit is not moving! It's
     * very unlikely - the position would have to be an exact int value - but
     * it's not impossible.
     *
     * You should usually use [standingPosition] instead, unless you have
     * a good reason. This is because this might be non-null when we are
     * attacking doors (or some future movement-like action), which this
     * won't account for.
     */
    private var roomPosition: RoomPoint? = null

    /**
     * Get [roomPosition], but null if this unit is moving (when doors
     * are implemented properly, this will also be null if the crewmember
     * is attacking a door).
     *
     * This is also null if we've been set to move, but haven't since updated.
     */
    val standingPosition: RoomPoint?
        get() {
            // Note that we have to check if nextTargetPos is non-null rather than
            // if currentAction is MOVING. This is because when we come to a stop,
            // currentAction isn't instantly changed - on the last update where we're
            // still set to moving, we'll end the update having completed our movement.
            // This would break the updateCrewReservedSlots system, since we'd
            // still count as moving and thus not occupy a slot.
            if (nextTargetPos != null || pathingTarget != null) {
                return null
            }
            return roomPosition
        }

    init {
        // Initialise pixelPosition, pixelPositionCentre, and roomPosition
        positionChanged()
    }

    @Suppress("LeakingThis")
    var health: Float = maxHealth
    open val maxHealth: Float get() = 100f

    open val canManSystem: Boolean get() = true
    open val repairSpeed: Float get() = 1f
    open val canPunch: Boolean get() = true
    open val canFight: Boolean get() = true
    open val attackDamageMult: Float get() = 1f
    open val hasDyingAnimation: Boolean get() = true
    open val suffocationMultiplier: Float get() = 1f
    open val playerControllable: Boolean get() = true
    open val movementSpeed: Float get() = BASE_MOVEMENT_SPEED

    var pathingTarget: RoomPoint? = null
        private set(value) {
            field = value

            updateMovement()

            // If we're leaving a room, reset it's reserved slots so we don't keep taking up space there
            // The way it works in FTL (and should here) is that as soon as a crewmember starts walking, their
            // space is free and another crewmember can start pathing there.
            //
            // Likewise, if we start pathing to a room, mark that slot as taken.
            //
            // This is done by recalculating all the reserved slots on the ship. Previously
            // we kept a manually-updated list, but when we were adding and removing
            // crew it was very easy for it to fall out-of-sync with reality.
            room.ship.updateCrewReservedSlots()
        }

    var nextTargetPos: IPoint? = null
        set(value) {
            field = value

            // The direction may have changed
            updateAnimation()
        }

    var mode: SlotType = mode
        set(value) {
            if (field == value)
                return
            field = value
            room.ship.updateCrewReservedSlots()
        }

    // If we're currently attacking someone, the time until our next
    // punch or shot is fired.
    private var attackTimer: Float? = null
    private var damageTime: Float = 0f // Damage is applied half-way through the attack animation
    private var enemyToAttack: AbstractCrew? = null
    private var isPunching: Boolean = false
    private var sharesHostileCell: Boolean = false

    private var teleportingTo: Room? = null
    private var teleportTimer: Float = 0f

    private var cloneAnimationTimer: Float = 0f

    val screenX: Int get() = pixelPosition.x
    val screenY: Int
        get() {
            val base = pixelPosition.y

            if (!sharesHostileCell)
                return base + 3

            // When we're fighting in the same cell as an enemy, displace both parties so
            // they don't fully overlap. Instead of 3 down, use 10 down or 4 up depending
            // on whether we're at the top or bottom.
            return when (mode) {
                SlotType.CREW -> base - 4
                SlotType.INTRUDER -> base + 10
            }
        }

    /**
     * The action this crewmember is currently performing.
     */
    var currentAction: Action = Action.IDLE
        private set(value) {
            val changed = field != value
            field = value
            if (changed)
                updateAnimation()
        }

    init {

        // Since the portrait doesn't have a background colour frame, use the top-left frame.
        // Drones need to use the correct image, though!
        val portrait = anims["${codename}_portrait"]
        portraitAnim = if (backImg == null) {
            portrait
        } else {
            val name = "$codename synthetic portrait animation"
            AnimationSpec(portrait.sheet, name, 0, 0, 1, 1f)
        }

        icon = portraitAnim.startLooping()

        updateAnimation()
    }

    open fun update(dt: Float) {
        icon.update(dt)

        if (room.oxygen < Oxygen.OXYGEN_CRITICAL_LEVEL) {
            dealDamage(6.4f * dt * suffocationMultiplier)
        }

        if (health == 0f) {
            if (!hasDyingAnimation) {
                onDied()
                return
            }

            // For crew with dying animations, play that before removing them.
            currentAction = Action.DYING

            if (icon.isStopped)
                onDied()

            return
        }

        // Playing the clone animation after being revived?
        if (cloneAnimationTimer > 0f) {
            cloneAnimationTimer = max(0f, cloneAnimationTimer - dt)
            currentAction = Action.CLONING
            return
        }

        if (teleportingTo != null) {
            currentAction = Action.TELEPORTING
            teleportTimer += dt

            // Run a timer matching the duration of the teleport animation, then:
            // 1. When the animation has fully played, move
            //    to the enemy ship and play it backwards.
            // 2. When that's done, the teleporting is finished.
            if (teleportTimer < TELEPORT_ANIMATION_TIME)
                return
            teleportTimer = 0f

            val destination = teleportingTo!!
            if (destination.ship != room.ship) {
                // Step 1

                // Clear out any movement, as the pathfinding system
                // is understandably unhappy about walking between ships.
                nextTargetPos = null
                pathingTarget = null

                // Before we swap modes, we have to remove ourselves
                // from the current ship - otherwise when we swap modes, we may
                // conflict with the other crew (both standing on the same cell)
                // and thus force someone to move.
                removeFromShip()

                // Flip between being an intruder and a crewmember
                mode = mode.other

                // Move to the other ship
                destination.ship.crew.add(this)

                // Change the room over, preserving our current position if possible.

                val roomPosition = standingPosition
                if (roomPosition != null && destination.containsRelative(roomPosition)
                    && destination.isSlotFree(roomPosition, mode)
                ) {
                    // The corresponding position in this new room is free
                    val newPosition = RoomPoint(destination, roomPosition)
                    pixelSpaceX = newPosition.offsetX.f
                    pixelSpaceY = newPosition.offsetY.f
                    room = destination
                } else {
                    // If our preferred location is unavailable, pick another
                    // spot (potentially in a different room, if required).
                    val free = destination.ship.findSpaceForCrew(destination, mode)
                    pixelSpaceX = free.offsetX.f
                    pixelSpaceY = free.offsetY.f
                    room = free.room
                }

                // Update our position to account for the room change
                positionChanged()

                // Mark this slot as now in use
                room.ship.updateCrewReservedSlots()

                updateAnimation()

                onMidTeleport()
            } else {
                // Step 2 - we're done
                teleportingTo = null
            }

            // When the teleportation finishes, immediately continue with
            // our regular stuff. This avoids the player animation breaking
            // from still having the teleport action set, but not being able
            // to check whether they're on the source or destination ship.
            if (teleportingTo != null)
                return
        }

        val nextTargetPos = this.nextTargetPos // Avoid mutability errors
        if (nextTargetPos != null) {
            currentAction = Action.MOVING

            val deltaX = nextTargetPos.x - pixelSpaceX
            val deltaY = nextTargetPos.y - pixelSpaceY

            val distance = sqrt(deltaX * deltaX + deltaY * deltaY)
            if (distance > 0.001f) {
                val directionX = deltaX / distance
                val directionY = deltaY / distance

                // Move closer towards our position
                val movement = min(distance, dt * movementSpeed)
                pixelSpaceX += directionX * movement
                pixelSpaceY += directionY * movement
            }

            // Check if we've switched room. Use our centre position, so
            // we switch room when we appear half-way across.
            // We must do this before updating our movement - if we're running
            // with a very high delta-time (for example, if we were stopped in
            // a debugger) then we'd be marked as being in the wrong room when
            // updateMovement was called.
            @Suppress("FoldInitializerAndIfToElvis")
            if (!room.containsShipSpace(pixelPositionCentre)) {
                val newRoom = room.ship.rooms.firstOrNull { it.containsShipSpace(pixelPositionCentre) }

                if (newRoom == null) {
                    throw IllegalStateException("Crewmember '$this' on ship '${room.ship.name}' walked into roomless position $pixelPosition")
                }

                room = newRoom

                // We should never be aligned to the grid while walking through
                // a door so we shouldn't have a roomPosition set, but check for
                // that just in case - if so, we need to update the room.
                positionChanged()
            }

            // Check if we've now reached our destination. If we're
            // very close, snap to the exact position.
            val newDistance = sqrt((nextTargetPos.x - pixelSpaceX).pow(2) + (nextTargetPos.y - pixelSpaceY).pow(2))
            if (newDistance < 0.01f) {
                pixelSpaceX = nextTargetPos.x.f
                pixelSpaceY = nextTargetPos.y.f

                this.nextTargetPos = null

                // TODO get stuck on locked doors

                // Calculate the next movement
                updateMovement()
            }

            // Check if a door needs to be held open for us.
            for (door in room.doors) {
                var centreX = door.offsetX
                var centreY = door.offsetY

                if (door.isVertical) {
                    centreY += ROOM_SIZE / 2
                } else {
                    centreX += ROOM_SIZE / 2
                }

                val distSq = pixelPositionCentre.distToSq(centreX, centreY)

                if (distSq < DOOR_OPEN_DISTANCE.pow(2)) {
                    door.crewRequestOpen()
                    break
                }
            }

            return
        }

        // If we're not centred but not moving, start walking to align ourselves to a cell.
        // This probably shouldn't happen, but check just in case.
        val roomPosition = this.roomPosition
        if (roomPosition == null) {
            this.nextTargetPos = pixelPositionCentre.divideTruncate(ROOM_SIZE.f) * ROOM_SIZE
            return
        }

        // Check if we're standing in the same cell as someone else.
        // This check is separate to the combat thing, since this
        // includes dying enemies (so their death animation is visible),
        // while the combat code doesn't (to avoid attacking
        // an already-dying enemy).
        sharesHostileCell = room.crew.any { it.mode != mode && it.standingPosition == roomPosition }

        // Check if any enemies are in the room
        val hostiles = room.crew.filter { it.mode != mode && it.currentAction != Action.DYING }
        if (hostiles.isNotEmpty() && canFight) {
            // Check if someone is standing in the same cell as us
            val sameCell = hostiles.firstOrNull { it.standingPosition == roomPosition }

            isPunching = sameCell != null && canPunch

            // Has the enemy walked out of the room?
            enemyToAttack?.let { target ->
                if (!hostiles.contains(target)) {
                    enemyToAttack = null
                    attackTimer = null
                }
            }

            updateAttack(Action.FIGHTING, dt, {
                // Apply damage
                val damage = (3f..7f).random(Random.Default) * attackDamageMult
                enemyToAttack!!.dealDamage(damage)
            }, {
                // If we're punching someone always attack them, otherwise pick
                // someone in the room at random to shoot.
                // For boarding drones that don't punch, still attack the person
                // in the same slot instead of sharing the damage around.
                enemyToAttack = sameCell ?: hostiles.random()
            })

            return
        }

        val system = room.system
        if (mode == SlotType.INTRUDER && system != null && !system.broken) {
            isPunching = false
            enemyToAttack = null

            updateAttack(Action.SABOTAGE, dt, {}, {})

            // Sabotage doesn't deal damage in bursts - it's constant 16% damage/second.
            // Note this is the same for any crew, regardless of the attack damage multiplier.
            system.attack(dt * 0.16f)

            return
        }

        // If we're not fighting, clear all the combat-related variables.
        attackTimer = null
        enemyToAttack = null
        isPunching = false


        // Check if the system in this room is broken, and if so repair it.
        system?.let { sys ->
            if (sys.damaged && mode == SlotType.CREW) {
                currentAction = Action.REPAIRING
                sys.repair(repairSpeed * dt / BASE_REPAIR_TIME)
                return
            }
        }

        val computerPoint = system?.configuration?.computerPoint
        if (computerPoint != null && canManSystem && mode == SlotType.CREW && system.hackedBy?.isPoweredUp != true) {
            if (computerPoint posEq roomPosition) {
                currentAction = Action.MANNING
                return
            }

            // Walk to the computer if no-one is occupying that slot.
            // (don't specify ourselves as the allow argument, as we don't want
            //  to constantly call setTargetRoom when we're standing there)
            if (room.isSlotFree(computerPoint, mode)) {
                setTargetRoom(room)
            }
        }

        // Nothing else is being done
        currentAction = Action.IDLE
    }

    open fun draw(g: Graphics) {
        // Bit of a hack, since we're drawn from the ship
        val isSelected = room.ship.sys.shipUI.isCrewSelected(this)

        // Intruders on the player ship and crew on the enemy ship are hostile.
        // This isn't very nice to read, but it gets the job done.
        val isHostileToPlayer = (mode == SlotType.INTRUDER) == (room.ship == room.ship.sys.player)

        val cf = icon.currentFrame

        var spriteY = screenY
        var opacity = 1f
        var stretch = 0f
        if (currentAction == Action.TELEPORTING) {
            // The sprite image (and not the health bar - hence why this isn't
            // built into screenY) moves up when teleporting, since it needs
            // to go further up on the screen.
            spriteY -= 8

            // The player fades out, and stretches upwards while teleporting.
            val progress = teleportTimer / TELEPORT_ANIMATION_TIME
            if (room.ship != teleportingTo?.ship) {
                // Teleporting up
                opacity *= 1 - progress
                stretch = icon.frame * TELEPORT_IMAGE_STRETCH
            } else {
                // Teleporting down (the 2nd half of the animation)
                stretch = icon.frame * TELEPORT_IMAGE_STRETCH
                opacity *= progress
            }
        }

        if (currentAction == Action.CLONING) {
            // Cloning has the same animation as the 2nd half of teleporting.
            // Note here though that cloneAnimationTimer goes down rather than up.
            val progress = 1 - cloneAnimationTimer / TELEPORT_ANIMATION_TIME
            spriteY -= 8
            stretch = icon.frame * TELEPORT_IMAGE_STRETCH
            opacity *= progress
        }

        // Calculate the bounds of the image
        val height = cf.height * (stretch + 1)
        val x0 = screenX.f
        val y0 = spriteY - stretch * cf.height
        val x1 = x0 + cf.width
        val y1 = y0 + height

        // Draw the background image - the coloured hint that changes
        // when you mouse over the crew.
        // This is missing for drones, which makes sense as you can't
        // select them.
        if (backImg != null) {
            val backSubImg = backImg.getSubImage(
                (cf.textureOffsetX * cf.texture.textureWidth).toInt(),
                (cf.textureOffsetY * cf.texture.textureHeight).toInt(),
                cf.width,
                cf.height
            )
            val backColour = when {
                isHostileToPlayer -> Color(CREW_HOSTILE_BG)
                isSelected -> Color(CREW_SELECTED_BG)
                else -> Color(CREW_DESELECTED_BG)
            }
            backColour.a *= opacity
            backSubImg.draw(x0, y0, x1, y1, 0f, 0f, cf.width.f, cf.height.f, backColour)
        }

        // Draw the actual image
        // This is moved into its own function so that living crew with layered
        // rendering can apply their different colour filters.
        drawImage(x0, y0, x1, y1, cf, opacity)
    }

    fun drawPortrait(x: Int, y: Int, scale: Float = 1f) {
        val img = portraitAnim.spriteAt(0)
        drawImage(
            x.f, y.f,
            x.f + img.width * scale, y.f + img.height * scale,
            img, 1f
        )
    }

    protected open fun drawImage(
        // The corners of the image as it should be drawn on-screen.
        // This is stretched for the teleport animation.
        x0: Float, y0: Float,
        x1: Float, y1: Float,

        // The image to be drawn, from the base sprite sheet.
        baseFrame: Image,

        alpha: Float
    ) {
        // Use nearest filtering so the image looks good when scaled up
        // in the crew management screen.
        baseFrame.filter = Image.FILTER_NEAREST
        baseFrame.alpha = alpha
        baseFrame.draw(x0, y0, x1, y1, 0f, 0f, baseFrame.width.f, baseFrame.height.f)
        baseFrame.alpha = 1f
    }

    fun drawForeground(g: Graphics) {
        // It should already be pretty obvious what their health is...
        if (currentAction == Action.DYING)
            return

        val isSelected = room.ship.sys.shipUI.isCrewSelected(this)

        // Draw the health bar
        if (health < maxHealth || isSelected) {
            val healthBox = room.ship.sys.getImg("img/people/health_box.png")
            healthBox.draw(screenX - 1f, screenY.f)

            val width = ceil(25f * health / maxHealth).toInt()
            g.color = Color.green
            g.fillRect(screenX + 4f, screenY + 3f, width.f, 3f)
        }
    }

    fun dealDamage(damage: Float) {
        health = max(0f, health - damage)

        // Dying is handled in the update loop
    }

    /**
     * Update the attack animation for fighting and sabotage actions.
     */
    private fun updateAttack(action: Action, dt: Float, dealDamage: () -> Unit, onAnimationStart: () -> Unit) {
        // Reset everything if we switch between attacking and sabotaging
        // Otherwise we could end up doing damage after we started attacking
        // a system, and have a null enemy we're shooting at.
        if (currentAction != action) {
            attackTimer = null
            enemyToAttack = null
            isPunching = false
        }
        currentAction = action

        if (attackTimer != null) {
            attackTimer = attackTimer!! - dt
        }

        if (attackTimer != null && attackTimer!! <= damageTime) {
            damageTime = -1f

            dealDamage()

            // TODO if shooting, play the little laser graphic animation
        }

        if (attackTimer == null || attackTimer!! <= 0f) {
            attackTimer = (1.0f..1.3f).random(Random.Default)

            // Apply the damage half-way through the animation
            damageTime = attackTimer!! / 2f

            onAnimationStart()

            // Restart the fighting animation, and set its duration equal to that of the attack.
            updateAnimation()
        }
    }

    /**
     * Recalculate (via pathfinding) the player's current [nextTargetPos] to get to
     * their desired target.
     */
    private fun updateMovement() {
        val currentTarget = pathingTarget ?: return

        // It's fine to compare floats, since we snap into position.
        if (currentTarget.offsetX.f == pixelSpaceX && currentTarget.offsetY.f == pixelSpaceY) {
            pathingTarget = null
            return
        }

        // Rather than using roomPosition, recalculate it here from our centre.
        // This is because we might have our movement changed when we're already moving.
        val roomPos = findNearestRoomPos()

        // If we're already in the same cell as our target, we can safely walk there.
        // Using the pathfinder wouldn't do anything useful - indeed, we'd get
        // a null pointer exception since [next] would be null.
        if (roomPos == currentTarget) {
            nextTargetPos = ConstPoint(roomPos.offsetX, roomPos.offsetY)
            return
        }

        val pf = room.ship.pathFinder
        pf.path(currentTarget)
        val current = pf.nodes.getValue(roomPos)
        val next = current.next!!.pos

        // Figure out if we're in a doorway. We'll use this to prevent
        // ourselves from visually walking through a wall if we were
        // in the process of walking through a door (but our centre
        // point hasn't crossed yet) and our target is then changed.
        // If we didn't use this, we'd walk directly to our target
        // even if that meant sliding through a wall.
        // TODO make this more accurate when we figure out how close
        //  a crewmember can get to a locked door.
        val isInDoorway = pixelPosition.x - room.offsetX !in 0..(room.width - 1) * ROOM_SIZE ||
                pixelPosition.y - room.offsetY !in 0..(room.height - 1) * ROOM_SIZE

        // If we're being told to walk to another position in the same room,
        // we can usually do that regardless of if we're grid-aligned or not. But
        // if we're walking through a door, we have to get lined up first.
        if ((next.room == room || roomPosition != null) && !isInDoorway) {
            nextTargetPos = ConstPoint(next.offsetX, next.offsetY)
        } else {
            nextTargetPos = ConstPoint(roomPos.offsetX, roomPos.offsetY)
        }
    }

    private fun dirAsString(dir: Direction): String {
        return when (dir) {
            Direction.UP -> "up"
            Direction.DOWN -> "down"
            Direction.LEFT -> "left"
            Direction.RIGHT -> "right"

            Direction.RIGHT_DOWN -> "right"
            Direction.LEFT_UP -> "up"
            Direction.DOWN_LEFT -> "left"
            Direction.UP_RIGHT -> "up"
        }
    }

    protected fun updateAnimation() {
        icon = when (currentAction) {
            Action.IDLE -> portraitAnim.startLooping()

            Action.MOVING -> {
                // We can have a null targetPos for a short amount of time, while
                // changing as we reach a target position before it's reset.
                val target = nextTargetPos ?: return

                val deltaX = target.x - pixelSpaceX
                val deltaY = target.y - pixelSpaceY
                val direction = Direction.bestFit(deltaX, deltaY)

                // If we're already using the right animation, don't restart it.
                if (walkDirection == direction)
                    return
                walkDirection = direction

                anims["${codename}_walk_${dirAsString(direction)}"].startLooping()
            }

            Action.MANNING -> anims["${codename}_type_${dirAsString(room.system!!.configuration.computerDirection!!)}"].startLooping()
            Action.REPAIRING -> anims["${codename}_repair"].startLooping()
            Action.FIGHTING, Action.SABOTAGE -> {
                // Figure out the direction.
                val dir: Direction = when {
                    // When sabotaging, boarders in a two-cell room face the obvious
                    // centre, and in a four-cell room the top two point down, the
                    // bottom-left boarder faces right, and the bottom-right one
                    // faces left.
                    currentAction == Action.SABOTAGE -> when {
                        room.width == 1 && roomPosition!!.y == 0 -> Direction.DOWN
                        room.width == 1 && roomPosition!!.y == 1 -> Direction.UP

                        room.height == 1 && roomPosition!!.x == 0 -> Direction.RIGHT
                        room.height == 1 && roomPosition!!.x == 1 -> Direction.LEFT

                        roomPosition!!.y == 0 -> Direction.DOWN
                        roomPosition!!.x == 0 -> Direction.RIGHT
                        else -> Direction.UP
                    }

                    // If two parties are in the same cell, the crewmember stands
                    // at the top and the intruder stands at the bottom.
                    sharesHostileCell && mode == SlotType.CREW -> Direction.DOWN
                    sharesHostileCell && mode == SlotType.INTRUDER -> Direction.UP

                    // When combat is starting, we might not have the enemy selected yet.
                    enemyToAttack == null -> Direction.UP

                    // Otherwise we're standing in the open, shooting at someone.
                    else -> Direction.bestFit(pixelPosition, enemyToAttack!!.pixelPosition)
                }

                // We have to update the icon each time the punch timer expires,
                // so make it obvious if that isn't done by not looping.
                val icon = when (isPunching) {
                    true -> anims["${codename}_punch_${dirAsString(dir)}"].startSingle()
                    false -> anims["${codename}_shoot_${dirAsString(dir)}"].startSingle()
                }

                // Only use the first frame of shooting animations - this seems
                // a bit weird, but also seems to match FTL.
                if (!isPunching) {
                    icon.isPaused = true
                }

                // Leave the animation at its default 1 second if
                // the attack timer isn't set, as the animation will
                // be updated again once it's set.
                if (attackTimer != null) {
                    icon.duration = attackTimer!!
                }

                icon
            }

            // Don't loop, we'll disappear when the animation finishes
            Action.DYING -> anims["${codename}_death_right"].startSingle()

            Action.TELEPORTING -> {
                // If we're beaming back down on the destination ship, play it backwards.
                val backwards = teleportingTo?.ship == room.ship

                // Stop at the end, as it would look weird if we restarted for a moment.
                anims["${codename}_teleport"].startSingle(1f, backwards)
            }

            Action.CLONING -> {
                // This just re-uses the teleport animation
                anims["${codename}_teleport"].startSingle(1f, true)
            }
        }

        if (currentAction != Action.MOVING) {
            walkDirection = null
        }
    }

    fun setTargetRoom(value: Room): Boolean {
        val computerPoint = value.system?.configuration?.computerPoint
        if (computerPoint != null)
            if (setTargetRoom(value, computerPoint))
                return true

        // If we're being told to go to our current room, see if our
        // current position is still valid. This might not be the case
        // if this was called by updateCrewReservedSlots.
        val currentPos = standingPosition
        if (value == room && currentPos != null) {
            if (setTargetRoom(room, currentPos)) {
                return true
            }
        }

        val freeSlot = value.firstFreeSlot(mode)
        if (freeSlot != null) {
            if (setTargetRoom(value, freeSlot)) {
                return true
            }
        }

        return false
    }

    private fun setTargetRoom(value: Room, pos: IPoint): Boolean {
        // Check if the slot is free. We have to do this *before* checking
        // if we're already pathing to or in the slot.
        // This only matters if we're not marked as occupying this slot - this
        // should never be the case, but if two crew walk into the same
        // slot somehow it will happen while the conflict is being resolved.
        if (!value.isSlotFree(pos, mode, this))
            return false

        // If we're already going to, or are at, this point then do nothing.
        if (pathingTarget?.room == value && pathingTarget!! posEq pos)
            return true
        if (room == value && standingPosition?.posEq(pos) == true)
            return true

        // Verify we have a path to this room
        val pf = room.ship.pathFinder
        pf.path(RoomPoint(value, ConstPoint.ZERO))
        val pNode = pf.nodes.getValue(findNearestRoomPos())
        if (pNode.next == null && value != room)
            return false

        pathingTarget = RoomPoint(value, pos)
        return true
    }

    fun jumpTo(newPoint: RoomPoint): Unit = jumpTo(newPoint.room, newPoint)

    /**
     * Set the room and position to the given values.
     *
     * This ensures movement is updated appropriately and only once.
     *
     * [newPoint] is given in units of cells, not pixels.
     */
    fun jumpTo(newRoom: Room, newPoint: IPoint) {
        room = newRoom
        pixelSpaceX = newRoom.offsetX.f + newPoint.x * ROOM_SIZE
        pixelSpaceY = newRoom.offsetY.f + newPoint.y * ROOM_SIZE

        // This has already been done when we updated the pixel positions,
        // but make it explicit since we changed rooms.
        positionChanged()

        updateMovement()
        updateAnimation()

        // Unless we're moving (but even then do it just to be safe),
        // we're now occupying a different slot. Thus update them all.
        room.ship.updateCrewReservedSlots()
    }

    /**
     * Immediately remove this crew from the ship. This is run after
     * the dying animation finishes.
     */
    open fun removeFromShip() {
        room.ship.crew.remove(this)
        room.ship.updateCrewReservedSlots()
    }

    open fun onDied() {
        removeFromShip()
    }

    open fun onCloned() {
        health = maxHealth

        // Play the teleport animation after cloning.
        cloneAnimationTimer = TELEPORT_ANIMATION_TIME

        // Clear out movement for the same reason as when teleporting - we might
        // be respawning on a different ship than the one we were killed on.
        // In any case, in vanilla crew don't run back to the place they died.
        nextTargetPos = null
        pathingTarget = null
    }

    /**
     * Teleport to a room on an enemy ship, playing the teleport animation.
     */
    fun teleportAnimatedTo(room: Room) {
        // Make sure we're teleporting to another ship
        require(room.ship != this.room.ship)

        teleportingTo = room
        teleportTimer = 0f
    }

    /**
     * A hook function for subclasses, called just after the crew swaps
     * between the two ships.
     */
    protected open fun onMidTeleport() {
    }

    /**
     * Find the RoomPos that best fits our current pixel position.
     *
     * This is distinct from [roomPosition], which is null unless
     * we're exactly pixel-aligned.
     */
    fun findNearestRoomPos(): RoomPoint {
        return RoomPoint(
            room,
            (pixelPositionCentre.x - room.offsetX) / ROOM_SIZE,
            (pixelPositionCentre.y - room.offsetY) / ROOM_SIZE
        )
    }

    private fun positionChanged() {
        pixelPosition.set(pixelSpaceX.toInt(), pixelSpaceY.toInt())
        pixelPositionCentre.x = pixelPosition.x + ROOM_SIZE / 2
        pixelPositionCentre.y = pixelPosition.y + ROOM_SIZE / 2

        // Check we're pixel-aligned. Being exact about floats is fine
        // since we snap into position anyway.
        if (pixelSpaceX.toInt().toFloat() != pixelSpaceX || pixelSpaceY.toInt().toFloat() != pixelSpaceY) {
            roomPosition = null
            return
        }

        // Calculate our cell-aligned position, if we are cell-aligned.
        val roomPosX = pixelPosition.x - room.offsetX
        val roomPosY = pixelPosition.y - room.offsetY
        if (roomPosX % ROOM_SIZE == 0 && roomPosY % ROOM_SIZE == 0) {
            val cellX = roomPosX / ROOM_SIZE
            val cellY = roomPosY / ROOM_SIZE

            // Only update the position if it has changed
            val currentPos = roomPosition
            if (currentPos == null || currentPos.x != cellX || currentPos.y != cellY || currentPos.room != room) {
                roomPosition = RoomPoint(room, cellX, cellY)
            }
        }
    }

    open fun saveToXML(elem: Element, refs: ObjectRefs) {
        SaveUtil.addObjectId(elem, refs, this)
        elem.setAttribute("type", codename)

        // We can't use the normal point serialisation since
        // it doesn't support floating-point values.
        SaveUtil.addAttrFloat(elem, "x", pixelSpaceX)
        SaveUtil.addAttrFloat(elem, "y", pixelSpaceY)

        SaveUtil.addAttrFloat(elem, "health", health)
        SaveUtil.addAttr(elem, "mode", mode.name)
        SaveUtil.addAttr(elem, "action", currentAction.name)

        if (pathingTarget != null) {
            SaveUtil.addRoomPoint(elem, "pathingTarget", pathingTarget!!)
        }

        if (nextTargetPos != null) {
            SaveUtil.addPoint(elem, "nextTargetPos", nextTargetPos!!)
        }

        // Combat stuff
        if (currentAction == Action.FIGHTING || currentAction == Action.SABOTAGE) {
            val combat = Element("combat")
            combat.setAttribute("attackTimer", Objects.toString(attackTimer))
            combat.setAttribute("damageTimer", damageTime.toString())
            combat.setAttribute("enemy", refs[enemyToAttack])
            // isPunching and sharesHostileCell are only used for animations
            elem.addContent(combat)
        }

        if (teleportingTo != null) {
            val teleport = Element("teleporting")
            teleport.setAttribute("destRoom", teleportingTo!!.id.toString())
            teleport.setAttribute("destShip", refs[teleportingTo!!.ship])
            teleport.setAttribute("timer", teleportTimer.toString())
            elem.addContent(teleport)
        }

        // Serialise the animation progress, so we can use it for stuff like
        // checking if the dying animation has finished.
        SaveUtil.addAttrFloat(elem, "animTimer", icon.timer)
    }

    open fun loadFromXML(elem: Element, refs: RefLoader) {
        SaveUtil.registerObjectId(elem, refs, this)

        // The 'type' property is already read by the class that spawns us,
        // we just need to check it.
        require(codename == elem.getAttributeValue("type")) { "Wrong crewmember type was spawned!" }

        pixelSpaceX = SaveUtil.getAttrFloat(elem, "x")
        pixelSpaceY = SaveUtil.getAttrFloat(elem, "y")

        // Correctly set what room we're in - as soon as we start setting
        // target positions and stuff like that, we'll be updating the
        // occupied cells which needs to know what room we're in.
        room = room.ship.rooms.first { it.containsShipSpace(pixelPositionCentre) }
        positionChanged()

        health = SaveUtil.getAttrFloat(elem, "health")
        mode = SlotType.valueOf(SaveUtil.getAttr(elem, "mode"))
        currentAction = Action.valueOf(SaveUtil.getAttr(elem, "action"))

        if (elem.getChild("pathingTarget") != null) {
            pathingTarget = SaveUtil.getRoomPoint(elem, "pathingTarget", room.ship)
        } else {
            // This might be required if we're spawned into the wrong
            // room, while we're being created but before being deserialised.
            pathingTarget = null
        }

        // Have to deserialise this after pathingTarget, since setting
        // that overwrites it.
        if (elem.getChild("nextTargetPos") != null) {
            nextTargetPos = SaveUtil.getPoint(elem, "nextTargetPos")
        } else {
            nextTargetPos = null
        }

        // Combat stuff
        val combat = elem.getChild("combat")
        if (currentAction == Action.FIGHTING || currentAction == Action.SABOTAGE) {
            attackTimer = combat.getAttributeValue("attackTimer")?.toFloatOrNull()
            damageTime = combat.getAttributeValue("damageTimer").toFloat()
            val enemyId = combat.getAttributeValue("enemy")
            refs.asyncResolve(AbstractCrew::class.java, enemyId) { enemyToAttack = it }
        }

        val teleport = elem.getChild("teleporting")
        if (teleport != null) {
            val roomId = teleport.getAttributeValue("destRoom").toInt()
            val shipRef = teleport.getAttributeValue("destShip")
            refs.asyncResolve(Ship::class.java, shipRef) { teleportingTo = it!!.rooms[roomId] }
            teleportTimer = teleport.getAttributeValue("timer").toFloat()
        }

        // Load this *after* currentAction is set, so the timer doesn't get
        // overwritten by updating our icon.
        icon.timer = SaveUtil.getAttrFloat(elem, "animTimer")
    }

    enum class SlotType {
        CREW,
        INTRUDER;

        val other
            get() = when (this) {
                CREW -> INTRUDER
                INTRUDER -> CREW
            }
    }

    enum class Action {
        IDLE,
        MOVING,
        MANNING, // Working at a computer
        REPAIRING,
        FIGHTING,
        SABOTAGE,
        TELEPORTING,
        DYING,
        CLONING, // Playing the animation after being cloned
    }

    companion object {
        const val TELEPORT_ANIMATION_TIME: Float = 0.5f
        const val TELEPORT_IMAGE_STRETCH: Float = 0.1f

        // See https://mikehopley.github.io/ftl-crew-speed/
        // This is in pixels per second, taken from the fastest
        // speed, converted to pixels and rounded from 79.8 to 80.
        const val BASE_MOVEMENT_SPEED: Float = 80f

        const val DOOR_OPEN_DISTANCE: Float = ROOM_SIZE / 2f - 2f
    }
}
