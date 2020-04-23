package xyz.znix.xftl.layout

import org.newdawn.slick.Color
import org.newdawn.slick.Graphics
import xyz.znix.xftl.AbstractSystem
import xyz.znix.xftl.Constants.*
import xyz.znix.xftl.Ship
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.f
import xyz.znix.xftl.lerp
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.Direction
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.math.Point
import xyz.znix.xftl.systems.Oxygen

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
            return _doors ?: error("Doors are not yet initialised")
        }

    /**
     * Oxygen level from 1-0.
     */
    var oxygen: Float = 1f

    // Offset of this room from the ship's 0,0 screen position
    val offsetX get() = ROOM_SIZE * (x + ship.offset.x) - ship.hullOffset.x
    val offsetY get() = ROOM_SIZE * (y + ship.offset.y) - ship.hullOffset.y

    val position = ConstPoint(x, y)

    val reservedPlayerSlots: Array<AbstractCrew?> = Array(width * height) { null }
    val reservedEnemySlots: Array<AbstractCrew?> = Array(width * height) { null }

    val obstructions = HashSet<ConstPoint>()

    fun initialise(doors: List<Door>) {
        check(_doors == null) { "Cannot reinitialise room" }

        _doors = doors
    }

    fun update(dt: Float) {
        system?.update(dt)

        // On the observation from the FTL wiki that ships loose ~1% oxygen per second
        val refillRate = (ship.oxygen?.refillRate ?: 0f) - Oxygen.ROOM_DRAIN_RATE
        oxygen = (oxygen + refillRate * dt).coerceAtLeast(0f).coerceAtMost(1f)
    }

    fun render(g: Graphics, selected: Boolean) {
        val x = offsetX
        val y = offsetY

        val w = width * ROOM_SIZE
        val h = height * ROOM_SIZE

        // TODO draw oxygen stripes when very low (<= 5%) - img/effects/low_o2_stripes_*x*.png
        g.color = FLOOR_COLOUR_NO_OXYGEN.lerp(FLOOR_COLOUR, oxygen)
        g.fillRect(
                x.f,
                y.f,
                w.f,
                h.f)

        g.color = FLOOR_GRID_COLOUR
        for (i in 1 until width) {
            g.drawLine(
                    (x + i * ROOM_SIZE).f,
                    y.f,
                    (x + i * ROOM_SIZE).f,
                    (y + h - 1).f)
        }

        for (i in 1 until height) {
            g.drawLine(
                    x.f,
                    (y + ROOM_SIZE * i).f,
                    (x + w - 1).f,
                    (y + ROOM_SIZE * i).f)
        }

        val system = system
        if (system?.img != null) {
            // Render the interior decals
            val bg = ship.sys.getImg(system.img)
            g.drawImage(bg, x.f, y.f)
        } else if (system != null && computerPoint != null) {
            // AI ships rarely (never?) use proper room textures. For systems like
            // engines and piloting that can be manned, draw a standard computer image
            // on instead.
            val comp = ship.sys.getImg("img/ship/interior/computer1.png")
            val imgX = x.f + computerPoint!!.x * ROOM_SIZE
            val imgY = y.f + computerPoint!!.y * ROOM_SIZE
            g.pushTransform()
            g.rotate(imgX + ROOM_SIZE / 2, imgY + ROOM_SIZE / 2, computerDirection!!.angle.f)
            g.drawImage(comp, imgX, imgY)
            g.popTransform()
        }

        // Draw the pathing-to boxes, if required
        reservedPlayerSlots.forEachIndexed draw@{ i, crew ->
            if (crew == null)
                return@draw

            val slot = slotToPoint(i)

            // If the crewmember is in their assigned position, don't draw the box
            if (crew.position == slot && crew.room == this)
                return@draw

            val point = Point(slot)
            point *= ROOM_SIZE
            point.x += offsetX
            point.y += offsetY

            ship.sys.getImg("img/people/green_destination.png").draw(point.x.f, point.y.f)
        }

        // Draw two one-pixel lines around the room, as it's too much of a hassle to
        // change the line width, as it seems to be rather implementation-specific
        for (i in 0..5) {
            if (i < 2)
                g.color = ROOM_BORDER_COLOUR
            else if (!selected)
                continue
            else if (i < 5)
                g.color = ROOM_BORDER_COLOUR_SELECTED
            else
                g.color = ROOM_BORDER_COLOUR_SELECTED_INNER
            g.drawRect((x + i).f, (y + i).f,
                    (w - 1 - i * 2).f,
                    (h - 1 - i * 2).f)
        }

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
