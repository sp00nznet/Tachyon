package xyz.znix.xftl.layout

import org.newdawn.slick.Graphics
import xyz.znix.xftl.AbstractSystem
import xyz.znix.xftl.Constants.*
import xyz.znix.xftl.Ship
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.Direction
import xyz.znix.xftl.math.IPoint

data class Room(val ship: Ship, val id: Int, val x: Int, val y: Int, val width: Int, val height: Int) {

    var system: AbstractSystem? = null
        private set
    var computerDirection: Direction? = null
        private set
    var computerPoint: IPoint? = null
        private set

    private var _doors: List<Door>? = null
    val doors: List<Door>
        get() {
            return _doors ?: throw IllegalStateException("Doors are not yet initialised")
        }

    // Oxygen level in percent
    var oxygen: Int = 100

    // Offset of this room from the ship's 0,0 screen position
    val offsetX get() = ROOM_SIZE * (x + ship.offset.x) - ship.hullOffset.x
    val offsetY get() = ROOM_SIZE * (y + ship.offset.y) - ship.hullOffset.y

    val position = ConstPoint(x, y)

    val reservedPlayerSlots: Array<AbstractCrew?> = Array(width * height) { null }
    val reservedEnemySlots: Array<AbstractCrew?> = Array(width * height) { null }

    fun initialise(doors: List<Door>) {
        if (_doors != null)
            throw IllegalStateException("Cannot reinitialise room")

        _doors = doors
    }

    fun update(dt: Float) {
        system?.update(dt)
    }

    fun render(g: Graphics, selected: Boolean) {
        val x = offsetX
        val y = offsetY

        val w = width * ROOM_SIZE
        val h = height * ROOM_SIZE

        g.color = FLOOR_COLOUR
        g.fillRect(
                x.toFloat(),
                y.toFloat(),
                w.toFloat(),
                h.toFloat())

        g.color = FLOOR_GRID_COLOUR
        for (i in 1 until width) {
            g.drawLine(
                    (x + i * ROOM_SIZE).toFloat(),
                    y.toFloat(),
                    (x + i * ROOM_SIZE).toFloat(),
                    (y + h - 1).toFloat())
        }

        for (i in 1 until height) {
            g.drawLine(
                    x.toFloat(),
                    (y + ROOM_SIZE * i).toFloat(),
                    (x + w - 1).toFloat(),
                    (y + ROOM_SIZE * i).toFloat())
        }

        val system = system
        if (system?.img != null) {
            // Render the interior decals
            val bg = ship.sys.getImg(system.img)
            g.drawImage(bg, x.toFloat(), y.toFloat())
        }

        g.color = ROOM_BORDER_COLOUR
        // Draw two one-pixel lines around the room, as it's too much of a hassle to
        // change the line width, as it seems to be rather implementation-specific
        g.drawRect(x.toFloat(), y.toFloat(),
                (w - 1).toFloat(),
                (h - 1).toFloat())
        g.drawRect((x + 1).toFloat(), (y + 1).toFloat(),
                (w - 3).toFloat(),
                (h - 3).toFloat())

        // Draw the system icon
        system?.drawRoom(g)
    }

    fun setSystem(system: AbstractSystem?, compPoint: IPoint?, compDir: Direction?) {
        this.system = system
        this.computerDirection = compDir
        this.computerPoint = compPoint

        system?.room = this

        if (system == null)
            check(compPoint == null)

        if (compPoint == null)
            check(compDir == null)
    }

    // Check if a point (relative to this room's origin) is inside this room
    fun containsRelative(target: IPoint): Boolean {
        if (target.x < 0 || target.y < 0)
            return false

        if (target.x >= width || target.y >= height)
            return false

        return true
    }

    // Check if a point (relative to the ship's origin) is inside this room
    fun containsAbsolute(target: IPoint): Boolean {
        if (target.x < x || target.y < y)
            return false

        if (target.x >= x + width || target.y >= y + height)
            return false

        return true
    }

    fun slotToPoint(slot: Int): IPoint {
        if (slot >= width * height)
            throw ArrayIndexOutOfBoundsException("Invalid slot $slot for $width*$height room - range is 0 to ${width * height}")

        return ConstPoint(slot % width, slot / width)
    }

    fun pointToSlot(point: IPoint): Int {
        check(containsRelative(point))

        return point.x + point.y * width
    }
}
