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
import kotlin.math.ceil
import kotlin.math.max
import kotlin.random.Random

abstract class AbstractCrew(
    val codename: String,
    private val anims: Animations,
    var room: Room,
    mode: SlotType
) {
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
    open val attackDamageMult: Float get() = 1f
    open val hasDyingAnimation: Boolean get() = true

    var pathingTarget: RoomPoint? = null
        private set(value) {
            val old = field
            field = value
            if (old == null)
                updateMovement()

            // If we're leaving a room, reset it's reserved slots so we don't keep taking up space there
            // The way it works in FTL (and should here) is that as soon as a crewmember starts walking, their
            // space is free and another crewmember can start pathing there
            //
            // Likewise, if we start pathing to a room, mark that slot as taken

            // Which room are we coming from. If we were stationary then it's our current room, if we changed
            // destination then it was our previous destination.
            val from = old ?: roomPosition

            // Even if we're becoming stationary, free up the space - we'll fill it in later
            clearFromRoomSlots(slotsFor(from.room))

            // Now mark the slot we're taking up
            val dest = value ?: roomPosition
            val slot = dest.room.pointToSlot(dest)
            val slots = slotsFor(dest.room)
            check(slots[slot] == null)
            slots[slot] = this
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
            field = value // Suppress warning
            TODO()
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

    val screenX: Int get() = room.offsetX + ((position.x + movementOffsetX) * ROOM_SIZE).toInt()
    val screenY: Int
        get() {
            val base = room.offsetY + ((position.y + movementOffsetY) * ROOM_SIZE).toInt()

            if (currentAction != Action.FIGHTING || !sharesHostileCell)
                return base + 3

            // When we're fighting in the same cell as an enemy, displace both parties so
            // they don't fully overlap. Instead of 3 down, use 10 down or 2 (maybe 3?) up depending
            // on whether we're at the top or bottom.
            return when (mode) {
                SlotType.CREW -> base - 2
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

    fun slotsFor(room: Room) = mode.slotsFor(room)

    open fun update(dt: Float) {
        icon.update((dt * 1000).toLong())

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

        // Check if any enemies are in the room
        // FIXME attack enemies that are just passing through the room, which
        //  don't have a reserved slot.
        val hostiles = mode.other.slotsFor(room).filterNotNull().filter { it.room == room }
        if (hostiles.isNotEmpty()) {
            // Check if someone is standing in the same cell as us
            val sameCell = hostiles.firstOrNull { it.movement == null && it.position == position }

            isPunching = sameCell != null && canPunch
            sharesHostileCell = sameCell != null

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
        sharesHostileCell = false


        // Check if the system in this room is broken, and if so repair it.
        room.system?.let { sys ->
            if (sys.damaged) {
                currentAction = Action.REPAIRING
                sys.repair(repairSpeed * dt / BASE_REPAIR_TIME)
                return
            }
        }

        val computerPoint = room.computerPoint?.let(room::pointToSlot)
        if (computerPoint != null && canManSystem) {
            if (room.computerPoint == position) {
                currentAction = Action.MANNING
                return
            }

            // Walk to the computer if no-one is occupying that slot.
            if (room.reservedPlayerSlots[computerPoint] == null) {
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
                isSelected -> CREW_SELECTED_BG
                else -> CREW_DESELECTED_BG
            }
            backSubImg.draw(screenX.f, screenY.f, backColour)
        }

        // Draw the actual image
        cf.draw(screenX.f, screenY.f)

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
        }
    }

    fun setTargetRoom(value: Room): Boolean {
        val slots = slotsFor(value)

        if (value.computerPoint != null)
            if (setTargetRoom(value, value.pointToSlot(value.computerPoint!!), slots))
                return true

        if (value == room)
            return true

        for (i in slots.indices) {
            if (setTargetRoom(value, i, slots))
                return true
        }

        return false
    }

    private fun setTargetRoom(value: Room, slot: Int, slots: Array<AbstractCrew?>): Boolean {
        // If this slot is neither empty nor consumed by us (since we're moving anyway), then
        // it's consumed and we should skip it.
        val current = slots[slot]
        if (current != null && current != this)
            return false

        val pos = value.slotToPoint(slot)

        // Skip obstructed cells - eg, healer in the medbay
        if (value.obstructions.contains(pos))
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

    private fun clearFromRoomSlots(slots: Array<AbstractCrew?>) {
        slots.forEachIndexed { index, crew ->
            if (crew == this)
                slots[index] = null
        }
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
    }

    /**
     * Immediately remove this crew from the ship. This is run after
     * the dying animation finishes.
     */
    open fun removeFromShip() {
        val ship = room.ship
        for (room in ship.rooms) {
            clearFromRoomSlots(room.reservedPlayerSlots)
            clearFromRoomSlots(room.reservedEnemySlots)
        }

        // To support both crew and drones, remove ourselves
        // from all the crew lists.
        ship.crew.remove(this)
        ship.dronePawns.remove(this)
    }

    enum class SlotType {
        CREW,
        INTRUDER;

        val other
            get() = when (this) {
                CREW -> INTRUDER
                INTRUDER -> CREW
            }

        fun slotsFor(room: Room): Array<AbstractCrew?> = when (this) {
            CREW -> room.reservedPlayerSlots
            INTRUDER -> room.reservedEnemySlots
        }
    }

    enum class Action {
        IDLE,
        MOVING,
        MANNING, // Working at a computer
        REPAIRING,
        FIGHTING,
        DYING,
    }
}
