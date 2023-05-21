package xyz.znix.xftl.layout

import org.newdawn.slick.Color
import org.newdawn.slick.Graphics
import xyz.znix.xftl.Constants
import xyz.znix.xftl.Constants.ROOM_SIZE
import xyz.znix.xftl.Ship
import xyz.znix.xftl.f
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
    val offsetX get() = ROOM_SIZE * (position.x + ship.offset.x)
    val offsetY get() = ROOM_SIZE * (position.y + ship.offset.y)

    val leftPos: RoomPoint? = if (left == null) null else RoomPoint(left, position - left.position + offsetFor(left))
    val rightPos: RoomPoint? =
        if (right == null) null else RoomPoint(right, position - right.position + offsetFor(right))

    val isAirlock: Boolean get() = left == null || right == null

    /**
     * True if this door is considered to be open for the purposes
     * of letting air and/or intruders through.
     *
     * The door will animate open when a friendly crewmember is walking
     * through, but that won't let intruders or air through.
     */
    var open: Boolean = false

    /**
     * Whether this door should appear open or not. Unlike [open], this
     * is set to true when a crewmember is walking through the door.
     */
    var visualOpen: Boolean = false

    // True if the player is hovering over this door
    private var hovered: Boolean = false

    // Set during the update cycle by crew walking through this door.
    // This is how we know if a door should appear to open for
    // a crewmember or not.
    private var crewOpenDemand: Boolean = false

    // How open this door is. This is updated to produce the animation.
    private var stateAnimation: Float = 0f

    private var isHacked = false

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

    /**
     * Called by crewmembers walking through this door to request that it opens visually.
     */
    fun crewRequestOpen() {
        crewOpenDemand = true
    }

    fun update(dt: Float) {
        // TODO update the door when the game is paused, so it animates open when clicked.

        // If a crewmember is walking through this door, they'll
        // set crewOpenDemand every update.
        visualOpen = open || crewOpenDemand
        crewOpenDemand = false

        if (visualOpen) {
            stateAnimation = (stateAnimation + dt / ANIMATION_TIME).coerceAtMost(1f)
        } else {
            stateAnimation = (stateAnimation - dt / ANIMATION_TIME).coerceAtLeast(0f)
        }

        // If the door is actually open, transfer air between the two rooms
        if (open) {
            updateOxygen(dt)
        }

        // The door counts as hacked if the system on either side of it is hacked
        // and the hacking system is at least powered on, or (for any door) if
        // the doors system is hacked with a hacking pulse.
        isHacked = ship.doorsSystem?.isHackActive == true ||
                left?.system?.hackedBy?.isPoweredUp == true ||
                right?.system?.hackedBy?.isPoweredUp == true
    }

    private fun updateOxygen(dt: Float) {
        // If this is an airlock, instantly drain the air from this room.
        if (left == null || right == null) {
            left?.oxygen = 0f
            right?.oxygen = 0f
            return
        }

        // Otherwise transfer air between the adjacent rooms.
        // There's 8% per second mentioned in the code, but that's part
        // of a fairly complicated-looking transfer thing and seems
        // way too slow here.
        // It seems to take about 6.5 seconds for a room adjacent to
        // an airlock to drain of oxygen, so we need to match that.
        // TODO make this properly match how FTL works.
        val transferRate = 0.45f
        val delta = left.oxygen - right.oxygen
        left.oxygen -= delta * dt * transferRate
        right.oxygen += delta * dt * transferRate
    }

    fun updateMouseHover(x: Int, y: Int) {
        var centreX = offsetX
        var centreY = offsetY

        if (isVertical) {
            centreY += ROOM_SIZE / 2
        } else {
            centreX += ROOM_SIZE / 2
        }

        hovered = false

        val hoverRange = 10

        if (x !in centreX - hoverRange..centreX + hoverRange)
            return
        if (y !in centreY - hoverRange..centreY + hoverRange)
            return

        hovered = true
    }

    fun click(x: Int, y: Int): Boolean {
        // Update our hovered status, since we use that
        // to determine if we're being clicked.
        updateMouseHover(x, y)

        if (!hovered) {
            return false
        }

        open = !open

        return true
    }

    fun render(g: Graphics) {
        val doorSheet = ship.sys.getImg("img/effects/door_sheet.png")
        val highlight = ship.sys.getImg("img/effects/door_highlight.png")

        val level = ship.doorsSystem?.undamagedEnergy ?: 0

        // Broken and level 1 doors use the same sprite, but broken doors
        // have a colour filter applied to them.
        val sheetY = ROOM_SIZE * when {
            isHacked -> 4
            level == 0 -> 0
            else -> level - 1
        }

        // This is used to animate the door opening and closing, selecting the
        // correct frame for its motion.
        val sheetX = ROOM_SIZE * (stateAnimation * 4).toInt()

        val filter = if (level == 0 && !isHacked) Constants.DOOR_BROKEN_FILTER else Color.white

        g.pushTransform()

        // Note that isVertical refers to whether the door is vertical
        // or not, NOT whether the two rooms it joins are placed one
        // above the other.
        if (isVertical) {
            g.translate(offsetX.f - ROOM_SIZE / 2, offsetY.f)
        } else {
            g.translate(offsetX.f + ROOM_SIZE, offsetY.f - ROOM_SIZE / 2)
            g.rotate(0f, 0f, 90f)
        }

        // Draw the mouse hover highlight
        if (hovered) {
            val highlightFilter = Color(1f, 1f, 1f, 0.75f)
            highlight.draw(0f, 0f, highlightFilter)
        }

        doorSheet.draw(
            0f, 0f, ROOM_SIZE.f, ROOM_SIZE.f,

            sheetX.f, sheetY.f,
            sheetX.f + ROOM_SIZE, sheetY.f + ROOM_SIZE,
            filter
        )

        g.popTransform()
    }

    companion object {
        private const val ANIMATION_TIME: Float = 0.2f
    }
}
