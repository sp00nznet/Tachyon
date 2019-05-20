package xyz.znix.xftl.layout

import xyz.znix.xftl.AbstractSystem
import xyz.znix.xftl.Constants.ROOM_SIZE
import xyz.znix.xftl.Ship
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

    fun initialise(doors: List<Door>) {
        if (_doors != null)
            throw IllegalStateException("Cannot reinitialise room")

        _doors = doors
    }

    fun update(dt: Float) {
        system?.update(dt)
    }

    fun setSystem(system: AbstractSystem?, compPoint: IPoint?, compDir: Direction?) {
        this.system = system
        this.computerDirection = compDir
        this.computerPoint = compPoint

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
}
