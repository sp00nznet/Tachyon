package xyz.znix.xftl.layout

import xyz.znix.xftl.Constants.ROOM_SIZE
import xyz.znix.xftl.Ship
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.Direction
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.math.RoomPoint

data class Door(val position: ConstPoint, val left: Room?, val right: Room?, val isVertical: Boolean) {
    init {
        check(left != null || right != null) { "Cannot have a door detached from any room" }
    }

    val ship: Ship = left?.ship ?: right!!.ship

    // Offset of this room from the ship's 0,0 screen position
    val offsetX get() = ROOM_SIZE * (position.x + ship.offset.x) - ship.hullOffset.x
    val offsetY get() = ROOM_SIZE * (position.y + ship.offset.y) - ship.hullOffset.y

    val leftPos: RoomPoint? = if (left == null) null else RoomPoint(left, position - left.position + offsetFor(left))
    val rightPos: RoomPoint? =
        if (right == null) null else RoomPoint(right, position - right.position + offsetFor(right))

    val isAirlock: Boolean get() = left == null || right == null

    /**
     * The other room this door connects to
     */
    fun other(room: Room): Room? {
        return when (room) {
            left -> right
            right -> left
            else -> throw IllegalArgumentException("Supplied room '$room' is not part of the door")
        }
    }

    /**
     * The position (relative to the given room) for one side of the door
     */
    fun roomPos(room: Room): RoomPoint {
        return when (room) {
            left -> leftPos!!
            right -> rightPos!!
            else -> throw IllegalArgumentException("Supplied room '$room' is not part of the door")
        }
    }

    /**
     * The position of the other side of the door, relative to the other room
     *
     * This is essentially roomPos(other(room)) but with null handling
     */
    fun otherPos(room: Room): IPoint? {
        return roomPos(other(room) ?: return null)
    }

    private fun offsetFor(room: Room): ConstPoint {
        return if (isVertical)
            if (room.x == position.x) ConstPoint.ZERO else ConstPoint(-1, 0)
        else
            if (room.y == position.y) ConstPoint.ZERO else ConstPoint(0, -1)
    }

    /**
     * Find the direction you would have to walk to go through this door, if standing inside [room].
     */
    fun dirFor(room: Room): Direction {
        return if (isVertical)
            if (room.x == position.x) Direction.LEFT else Direction.RIGHT
        else
            if (room.y == position.y) Direction.UP else Direction.DOWN
    }
}
