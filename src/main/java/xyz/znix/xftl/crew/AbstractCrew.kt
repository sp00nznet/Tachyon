package xyz.znix.xftl.crew

import org.newdawn.slick.Animation
import org.newdawn.slick.SpriteSheet
import xyz.znix.xftl.Animations
import xyz.znix.xftl.Constants.*
import xyz.znix.xftl.f
import xyz.znix.xftl.layout.Door
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.Direction
import xyz.znix.xftl.math.Point
import xyz.znix.xftl.math.RoomPoint

abstract class AbstractCrew(
    val codename: String,
    private val anims: Animations,
    var room: Room,
    mode: SlotType
) {
    var icon: Animation
    val backImg: SpriteSheet

    // The cell position in the current room
    var position: Point = Point(0, 0)
        set(value) {
            field = value
            updateAnimation()
        }

    val roomPosition: RoomPoint get() = RoomPoint(room, position)

    var roomWasDamaged: Boolean = false

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

            val target = position.copy()
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

    // The door we're walking through
    private var targetDoor: Door? = null

    val screenX: Int get() = room.offsetX + ((position.x + movementOffsetX) * ROOM_SIZE).toInt()
    val screenY: Int get() = room.offsetY + ((position.y + movementOffsetY) * ROOM_SIZE).toInt()

    val movementOffsetX: Float get() = movement?.x?.times(movementProgress) ?: 0f
    val movementOffsetY: Float get() = movement?.y?.times(movementProgress) ?: 0f

    init {
        val anim = anims["${codename}_portrait"]
        icon = anim.start()
        backImg = anim.sheet.secondary!!
        updateAnimation()
    }

    fun slotsFor(room: Room) = mode.slotsFor(room)

    fun update(dt: Float) {
        icon.update((dt * 1000).toLong())

        val pos = position
        if (movement != null) {
            movementProgress += dt * 2

            if (movementProgress > 1) {
                movementProgress = 0f
                pos += movement!!
                movement = null

                // If we've walked through a doorway, switch over
                if (targetDoor != null) {
                    val newRoom = targetDoor!!.other(room)!!

                    // Move our position into the new room
                    pos += room.position
                    pos -= newRoom.position

                    check(newRoom.containsRelative(pos))

                    room = newRoom
                    targetDoor = null

                    updateAnimation()
                }

                // Calculate the next movement
                updateMovement()
            }
            return
        } else {
            // Update ourselves to move to the computer
            val cp = room.computerPoint?.let(room::pointToSlot)
            if (cp != null && room.reservedPlayerSlots[cp] == null) {
                setTargetRoom(room)
            }
        }

        room.system?.let { sys ->
            if (sys.damaged)
                sys.repair(dt / BASE_REPAIR_TIME)
            if (roomWasDamaged != sys.damaged)
                updateAnimation()
            roomWasDamaged = sys.damaged
        }
    }

    fun draw() {
        // Bit of a hack, since we're drawn from the ship
        val isSelected = room.ship.sys.shipUI.isCrewSelected(this)

        // Draw the background image - the coloured hint that changes when you mouse over the crew
        val cf = icon.currentFrame
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

        // Draw the actual image
        cf.draw(screenX.f, screenY.f)
    }

    /**
     * Recalculate (via pathfinding) the player's current [movement] to get to
     * their desired target.
     */
    private fun updateMovement() {
        if (pathingTarget == roomPosition)
            pathingTarget = null

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

    private fun updateAnimation() {
        if (movement != null) {
            icon = anims["${codename}_walk_${dirAsString(movement!!)}"].start()
            return
        }

        if (room.system?.damaged == true) {
            icon = anims["${codename}_repair"].start()
            return
        }

        if (room.computerPoint == position) {
            icon = anims["${codename}_type_${dirAsString(room.computerDirection!!)}"].start()
            return
        }

        // Since the portrait doesn't have a background colour frame, use the top-left frame.
        icon = Animation(anims["${codename}_portrait"].sheet.sheet, 0, 0, 0, 0, true, 1, false)
        return
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

    enum class SlotType {
        CREW,
        INTRUDER;

        fun slotsFor(room: Room): Array<AbstractCrew?> = when (this) {
            CREW -> room.reservedPlayerSlots
            INTRUDER -> room.reservedEnemySlots
        }
    }
}
