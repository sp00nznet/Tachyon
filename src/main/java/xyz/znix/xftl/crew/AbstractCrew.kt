package xyz.znix.xftl.crew

import org.newdawn.slick.Animation
import org.newdawn.slick.Color
import org.newdawn.slick.Graphics
import org.newdawn.slick.SpriteSheet
import xyz.znix.xftl.Animations
import xyz.znix.xftl.Constants.*
import xyz.znix.xftl.f
import xyz.znix.xftl.layout.Door
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.*
import xyz.znix.xftl.random
import xyz.znix.xftl.systems.Oxygen
import kotlin.math.ceil
import kotlin.math.max
import kotlin.random.Random

abstract class AbstractCrew(
    val blueprint: CrewBlueprint,
    private val anims: Animations,
    var room: Room,
    mode: SlotType
) {
    val codename: String get() = blueprint.name

    var icon: Animation
    val backImg: SpriteSheet?

    // The cell position in the current room
    private var positionInternal = Point(0, 0)
    val position: IPoint get() = positionInternal

    val roomPosition: RoomPoint get() = RoomPoint(room, position)

    @Suppress("LeakingThis")
    var health: Float = maxHealth
    open val maxHealth: Float get() = 100f

    open val canManSystem: Boolean get() = true
    open val repairSpeed: Float get() = 1f
    open val canPunch: Boolean get() = true
    open val canFight: Boolean get() = true
    open val attackDamageMult: Float get() = 1f
    open val hasDyingAnimation: Boolean get() = true
    open val canSuffocate: Boolean get() = true
    open val playerControllable: Boolean get() = true

    var pathingTarget: RoomPoint? = null
        private set(value) {
            val old = field
            field = value
            if (old == null)
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

    var movement: Direction? = null
        set(value) {
            field = value

            // If we're stopping but are targeting to walk through a door, this will be handled for us, as
            // we're currently in an invalid position
            if (value != null || targetDoor == null)
                updateAnimation()

            if (value == null)
                return

            val target = positionInternal.copy()
            target += value

            if (room.containsRelative(target))
                return

            require(!value.isDiagonal) { "Cannot move diagonally through a doorway" }

            targetDoor = null
            for (door in room.doors) {
                // note can only walk horizontally through a vertical doorway and vice versa, since horizontal
                // and vertical refer to the door's orientation on the player's screen, not the direction
                // crew can travel through it
                if (door.roomPos(room) posEq position && door.dirFor(room) == value) {
                    targetDoor = door
                    break
                }
            }

            requireNotNull(targetDoor) { "Cannot walk outside room" }

            requireNotNull(targetDoor!!.other(room)) { "Cannot walk through airlock door" }
        }

    var mode: SlotType = mode
        set(value) {
            if (field == value)
                return
            field = value
            room.ship.updateCrewReservedSlots()
        }

    var movementProgress: Float = 0F
        private set

    // If we're currently attacking someone, the time until our next
    // punch or shot is fired.
    private var attackTimer: Float? = null
    private var damageTime: Float = 0f // Damage is applied half-way through the attack animation
    private var enemyToAttack: AbstractCrew? = null
    private var isPunching: Boolean = false
    private var sharesHostileCell: Boolean = false

    // The door we're walking through
    private var targetDoor: Door? = null

    private var teleportingTo: Room? = null
    private var teleportTimer: Float = 0f

    val screenX: Int get() = room.offsetX + ((position.x + movementOffsetX) * ROOM_SIZE).toInt()
    val screenY: Int
        get() {
            val base = room.offsetY + ((position.y + movementOffsetY) * ROOM_SIZE).toInt()

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

    val movementOffsetX: Float get() = movement?.x?.times(movementProgress) ?: 0f
    val movementOffsetY: Float get() = movement?.y?.times(movementProgress) ?: 0f

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

        if (room.oxygen < Oxygen.OXYGEN_CRITICAL_LEVEL && canSuffocate) {
            dealDamage(6.4f * dt)
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
                movement = null
                movementProgress = 0f
                pathingTarget = null
                targetDoor = null

                // Flip between being an intruder and a crewmember
                mode = mode.other

                // Move to the other ship
                removeFromShip()
                destination.ship.crew.add(this)

                // Change the room over, preserving our current position if possible

                if (destination.containsRelative(position) && destination.isSlotFree(position, mode)) {
                    // The corresponding position in this new room is free
                    room = destination
                } else {
                    // If our preferred location is unavailable, pick another
                    // spot (potentially in a different room, if required).
                    val free = destination.ship.findSpaceForCrew(destination, mode)
                    positionInternal.set(free)
                    room = free.room
                }

                // Mark this slot as now in use
                room.ship.updateCrewReservedSlots()

                updateAnimation()
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

        val pos = positionInternal
        if (movement != null) {
            currentAction = Action.MOVING
            movementProgress += dt * 2

            if (movementProgress > 1) {
                movementProgress = 0f
                pos += movement!!
                movement = null
                currentAction = Action.IDLE

                // If we've walked through a doorway, switch over
                if (targetDoor != null) {
                    val newRoom = targetDoor!!.other(room)!!

                    // Move our position into the new room
                    pos += room.position
                    pos -= newRoom.position

                    check(newRoom.containsRelative(pos))

                    room = newRoom
                    targetDoor = null
                }

                // Calculate the next movement
                updateMovement()
            }
            return
        }

        // Check if we're standing in the same cell as someone else.
        // This check is separate to the combat thing, since this
        // includes dying enemies (so their death animation is visible),
        // while the combat code doesn't (to avoid attacking
        // an already-dying enemy).
        sharesHostileCell = room.crew.any { it.mode != mode && it.movement == null && it.position == position }

        // Check if any enemies are in the room
        val hostiles = room.crew.filter { it.mode != mode && it.currentAction != Action.DYING }
        if (hostiles.isNotEmpty() && canFight) {
            // Check if someone is standing in the same cell as us
            val sameCell = hostiles.firstOrNull { it.movement == null && it.position == position }

            isPunching = sameCell != null && canPunch

            currentAction = Action.FIGHTING

            // Has the enemy walked out of the room?
            enemyToAttack?.let { target ->
                if (!hostiles.contains(target)) {
                    enemyToAttack = null
                    attackTimer = null
                }
            }

            if (attackTimer != null) {
                attackTimer = attackTimer!! - dt
            }

            if (attackTimer != null && attackTimer!! <= damageTime) {
                damageTime = -1f

                // Apply damage
                val damage = (3f..7f).random(Random.Default) * attackDamageMult
                enemyToAttack!!.dealDamage(damage)

                // TODO if shooting, play the little laser graphic animation
            }

            if (attackTimer == null || attackTimer!! <= 0f) {
                // If we're punching someone always attack them, otherwise pick
                // someone in the room at random to shoot.
                // For boarding drones that don't punch, still attack the person
                // in the same slot instead of sharing the damage around.
                enemyToAttack = sameCell ?: hostiles.random()

                attackTimer = (1.0f..1.3f).random(Random.Default)

                // Apply the damage half-way through the animation
                damageTime = attackTimer!! / 2f

                // Restart the fighting animation, and set its duration equal to that of the attack.
                updateAnimation()
            }

            return
        }

        // If we're not fighting, clear all the combat-related variables.
        attackTimer = null
        enemyToAttack = null
        isPunching = false


        // Check if the system in this room is broken, and if so repair it.
        room.system?.let { sys ->
            if (sys.damaged && mode == SlotType.CREW) {
                currentAction = Action.REPAIRING
                sys.repair(repairSpeed * dt / BASE_REPAIR_TIME)
                return
            }
        }

        val computerPoint = room.computerPoint
        if (computerPoint != null && canManSystem && mode == SlotType.CREW) {
            if (room.computerPoint == position) {
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
     * Recalculate (via pathfinding) the player's current [movement] to get to
     * their desired target.
     */
    private fun updateMovement() {
        if (pathingTarget == roomPosition) {
            pathingTarget = null
            movement = null
        }

        val immPt = pathingTarget ?: return

        val pf = room.ship.pathFinder
        pf.path(immPt)
        val current = pf.nodes.getValue(roomPosition)
        val tgt = current.next!!
        movement = Direction.fromPoint(tgt.pos.shipPoint - roomPosition.shipPoint)
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
            Action.MOVING -> anims["${codename}_walk_${dirAsString(movement ?: Direction.UP)}"].start()

            Action.MANNING -> anims["${codename}_type_${dirAsString(room.computerDirection!!)}"].start()
            Action.REPAIRING -> anims["${codename}_repair"].start()
            Action.FIGHTING -> {
                // Figure out the direction.
                val dir: Direction = when {
                    // If two parties are in the same cell, the crewmember stands
                    // at the top and the intruder stands at the bottom.
                    sharesHostileCell && mode == SlotType.CREW -> Direction.DOWN
                    sharesHostileCell && mode == SlotType.INTRUDER -> Direction.UP

                    // When combat is starting, we might not have the enemy selected yet.
                    enemyToAttack == null -> Direction.UP

                    // Otherwise we're standing in the open, shooting at someone.
                    else -> Direction.fromPoint(enemyToAttack!!.position - position) ?: Direction.UP
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

        if (value == room)
            return true

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
        if (pathingTarget?.room == value && pathingTarget!! == pos)
            return true
        if (pathingTarget == null && room == value && position == pos)
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
        val pNode = pf.nodes.getValue(roomPosition)
        if (pNode.next == null && value != room)
            return false

        pathingTarget = RoomPoint(value, pos)
        return true
    }

    /**
     * Set the room and position to the given values.
     *
     * This ensures movement is updated appropriately and only once.
     */
    fun jumpTo(newRoom: Room, newPoint: IPoint) {
        room = newRoom
        positionInternal.set(newPoint)
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
        TELEPORTING,
        DYING,
    }

    companion object {
        const val TELEPORT_ANIMATION_TIME: Float = 0.5f
        const val TELEPORT_IMAGE_STRETCH: Float = 0.1f
    }
}
