package xyz.znix.xftl.crew

import org.newdawn.slick.Animation
import org.newdawn.slick.Color
import org.newdawn.slick.Graphics
import org.newdawn.slick.SpriteSheet
import xyz.znix.xftl.Animations
import xyz.znix.xftl.Constants.*
import xyz.znix.xftl.f
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.*
import xyz.znix.xftl.random
import xyz.znix.xftl.systems.Oxygen
import kotlin.math.*
import kotlin.random.Random

abstract class AbstractCrew(
    val blueprint: CrewBlueprint,
    private val anims: Animations,
    initialRoom: Room,
    mode: SlotType
) {
    val codename: String get() = blueprint.name

    var icon: Animation
    val backImg: SpriteSheet?

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
            if (currentAction == Action.MOVING || pathingTarget != null) {
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

            // In order to preserve the invariant that moving
            // implies a non-null nextTargetPos, immediately switch
            // to idle, even if we'll switch back next frame.
            if (value == null && currentAction == Action.MOVING) {
                currentAction = Action.IDLE
            }

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
        val anim = anims["${codename}_portrait"]
        icon = anim.start()
        backImg = anim.sheet.secondary
        updateAnimation()
    }

    open fun update(dt: Float) {
        icon.update((dt * 1000).toLong())

        if (room.oxygen < Oxygen.OXYGEN_CRITICAL_LEVEL) {
            dealDamage(6.4f * dt * suffocationMultiplier)
        }

        if (health == 0f) {
            if (!hasDyingAnimation) {
                removeFromShip()
                return
            }

            // For crew with dying animations, play that before removing them.
            currentAction = Action.DYING

            if (icon.isStopped)
                removeFromShip()

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
                val movement = min(distance, dt * BASE_MOVEMENT_SPEED)
                pixelSpaceX += directionX * movement
                pixelSpaceY += directionY * movement
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

            // Check if we've switched room. Use our centre position, so
            // we switch room when we appear half-way across.
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

        val computerPoint = room.computerPoint
        if (computerPoint != null && canManSystem && mode == SlotType.CREW) {
            if (room.computerPoint == roomPosition) {
                currentAction = Action.MANNING
                return
            }

            // Walk to the computer if no-one is occupying that slot.
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
                stretch = (icon.frameCount - icon.frame) * TELEPORT_IMAGE_STRETCH
                opacity *= progress
            }
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
        val opacityColour = Color(1f, 1f, 1f, opacity)
        cf.draw(x0, y0, x1, y1, 0f, 0f, cf.width.f, cf.height.f, opacityColour)
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

        // If we're being told to walk somewhere within this room, go straight there.
        if (currentTarget.room == room) {
            nextTargetPos = ConstPoint(currentTarget.offsetX, currentTarget.offsetY)
            return
        }

        // Rather than using roomPosition, recalculate it here from our centre.
        // This is because we might have our movement changed when we're already moving.
        val roomPos = findNearestRoomPos()

        val pf = room.ship.pathFinder
        pf.path(currentTarget)
        val current = pf.nodes.getValue(roomPos)
        val next = current.next!!.pos

        // TODO handle the case where we start walking through a door, and
        //  our target then changes.

        // If we're being told to walk to another position in the same room,
        // we can always do that regardless of if we're grid-aligned or not. But
        // if we're walking through a door, we have to get lined up first.
        if (next.room == room || roomPosition != null) {
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
            Action.IDLE -> {
                // Since the portrait doesn't have a background colour frame, use the top-left frame.
                // Drones need to use the correct image, though!
                val portrait = anims["${codename}_portrait"]
                if (backImg == null) {
                    portrait.start()
                } else {
                    Animation(portrait.sheet.sheet, 0, 0, 0, 0, true, 1, false)
                }
            }

            // Guard against movement being null, which could potentially
            // happen if the user cancels the movement while paused at the
            // perfect moment?
            Action.MOVING -> {
                val deltaX = nextTargetPos!!.x - pixelSpaceX
                val deltaY = nextTargetPos!!.y - pixelSpaceY
                anims["${codename}_walk_${dirAsString(Direction.bestFit(deltaX, deltaY))}"].start()
            }

            Action.MANNING -> anims["${codename}_type_${dirAsString(room.computerDirection!!)}"].start()
            Action.REPAIRING -> anims["${codename}_repair"].start()
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

                val icon = when (isPunching) {
                    true -> anims["${codename}_punch_${dirAsString(dir)}"].start()
                    false -> anims["${codename}_shoot_${dirAsString(dir)}"].start()
                }

                // We have to update the icon each time the punch timer expires,
                // so make it obvious if that isn't done.
                icon.setLooping(false)

                // Only use the first frame of shooting animations - this seems
                // a bit weird, but also seems to match FTL.
                if (!isPunching) {
                    icon.stop()
                }

                // Leave the animation at its default 1 second if
                // the attack timer isn't set, as the animation will
                // be updated again once it's set.
                if (attackTimer != null) {
                    val frameDurationMs = (attackTimer!! / icon.frameCount * 1000).toInt()
                    for (i in 0 until icon.frameCount)
                        icon.setDuration(i, frameDurationMs)
                }

                icon
            }

            Action.DYING -> anims["${codename}_death_right"].start().apply {
                // Don't loop, we'll disappear when the animation finishes
                setLooping(false)
            }

            Action.TELEPORTING -> {
                // If we're beaming back down on the destination ship, play it backwards.
                val backwards = teleportingTo?.ship == room.ship

                anims["${codename}_teleport"].start(1f, backwards).apply {
                    // Stop, as it would look weird if we restarted for a moment.
                    setLooping(false)
                }
            }
        }
    }

    fun setTargetRoom(value: Room): Boolean {
        if (value.computerPoint != null)
            if (setTargetRoom(value, value.computerPoint!!))
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
        // If we're already going to, or are at, this point then do nothing.
        if (pathingTarget?.room == value && pathingTarget!! posEq pos)
            return true
        if (room == value && standingPosition?.posEq(pos) == true)
            return true

        // If this slot isn't empty, we should skip it.
        // We should never be in this slot ourselves, the checks above should
        // filter out those cases.
        // This also checks for obstructed cells (eg, healer in the medbay)
        if (!value.isSlotFree(pos, mode))
            return false

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
    }

    companion object {
        const val TELEPORT_ANIMATION_TIME: Float = 0.5f
        const val TELEPORT_IMAGE_STRETCH: Float = 0.1f

        // See https://mikehopley.github.io/ftl-crew-speed/
        // This is in pixels per second, taken from the fastest
        // speed, converted to pixels and rounded from 79.8 to 80.
        const val BASE_MOVEMENT_SPEED: Float = 80f
    }
}
